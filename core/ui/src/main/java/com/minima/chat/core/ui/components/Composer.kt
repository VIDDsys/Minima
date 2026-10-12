package com.minima.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaDimens
import com.minima.chat.core.ui.theme.MinimaTypography

/** One composer attachment chip (filename text + dismiss). No thumbnail. */
data class ComposerChip(
    val id: String,
    val label: String,
)

/**
 * Ink-minimal composer:
 * - Capsule input bar
 * - Left control (attach) + right circular send/stop nested *inside* the bar
 * - Multi-attach chips: filename + X only, horizontally scrollable
 */
@Composable
fun MinimaComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    enabled: Boolean = true,
    placeholder: String = "输入消息…",
    attachmentChips: List<ComposerChip> = emptyList(),
    onAttachClick: (() -> Unit)? = null,
    onRemoveAttachment: ((String) -> Unit)? = null,
    attachEnabled: Boolean = true,
    attaching: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMinimaColors.current
    val shape = RoundedCornerShape(28.dp)
    val hasAttachment = attachmentChips.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MinimaDimens.ComposerOuterPadding, vertical = 8.dp),
    ) {
        if (attachmentChips.isNotEmpty() || attaching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (attaching && attachmentChips.isEmpty()) {
                    AttachChip(
                        label = "准备中…",
                        onRemove = null,
                    )
                }
                for (chip in attachmentChips) {
                    AttachChip(
                        label = chip.label,
                        onRemove = if (onRemoveAttachment != null && !isStreaming) {
                            { onRemoveAttachment(chip.id) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(colors.bgContainer)
                .border(1.dp, colors.borderSecondary, shape)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT control
            if (onAttachClick != null) {
                val paperclipEnabled = enabled && !isStreaming && attachEnabled && !attaching
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = paperclipEnabled,
                            indication = ripple(bounded = true, radius = 20.dp),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onAttachClick,
                        )
                        .semantics {
                            contentDescription = "添加图片"
                            if (hasAttachment) stateDescription = "${attachmentChips.size} images attached"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = if (paperclipEnabled) {
                            if (hasAttachment) colors.accent else colors.textSecondary
                        } else {
                            colors.textQuaternary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp, max = 160.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MinimaTypography.bodyMedium,
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled && !isStreaming,
                    textStyle = MinimaTypography.bodyMedium.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // RIGHT control — circular send/stop nested inside the capsule
            if (isStreaming) {
                ComposerCircleButton(
                    onClick = onStop,
                    enabled = true,
                    filled = true,
                    contentDescription = "停止",
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                val canSend = enabled && !attaching && (value.isNotBlank() || hasAttachment)
                ComposerCircleButton(
                    onClick = onSend,
                    enabled = canSend,
                    filled = canSend,
                    contentDescription = "发送",
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (canSend) colors.onPrimary else colors.textQuaternary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachChip(
    label: String,
    onRemove: (() -> Unit)?,
) {
    val colors = LocalMinimaColors.current
    Row(
        modifier = Modifier
            .widthIn(max = 160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgContainer)
            .border(1.dp, colors.borderSecondary, RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .semantics { contentDescription = "Attached $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MinimaTypography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = "Remove $label" },
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposerCircleButton(
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalMinimaColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                when {
                    filled && enabled -> colors.primary
                    else -> colors.fill
                },
            )
            .clickable(
                enabled = enabled,
                indication = ripple(bounded = true, radius = 20.dp),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
