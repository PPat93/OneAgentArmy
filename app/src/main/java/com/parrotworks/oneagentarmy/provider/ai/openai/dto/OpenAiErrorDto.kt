package com.parrotworks.oneagentarmy.provider.ai.openai.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenAiErrorResponse(val error: OpenAiErrorDetail? = null)

@Serializable
data class OpenAiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
