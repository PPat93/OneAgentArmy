package com.parrotworks.oneagentarmy.provider.ai.tools.weather

import com.parrotworks.oneagentarmy.provider.ai.tools.RoundTripToolExecutor
import com.parrotworks.oneagentarmy.provider.ai.tools.weather.dto.ForecastResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class WeatherArgs(val location: String, val days: Int = 1)

class WeatherExecutor(
    private val weatherClient: OpenMeteoWeatherClient,
) : RoundTripToolExecutor {

    private val json = Json { ignoreUnknownKeys = true }

    override val toolName = GET_WEATHER_TOOL

    override suspend fun execute(argumentsJson: String): String {
        val args = json.decodeFromString(WeatherArgs.serializer(), argumentsJson)
        val place = weatherClient.geocode(args.location)
            ?: return "Location '${args.location}' not found."
        val forecast = weatherClient.forecast(
            latitude = place.latitude,
            longitude = place.longitude,
            days = args.days.coerceIn(1, 7),
        )
        return formatForecast(place.name, place.country, forecast)
    }

    private fun formatForecast(name: String, country: String?, forecast: ForecastResponse): String {
        val lines = mutableListOf<String>()
        val location = if (country != null) "$name, $country" else name
        lines += "Weather for $location:"

        forecast.current?.let { current ->
            val parts = buildList {
                current.temperature?.let { add("temperature ${it}°C") }
                current.apparentTemperature?.let { add("feels like ${it}°C") }
                current.relativeHumidity?.let { add("humidity $it%") }
                current.windSpeed?.let { add("wind $it km/h") }
                current.weatherCode?.let { add(describeWeatherCode(it)) }
            }
            if (parts.isNotEmpty()) lines += "Now: ${parts.joinToString(", ")}"
        }

        forecast.daily?.let { daily ->
            daily.time.forEachIndexed { i, date ->
                val max = daily.temperatureMax.getOrNull(i)
                val min = daily.temperatureMin.getOrNull(i)
                val rain = daily.precipitationProbabilityMax.getOrNull(i)
                val code = daily.weatherCode.getOrNull(i)
                val parts = buildList {
                    if (min != null && max != null) add("$min°C to $max°C")
                    if (rain != null) add("precipitation chance $rain%")
                    if (code != null) add(describeWeatherCode(code))
                }
                if (parts.isNotEmpty()) lines += "$date: ${parts.joinToString(", ")}"
            }
        }

        return lines.joinToString("\n")
    }

    // WMO weather interpretation codes used by Open-Meteo.
    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67 -> "rain"
        71, 73, 75, 77 -> "snow"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95, 96, 99 -> "thunderstorm"
        else -> "weather code $code"
    }
}
