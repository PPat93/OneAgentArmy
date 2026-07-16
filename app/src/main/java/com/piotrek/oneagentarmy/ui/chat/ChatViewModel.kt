package com.piotrek.oneagentarmy.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategy
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatError {
    data object MissingApiKey : ChatError
    data class InvalidApiKey(val detail: String?) : ChatError
    data object NoConnectivity : ChatError
    data class RateLimited(val retryAfterSeconds: Int?, val detail: String?) : ChatError
    data class ServerError(val statusCode: Int, val detail: String?) : ChatError
    data class Unknown(val detail: String) : ChatError
}

class ChatViewModel(
    private val conversationId: String,
    private val repository: ConversationRepository,
    private val aiProvider: AiProvider,
    private val contextWindowStrategy: ContextWindowStrategy,
) : ViewModel() {

    val messages: StateFlow<List<Message>> = repository.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversationTitle: StateFlow<String?> = repository.observeConversation(conversationId)
        .map { it?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<ChatError?>(null)
    val error: StateFlow<ChatError?> = _error.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _error.value = null

            if (messages.value.isEmpty()) {
                repository.createConversation(conversationId, deriveTitle(text))
            }

            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = Sender.USER,
                text = text,
                timestamp = Instant.now(),
            )
            repository.addMessage(conversationId, userMessage)

            _isSending.value = true
            try {
                val historyToSend = contextWindowStrategy.apply(messages.value + userMessage)
                val aiMessage = aiProvider.sendMessage(historyToSend)
                repository.addMessage(conversationId, aiMessage)
            } catch (e: AiProviderException) {
                _error.value = e.toChatError()
            } finally {
                _isSending.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}

private fun deriveTitle(messageText: String): String {
    val singleLine = messageText.replace('\n', ' ').trim()
    return if (singleLine.length <= 50) singleLine else singleLine.take(50) + "…"
}

private fun AiProviderException.toChatError(): ChatError = when (this) {
    is AiProviderException.MissingApiKey -> ChatError.MissingApiKey
    is AiProviderException.InvalidApiKey -> ChatError.InvalidApiKey(detail)
    is AiProviderException.NoConnectivity -> ChatError.NoConnectivity
    is AiProviderException.RateLimited -> ChatError.RateLimited(retryAfterSeconds, detail)
    is AiProviderException.ServerError -> ChatError.ServerError(statusCode, detail)
    is AiProviderException.Unknown -> ChatError.Unknown(detail)
}
