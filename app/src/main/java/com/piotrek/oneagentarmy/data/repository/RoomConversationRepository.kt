package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.data.local.ConversationDao
import com.piotrek.oneagentarmy.data.local.normalizeForSearch
import com.piotrek.oneagentarmy.data.local.toDomain
import com.piotrek.oneagentarmy.data.local.toEntity
import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.MessageSearchResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationRepository(
    private val dao: ConversationDao,
) : ConversationRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { entities -> entities.map { it.toDomain() } }

    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        dao.observeConversation(conversationId).map { it?.toDomain() }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        dao.observeMessages(conversationId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun createConversation(id: String, title: String, modelId: String) {
        val conversation = Conversation(id = id, title = title, createdAt = Instant.now(), modelId = modelId)
        dao.insertConversation(conversation.toEntity())
    }

    override suspend fun addMessage(conversationId: String, message: Message) {
        dao.insertMessage(message.toEntity())
    }

    override suspend fun deleteConversation(conversationId: String) {
        dao.deleteConversation(conversationId)
    }

    override suspend fun renameConversation(conversationId: String, title: String) {
        dao.renameConversation(conversationId, title)
    }

    override suspend fun updateConversationModel(conversationId: String, modelId: String) {
        dao.updateConversationModel(conversationId, modelId)
    }

    override fun searchMessages(query: String): Flow<List<MessageSearchResult>> {
        val normalizedEscaped = normalizeForSearch(query)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.searchMessages(normalizedEscaped).map { rows ->
            rows.map { MessageSearchResult(message = it.message.toDomain(), conversationTitle = it.conversationTitle) }
        }
    }
}
