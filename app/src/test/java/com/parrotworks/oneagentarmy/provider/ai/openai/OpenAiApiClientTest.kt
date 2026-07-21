package com.parrotworks.oneagentarmy.provider.ai.openai

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.ResponsesRequest
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.inputMessageItem
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.outputText
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

class OpenAiApiClientTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val client by lazy { OpenAiApiClient(redirectingClient(mockWebServerRule.server)) }

    private fun sampleRequest() = ResponsesRequest(
        model = "gpt-4.1-nano",
        instructions = "You are a helpful assistant.",
        input = listOf(inputMessageItem("user", "Hi")),
    )

    @Test
    fun `parses a successful response`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {
                      "output": [
                        {"type": "message", "content": [{"type": "output_text", "text": "Hello there!"}]}
                      ],
                      "usage": {"input_tokens": 12, "output_tokens": 34}
                    }
                """.trimIndent(),
            ),
        )

        val response = client.createResponse("test-key", sampleRequest())

        assertEquals("Hello there!", response.outputText())
        assertEquals(12L, response.usage?.inputTokens)
        assertEquals(34L, response.usage?.outputTokens)
    }

    @Test
    fun `sends the request to the responses endpoint with auth header and store=false`() = runTest {
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = """{"output":[]}"""))

        client.createResponse("test-key", sampleRequest())

        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("/v1/responses", recorded.url.encodedPath)
        assertEquals("Bearer test-key", recorded.headers["Authorization"])
        assertTrue(recorded.body?.utf8()?.contains("\"store\":false") == true)
    }

    @Test
    fun `401 throws InvalidApiKey`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 401, body = """{"error":{"message":"Incorrect API key"}}"""),
        )

        val exception = assertThrows(AiProviderException.InvalidApiKey::class.java) {
            runBlocking { client.createResponse("bad-key", sampleRequest()) }
        }
        assertTrue(exception.detail?.contains("Incorrect API key") == true)
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
            runBlocking { client.createResponse("test-key", sampleRequest()) }
        }
        assertEquals(42, exception.retryAfterSeconds)
    }

    @Test
    fun `500 throws ServerError`() {
        mockWebServerRule.server.enqueue(MockResponse(code = 500, body = "internal error"))

        val exception = assertThrows(AiProviderException.ServerError::class.java) {
            runBlocking { client.createResponse("test-key", sampleRequest()) }
        }
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun `connection failure throws NoConnectivity`() {
        mockWebServerRule.server.close()

        assertThrows(AiProviderException.NoConnectivity::class.java) {
            runBlocking { client.createResponse("test-key", sampleRequest()) }
        }
    }
}
