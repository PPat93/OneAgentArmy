package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.data.local.ConversationDao
import com.piotrek.oneagentarmy.data.local.toDomain
import com.piotrek.oneagentarmy.data.local.toEntity
import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Message
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationRepository(
    private val dao: ConversationDao,
) : ConversationRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { entities -> entities.map { it.toDomain() } }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        dao.observeMessages(conversationId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun createConversation(title: String): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = Instant.now(),
        )
        dao.insertConversation(conversation.toEntity())
        return conversation
    }

    override suspend fun addMessage(conversationId: String, message: Message) {
        dao.insertMessage(message.toEntity())
    }
}
