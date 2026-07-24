package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.Sender
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowStrategiesTest {

    private fun message(index: Int) = Message(
        id = "msg-$index",
        conversationId = "convo",
        sender = Sender.USER,
        text = "message $index",
        timestamp = Instant.EPOCH,
    )

    private fun history(size: Int) = (0 until size).map(::message)

    // The app's default: keep at least 40, let it reach 60, then drop 20 at once.
    private val strategy = ContextWindowStrategies.rollingChunked(minimumKept = 40)

    @Test
    fun `history below the ceiling is passed through untouched`() {
        val full = history(60)

        assertEquals(full, strategy.apply(full))
        assertEquals(history(5), strategy.apply(history(5)))
    }

    @Test
    fun `the first retained message stays identical across a whole chunk of growth`() {
        // This is the property prompt caching depends on: providers match the cached
        // prompt as a contiguous run from the very first token, so if the opening
        // message changed every turn nothing would ever match.
        val firstKeptIds = (61..80).map { size -> strategy.apply(history(size)).first().id }

        assertEquals(setOf("msg-20"), firstKeptIds.toSet())
    }

    @Test
    fun `the retained window shifts by exactly one chunk at the next boundary`() {
        assertEquals("msg-20", strategy.apply(history(80)).first().id)
        assertEquals("msg-40", strategy.apply(history(81)).first().id)

        // ...and then holds steady again for the next chunk.
        assertEquals(setOf("msg-40"), (81..100).map { strategy.apply(history(it)).first().id }.toSet())
    }

    @Test
    fun `a sliding window would move the prefix every turn - the case this avoids`() {
        // Guards against quietly reverting to takeLast(n): with a per-message slide
        // every size yields a different opening message, which is a guaranteed cache
        // miss on every single turn.
        val slidingFirstIds = (61..80).map { size -> history(size).takeLast(40).first().id }

        assertEquals(20, slidingFirstIds.toSet().size)
    }

    @Test
    fun `never keeps fewer than the configured minimum`() {
        (1..200).forEach { size ->
            val kept = strategy.apply(history(size)).size
            assertTrue("size=$size kept=$kept", kept >= minOf(size, 40))
        }
    }

    @Test
    fun `never keeps more than the minimum plus one chunk`() {
        (1..200).forEach { size ->
            val kept = strategy.apply(history(size)).size
            assertTrue("size=$size kept=$kept", kept <= 60)
        }
    }

    @Test
    fun `retained messages are always the most recent ones in order`() {
        val kept = strategy.apply(history(95))

        assertEquals("msg-94", kept.last().id)
        assertEquals(history(95).takeLast(kept.size), kept)
    }

    @Test
    fun `the chunk defaults to half the minimum`() {
        // minimumKept 20 -> ceiling 30, drop 10 (the pre-caching default, batched).
        val smaller = ContextWindowStrategies.rollingChunked(minimumKept = 20)

        assertEquals(30, smaller.apply(history(30)).size)
        assertEquals(21, smaller.apply(history(31)).size)
        assertEquals(setOf("msg-10"), (31..40).map { smaller.apply(history(it)).first().id }.toSet())
    }

    @Test
    fun `an explicit chunk size is honoured`() {
        val everyFive = ContextWindowStrategies.rollingChunked(minimumKept = 10, chunk = 5)

        assertEquals(15, everyFive.apply(history(15)).size)
        assertEquals(11, everyFive.apply(history(16)).size)
        assertEquals(setOf("msg-5"), (16..20).map { everyFive.apply(history(it)).first().id }.toSet())
    }

    @Test
    fun `a minimum of one still trims safely instead of dividing by zero`() {
        // chunk would round to 0 from minimumKept/2 - it must be clamped to 1.
        val tiny = ContextWindowStrategies.rollingChunked(minimumKept = 1)

        assertEquals(2, tiny.apply(history(2)).size)
        assertEquals(2, tiny.apply(history(3)).size)
        assertTrue(tiny.apply(history(50)).isNotEmpty())
    }
}
