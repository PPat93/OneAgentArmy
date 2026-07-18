package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AiReply
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatCompletionRequest
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatMessageDto
import com.piotrek.oneagentarmy.provider.ai.openai.dto.FunctionDto
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ToolDto
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.TAVILY_KEY_ID
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WEB_SEARCH_TOOL
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WebSearchArgumentsParser
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WebSearchClient
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WebSearchResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

class OpenAiProvider(
    private val apiClient: OpenAiApiClient,
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    private val webSearchClient: WebSearchClient,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AiProvider {

    override suspend fun sendMessage(history: List<Message>, modelId: String): AiReply {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.OPENAI)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId
        val tavilyApiKey = settingsRepository.getApiKey(TAVILY_KEY_ID)

        val tools = toolRegistry.definitions
            .filter { it.name != WEB_SEARCH_TOOL || tavilyApiKey != null }
            .map { definition ->
                ToolDto(
                    function = FunctionDto(
                        name = definition.name,
                        description = definition.description,
                        parameters = definition.parametersSchema,
                        strict = definition.strict,
                    ),
                )
            }

        var messages = listOf(systemMessage()) + history.map { it.toDto() }
        var searchesUsed = 0
        val disableReasoningForTools =
            AiProviderRegistry.modelOptionFor(modelId)?.disableReasoningForTools == true

        while (true) {
            // Hard cap of a few search round-trips per message: once used up, the next
            // request omits tools entirely, forcing the model to answer with what it has.
            val toolsForThisRequest = if (searchesUsed >= MAX_SEARCHES_PER_MESSAGE) null else tools.ifEmpty { null }

            val request = ChatCompletionRequest(
                model = modelId,
                messages = messages,
                tools = toolsForThisRequest,
                parallelToolCalls = if (toolsForThisRequest == null) null else false,
                reasoningEffort = if (toolsForThisRequest != null && disableReasoningForTools) "none" else null,
            )
            val response = apiClient.chatCompletion(apiKey, request)
            val choiceMessage = response.choices.firstOrNull()?.message
                ?: throw AiProviderException.Unknown("Empty response from OpenAI")

            val toolCall = choiceMessage.toolCalls?.firstOrNull()
            if (toolCall == null) {
                val replyText = choiceMessage.content
                    ?: throw AiProviderException.Unknown("Response contained neither text nor a tool call")
                return AiReply.Text(
                    Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        sender = Sender.AI,
                        text = replyText,
                        timestamp = Instant.now(),
                    ),
                )
            }

            if (toolCall.function.name == WEB_SEARCH_TOOL && tavilyApiKey != null && searchesUsed < MAX_SEARCHES_PER_MESSAGE) {
                searchesUsed++
                val query = WebSearchArgumentsParser.parse(toolCall.function.arguments)
                val results = webSearchClient.search(query, tavilyApiKey)
                messages = messages +
                    ChatMessageDto(role = "assistant", toolCalls = listOf(toolCall)) +
                    ChatMessageDto(role = "tool", toolCallId = toolCall.id, content = formatSearchResults(results))
                continue
            }

            // Any other tool (e.g. the calendar) has a client-side effect the ViewModel
            // must confirm with the user - handed back unresolved, exactly as in Stage 7.
            return AiReply.ToolCall(ToolCallRequest(toolCall.function.name, toolCall.function.arguments))
        }
    }

    private fun formatSearchResults(results: List<WebSearchResult>): String =
        if (results.isEmpty()) {
            "No results found."
        } else {
            results.withIndex().joinToString("\n\n") { (index, result) ->
                "${index + 1}. ${result.title} (${result.url})\n${result.content}"
            }
        }

    private fun systemMessage(): ChatMessageDto {
        val now = LocalDateTime.now(clock)
        val dayOfWeek = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val formatted = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return ChatMessageDto(
            role = "system",
            content = "Current date and time: $formatted ($dayOfWeek), timezone ${clock.zone}. " +
                "Resolve all relative dates ('tomorrow', 'jutro', 'next Friday') against this. " +
                "When the user asks to schedule a calendar event, call create_calendar_event. " +
                "Only include attendee emails the user explicitly provided; if they name a person " +
                "without an email address, ask for the address instead of calling the tool. " +
                "Use web_search only when the question genuinely needs current, real-time, or " +
                "recent information - answer from your own knowledge otherwise. You may call " +
                "web_search more than once per message: if the first results are too shallow, " +
                "off-topic, or don't fully answer the question, refine the query and search again " +
                "rather than settling for a weak answer. " +
                "Otherwise answer normally, in the user's language.",
        )
    }

    private companion object {
        const val MAX_SEARCHES_PER_MESSAGE = 4
    }
}
