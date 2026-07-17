package com.piotrek.oneagentarmy.ui.chat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val error by viewModel.error.collectAsState()
    val conversationTitle by viewModel.conversationTitle.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversationTitle ?: stringResource(R.string.new_conversation)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
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
                                    text = { Text(stringResource(option.labelRes)) },
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
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(
                            message = message,
                            onResend = if (message.sender == Sender.USER) {
                                { viewModel.resendMessage(message) }
                            } else {
                                null
                            },
                        )
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.message_placeholder)) },
                    enabled = !isSending,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                } else {
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        },
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
        is ChatError.RateLimited ->
            stringResource(R.string.error_rate_limited).withDetail(error.detail) to false
        is ChatError.ServerError ->
            stringResource(R.string.error_server, error.statusCode).withDetail(error.detail) to false
        is ChatError.Unknown -> stringResource(R.string.error_unknown, error.detail) to false
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
