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
    // Choices made before the conversation row exists to hold them (see Draft).
    val modelId: String? = null,
    val contextWindowOverride: Int? = null,
    // Comma-joined fact ids. These cannot live in conversation_facts: that table has a
    // foreign key to a conversations row which, by definition, does not exist yet. Nothing
    // can enforce referential integrity here either, so readers must tolerate an id whose
    // fact was deleted meanwhile.
    val factIds: String? = null,
)
