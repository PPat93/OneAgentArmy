package com.piotrek.oneagentarmy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.provider.ai.AiProviderInfo
import com.piotrek.oneagentarmy.provider.ai.AiProviderRegistry
import com.piotrek.oneagentarmy.provider.ai.tools.websearch.TAVILY_KEY_ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val activeProvider by viewModel.activeProvider.collectAsState()
    val apiKeyStates by viewModel.apiKeyStates.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val tavilyHasKey by viewModel.tavilyHasKey.collectAsState()
    val keyInputs = remember { mutableStateMapOf<String, String>() }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var clearKeyDialogFor by remember { mutableStateOf<AiProviderInfo?>(null) }
    var showClearTavilyDialog by remember { mutableStateOf(false) }

    val activeModels = AiProviderRegistry.byId(activeProvider)?.models.orEmpty()
    val selectedOption = activeModels.firstOrNull { it.id == selectedModel }
    val selectedModelLabel = if (selectedOption != null) stringResource(selectedOption.labelRes) else selectedModel

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            AiProviderRegistry.providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = activeProvider == provider.id,
                    hasKey = apiKeyStates[provider.id] == true,
                    keyInput = keyInputs[provider.id].orEmpty(),
                    onKeyInputChange = { keyInputs[provider.id] = it },
                    onSetActive = { viewModel.setActiveProvider(provider.id) },
                    onSaveKey = {
                        viewModel.saveApiKey(provider.id, keyInputs[provider.id].orEmpty())
                        keyInputs[provider.id] = ""
                    },
                    onClearKeyRequest = { clearKeyDialogFor = provider },
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.model_section_title), color = MaterialTheme.colorScheme.onTertiaryContainer)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = selectedModelLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.model_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { modelMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                        ) {
                            activeModels.forEach { option ->
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
                }
            }

            Text(
                text = stringResource(R.string.tools_section_title),
                style = MaterialTheme.typography.titleLarge,
            )

            ToolApiKeyCard(
                title = stringResource(R.string.tavily_api_key_label),
                hasKey = tavilyHasKey,
                keyInput = keyInputs[TAVILY_KEY_ID].orEmpty(),
                onKeyInputChange = { keyInputs[TAVILY_KEY_ID] = it },
                onSaveKey = {
                    viewModel.saveApiKey(TAVILY_KEY_ID, keyInputs[TAVILY_KEY_ID].orEmpty())
                    keyInputs[TAVILY_KEY_ID] = ""
                },
                onClearKeyRequest = { showClearTavilyDialog = true },
            )
        }
    }

    clearKeyDialogFor?.let { provider ->
        ClearApiKeyDialog(
            onDismiss = { clearKeyDialogFor = null },
            onConfirm = {
                viewModel.clearApiKey(provider.id)
                clearKeyDialogFor = null
            },
        )
    }

    if (showClearTavilyDialog) {
        ClearApiKeyDialog(
            onDismiss = { showClearTavilyDialog = false },
            onConfirm = {
                viewModel.clearApiKey(TAVILY_KEY_ID)
                showClearTavilyDialog = false
            },
        )
    }
}

@Composable
private fun ClearApiKeyDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_api_key_title)) },
        text = { Text(stringResource(R.string.clear_api_key_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ProviderCard(
    provider: AiProviderInfo,
    isActive: Boolean,
    hasKey: Boolean,
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    onSetActive: () -> Unit,
    onSaveKey: () -> Unit,
    onClearKeyRequest: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = isActive,
                    onClick = onSetActive,
                    enabled = provider.isAvailable,
                )
                Text(
                    text = provider.displayName,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!provider.isAvailable) {
                    Text(
                        text = stringResource(R.string.coming_soon),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            ApiKeyFields(
                hasKey = hasKey,
                keyInput = keyInput,
                onKeyInputChange = onKeyInputChange,
                onSaveKey = onSaveKey,
                onClearKeyRequest = onClearKeyRequest,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ToolApiKeyCard(
    title: String,
    hasKey: Boolean,
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onClearKeyRequest: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
            ApiKeyFields(
                hasKey = hasKey,
                keyInput = keyInput,
                onKeyInputChange = onKeyInputChange,
                onSaveKey = onSaveKey,
                onClearKeyRequest = onClearKeyRequest,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ApiKeyFields(
    hasKey: Boolean,
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onClearKeyRequest: () -> Unit,
    contentColor: Color,
) {
    Text(
        text = stringResource(if (hasKey) R.string.api_key_configured else R.string.api_key_missing),
        color = contentColor,
    )

    OutlinedTextField(
        value = keyInput,
        onValueChange = onKeyInputChange,
        label = { Text(stringResource(R.string.api_key_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )

    Row(modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = onSaveKey) {
            Text(stringResource(R.string.save))
        }
        TextButton(onClick = onClearKeyRequest) {
            Text(stringResource(R.string.clear))
        }
    }
}
