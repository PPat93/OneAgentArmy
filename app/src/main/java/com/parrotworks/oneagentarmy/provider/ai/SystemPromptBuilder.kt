package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.Sender
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shared by all providers - the same guidance goes to OpenAI's `instructions`,
// Gemini's `system_instruction` and Anthropic's `system`.
//
// Deliberately free of anything that changes between requests. This text is the very first
// thing in every prompt, and prompt caching matches a contiguous prefix starting at token
// zero: a clock in here changed the opening tokens on every single call, so no request ever
// matched the previous one's cached prefix. The cost was not merely a lost discount - cache
// writes are billed, and on Anthropic (where the app opts in via cache_control) at a 1.25x
// premium, so caching was actively more expensive than not caching at all.
//
// The current time now travels with the messages instead - see withSendTimes.
fun buildSystemPrompt(clock: Clock, contextFacts: List<String>): String {
    val factsSection = if (contextFacts.isEmpty()) {
        ""
    } else {
        "\n\nFacts about the user, provided by the user themselves - take them into account:\n" +
            contextFacts.withIndex().joinToString("\n") { (i, fact) -> "${i + 1}) $fact" }
    }
    return "Every user message is tagged with the time it was sent, in timezone ${clock.zone}. " +
        "The newest tag is the current time - resolve all relative dates ('tomorrow', 'jutro', " +
        "'next Friday') against it. " +
        "When the user asks to schedule a calendar event, call create_calendar_event. " +
        "Only include attendee emails the user explicitly provided; if they name a person " +
        "without an email address, ask for the address instead of calling the tool. " +
        "Use the other tools when the user asks for those actions: alarms, timers, " +
        "SMS drafts, navigation, opening the calendar at a date, weather forecasts, " +
        "saving a note. " +
        "Use web search only when the question genuinely needs current, real-time, or " +
        "recent information - answer from your own knowledge otherwise. One search is " +
        "normally enough: search a second time only if the first results genuinely failed " +
        "to answer the question, and never search more than twice for one message. " +
        "Otherwise answer normally, in the user's language. " +
        "The user reads your answers on a phone screen: keep formatting compact. " +
        "Avoid wide markdown tables - use at most 3 columns with terse cell values " +
        "(a few words), and prefer bulleted lists over tables when comparing many " +
        "attributes. Do not use images or LaTeX; plain markdown only." +
        factsSection
}

// Stamps each user message with the time it was sent, for the request only - nothing here
// is persisted, so the chat still shows exactly what was typed.
//
// The stamp comes from the message's own timestamp rather than from the clock, which is the
// whole point: a message replayed on a later turn carries the identical stamp it carried
// before, so the prefix every request shares stays byte-for-byte stable and stays cacheable.
// Putting "now" in the system prompt instead would break that on every call; putting only
// today's date there would break it once a day at midnight. This breaks it never.
//
// The newest message's stamp doubles as "what time is it now", which is what the tool calls
// that need a clock (alarms, timers, relative dates) actually resolve against.
fun withSendTimes(history: List<Message>, zone: ZoneId): List<Message> =
    history.map { message ->
        if (message.sender != Sender.USER) {
            message
        } else {
            message.copy(text = "${message.text}\n\n[sent ${formatSendTime(message.timestamp, zone)}]")
        }
    }

// Minute precision - seconds would be noise, and a coarser stamp would make "set a timer for
// 20 minutes" resolve visibly wrong.
private fun formatSendTime(timestamp: Instant, zone: ZoneId): String =
    SEND_TIME_FORMAT.format(timestamp.atZone(zone))

private val SEND_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)", Locale.ENGLISH)
