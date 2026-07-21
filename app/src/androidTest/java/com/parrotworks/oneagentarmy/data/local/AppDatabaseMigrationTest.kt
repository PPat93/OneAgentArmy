package com.parrotworks.oneagentarmy.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Exercises a real migration against the exported schema JSON (app/schemas), rather than
// just letting Room build the latest schema fresh - this is the only migration in the app
// that can be tested this way so far, since schemas/7.json is the first exported schema
// that exists (exportSchema was only turned on starting from version 7).
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8_createsDraftsTableUsableWithoutAConversationRow() {
        helper.createDatabase(TEST_DB_NAME, 7).close()

        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 8, true, AppDatabase.MIGRATION_7_8)

        // No FK to conversations - a draft for a not-yet-created (new) conversation must
        // be insertable on its own.
        db.execSQL(
            "INSERT INTO drafts (conversationId, text, attachmentKind, attachmentName, " +
                "attachmentContent, attachmentMediaType, attachmentPath, attachmentMime) " +
                "VALUES ('unsent-conversation-id', 'hello there', NULL, NULL, NULL, NULL, NULL, NULL)",
        )
        db.query("SELECT text FROM drafts WHERE conversationId = 'unsent-conversation-id'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("hello there", cursor.getString(0))
        }
    }

    @Test
    fun migrate8To9_backfillsLedgerAndSurvivesConversationDeletion() {
        helper.createDatabase(TEST_DB_NAME, 8).apply {
            execSQL(
                "INSERT INTO conversations (id, title, createdAt, modelId, pinned, lastMessageAt) " +
                    "VALUES ('convo-1', 'Test', 1000, 'gpt-4.1-nano', 0, 1000)",
            )
            execSQL(
                "INSERT INTO messages (id, conversationId, sender, text, textNormalized, timestamp, costUsd) " +
                    "VALUES ('msg-1', 'convo-1', 'AI', 'hi', 'hi', 1000, 0.05)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 9, true, AppDatabase.MIGRATION_8_9)

        db.query("SELECT providerId, costUsd FROM cost_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("openai", cursor.getString(0))
            assertEquals(0.05, cursor.getDouble(1), 0.0001)
        }

        // The whole point of the ledger: deleting the conversation and its cost-bearing
        // message (what the real app's cascading FK does at runtime - not re-tested here,
        // that's Room's own guarantee) must not erase the money already spent from the
        // record.
        db.execSQL("DELETE FROM messages WHERE conversationId = 'convo-1'")
        db.execSQL("DELETE FROM conversations WHERE id = 'convo-1'")
        db.query("SELECT COUNT(*) FROM cost_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB_NAME = "migration-test-app-db"
    }
}
