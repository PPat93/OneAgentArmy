package com.piotrek.oneagentarmy.data.local

import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import java.time.Instant

fun ConversationEntity.toDomain() = Conversation(
    id = id,
    title = title,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilli(),
)

fun MessageEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    sender = Sender.valueOf(sender),
    text = text,
    timestamp = Instant.ofEpochMilli(timestamp),
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    sender = sender.name,
    text = text,
    timestamp = timestamp.toEpochMilli(),
)
