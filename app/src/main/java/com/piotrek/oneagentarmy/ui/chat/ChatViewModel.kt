package com.piotrek.oneagentarmy.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: String,
    private val repository: ConversationRepository,
) : ViewModel() {

    val messages: StateFlow<List<Message>> = repository.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Stage 1 has no real AiProvider wired in yet - this canned echo marks the seam
        // where a real OpenAI call will replace it in a later stage.
        viewModelScope.launch {
            repository.addMessage(
                conversationId,
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    sender = Sender.USER,
                    text = text,
                    timestamp = Instant.now(),
                ),
            )

            delay(500)
            repository.addMessage(
                conversationId,
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    sender = Sender.AI,
                    text = "Na razie tylko odbijam Twoją wiadomość: \"$text\" - prawdziwe API dołączymy w kolejnym etapie.",
                    timestamp = Instant.now(),
                ),
            )
        }
    }
}
