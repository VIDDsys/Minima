package com.minima.chat.core.data

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.data.util.SessionTranscript
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionTranscriptTest {
    private fun msg(
        id: String,
        role: MessageRole,
        content: String,
        at: Long,
        kind: MessageKind = MessageKind.Text,
        toolName: String? = null,
        toolStatus: ToolMessageStatus? = null,
        toolResult: String? = null,
        toolArgs: String? = null,
        agentStep: Int = 0,
        attachmentPath: String? = null,
        attachmentMime: String? = null,
    ) = Message(
        id = id,
        sessionId = "s",
        role = role,
        content = content,
        createdAt = at,
        kind = kind,
        toolName = toolName,
        toolStatus = toolStatus,
        toolResult = toolResult,
        toolArgs = toolArgs,
        agentStep = agentStep,
        attachmentPath = attachmentPath,
        attachmentMime = attachmentMime,
    )

    @Test
    fun formatsUserAndAssistant() {
        val text = SessionTranscript.format(
            "Demo",
            listOf(
                msg("u1", MessageRole.User, "hello", 1),
                msg("a1", MessageRole.Assistant, "hi there", 2),
            ),
        )
        assertThat(text).contains("# Demo")
        assertThat(text).contains("You:")
        assertThat(text).contains("hello")
        assertThat(text).contains("Assistant:")
        assertThat(text).contains("hi there")
    }

    @Test
    fun includesToolCards() {
        val text = SessionTranscript.format(
            "T",
            listOf(
                msg("u1", MessageRole.User, "list", 1),
                msg(
                    "t1",
                    MessageRole.Tool,
                    "tool",
                    2,
                    kind = MessageKind.Tool,
                    toolName = "list_dir",
                    toolStatus = ToolMessageStatus.Ok,
                    toolResult = "a.txt",
                ),
            ),
        )
        assertThat(text).contains("[tool:list_dir — ok]")
        assertThat(text).contains("a.txt")
    }

    @Test
    fun emptySession() {
        val text = SessionTranscript.format("Empty", emptyList())
        assertThat(text).contains("(empty)")
    }

    @Test
    fun markdownFile_includesArgsStepAndTruncationMarker() {
        val longResult = "R".repeat(SessionTranscript.FILE_TOOL_RESULT_MAX + 50)
        val text = SessionTranscript.format(
            "Agent run",
            listOf(
                msg("u1", MessageRole.User, "do it", 1),
                msg(
                    "t1",
                    MessageRole.Tool,
                    "tool",
                    2,
                    kind = MessageKind.Tool,
                    toolName = "read_file",
                    toolStatus = ToolMessageStatus.Ok,
                    toolArgs = """{"path":"notes.md"}""",
                    toolResult = longResult,
                    agentStep = 2,
                ),
                msg("a1", MessageRole.Assistant, "done", 3),
            ),
            SessionTranscript.Mode.MarkdownFile,
        )
        assertThat(text).contains("_Exported from Minima")
        assertThat(text).contains("secrets redacted")
        assertThat(text).contains("## You")
        assertThat(text).contains("### tool:`read_file` — ok · step 2")
        assertThat(text).contains("**args**")
        assertThat(text).contains("notes.md")
        assertThat(text).contains("**result**")
        assertThat(text).contains("truncated result")
        assertThat(text).contains("## Assistant")
        assertThat(text).contains("done")
        // Clipped body must not contain the full dump (max + ellipsis only)
        assertThat(text.count { it == 'R' }).isAtMost(SessionTranscript.FILE_TOOL_RESULT_MAX)
        assertThat(text).doesNotContain("R".repeat(SessionTranscript.FILE_TOOL_RESULT_MAX + 1))
    }

    @Test
    fun markdownFile_attachmentNote() {
        val text = SessionTranscript.format(
            "Vision",
            listOf(
                msg(
                    "u1",
                    MessageRole.User,
                    "what is this?",
                    1,
                    attachmentPath = "attachments/a.jpg",
                    attachmentMime = "image/jpeg",
                ),
            ),
            SessionTranscript.Mode.MarkdownFile,
        )
        assertThat(text).contains("[attachment: image/jpeg]")
        assertThat(text).contains("what is this?")
    }

    @Test
    fun sanitizeFilename_stripsBadChars() {
        assertThat(SessionTranscript.sanitizeFilename("My Chat:/v1*")).doesNotContain("/")
        assertThat(SessionTranscript.sanitizeFilename("My Chat:/v1*")).doesNotContain("*")
        assertThat(SessionTranscript.sanitizeFilename("   ")).isEqualTo("chat")
        assertThat(SessionTranscript.sanitizeFilename("a".repeat(100)).length).isAtMost(40)
    }

    @Test
    fun plain_includesStepHint() {
        val text = SessionTranscript.format(
            "T",
            listOf(
                msg(
                    "t1",
                    MessageRole.Tool,
                    "tool",
                    1,
                    kind = MessageKind.Tool,
                    toolName = "tick",
                    toolStatus = ToolMessageStatus.Ok,
                    toolResult = "ok",
                    agentStep = 3,
                ),
            ),
        )
        assertThat(text).contains("[tool:tick — ok ·3]")
    }


    @Test
    fun markdownFile_redactsEmbeddedApiKeyInContentAndTools() {
        val key = "sk-abcdefghijklmnopqrstuvwxyz0123"
        val text = SessionTranscript.format(
            "Leak",
            listOf(
                msg("u1", MessageRole.User, "my key is $key", 1),
                msg(
                    "t1",
                    MessageRole.Tool,
                    "tool",
                    2,
                    kind = MessageKind.Tool,
                    toolName = "read_file",
                    toolStatus = ToolMessageStatus.Ok,
                    toolArgs = """{"token":"Bearer $key"}""",
                    toolResult = "Authorization: Bearer $key",
                    agentStep = 1,
                ),
            ),
            SessionTranscript.Mode.MarkdownFile,
        )
        assertThat(text).doesNotContain(key)
        assertThat(text).contains("***")
    }

    @Test
    fun sanitizeFilename_stripsSecretShapedTitle() {
        val stem = SessionTranscript.sanitizeFilename("sk-abcdefghijklmnopqrstuv chat")
        assertThat(stem.lowercase()).doesNotContain("sk-abcdefghijklmnopqrstuv")
    }
}
