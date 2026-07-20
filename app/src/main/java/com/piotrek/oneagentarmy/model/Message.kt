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
    // Media attachment (image/pdf) sent with a user message; the file lives in
    // the app-private attachments directory under attachmentPath.
    val attachmentType: String? = null,
    val attachmentPath: String? = null,
    val attachmentMime: String? = null,
    val attachmentName: String? = null,
) {
    companion object {
        const val ATTACHMENT_TYPE_IMAGE = "image"
        const val ATTACHMENT_TYPE_PDF = "pdf"
    }
}
