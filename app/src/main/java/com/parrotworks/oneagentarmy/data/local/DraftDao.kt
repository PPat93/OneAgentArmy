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

    // Only a staged Media draft (photo/PDF) has a file on disk - a staged TextFile draft's
    // content lives inline in attachmentContent, nothing to clean up there.
    @Query("SELECT attachmentPath FROM drafts WHERE conversationId IN (:conversationIds) AND attachmentPath IS NOT NULL")
    suspend fun attachmentPathsForConversations(conversationIds: List<String>): List<String>

    @Query("DELETE FROM drafts WHERE conversationId IN (:conversationIds)")
    suspend fun deleteDrafts(conversationIds: List<String>)

    // A draft with no matching conversations row is either the one still-unsent "new
    // conversation" (kept alive on purpose - see SettingsRepository.pendingNewConversationId)
    // or a leftover from before that reuse mechanism existed, when every "New conversation"
    // tap minted a fresh id and stranded whatever was typed under the last one.
    @Query("SELECT conversationId FROM drafts WHERE conversationId NOT IN (SELECT id FROM conversations)")
    suspend fun orphanedConversationIds(): List<String>
}
