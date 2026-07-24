package com.parrotworks.oneagentarmy.provider.ai

// Provider-agnostic token counts, accumulated across all API round-trips that
// served a single chat message.
//
// The three input buckets are DISJOINT - the full prompt size is their sum. That
// matters because providers disagree on how they report caching: Anthropic reports
// cached and written tokens separately from input_tokens, while OpenAI and Gemini
// count them inside their prompt total. Each provider's DTO converts its own
// convention into this shape exactly once (see normalizeCacheAwareUsage), so
// nothing downstream has to know which provider it came from.
//
// The cache buckets default to 0, so a provider that reports no caching at all -
// or stops reporting it in some future API version - behaves exactly as it did
// before caching existed.
data class TokenUsage(
    // Prompt tokens billed at full price: neither served from nor written to a cache.
    val inputTokens: Long,
    val outputTokens: Long,
    // Prompt tokens served from the provider's cache, billed at a discount.
    val cachedInputTokens: Long = 0,
    // Prompt tokens written into the cache. Anthropic bills these at a premium;
    // providers with automatic caching generally bill them as ordinary input.
    val cacheWriteInputTokens: Long = 0,
) {
    val totalInputTokens: Long
        get() = inputTokens + cachedInputTokens + cacheWriteInputTokens

    operator fun plus(other: TokenUsage) = TokenUsage(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        cachedInputTokens = cachedInputTokens + other.cachedInputTokens,
        cacheWriteInputTokens = cacheWriteInputTokens + other.cacheWriteInputTokens,
    )

    fun isEmpty(): Boolean = totalInputTokens == 0L && outputTokens == 0L

    companion object {
        val ZERO = TokenUsage(0, 0)
    }
}

// Converts a provider's usage numbers into TokenUsage's disjoint buckets.
//
// `promptTokens` is whatever the provider calls the prompt/input total.
// `subsetAccounting` says whether the cached and written counts are already
// included in that total (OpenAI, Gemini) or reported alongside it (Anthropic).
//
// Two deliberate safety properties, both erring toward over-reporting cost rather
// than under-reporting it:
//
//  - Everything is clamped at zero, so a malformed or negative field can never
//    produce a negative bucket (and so never a negative cost).
//  - If a provider that reports a subset today ever switches to reporting
//    disjointly, the cached and written counts would exceed the shrunken prompt
//    total. Subtracting there would wrongly zero out the full-price bucket, so
//    that case is treated as already-disjoint instead. The result may
//    over-count, which shows up as a slightly high cost estimate - the safe
//    direction for a spending tracker.
fun normalizeCacheAwareUsage(
    promptTokens: Long,
    outputTokens: Long,
    cachedInputTokens: Long,
    cacheWriteInputTokens: Long,
    subsetAccounting: Boolean,
): TokenUsage {
    val prompt = promptTokens.coerceAtLeast(0)
    val cached = cachedInputTokens.coerceAtLeast(0)
    val written = cacheWriteInputTokens.coerceAtLeast(0)
    val fullPrice = if (subsetAccounting && cached + written <= prompt) {
        prompt - cached - written
    } else {
        prompt
    }
    return TokenUsage(
        inputTokens = fullPrice,
        outputTokens = outputTokens.coerceAtLeast(0),
        cachedInputTokens = cached,
        cacheWriteInputTokens = written,
    )
}
