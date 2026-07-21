package com.parrotworks.oneagentarmy.data.local

import com.parrotworks.oneagentarmy.model.Conversation
import com.parrotworks.oneagentarmy.model.Draft
import com.parrotworks.oneagentarmy.model.Fact
import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.PendingAttachment
import com.parrotworks.oneagentarmy.model.Sender
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

fun DraftEntity.toDomain() = Draft(
    text = text,
    attachment = when (attachmentKind) {
        "TEXT_FILE" -> PendingAttachment.TextFile(
            name = attachmentName.orEmpty(),
            content = attachmentContent.orEmpty(),
        )
        "MEDIA" -> PendingAttachment.Media(
            type = attachmentMediaType.orEmpty(),
            path = attachmentPath.orEmpty(),
            mime = attachmentMime.orEmpty(),
            name = attachmentName.orEmpty(),
        )
        else -> null
    },
)

fun Draft.toEntity(conversationId: String) = DraftEntity(
    conversationId = conversationId,
    text = text,
    attachmentKind = when (attachment) {
        is PendingAttachment.TextFile -> "TEXT_FILE"
        is PendingAttachment.Media -> "MEDIA"
        null -> null
    },
    attachmentName = attachment?.name,
    attachmentContent = (attachment as? PendingAttachment.TextFile)?.content,
    attachmentMediaType = (attachment as? PendingAttachment.Media)?.type,
    attachmentPath = (attachment as? PendingAttachment.Media)?.path,
    attachmentMime = (attachment as? PendingAttachment.Media)?.mime,
)
