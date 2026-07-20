package com.piotrek.oneagentarmy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val modelId: String,
    val pinned: Boolean = false,
    val lastMessageAt: Long,
)
