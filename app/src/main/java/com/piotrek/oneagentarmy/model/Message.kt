package com.piotrek.oneagentarmy.model

import java.time.Instant

enum class Sender { USER, AI }

data class Message(
    val id: String,
    val conversationId: String,
    val sender: Sender,
    val text: String,
    val timestamp: Instant,
    // Token usage and estimated cost of the API round-trips that produced this
    // message. Null for user messages and for AI messages predating cost tracking.
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val costUsd: Double? = null,
)
