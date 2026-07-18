package com.piotrek.oneagentarmy.provider.ai.tools

// A tool the provider executes itself and feeds back to the model as a role:"tool"
// message (web search, weather) - as opposed to tools with client-side effects that
// are handed to the UI for confirmation (calendar, alarms, SMS drafts).
interface RoundTripToolExecutor {
    val toolName: String

    // Returns the tool result as text for the model; throws AiProviderException on failure.
    suspend fun execute(argumentsJson: String): String
}
