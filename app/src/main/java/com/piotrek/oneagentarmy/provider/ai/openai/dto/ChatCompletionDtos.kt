package com.piotrek.oneagentarmy.provider.ai.openai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null,
)
