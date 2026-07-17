package com.piotrek.oneagentarmy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertConversation(entity: ConversationEntity)

    @Insert
    suspend fun insertMessage(entity: MessageEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: String, title: String)

    @Query("UPDATE conversations SET modelId = :modelId WHERE id = :id")
    suspend fun updateConversationModel(id: String, modelId: String)

    // Caller is responsible for escaping %, _ and \ in the query (see RoomConversationRepository).
    @Query(
        """
        SELECT messages.*, conversations.title AS conversationTitle
        FROM messages
        JOIN conversations ON conversations.id = messages.conversationId
        WHERE messages.text LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY messages.timestamp DESC
        """,
    )
    fun searchMessages(escapedQuery: String): Flow<List<MessageSearchRow>>
}
