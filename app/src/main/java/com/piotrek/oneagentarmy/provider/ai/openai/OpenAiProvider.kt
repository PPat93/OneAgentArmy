package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AiReply
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.provider.ai.tools.ToolRegistry
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatCompletionRequest
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatMessageDto
import com.piotrek.oneagentarmy.provider.ai.openai.dto.FunctionDto
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ToolDto
import java.time.Clock
import java.time.LocalDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

class OpenAiProvider(
    private val apiClient: OpenAiApiClient,
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AiProvider {

    override suspend fun sendMessage(history: List<Message>, modelId: String): AiReply {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.OPENAI)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId

        val tools = toolRegistry.definitions.map { definition ->
            ToolDto(
                function = FunctionDto(
                    name = definition.name,
                    description = definition.description,
                    parameters = definition.parametersSchema,
                    strict = definition.strict,
                ),
            )
        }

        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(systemMessage()) + history.map { it.toDto() },
            tools = tools.ifEmpty { null },
            parallelToolCalls = if (tools.isEmpty()) null else false,
        )
        val response = apiClient.chatCompletion(apiKey, request)
        val choiceMessage = response.choices.firstOrNull()?.message
            ?: throw AiProviderException.Unknown("Empty response from OpenAI")

        val toolCall = choiceMessage.toolCalls?.firstOrNull()
        if (toolCall != null) {
            return AiReply.ToolCall(ToolCallRequest(toolCall.function.name, toolCall.function.arguments))
        }

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
                "Otherwise answer normally, in the user's language.",
        )
    }
}
