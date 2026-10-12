package com.minima.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minima.chat.core.ui.markdown.MarkdownText
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaDimens
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.core.ui.util.SensitiveClipboard
import com.minima.chat.core.ui.util.UnicodeSafe

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun MessageBubble(
    text: String,
    isUser: Boolean,
    isStreaming: Boolean = false,
    useMarkdown: Boolean = false,
    showActions: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    /**
     * Filename hint chips for attachments — rendered **above** the bubble for user
     * messages (never inside the black bubble, never a real image preview).
     */
    attachmentLabels: List<String> = emptyList(),
    /** @deprecated Prefer [attachmentLabels]. Kept for call-site compatibility. */
    attachmentLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMinimaColors.current
    val context = LocalContext.current
    val safeText = remember(text) { UnicodeSafe.sanitize(text) }
    val labels = remember(attachmentLabels, attachmentLabel) {
        when {
            attachmentLabels.isNotEmpty() -> attachmentLabels.filter { it.isNotBlank() }
            !attachmentLabel.isNullOrBlank() -> attachmentLabel.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }
    val shape = if (isUser) {
        RoundedCornerShape(
            topStart = MinimaDimens.BubbleRadius,
            topEnd = MinimaDimens.BubbleRadius,
            bottomStart = MinimaDimens.BubbleRadius,
            bottomEnd = 4.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = MinimaDimens.BubbleRadius,
            topEnd = MinimaDimens.BubbleRadius,
            bottomStart = 4.dp,
            bottomEnd = MinimaDimens.BubbleRadius,
        )
    }
    val display = if (safeText.isEmpty() && isStreaming) "…" else safeText
    val hidePlaceholderImageText = labels.isNotEmpty() && (display == "(image)" || display.isEmpty())
    val showText = !hidePlaceholderImageText && display.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MinimaDimens.ScreenPadding, vertical = MinimaDimens.MessageSpacing / 2),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            // Attachment chips ABOVE the bubble (user messages).
            if (labels.isNotEmpty() && isUser) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (label in labels) {
                        Text(
                            text = label,
                            style = MinimaTypography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.bgContainer)
                                .border(1.dp, colors.borderSecondary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .semantics { contentDescription = "Attachment $label" },
                        )
                    }
                }
            }
            // Assistant: keep a subtle inline hint if somehow attached (rare).
            if (labels.isNotEmpty() && !isUser) {
                Text(
                    text = labels.joinToString(", "),
                    style = MinimaTypography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (showText || (isStreaming && display == "…")) {
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isUser) colors.userBubble else colors.assistantBubble)
                        .then(
                            if (!isStreaming && safeText.isNotEmpty() && safeText != "…") {
                                Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                    onLongClick = {
                                        if (SensitiveClipboard.looksLikeApiKey(safeText)) return@combinedClickable
                                        SensitiveClipboard.copyPlain(context, "message", safeText, sensitive = false)
                                    },
                                )
                            } else Modifier,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics { contentDescription = if (isUser) "User message" else "Assistant message" },
                ) {
                    if (!isUser && useMarkdown && display.isNotEmpty() && display != "…") {
                        MarkdownText(
                            markdown = display,
                            color = colors.text,
                        )
                    } else {
                        Text(
                            text = display,
                            style = MinimaTypography.bodyMedium,
                            color = if (isUser) colors.onPrimary else colors.text,
                        )
                    }
                }
            }
        }
    }
}
