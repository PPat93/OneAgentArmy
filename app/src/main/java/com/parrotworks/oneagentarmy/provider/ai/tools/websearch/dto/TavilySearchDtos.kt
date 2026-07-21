package com.parrotworks.oneagentarmy.provider.ai.tools.websearch.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TavilySearchRequest(
    val query: String,
    @SerialName("max_results") val maxResults: Int = 5,
    @SerialName("search_depth") val searchDepth: String = "basic",
)

@Serializable
data class TavilySearchResponse(
    val results: List<TavilyResultDto> = emptyList(),
)

@Serializable
data class TavilyResultDto(
    val title: String,
    val url: String,
    val content: String,
)
