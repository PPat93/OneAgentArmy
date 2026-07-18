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
import com.piotrek.oneagentarmy.provider.ai.tools.RoundTripToolExecutor
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
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
    executors: List<RoundTripToolExecutor>,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AiProvider {

    private val executorsByName = executors.associateBy { it.toolName }

    override suspend fun sendMessage(history: List<Message>, modelId: String): AiReply {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.OPENAI)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId

        val tools = toolRegistry.definitions
            .filter { it.requiredKeyId == null || settingsRepository.getApiKey(it.requiredKeyId) != null }
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
        var roundTripsUsed = 0
        val disableReasoningForTools =
            AiProviderRegistry.modelOptionFor(modelId)?.disableReasoningForTools == true

        while (true) {
            // Hard cap on provider-executed tool round-trips per message: once used up,
            // the next request omits tools entirely, forcing a text answer.
            val toolsForThisRequest = if (roundTripsUsed >= MAX_TOOL_ROUND_TRIPS) null else tools.ifEmpty { null }

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

            val executor = executorsByName[toolCall.function.name]
            if (executor != null && roundTripsUsed < MAX_TOOL_ROUND_TRIPS) {
                roundTripsUsed++
                val result = executor.execute(toolCall.function.arguments)
                messages = messages +
                    ChatMessageDto(role = "assistant", toolCalls = listOf(toolCall)) +
                    ChatMessageDto(role = "tool", toolCallId = toolCall.id, content = result)
                continue
            }

            // Tools without an executor (calendar, alarms, SMS...) have a client-side
            // effect the ViewModel must confirm with the user - handed back unresolved.
            return AiReply.ToolCall(ToolCallRequest(toolCall.function.name, toolCall.function.arguments))
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
                "Use the other tools when the user asks for those actions: alarms, timers, " +
                "SMS drafts, navigation, opening the calendar at a date, weather forecasts. " +
                "Use web_search only when the question genuinely needs current, real-time, or " +
                "recent information - answer from your own knowledge otherwise. You may call " +
                "web_search more than once per message: if the first results are too shallow, " +
                "off-topic, or don't fully answer the question, refine the query and search again " +
                "rather than settling for a weak answer. " +
                "Otherwise answer normally, in the user's language.",
        )
    }

    private companion object {
        const val MAX_TOOL_ROUND_TRIPS = 4
    }
}
