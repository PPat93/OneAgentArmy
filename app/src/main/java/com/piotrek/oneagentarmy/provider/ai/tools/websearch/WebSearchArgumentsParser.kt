package com.piotrek.oneagentarmy.provider.ai.tools.websearch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class WebSearchArgs(val query: String)

object WebSearchArgumentsParser {

    private val json = Json { ignoreUnknownKeys = true }

    // Throws on malformed input - caller maps failures to a user-facing error.
    fun parse(argumentsJson: String): String =
        json.decodeFromString(WebSearchArgs.serializer(), argumentsJson).query
}
