package com.piotrek.oneagentarmy.provider.ai.tools

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject,
    val strict: Boolean = true,
    // When set, the tool is only offered to the model if an API key is stored
    // under this id in SettingsRepository (e.g. Tavily for web search).
    val requiredKeyId: String? = null,
)
