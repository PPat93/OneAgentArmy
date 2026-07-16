package com.piotrek.oneagentarmy.data.repository

import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Message
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun createConversation(title: String): Conversation
    fun addMessage(conversationId: String, message: Message)
}
