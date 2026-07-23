package com.parrotworks.oneagentarmy.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.data.repository.SettingsRepository
import com.parrotworks.oneagentarmy.model.PendingAttachment
import com.parrotworks.oneagentarmy.model.Sender
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.tools.calendar.CalendarIntentBuilder
import com.parrotworks.oneagentarmy.ui.components.WaveLoadingIndicator
import com.parrotworks.oneagentarmy.ui.components.formatCostEur
import com.parrotworks.oneagentarmy.ui.components.shareText
import com.parrotworks.oneagentarmy.ui.settings.formatTimeout
import kotlinx.coroutines.delay
import com.parrotworks.oneagentarmy.tools.calendar.buildOpenCalendarIntent
import com.parrotworks.oneagentarmy.tools.clock.buildAlarmIntent
import com.parrotworks.oneagentarmy.tools.clock.buildTimerIntent
import com.parrotworks.oneagentarmy.tools.navigation.buildNavigationIntent
import com.parrotworks.oneagentarmy.tools.notes.buildNoteIntent
import com.parrotworks.oneagentarmy.tools.sms.buildSmsIntent
import com.parrotworks.oneagentarmy.model.Message
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Text-ish types only - binary spreadsheets (xls/xlsx) are deliberately excluded;
// if a picker greys out a CSV, renaming it to .txt is the workaround.
private val TEXT_ATTACHMENT_MIME_TYPES = arrayOf(
    "text/*",
    "text/csv",
    "application/csv",
    "text/comma-separated-values",
)

// Resolves the display name and reads the whole file as text; throws on
// unreadable content - the caller maps that to a chat error.
private fun readTextAttachment(context: Context, uri: Uri): Pair<String, String> {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: "file"
    val content = context.contentResolver.openInputStream(uri)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: throw IOException("Cannot open attachment")
    return name to content
}

private fun buildConversationTranscript(
    title: String,
    messages: List<Message>,
    youLabel: String,
    aiLabel: String,
    attachmentPlaceholder: String,
): String = buildString {
    appendLine(title)
    appendLine()
    messages.forEach { message ->
        val speaker = if (message.sender == Sender.USER) youLabel else aiLabel
        val text = message.text.ifBlank { attachmentPlaceholder }
        appendLine("$speaker: $text")
        appendLine()
    }
}.trimEnd()

// List rows for the chat LazyColumn: messages interleaved with day separators.
private sealed interface ChatListItem {
    data class MessageItem(val message: Message) : ChatListItem
    data class DateHeader(val date: LocalDate) : ChatListItem
}

@Composable
private fun DateHeaderRow(date: LocalDate) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatter.format(date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    focusMessageId: String?,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val error by viewModel.error.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()
    val conversationTitle by viewModel.conversationTitle.collectAsState()
    val conversationCost by viewModel.conversationCost.collectAsState()
    val usdToEur by viewModel.usdToEur.collectAsState()
    val chatFontScale by viewModel.chatFontScale.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectableFacts by viewModel.selectableFacts.collectAsState()
    val selectedFactIds by viewModel.selectedFactIds.collectAsState()
    val pendingAttachment by viewModel.pendingAttachment.collectAsState()
    val inputText by viewModel.draftText.collectAsState()
    val contextWindowOverride by viewModel.contextWindowOverride.collectAsState()
    val effectiveContextWindowSize by viewModel.effectiveContextWindowSize.collectAsState()
    val requestTimeoutSeconds by viewModel.requestTimeoutSeconds.collectAsState()
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var factsMenuExpanded by remember { mutableStateOf(false) }
    var contextWindowDialogVisible by remember { mutableStateOf(false) }

    // Elapsed time since the in-flight message was sent - purely informational, so the
    // user can see how long a slow flagship model has been thinking.
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isSending) {
        elapsedSeconds = 0
        if (isSending) {
            while (true) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }
    val listState = rememberLazyListState()
    val attachContext = LocalContext.current
    val shareTranscriptTitle = conversationTitle ?: stringResource(R.string.new_conversation)
    val shareYouLabel = stringResource(R.string.share_transcript_you)
    val shareAiLabel = stringResource(R.string.share_transcript_ai)
    val shareAttachmentPlaceholder = stringResource(R.string.share_transcript_attachment)
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { readTextAttachment(attachContext, uri) }
                .onSuccess { (name, content) -> viewModel.attachFile(name, content) }
                .onFailure { viewModel.reportAttachmentError(it.message ?: "attachment read failed") }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attachImage)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::attachPdf)
    }
    var attachMenuExpanded by remember { mutableStateOf(false) }

    // With reverseLayout, index 0 is the newest message and the list is anchored to the
    // bottom natively - immune to bubbles growing after async markdown parsing finishes.
    // A day's header is emitted after its last (oldest-rendered-highest) message, so on
    // screen it appears above that day's messages.
    val chatItems = remember(messages) {
        val reversed = messages.asReversed()
        val zone = ZoneId.systemDefault()
        buildList {
            reversed.forEachIndexed { index, message ->
                add(ChatListItem.MessageItem(message))
                val date = message.timestamp.atZone(zone).toLocalDate()
                val olderDate = reversed.getOrNull(index + 1)?.timestamp?.atZone(zone)?.toLocalDate()
                if (olderDate != date) add(ChatListItem.DateHeader(date))
            }
        }
    }

    var initialScrollHandled by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!initialScrollHandled && focusMessageId != null) {
            // Opened from a search result - land on the matched message instead of the bottom.
            val index = chatItems.indexOfFirst { it is ChatListItem.MessageItem && it.message.id == focusMessageId }
            listState.scrollToItem(index.coerceAtLeast(0))
        } else {
            listState.animateScrollToItem(0)
        }
        initialScrollHandled = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            conversationTitle ?: stringResource(R.string.new_conversation),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        conversationCost?.takeIf { it > 0.0 }?.let { cost ->
                            Text(
                                text = formatCostEur(cost, usdToEur),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            shareText(
                                attachContext,
                                buildConversationTranscript(
                                    shareTranscriptTitle,
                                    messages,
                                    shareYouLabel,
                                    shareAiLabel,
                                    shareAttachmentPlaceholder,
                                ),
                            )
                        },
                        enabled = messages.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_conversation))
                    }
                    Box {
                        IconButton(onClick = { factsMenuExpanded = true }) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = stringResource(R.string.facts_picker),
                                tint = if (selectedFactIds.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = factsMenuExpanded,
                            onDismissRequest = { factsMenuExpanded = false },
                        ) {
                            if (selectableFacts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.facts_picker_empty)) },
                                    onClick = { factsMenuExpanded = false },
                                    enabled = false,
                                )
                            } else {
                                selectableFacts.forEach { fact ->
                                    DropdownMenuItem(
                                        text = { Text(fact.title) },
                                        trailingIcon = {
                                            if (fact.id in selectedFactIds) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        onClick = { viewModel.toggleFact(fact.id) },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { contextWindowDialogVisible = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.context_window_size_title),
                            tint = if (contextWindowOverride == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Box {
                        TextButton(onClick = { modelMenuExpanded = true }) {
                            Text(
                                text = selectedModel?.let { AiProviderRegistry.shortLabelFor(it) } ?: "",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.model_label),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                        ) {
                            availableModels.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.labelFor(polish = Locale.getDefault().language == "pl")) },
                                    onClick = {
                                        viewModel.selectModel(option.id)
                                        modelMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.empty_chat))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        chatItems,
                        key = { item ->
                            when (item) {
                                is ChatListItem.MessageItem -> item.message.id
                                is ChatListItem.DateHeader -> "date-${item.date}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ChatListItem.MessageItem -> ChatBubble(
                                message = item.message,
                                onResend = if (item.message.sender == Sender.USER) {
                                    { viewModel.resendMessage(item.message) }
                                } else {
                                    null
                                },
                                usdToEur = usdToEur,
                                fontScale = chatFontScale,
                                resolveAttachmentPath = viewModel::attachmentAbsolutePath,
                            )
                            is ChatListItem.DateHeader -> DateHeaderRow(item.date)
                        }
                    }
                }
            }

            error?.let { chatError ->
                ChatErrorBanner(
                    error = chatError,
                    onDismiss = viewModel::dismissError,
                    onNavigateToSettings = onNavigateToSettings,
                )
            }

            pendingAction?.let { action ->
                PendingActionCard(action = action, viewModel = viewModel)
            }

            pendingAttachment?.let { attachment ->
                val chipPrefix = when (attachment) {
                    is PendingAttachment.TextFile -> "📎"
                    is PendingAttachment.Media ->
                        if (attachment.type == Message.ATTACHMENT_TYPE_IMAGE) "🖼" else "📄"
                }
                AssistChip(
                    onClick = viewModel::clearAttachment,
                    label = { Text("$chipPrefix ${attachment.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove_attachment),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            // Reassurance once a request has been running for 60% of the configured
            // timeout - flagship/reasoning models legitimately take minutes.
            if (isSending && elapsedSeconds >= requestTimeoutSeconds * 6 / 10) {
                Text(
                    text = stringResource(R.string.slow_request_warning, formatTimeout(requestTimeoutSeconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(
                        onClick = { attachMenuExpanded = true },
                        enabled = !isSending,
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = stringResource(R.string.attach_file),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = attachMenuExpanded,
                        onDismissRequest = { attachMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_menu_image)) },
                            onClick = {
                                attachMenuExpanded = false
                                imageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_menu_pdf)) },
                            onClick = {
                                attachMenuExpanded = false
                                pdfLauncher.launch(arrayOf("application/pdf"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_menu_text)) },
                            onClick = {
                                attachMenuExpanded = false
                                attachLauncher.launch(TEXT_ATTACHMENT_MIME_TYPES)
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.updateDraftText(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.message_placeholder)) },
                    enabled = !isSending,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (isSending) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WaveLoadingIndicator(modifier = Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp))
                        Text(
                            text = formatTimeout(elapsedSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.sendMessage(inputText) },
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (contextWindowDialogVisible) {
        ContextWindowOverrideDialog(
            override = contextWindowOverride,
            effectiveSize = effectiveContextWindowSize,
            onDismiss = { contextWindowDialogVisible = false },
            onConfirm = { value ->
                viewModel.setContextWindowOverride(value)
                contextWindowDialogVisible = false
            },
        )
    }
}

// Same warning threshold as the global default in Settings - a long context window means
// more resent tokens, and resent attachments are re-billed on every turn until they age
// out of the window.
private const val CONTEXT_WINDOW_WARNING_THRESHOLD = 200

@Composable
private fun ContextWindowOverrideDialog(
    override: Int?,
    effectiveSize: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    var text by remember { mutableStateOf((override ?: effectiveSize).toString()) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.context_window_override_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.context_window_override_dialog_explanation, effectiveSize),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.context_window_size_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                when {
                    (parsed ?: 0) > SettingsRepository.MAX_CONTEXT_WINDOW_SIZE -> Text(
                        stringResource(R.string.context_window_size_max_exceeded, SettingsRepository.MAX_CONTEXT_WINDOW_SIZE),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    (parsed ?: 0) > CONTEXT_WINDOW_WARNING_THRESHOLD -> Text(
                        stringResource(R.string.context_window_size_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.takeIf { it in 1..SettingsRepository.MAX_CONTEXT_WINDOW_SIZE }?.let { onConfirm(it) } },
                enabled = parsed != null && parsed in 1..SettingsRepository.MAX_CONTEXT_WINDOW_SIZE,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null) }) {
                    Text(stringResource(R.string.context_window_override_use_default))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private data class PendingActionUi(
    val title: String,
    val lines: List<String>,
    val intent: android.content.Intent,
    val summary: String,
)

@Composable
private fun PendingActionCard(
    action: PendingAction,
    viewModel: ChatViewModel,
) {
    val context = LocalContext.current
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    }
    val cancelNote = stringResource(R.string.chat_action_cancelled)

    val ui = when (action) {
        is PendingAction.CreateCalendarEvent -> {
            val draft = action.draft
            val start = dateTimeFormatter.format(Instant.ofEpochMilli(draft.startEpochMillis))
            val end = dateTimeFormatter.format(Instant.ofEpochMilli(draft.endEpochMillis))
            PendingActionUi(
                title = stringResource(R.string.pending_calendar_title),
                lines = buildList {
                    add(draft.title)
                    add("$start – $end")
                    if (draft.allDay) add(stringResource(R.string.pending_calendar_all_day))
                    draft.location?.let { add(stringResource(R.string.pending_calendar_location, it)) }
                    draft.description?.let { add(it) }
                    if (draft.attendees.isNotEmpty()) {
                        add(stringResource(R.string.pending_calendar_attendees, draft.attendees.joinToString(", ")))
                    }
                },
                intent = CalendarIntentBuilder.build(draft),
                summary = stringResource(R.string.chat_calendar_summary, draft.title, start, end),
            )
        }
        is PendingAction.SetAlarm -> {
            val time = "%02d:%02d".format(action.draft.hour, action.draft.minute)
            PendingActionUi(
                title = stringResource(R.string.pending_alarm_title),
                lines = listOfNotNull(time, action.draft.label),
                intent = buildAlarmIntent(action.draft),
                summary = stringResource(R.string.chat_alarm_summary, time),
            )
        }
        is PendingAction.SetTimer -> {
            val duration = stringResource(R.string.timer_minutes, action.draft.durationMinutes)
            PendingActionUi(
                title = stringResource(R.string.pending_timer_title),
                lines = listOfNotNull(duration, action.draft.label),
                intent = buildTimerIntent(action.draft),
                summary = stringResource(R.string.chat_timer_summary, duration),
            )
        }
        is PendingAction.DraftSms -> PendingActionUi(
            title = stringResource(R.string.pending_sms_title),
            lines = listOfNotNull(action.draft.phoneNumber, action.draft.message),
            intent = buildSmsIntent(action.draft),
            summary = stringResource(R.string.chat_sms_summary, action.draft.message),
        )
        is PendingAction.Navigate -> PendingActionUi(
            title = stringResource(R.string.pending_navigation_title),
            lines = listOf(action.draft.destination),
            intent = buildNavigationIntent(action.draft),
            summary = stringResource(R.string.chat_navigation_summary, action.draft.destination),
        )
        is PendingAction.OpenCalendarDate -> {
            val date = action.draft.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            PendingActionUi(
                title = stringResource(R.string.pending_open_calendar_title),
                lines = listOf(date),
                intent = buildOpenCalendarIntent(action.draft),
                summary = stringResource(R.string.chat_open_calendar_summary, date),
            )
        }
        is PendingAction.CreateNote -> PendingActionUi(
            title = stringResource(R.string.pending_note_title),
            lines = listOfNotNull(action.draft.title, action.draft.content),
            intent = buildNoteIntent(context, action.draft),
            summary = stringResource(R.string.chat_note_summary, action.draft.title ?: action.draft.content),
        )
    }

    ConfirmActionCard(
        title = ui.title,
        lines = ui.lines,
        onConfirm = {
            try {
                context.startActivity(ui.intent)
                viewModel.confirmPendingAction(ui.summary)
            } catch (e: ActivityNotFoundException) {
                viewModel.reportNoAppForAction()
            }
        },
        onCancel = { viewModel.cancelPendingAction(cancelNote) },
    )
}

@Composable
private fun ConfirmActionCard(
    title: String,
    lines: List<String>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
            lines.forEach { line ->
                Text(line, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.pending_confirm))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.pending_cancel))
                }
            }
        }
    }
}

@Composable
private fun ChatErrorBanner(
    error: ChatError,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val (message, showSettingsAction) = when (error) {
        ChatError.MissingApiKey -> stringResource(R.string.error_missing_api_key) to true
        is ChatError.InvalidApiKey ->
            stringResource(R.string.error_invalid_api_key).withDetail(error.detail) to true
        is ChatError.NoConnectivity ->
            stringResource(R.string.error_no_connectivity).withDetail(error.detail) to false
        is ChatError.Timeout ->
            stringResource(R.string.error_timeout, formatTimeout(error.timeoutSeconds)).withDetail(error.detail) to true
        is ChatError.RateLimited ->
            stringResource(R.string.error_rate_limited).withDetail(error.detail) to false
        is ChatError.ServerError ->
            stringResource(R.string.error_server, error.statusCode).withDetail(error.detail) to false
        is ChatError.Unknown -> stringResource(R.string.error_unknown, error.detail) to false
        ChatError.ToolArguments -> stringResource(R.string.error_tool_arguments) to false
        ChatError.NoAppForAction -> stringResource(R.string.error_no_app_for_action) to false
        ChatError.AttachmentTooLarge -> stringResource(R.string.error_attachment_too_large) to false
        ChatError.PdfTooLarge -> stringResource(R.string.error_pdf_too_large) to false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (showSettingsAction) {
                    Button(onClick = onNavigateToSettings) {
                        Text(stringResource(R.string.settings))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    }
}

private fun String.withDetail(detail: String?): String =
    if (detail.isNullOrBlank()) this else "$this\n$detail"
