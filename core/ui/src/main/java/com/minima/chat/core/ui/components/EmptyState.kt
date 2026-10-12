package com.minima.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography

/**
 * Quiet centered empty / first-open state. Optional primary + secondary actions and
 * suggestion chips (fill draft / navigate — caller decides).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    suggestions: List<String> = emptyList(),
    onSuggestion: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val colors = LocalMinimaColors.current
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .semantics { contentDescription = "Empty: $title" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MinimaTypography.titleLarge,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MinimaTypography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onAction,
                modifier = Modifier.semantics { contentDescription = actionLabel },
            ) {
                Text(actionLabel, color = colors.accent)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(2.dp))
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.semantics { contentDescription = secondaryActionLabel },
            ) {
                Text(secondaryActionLabel, color = colors.textSecondary)
            }
        }
        if (suggestions.isNotEmpty() && onSuggestion != null) {
            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Empty suggestions" },
            ) {
                suggestions.forEach { label ->
                    SuggestionChip(
                        label = label,
                        onClick = { onSuggestion(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    val colors = LocalMinimaColors.current
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = label,
        style = MinimaTypography.bodySmall,
        color = colors.text,
        modifier = Modifier
            .clip(shape)
            .background(colors.bgContainer)
            .border(1.dp, colors.borderSecondary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "Suggestion $label" },
    )
}
