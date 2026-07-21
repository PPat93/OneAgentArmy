package com.parrotworks.oneagentarmy.provider.ai.anthropic

import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.AnthropicErrorResponse
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.MessagesRequest
import com.parrotworks.oneagentarmy.provider.ai.anthropic.dto.MessagesResponse
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicApiClient(
    private val okHttpClient: OkHttpClient,
) {
    // encodeDefaults for stable serialization of default fields;
    // explicitNulls=false so null fields (unused tools, tool_choice) are omitted.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun createMessage(apiKey: String, request: MessagesRequest): MessagesResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(MessagesRequest.serializer(), request)
                .toRequestBody(JSON_MEDIA_TYPE)

            val httpRequest = Request.Builder()
                .url(MESSAGES_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
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
                    in 200..299 -> json.decodeFromString(MessagesResponse.serializer(), responseBody)
                    401, 403 -> throw AiProviderException.InvalidApiKey(extractErrorDetail(it.code, responseBody))
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
            json.decodeFromString(AnthropicErrorResponse.serializer(), responseBody).error?.message
        } catch (e: Exception) {
            null
        }
        val detail = parsedMessage ?: responseBody.take(300).ifBlank { null }
        return if (detail != null) "HTTP $httpCode: $detail" else "HTTP $httpCode (empty response body)"
    }

    private companion object {
        const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
