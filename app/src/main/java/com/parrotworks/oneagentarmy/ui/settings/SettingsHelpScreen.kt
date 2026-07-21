package com.parrotworks.oneagentarmy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R

// Static content only - no ViewModel needed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_help_title)) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HelpSection(R.string.help_section_providers_title, R.string.help_section_providers_body)
            HelpSection(R.string.help_section_keys_title, R.string.help_section_keys_body)
            HelpSection(R.string.help_section_costs_title, R.string.help_section_costs_body)
            HelpSection(R.string.help_section_facts_title, R.string.help_section_facts_body)
            HelpSection(R.string.help_section_tools_title, R.string.help_section_tools_body)
            HelpSection(R.string.help_section_attachments_title, R.string.help_section_attachments_body)
            HelpSection(R.string.help_section_context_title, R.string.help_section_context_body)
            HelpSection(R.string.help_section_personalization_title, R.string.help_section_personalization_body)
            HelpSection(R.string.help_section_conversations_title, R.string.help_section_conversations_body)
            HelpSection(R.string.help_section_sharing_title, R.string.help_section_sharing_body)
        }
    }
}

@Composable
private fun HelpSection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
