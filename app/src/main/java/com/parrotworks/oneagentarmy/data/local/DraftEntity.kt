package com.parrotworks.oneagentarmy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// No foreign key to conversations - a draft can exist for a conversationId before that
// conversation's row is ever created (new, unsent chats).
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val attachmentKind: String?,
    val attachmentName: String?,
    val attachmentContent: String?,
    val attachmentMediaType: String?,
    val attachmentPath: String?,
    val attachmentMime: String?,
)
