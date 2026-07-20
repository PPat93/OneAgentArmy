package com.piotrek.oneagentarmy.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.piotrek.oneagentarmy.R
import com.piotrek.oneagentarmy.model.Message
import com.piotrek.oneagentarmy.model.Sender
import com.piotrek.oneagentarmy.ui.components.AttachmentImage
import com.piotrek.oneagentarmy.ui.components.formatCostEur
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ChatBubble(
    message: Message,
    onResend: (() -> Unit)?,
    usdToEur: Double,
    fontScale: Float,
    resolveAttachmentPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val isUser = message.sender == Sender.USER
    val clipboard = LocalClipboardManager.current
    val timeFormatter = remember {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    // Scaling fontScale (not density) bumps every sp-sized text in the bubble -
    // user text and all markdown alike - while dp paddings and icons stay put.
    val density = LocalDensity.current
    val scaledDensity = remember(density, fontScale) {
        Density(density.density, density.fontScale * fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        ChatBubbleContent(message, onResend, usdToEur, isUser, clipboard, timeFormatter, resolveAttachmentPath, modifier)
    }
}

@Composable
private fun ChatBubbleContent(
    message: Message,
    onResend: (() -> Unit)?,
    usdToEur: Double,
    isUser: Boolean,
    clipboard: ClipboardManager,
    timeFormatter: DateTimeFormatter,
    resolveAttachmentPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (isUser) {
                onResend?.let { ResendButton(it) }
                CopyButton { clipboard.setText(AnnotatedString(message.text)) }
            }
            Box(
                modifier = Modifier
                    // Grows up to the full width left over by the action icons, but
                    // short messages stay as narrow as their content.
                    .weight(1f, fill = false)
                    .background(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.attachmentPath?.let { path ->
                        when (message.attachmentType) {
                            Message.ATTACHMENT_TYPE_IMAGE -> AttachmentImage(
                                absolutePath = resolveAttachmentPath(path),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Message.ATTACHMENT_TYPE_PDF -> Text(
                                text = "📄 ${message.attachmentName ?: "PDF"}",
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (message.text.isNotBlank()) {
                        BubbleText(message, isUser)
                    }
                }
            }
            if (!isUser) {
                CopyButton { clipboard.setText(AnnotatedString(message.text)) }
            }
        }
        val timeLine = buildString {
            append(timeFormatter.format(message.timestamp))
            message.costUsd?.let { append(" · ").append(formatCostEur(it, usdToEur)) }
        }
        Text(
            text = timeLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun BubbleText(message: Message, isUser: Boolean) {
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
                // Default heading sizes are tuned for full-screen articles and
                // look huge inside a chat bubble - scaled down to title/body roles.
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.titleLarge,
                    h2 = MaterialTheme.typography.titleMedium,
                    h3 = MaterialTheme.typography.titleSmall,
                    h4 = MaterialTheme.typography.titleSmall,
                    h5 = MaterialTheme.typography.bodyMedium,
                    h6 = MaterialTheme.typography.bodyMedium,
                ),
                // The library's table cells default to maxLines = 1 with
                // ellipsis, silently truncating longer values - re-rendered
                // here with wrapping enabled.
                components = markdownComponents(
                    table = { model ->
                        MarkdownTable(
                            content = model.content,
                            node = model.node,
                            style = model.typography.text,
                            headerBlock = { content, header, tableWidth, style ->
                                MarkdownTableHeader(content, header, tableWidth, style, maxLines = Int.MAX_VALUE)
                            },
                            rowBlock = { content, row, tableWidth, style ->
                                MarkdownTableRow(content, row, tableWidth, style, maxLines = Int.MAX_VALUE)
                            },
                        )
                    },
                ),
            )
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
