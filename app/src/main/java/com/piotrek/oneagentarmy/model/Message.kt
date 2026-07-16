package com.piotrek.oneagentarmy.model

import java.time.Instant

enum class Sender { USER, AI }

data class Message(
    val id: String,
    val conversationId: String,
    val sender: Sender,
    val text: String,
    val timestamp: Instant,
)
