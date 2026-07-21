package com.parrotworks.oneagentarmy.provider.ai.openai

import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.Sender
import com.parrotworks.oneagentarmy.provider.ai.openai.dto.inputMessageItem
import kotlinx.serialization.json.JsonObject

fun Message.toInputItem(): JsonObject = inputMessageItem(
    role = if (sender == Sender.USER) "user" else "assistant",
    text = text,
)
