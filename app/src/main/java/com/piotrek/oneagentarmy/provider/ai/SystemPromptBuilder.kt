package com.piotrek.oneagentarmy.provider.ai

import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Shared by all providers - the same guidance goes to OpenAI's `instructions`
// and Gemini's `system_instruction`.
fun buildSystemPrompt(clock: Clock, contextFacts: List<String>): String {
    val now = LocalDateTime.now(clock)
    val dayOfWeek = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val formatted = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val factsSection = if (contextFacts.isEmpty()) {
        ""
    } else {
        "\n\nFacts about the user, provided by the user themselves - take them into account:\n" +
            contextFacts.withIndex().joinToString("\n") { (i, fact) -> "${i + 1}) $fact" }
    }
    return "Current date and time: $formatted ($dayOfWeek), timezone ${clock.zone}. " +
        "Resolve all relative dates ('tomorrow', 'jutro', 'next Friday') against this. " +
        "When the user asks to schedule a calendar event, call create_calendar_event. " +
        "Only include attendee emails the user explicitly provided; if they name a person " +
        "without an email address, ask for the address instead of calling the tool. " +
        "Use the other tools when the user asks for those actions: alarms, timers, " +
        "SMS drafts, navigation, opening the calendar at a date, weather forecasts, " +
        "saving a note. " +
        "Use web search only when the question genuinely needs current, real-time, or " +
        "recent information - answer from your own knowledge otherwise. You may search " +
        "more than once per message: if the first results are too shallow, " +
        "off-topic, or don't fully answer the question, refine the query and search again " +
        "rather than settling for a weak answer. " +
        "Otherwise answer normally, in the user's language. " +
        "The user reads your answers on a phone screen: keep formatting compact. " +
        "Avoid wide markdown tables - use at most 3 columns with terse cell values " +
        "(a few words), and prefer bulleted lists over tables when comparing many " +
        "attributes. Do not use images or LaTeX; plain markdown only." +
        factsSection
}
