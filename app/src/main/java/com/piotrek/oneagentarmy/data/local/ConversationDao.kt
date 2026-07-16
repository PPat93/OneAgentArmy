package com.piotrek.oneagentarmy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertConversation(entity: ConversationEntity)

    @Insert
    suspend fun insertMessage(entity: MessageEntity)
}
