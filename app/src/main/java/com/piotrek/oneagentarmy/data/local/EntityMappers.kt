package com.piotrek.oneagentarmy.data.local

import com.piotrek.oneagentarmy.model.Conversation
import com.piotrek.oneagentarmy.model.Fact
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import java.time.Instant

fun ConversationEntity.toDomain() = Conversation(
    id = id,
    title = title,
    createdAt = Instant.ofEpochMilli(createdAt),
    modelId = modelId,
    pinned = pinned,
    lastMessageAt = Instant.ofEpochMilli(lastMessageAt),
)

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilli(),
    modelId = modelId,
    pinned = pinned,
    lastMessageAt = lastMessageAt.toEpochMilli(),
)

fun MessageEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    sender = Sender.valueOf(sender),
    text = text,
    timestamp = Instant.ofEpochMilli(timestamp),
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    costUsd = costUsd,
    attachmentType = attachmentType,
    attachmentPath = attachmentPath,
    attachmentMime = attachmentMime,
    attachmentName = attachmentName,
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    sender = sender.name,
    text = text,
    textNormalized = normalizeForSearch(text),
    timestamp = timestamp.toEpochMilli(),
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    costUsd = costUsd,
    attachmentType = attachmentType,
    attachmentPath = attachmentPath,
    attachmentMime = attachmentMime,
    attachmentName = attachmentName,
)

fun FactEntity.toDomain() = Fact(
    id = id,
    title = title,
    content = content,
    isGlobal = isGlobal,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Fact.toEntity() = FactEntity(
    id = id,
    title = title,
    content = content,
    isGlobal = isGlobal,
    createdAt = createdAt.toEpochMilli(),
)
