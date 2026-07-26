package com.parrotworks.oneagentarmy.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parrotworks.oneagentarmy.data.local.AttachmentStore
import com.parrotworks.oneagentarmy.data.local.AttachmentTooLargeException
import com.parrotworks.oneagentarmy.data.repository.ConversationRepository
import com.parrotworks.oneagentarmy.data.repository.ExchangeRateRepository
import com.parrotworks.oneagentarmy.data.repository.FactRepository
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.model.DeliveryFailure
import com.parrotworks.oneagentarmy.model.Draft
import com.parrotworks.oneagentarmy.model.Fact
import com.parrotworks.oneagentarmy.model.Message
import com.parrotworks.oneagentarmy.model.PendingAttachment
import com.parrotworks.oneagentarmy.model.Sender
import com.parrotworks.oneagentarmy.provider.ai.AiModelOption
import com.parrotworks.oneagentarmy.provider.ai.AiProvider
import com.parrotworks.oneagentarmy.provider.ai.AiProviderException
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.provider.ai.AiReply
import com.parrotworks.oneagentarmy.provider.ai.ContextWindowStrategies
import com.parrotworks.oneagentarmy.provider.ai.TokenUsage
import com.parrotworks.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.parrotworks.oneagentarmy.tools.calendar.CREATE_CALENDAR_EVENT_TOOL
import com.parrotworks.oneagentarmy.tools.calendar.CalendarEventArgumentsParser
import com.parrotworks.oneagentarmy.tools.calendar.CalendarEventDraft
import com.parrotworks.oneagentarmy.tools.calendar.OPEN_CALENDAR_AT_TOOL
import com.parrotworks.oneagentarmy.tools.calendar.OpenCalendarDraft
import com.parrotworks.oneagentarmy.tools.calendar.parseOpenCalendarArgs
import com.parrotworks.oneagentarmy.tools.clock.AlarmDraft
import com.parrotworks.oneagentarmy.tools.clock.SET_ALARM_TOOL
import com.parrotworks.oneagentarmy.tools.clock.SET_TIMER_TOOL
import com.parrotworks.oneagentarmy.tools.clock.TimerDraft
import com.parrotworks.oneagentarmy.tools.clock.parseAlarmArgs
import com.parrotworks.oneagentarmy.tools.clock.parseTimerArgs
import com.parrotworks.oneagentarmy.tools.navigation.NAVIGATE_TO_TOOL
import com.parrotworks.oneagentarmy.tools.navigation.NavigationDraft
import com.parrotworks.oneagentarmy.tools.navigation.parseNavigationArgs
import com.parrotworks.oneagentarmy.tools.notes.CREATE_NOTE_TOOL
import com.parrotworks.oneagentarmy.tools.notes.NoteDraft
import com.parrotworks.oneagentarmy.tools.notes.parseNoteArgs
import com.parrotworks.oneagentarmy.tools.sms.DRAFT_SMS_TOOL
import com.parrotworks.oneagentarmy.tools.sms.SmsDraft
import com.parrotworks.oneagentarmy.tools.sms.parseSmsArgs
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

sealed interface ChatError {
    data object MissingApiKey : ChatError
    data class InvalidApiKey(val detail: String?) : ChatError
    data class NoConnectivity(val detail: String?) : ChatError
    data class Timeout(val timeoutSeconds: Int, val detail: String?) : ChatError
    data class RateLimited(val retryAfterSeconds: Int?, val detail: String?) : ChatError
    data class ServerError(val statusCode: Int, val detail: String?) : ChatError
    data class Unknown(val detail: String) : ChatError
    data object ToolArguments : ChatError
    data object NoAppForAction : ChatError
    data object AttachmentTooLarge : ChatError
    data object PdfTooLarge : ChatError
}

sealed interface PendingAction {
    data class CreateCalendarEvent(val draft: CalendarEventDraft) : PendingAction
    data class SetAlarm(val draft: AlarmDraft) : PendingAction
    data class SetTimer(val draft: TimerDraft) : PendingAction
    data class DraftSms(val draft: SmsDraft) : PendingAction
    data class Navigate(val draft: NavigationDraft) : PendingAction
    data class OpenCalendarDate(val draft: OpenCalendarDraft) : PendingAction
    data class CreateNote(val draft: NoteDraft) : PendingAction
}

class ChatViewModel(
    private val conversationId: String,
    private val repository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val factRepository: FactRepository,
    private val aiProvider: AiProvider,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val attachmentStore: AttachmentStore,
) : ViewModel() {

    init {
        viewModelScope.launch { exchangeRateRepository.refreshIfStale() }
        viewModelScope.launch {
            repository.observeDraft(conversationId).first()?.let { draft ->
                _draftText.value = draft.text
                _pendingAttachment.value = draft.attachment
                pendingModel.value = draft.modelId
                pendingContextWindowOverride.value = draft.contextWindowOverride
                if (draft.factIds.isNotEmpty()) {
                    // Nothing can stop a fact from being deleted while the draft sits unsent
                    // (no foreign key is possible - see DraftEntity.factIds), so ids are kept
                    // only if they still resolve. Restoring a dangling one would show a
                    // selection count that doesn't match any visible fact.
                    val stillExists = factRepository.observeFacts().first().mapTo(mutableSetOf()) { it.id }
                    pendingFactIds.value = draft.factIds.intersect(stillExists)
                }
            }
        }
    }

    val usdToEur: StateFlow<Double> = exchangeRateRepository.usdToEur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.86)

    val chatFontScale: StateFlow<Float> = settingsRepository.observeChatFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val requestTimeoutSeconds: StateFlow<Int> = settingsRepository.observeRequestTimeoutSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_REQUEST_TIMEOUT_SECONDS)

    val messages: StateFlow<List<Message>> = repository.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversationTitle: StateFlow<String?> = repository.observeConversation(conversationId)
        .map { it?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conversationCost: StateFlow<Double?> = repository.observeConversationCost(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Model chosen in this screen before the conversation exists in the database.
    // Once the conversation row exists, its persisted modelId wins.
    private val pendingModel = MutableStateFlow<String?>(null)

    val selectedModel: StateFlow<String?> = combine(
        repository.observeConversation(conversationId),
        pendingModel,
        settingsRepository.observeActiveProvider(),
    ) { conversation, pending, activeProvider ->
        conversation?.modelId ?: pending ?: AiProviderRegistry.defaultModelFor(activeProvider)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Combined with the registry flow so the picker updates live when a remote model
    // catalog refresh lands (not just on the next screen open).
    val availableModels: StateFlow<List<AiModelOption>> = combine(
        settingsRepository.observeActiveProvider(),
        AiProviderRegistry.providersFlow,
    ) { providerId, providers ->
        providers.firstOrNull { it.id == providerId }?.models.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Selectable (non-global) facts shown in the chat's fact picker.
    val selectableFacts: StateFlow<List<Fact>> = factRepository.observeFacts()
        .map { facts -> facts.filter { !it.isGlobal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Facts toggled before the conversation exists in the database - same trick as pendingModel.
    private val pendingFactIds = MutableStateFlow<Set<String>>(emptySet())

    val selectedFactIds: StateFlow<Set<String>> = combine(
        repository.observeConversation(conversationId),
        factRepository.observeSelectedFactIds(conversationId),
        pendingFactIds,
    ) { conversation, persisted, pending ->
        if (conversation != null) persisted else pending
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFact(factId: String) {
        viewModelScope.launch {
            val currentlySelected = factId in selectedFactIds.value
            if (repository.conversationExists(conversationId)) {
                factRepository.setFactSelected(conversationId, factId, !currentlySelected)
            } else {
                pendingFactIds.value =
                    if (currentlySelected) pendingFactIds.value - factId else pendingFactIds.value + factId
                persistDraft()
            }
        }
    }

    // Override chosen before the conversation exists in the database - same trick as
    // pendingModel/pendingFactIds.
    private val pendingContextWindowOverride = MutableStateFlow<Int?>(null)

    // Null means "use the global default" - surfaced as-is so the UI can show either the
    // explicit override or a "default (N)" placeholder.
    val contextWindowOverride: StateFlow<Int?> = combine(
        repository.observeConversation(conversationId),
        pendingContextWindowOverride,
    ) { conversation, pending ->
        if (conversation != null) conversation.contextWindowOverride else pending
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val effectiveContextWindowSize: StateFlow<Int> = combine(
        contextWindowOverride,
        settingsRepository.observeContextWindowSize(),
    ) { override, globalDefault -> override ?: globalDefault }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_CONTEXT_WINDOW_SIZE)

    fun setContextWindowOverride(value: Int?) {
        viewModelScope.launch {
            pendingContextWindowOverride.value = value
            if (repository.conversationExists(conversationId)) {
                repository.setContextWindowOverride(conversationId, value)
            } else {
                persistDraft()
            }
        }
    }

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<ChatError?>(null)
    val error: StateFlow<ChatError?> = _error.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    // Usage/cost of the tool-call turn behind the pending action - attached to the
    // locally-generated summary note once the user confirms or cancels.
    private var pendingActionUsage: TokenUsage? = null
    private var pendingActionCost: Double? = null

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    // Unsent message text, persisted to the drafts table so it survives the app being
    // locked, backgrounded and reclaimed by the system, or fully closed. Kept in memory
    // for instant typing feedback; writes to disk are debounced so every keystroke doesn't
    // hit the database.
    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private var draftSaveJob: Job? = null

    fun updateDraftText(text: String) {
        _draftText.value = text
        scheduleDraftSave()
    }

    private fun scheduleDraftSave() {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            persistDraft()
        }
    }

    private suspend fun persistDraft() {
        // The model/facts/context-window choices are only worth carrying while there is no
        // conversation row to hold them. Once it exists that row is the single source of
        // truth, and copying the values into the draft as well would leave a stray draft row
        // behind every time the text is cleared.
        val unsent = !repository.conversationExists(conversationId)
        val draft = Draft(
            text = _draftText.value,
            attachment = _pendingAttachment.value,
            modelId = pendingModel.value.takeIf { unsent },
            contextWindowOverride = pendingContextWindowOverride.value.takeIf { unsent },
            factIds = if (unsent) pendingFactIds.value else emptySet(),
        )
        if (draft.isEmpty()) {
            repository.clearDraft(conversationId)
        } else {
            repository.saveDraft(conversationId, draft)
        }
    }

    private fun persistDraftNow() {
        draftSaveJob?.cancel()
        viewModelScope.launch { persistDraft() }
    }

    // The debounced save above runs on viewModelScope, which the framework cancels before
    // onCleared() is even entered - a save still waiting out its delay is lost the instant
    // this fires. Hooking every individual way to leave the screen (the arrow button, the
    // system back gesture, navigating to Settings from within chat...) would be fragile and
    // easy to under-cover - this screen, for instance, has no BackHandler at all, so the
    // system back gesture bypasses the on-screen arrow entirely and would have skipped a
    // save wired only there. onCleared runs no matter which exit path was taken, so the
    // flush is placed here instead, once.
    //
    // Blocking briefly is deliberate: persistDraft is a single local SQLite upsert (no
    // network, no large payload), and runBlocking is the only way to guarantee it completes
    // before the ViewModel - and viewModelScope with it - is gone.
    override fun onCleared() {
        runBlocking {
            persistDraft()
            // A tool-call card the user walked away from still cost a billed API call, and
            // its usage/cost live only in memory until persistAiNote writes them down.
            // Leaving without answering would drop the money *and* every trace of the turn -
            // the same silent gap deliveryFailure exists to prevent for failed requests.
            if (_pendingAction.value != null) {
                abandonedActionNote?.let { persistAiNote(it) }
            }
        }
    }

    // Handed over by the UI because onCleared has no Context, and no opportunity to ask for
    // one - the same reason the confirm/cancel notes are passed in rather than built here.
    private var abandonedActionNote: String? = null

    fun setAbandonedActionNote(note: String) {
        abandonedActionNote = note
    }

    // A picked photo/PDF is written into the attachments directory immediately, but until the
    // message is actually sent the only thing referencing that file is the pending draft.
    // Replacing or discarding the staged attachment therefore has to delete the file as well,
    // or it is stranded on disk permanently - these live in filesDir, which (unlike a cache
    // directory) Android never reclaims on its own.
    //
    // Deliberately NOT called from the send path: once the message row exists it owns that
    // very same path, so clearing the pending reference there must leave the file alone.
    private fun deleteStagedMedia(attachment: PendingAttachment?) {
        val path = (attachment as? PendingAttachment.Media)?.path ?: return
        viewModelScope.launch { attachmentStore.deleteAll(listOf(path)) }
    }

    fun attachFile(name: String, content: String) {
        if (content.length > MAX_ATTACHMENT_CHARS) {
            _error.value = ChatError.AttachmentTooLarge
            return
        }
        val replaced = _pendingAttachment.value
        _pendingAttachment.value = PendingAttachment.TextFile(name, content)
        persistDraftNow()
        deleteStagedMedia(replaced)
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val saved = attachmentStore.saveImage(uri)
                // Captured only after the save succeeded - a failed pick must not throw away
                // the attachment that is already staged.
                val replaced = _pendingAttachment.value
                _pendingAttachment.value = PendingAttachment.Media(
                    type = Message.ATTACHMENT_TYPE_IMAGE,
                    path = saved.path,
                    mime = saved.mime,
                    name = saved.name,
                )
                persistDraftNow()
                deleteStagedMedia(replaced)
            } catch (e: Exception) {
                _error.value = ChatError.Unknown(e.message ?: "image attachment failed")
            }
        }
    }

    fun attachPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                val saved = attachmentStore.savePdf(uri)
                val replaced = _pendingAttachment.value
                _pendingAttachment.value = PendingAttachment.Media(
                    type = Message.ATTACHMENT_TYPE_PDF,
                    path = saved.path,
                    mime = saved.mime,
                    name = saved.name,
                )
                persistDraftNow()
                deleteStagedMedia(replaced)
            } catch (e: AttachmentTooLargeException) {
                _error.value = ChatError.PdfTooLarge
            } catch (e: Exception) {
                _error.value = ChatError.Unknown(e.message ?: "pdf attachment failed")
            }
        }
    }

    // Resolves a stored attachment path for UI display (thumbnail decoding).
    fun attachmentAbsolutePath(path: String): String = attachmentStore.absolutePath(path)

    fun clearAttachment() {
        val discarded = _pendingAttachment.value
        _pendingAttachment.value = null
        persistDraftNow()
        deleteStagedMedia(discarded)
    }

    fun reportAttachmentError(detail: String) {
        _error.value = ChatError.Unknown(detail)
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            pendingModel.value = modelId
            if (repository.conversationExists(conversationId)) {
                repository.updateConversationModel(conversationId, modelId)
            } else {
                // No conversation row yet - the draft is the only place this can live, and it
                // has to be written now rather than waiting for a keystroke to trigger the
                // debounced save: picking a model and leaving without typing is exactly the
                // case that used to silently revert to the default.
                persistDraft()
            }
        }
    }

    fun sendMessage(text: String) {
        val attachment = _pendingAttachment.value
        if (text.isBlank() && attachment == null) return
        // Guards against a double-send from a rapid double-tap slipping in before the Send
        // button's `enabled = !isSending` has recomposed - relying on the UI state alone
        // isn't enough since both taps can land in the same frame.
        if (_isSending.value) return

        draftSaveJob?.cancel()
        _draftText.value = ""

        viewModelScope.launch {
            _error.value = null
            // A new user message supersedes any unconfirmed action - the model will
            // re-emit a corrected tool call if the user is refining the request.
            _pendingAction.value = null
            repository.clearDraft(conversationId)

            val modelId = currentModelId()
            // Captured before any mutation - the combined flow may lag right after the
            // pending selections are persisted below.
            val selectedIds = selectedFactIds.value

            // Text files are inlined into the message text (replayed like any other
            // text); media attachments ride as metadata and are sent to the API as
            // native multimodal blocks by the providers.
            val messageText = when (attachment) {
                null, is PendingAttachment.Media -> text
                is PendingAttachment.TextFile -> buildString {
                    append("📎 ").append(attachment.name).append("\n\n").append(attachment.content)
                    if (text.isNotBlank()) append("\n\n").append(text)
                }
            }
            val media = attachment as? PendingAttachment.Media
            _pendingAttachment.value = null

            if (messages.value.isEmpty()) {
                val titleSource = text.ifBlank { attachment?.name ?: messageText }
                repository.createConversation(conversationId, deriveTitle(titleSource), modelId)
                // Persist fact selections made before the conversation row existed.
                pendingFactIds.value.forEach { factId ->
                    factRepository.setFactSelected(conversationId, factId, true)
                }
                pendingFactIds.value = emptySet()
                // Persist a context window override chosen before the conversation row existed.
                pendingContextWindowOverride.value?.let { override ->
                    repository.setContextWindowOverride(conversationId, override)
                }
                // This conversation just graduated from "reserved new-conversation id" to a
                // real row - if it was the one reused across "New conversation" taps, that
                // reservation is now spent, so the next tap must mint a fresh one rather
                // than reopening this (now real, already-in-the-list) conversation.
                if (settingsRepository.getPendingNewConversationId() == conversationId) {
                    settingsRepository.setPendingNewConversationId(null)
                }
            }

            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = Sender.USER,
                text = messageText,
                timestamp = Instant.now(),
                attachmentType = media?.type,
                attachmentPath = media?.path,
                attachmentMime = media?.mime,
                attachmentName = media?.name,
            )
            repository.addMessage(conversationId, userMessage)

            requestAiReply(messages.value + userMessage, modelId, selectedIds)
        }
    }

    // Retry semantics: if the message is the last one in the conversation (its AI reply
    // never arrived), just re-request the reply; otherwise re-send it as a new message.
    fun resendMessage(message: Message) {
        // The resend button has no `enabled = !isSending` guard in the UI (unlike Send),
        // so this is the only thing stopping a rapid double-tap from firing two requests -
        // duplicating the message and the API cost.
        if (_isSending.value) return

        viewModelScope.launch {
            _error.value = null

            val modelId = currentModelId()
            val selectedIds = selectedFactIds.value
            val current = messages.value

            if (current.lastOrNull()?.id == message.id) {
                requestAiReply(current, modelId, selectedIds)
            } else {
                val copy = message.copy(id = UUID.randomUUID().toString(), timestamp = Instant.now())
                repository.addMessage(conversationId, copy)
                requestAiReply(current + copy, modelId, selectedIds)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    // Called by the UI after the calendar intent was fired successfully; summaryText is
    // localized and formatted in the UI layer (the ViewModel has no Context).
    fun confirmPendingAction(summaryText: String) {
        _pendingAction.value = null
        viewModelScope.launch { persistAiNote(summaryText) }
    }

    // The persisted note tells the model the user declined - without it, the next request
    // would show an unanswered scheduling request and the model would re-emit the tool call.
    fun cancelPendingAction(cancelNote: String) {
        _pendingAction.value = null
        viewModelScope.launch { persistAiNote(cancelNote) }
    }

    fun reportNoAppForAction() {
        _pendingAction.value = null
        _error.value = ChatError.NoAppForAction
    }

    private suspend fun currentModelId(): String =
        selectedModel.value
            ?: AiProviderRegistry.defaultModelFor(settingsRepository.observeActiveProvider().first())

    private suspend fun activeFactContents(selectedIds: Set<String>): List<String> =
        factRepository.observeFacts().first()
            .filter { it.isGlobal || it.id in selectedIds }
            .map { it.content }

    private suspend fun requestAiReply(history: List<Message>, modelId: String, selectedIds: Set<String>) {
        // The message this reply belongs to. If nothing comes back, the reason is written
        // onto it, so the gap in the transcript explains itself instead of looking like a
        // message that vanished - the error banner below is in-memory and does not survive
        // leaving the screen.
        val awaiting = history.lastOrNull { it.sender == Sender.USER }
        var failure: String? = null
        _isSending.value = true
        try {
            val historyToSend = ContextWindowStrategies.rollingChunked(effectiveContextWindowSize.value).apply(history)
            when (val reply = aiProvider.sendMessage(historyToSend, modelId, activeFactContents(selectedIds))) {
                is AiReply.Text -> repository.addMessage(conversationId, reply.message)
                is AiReply.ToolCall -> {
                    pendingActionUsage = reply.usage
                    pendingActionCost = reply.costUsd
                    // A dispatched tool call is not a failure - the reply arrives later as
                    // a note, once the user confirms or declines the action.
                    dispatchToolCall(reply.request)?.let { toolError ->
                        _error.value = toolError
                        failure = toolError.deliveryFailureCode()
                    }
                }
            }
        } catch (e: CancellationException) {
            // The screen was closed mid-request. Not a delivery failure, and rethrowing
            // is what keeps structured concurrency working.
            throw e
        } catch (e: Exception) {
            // Deliberately broader than AiProviderException: a malformed 200 body throws
            // SerializationException, which used to escape this catch, kill the app, and
            // leave behind exactly the same silent unanswered message.
            val chatError = e.toChatError(requestTimeoutSeconds.value)
            _error.value = chatError
            failure = chatError.deliveryFailureCode()
        } finally {
            _isSending.value = false
        }
        // Clearing on success is how a resend heals the transcript. Guarded so the ordinary
        // null -> null case doesn't write (and re-emit the message flow) on every turn.
        if (awaiting != null && awaiting.deliveryFailure != failure) {
            repository.setDeliveryFailure(awaiting.id, failure)
        }
    }

    private fun dispatchToolCall(request: ToolCallRequest): ChatError? {
        val parser = actionParsers[request.name] ?: return ChatError.ToolArguments
        val action = try {
            parser(request.argumentsJson)
        } catch (e: Exception) {
            return ChatError.ToolArguments
        }
        _pendingAction.value = action
        return null
    }

    private val actionParsers: Map<String, (String) -> PendingAction> = mapOf(
        CREATE_CALENDAR_EVENT_TOOL to { args ->
            PendingAction.CreateCalendarEvent(CalendarEventArgumentsParser.parse(args, ZoneId.systemDefault()))
        },
        SET_ALARM_TOOL to { args -> PendingAction.SetAlarm(parseAlarmArgs(args)) },
        SET_TIMER_TOOL to { args -> PendingAction.SetTimer(parseTimerArgs(args)) },
        DRAFT_SMS_TOOL to { args -> PendingAction.DraftSms(parseSmsArgs(args)) },
        NAVIGATE_TO_TOOL to { args -> PendingAction.Navigate(parseNavigationArgs(args)) },
        OPEN_CALENDAR_AT_TOOL to { args -> PendingAction.OpenCalendarDate(parseOpenCalendarArgs(args)) },
        CREATE_NOTE_TOOL to { args -> PendingAction.CreateNote(parseNoteArgs(args)) },
    )

    private suspend fun persistAiNote(text: String) {
        val usage = pendingActionUsage
        val cost = pendingActionCost
        pendingActionUsage = null
        pendingActionCost = null
        repository.addMessage(
            conversationId,
            Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = Sender.AI,
                text = text,
                timestamp = Instant.now(),
                inputTokens = usage?.totalInputTokens,
                outputTokens = usage?.outputTokens,
                costUsd = cost,
            ),
        )
    }
}

// Inlined attachments ride along with every later request in the context window -
// the cap keeps a single file from dominating token costs.
private const val MAX_ATTACHMENT_CHARS = 30_000

// Delay before a draft text change is written to disk - long enough that a normal typing
// burst only triggers one write, short enough that a quick app-switch rarely loses anything.
private const val DRAFT_SAVE_DEBOUNCE_MS = 500L

private fun deriveTitle(messageText: String): String {
    val singleLine = messageText.replace('\n', ' ').trim()
    return if (singleLine.length <= 50) singleLine else singleLine.take(50) + "…"
}

// timeoutSeconds is passed in because the exception itself doesn't know what the limit was
// configured to - only the banner needs to state it.
internal fun Throwable.toChatError(timeoutSeconds: Int): ChatError = when (this) {
    is AiProviderException.MissingApiKey -> ChatError.MissingApiKey
    is AiProviderException.InvalidApiKey -> ChatError.InvalidApiKey(detail)
    is AiProviderException.NoConnectivity -> ChatError.NoConnectivity(detail)
    is AiProviderException.Timeout -> ChatError.Timeout(timeoutSeconds, detail)
    is AiProviderException.RateLimited -> ChatError.RateLimited(retryAfterSeconds, detail)
    is AiProviderException.ServerError -> ChatError.ServerError(statusCode, detail)
    is AiProviderException.Unknown -> ChatError.Unknown(detail)
    // Not a provider failure but a bug on our side. Naming the exception type is the only
    // clue there is, so it goes in rather than a generic "something went wrong".
    else -> ChatError.Unknown("${javaClass.simpleName}: ${message ?: "no message"}")
}

// The durable counterpart of the banner - see DeliveryFailure for why these are frozen.
internal fun ChatError.deliveryFailureCode(): String = when (this) {
    is ChatError.MissingApiKey -> DeliveryFailure.MISSING_API_KEY
    is ChatError.InvalidApiKey -> DeliveryFailure.INVALID_API_KEY
    is ChatError.NoConnectivity -> DeliveryFailure.NO_CONNECTIVITY
    is ChatError.Timeout -> DeliveryFailure.TIMEOUT
    is ChatError.RateLimited -> DeliveryFailure.RATE_LIMITED
    is ChatError.ServerError -> DeliveryFailure.SERVER_ERROR
    is ChatError.ToolArguments -> DeliveryFailure.TOOL_ARGUMENTS
    // The remaining cases never reach a message: attachment errors happen before sending
    // and NoAppForAction after the reply already arrived.
    else -> DeliveryFailure.UNEXPECTED
}
