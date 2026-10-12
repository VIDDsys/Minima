package com.minima.chat.core.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minima.chat.core.ui.components.EmptyState
import com.minima.chat.core.ui.components.MessageBubble
import com.minima.chat.core.ui.components.MinimaComposer
import com.minima.chat.core.ui.components.ToolCard
import com.minima.chat.core.ui.components.ToolCardStatus
import com.minima.chat.core.ui.theme.AppearanceMode
import com.minima.chat.core.ui.theme.MinimaTheme

@Preview(name = "Light bubbles", showBackground = true)
@Composable
private fun PreviewBubblesLight() {
    MinimaTheme(appearance = AppearanceMode.Light) {
        Column(Modifier.padding(12.dp)) {
            MessageBubble(text = "Hello from user", isUser = true)
            MessageBubble(text = "Quiet reply with **bold**", isUser = false, useMarkdown = true)
            ToolCard(
                name = "sandbox_read",
                argsSummary = "notes.md",
                resultPreview = "ok",
                status = ToolCardStatus.Ok,
            )
        }
    }
}

@Preview(name = "Dark bubbles", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun PreviewBubblesDark() {
    MinimaTheme(appearance = AppearanceMode.Dark) {
        Column(Modifier.padding(12.dp)) {
            MessageBubble(text = "Hello from user", isUser = true)
            MessageBubble(text = "Quiet reply", isUser = false, isStreaming = true)
            EmptyState(title = "Say hello", subtitle = "Agent mode on.")
            MinimaComposer(
                value = "",
                onValueChange = {},
                onSend = {},
                onStop = {},
                isStreaming = false,
            )
        }
    }
}
