package com.parrotworks.oneagentarmy.provider.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenUsageTest {

    // --- disjoint accounting (Anthropic) ---

    @Test
    fun `disjoint accounting keeps the prompt total as full-price tokens`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = 50,
            outputTokens = 500,
            cachedInputTokens = 100_000,
            cacheWriteInputTokens = 0,
            subsetAccounting = false,
        )

        // Anthropic's input_tokens already excludes cached tokens - nothing to subtract.
        assertEquals(50L, usage.inputTokens)
        assertEquals(100_000L, usage.cachedInputTokens)
        assertEquals(100_050L, usage.totalInputTokens)
        assertEquals(500L, usage.outputTokens)
    }

    // --- subset accounting (OpenAI, Gemini) ---

    @Test
    fun `subset accounting splits the prompt total into cached and full-price`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = 1000,
            outputTokens = 200,
            cachedInputTokens = 800,
            cacheWriteInputTokens = 0,
            subsetAccounting = true,
        )

        assertEquals(200L, usage.inputTokens)
        assertEquals(800L, usage.cachedInputTokens)
        // The split must not invent or lose tokens.
        assertEquals(1000L, usage.totalInputTokens)
    }

    @Test
    fun `subset accounting also subtracts cache writes`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = 1000,
            outputTokens = 0,
            cachedInputTokens = 600,
            cacheWriteInputTokens = 300,
            subsetAccounting = true,
        )

        assertEquals(100L, usage.inputTokens)
        assertEquals(1000L, usage.totalInputTokens)
    }

    @Test
    fun `subset accounting with no caching reported is unchanged`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = 1234,
            outputTokens = 56,
            cachedInputTokens = 0,
            cacheWriteInputTokens = 0,
            subsetAccounting = true,
        )

        // The pre-caching behavior, byte for byte.
        assertEquals(TokenUsage(inputTokens = 1234, outputTokens = 56), usage)
    }

    // --- future-proofing ---

    @Test
    fun `subset accounting falls back to disjoint when cached exceeds the prompt total`() {
        // If a provider that reports a subset today ever switched to reporting
        // disjointly, subtracting would wrongly zero the full-price bucket and
        // under-report the bill. Treating it as disjoint over-counts instead.
        val usage = normalizeCacheAwareUsage(
            promptTokens = 50,
            outputTokens = 10,
            cachedInputTokens = 9000,
            cacheWriteInputTokens = 0,
            subsetAccounting = true,
        )

        assertEquals(50L, usage.inputTokens)
        assertEquals(9000L, usage.cachedInputTokens)
        assertEquals(9050L, usage.totalInputTokens)
    }

    @Test
    fun `negative counts are clamped so no bucket can go below zero`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = -5,
            outputTokens = -10,
            cachedInputTokens = -20,
            cacheWriteInputTokens = -1,
            subsetAccounting = true,
        )

        assertEquals(TokenUsage.ZERO, usage)
    }

    @Test
    fun `cached exactly equal to the prompt total leaves no full-price tokens`() {
        val usage = normalizeCacheAwareUsage(
            promptTokens = 500,
            outputTokens = 0,
            cachedInputTokens = 500,
            cacheWriteInputTokens = 0,
            subsetAccounting = true,
        )

        assertEquals(0L, usage.inputTokens)
        assertEquals(500L, usage.totalInputTokens)
    }

    // --- arithmetic ---

    @Test
    fun `plus sums every bucket independently`() {
        val a = TokenUsage(inputTokens = 1, outputTokens = 2, cachedInputTokens = 3, cacheWriteInputTokens = 4)
        val b = TokenUsage(inputTokens = 10, outputTokens = 20, cachedInputTokens = 30, cacheWriteInputTokens = 40)

        assertEquals(
            TokenUsage(inputTokens = 11, outputTokens = 22, cachedInputTokens = 33, cacheWriteInputTokens = 44),
            a + b,
        )
    }

    @Test
    fun `usage made only of cached tokens is not treated as empty`() {
        // A fully-cached turn still costs money - it must not be mistaken for
        // "no usage reported" and dropped from the cost ledger.
        val cachedOnly = TokenUsage(inputTokens = 0, outputTokens = 0, cachedInputTokens = 900)

        assertFalse(cachedOnly.isEmpty())
        assertTrue(TokenUsage.ZERO.isEmpty())
    }

    @Test
    fun `cache buckets default to zero so pre-caching construction is unchanged`() {
        val usage = TokenUsage(inputTokens = 10, outputTokens = 20)

        assertEquals(0L, usage.cachedInputTokens)
        assertEquals(0L, usage.cacheWriteInputTokens)
        assertEquals(10L, usage.totalInputTokens)
    }
}
