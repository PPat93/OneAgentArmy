package com.parrotworks.oneagentarmy.model

// Why a request never produced a reply, recorded on the user message that went unanswered.
//
// The error banner in the chat lives in memory only, so a failure used to leave no trace at
// all once the screen was left or the process died - all that remained was a message with
// nothing after it, indistinguishable from data loss. These codes are the durable record.
//
// They are written to the database, so the string values are frozen: renaming one silently
// turns every row already carrying it into UNEXPECTED. Add new codes instead.
object DeliveryFailure {
    const val MISSING_API_KEY = "missing_api_key"
    const val INVALID_API_KEY = "invalid_api_key"
    const val NO_CONNECTIVITY = "no_connectivity"
    const val TIMEOUT = "timeout"

    // The connection died before the configured timeout could possibly have expired, so
    // blaming that timeout would point the user at the wrong setting. Distinct from
    // NO_CONNECTIVITY, which means the request never got off the ground at all.
    const val CONNECTION_LOST = "connection_lost"
    const val RATE_LIMITED = "rate_limited"
    const val SERVER_ERROR = "server_error"
    const val TOOL_ARGUMENTS = "tool_arguments"

    // Anything that isn't a recognised provider failure - including bugs on our side, which
    // is the whole point of catching broadly: a crash used to leave the same silent gap.
    const val UNEXPECTED = "unexpected"
}
