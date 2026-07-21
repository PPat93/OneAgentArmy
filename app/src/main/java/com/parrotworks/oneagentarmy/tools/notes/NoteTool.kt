package com.parrotworks.oneagentarmy.tools.notes

import android.content.Context
import android.content.Intent
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val CREATE_NOTE_TOOL = "create_note"

data class NoteDraft(val title: String?, val content: String)

@Serializable
private data class NoteArgs(val title: String? = null, val content: String)

private val json = Json { ignoreUnknownKeys = true }

val CreateNoteToolDefinition = ToolDefinition(
    name = CREATE_NOTE_TOOL,
    description = "Save a note on the user's phone. Opens the user's notes app (e.g. Google Keep) " +
        "with the note pre-filled. Use when the user asks to note something down or save " +
        "a piece of text for later.",
    parametersSchema = Json.parseToJsonElement(
        """
        {
            "type": "object",
            "properties": {
                "title": { "type": ["string", "null"], "description": "Optional short note title" },
                "content": { "type": "string", "description": "The note text" }
            },
            "required": ["title", "content"],
            "additionalProperties": false
        }
        """,
    ).jsonObject,
)

fun parseNoteArgs(argumentsJson: String): NoteDraft {
    val args = json.decodeFromString(NoteArgs.serializer(), argumentsJson)
    require(args.content.isNotBlank()) { "Note content must not be blank" }
    return NoteDraft(args.title?.takeIf { it.isNotBlank() }, args.content)
}

// Note apps (Keep, Samsung Notes...) handle the assistant-era CREATE_NOTE action and
// open directly with the content pre-filled - the share sheet is only the fallback
// for phones without any app handling it.
fun buildNoteIntent(context: Context, draft: NoteDraft): Intent {
    val createNote = Intent(ACTION_CREATE_NOTE).apply {
        type = "text/plain"
        draft.title?.let { putExtra(EXTRA_NOTE_NAME, it) }
        putExtra(Intent.EXTRA_TEXT, draft.content)
    }
    if (createNote.resolveActivity(context.packageManager) != null) return createNote

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        draft.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        putExtra(Intent.EXTRA_TEXT, draft.content)
    }
    return Intent.createChooser(send, null)
}

private const val ACTION_CREATE_NOTE = "com.google.android.gms.actions.CREATE_NOTE"
private const val EXTRA_NOTE_NAME = "com.google.android.gms.actions.EXTRA_NAME"
