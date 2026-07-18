package com.piotrek.oneagentarmy.tools.clock

import android.content.Intent
import android.provider.AlarmClock
import com.piotrek.oneagentarmy.provider.ai.tools.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val SET_ALARM_TOOL = "set_alarm"
const val SET_TIMER_TOOL = "set_timer"

private val json = Json { ignoreUnknownKeys = true }

// --- Alarm ---

data class AlarmDraft(val hour: Int, val minute: Int, val label: String?)

@Serializable
private data class AlarmArgs(val hour: Int, val minute: Int, val label: String? = null)

val SetAlarmToolDefinition = ToolDefinition(
    name = SET_ALARM_TOOL,
    description = "Set an alarm clock on the user's phone at a given time of day.",
    parametersSchema = Json.parseToJsonElement(
        """
        {
            "type": "object",
            "properties": {
                "hour": { "type": "integer", "description": "Hour, 0-23" },
                "minute": { "type": "integer", "description": "Minute, 0-59" },
                "label": { "type": ["string", "null"], "description": "Optional alarm label" }
            },
            "required": ["hour", "minute", "label"],
            "additionalProperties": false
        }
        """,
    ).jsonObject,
)

fun parseAlarmArgs(argumentsJson: String): AlarmDraft {
    val args = json.decodeFromString(AlarmArgs.serializer(), argumentsJson)
    require(args.hour in 0..23 && args.minute in 0..59) { "Invalid alarm time" }
    return AlarmDraft(args.hour, args.minute, args.label?.takeIf { it.isNotBlank() })
}

fun buildAlarmIntent(draft: AlarmDraft): Intent =
    Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, draft.hour)
        putExtra(AlarmClock.EXTRA_MINUTES, draft.minute)
        draft.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
    }

// --- Timer ---

data class TimerDraft(val durationMinutes: Int, val label: String?)

@Serializable
private data class TimerArgs(
    @SerialName("duration_minutes") val durationMinutes: Int,
    val label: String? = null,
)

val SetTimerToolDefinition = ToolDefinition(
    name = SET_TIMER_TOOL,
    description = "Start a countdown timer on the user's phone.",
    parametersSchema = Json.parseToJsonElement(
        """
        {
            "type": "object",
            "properties": {
                "duration_minutes": { "type": "integer", "description": "Timer length in minutes" },
                "label": { "type": ["string", "null"], "description": "Optional timer label" }
            },
            "required": ["duration_minutes", "label"],
            "additionalProperties": false
        }
        """,
    ).jsonObject,
)

fun parseTimerArgs(argumentsJson: String): TimerDraft {
    val args = json.decodeFromString(TimerArgs.serializer(), argumentsJson)
    require(args.durationMinutes > 0) { "Timer duration must be positive" }
    return TimerDraft(args.durationMinutes, args.label?.takeIf { it.isNotBlank() })
}

fun buildTimerIntent(draft: TimerDraft): Intent =
    Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, draft.durationMinutes * 60)
        draft.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
    }
