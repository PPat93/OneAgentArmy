package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.openai.dto.ChatMessageDto

fun Message.toDto() = ChatMessageDto(
    role = if (sender == Sender.USER) "user" else "assistant",
    content = text,
)
