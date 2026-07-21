package com.parrotworks.oneagentarmy.data.repository

import com.parrotworks.oneagentarmy.model.Conversation
import com.parrotworks.oneagentarmy.model.Draft
import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.MessageSearchResult
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun observeDraft(conversationId: String): Flow<Draft?>
    suspend fun saveDraft(conversationId: String, draft: Draft)
    suspend fun clearDraft(conversationId: String)
    suspend fun createConversation(id: String, title: String, modelId: String)
    suspend fun addMessage(conversationId: String, message: Message)
    suspend fun deleteConversation(conversationId: String)
    suspend fun deleteConversations(conversationIds: List<String>)
    suspend fun renameConversation(conversationId: String, title: String)
    suspend fun updateConversationModel(conversationId: String, modelId: String)
    suspend fun setPinned(conversationId: String, pinned: Boolean)
    fun searchMessages(query: String): Flow<List<MessageSearchResult>>
    fun observeConversationCost(conversationId: String): Flow<Double?>
    fun observeCostSince(since: Instant): Flow<Double?>
    fun observeCostByProviderSince(since: Instant): Flow<Map<String, Double>>
}
