package com.parrotworks.oneagentarmy.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val isGlobal: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "conversation_facts",
    primaryKeys = ["conversationId", "factId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FactEntity::class,
            parentColumns = ["id"],
            childColumns = ["factId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("factId")],
)
data class ConversationFactEntity(
    val conversationId: String,
    val factId: String,
)
