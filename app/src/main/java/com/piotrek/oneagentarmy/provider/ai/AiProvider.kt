package com.piotrek.oneagentarmy.provider.ai

import com.piotrek.oneagentarmy.model.Message

// Integration seam for OpenAI/Gemini/Claude clients. The modelId is provider-specific
// (e.g. an OpenAI model name) and comes from the conversation being replied to.
interface AiProvider {
    suspend fun sendMessage(history: List<Message>, modelId: String): Message
}
