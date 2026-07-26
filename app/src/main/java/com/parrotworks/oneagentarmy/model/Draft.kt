package com.parrotworks.oneagentarmy.model

// Unsent input for a conversation - text and/or a staged attachment - persisted so it
// survives the app being backgrounded, locked, or killed by the system before it's sent.
//
// It also carries the choices made for a conversation that has no row of its own yet: a
// conversation is only created on its first message, so until then there is nowhere else to
// keep them. Restoring the typed text but silently reverting the model would be worse than
// losing both - you would send on a different model than the one shown when you walked away.
data class Draft(
    val text: String,
    val attachment: PendingAttachment?,
    val modelId: String? = null,
    val contextWindowOverride: Int? = null,
    val factIds: Set<String> = emptySet(),
) {
    // Nothing worth keeping - the draft row is deleted rather than stored blank. Note that a
    // picked model alone counts as worth keeping, which is the whole point: choosing a model
    // and walking away without typing anything still has to survive.
    fun isEmpty(): Boolean =
        text.isBlank() &&
            attachment == null &&
            modelId == null &&
            contextWindowOverride == null &&
            factIds.isEmpty()
}
