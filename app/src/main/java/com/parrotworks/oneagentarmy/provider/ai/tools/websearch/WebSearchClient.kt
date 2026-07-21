package com.parrotworks.oneagentarmy.provider.ai.tools.websearch

data class WebSearchResult(
    val title: String,
    val url: String,
    val content: String,
)

interface WebSearchClient {
    suspend fun search(query: String, apiKey: String, maxResults: Int = 5): List<WebSearchResult>
}
