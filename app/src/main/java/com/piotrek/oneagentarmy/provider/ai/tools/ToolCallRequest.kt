package com.piotrek.oneagentarmy.provider.ai.tools

data class ToolCallRequest(
    val name: String,
    val argumentsJson: String,
)
