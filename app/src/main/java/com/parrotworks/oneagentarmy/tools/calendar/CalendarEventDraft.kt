package com.parrotworks.oneagentarmy.tools.calendar

data class CalendarEventDraft(
    val title: String,
    val description: String?,
    val location: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean,
    val attendees: List<String>,
)
