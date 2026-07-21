package com.parrotworks.oneagentarmy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.ui.lock.canUseAppLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToFacts: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val chatFontScale by viewModel.chatFontScale.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockAvailable = canUseAppLock(LocalContext.current)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsMenuCard(
                title = stringResource(R.string.settings_providers_title),
                subtitle = stringResource(R.string.settings_providers_subtitle),
                onClick = onNavigateToProviders,
            )
            SettingsMenuCard(
                title = stringResource(R.string.settings_tools_title),
                subtitle = stringResource(R.string.settings_tools_subtitle),
                onClick = onNavigateToTools,
            )
            SettingsMenuCard(
                title = stringResource(R.string.settings_facts_title),
                subtitle = stringResource(R.string.settings_facts_subtitle),
                onClick = onNavigateToFacts,
            )
            ChatFontScaleCard(
                currentScale = chatFontScale,
                onScaleSelected = viewModel::setChatFontScale,
            )
            AppLockCard(
                enabled = appLockEnabled,
                available = appLockAvailable,
                onEnabledChange = viewModel::setAppLockEnabled,
            )
            SettingsMenuCard(
                title = stringResource(R.string.settings_help_title),
                subtitle = stringResource(R.string.settings_help_subtitle),
                onClick = onNavigateToHelp,
            )
            SettingsMenuCard(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                onClick = onNavigateToAbout,
            )
        }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
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
private fun SettingsMenuCard(
    title: String,
    subtitle: String,
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
                    title,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(subtitle, color = MaterialTheme.colorScheme.onTertiaryContainer)
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
