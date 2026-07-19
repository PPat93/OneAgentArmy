package com.piotrek.oneagentarmy.provider.ai

// Provider-agnostic token counts, accumulated across all API round-trips that
// served a single chat message.
data class TokenUsage(
    val inputTokens: Long,
    val outputTokens: Long,
) {
    operator fun plus(other: TokenUsage) = TokenUsage(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
    )

    fun isEmpty(): Boolean = inputTokens == 0L && outputTokens == 0L

    companion object {
        val ZERO = TokenUsage(0, 0)
    }
}
