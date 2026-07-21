package com.parrotworks.oneagentarmy.provider.ai.gemini

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.GeminiErrorResponse
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.InteractionsRequest
import com.parrotworks.oneagentarmy.provider.ai.gemini.dto.InteractionsResponse
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiApiClient(
    private val okHttpClient: OkHttpClient,
) {
    // encodeDefaults so InteractionsRequest.store=false is serialized;
    // explicitNulls=false so null fields (unused tools) are omitted.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun createInteraction(apiKey: String, request: InteractionsRequest): InteractionsResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(InteractionsRequest.serializer(), request)
                .toRequestBody(JSON_MEDIA_TYPE)

            val httpRequest = Request.Builder()
                .url(INTERACTIONS_URL)
                .header("x-goog-api-key", apiKey)
                .post(body)
                .build()

            val response = try {
                okHttpClient.newCall(httpRequest).execute()
            } catch (e: IOException) {
                throw AiProviderException.NoConnectivity("${e.javaClass.simpleName}: ${e.message}")
            }

            response.use {
                val responseBody = it.body?.string().orEmpty()
                when (it.code) {
                    in 200..299 -> json.decodeFromString(InteractionsResponse.serializer(), responseBody)
                    401, 403 -> throw AiProviderException.InvalidApiKey(extractErrorDetail(it.code, responseBody))
                    // Gemini reports an invalid key as 400 API_KEY_INVALID, not 401.
                    400 -> {
                        val detail = extractErrorDetail(it.code, responseBody)
                        if (detail.contains("API key", ignoreCase = true)) {
                            throw AiProviderException.InvalidApiKey(detail)
                        } else {
                            throw AiProviderException.Unknown(detail)
                        }
                    }
                    429 -> throw AiProviderException.RateLimited(
                        retryAfterSeconds = it.header("Retry-After")?.toIntOrNull(),
                        detail = extractErrorDetail(it.code, responseBody),
                    )
                    in 500..599 -> throw AiProviderException.ServerError(it.code, extractErrorDetail(it.code, responseBody))
                    else -> throw AiProviderException.Unknown(extractErrorDetail(it.code, responseBody))
                }
            }
        }

    // Always yields something diagnosable: the server's own error message when the body
    // parses, otherwise the raw body snippet, otherwise just the HTTP status.
    private fun extractErrorDetail(httpCode: Int, responseBody: String): String {
        val parsedMessage = try {
            json.decodeFromString(GeminiErrorResponse.serializer(), responseBody).error?.message
        } catch (e: Exception) {
            null
        }
        val detail = parsedMessage ?: responseBody.take(300).ifBlank { null }
        return if (detail != null) "HTTP $httpCode: $detail" else "HTTP $httpCode (empty response body)"
    }

    private companion object {
        const val INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
