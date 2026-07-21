package com.parrotworks.oneagentarmy.ui.conversationlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.model.Conversation
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import com.parrotworks.oneagentarmy.ui.components.ParrotLogoBadge
import com.parrotworks.oneagentarmy.ui.components.formatCostEur
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onConversationClick: (String) -> Unit,
    onNewConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val conversations by viewModel.conversations.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val monthlyCost by viewModel.monthlyCost.collectAsState()
    val usdToEur by viewModel.usdToEur.collectAsState()
    var renameDialogFor by remember { mutableStateOf<Conversation?>(null) }
    var deleteDialogFor by remember { mutableStateOf<Conversation?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) { exitSelectionMode() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = ::exitSelectionMode) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            monthlyCost?.takeIf { it > 0.0 }?.let { cost ->
                                Text(
                                    text = stringResource(R.string.monthly_cost_label, formatCostEur(cost, usdToEur)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        ParrotLogoBadge(
                            size = 32.dp,
                            innerSize = 26.dp,
                            modifier = Modifier.padding(8.dp),
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = onNewConversation,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_conversation))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ProviderChipRow(
                activeProvider = activeProvider,
                onProviderSelected = viewModel::setActiveProvider,
            )
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.empty_conversation_list))
                }
            } else {
                val pinnedConversations = conversations.filter { it.pinned }
                val unpinnedConversations = conversations.filter { !it.pinned }
                val renderConversationRow: @Composable (Conversation) -> Unit = { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selectionMode = selectionMode,
                        isSelected = conversation.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (conversation.id in selectedIds) {
                                    selectedIds - conversation.id
                                } else {
                                    selectedIds + conversation.id
                                }
                            } else {
                                onConversationClick(conversation.id)
                            }
                        },
                        onRenameRequest = { renameDialogFor = conversation },
                        onDeleteRequest = { deleteDialogFor = conversation },
                        onPinRequest = { viewModel.setPinned(conversation.id, !conversation.pinned) },
                        onSelectRequest = {
                            selectionMode = true
                            selectedIds = setOf(conversation.id)
                        },
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(pinnedConversations, key = { it.id }) { conversation -> renderConversationRow(conversation) }
                    if (pinnedConversations.isNotEmpty() && unpinnedConversations.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                    items(unpinnedConversations, key = { it.id }) { conversation -> renderConversationRow(conversation) }
                }
            }
        }
    }

    renameDialogFor?.let { conversation ->
        RenameConversationDialog(
            conversation = conversation,
            onDismiss = { renameDialogFor = null },
            onConfirm = { newTitle ->
                viewModel.renameConversation(conversation.id, newTitle)
                renameDialogFor = null
            },
        )
    }

    deleteDialogFor?.let { conversation ->
        DeleteConversationDialog(
            conversation = conversation,
            onDismiss = { deleteDialogFor = null },
            onConfirm = {
                viewModel.deleteConversation(conversation.id)
                deleteDialogFor = null
            },
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete_selected_title)) },
            text = { Text(stringResource(R.string.delete_selected_text, selectedIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversations(selectedIds)
                        showDeleteSelectedDialog = false
                        exitSelectionMode()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderChipRow(
    activeProvider: String,
    onProviderSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.provider_chip_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AiProviderRegistry.providers.forEach { provider ->
            FilterChip(
                selected = provider.id == activeProvider,
                onClick = { onProviderSelected(provider.id) },
                label = { Text(provider.chipLabel) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onPinRequest: () -> Unit,
    onSelectRequest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }
    // E.g. "ChatGPT 4.1 nano" - saves opening each conversation to check.
    val modelLabel = remember(conversation.modelId) {
        val providerChip = AiProviderRegistry
            .byId(AiProviderRegistry.providerIdForModel(conversation.modelId))
            ?.chipLabel
        listOfNotNull(providerChip, AiProviderRegistry.shortLabelFor(conversation.modelId))
            .joinToString(" ")
    }

    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!selectionMode) menuExpanded = true },
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
        ) {
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (conversation.pinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(16.dp),
                            )
                        }
                        Text(
                            conversation.title,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                supportingContent = {
                    // Deliberately smaller and faded - at full contrast the metadata
                    // line reads like a second title.
                    Text(
                        "${formatter.format(conversation.createdAt)} · $modelLabel",
                        color = contentColor.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.select)) },
                onClick = {
                    menuExpanded = false
                    onSelectRequest()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (conversation.pinned) R.string.unpin else R.string.pin)) },
                onClick = {
                    menuExpanded = false
                    onPinRequest()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    menuExpanded = false
                    onRenameRequest()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuExpanded = false
                    onDeleteRequest()
                },
            )
        }
    }
}

@Composable
private fun RenameConversationDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(conversation.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_conversation_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteConversationDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_conversation_title)) },
        text = { Text(stringResource(R.string.delete_conversation_text, conversation.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
