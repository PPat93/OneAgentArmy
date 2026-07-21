package com.parrotworks.oneagentarmy.data.local

import androidx.room.Embedded

data class MessageSearchRow(
    @Embedded val message: MessageEntity,
    val conversationTitle: String,
)
