package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AiReply
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ResponsesRequest
import com.piotrek.oneagentarmy.provider.ai.openai.dto.firstFunctionCall
import com.piotrek.oneagentarmy.provider.ai.openai.dto.functionCallOutputItem
import com.piotrek.oneagentarmy.provider.ai.openai.dto.functionToolJson
import com.piotrek.oneagentarmy.provider.ai.openai.dto.outputText
import com.piotrek.oneagentarmy.provider.ai.openai.dto.webSearchToolJson
import com.piotrek.oneagentarmy.provider.ai.tools.RoundTripToolExecutor
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.WEB_SEARCH_TOOL
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement

class OpenAiProvider(
    private val apiClient: OpenAiApiClient,
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    executors: List<RoundTripToolExecutor>,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AiProvider {

    private val executorsByName = executors.associateBy { it.toolName }

    override suspend fun sendMessage(history: List<Message>, modelId: String, contextFacts: List<String>): AiReply {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.OPENAI)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId

        // Hosted mode swaps the Tavily function tool for OpenAI's server-side web_search,
        // which needs no key and no round-trip on our side. Models that reject the hosted
        // tool (e.g. gpt-4.1-nano) fall back to Tavily regardless of the setting.
        val useHostedSearch =
            settingsRepository.observeSearchProvider().first() == SettingsRepository.SEARCH_PROVIDER_BUILT_IN &&
                AiProviderRegistry.modelOptionFor(modelId)?.supportsHostedWebSearch == true

        val functionTools = toolRegistry.definitions
            .filter { !(useHostedSearch && it.name == WEB_SEARCH_TOOL) }
            .filter { it.requiredKeyId == null || settingsRepository.getApiKey(it.requiredKeyId) != null }
            .map { functionToolJson(it) }
        val tools: List<JsonElement> =
            if (useHostedSearch) functionTools + webSearchToolJson() else functionTools

        val instructions = buildInstructions(contextFacts)
        var input: List<JsonElement> = history.map { it.toInputItem() }
        var roundTripsUsed = 0

        while (true) {
            // Hard cap on provider-executed tool round-trips per message: once used up,
            // the next request omits tools entirely, forcing a text answer.
            val toolsForThisRequest = if (roundTripsUsed >= MAX_TOOL_ROUND_TRIPS) null else tools.ifEmpty { null }

            val request = ResponsesRequest(
                model = modelId,
                instructions = instructions,
                input = input,
                tools = toolsForThisRequest,
                parallelToolCalls = if (toolsForThisRequest == null) null else false,
                include = listOf("reasoning.encrypted_content"),
            )
            val response = apiClient.createResponse(apiKey, request)

            val functionCall = response.firstFunctionCall()
            if (functionCall == null) {
                val replyText = response.outputText()
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

            val executor = executorsByName[functionCall.name]
            if (executor != null && roundTripsUsed < MAX_TOOL_ROUND_TRIPS) {
                roundTripsUsed++
                val result = executor.execute(functionCall.arguments)
                // All output items are replayed verbatim - with store=false, reasoning
                // models require their reasoning items (encrypted_content) back on the
                // next turn, and the function_call item must precede its output.
                input = input + response.output + functionCallOutputItem(functionCall.callId, result)
                continue
            }

            // Tools without an executor (calendar, alarms, SMS...) have a client-side
            // effect the ViewModel must confirm with the user - handed back unresolved.
            return AiReply.ToolCall(ToolCallRequest(functionCall.name, functionCall.arguments))
        }
    }

    private fun buildInstructions(contextFacts: List<String>): String {
        val now = LocalDateTime.now(clock)
        val dayOfWeek = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val formatted = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val factsSection = if (contextFacts.isEmpty()) {
            ""
        } else {
            "\n\nFacts about the user, provided by the user themselves - take them into account:\n" +
                contextFacts.withIndex().joinToString("\n") { (i, fact) -> "${i + 1}) $fact" }
        }
        return "Current date and time: $formatted ($dayOfWeek), timezone ${clock.zone}. " +
            "Resolve all relative dates ('tomorrow', 'jutro', 'next Friday') against this. " +
            "When the user asks to schedule a calendar event, call create_calendar_event. " +
            "Only include attendee emails the user explicitly provided; if they name a person " +
            "without an email address, ask for the address instead of calling the tool. " +
            "Use the other tools when the user asks for those actions: alarms, timers, " +
            "SMS drafts, navigation, opening the calendar at a date, weather forecasts, " +
            "saving a note. " +
            "Use web search only when the question genuinely needs current, real-time, or " +
            "recent information - answer from your own knowledge otherwise. You may search " +
            "more than once per message: if the first results are too shallow, " +
            "off-topic, or don't fully answer the question, refine the query and search again " +
            "rather than settling for a weak answer. " +
            "Otherwise answer normally, in the user's language." +
            factsSection
    }

    private companion object {
        const val MAX_TOOL_ROUND_TRIPS = 4
    }
}
