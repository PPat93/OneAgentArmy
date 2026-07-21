package com.parrotworks.oneagentarmy.tools.calendar

import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CalendarEventArgs(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val attendees: List<String> = emptyList(),
)

object CalendarEventArgumentsParser {

    private val json = Json { ignoreUnknownKeys = true }

    // Throws on malformed input - callers map failures to a user-facing error.
    fun parse(argumentsJson: String, zone: ZoneId): CalendarEventDraft {
        val args = json.decodeFromString(CalendarEventArgs.serializer(), argumentsJson)
        val start = LocalDateTime.parse(args.start).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.parse(args.end).atZone(zone).toInstant().toEpochMilli()
        require(end > start) { "Event end must be after start" }
        return CalendarEventDraft(
            title = args.title,
            description = args.description?.takeIf { it.isNotBlank() },
            location = args.location?.takeIf { it.isNotBlank() },
            startEpochMillis = start,
            endEpochMillis = end,
            allDay = args.allDay,
            attendees = args.attendees.filter { it.isNotBlank() },
        )
    }
}
