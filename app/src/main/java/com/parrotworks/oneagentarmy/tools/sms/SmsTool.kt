package com.parrotworks.oneagentarmy.tools.sms

import android.content.Intent
import android.net.Uri
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val DRAFT_SMS_TOOL = "draft_sms"

data class SmsDraft(val phoneNumber: String?, val message: String)

@Serializable
private data class SmsArgs(
    @SerialName("phone_number") val phoneNumber: String? = null,
    val message: String,
)

private val json = Json { ignoreUnknownKeys = true }

val DraftSmsToolDefinition = ToolDefinition(
    name = DRAFT_SMS_TOOL,
    description = "Open the user's SMS app with a pre-filled draft message. Nothing is sent " +
        "automatically - the user reviews and sends it themselves. Only fill phone_number if the " +
        "user explicitly provided one; leave it null otherwise (they'll pick the recipient).",
    parametersSchema = Json.parseToJsonElement(
        """
        {
            "type": "object",
            "properties": {
                "phone_number": { "type": ["string", "null"], "description": "Recipient phone number, only if explicitly given" },
                "message": { "type": "string", "description": "The SMS text to pre-fill" }
            },
            "required": ["phone_number", "message"],
            "additionalProperties": false
        }
        """,
    ).jsonObject,
)

fun parseSmsArgs(argumentsJson: String): SmsDraft {
    val args = json.decodeFromString(SmsArgs.serializer(), argumentsJson)
    require(args.message.isNotBlank()) { "SMS message must not be blank" }
    return SmsDraft(args.phoneNumber?.takeIf { it.isNotBlank() }, args.message)
}

fun buildSmsIntent(draft: SmsDraft): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${draft.phoneNumber.orEmpty()}")
        putExtra("sms_body", draft.message)
    }
