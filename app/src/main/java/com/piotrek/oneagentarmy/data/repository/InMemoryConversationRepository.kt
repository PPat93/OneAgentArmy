package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryConversationRepository : ConversationRepository {

    private val conversations = MutableStateFlow(sampleConversations())
    private val messagesByConversation = MutableStateFlow(sampleMessages())

    override fun observeConversations(): Flow<List<Conversation>> =
        conversations.asStateFlow()

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messagesByConversation.map { it[conversationId].orEmpty() }

    override fun createConversation(title: String): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = Instant.now(),
        )
        conversations.update { it + conversation }
        return conversation
    }

    override fun addMessage(conversationId: String, message: Message) {
        messagesByConversation.update { current ->
            val existing = current[conversationId].orEmpty()
            current + (conversationId to (existing + message))
        }
    }

    private fun sampleConversations(): List<Conversation> {
        val now = Instant.now()
        return listOf(
            Conversation(id = "sample-1", title = "Pomysły na wakacje", createdAt = now.minusSeconds(86_400)),
            Conversation(id = "sample-2", title = "Przepis na risotto", createdAt = now.minusSeconds(3_600)),
        )
    }

    private fun sampleMessages(): Map<String, List<Message>> {
        val now = Instant.now()
        return mapOf(
            "sample-1" to listOf(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = "sample-1",
                    sender = Sender.USER,
                    text = "Gdzie warto pojechać we wrześniu?",
                    timestamp = now.minusSeconds(86_400),
                ),
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = "sample-1",
                    sender = Sender.AI,
                    text = "Wrzesień to dobry czas na Grecję lub Portugalię - mniej tłumów i wciąż ciepło.",
                    timestamp = now.minusSeconds(86_390),
                ),
            ),
        )
    }
}
