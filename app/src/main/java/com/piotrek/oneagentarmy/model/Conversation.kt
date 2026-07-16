package com.piotrek.oneagentarmy.model

import java.time.Instant

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val modelId: String,
)
