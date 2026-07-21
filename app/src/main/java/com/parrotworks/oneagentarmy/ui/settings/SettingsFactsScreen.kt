package com.parrotworks.oneagentarmy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parrotworks.oneagentarmy.R
import com.parrotworks.oneagentarmy.model.Fact

// Sentinel meaning "the add-new dialog is open" (as opposed to editing an existing fact).
private val NewFactSentinel = Fact(id = "", title = "", content = "", isGlobal = false, createdAt = java.time.Instant.EPOCH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFactsScreen(
    viewModel: FactsViewModel,
    onBack: () -> Unit,
) {
    val facts by viewModel.facts.collectAsState()
    var editorFor by remember { mutableStateOf<Fact?>(null) }
    var deleteDialogFor by remember { mutableStateOf<Fact?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_facts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorFor = NewFactSentinel }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fact_new_title))
            }
        },
    ) { innerPadding ->
        if (facts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.facts_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(facts, key = { it.id }) { fact ->
                    FactRow(
                        fact = fact,
                        onClick = { editorFor = fact },
                        onDeleteRequest = { deleteDialogFor = fact },
                    )
                }
            }
        }
    }

    editorFor?.let { fact ->
        val isNew = fact === NewFactSentinel
        FactEditorDialog(
            fact = if (isNew) null else fact,
            onDismiss = { editorFor = null },
            onConfirm = { title, content, isGlobal ->
                viewModel.saveFact(if (isNew) null else fact, title, content, isGlobal)
                editorFor = null
            },
        )
    }

    deleteDialogFor?.let { fact ->
        AlertDialog(
            onDismissRequest = { deleteDialogFor = null },
            title = { Text(stringResource(R.string.fact_delete_title)) },
            text = { Text(stringResource(R.string.fact_delete_text, fact.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFact(fact.id)
                        deleteDialogFor = null
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FactRow(
    fact: Fact,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        ListItem(
            leadingContent = {
                if (fact.isGlobal) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = stringResource(R.string.fact_global_label),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            },
            headlineContent = {
                Text(
                    fact.title,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    fact.content,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                IconButton(onClick = onDeleteRequest) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun FactEditorDialog(
    fact: Fact?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, isGlobal: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(fact?.title.orEmpty()) }
    var content by remember { mutableStateOf(fact?.content.orEmpty()) }
    var isGlobal by remember { mutableStateOf(fact?.isGlobal ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (fact == null) R.string.fact_new_title else R.string.fact_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.fact_title_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.fact_content_label)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isGlobal, onCheckedChange = { isGlobal = it })
                    Text(
                        stringResource(R.string.fact_global_label),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, content, isGlobal) },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
