package com.parrotworks.oneagentarmy.provider.ai.anthropic.dto

import com.parrotworks.oneagentarmy.provider.ai.TokenUsage
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

@Serializable
data class MessagesRequest(
    val model: String,
    // Required by the Messages API; caps thinking + visible text combined.
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    // Messages are raw JsonElements on purpose: history entries are hand-built
    // {role, content-string} objects, while tool round-trips echo the assistant's
    // content blocks verbatim (thinking blocks must be resent unchanged).
    val messages: List<JsonElement>,
    val tools: List<JsonElement>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
)

@Serializable
data class MessagesResponse(
    val content: List<JsonObject> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
)

@Serializable
data class AnthropicUsage(
    // Thinking tokens are already included in output_tokens.
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
)

fun AnthropicUsage?.toTokenUsage(): TokenUsage =
    if (this == null) TokenUsage.ZERO else TokenUsage(inputTokens, outputTokens)

@Serializable
data class AnthropicErrorResponse(
    val error: AnthropicErrorDetail? = null,
)

@Serializable
data class AnthropicErrorDetail(
    val message: String? = null,
)

data class AnthropicToolUse(
    val id: String,
    val name: String,
    // JSON-encoded arguments object - the API delivers input as an object, callers
    // get the same string shape ToolCallRequest/argument parsers expect.
    val inputJson: String,
)

fun MessagesResponse.allToolUses(): List<AnthropicToolUse> =
    content.mapNotNull { block ->
        if (block.stringField("type") != "tool_use") return@mapNotNull null
        val input = block["input"] as? JsonObject ?: return@mapNotNull null
        AnthropicToolUse(
            id = block.stringField("id") ?: return@mapNotNull null,
            name = block.stringField("name") ?: return@mapNotNull null,
            inputJson = input.toString(),
        )
    }

fun MessagesResponse.outputText(): String? =
    content.asSequence()
        .filter { it.stringField("type") == "text" }
        .mapNotNull { it.stringField("text") }
        .joinToString("\n\n")
        .ifBlank { null }

fun historyMessage(role: String, text: String): JsonObject = buildJsonObject {
    put("role", JsonPrimitive(role))
    put("content", JsonPrimitive(text))
}

// User message carrying a media attachment: image/document source block first,
// then the (optional) text block.
fun historyMessageWithAttachment(
    text: String,
    attachmentBase64: String,
    mime: String,
    isPdf: Boolean,
): JsonObject = buildJsonObject {
    put("role", JsonPrimitive("user"))
    put(
        "content",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("type", JsonPrimitive(if (isPdf) "document" else "image"))
                    put(
                        "source",
                        buildJsonObject {
                            put("type", JsonPrimitive("base64"))
                            put("media_type", JsonPrimitive(mime))
                            put("data", JsonPrimitive(attachmentBase64))
                        },
                    )
                },
            )
            if (text.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(text))
                    },
                )
            }
        },
    )
}

// Echoes a response's content blocks back verbatim - required so thinking blocks
// (Sonnet) and server-tool blocks return unchanged on the next request.
fun assistantMessage(content: List<JsonObject>): JsonObject = buildJsonObject {
    put("role", JsonPrimitive("assistant"))
    put("content", JsonArray(content))
}

// The API rejects a follow-up unless every tool_use in the turn has a matching
// tool_result - all results go into a single user message.
fun toolResultsMessage(results: List<Pair<String, String>>): JsonObject = buildJsonObject {
    put("role", JsonPrimitive("user"))
    put(
        "content",
        buildJsonArray {
            results.forEach { (toolUseId, result) ->
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("tool_result"))
                        put("tool_use_id", JsonPrimitive(toolUseId))
                        put("content", JsonPrimitive(result))
                    },
                )
            }
        },
    )
}

// strict is skipped when the request also carries the dynamic-filtering web search
// (web_search_20260209) - its under-the-hood code execution rejects strict tools.
fun functionToolJson(definition: ToolDefinition, includeStrict: Boolean): JsonObject = buildJsonObject {
    put("name", JsonPrimitive(definition.name))
    put("description", JsonPrimitive(definition.description))
    put("input_schema", definition.parametersSchema)
    if (includeStrict) put("strict", JsonPrimitive(definition.strict))
}

// Server-side web search - the tool type variant differs per model
// (web_search_20260209 on Sonnet 5, web_search_20250305 on Haiku 4.5).
fun webSearchToolJson(type: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive(type))
    put("name", JsonPrimitive("web_search"))
}

// The loop handles one tool call at a time; parallel tool_use blocks would each
// demand a tool_result, so parallel calls are disabled outright.
fun toolChoiceAutoNoParallel(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("auto"))
    put("disable_parallel_tool_use", JsonPrimitive(true))
}

// Used once the round-trip cap is reached - tools must stay defined (history
// containing tool_use blocks without a tools param is rejected), so further
// calls are blocked via tool_choice instead.
fun toolChoiceNone(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("none"))
}

private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
