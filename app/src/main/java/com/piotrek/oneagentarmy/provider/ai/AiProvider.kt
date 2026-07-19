package com.piotrek.oneagentarmy.provider.ai

import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest

sealed interface AiReply {
    // Text carries usage/cost inside the Message; ToolCall turns are never persisted
    // as-is, so their usage rides along for the ViewModel to attach to the
    // locally-generated summary note.
    data class Text(val message: Message) : AiReply
    data class ToolCall(
        val request: ToolCallRequest,
        val usage: TokenUsage = TokenUsage.ZERO,
        val costUsd: Double? = null,
    ) : AiReply
}

// Integration seam for OpenAI/Gemini/Claude clients. The modelId is provider-specific
// (e.g. an OpenAI model name) and comes from the conversation being replied to.
// contextFacts are user-authored notes (global + per-conversation selected) that the
// provider injects into its system prompt.
interface AiProvider {
    suspend fun sendMessage(history: List<Message>, modelId: String, contextFacts: List<String>): AiReply
}
