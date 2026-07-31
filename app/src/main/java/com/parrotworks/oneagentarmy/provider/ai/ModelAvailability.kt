package com.parrotworks.oneagentarmy.provider.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Parsing for the three providers' "list models" endpoints, used to warn when a model
// in the catalog is no longer listed by its provider (likely retired). Warning-only by
// design: listings can lag reality (Gemini preview ids sometimes don't appear while
// still working), so a missing entry must never auto-remove a model.

@Serializable
private data class OpenAiModelsResponse(val data: List<OpenAiModelEntry> = emptyList())

@Serializable
private data class OpenAiModelEntry(val id: String)

@Serializable
private data class AnthropicModelsResponse(val data: List<AnthropicModelEntry> = emptyList())

@Serializable
private data class AnthropicModelEntry(val id: String)

@Serializable
private data class GeminiModelsResponse(val models: List<GeminiModelEntry> = emptyList())

@Serializable
private data class GeminiModelEntry(val name: String)

private val availabilityJson = Json { ignoreUnknownKeys = true }

// Throws on malformed JSON - callers decide how to surface that.
fun parseAvailableModelIds(providerId: String, body: String): Set<String> = when (providerId) {
    AiProviderRegistry.OPENAI ->
        availabilityJson.decodeFromString(OpenAiModelsResponse.serializer(), body)
            .data.map { it.id }.toSet()
    AiProviderRegistry.ANTHROPIC ->
        availabilityJson.decodeFromString(AnthropicModelsResponse.serializer(), body)
            .data.map { it.id }.toSet()
    AiProviderRegistry.GEMINI ->
        // Gemini names are prefixed ("models/gemini-3.6-flash") - stripped so they
        // compare against the bare ids the registry uses.
        availabilityJson.decodeFromString(GeminiModelsResponse.serializer(), body)
            .models.map { it.name.removePrefix("models/") }.toSet()
    else -> emptySet()
}

// Providers list concrete model ids, while the app (like the provider docs) uses
// aliases. Anthropic publishes "claude-haiku-4-5-20251001" but the alias you actually
// call is "claude-haiku-4-5" - an exact-match check reports that as retired even though
// it works perfectly. So an id also counts as present when the listing carries a dated
// snapshot of it. Erring toward "present" is the right bias here: a missed retirement
// costs one confusing error later, a false alarm costs trust in every warning.
private val DATED_SNAPSHOT_SUFFIX = Regex("""-\d{8}$""")

fun missingModelIds(registryModels: List<AiModelOption>, availableIds: Set<String>): List<String> {
    val aliasesOfListedSnapshots = availableIds.mapNotNullTo(mutableSetOf()) { listed ->
        DATED_SNAPSHOT_SUFFIX.find(listed)?.let { match -> listed.removeRange(match.range) }
    }
    return registryModels.map { it.id }
        .filter { it !in availableIds && it !in aliasesOfListedSnapshots }
}
