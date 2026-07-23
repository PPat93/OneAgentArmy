package com.parrotworks.oneagentarmy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.provider.ai.AiProviderInfo
import com.parrotworks.oneagentarmy.provider.ai.AiProviderRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProvidersScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val activeProvider by viewModel.activeProvider.collectAsState()
    val apiKeyStates by viewModel.apiKeyStates.collectAsState()
    val catalogSyncedAtMillis by viewModel.modelCatalogSyncedAtMillis.collectAsState()
    val catalogRefreshState by viewModel.catalogRefreshState.collectAsState()
    val keyInputs = remember { mutableStateMapOf<String, String>() }
    var clearKeyDialogFor by remember { mutableStateOf<AiProviderInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_providers_title)) },
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
            ModelCatalogCard(
                syncedAtMillis = catalogSyncedAtMillis,
                refreshState = catalogRefreshState,
                onRefresh = viewModel::refreshModelCatalog,
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
}

@Composable
private fun ModelCatalogCard(
    syncedAtMillis: Long?,
    refreshState: CatalogRefreshState,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.model_catalog_title),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.model_catalog_subtitle),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            val syncedText = if (syncedAtMillis != null) {
                val formatted = remember(syncedAtMillis) {
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(syncedAtMillis))
                }
                stringResource(R.string.model_catalog_synced_at, formatted)
            } else {
                stringResource(R.string.model_catalog_never_synced)
            }
            Text(
                text = syncedText,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            when (refreshState) {
                is CatalogRefreshState.Success -> {
                    Text(
                        text = stringResource(R.string.model_catalog_refresh_success),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (refreshState.droppedModelIds.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.model_catalog_dropped,
                                refreshState.droppedModelIds.joinToString(", "),
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val availability = refreshState.availability
                    availability.warnings.forEach { warning ->
                        Text(
                            text = stringResource(
                                R.string.model_catalog_availability_missing,
                                warning.providerName,
                                warning.missingModelIds.joinToString(", "),
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (availability.failedProviderNames.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.model_catalog_availability_failed,
                                availability.failedProviderNames.joinToString(", "),
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (availability.checkedProviderCount > 0 &&
                        availability.warnings.isEmpty() &&
                        availability.failedProviderNames.isEmpty()
                    ) {
                        Text(
                            text = stringResource(R.string.model_catalog_availability_ok),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                is CatalogRefreshState.Error -> Text(
                    text = stringResource(R.string.model_catalog_refresh_error, refreshState.detail),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                CatalogRefreshState.Idle, CatalogRefreshState.Running -> Unit
            }
            TextButton(
                onClick = onRefresh,
                enabled = refreshState != CatalogRefreshState.Running,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    stringResource(
                        if (refreshState == CatalogRefreshState.Running) {
                            R.string.model_catalog_refreshing
                        } else {
                            R.string.model_catalog_refresh
                        },
                    ),
                )
            }
        }
    }
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

            Text(
                text = stringResource(provider.taglineRes),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            provider.noteRes?.let { noteRes ->
                Text(
                    text = stringResource(noteRes),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
