package com.parrotworks.oneagentarmy.provider.ai.anthropic

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.MessagesRequest
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.ephemeralCacheControl
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.historyMessage
import com.parrotworks.oneagentarmy.testutil.redirectingClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Prompt caching must never be able to break sending a message: if the API stops
// accepting cache_control, the client drops it and retries instead of failing.
class AnthropicCacheControlTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val client by lazy { AnthropicApiClient(redirectingClient(mockWebServerRule.server)) }

    private fun cachedRequest() = MessagesRequest(
        model = "claude-opus-5",
        maxTokens = 1024,
        system = "You are helpful.",
        messages = listOf(historyMessage("user", "Hi")),
        cacheControl = ephemeralCacheControl(),
    )

    private val successBody = """{"content":[{"type":"text","text":"Hello"}],"usage":{"input_tokens":5,"output_tokens":2}}"""

    @Test
    fun `cache_control is sent on the request`() = runTest {
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = successBody))

        client.createMessage("test-key", cachedRequest())

        val body = mockWebServerRule.server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains("\"cache_control\""))
        assertTrue(body.contains("\"ephemeral\""))
    }

    @Test
    fun `a 400 mentioning the cache retries once without cache_control and succeeds`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 400,
                body = """{"error":{"message":"cache_control: unsupported parameter"}}""",
            ),
        )
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = successBody))

        // The caller gets a normal response - the rejection is absorbed.
        val response = client.createMessage("test-key", cachedRequest())
        assertEquals(1, response.content.size)

        val first = mockWebServerRule.server.takeRequest().body?.utf8().orEmpty()
        val retry = mockWebServerRule.server.takeRequest().body?.utf8().orEmpty()
        assertTrue("first attempt should carry cache_control", first.contains("\"cache_control\""))
        assertFalse("retry must drop cache_control", retry.contains("\"cache_control\""))
    }

    @Test
    fun `once rejected later requests skip cache_control entirely`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 400, body = """{"error":{"message":"cache_control is not supported"}}"""),
        )
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = successBody))
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = successBody))

        client.createMessage("test-key", cachedRequest()) // discovers the rejection
        mockWebServerRule.server.takeRequest()
        mockWebServerRule.server.takeRequest()

        client.createMessage("test-key", cachedRequest())

        // No wasted rejected round-trip on subsequent sends.
        val body = mockWebServerRule.server.takeRequest().body?.utf8().orEmpty()
        assertFalse(body.contains("\"cache_control\""))
    }

    @Test
    fun `an unrelated 400 is not retried and still surfaces`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 400, body = """{"error":{"message":"max_tokens must be positive"}}"""),
        )

        val exception = assertThrows(AiProviderException.Unknown::class.java) {
            runBlocking { client.createMessage("test-key", cachedRequest()) }
        }

        assertTrue(exception.detail.contains("max_tokens"))
        // Exactly one attempt - a real error must not be masked by a retry.
        assertEquals(1, mockWebServerRule.server.requestCount)
    }

    @Test
    fun `a request that never carried cache_control is not retried`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 400, body = """{"error":{"message":"cache something went wrong"}}"""),
        )

        assertThrows(AiProviderException.Unknown::class.java) {
            runBlocking { client.createMessage("test-key", cachedRequest().copy(cacheControl = null)) }
        }

        assertEquals(1, mockWebServerRule.server.requestCount)
    }

    @Test
    fun `cache usage fields are parsed off a successful response`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {"content":[{"type":"text","text":"Hi"}],
                     "usage":{"input_tokens":50,"output_tokens":10,
                              "cache_read_input_tokens":9000,"cache_creation_input_tokens":100}}
                """.trimIndent(),
            ),
        )

        val response = client.createMessage("test-key", cachedRequest())

        // Long literals: the fields are Long?, so an Int literal would box and compare
        // unequal against a boxed Long.
        assertEquals(9000L, response.usage?.cacheReadInputTokens)
        assertEquals(100L, response.usage?.cacheCreationInputTokens)
    }
}
