package com.piotrek.oneagentarmy.provider.ai.gemini.dto

import com.piotrek.oneagentarmy.provider.ai.tools.ToolDefinition
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
data class InteractionsRequest(
    val model: String,
    // Items are raw JsonElements on purpose: input mixes hand-built history steps with
    // model steps replayed verbatim (thought/function_call carry signatures that must be
    // resent unchanged), and tools mix function tools with hosted {"type":"google_search"}.
    val input: List<JsonElement>,
    @SerialName("system_instruction") val systemInstruction: String,
    val tools: List<JsonElement>? = null,
    // Nothing may be persisted server-side - the app is the only history store.
    val store: Boolean = false,
)

@Serializable
data class InteractionsResponse(
    val steps: List<JsonObject> = emptyList(),
)

@Serializable
data class GeminiErrorResponse(
    val error: GeminiErrorDetail? = null,
)

// Only message is modeled - the envelope's code field is a string URI in the
// Interactions API but numeric in older Google error formats; ignoreUnknownKeys
// skips it either way instead of risking a type mismatch.
@Serializable
data class GeminiErrorDetail(
    val message: String? = null,
)

data class GeminiFunctionCall(
    val id: String,
    val name: String,
    // JSON-encoded arguments object - Gemini delivers args as an object, callers
    // get the same string shape ToolCallRequest/argument parsers expect.
    val argumentsJson: String,
)

fun InteractionsResponse.firstFunctionCall(): GeminiFunctionCall? {
    val item = steps.firstOrNull { it.stringField("type") == "function_call" } ?: return null
    val arguments = item["arguments"] as? JsonObject ?: return null
    return GeminiFunctionCall(
        id = item.stringField("id") ?: return null,
        name = item.stringField("name") ?: return null,
        argumentsJson = arguments.toString(),
    )
}

fun InteractionsResponse.outputText(): String? =
    steps.asSequence()
        .filter { it.stringField("type") == "model_output" }
        .flatMap { step -> (step["content"] as? JsonArray).orEmpty() }
        .mapNotNull { part -> (part as? JsonObject)?.takeIf { it.stringField("type") == "text" } }
        .mapNotNull { it.stringField("text") }
        .joinToString("\n\n")
        .ifBlank { null }

fun userInputStep(text: String): JsonObject = historyStep("user_input", text)

fun modelOutputStep(text: String): JsonObject = historyStep("model_output", text)

fun functionResultStep(callId: String, result: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function_result"))
    put("id", JsonPrimitive(callId))
    put("result", JsonPrimitive(result))
}

fun functionToolJson(definition: ToolDefinition): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(definition.name))
    put("description", JsonPrimitive(definition.description))
    // Gemini accepts only a subset of JSON Schema - additionalProperties is not part
    // of it, so it is stripped everywhere; strict has no equivalent and is dropped.
    put("parameters", definition.parametersSchema.withoutAdditionalProperties())
}

fun googleSearchToolJson(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("google_search"))
}

private fun historyStep(type: String, text: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive(type))
    put(
        "content",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(text))
                },
            )
        },
    )
}

private fun JsonObject.withoutAdditionalProperties(): JsonObject = buildJsonObject {
    for ((key, value) in this@withoutAdditionalProperties) {
        if (key == "additionalProperties") continue
        put(key, value.stripAdditionalProperties())
    }
}

private fun JsonElement.stripAdditionalProperties(): JsonElement = when (this) {
    is JsonObject -> withoutAdditionalProperties()
    is JsonArray -> JsonArray(map { it.stripAdditionalProperties() })
    else -> this
}

private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
