package com.parrotworks.oneagentarmy.data.local

import com.parrotworks.oneagentarmy.model.Draft
import com.parrotworks.oneagentarmy.model.PendingAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftMapperTest {

    private fun roundTrip(draft: Draft): Draft = draft.toEntity("convo-1").toDomain()

    @Test
    fun `pre-creation choices survive a round trip`() {
        val draft = Draft(
            text = "half a sentence",
            attachment = null,
            modelId = "claude-opus-5",
            contextWindowOverride = 80,
            factIds = setOf("fact-a", "fact-b"),
        )

        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun `no selected facts is stored as null rather than an empty string`() {
        // So the column reads identically to a row written before these columns existed.
        val entity = Draft(text = "hi", attachment = null).toEntity("convo-1")

        assertNull(entity.factIds)
        assertEquals(emptySet<String>(), entity.toDomain().factIds)
    }

    @Test
    fun `a media attachment still round trips alongside the new fields`() {
        val draft = Draft(
            text = "",
            attachment = PendingAttachment.Media("image", "photo.jpg", "image/jpeg", "photo.jpg"),
            modelId = "gpt-5.6-sol",
        )

        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun `a text-file attachment still round trips`() {
        val draft = Draft(
            text = "look at this",
            attachment = PendingAttachment.TextFile("notes.txt", "file body"),
        )

        assertEquals(draft, roundTrip(draft))
    }

    // --- isEmpty: what decides whether the row is kept at all ---

    @Test
    fun `a model choice alone is worth keeping`() {
        // The regression this whole feature exists for: picking a model and walking away
        // without typing anything used to leave nothing behind, so the next visit silently
        // showed the cheapest default while the restored draft text made everything look
        // preserved.
        assertFalse(Draft(text = "", attachment = null, modelId = "claude-opus-5").isEmpty())
    }

    @Test
    fun `a context window override alone is worth keeping`() {
        assertFalse(Draft(text = "", attachment = null, contextWindowOverride = 80).isEmpty())
    }

    @Test
    fun `a selected fact alone is worth keeping`() {
        assertFalse(Draft(text = "", attachment = null, factIds = setOf("fact-a")).isEmpty())
    }

    @Test
    fun `blank text with nothing else is empty`() {
        assertTrue(Draft(text = "   ", attachment = null).isEmpty())
    }

    @Test
    fun `text alone is not empty`() {
        assertFalse(Draft(text = "typing", attachment = null).isEmpty())
    }
}
