package com.parrotworks.oneagentarmy.tools.navigation

import android.content.Intent
import android.net.Uri
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val NAVIGATE_TO_TOOL = "navigate_to"

data class NavigationDraft(val destination: String)

@Serializable
private data class NavigationArgs(val destination: String)

private val json = Json { ignoreUnknownKeys = true }

val NavigateToolDefinition = ToolDefinition(
    name = NAVIGATE_TO_TOOL,
    description = "Open the user's maps app with a destination searched, so they can start " +
        "navigation. Use when the user asks for directions or how to get somewhere.",
    parametersSchema = Json.parseToJsonElement(
        """
        {
            "type": "object",
            "properties": {
                "destination": { "type": "string", "description": "Place name or address to navigate to" }
            },
            "required": ["destination"],
            "additionalProperties": false
        }
        """,
    ).jsonObject,
)

fun parseNavigationArgs(argumentsJson: String): NavigationDraft {
    val args = json.decodeFromString(NavigationArgs.serializer(), argumentsJson)
    require(args.destination.isNotBlank()) { "Destination must not be blank" }
    return NavigationDraft(args.destination)
}

fun buildNavigationIntent(draft: NavigationDraft): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(draft.destination)}"))
