package com.piotrek.oneagentarmy.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.piotrek.oneagentarmy.R

@Composable
fun ApiKeyFields(
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

@Composable
fun ClearApiKeyDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
