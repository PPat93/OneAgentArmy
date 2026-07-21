package com.parrotworks.oneagentarmy.provider.ai.tools.websearch

import com.parrotworks.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val WEB_SEARCH_TOOL = "web_search"

// Same generic key-storage id space as AiProviderRegistry providers (SettingsRepository is
// keyed by an arbitrary string), but deliberately not registered as a provider itself -
// Tavily is an auxiliary tool credential, not a selectable chat model.
const val TAVILY_KEY_ID = "tavily"

private val PARAMETERS_SCHEMA = Json.parseToJsonElement(
    """
    {
        "type": "object",
        "properties": {
            "query": { "type": "string", "description": "The search query" }
        },
        "required": ["query"],
        "additionalProperties": false
    }
    """,
).jsonObject

val WebSearchToolDefinition = ToolDefinition(
    name = WEB_SEARCH_TOOL,
    description = "Search the web for current, real-time, or recent information (news, prices, " +
        "sports results, anything likely beyond your training data). Only call this when " +
        "the answer genuinely requires up-to-date information - answer from your own knowledge " +
        "otherwise. For weather forecasts prefer the get_weather tool.",
    parametersSchema = PARAMETERS_SCHEMA,
    requiredKeyId = TAVILY_KEY_ID,
)
