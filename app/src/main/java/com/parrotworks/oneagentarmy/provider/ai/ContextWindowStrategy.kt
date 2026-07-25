package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message

fun interface ContextWindowStrategy {
    fun apply(history: List<Message>): List<Message>
}

object ContextWindowStrategies {
    // Trimming one message per turn looks tidy but defeats prompt caching entirely:
    // providers match a cached prompt as a contiguous run from the very first token,
    // so dropping the oldest message shifts the start of every request and nothing
    // matches. Worse, the whole prompt is then re-written to the cache at a premium,
    // making long conversations more expensive than not caching at all.
    //
    // Dropping a whole chunk at once instead keeps the first retained message fixed
    // for a chunk's worth of growth, so only the one turn that actually trims pays
    // to rebuild the cache; the turns in between reuse it.
    //
    // History grows to minimumKept + chunk before anything is dropped and never
    // falls below minimumKept, so the model always has at least the configured
    // amount of context.
    fun rollingChunked(
        minimumKept: Int,
        chunk: Int = (minimumKept / 2).coerceAtLeast(1),
    ) = ContextWindowStrategy { history ->
        val overflow = history.size - (minimumKept + chunk)
        if (overflow <= 0) {
            history
        } else {
            // Rounded up to whole chunks, so the number dropped - and therefore the
            // message the prompt starts with - only changes once per chunk rather
            // than on every turn. That stability is the entire point.
            val chunksToDrop = (overflow + chunk - 1) / chunk
            history.drop(chunksToDrop * chunk)
        }
    }
}
