package com.piotrek.oneagentarmy.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.data.local.normalizeForSearch
import com.piotrek.oneagentarmy.model.MessageSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onResultClick: (conversationId: String, messageId: String) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search)) },
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
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )

            if (query.isNotBlank() && results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.search_no_results))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.message.id }) { result ->
                        SearchResultRow(
                            result = result,
                            query = query,
                            onClick = { onResultClick(result.message.conversationId, result.message.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: MessageSearchResult,
    query: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    result.conversationTitle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    snippetAround(result.message.text, query),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// Trims long messages so the matched fragment is actually visible in the two-line preview.
// The match position is located on normalized text (diacritic/case-insensitive, same as the
// SQL query); for our alphabets normalization is 1:1 in length, so indices line up with the
// original string - bounds are coerced anyway in case an exotic character shifts them.
private fun snippetAround(text: String, query: String, contextChars: Int = 30): String {
    val singleLine = text.replace('\n', ' ')
    val matchIndex = normalizeForSearch(singleLine).indexOf(normalizeForSearch(query.trim()))
    if (matchIndex <= contextChars) return singleLine
    return "…" + singleLine.substring((matchIndex - contextChars).coerceIn(0, singleLine.length))
}
