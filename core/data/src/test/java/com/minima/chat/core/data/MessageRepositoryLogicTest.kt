package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.data.util.LlmHistoryMapper
import org.junit.Test

/** Pure helpers mirroring repository title truncation + LLM history mapping. */
class MessageRepositoryLogicTest {
    @Test
    fun titleFromFirstLine_truncates() {
        val content = "Hello world this is a very long first message that should be cut"
        val title = content.trim().lineSequence().firstOrNull()?.take(48)
        assertThat(title!!.length).isAtMost(48)
        assertThat(title).startsWith("Hello world")
    }

    @Test
    fun messageRoles_mapToApiStrings() {
        fun role(m: Message) = when (m.role) {
            MessageRole.User -> "user"
            MessageRole.Assistant -> "assistant"
            MessageRole.System -> "system"
            MessageRole.Tool -> "tool"
        }
        val u = Message("1", "s", MessageRole.User, "hi", 0)
        val a = Message("2", "s", MessageRole.Assistant, "yo", 0)
        assertThat(role(u)).isEqualTo("user")
        assertThat(role(a)).isEqualTo("assistant")
    }

    @Test
    fun listForLlm_replaysToolKind_notDropped() {
        val msgs = listOf(
            Message("1", "s", MessageRole.User, "hi", 0, kind = MessageKind.Text),
            Message(
                "3", "s", MessageRole.Assistant, "ok", 2, kind = MessageKind.Text,
            ),
            Message(
                "2", "s", MessageRole.Tool, "🔧 now", 1,
                kind = MessageKind.Tool,
                toolName = "now",
                toolArgs = "{}",
                toolResult = "tick",
                toolStatus = ToolMessageStatus.Ok,
                toolCallId = "call_x",
            ),
        )
        // Chronological: user(0), tool(1), assistant(2) — orphan tool then assistant text
        val ordered = msgs.sortedBy { it.createdAt }
        val forLlm = LlmHistoryMapper.toChatMessages(ordered)
        assertThat(forLlm.any { it.role == "tool" }).isTrue()
        assertThat(forLlm.any { it.toolCalls != null }).isTrue()
        assertThat(forLlm.map { it.role }).contains("user")
        assertThat(forLlm.map { it.role }).contains("assistant")
    }
}
