package com.piotrek.oneagentarmy.ui.conversationlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.model.Conversation
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
) {
    val conversations by viewModel.conversations.collectAsState()
    var renameDialogFor by remember { mutableStateOf<Conversation?>(null) }
    var deleteDialogFor by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OneAgentArmy") },
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.logo_parrot),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewConversation) {
                Icon(Icons.Default.Add, contentDescription = "Nowa rozmowa")
            }
        },
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Brak rozmów - kliknij +, żeby zacząć")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) },
                        onRenameRequest = { renameDialogFor = conversation },
                        onDeleteRequest = { deleteDialogFor = conversation },
                    )
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }

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
                    onLongClick = { menuExpanded = true },
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            ListItem(
                headlineContent = { Text(conversation.title, color = MaterialTheme.colorScheme.onPrimaryContainer) },
                supportingContent = {
                    Text(
                        formatter.format(conversation.createdAt),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Zmień nazwę") },
                onClick = {
                    menuExpanded = false
                    onRenameRequest()
                },
            )
            DropdownMenuItem(
                text = { Text("Usuń", color = MaterialTheme.colorScheme.error) },
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
        title = { Text("Zmień nazwę rozmowy") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
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
        title = { Text("Usunąć rozmowę?") },
        text = { Text("Usunąć rozmowę \"${conversation.title}\"? Tej operacji nie można cofnąć.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}
