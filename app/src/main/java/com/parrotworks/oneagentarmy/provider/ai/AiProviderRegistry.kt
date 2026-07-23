package com.parrotworks.oneagentarmy.provider.ai

import androidx.annotation.StringRes
import com.parrotworks.oneagentarmy.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AiModelOption(
    val id: String,
    // Plain strings (not resources) so models can come from the remote catalog, which
    // has no way to reference Android string resources. English is the fallback;
    // labelPl is the optional Polish variant.
    val label: String,
    val labelPl: String? = null,
    val shortLabel: String,
    // USD per 1M tokens - keep in sync with the price shown in the label string.
    val inputUsdPerMTok: Double,
    val outputUsdPerMTok: Double,
    // Not every model supports the hosted web_search tool in the Responses API
    // (gpt-4.1-nano rejects it with HTTP 400) - models without support fall back
    // to the Tavily function tool even when hosted search is selected in settings.
    val supportsHostedWebSearch: Boolean = false,
) {
    fun labelFor(polish: Boolean): String = if (polish) labelPl ?: label else label
}

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    // Compact name for the provider chips on the conversation list screen.
    val chipLabel: String,
    // Convention: models are ordered cheapest-first; the first entry is the
    // default model for new conversations (see defaultModelFor).
    val models: List<AiModelOption>,
    val isAvailable: Boolean,
    // Short one-line characterization shown under the provider name (price/quality
    // tradeoff at a glance).
    @StringRes val taglineRes: Int,
    // Optional longer informational note rendered below the tagline
    // (e.g. Gemini's free-tier/privacy explanation).
    @StringRes val noteRes: Int? = null,
)

object AiProviderRegistry {
    const val OPENAI = "openai"
    const val ANTHROPIC = "anthropic"
    const val GEMINI = "gemini"

    // Compiled-in defaults: the fallback when no remote catalog has ever been fetched
    // (or the fetched one is unusable). The remote catalog can only replace the model
    // lists - provider identity/labels stay compiled in.
    val builtInProviders = listOf(
        AiProviderInfo(
            id = OPENAI,
            displayName = "OpenAI (ChatGPT)",
            chipLabel = "ChatGPT",
            models = listOf(
                AiModelOption(
                    id = "gpt-4.1-nano",
                    label = "GPT-4.1 nano - cheapest ($0.10 / $0.40 per 1M tokens)",
                    labelPl = "GPT-4.1 nano - najtańszy ($0.10 / $0.40 za 1M tokenów)",
                    shortLabel = "4.1 nano",
                    inputUsdPerMTok = 0.10,
                    outputUsdPerMTok = 0.40,
                ),
                AiModelOption(
                    id = "gpt-5.6-luna",
                    label = "GPT-5.6 Luna - better quality ($1.00 / $6.00 per 1M tokens)",
                    labelPl = "GPT-5.6 Luna - lepsza jakość ($1.00 / $6.00 za 1M tokenów)",
                    shortLabel = "5.6 Luna",
                    inputUsdPerMTok = 1.00,
                    outputUsdPerMTok = 6.00,
                    supportsHostedWebSearch = true,
                ),
                AiModelOption(
                    id = "gpt-5.6-sol",
                    label = "GPT-5.6 Sol - flagship ($5.00 / $30.00 per 1M tokens)",
                    labelPl = "GPT-5.6 Sol - flagowy ($5.00 / $30.00 za 1M tokenów)",
                    shortLabel = "5.6 Sol",
                    inputUsdPerMTok = 5.00,
                    outputUsdPerMTok = 30.00,
                    supportsHostedWebSearch = true,
                ),
            ),
            isAvailable = true,
            taglineRes = R.string.provider_tagline_openai,
        ),
        AiProviderInfo(
            id = GEMINI,
            displayName = "Google (Gemini)",
            chipLabel = "Gemini",
            models = listOf(
                // Flash-Lite is absent from Google's list of grounding-capable models -
                // it falls back to the Tavily function tool for web search.
                AiModelOption(
                    id = "gemini-3.1-flash-lite",
                    label = "Gemini 3.1 Flash-Lite - cheapest ($0.25 / $1.50 per 1M tokens, free tier eligible)",
                    labelPl = "Gemini 3.1 Flash-Lite - najtańszy ($0.25 / $1.50 za 1M tokenów, dostępny w darmowym tierze)",
                    shortLabel = "3.1 Lite",
                    inputUsdPerMTok = 0.25,
                    outputUsdPerMTok = 1.50,
                ),
                // The Gemini 3 (non-.5) series is published only under preview ids -
                // the bare "gemini-3-flash" alias 404s.
                AiModelOption(
                    id = "gemini-3-flash-preview",
                    label = "Gemini 3 Flash - balanced ($0.50 / $3.00 per 1M tokens, free tier eligible)",
                    labelPl = "Gemini 3 Flash - zbalansowany ($0.50 / $3.00 za 1M tokenów, dostępny w darmowym tierze)",
                    shortLabel = "3 Flash",
                    inputUsdPerMTok = 0.50,
                    outputUsdPerMTok = 3.00,
                    supportsHostedWebSearch = true,
                ),
                AiModelOption(
                    id = "gemini-3.5-flash",
                    label = "Gemini 3.5 Flash - better quality ($1.50 / $9.00 per 1M tokens)",
                    labelPl = "Gemini 3.5 Flash - lepsza jakość ($1.50 / $9.00 za 1M tokenów)",
                    shortLabel = "3.5 Flash",
                    inputUsdPerMTok = 1.50,
                    outputUsdPerMTok = 9.00,
                    supportsHostedWebSearch = true,
                ),
            ),
            isAvailable = true,
            taglineRes = R.string.provider_tagline_gemini,
            noteRes = R.string.gemini_free_tier_note,
        ),
        AiProviderInfo(
            id = ANTHROPIC,
            displayName = "Anthropic (Claude)",
            chipLabel = "Claude",
            models = listOf(
                AiModelOption(
                    id = "claude-haiku-4-5",
                    label = "Claude Haiku 4.5 - cheapest ($1.00 / $5.00 per 1M tokens)",
                    labelPl = "Claude Haiku 4.5 - najtańszy ($1.00 / $5.00 za 1M tokenów)",
                    shortLabel = "Haiku 4.5",
                    inputUsdPerMTok = 1.00,
                    outputUsdPerMTok = 5.00,
                    supportsHostedWebSearch = true,
                ),
                // Intro pricing until Aug 2026 - bump to 3.00/15.00 afterwards (label too),
                // ideally via the remote catalog rather than an app release.
                AiModelOption(
                    id = "claude-sonnet-5",
                    label = "Claude Sonnet 5 - better quality ($2.00 / $10.00 per 1M tokens until Aug 2026, then $3.00 / $15.00)",
                    labelPl = "Claude Sonnet 5 - lepsza jakość ($2.00 / $10.00 za 1M tokenów do sie 2026, potem $3.00 / $15.00)",
                    shortLabel = "Sonnet 5",
                    inputUsdPerMTok = 2.00,
                    outputUsdPerMTok = 10.00,
                    supportsHostedWebSearch = true,
                ),
                AiModelOption(
                    id = "claude-opus-4-8",
                    label = "Claude Opus 4.8 - flagship ($5.00 / $25.00 per 1M tokens)",
                    labelPl = "Claude Opus 4.8 - flagowy ($5.00 / $25.00 za 1M tokenów)",
                    shortLabel = "Opus 4.8",
                    inputUsdPerMTok = 5.00,
                    outputUsdPerMTok = 25.00,
                    supportsHostedWebSearch = true,
                ),
            ),
            isAvailable = true,
            taglineRes = R.string.provider_tagline_anthropic,
        ),
    )

    private val _providersFlow = MutableStateFlow(builtInProviders)

    // Reactive view for UI that must update live when a remote catalog is applied
    // (e.g. the model picker in an open chat).
    val providersFlow: StateFlow<List<AiProviderInfo>> = _providersFlow

    // Snapshot view for one-shot callers (cost estimation, provider routing).
    val providers: List<AiProviderInfo>
        get() = _providersFlow.value

    // Returns the ids of catalog models rejected by validation, so callers can surface
    // them instead of letting a bad edit silently shrink a model picker.
    fun applyRemoteCatalog(catalog: ModelCatalog): List<String> {
        val result = mergeCatalog(builtInProviders, catalog)
        _providersFlow.value = result.providers
        return result.droppedModelIds
    }

    fun byId(id: String): AiProviderInfo? = providers.firstOrNull { it.id == id }

    fun modelOptionFor(modelId: String): AiModelOption? =
        providers.asSequence().flatMap { it.models }.firstOrNull { it.id == modelId }

    fun providerIdForModel(modelId: String): String =
        providers.firstOrNull { provider -> provider.models.any { it.id == modelId } }?.id ?: OPENAI

    // Models are ordered cheapest-first, so the default is always the cheapest
    // model of the given provider (users pick stronger models per conversation).
    fun defaultModelFor(providerId: String): String =
        byId(providerId)?.models?.firstOrNull()?.id
            ?: providers.first().models.first().id

    fun shortLabelFor(modelId: String): String =
        modelOptionFor(modelId)?.shortLabel ?: modelId

    // Token-based estimate only: hosted web search fees (billed per query) and
    // cache discounts are not reflected; on Gemini's free tier the nominal cost
    // is shown even though nothing is billed.
    fun estimateCostUsd(modelId: String, usage: TokenUsage): Double? {
        val model = modelOptionFor(modelId) ?: return null
        return (usage.inputTokens * model.inputUsdPerMTok + usage.outputTokens * model.outputUsdPerMTok) / 1_000_000.0
    }
}

data class CatalogMergeResult(
    val providers: List<AiProviderInfo>,
    // Catalog models rejected by validation (blank id, negative price) - not applied,
    // but reported so the bad entry in models.json gets noticed and fixed.
    val droppedModelIds: List<String>,
)

// Pure so it can be unit-tested without mutating the global registry. Per provider:
// the remote model list wins only when it is present and has at least one valid model,
// otherwise the compiled-in list stays - a broken or partial catalog can never leave
// a provider with no models. Remote-only providers (unknown ids) are ignored.
fun mergeCatalog(builtIn: List<AiProviderInfo>, catalog: ModelCatalog): CatalogMergeResult {
    val dropped = mutableListOf<String>()
    val providers = builtIn.map { provider ->
        val remoteEntries = catalog.providers.firstOrNull { it.id == provider.id }?.models.orEmpty()
        val (valid, invalid) = remoteEntries.partition {
            it.id.isNotBlank() && it.inputUsdPerMTok >= 0.0 && it.outputUsdPerMTok >= 0.0
        }
        dropped += invalid.map { it.id.ifBlank { "(missing id)" } }
        if (valid.isEmpty()) provider else provider.copy(models = valid.map { it.toOption() })
    }
    return CatalogMergeResult(providers, dropped)
}
