package com.parrotworks.oneagentarmy.data.local

import com.parrotworks.oneagentarmy.model.Draft
import com.parrotworks.oneagentarmy.model.PendingAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftMappersTest {

    @Test
    fun `draft with no attachment round-trips`() {
        val draft = Draft(text = "hello", attachment = null)

        val entity = draft.toEntity("conversation-1")
        val roundTripped = entity.toDomain()

        assertEquals("conversation-1", entity.conversationId)
        assertNull(entity.attachmentKind)
        assertEquals(draft, roundTripped)
    }

    @Test
    fun `draft with a text file attachment round-trips`() {
        val draft = Draft(
            text = "see attached",
            attachment = PendingAttachment.TextFile(name = "notes.txt", content = "line one\nline two"),
        )

        val entity = draft.toEntity("conversation-2")
        val roundTripped = entity.toDomain()

        assertEquals("TEXT_FILE", entity.attachmentKind)
        assertEquals(draft, roundTripped)
    }

    @Test
    fun `draft with a media attachment round-trips`() {
        val draft = Draft(
            text = "",
            attachment = PendingAttachment.Media(type = "image", path = "img/1.jpg", mime = "image/jpeg", name = "1.jpg"),
        )

        val entity = draft.toEntity("conversation-3")
        val roundTripped = entity.toDomain()

        assertEquals("MEDIA", entity.attachmentKind)
        assertEquals(draft, roundTripped)
    }
}
