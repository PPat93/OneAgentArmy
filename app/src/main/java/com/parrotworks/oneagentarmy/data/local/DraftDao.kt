package com.parrotworks.oneagentarmy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE conversationId = :conversationId")
    fun observeDraft(conversationId: String): Flow<DraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(entity: DraftEntity)

    @Query("DELETE FROM drafts WHERE conversationId = :conversationId")
    suspend fun deleteDraft(conversationId: String)
}
