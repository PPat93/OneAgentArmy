package com.parrotworks.oneagentarmy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import java.util.UUID

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        FactEntity::class,
        ConversationFactEntity::class,
        DraftEntity::class,
        CostEntryEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun factDao(): FactDao
    abstract fun draftDao(): DraftDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS drafts (" +
                        "conversationId TEXT NOT NULL PRIMARY KEY, " +
                        "text TEXT NOT NULL, " +
                        "attachmentKind TEXT, " +
                        "attachmentName TEXT, " +
                        "attachmentContent TEXT, " +
                        "attachmentMediaType TEXT, " +
                        "attachmentPath TEXT, " +
                        "attachmentMime TEXT)",
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cost_entries (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "timestamp INTEGER NOT NULL, " +
                        "providerId TEXT NOT NULL, " +
                        "costUsd REAL NOT NULL)",
                )
                // Backfill from existing message history so upgrading doesn't zero out
                // money already spent - provider resolution lives in Kotlin, not SQL.
                db.query(
                    "SELECT messages.timestamp, messages.costUsd, conversations.modelId " +
                        "FROM messages JOIN conversations ON conversations.id = messages.conversationId " +
                        "WHERE messages.costUsd IS NOT NULL",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val timestamp = cursor.getLong(0)
                        val costUsd = cursor.getDouble(1)
                        val modelId = cursor.getString(2)
                        db.execSQL(
                            "INSERT INTO cost_entries (id, timestamp, providerId, costUsd) VALUES (?, ?, ?, ?)",
                            arrayOf(
                                UUID.randomUUID().toString(),
                                timestamp,
                                AiProviderRegistry.providerIdForModel(modelId),
                                costUsd,
                            ),
                        )
                    }
                }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable, no DEFAULT needed - null means "use the global Settings value",
                // which is exactly what every existing conversation should keep meaning.
                db.execSQL("ALTER TABLE conversations ADD COLUMN contextWindowOverride INTEGER")
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
