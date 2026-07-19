package com.piotrek.oneagentarmy.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piotrek.oneagentarmy.data.repository.ConversationRepository
import com.piotrek.oneagentarmy.data.repository.ExchangeRateRepository
import com.piotrek.oneagentarmy.data.repository.FactRepository
import com.piotrek.oneagentarmy.data.repository.SettingsRepository
import com.piotrek.oneagentarmy.model.Fact
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiModelOption
import com.piotrek.oneagentarmy.provider.ai.AiProvider
import com.piotrek.oneagentarmy.provider.ai.AiProviderException
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.AiReply
import com.piotrek.oneagentarmy.provider.ai.ContextWindowStrategy
import com.piotrek.oneagentarmy.provider.ai.TokenUsage
import com.piotrek.oneagentarmy.provider.ai.tools.ToolCallRequest
import com.piotrek.oneagentarmy.tools.calendar.CREATE_CALENDAR_EVENT_TOOL
import com.piotrek.oneagentarmy.tools.calendar.CalendarEventArgumentsParser
import com.piotrek.oneagentarmy.tools.calendar.CalendarEventDraft
import com.piotrek.oneagentarmy.tools.calendar.OPEN_CALENDAR_AT_TOOL
import com.piotrek.oneagentarmy.tools.calendar.OpenCalendarDraft
import com.piotrek.oneagentarmy.tools.calendar.parseOpenCalendarArgs
import com.piotrek.oneagentarmy.tools.clock.AlarmDraft
import com.piotrek.oneagentarmy.tools.clock.SET_ALARM_TOOL
import com.piotrek.oneagentarmy.tools.clock.SET_TIMER_TOOL
import com.piotrek.oneagentarmy.tools.clock.TimerDraft
import com.piotrek.oneagentarmy.tools.clock.parseAlarmArgs
import com.piotrek.oneagentarmy.tools.clock.parseTimerArgs
import com.piotrek.oneagentarmy.tools.navigation.NAVIGATE_TO_TOOL
import com.piotrek.oneagentarmy.tools.navigation.NavigationDraft
import com.piotrek.oneagentarmy.tools.navigation.parseNavigationArgs
import com.piotrek.oneagentarmy.tools.notes.CREATE_NOTE_TOOL
import com.piotrek.oneagentarmy.tools.notes.NoteDraft
import com.piotrek.oneagentarmy.tools.notes.parseNoteArgs
import com.piotrek.oneagentarmy.tools.sms.DRAFT_SMS_TOOL
import com.piotrek.oneagentarmy.tools.sms.SmsDraft
import com.piotrek.oneagentarmy.tools.sms.parseSmsArgs
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatError {
    data object MissingApiKey : ChatError
    data class InvalidApiKey(val detail: String?) : ChatError
    data class NoConnectivity(val detail: String?) : ChatError
    data class RateLimited(val retryAfterSeconds: Int?, val detail: String?) : ChatError
    data class ServerError(val statusCode: Int, val detail: String?) : ChatError
    data class Unknown(val detail: String) : ChatError
    data object ToolArguments : ChatError
    data object NoAppForAction : ChatError
    data object AttachmentTooLarge : ChatError
}

// Text file (txt/md/csv) staged for the next message - its content is inlined
// into the message text on send.
data class PendingAttachment(val name: String, val content: String)

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
    private val contextWindowStrategy: ContextWindowStrategy,
    private val exchangeRateRepository: ExchangeRateRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { exchangeRateRepository.refreshIfStale() }
    }

    val usdToEur: StateFlow<Double> = exchangeRateRepository.usdToEur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.86)

    val chatFontScale: StateFlow<Float> = settingsRepository.observeChatFontScale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

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

    val availableModels: StateFlow<List<AiModelOption>> = settingsRepository.observeActiveProvider()
        .map { AiProviderRegistry.byId(it)?.models.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            if (repository.observeConversation(conversationId).first() != null) {
                factRepository.setFactSelected(conversationId, factId, !currentlySelected)
            } else {
                pendingFactIds.value =
                    if (currentlySelected) pendingFactIds.value - factId else pendingFactIds.value + factId
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

    fun attachFile(name: String, content: String) {
        if (content.length > MAX_ATTACHMENT_CHARS) {
            _error.value = ChatError.AttachmentTooLarge
            return
        }
        _pendingAttachment.value = PendingAttachment(name, content)
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
    }

    fun reportAttachmentError(detail: String) {
        _error.value = ChatError.Unknown(detail)
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            pendingModel.value = modelId
            if (repository.observeConversation(conversationId).first() != null) {
                repository.updateConversationModel(conversationId, modelId)
            }
        }
    }

    fun sendMessage(text: String) {
        val attachment = _pendingAttachment.value
        if (text.isBlank() && attachment == null) return

        viewModelScope.launch {
            _error.value = null
            // A new user message supersedes any unconfirmed action - the model will
            // re-emit a corrected tool call if the user is refining the request.
            _pendingAction.value = null

            val modelId = currentModelId()
            // Captured before any mutation - the combined flow may lag right after the
            // pending selections are persisted below.
            val selectedIds = selectedFactIds.value

            // Attachment content is inlined into the message text - it becomes part of
            // the conversation history and is replayed to the API like any other text.
            val messageText = if (attachment == null) {
                text
            } else {
                buildString {
                    append("📎 ").append(attachment.name).append("\n\n").append(attachment.content)
                    if (text.isNotBlank()) append("\n\n").append(text)
                }
            }
            _pendingAttachment.value = null

            if (messages.value.isEmpty()) {
                val titleSource = text.ifBlank { attachment?.name ?: messageText }
                repository.createConversation(conversationId, deriveTitle(titleSource), modelId)
                // Persist fact selections made before the conversation row existed.
                pendingFactIds.value.forEach { factId ->
                    factRepository.setFactSelected(conversationId, factId, true)
                }
                pendingFactIds.value = emptySet()
            }

            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                sender = Sender.USER,
                text = messageText,
                timestamp = Instant.now(),
            )
            repository.addMessage(conversationId, userMessage)

            requestAiReply(messages.value + userMessage, modelId, selectedIds)
        }
    }

    // Retry semantics: if the message is the last one in the conversation (its AI reply
    // never arrived), just re-request the reply; otherwise re-send it as a new message.
    fun resendMessage(message: Message) {
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
        _isSending.value = true
        try {
            val historyToSend = contextWindowStrategy.apply(history)
            when (val reply = aiProvider.sendMessage(historyToSend, modelId, activeFactContents(selectedIds))) {
                is AiReply.Text -> repository.addMessage(conversationId, reply.message)
                is AiReply.ToolCall -> {
                    pendingActionUsage = reply.usage
                    pendingActionCost = reply.costUsd
                    dispatchToolCall(reply.request)
                }
            }
        } catch (e: AiProviderException) {
            _error.value = e.toChatError()
        } finally {
            _isSending.value = false
        }
    }

    private fun dispatchToolCall(request: ToolCallRequest) {
        val parser = actionParsers[request.name]
        if (parser == null) {
            _error.value = ChatError.ToolArguments
            return
        }
        val action = try {
            parser(request.argumentsJson)
        } catch (e: Exception) {
            _error.value = ChatError.ToolArguments
            return
        }
        _pendingAction.value = action
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
                inputTokens = usage?.inputTokens,
                outputTokens = usage?.outputTokens,
                costUsd = cost,
            ),
        )
    }
}

// Inlined attachments ride along with every later request in the context window -
// the cap keeps a single file from dominating token costs.
private const val MAX_ATTACHMENT_CHARS = 30_000

private fun deriveTitle(messageText: String): String {
    val singleLine = messageText.replace('\n', ' ').trim()
    return if (singleLine.length <= 50) singleLine else singleLine.take(50) + "…"
}

private fun AiProviderException.toChatError(): ChatError = when (this) {
    is AiProviderException.MissingApiKey -> ChatError.MissingApiKey
    is AiProviderException.InvalidApiKey -> ChatError.InvalidApiKey(detail)
    is AiProviderException.NoConnectivity -> ChatError.NoConnectivity(detail)
    is AiProviderException.RateLimited -> ChatError.RateLimited(retryAfterSeconds, detail)
    is AiProviderException.ServerError -> ChatError.ServerError(statusCode, detail)
    is AiProviderException.Unknown -> ChatError.Unknown(detail)
}
