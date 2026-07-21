package com.parrotworks.oneagentarmy.provider.ai.tools.websearch

import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.tools.RoundTripToolExecutor

class WebSearchExecutor(
    private val webSearchClient: WebSearchClient,
    private val settingsRepository: SettingsRepository,
) : RoundTripToolExecutor {

    override val toolName = WEB_SEARCH_TOOL

    override suspend fun execute(argumentsJson: String): String {
        // The tool is only offered when the key exists (requiredKeyId gating), but the
        // key can be cleared mid-conversation - fail with a readable error, not a crash.
        val apiKey = settingsRepository.getApiKey(TAVILY_KEY_ID)
            ?: throw AiProviderException.Unknown("Web search key is no longer configured")
        val query = WebSearchArgumentsParser.parse(argumentsJson)
        val results = webSearchClient.search(query, apiKey)
        return formatResults(results)
    }

    private fun formatResults(results: List<WebSearchResult>): String =
        if (results.isEmpty()) {
            "No results found."
        } else {
            results.withIndex().joinToString("\n\n") { (index, result) ->
                "${index + 1}. ${result.title} (${result.url})\n${result.content}"
            }
        }
}
