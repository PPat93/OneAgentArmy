package com.parrotworks.oneagentarmy.provider.ai

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Fetches the set of model ids a provider currently lists for the given API key.
// One cheap GET per provider, on demand only (piggybacks on the catalog refresh).
class ModelAvailabilityChecker(
    private val okHttpClient: OkHttpClient,
) {
    sealed interface ProviderCheck {
        data class Available(val modelIds: Set<String>) : ProviderCheck
        data class Failed(val detail: String) : ProviderCheck
    }

    suspend fun listAvailable(providerId: String, apiKey: String): ProviderCheck =
        withContext(Dispatchers.IO) {
            val request = when (providerId) {
                AiProviderRegistry.OPENAI -> Request.Builder()
                    .url(OPENAI_MODELS_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                AiProviderRegistry.ANTHROPIC -> Request.Builder()
                    .url(ANTHROPIC_MODELS_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .build()
                AiProviderRegistry.GEMINI -> Request.Builder()
                    .url(GEMINI_MODELS_URL)
                    .header("x-goog-api-key", apiKey)
                    .build()
                else -> return@withContext ProviderCheck.Failed("Unknown provider: $providerId")
            }

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ProviderCheck.Failed("HTTP ${response.code}")
                    }
                    val ids = parseAvailableModelIds(providerId, response.body?.string().orEmpty())
                    ProviderCheck.Available(ids)
                }
            } catch (e: IOException) {
                ProviderCheck.Failed("${e.javaClass.simpleName}: ${e.message}")
            } catch (e: Exception) {
                ProviderCheck.Failed("Malformed response: ${e.message?.take(200)}")
            }
        }

    private companion object {
        const val OPENAI_MODELS_URL = "https://api.openai.com/v1/models"
        // limit/pageSize raised to the max so the paged default (20/50) can't hide models.
        const val ANTHROPIC_MODELS_URL = "https://api.anthropic.com/v1/models?limit=1000"
        const val GEMINI_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
        // Mirrors AnthropicApiClient.ANTHROPIC_VERSION (private there).
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
