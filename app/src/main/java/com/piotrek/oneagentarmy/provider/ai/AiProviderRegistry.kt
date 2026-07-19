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
            ),
            isAvailable = true,
        ),
        AiProviderInfo(
            id = GEMINI,
            displayName = "Google (Gemini)",
            models = listOf(
                // Combining google_search with custom function tools requires Gemini 3.x -
                // 2.5 models fall back to the Tavily function tool.
                AiModelOption("gemini-2.5-flash", R.string.model_gemini_25_flash, "2.5 Flash"),
                AiModelOption("gemini-3.5-flash", R.string.model_gemini_35_flash, "3.5 Flash", supportsHostedWebSearch = true),
            ),
            isAvailable = true,
        ),
        // Placeholder - keys can already be stored, but the provider can't be
        // activated until a real client implementation lands (flip isAvailable then).
        AiProviderInfo(id = ANTHROPIC, displayName = "Anthropic (Claude)", models = emptyList(), isAvailable = false),
    )

    fun byId(id: String): AiProviderInfo? = providers.firstOrNull { it.id == id }

    fun modelOptionFor(modelId: String): AiModelOption? =
        providers.asSequence().flatMap { it.models }.firstOrNull { it.id == modelId }

    fun providerIdForModel(modelId: String): String =
        providers.firstOrNull { provider -> provider.models.any { it.id == modelId } }?.id ?: OPENAI

    fun shortLabelFor(modelId: String): String =
        modelOptionFor(modelId)?.shortLabel ?: modelId
}
