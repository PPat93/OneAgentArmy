package com.parrotworks.oneagentarmy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.ui.lock.canUseAppLock

// A number above this is still allowed (open field, not a hard cap) but shown with a cost
// warning - a long context window means more resent tokens, and resent attachments are
// re-billed on every turn until they age out of the window.
private const val CONTEXT_WINDOW_WARNING_THRESHOLD = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsChatScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val chatFontScale by viewModel.chatFontScale.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockAvailable = canUseAppLock(LocalContext.current)
    val contextWindowSize by viewModel.contextWindowSize.collectAsState()
    var showContextWindowDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_chat_title)) },
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
            ChatFontScaleCard(
                currentScale = chatFontScale,
                onScaleSelected = viewModel::setChatFontScale,
            )
            AppLockCard(
                enabled = appLockEnabled,
                available = appLockAvailable,
                onEnabledChange = viewModel::setAppLockEnabled,
            )
            ContextWindowSizeCard(
                size = contextWindowSize,
                onClick = { showContextWindowDialog = true },
            )
            DeleteAllConversationsCard(onClick = { showDeleteAllDialog = true })
        }
    }

    if (showContextWindowDialog) {
        ContextWindowSizeDialog(
            currentSize = contextWindowSize,
            onDismiss = { showContextWindowDialog = false },
            onConfirm = { newSize ->
                viewModel.setContextWindowSize(newSize)
                showContextWindowDialog = false
            },
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.delete_all_conversations_dialog_title)) },
            text = { Text(stringResource(R.string.delete_all_conversations_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllConversations()
                        showDeleteAllDialog = false
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ChatFontScaleCard(
    currentScale: Float,
    onScaleSelected: (Float) -> Unit,
) {
    val options = listOf(
        0.85f to stringResource(R.string.font_scale_small),
        1.0f to stringResource(R.string.font_scale_normal),
        1.15f to stringResource(R.string.font_scale_large),
        1.3f to stringResource(R.string.font_scale_xlarge),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.chat_font_scale_title),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (scale, label) ->
                    FilterChip(
                        selected = scale == currentScale,
                        onClick = { onScaleSelected(scale) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLockCard(
    enabled: Boolean,
    available: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.app_lock_title),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    stringResource(if (available) R.string.app_lock_subtitle else R.string.app_lock_unavailable),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            trailingContent = {
                Switch(
                    checked = enabled && available,
                    onCheckedChange = onEnabledChange,
                    enabled = available,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ContextWindowSizeCard(
    size: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.context_window_size_title),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.context_window_size_current, size),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun ContextWindowSizeDialog(
    currentSize: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentSize.toString()) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.context_window_size_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.context_window_size_dialog_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.context_window_size_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if ((parsed ?: 0) > CONTEXT_WINDOW_WARNING_THRESHOLD) {
                    Text(
                        stringResource(R.string.context_window_size_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.takeIf { it > 0 }?.let(onConfirm) },
                enabled = parsed != null && parsed > 0,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteAllConversationsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.settings_delete_all_title),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_delete_all_subtitle),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
