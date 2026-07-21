package com.parrotworks.oneagentarmy.model

// Attachment staged for the next message: text files are inlined into the
// message text on send; media (image/pdf) is stored locally and sent to the
// API as a native multimodal block.
sealed interface PendingAttachment {
    val name: String

    data class TextFile(override val name: String, val content: String) : PendingAttachment
    data class Media(
        val type: String,
        val path: String,
        val mime: String,
        override val name: String,
    ) : PendingAttachment
}
