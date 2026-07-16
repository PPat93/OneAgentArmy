package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Message
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: String): Flow<Conversation?>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun createConversation(id: String, title: String)
    suspend fun addMessage(conversationId: String, message: Message)
    suspend fun deleteConversation(conversationId: String)
    suspend fun renameConversation(conversationId: String, title: String)
}
