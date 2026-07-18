package com.piotrek.oneagentarmy.tools.calendar

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import com.piotrek.oneagentarmy.provider.ai.tools.ToolDefinition
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val OPEN_CALENDAR_AT_TOOL = "open_calendar_at"

data class OpenCalendarDraft(val date: LocalDate)

@Serializable
private data class OpenCalendarArgs(val date: String)

private val json = Json { ignoreUnknownKeys = true }

val OpenCalendarToolDefinition = ToolDefinition(
    name = OPEN_CALENDAR_AT_TOOL,
    description = "Open the user's calendar app at a specific date, e.g. so they can review, " +
        "edit or delete events on that day. Use when the user wants to see their calendar or " +
        "modify existing events (which you cannot do directly).",
    parametersSchema = Json.parseToJsonElement(
        """
        {
            "type": "object",
            "properties": {
                "date": { "type": "string", "description": "Date to open, format yyyy-MM-dd" }
            },
            "required": ["date"],
            "additionalProperties": false
        }
        """,
    ).jsonObject,
)

fun parseOpenCalendarArgs(argumentsJson: String): OpenCalendarDraft {
    val args = json.decodeFromString(OpenCalendarArgs.serializer(), argumentsJson)
    return OpenCalendarDraft(LocalDate.parse(args.date))
}

fun buildOpenCalendarIntent(draft: OpenCalendarDraft): Intent {
    val epochMillis = draft.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = ContentUris.appendId(
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
        epochMillis,
    ).build()
    return Intent(Intent.ACTION_VIEW, uri)
}
