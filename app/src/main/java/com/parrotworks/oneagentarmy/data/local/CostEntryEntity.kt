package com.parrotworks.oneagentarmy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Deliberately has no foreign key to conversations: this is an append-only ledger of
// money actually spent, independent of whatever local chat history still exists. Deleting
// a conversation must not un-spend the cost of the AI replies it contained.
@Entity(tableName = "cost_entries")
data class CostEntryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val providerId: String,
    val costUsd: Double,
)
