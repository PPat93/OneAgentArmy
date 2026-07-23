package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.testutil.redirectingClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelAvailabilityTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val checker by lazy { ModelAvailabilityChecker(redirectingClient(mockWebServerRule.server)) }

    // --- parsing ---

    @Test
    fun `parses openai model list`() {
        val body = """{"object":"list","data":[{"id":"gpt-4.1-nano"},{"id":"gpt-5.6-luna"}]}"""

        assertEquals(setOf("gpt-4.1-nano", "gpt-5.6-luna"), parseAvailableModelIds(AiProviderRegistry.OPENAI, body))
    }

    @Test
    fun `parses anthropic model list`() {
        val body = """{"data":[{"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"}],"has_more":false}"""

        assertEquals(setOf("claude-haiku-4-5"), parseAvailableModelIds(AiProviderRegistry.ANTHROPIC, body))
    }

    @Test
    fun `parses gemini model list and strips the models prefix`() {
        val body = """{"models":[{"name":"models/gemini-3.5-flash"},{"name":"models/gemini-3-flash-preview"}]}"""

        assertEquals(
            setOf("gemini-3.5-flash", "gemini-3-flash-preview"),
            parseAvailableModelIds(AiProviderRegistry.GEMINI, body),
        )
    }

    @Test
    fun `malformed listing throws`() {
        assertThrows(Exception::class.java) { parseAvailableModelIds(AiProviderRegistry.OPENAI, "not json") }
    }

    // --- missing-model computation ---

    @Test
    fun `missing ids are those in the registry but not in the listing`() {
        val models = AiProviderRegistry.builtInProviders.first { it.id == AiProviderRegistry.OPENAI }.models

        val missing = missingModelIds(models, setOf("gpt-4.1-nano", "gpt-5.6-luna"))

        assertEquals(listOf("gpt-5.6-sol"), missing)
    }

    @Test
    fun `nothing is missing when the listing covers the registry`() {
        val models = AiProviderRegistry.builtInProviders.first { it.id == AiProviderRegistry.OPENAI }.models

        assertTrue(missingModelIds(models, models.map { it.id }.toSet() + "extra-model").isEmpty())
    }

    // --- HTTP checker ---

    @Test
    fun `openai check sends bearer auth to the models endpoint`() = runBlocking {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 200, body = """{"data":[{"id":"gpt-4.1-nano"}]}"""),
        )

        val result = checker.listAvailable(AiProviderRegistry.OPENAI, "test-key")

        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("/v1/models", recorded.url.encodedPath)
        assertEquals("Bearer test-key", recorded.headers["Authorization"])
        assertEquals(
            setOf("gpt-4.1-nano"),
            (result as ModelAvailabilityChecker.ProviderCheck.Available).modelIds,
        )
    }

    @Test
    fun `anthropic check sends api key and version headers`() = runBlocking {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 200, body = """{"data":[{"id":"claude-haiku-4-5"}]}"""),
        )

        val result = checker.listAvailable(AiProviderRegistry.ANTHROPIC, "test-key")

        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("/v1/models", recorded.url.encodedPath)
        assertEquals("1000", recorded.url.queryParameter("limit"))
        assertEquals("test-key", recorded.headers["x-api-key"])
        assertEquals("2023-06-01", recorded.headers["anthropic-version"])
        assertTrue(result is ModelAvailabilityChecker.ProviderCheck.Available)
    }

    @Test
    fun `gemini check sends goog api key header`() = runBlocking {
        mockWebServerRule.server.enqueue(
            MockResponse(code = 200, body = """{"models":[{"name":"models/gemini-3.5-flash"}]}"""),
        )

        val result = checker.listAvailable(AiProviderRegistry.GEMINI, "test-key")

        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("/v1beta/models", recorded.url.encodedPath)
        assertEquals("test-key", recorded.headers["x-goog-api-key"])
        assertEquals(
            setOf("gemini-3.5-flash"),
            (result as ModelAvailabilityChecker.ProviderCheck.Available).modelIds,
        )
    }

    @Test
    fun `http error maps to Failed not an exception`() = runBlocking {
        mockWebServerRule.server.enqueue(MockResponse(code = 401, body = """{"error":"bad key"}"""))

        val result = checker.listAvailable(AiProviderRegistry.OPENAI, "bad-key")

        assertTrue(result is ModelAvailabilityChecker.ProviderCheck.Failed)
        assertEquals("HTTP 401", (result as ModelAvailabilityChecker.ProviderCheck.Failed).detail)
    }

    @Test
    fun `connection failure maps to Failed`() = runBlocking {
        mockWebServerRule.server.close()

        val result = checker.listAvailable(AiProviderRegistry.GEMINI, "test-key")

        assertTrue(result is ModelAvailabilityChecker.ProviderCheck.Failed)
    }
}
