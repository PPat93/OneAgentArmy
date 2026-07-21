package com.parrotworks.oneagentarmy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {
    @Query("SELECT * FROM facts ORDER BY createdAt ASC")
    fun observeFacts(): Flow<List<FactEntity>>

    @Insert
    suspend fun insertFact(entity: FactEntity)

    @Update
    suspend fun updateFact(entity: FactEntity)

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun deleteFact(id: String)

    @Query("SELECT factId FROM conversation_facts WHERE conversationId = :conversationId")
    fun observeSelectedFactIds(conversationId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun selectFact(entity: ConversationFactEntity)

    @Query("DELETE FROM conversation_facts WHERE conversationId = :conversationId AND factId = :factId")
    suspend fun unselectFact(conversationId: String, factId: String)
}
