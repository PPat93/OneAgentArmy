package com.piotrek.oneagentarmy.provider.ai

import androidx.annotation.StringRes
import com.piotrek.oneagentarmy.R

data class AiModelOption(
    val id: String,
    @StringRes val labelRes: Int,
    val shortLabel: String,
    // Not every model supports the hosted web_search tool in the Responses API
    // (gpt-4.1-nano rejects it with HTTP 400) - models without support fall back
    // to the Tavily function tool even when hosted search is selected in settings.
    val supportsHostedWebSearch: Boolean = false,
)

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    val models: List<AiModelOption>,
    val isAvailable: Boolean,
    // Optional informational note rendered in the provider's settings card
    // (e.g. Gemini's free-tier/privacy explanation).
    @StringRes val noteRes: Int? = null,
)

object AiProviderRegistry {
    const val OPENAI = "openai"
    const val ANTHROPIC = "anthropic"
    const val GEMINI = "gemini"

    const val DEFAULT_MODEL = "gpt-4.1-nano"

    val providers = listOf(
        AiProviderInfo(
            id = OPENAI,
            displayName = "OpenAI",
            models = listOf(
                AiModelOption("gpt-4.1-nano", R.string.model_gpt41_nano, "4.1 nano"),
                AiModelOption("gpt-5.6-luna", R.string.model_gpt56_luna, "5.6 Luna", supportsHostedWebSearch = true),
                AiModelOption("gpt-5.6-sol", R.string.model_gpt56_sol, "5.6 Sol", supportsHostedWebSearch = true),
            ),
            isAvailable = true,
        ),
        AiProviderInfo(
            id = GEMINI,
            displayName = "Google (Gemini)",
            models = listOf(
                // Flash-Lite is absent from Google's list of grounding-capable models -
                // it falls back to the Tavily function tool for web search.
                AiModelOption("gemini-3.1-flash-lite", R.string.model_gemini_31_flash_lite, "3.1 Lite"),
                // The Gemini 3 (non-.5) series is published only under preview ids -
                // the bare "gemini-3-flash" alias 404s.
                AiModelOption("gemini-3-flash-preview", R.string.model_gemini_3_flash, "3 Flash", supportsHostedWebSearch = true),
                AiModelOption("gemini-3.5-flash", R.string.model_gemini_35_flash, "3.5 Flash", supportsHostedWebSearch = true),
            ),
            isAvailable = true,
            noteRes = R.string.gemini_free_tier_note,
        ),
        AiProviderInfo(
            id = ANTHROPIC,
            displayName = "Anthropic (Claude)",
            models = listOf(
                AiModelOption("claude-haiku-4-5", R.string.model_claude_haiku_45, "Haiku 4.5", supportsHostedWebSearch = true),
                AiModelOption("claude-sonnet-5", R.string.model_claude_sonnet_5, "Sonnet 5", supportsHostedWebSearch = true),
                AiModelOption("claude-opus-4-8", R.string.model_claude_opus_48, "Opus 4.8", supportsHostedWebSearch = true),
            ),
            isAvailable = true,
        ),
    )

    fun byId(id: String): AiProviderInfo? = providers.firstOrNull { it.id == id }

    fun modelOptionFor(modelId: String): AiModelOption? =
        providers.asSequence().flatMap { it.models }.firstOrNull { it.id == modelId }

    fun providerIdForModel(modelId: String): String =
        providers.firstOrNull { provider -> provider.models.any { it.id == modelId } }?.id ?: OPENAI

    fun shortLabelFor(modelId: String): String =
        modelOptionFor(modelId)?.shortLabel ?: modelId
}
