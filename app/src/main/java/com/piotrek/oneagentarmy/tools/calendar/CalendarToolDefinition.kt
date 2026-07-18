package com.piotrek.oneagentarmy.tools.calendar

import com.piotrek.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

const val CREATE_CALENDAR_EVENT_TOOL = "create_calendar_event"

// Strict-mode schema: additionalProperties=false, every field required,
// optional fields expressed as nullable unions.
private val PARAMETERS_SCHEMA: JsonObject = Json.parseToJsonElement(
    """
    {
        "type": "object",
        "properties": {
            "title": { "type": "string", "description": "Short event title" },
            "description": { "type": ["string", "null"], "description": "Optional longer description" },
            "location": { "type": ["string", "null"], "description": "Optional place name or address" },
            "start": { "type": "string", "description": "Local start datetime, format yyyy-MM-ddTHH:mm, e.g. 2026-12-19T18:00" },
            "end": { "type": "string", "description": "Local end datetime, same format, must be after start" },
            "allDay": { "type": "boolean", "description": "True for all-day events" },
            "attendees": { "type": "array", "items": { "type": "string" }, "description": "Email addresses of guests" }
        },
        "required": ["title", "description", "location", "start", "end", "allDay", "attendees"],
        "additionalProperties": false
    }
    """,
).jsonObject

val CalendarToolDefinition = ToolDefinition(
    name = CREATE_CALENDAR_EVENT_TOOL,
    description = "Create a calendar event on the user's phone. Call this when the user asks to " +
        "schedule a meeting, appointment or event. Only fill attendees with email addresses the " +
        "user explicitly provided - if they name a person without an email address, ask for the " +
        "address instead of calling this tool.",
    parametersSchema = PARAMETERS_SCHEMA,
)
