package com.piotrek.oneagentarmy.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp

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
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Czat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
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
                    placeholder = { Text("Napisz wiadomość...") },
                    enabled = !isSending,
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
                        Icon(Icons.Default.Send, contentDescription = "Wyślij")
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
        ChatError.MissingApiKey -> "Nie skonfigurowano klucza API OpenAI." to true
        is ChatError.InvalidApiKey ->
            "Klucz API został odrzucony przez OpenAI.".withDetail(error.detail) to true
        ChatError.NoConnectivity -> "Brak połączenia z internetem." to false
        is ChatError.RateLimited ->
            "Przekroczono limit zapytań, spróbuj ponownie za chwilę.".withDetail(error.detail) to false
        is ChatError.ServerError ->
            "Błąd serwera OpenAI (${error.statusCode}), spróbuj ponownie.".withDetail(error.detail) to false
        is ChatError.Unknown -> "Nie udało się uzyskać odpowiedzi: ${error.detail}" to false
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
                        Text("Ustawienia")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Zamknij")
                }
            }
        }
    }
}

private fun String.withDetail(detail: String?): String =
    if (detail.isNullOrBlank()) this else "$this\n$detail"
