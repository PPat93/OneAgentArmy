package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.data.local.AttachmentStore
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
    private val attachmentStore: AttachmentStore,
) : ConversationRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { entities -> entities.map { it.toDomain() } }

    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        dao.observeConversation(conversationId).map { it?.toDomain() }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        dao.observeMessages(conversationId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun createConversation(id: String, title: String, modelId: String) {
        val now = Instant.now()
        val conversation = Conversation(
            id = id,
            title = title,
            createdAt = now,
            modelId = modelId,
            pinned = false,
            lastMessageAt = now,
        )
        dao.insertConversation(conversation.toEntity())
    }

    override suspend fun addMessage(conversationId: String, message: Message) {
        dao.insertMessage(message.toEntity())
        dao.touchConversation(conversationId, message.timestamp.toEpochMilli())
    }

    override suspend fun deleteConversation(conversationId: String) {
        // Attachment file paths must be captured before the CASCADE wipes the rows.
        val attachmentPaths = dao.attachmentPathsForConversations(listOf(conversationId))
        dao.deleteConversation(conversationId)
        attachmentStore.deleteAll(attachmentPaths)
    }

    override suspend fun deleteConversations(conversationIds: List<String>) {
        if (conversationIds.isEmpty()) return
        val attachmentPaths = dao.attachmentPathsForConversations(conversationIds)
        dao.deleteConversations(conversationIds)
        attachmentStore.deleteAll(attachmentPaths)
    }

    override suspend fun renameConversation(conversationId: String, title: String) {
        dao.renameConversation(conversationId, title)
    }

    override suspend fun updateConversationModel(conversationId: String, modelId: String) {
        dao.updateConversationModel(conversationId, modelId)
    }

    override suspend fun setPinned(conversationId: String, pinned: Boolean) {
        dao.setPinned(conversationId, pinned)
    }

    override fun observeConversationCost(conversationId: String): Flow<Double?> =
        dao.observeConversationCost(conversationId)

    override fun observeCostSince(since: Instant): Flow<Double?> =
        dao.observeCostSince(since.toEpochMilli())

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
