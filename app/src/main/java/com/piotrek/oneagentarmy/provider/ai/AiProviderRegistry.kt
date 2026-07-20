package com.piotrek.oneagentarmy.provider.ai

import androidx.annotation.StringRes
import com.piotrek.oneagentarmy.R

data class AiModelOption(
    val id: String,
    @StringRes val labelRes: Int,
    val shortLabel: String,
    // USD per 1M tokens - keep in sync with the price shown in the label string.
    val inputUsdPerMTok: Double,
    val outputUsdPerMTok: Double,
    // Not every model supports the hosted web_search tool in the Responses API
    // (gpt-4.1-nano rejects it with HTTP 400) - models without support fall back
    // to the Tavily function tool even when hosted search is selected in settings.
    val supportsHostedWebSearch: Boolean = false,
)

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

    val providers = listOf(
        AiProviderInfo(
            id = OPENAI,
            displayName = "OpenAI (ChatGPT)",
            chipLabel = "ChatGPT",
            models = listOf(
                AiModelOption("gpt-4.1-nano", R.string.model_gpt41_nano, "4.1 nano", 0.10, 0.40),
                AiModelOption("gpt-5.6-luna", R.string.model_gpt56_luna, "5.6 Luna", 1.00, 6.00, supportsHostedWebSearch = true),
                AiModelOption("gpt-5.6-sol", R.string.model_gpt56_sol, "5.6 Sol", 5.00, 30.00, supportsHostedWebSearch = true),
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
                AiModelOption("gemini-3.1-flash-lite", R.string.model_gemini_31_flash_lite, "3.1 Lite", 0.25, 1.50),
                // The Gemini 3 (non-.5) series is published only under preview ids -
                // the bare "gemini-3-flash" alias 404s.
                AiModelOption("gemini-3-flash-preview", R.string.model_gemini_3_flash, "3 Flash", 0.50, 3.00, supportsHostedWebSearch = true),
                AiModelOption("gemini-3.5-flash", R.string.model_gemini_35_flash, "3.5 Flash", 1.50, 9.00, supportsHostedWebSearch = true),
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
                AiModelOption("claude-haiku-4-5", R.string.model_claude_haiku_45, "Haiku 4.5", 1.00, 5.00, supportsHostedWebSearch = true),
                // Intro pricing until Aug 2026 - bump to 3.00/15.00 afterwards (label too).
                AiModelOption("claude-sonnet-5", R.string.model_claude_sonnet_5, "Sonnet 5", 2.00, 10.00, supportsHostedWebSearch = true),
                AiModelOption("claude-opus-4-8", R.string.model_claude_opus_48, "Opus 4.8", 5.00, 25.00, supportsHostedWebSearch = true),
            ),
            isAvailable = true,
            taglineRes = R.string.provider_tagline_anthropic,
        ),
    )

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
