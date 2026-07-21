package com.parrotworks.oneagentarmy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.data.local.normalizeForSearch

private data class HelpSectionText(val title: String, val body: String)

// Static content, but the search box needs local state - still no ViewModel: filtering
// happens over the already-resolved string resources, nothing to load.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }

    val sections = listOf(
        HelpSectionText(stringResource(R.string.help_section_providers_title), stringResource(R.string.help_section_providers_body)),
        HelpSectionText(stringResource(R.string.help_section_keys_title), stringResource(R.string.help_section_keys_body)),
        HelpSectionText(stringResource(R.string.help_section_costs_title), stringResource(R.string.help_section_costs_body)),
        HelpSectionText(stringResource(R.string.help_section_facts_title), stringResource(R.string.help_section_facts_body)),
        HelpSectionText(stringResource(R.string.help_section_tools_title), stringResource(R.string.help_section_tools_body)),
        HelpSectionText(stringResource(R.string.help_section_attachments_title), stringResource(R.string.help_section_attachments_body)),
        HelpSectionText(stringResource(R.string.help_section_context_title), stringResource(R.string.help_section_context_body)),
        HelpSectionText(stringResource(R.string.help_section_personalization_title), stringResource(R.string.help_section_personalization_body)),
        HelpSectionText(stringResource(R.string.help_section_conversations_title), stringResource(R.string.help_section_conversations_body)),
        HelpSectionText(stringResource(R.string.help_section_sharing_title), stringResource(R.string.help_section_sharing_body)),
    )

    val normalizedQuery = normalizeForSearch(query)
    val visibleSections = if (normalizedQuery.isBlank()) {
        sections
    } else {
        sections.filter {
            normalizeForSearch(it.title).contains(normalizedQuery) || normalizeForSearch(it.body).contains(normalizedQuery)
        }
    }

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
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.settings_help_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.settings_help_search_clear))
                        }
                    }
                },
                singleLine = true,
            )
            if (visibleSections.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_help_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    visibleSections.forEachIndexed { index, section ->
                        HelpSection(section.title, section.body, normalizedQuery)
                        if (index != visibleSections.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String, normalizedQuery: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = highlightMatches(title, normalizedQuery),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = highlightMatches(body, normalizedQuery),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Matches are found via the diacritic/case-insensitive normalized text (same normalization
// used for message search), but highlighted on the original string - normalizeForSearch is
// a 1:1 per-character mapping (lowercase + strip combining marks + ł->l), so match indices
// line up between the two.
@Composable
private fun highlightMatches(text: String, normalizedQuery: String): AnnotatedString {
    if (normalizedQuery.isBlank()) return AnnotatedString(text)
    val normalizedText = normalizeForSearch(text)
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val matchStart = normalizedText.indexOf(normalizedQuery, index)
            if (matchStart == -1) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, matchStart))
            val matchEnd = (matchStart + normalizedQuery.length).coerceAtMost(text.length)
            withStyle(
                SpanStyle(
                    background = MaterialTheme.colorScheme.tertiary,
                    color = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                append(text.substring(matchStart, matchEnd))
            }
            index = matchEnd
        }
    }
}
