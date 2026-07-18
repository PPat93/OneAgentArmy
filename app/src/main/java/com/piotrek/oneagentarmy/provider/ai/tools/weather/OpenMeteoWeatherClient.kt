package com.piotrek.oneagentarmy.provider.ai.tools.weather

import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.tools.weather.dto.ForecastResponse
import com.piotrek.oneagentarmy.provider.ai.tools.weather.dto.GeocodingResponse
import com.piotrek.oneagentarmy.provider.ai.tools.weather.dto.GeocodingResultDto
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenMeteoWeatherClient(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun geocode(locationName: String): GeocodingResultDto? = withContext(Dispatchers.IO) {
        val url = GEOCODING_URL.toHttpUrl().newBuilder()
            .addQueryParameter("name", locationName)
            .addQueryParameter("count", "1")
            .build()
        get(url.toString(), GeocodingResponse.serializer()).results.firstOrNull()
    }

    suspend fun forecast(latitude: Double, longitude: Double, days: Int): ForecastResponse =
        withContext(Dispatchers.IO) {
            val url = FORECAST_URL.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", latitude.toString())
                .addQueryParameter("longitude", longitude.toString())
                .addQueryParameter(
                    "current",
                    "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code",
                )
                .addQueryParameter(
                    "daily",
                    "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code",
                )
                .addQueryParameter("forecast_days", days.toString())
                .addQueryParameter("timezone", "auto")
                .build()
            get(url.toString(), ForecastResponse.serializer())
        }

    private fun <T> get(url: String, deserializer: DeserializationStrategy<T>): T {
        val response = try {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute()
        } catch (e: IOException) {
            throw AiProviderException.NoConnectivity("${e.javaClass.simpleName}: ${e.message}")
        }
        return response.use {
            val responseBody = it.body?.string().orEmpty()
            if (it.code !in 200..299) {
                throw AiProviderException.Unknown("Open-Meteo HTTP ${it.code}: ${responseBody.take(300)}")
            }
            json.decodeFromString(deserializer, responseBody)
        }
    }

    private companion object {
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    }
}
