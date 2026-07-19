package com.piotrek.oneagentarmy.provider.ai.openai

import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.openai.dto.inputMessageItem
import kotlinx.serialization.json.JsonObject

fun Message.toInputItem(): JsonObject = inputMessageItem(
    role = if (sender == Sender.USER) "user" else "assistant",
    text = text,
)
