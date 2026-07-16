package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatCompletionRequest
import java.time.Instant
import java.util.UUID

class OpenAiProvider(
    private val apiClient: OpenAiApiClient,
    private val settingsRepository: SettingsRepository,
) : AiProvider {

    override suspend fun sendMessage(history: List<Message>, modelId: String): Message {
        val apiKey = settingsRepository.getApiKey(AiProviderRegistry.OPENAI)
        if (apiKey.isNullOrBlank()) throw AiProviderException.MissingApiKey

        val conversationId = history.last().conversationId

        val request = ChatCompletionRequest(
            model = modelId,
            messages = history.map { it.toDto() },
        )
        val response = apiClient.chatCompletion(apiKey, request)
        val replyText = response.choices.firstOrNull()?.message?.content
            ?: throw AiProviderException.Unknown("Empty response from OpenAI")

        return Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            sender = Sender.AI,
            text = replyText,
            timestamp = Instant.now(),
        )
    }
}
