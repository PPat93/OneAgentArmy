package com.parrotworks.oneagentarmy.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parrotworks.oneagentarmy.data.local.AppDatabase
import com.parrotworks.oneagentarmy.data.local.AttachmentStore
import com.parrotworks.oneagentarmy.model.Draft
import com.parrotworks.oneagentarmy.model.PendingAttachment
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// A draft has no foreign key to conversations - by design, so it can hold a message typed
// for a conversation whose row doesn't exist yet. That decoupling cuts both ways: nothing
// automatically takes a draft with it when its conversation disappears, whether that's an
// explicit delete (deleteConversation/deleteConversations) or the conversation simply never
// having been created in the first place (cleanupOrphanedDrafts - see its own doc comment on
// ConversationRepository for how a draft ends up in that state without ever being deleted).
// Both are exercised here against a real (in-memory) database and real file storage, the way
// they'd actually surface: a photo staged from the camera, never sent.
@RunWith(AndroidJUnit4::class)
class RoomConversationRepositoryDeletionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val attachmentStore = AttachmentStore(context)
    private val repository = RoomConversationRepository(database.conversationDao(), database.draftDao(), attachmentStore)

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingAConversationRemovesItsDraftAndTheDraftsAttachmentFile() = runBlocking {
        val conversationId = "convo-with-unsent-photo"
        repository.createConversation(conversationId, "Test", "gpt-4.1-nano")

        // Simulates what attachmentStore.saveImage would have produced: a file already
        // written to the shared attachments directory, referenced only from the draft
        // because the photo was staged but never sent.
        val attachmentPath = "staged-photo.jpg"
        val file = File(attachmentStore.absolutePath(attachmentPath))
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(8))
        repository.saveDraft(
            conversationId,
            Draft(text = "", attachment = PendingAttachment.Media("image", attachmentPath, "image/jpeg", "staged-photo.jpg")),
        )
        assertTrue("the file must exist before deletion for this test to prove anything", file.exists())

        repository.deleteConversation(conversationId)

        assertNull("the draft row must not outlive its conversation", repository.observeDraft(conversationId).first())
        assertFalse("the orphaned attachment file must be cleaned up, not just the row", file.exists())
    }

    @Test
    fun conversationExistsFlipsOnlyOnceTheConversationIsCreated() = runBlocking {
        // Every pre-creation choice (model, facts, context window) branches on this, so it
        // has to answer for a conversation that only has a draft, not a row.
        repository.saveDraft("not-a-conversation-yet", Draft(text = "typing", attachment = null))
        assertFalse(repository.conversationExists("not-a-conversation-yet"))

        repository.createConversation("not-a-conversation-yet", "Test", "gpt-4.1-nano")

        assertTrue(repository.conversationExists("not-a-conversation-yet"))
    }

    @Test
    fun aDraftCanCarryPreCreationChoicesWithNoConversationRow() = runBlocking {
        // The drafts table has no foreign key to conversations precisely so this works.
        val draft = Draft(
            text = "",
            attachment = null,
            modelId = "claude-opus-4-8",
            contextWindowOverride = 80,
            factIds = setOf("fact-a", "fact-b"),
        )

        repository.saveDraft("unsent-convo", draft)

        assertEquals(draft, repository.observeDraft("unsent-convo").first())
    }

    @Test
    fun bulkDeleteAlsoTakesDraftsWithIt() = runBlocking {
        val ids = listOf("convo-a", "convo-b")
        ids.forEach { repository.createConversation(it, "Test", "gpt-4.1-nano") }
        ids.forEach { repository.saveDraft(it, Draft(text = "unsent for $it", attachment = null)) }

        repository.deleteConversations(ids)

        ids.forEach { id -> assertNull(repository.observeDraft(id).first()) }
    }

    @Test
    fun deletingAConversationLeavesAnotherConversationsDraftAlone() = runBlocking {
        repository.createConversation("keep", "Keep", "gpt-4.1-nano")
        repository.createConversation("drop", "Drop", "gpt-4.1-nano")
        repository.saveDraft("keep", Draft(text = "still typing this one", attachment = null))
        repository.saveDraft("drop", Draft(text = "abandoned", attachment = null))

        repository.deleteConversation("drop")

        assertEquals("still typing this one", repository.observeDraft("keep").first()?.text)
    }

    // --- cleanupOrphanedDrafts: the other way a draft ends up pointing at nothing, without
    // an explicit delete - a never-sent "New conversation" whose id, before the reuse fix,
    // was thrown away the moment the user left and a fresh random one was minted next time.

    @Test
    fun cleanupOrphanedDrafts_removesADraftWhoseConversationWasNeverCreatedAndItsFile() = runBlocking {
        val strandedId = "stranded-from-before-the-fix"
        val attachmentPath = "stranded-photo.jpg"
        val file = File(attachmentStore.absolutePath(attachmentPath))
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(8))
        repository.saveDraft(
            strandedId,
            Draft(text = "", attachment = PendingAttachment.Media("image", attachmentPath, "image/jpeg", "stranded-photo.jpg")),
        )

        repository.cleanupOrphanedDrafts(pendingNewConversationId = null)

        assertNull(repository.observeDraft(strandedId).first())
        assertFalse("an orphan's file must be cleaned up too, not just its row", file.exists())
    }

    @Test
    fun cleanupOrphanedDrafts_keepsTheCurrentlyReservedNewConversationDraft() = runBlocking {
        val reservedId = "reserved-for-the-new-conversation-button"
        repository.saveDraft(reservedId, Draft(text = "still composing this one", attachment = null))

        repository.cleanupOrphanedDrafts(pendingNewConversationId = reservedId)

        assertEquals("still composing this one", repository.observeDraft(reservedId).first()?.text)
    }

    @Test
    fun cleanupOrphanedDrafts_leavesAnExistingConversationsDraftAlone() = runBlocking {
        repository.createConversation("real-convo", "Test", "gpt-4.1-nano")
        repository.saveDraft("real-convo", Draft(text = "typing a follow-up", attachment = null))

        repository.cleanupOrphanedDrafts(pendingNewConversationId = null)

        assertEquals("typing a follow-up", repository.observeDraft("real-convo").first()?.text)
    }
}
