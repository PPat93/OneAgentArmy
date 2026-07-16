package com.piotrek.oneagentarmy.provider.ai

import com.piotrek.oneagentarmy.model.Message

// Future integration seam for OpenAI/Gemini/Claude clients - not called anywhere yet in Stage 1.
interface AiProvider {
    suspend fun sendMessage(history: List<Message>): Message
}
