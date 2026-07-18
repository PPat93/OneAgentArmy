package com.piotrek.oneagentarmy.provider.ai.tools.websearch

import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.dto.TavilySearchRequest
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.dto.TavilySearchResponse
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TavilyWebSearchClient(
    private val okHttpClient: OkHttpClient,
) : WebSearchClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, apiKey: String, maxResults: Int): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                TavilySearchRequest.serializer(),
                TavilySearchRequest(query = query, maxResults = maxResults),
            ).toRequestBody(JSON_MEDIA_TYPE)

            val httpRequest = Request.Builder()
                .url(SEARCH_URL)
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
                if (it.code !in 200..299) {
                    throw AiProviderException.Unknown("Tavily HTTP ${it.code}: ${responseBody.take(300)}")
                }
                json.decodeFromString(TavilySearchResponse.serializer(), responseBody)
                    .results.map { result -> WebSearchResult(result.title, result.url, result.content) }
            }
        }

    private companion object {
        const val SEARCH_URL = "https://api.tavily.com/search"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
