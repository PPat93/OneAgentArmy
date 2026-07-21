package com.parrotworks.oneagentarmy.provider.ai.anthropic

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.MessagesRequest
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.historyMessage
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.outputText
import com.parrotworks.oneagentarmy.testutil.redirectingClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnthropicApiClientTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val client by lazy { AnthropicApiClient(redirectingClient(mockWebServerRule.server)) }

    private fun sampleRequest() = MessagesRequest(
        model = "claude-haiku-4-5",
        maxTokens = 1024,
        system = "You are a helpful assistant.",
        messages = listOf(historyMessage("user", "Hi")),
    )

    @Test
    fun `parses a successful response`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {
                      "content": [{"type": "text", "text": "Hello there!"}],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 12, "output_tokens": 34}
                    }
                """.trimIndent(),
            ),
        )

        val response = client.createMessage("test-key", sampleRequest())

        assertEquals("Hello there!", response.outputText())
        assertEquals(12L, response.usage?.inputTokens)
        assertEquals(34L, response.usage?.outputTokens)
    }

    @Test
    fun `sends the request to the messages endpoint with auth headers and max_tokens`() = runTest {
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = """{"content":[]}"""))

        client.createMessage("test-key", sampleRequest())

        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("/v1/messages", recorded.url.encodedPath)
        assertEquals("test-key", recorded.headers["x-api-key"])
        assertEquals("2023-06-01", recorded.headers["anthropic-version"])
        assertTrue(recorded.body?.utf8()?.contains("\"max_tokens\":1024") == true)
    }

    @Test
    fun `401 throws InvalidApiKey`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 401, body = """{"error":{"message":"Invalid API key"}}"""),
        )

        assertThrows(AiProviderException.InvalidApiKey::class.java) {
            runBlocking { client.createMessage("bad-key", sampleRequest()) }
        }
    }

    @Test
    fun `403 throws InvalidApiKey`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 403, body = """{"error":{"message":"Forbidden"}}"""),
        )

        assertThrows(AiProviderException.InvalidApiKey::class.java) {
            runBlocking { client.createMessage("bad-key", sampleRequest()) }
        }
    }

    @Test
    fun `429 throws RateLimited with retry-after`() {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 429,
                headers = headersOf("Retry-After", "42"),
                body = """{"error":{"message":"Too many requests"}}""",
            ),
        )

        val exception = assertThrows(AiProviderException.RateLimited::class.java) {
            runBlocking { client.createMessage("test-key", sampleRequest()) }
        }
        assertEquals(42, exception.retryAfterSeconds)
    }

    @Test
    fun `500 throws ServerError`() {
        mockWebServerRule.server.enqueue(MockResponse(code = 500, body = "internal error"))

        val exception = assertThrows(AiProviderException.ServerError::class.java) {
            runBlocking { client.createMessage("test-key", sampleRequest()) }
        }
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun `connection failure throws NoConnectivity`() {
        mockWebServerRule.server.close()

        assertThrows(AiProviderException.NoConnectivity::class.java) {
            runBlocking { client.createMessage("test-key", sampleRequest()) }
        }
    }
}
