package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.Sender
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptBuilderTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), zone)

    private fun userMessage(id: String, text: String, at: String) = Message(
        id = id,
        conversationId = "convo-1",
        sender = Sender.USER,
        text = text,
        timestamp = Instant.parse(at),
    )

    private fun aiMessage(id: String, text: String, at: String) =
        userMessage(id, text, at).copy(sender = Sender.AI)

    // --- the regression this whole change exists for ---

    @Test
    fun `system prompt is identical no matter when it is built`() {
        // It is the opening tokens of every request, and caching matches a contiguous prefix
        // from token zero. Anything time-varying in here means no request can ever hit the
        // cache - while still being billed for writing it.
        val morning = buildSystemPrompt(clockAt("2026-07-25T06:59:23.481Z"), emptyList())
        val evening = buildSystemPrompt(clockAt("2026-07-25T21:04:11.002Z"), emptyList())
        val nextDay = buildSystemPrompt(clockAt("2026-07-26T00:00:00.000Z"), emptyList())

        assertEquals(morning, evening)
        // Crossing midnight must not change it either - that is why the date lives on the
        // messages rather than here.
        assertEquals(morning, nextDay)
    }

    @Test
    fun `system prompt carries the timezone but never a concrete time`() {
        val prompt = buildSystemPrompt(clockAt("2026-07-25T06:59:23.481Z"), emptyList())

        assertTrue(prompt.contains("Europe/Warsaw"))
        assertFalse(prompt.contains("2026"))
        assertFalse(prompt.contains("06:59"))
    }

    @Test
    fun `facts still land in the prompt and are stable`() {
        val facts = listOf("Has two cats", "Works in Krakow")

        val prompt = buildSystemPrompt(clockAt("2026-07-25T06:59:23.481Z"), facts)

        assertTrue(prompt.contains("1) Has two cats"))
        assertTrue(prompt.contains("2) Works in Krakow"))
        assertEquals(prompt, buildSystemPrompt(clockAt("2026-11-02T18:30:00.000Z"), facts))
    }

    @Test
    fun `the prompt no longer talks the model into extra searches`() {
        val prompt = buildSystemPrompt(clockAt("2026-07-25T06:59:23.481Z"), emptyList())

        assertTrue(prompt.contains("never search more than twice"))
        assertFalse(prompt.contains("search again rather than settling"))
    }

    // --- per-message send times ---

    @Test
    fun `user messages are stamped with their own send time`() {
        val stamped = withSendTimes(
            listOf(userMessage("m1", "what time is it", "2026-07-25T12:32:40Z")),
            zone,
        )

        // 12:32 UTC is 14:32 in Warsaw (CEST).
        assertEquals("what time is it\n\n[sent 2026-07-25 14:32 (Saturday)]", stamped.single().text)
    }

    @Test
    fun `ai messages are left untouched`() {
        val reply = aiMessage("a1", "it is half past two", "2026-07-25T12:33:00Z")

        assertEquals(listOf(reply), withSendTimes(listOf(reply), zone))
    }

    @Test
    fun `a message keeps the same stamp once it becomes history`() {
        // The point of stamping from the message's own timestamp rather than from the clock:
        // replaying an older message must reproduce the exact bytes sent last time, or the
        // shared prefix - and with it the cache hit - is lost on every turn.
        val first = userMessage("m1", "hello", "2026-07-25T12:32:40Z")
        val second = userMessage("m2", "and now", "2026-07-25T15:10:00Z")

        val turnOne = withSendTimes(listOf(first), zone)
        val turnTwo = withSendTimes(listOf(first, aiMessage("a1", "hi", "2026-07-25T12:33:00Z"), second), zone)

        assertEquals(turnOne.first().text, turnTwo.first().text)
    }

    @Test
    fun `the newest stamp is what tells the model the current time`() {
        val stamped = withSendTimes(
            listOf(
                userMessage("m1", "hello", "2026-07-25T12:32:40Z"),
                aiMessage("a1", "hi", "2026-07-25T12:33:00Z"),
                userMessage("m2", "set a timer for 20 minutes", "2026-07-25T15:10:00Z"),
            ),
            zone,
        )

        assertTrue(stamped.last().text.endsWith("[sent 2026-07-25 17:10 (Saturday)]"))
    }

    @Test
    fun `stamping does not touch anything but the text`() {
        val original = userMessage("m1", "look at this", "2026-07-25T12:32:40Z").copy(
            attachmentType = Message.ATTACHMENT_TYPE_IMAGE,
            attachmentPath = "photo.jpg",
            attachmentMime = "image/jpeg",
            attachmentName = "photo.jpg",
        )

        val stamped = withSendTimes(listOf(original), zone).single()

        assertEquals(original, stamped.copy(text = original.text))
    }
}
