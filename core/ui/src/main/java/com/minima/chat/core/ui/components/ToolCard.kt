package com.minima.chat.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minima.chat.core.network.api.SecretRedact
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaDimens
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.core.ui.util.ToolCardExpand
import com.minima.chat.core.ui.util.ToolPreviewText

enum class ToolCardStatus { Running, Ok, Error }

/**
 * Expand: collapsed ↔ expanded (single tap either way).
 * Args/results are [SecretRedact]-ed before display so expand never paints raw keys.
 *
 * Keeps slow [animateContentSize] expand. Ripple / circular indication is disabled —
 * that Material ripple was the tiny circle that grew to fill the card (~1–2s).
 */
@Composable
fun ToolCard(
    name: String,
    argsSummary: String = "",
    resultPreview: String = "",
    status: ToolCardStatus,
    modifier: Modifier = Modifier,
    /** 1-based agent iteration; shown only when > 0 (ink-minimal hint). */
    agentStep: Int = 0,
    /** Stable id so expand state resets when the row identity changes. */
    expandKey: String = name,
) {
    val colors = LocalMinimaColors.current
    var expandLevel by remember(expandKey) { mutableIntStateOf(ToolCardExpand.COLLAPSED) }
    val safeArgs = remember(argsSummary) { SecretRedact.redact(argsSummary) }
    val safeResult = remember(resultPreview) { SecretRedact.redact(resultPreview) }
    val interaction = remember { MutableInteractionSource() }
    val icon = when (status) {
        ToolCardStatus.Running -> Icons.Outlined.HourglassEmpty
        ToolCardStatus.Ok -> Icons.Outlined.CheckCircle
        ToolCardStatus.Error -> Icons.Outlined.ErrorOutline
    }
    val tint = when (status) {
        ToolCardStatus.Running -> colors.accent
        ToolCardStatus.Ok -> colors.textSecondary
        ToolCardStatus.Error -> colors.error
    }
    val statusLabel = when (status) {
        ToolCardStatus.Running -> "running"
        ToolCardStatus.Ok -> "ok"
        ToolCardStatus.Error -> "error"
    }
    val stepHint = if (agentStep > 0) " ·$agentStep" else ""
    val argsCap = if (expandLevel >= ToolCardExpand.PREVIEW) {
        ToolPreviewText.ARGS_EXPANDED
    } else {
        ToolPreviewText.ARGS_COLLAPSED
    }
    val resultCap = if (expandLevel >= ToolCardExpand.PREVIEW) {
        ToolPreviewText.RESULT_FULL
    } else {
        ToolPreviewText.RESULT_EXPANDED
    }
    val argsSlice = remember(safeArgs, argsCap) { ToolPreviewText.slice(safeArgs, argsCap) }
    val resultSlice = remember(safeResult, resultCap) { ToolPreviewText.slice(safeResult, resultCap) }
    val expandStateLabel = if (expandLevel >= ToolCardExpand.PREVIEW) "expanded" else "collapsed"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MinimaDimens.ScreenPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.fillTertiary)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                expandLevel = ToolCardExpand.next(expandLevel)
            }
            .animateContentSize()
            .semantics {
                contentDescription = "Tool $name $statusLabel$stepHint"
                stateDescription = expandStateLabel
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Build,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MinimaTypography.labelMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (agentStep > 0) {
                Text(
                    "·$agentStep",
                    style = MinimaTypography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                statusLabel,
                style = MinimaTypography.labelSmall,
                color = tint,
            )
        }
        if (argsSlice.text.isNotBlank()) {
            Text(
                argsSlice.text,
                style = MinimaTypography.bodySmall,
                color = colors.textTertiary,
                maxLines = if (expandLevel >= ToolCardExpand.PREVIEW) 12 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 26.dp),
            )
            ToolPreviewText.truncationHint(argsSlice)?.let { hint ->
                if (expandLevel >= ToolCardExpand.PREVIEW) {
                    Text(
                        hint,
                        style = MinimaTypography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 2.dp, start = 26.dp),
                    )
                }
            }
        }
        if (expandLevel >= ToolCardExpand.PREVIEW && resultSlice.text.isNotBlank()) {
            Text(
                resultSlice.text,
                style = MinimaTypography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp, start = 26.dp),
            )
            ToolPreviewText.truncationHint(resultSlice)?.let { hint ->
                Text(
                    hint,
                    style = MinimaTypography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp, start = 26.dp),
                )
            }
        }
    }
}
