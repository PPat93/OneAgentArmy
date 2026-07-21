package com.parrotworks.oneagentarmy.model

// Unsent input for a conversation - text and/or a staged attachment - persisted so it
// survives the app being backgrounded, locked, or killed by the system before it's sent.
data class Draft(
    val text: String,
    val attachment: PendingAttachment?,
)
