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
// for a conversation whose row doesn't exist yet. The cost of that: deleting a conversation
// does not automatically take its draft with it, which is exactly the kind of gap the app's
// own cost-ledger bug (fixed earlier this project) came from - a table deliberately decoupled
// from conversations turning into a silent leak on deletion. This exercises the real
// repository against a real (in-memory) database and real file storage, the way the bug
// would actually have surfaced: a photo staged from the camera, never sent, then the
// conversation deleted.
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
}
