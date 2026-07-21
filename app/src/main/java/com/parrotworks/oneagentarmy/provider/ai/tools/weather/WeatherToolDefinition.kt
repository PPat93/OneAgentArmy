package com.parrotworks.oneagentarmy.provider.ai.tools.weather

import com.parrotworks.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val GET_WEATHER_TOOL = "get_weather"

private val PARAMETERS_SCHEMA = Json.parseToJsonElement(
    """
    {
        "type": "object",
        "properties": {
            "location": { "type": "string", "description": "City or place name, e.g. 'Kraków'" },
            "days": { "type": "integer", "description": "Forecast days ahead needed, 1-7 (1 = today only)" }
        },
        "required": ["location", "days"],
        "additionalProperties": false
    }
    """,
).jsonObject

val WeatherToolDefinition = ToolDefinition(
    name = GET_WEATHER_TOOL,
    description = "Get the current weather and a daily forecast for a location. Call this whenever " +
        "the user asks about weather, temperature, rain, or forecast conditions.",
    parametersSchema = PARAMETERS_SCHEMA,
)
