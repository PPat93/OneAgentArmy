package com.piotrek.oneagentarmy.provider.ai.openai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatMessageDto(
    val role: String,
    // Null on tool-call turns - the model returns tool_calls instead of text content.
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    // Only set on outgoing role:"tool" messages - the result of a tool call, keyed by
    // the id from the assistant's tool_calls entry it answers.
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val tools: List<ToolDto>? = null,
    // gpt-4.1-nano may emit duplicate tool calls with parallel calls enabled -
    // explicitly disabled whenever tools are attached.
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    // Reasoning models (e.g. gpt-5.6-luna) reject function tools in Chat Completions
    // unless this is "none"; sent only for models flagged in AiProviderRegistry.
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class ToolDto(
    val type: String = "function",
    val function: FunctionDto,
)

@Serializable
data class FunctionDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val strict: Boolean = true,
)

@Serializable
data class ToolCallDto(
    val id: String,
    val type: String,
    val function: FunctionCallDto,
)

@Serializable
data class FunctionCallDto(
    val name: String,
    // JSON-encoded arguments object, delivered as a string by the API.
    val arguments: String,
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
