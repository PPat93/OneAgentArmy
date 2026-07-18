package com.piotrek.oneagentarmy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.TAVILY_KEY_ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsToolsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val tavilyHasKey by viewModel.tavilyHasKey.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tools_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.tavily_api_key_label),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ApiKeyFields(
                        hasKey = tavilyHasKey,
                        keyInput = keyInput,
                        onKeyInputChange = { keyInput = it },
                        onSaveKey = {
                            viewModel.saveApiKey(TAVILY_KEY_ID, keyInput)
                            keyInput = ""
                        },
                        onClearKeyRequest = { showClearDialog = true },
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ClearApiKeyDialog(
            onDismiss = { showClearDialog = false },
            onConfirm = {
                viewModel.clearApiKey(TAVILY_KEY_ID)
                showClearDialog = false
            },
        )
    }
}
