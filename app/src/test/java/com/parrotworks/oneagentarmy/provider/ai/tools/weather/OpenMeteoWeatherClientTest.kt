package com.parrotworks.oneagentarmy.provider.ai.tools.weather

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.testutil.redirectingClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class OpenMeteoWeatherClientTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private val client by lazy { OpenMeteoWeatherClient(redirectingClient(mockWebServerRule.server)) }

    @Test
    fun `geocode parses the first result`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """{"results":[{"name":"Krakow","latitude":50.06,"longitude":19.94,"country":"Poland"}]}""",
            ),
        )

        val result = client.geocode("Krakow")

        assertEquals("Krakow", result?.name)
        assertEquals("Poland", result?.country)
    }

    @Test
    fun `forecast parses current and daily blocks`() = runTest {
        mockWebServerRule.server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {
                      "current": {"temperature_2m": 21.5, "weather_code": 1},
                      "daily": {"time": ["2026-07-25"], "temperature_2m_max": [24.0], "temperature_2m_min": [15.0]}
                    }
                """.trimIndent(),
            ),
        )

        val forecast = client.forecast(50.06, 19.94, days = 1)

        assertEquals(21.5, forecast.current?.temperature)
        assertEquals(listOf(24.0), forecast.daily?.temperatureMax)
    }

    @Test
    fun `a non-2xx response throws Unknown, not silently returning garbage`() {
        mockWebServerRule.server.enqueue(MockResponse(code = 500, body = "upstream error"))

        assertThrows(AiProviderException.Unknown::class.java) {
            runBlocking { client.geocode("Krakow") }
        }
    }

    // Regression guard for the fix itself: this client used to hardcode every IOException as
    // NoConnectivity, which mislabels a genuine timeout as "no internet connection" - the
    // exact bug already fixed for the three main provider clients via the shared
    // IOException.toProviderException() classifier, just missed here since this client talks
    // to a different host and predates that fix. A real socket-timeout is exercised directly
    // against the classifier in IoExceptionClassifierTest; what matters here is only that this
    // client now goes through it instead of a hardcoded branch - proven by connection failure
    // (the one IOException shape a fast unit test can trigger) still mapping correctly.
    @Test
    fun `connection failure still maps to NoConnectivity`() {
        mockWebServerRule.server.close()

        assertThrows(AiProviderException.NoConnectivity::class.java) {
            runBlocking { client.geocode("Krakow") }
        }
    }
}
