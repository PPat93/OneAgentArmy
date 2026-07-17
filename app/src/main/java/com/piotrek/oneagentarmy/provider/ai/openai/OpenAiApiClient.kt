package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatCompletionRequest
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatCompletionResponse
import com.piotrek.oneagentarmy.provider.ai.openai.dto.OpenAiErrorResponse
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiApiClient(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chatCompletion(apiKey: String, request: ChatCompletionRequest): ChatCompletionResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody(JSON_MEDIA_TYPE)

            val httpRequest = Request.Builder()
                .url(CHAT_COMPLETIONS_URL)
                .header("Authorization", "Bearer $apiKey")
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
                    in 200..299 -> json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
                    401 -> throw AiProviderException.InvalidApiKey(extractErrorDetail(it.code, responseBody))
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
            json.decodeFromString(OpenAiErrorResponse.serializer(), responseBody).error?.message
        } catch (e: Exception) {
            null
        }
        val detail = parsedMessage ?: responseBody.take(300).ifBlank { null }
        return if (detail != null) "HTTP $httpCode: $detail" else "HTTP $httpCode (empty response body)"
    }

    private companion object {
        const val CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
