package com.parrotworks.oneagentarmy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, FactEntity::class, ConversationFactEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun factDao(): FactDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Frozen literal: migrations must never change, so the default-model
                // value from the time this migration shipped is inlined here.
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN modelId TEXT NOT NULL DEFAULT 'gpt-4.1-nano'",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS facts (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "isGlobal INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS conversation_facts (" +
                        "conversationId TEXT NOT NULL, " +
                        "factId TEXT NOT NULL, " +
                        "PRIMARY KEY(conversationId, factId), " +
                        "FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(factId) REFERENCES facts(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conversation_facts_factId ON conversation_facts (factId)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN inputTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN outputTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN costUsd REAL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentType TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentPath TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentMime TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentName TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessageAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE conversations SET lastMessageAt = COALESCE(" +
                        "(SELECT MAX(timestamp) FROM messages WHERE messages.conversationId = conversations.id), createdAt)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN textNormalized TEXT NOT NULL DEFAULT ''")
                // Backfill existing rows - normalization lives in Kotlin, not SQL.
                db.query("SELECT id, text FROM messages").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "UPDATE messages SET textNormalized = ? WHERE id = ?",
                            arrayOf(normalizeForSearch(cursor.getString(1)), cursor.getString(0)),
                        )
                    }
                }
            }
        }
    }
}
