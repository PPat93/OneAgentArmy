package com.parrotworks.oneagentarmy.provider.ai.gemini

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.InteractionsRequest
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.outputText
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.userInputStep
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

class GeminiApiClientTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val client by lazy { GeminiApiClient(redirectingClient(mockWebServerRule.server)) }

    private fun sampleRequest() = InteractionsRequest(
        model = "gemini-3.1-flash-lite",
        input = listOf(userInputStep("Hi")),
        systemInstruction = "You are a helpful assistant.",
    )

    @Test
    fun `parses a successful response`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {
                      "steps": [
                        {"type": "model_output", "content": [{"type": "text", "text": "Hello there!"}]}
                      ],
                      "usage": {"total_input_tokens": 12, "total_output_tokens": 34, "total_thought_tokens": 0}
                    }
                """.trimIndent(),
            ),
        )

        val response = client.createInteraction("test-key", sampleRequest())

        assertEquals("Hello there!", response.outputText())
        assertEquals(12L, response.usage?.totalInputTokens)
        assertEquals(34L, response.usage?.totalOutputTokens)
    }

    @Test
    fun `sends the request to the interactions endpoint with auth header and store=false`() = runTest {
        mockWebServerRule.server.enqueue(MockResponse(code = 200, body = """{"steps":[]}"""))

        client.createInteraction("test-key", sampleRequest())

        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("/v1beta/interactions", recorded.url.encodedPath)
        assertEquals("test-key", recorded.headers["x-goog-api-key"])
        assertTrue(recorded.body?.utf8()?.contains("\"store\":false") == true)
    }

    @Test
    fun `401 throws InvalidApiKey`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 401, body = """{"error":{"message":"Invalid API key"}}"""),
        )

        assertThrows(AiProviderException.InvalidApiKey::class.java) {
            runBlocking { client.createInteraction("bad-key", sampleRequest()) }
        }
    }

    @Test
    fun `403 throws InvalidApiKey`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 403, body = """{"error":{"message":"Forbidden"}}"""),
        )

        assertThrows(AiProviderException.InvalidApiKey::class.java) {
            runBlocking { client.createInteraction("bad-key", sampleRequest()) }
        }
    }

    // Gemini reports an invalid key as 400 API_KEY_INVALID rather than 401 - see the comment
    // in GeminiApiClient.kt. Only a 400 whose message actually mentions "API key" should map
    // to InvalidApiKey; any other 400 is a genuine bad-request, not a key problem.
    @Test
    fun `400 with API key message throws InvalidApiKey`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 400, body = """{"error":{"message":"API key not valid"}}"""),
        )

        assertThrows(AiProviderException.InvalidApiKey::class.java) {
            runBlocking { client.createInteraction("bad-key", sampleRequest()) }
        }
    }

    @Test
    fun `400 without API key message throws Unknown`() {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 400, body = """{"error":{"message":"Malformed request body"}}"""),
        )

        assertThrows(AiProviderException.Unknown::class.java) {
            runBlocking { client.createInteraction("test-key", sampleRequest()) }
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
            runBlocking { client.createInteraction("test-key", sampleRequest()) }
        }
        assertEquals(42, exception.retryAfterSeconds)
    }

    @Test
    fun `500 throws ServerError`() {
        mockWebServerRule.server.enqueue(MockResponse(code = 500, body = "internal error"))

        val exception = assertThrows(AiProviderException.ServerError::class.java) {
            runBlocking { client.createInteraction("test-key", sampleRequest()) }
        }
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun `connection failure throws NoConnectivity`() {
        mockWebServerRule.server.close()

        assertThrows(AiProviderException.NoConnectivity::class.java) {
            runBlocking { client.createInteraction("test-key", sampleRequest()) }
        }
    }
}
