package com.piotrek.oneagentarmy.provider.ai.openai.dto

import com.piotrek.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ResponsesRequest(
    val model: String,
    val instructions: String,
    // Items are raw JsonElements on purpose: input mixes hand-built message items with
    // output items replayed verbatim (reasoning with encrypted_content, function_call),
    // and tools mix flattened function tools with hosted ones like {"type":"web_search"}.
    val input: List<JsonElement>,
    val tools: List<JsonElement>? = null,
    // Nothing may be persisted server-side - the app is the only history store.
    val store: Boolean = false,
    // gpt-4.1-nano may emit duplicate tool calls with parallel calls enabled -
    // explicitly disabled whenever tools are attached.
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    // With store=false, reasoning items can only be replayed on tool round-trips
    // if their encrypted content is returned to us.
    val include: List<String>? = null,
)

@Serializable
data class ResponsesResponse(
    val output: List<JsonObject> = emptyList(),
)

data class FunctionCallItem(
    val callId: String,
    val name: String,
    // JSON-encoded arguments object, delivered as a string by the API.
    val arguments: String,
)

fun ResponsesResponse.firstFunctionCall(): FunctionCallItem? {
    val item = output.firstOrNull { it.stringField("type") == "function_call" } ?: return null
    return FunctionCallItem(
        callId = item.stringField("call_id") ?: return null,
        name = item.stringField("name") ?: return null,
        arguments = item.stringField("arguments") ?: return null,
    )
}

fun ResponsesResponse.outputText(): String? =
    output.asSequence()
        .filter { it.stringField("type") == "message" }
        .flatMap { message -> (message["content"] as? JsonArray).orEmpty() }
        .mapNotNull { part -> (part as? JsonObject)?.takeIf { it.stringField("type") == "output_text" } }
        .mapNotNull { it.stringField("text") }
        .joinToString("\n\n")
        .ifBlank { null }

fun inputMessageItem(role: String, text: String): JsonObject = buildJsonObject {
    put("role", JsonPrimitive(role))
    put("content", JsonPrimitive(text))
}

fun functionCallOutputItem(callId: String, output: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function_call_output"))
    put("call_id", JsonPrimitive(callId))
    put("output", JsonPrimitive(output))
}

fun functionToolJson(definition: ToolDefinition): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(definition.name))
    put("description", JsonPrimitive(definition.description))
    put("parameters", definition.parametersSchema)
    put("strict", JsonPrimitive(definition.strict))
}

fun webSearchToolJson(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("web_search"))
}

private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
