package com.parrotworks.oneagentarmy.model

import java.time.Instant

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val modelId: String,
    val pinned: Boolean,
    val lastMessageAt: Instant,
    // Null means "use the global default" from Settings.
    val contextWindowOverride: Int? = null,
)
