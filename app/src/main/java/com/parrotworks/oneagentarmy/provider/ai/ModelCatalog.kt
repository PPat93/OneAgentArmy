package com.parrotworks.oneagentarmy.provider.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Remote model catalog: a models.json file hosted in the app's own GitHub repo, fetched
// on demand so model lists and prices can be updated by editing that file - no app
// release needed. See mergeCatalog for how it is combined with the built-in registry.

@Serializable
data class ModelCatalog(
    val schemaVersion: Int,
    // Informational, shown in Settings after a successful refresh ("2026-07-23").
    val updatedAt: String? = null,
    val providers: List<CatalogProvider> = emptyList(),
)

@Serializable
data class CatalogProvider(
    val id: String,
    val models: List<CatalogModel> = emptyList(),
)

@Serializable
data class CatalogModel(
    val id: String,
    val label: String,
    val labelPl: String? = null,
    val shortLabel: String,
    val inputUsdPerMTok: Double,
    val outputUsdPerMTok: Double,
    // Prompt-caching rates. Optional: omitting them (or a provider dropping its
    // caching discount entirely) prices cached tokens at the full input rate, which
    // is what the app did before caching existed - so a catalog written for an older
    // app version keeps working and can never under-report a bill.
    val cachedInputUsdPerMTok: Double? = null,
    val cacheWriteUsdPerMTok: Double? = null,
    val supportsHostedWebSearch: Boolean = false,
)

fun CatalogModel.toOption() = AiModelOption(
    id = id,
    label = label,
    labelPl = labelPl,
    shortLabel = shortLabel,
    inputUsdPerMTok = inputUsdPerMTok,
    outputUsdPerMTok = outputUsdPerMTok,
    // A nonsensical (negative) cache rate is dropped rather than failing the whole
    // model - it falls back to the full input price instead.
    cachedInputUsdPerMTok = cachedInputUsdPerMTok?.takeIf { it >= 0.0 },
    cacheWriteUsdPerMTok = cacheWriteUsdPerMTok?.takeIf { it >= 0.0 },
    supportsHostedWebSearch = supportsHostedWebSearch,
)

// The schema version this app understands. A catalog with a different version is
// rejected as a whole - safer to keep the last known-good models than to half-parse
// a future format.
const val SUPPORTED_CATALOG_SCHEMA_VERSION = 1

private val catalogJson = Json { ignoreUnknownKeys = true }

// Throws on malformed JSON - callers decide how to surface that.
fun parseModelCatalog(jsonText: String): ModelCatalog =
    catalogJson.decodeFromString(ModelCatalog.serializer(), jsonText)
