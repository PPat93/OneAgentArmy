package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.Sender
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextWindowStrategiesTest {

    private fun message(index: Int) = Message(
        id = "msg-$index",
        conversationId = "convo",
        sender = Sender.USER,
        text = "message $index",
        timestamp = Instant.EPOCH,
    )

    @Test
    fun `history shorter than the window is returned unchanged`() {
        val history = (1..5).map(::message)

        val result = ContextWindowStrategies.lastN(20).apply(history)

        assertEquals(history, result)
    }

    @Test
    fun `history longer than the window is truncated to the newest N`() {
        val history = (1..25).map(::message)

        val result = ContextWindowStrategies.lastN(20).apply(history)

        assertEquals(20, result.size)
        assertEquals("msg-6", result.first().id)
        assertEquals("msg-25", result.last().id)
    }

    @Test
    fun `history exactly matching the window is returned unchanged`() {
        val history = (1..20).map(::message)

        val result = ContextWindowStrategies.lastN(20).apply(history)

        assertEquals(history, result)
    }
}
