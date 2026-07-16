package com.piotrek.oneagentarmy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN modelId TEXT NOT NULL DEFAULT '${AiProviderRegistry.DEFAULT_MODEL}'",
                )
            }
        }
    }
}
