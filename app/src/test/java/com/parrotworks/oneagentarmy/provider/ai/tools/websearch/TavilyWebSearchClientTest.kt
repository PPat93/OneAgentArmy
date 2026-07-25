package com.parrotworks.oneagentarmy.provider.ai.tools.websearch

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.testutil.redirectingClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TavilyWebSearchClientTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val client by lazy { TavilyWebSearchClient(redirectingClient(mockWebServerRule.server)) }

    @Test
    fun `parses results and sends the query with auth and max_results`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """{"results":[{"title":"Result","url":"https://example.com","content":"snippet"}]}""",
            ),
        )

        val results = client.search("weather in Krakow", apiKey = "test-key", maxResults = 3)

        assertEquals(listOf(WebSearchResult("Result", "https://example.com", "snippet")), results)
        val recorded = mockWebServerRule.server.takeRequest()
        assertEquals("Bearer test-key", recorded.headers["Authorization"])
        assertTrue(recorded.body?.utf8()?.contains("\"max_results\":3") == true)
    }

    @Test
    fun `a non-2xx response throws Unknown, not silently returning empty results`() {
        mockWebServerRule.server.enqueue(MockResponse(code = 401, body = "invalid api key"))

        assertThrows(AiProviderException.Unknown::class.java) {
            runBlocking { client.search("query", "bad-key", 5) }
        }
    }

    // Same regression guard as OpenMeteoWeatherClientTest - this client had the identical
    // hardcoded-NoConnectivity bug, missed by the earlier fix because it talks to a host
    // (api.tavily.com) outside the set the main provider clients use.
    @Test
    fun `connection failure still maps to NoConnectivity`() {
        mockWebServerRule.server.close()

        assertThrows(AiProviderException.NoConnectivity::class.java) {
            runBlocking { client.search("query", "test-key", 5) }
        }
    }
}
