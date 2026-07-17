package com.piotrek.oneagentarmy.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender

@Composable
fun ChatBubble(
    message: Message,
    onResend: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isUser = message.sender == Sender.USER
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (isUser) {
            onResend?.let { ResendButton(it) }
            CopyButton { clipboard.setText(AnnotatedString(message.text)) }
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SelectionContainer {
                if (isUser) {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Markdown(
                        content = message.text,
                        colors = markdownColor(text = MaterialTheme.colorScheme.onTertiaryContainer),
                    )
                }
            }
        }
        if (!isUser) {
            CopyButton { clipboard.setText(AnnotatedString(message.text)) }
        }
    }
}

@Composable
private fun CopyButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.copy_message),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ResendButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = stringResource(R.string.resend_message),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
