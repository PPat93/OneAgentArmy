package com.piotrek.oneagentarmy.provider.ai.tools

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject,
    val strict: Boolean = true,
)
