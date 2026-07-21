package com.parrotworks.oneagentarmy.tools.calendar

import android.content.Intent
import android.provider.CalendarContract

object CalendarIntentBuilder {

    fun build(draft: CalendarEventDraft): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.startEpochMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.endEpochMillis)
            putExtra(CalendarContract.Events.TITLE, draft.title)
            draft.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            draft.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, draft.allDay)
            if (draft.attendees.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, draft.attendees.joinToString(","))
            }
        }
}
