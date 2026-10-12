package com.minima.chat.core.data.util

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.network.api.SecretRedact

/**
 * Transcript for share / clipboard.
 *
 * Always runs [SecretRedact] on title, content, tool args/results so FileProvider
 * exports and EXTRA_TEXT fallbacks do not leak keys beyond what the gate catches.
 *
 * [Mode.Plain] — compact text (INTENT EXTRA_TEXT legacy).
 * [Mode.MarkdownFile] — richer agent transcript for FileProvider .md share.
 */
object SessionTranscript {
    enum class Mode { Plain, MarkdownFile }

    const val PLAIN_TOOL_RESULT_MAX = 400
    const val PLAIN_TOOL_ARGS_MAX = 160
    const val FILE_TOOL_RESULT_MAX = 12_000
    const val FILE_TOOL_ARGS_MAX = 4_000

    fun format(
        title: String,
        messages: List<Message>,
        mode: Mode = Mode.Plain,
    ): String {
        val safeTitle = SecretRedact.redact(title).ifBlank { "chat" }
        if (messages.isEmpty()) {
            return when (mode) {
                Mode.Plain -> "# $safeTitle\n\n(empty)\n"
                Mode.MarkdownFile ->
                    "# $safeTitle\n\n_Exported from Minima · secrets redacted_\n\n(empty)\n"
            }
        }
        val argsMax = when (mode) {
            Mode.Plain -> PLAIN_TOOL_ARGS_MAX
            Mode.MarkdownFile -> FILE_TOOL_ARGS_MAX
        }
        val resultMax = when (mode) {
            Mode.Plain -> PLAIN_TOOL_RESULT_MAX
            Mode.MarkdownFile -> FILE_TOOL_RESULT_MAX
        }
        val body = buildString {
            appendLine("# $safeTitle")
            appendLine()
            if (mode == Mode.MarkdownFile) {
                appendLine("_Exported from Minima · agent transcript · secrets redacted_")
                appendLine()
            }
            for (m in messages) {
                when {
                    m.kind == MessageKind.Tool -> appendTool(m, mode, argsMax, resultMax)
                    m.kind == MessageKind.Text &&
                        (m.content.isNotBlank() || !m.attachmentPath.isNullOrBlank()) -> {
                        val who = when (m.role) {
                            MessageRole.User -> "You"
                            MessageRole.Assistant -> "Assistant"
                            MessageRole.System -> "System"
                            MessageRole.Tool -> "Tool"
                        }
                        if (mode == Mode.MarkdownFile) {
                            appendLine("## $who")
                        } else {
                            appendLine("$who:")
                        }
                        if (!m.attachmentPath.isNullOrBlank()) {
                            val mime = m.attachmentMime?.takeIf { it.isNotBlank() } ?: "image"
                            // Never export absolute paths — mime only.
                            appendLine("[attachment: $mime]")
                        }
                        if (m.content.isNotBlank()) {
                            appendLine(SecretRedact.redact(m.content.trim()))
                        }
                        appendLine()
                    }
                }
            }
        }.trimEnd()
        return body + "\n"
    }

    private fun StringBuilder.appendTool(
        m: Message,
        mode: Mode,
        argsMax: Int,
        resultMax: Int,
    ) {
        val name = SecretRedact.redact(m.toolName ?: "tool").ifBlank { "tool" }
        val status = m.toolStatus?.name?.lowercase() ?: "running"
        val step = if (m.agentStep > 0) m.agentStep else null
        when (mode) {
            Mode.Plain -> {
                val stepHint = step?.let { " ·$it" }.orEmpty()
                appendLine("[tool:$name — $status$stepHint]")
                val args = SecretRedact.redact(m.toolArgs?.trim().orEmpty())
                if (args.isNotEmpty()) {
                    appendLine(clip(args, argsMax))
                }
                val preview = SecretRedact.redact(m.toolResult?.trim().orEmpty())
                if (preview.isNotEmpty()) {
                    appendLine(clip(preview, resultMax))
                }
                appendLine()
            }
            Mode.MarkdownFile -> {
                val stepHint = step?.let { " · step $it" }.orEmpty()
                appendLine("### tool:`$name` — $status$stepHint")
                appendLine()
                val args = SecretRedact.redact(m.toolArgs?.trim().orEmpty())
                if (args.isNotEmpty()) {
                    appendLine("**args**")
                    appendLine("```")
                    appendLine(clip(args, argsMax))
                    appendLine("```")
                    if (args.length > argsMax) {
                        appendLine("_truncated args (${args.length} chars)_")
                    }
                    appendLine()
                }
                val preview = SecretRedact.redact(m.toolResult?.trim().orEmpty())
                if (preview.isNotEmpty()) {
                    appendLine("**result**")
                    appendLine("```")
                    appendLine(clip(preview, resultMax))
                    appendLine("```")
                    if (preview.length > resultMax) {
                        appendLine("_truncated result (${preview.length} chars)_")
                    }
                    appendLine()
                } else if (status == "running") {
                    appendLine("_still running_")
                    appendLine()
                }
            }
        }
    }

    /** Char-index clip with ellipsis; marker is separate when length exceeds max. */
    fun clip(text: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "…"
    }

    /**
     * Safe filesystem stem from session title.
     * Strips path-hostile chars and secret-shaped tokens so cache filenames stay bland.
     */
    fun sanitizeFilename(title: String, maxLen: Int = 40): String {
        val redacted = SecretRedact.redact(title)
        val cleaned = redacted.trim()
            .ifBlank { "chat" }
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]+"), "_")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("…|\\*{2,}"), "")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '_')
            .ifBlank { "chat" }
        return if (cleaned.length <= maxLen) cleaned else cleaned.take(maxLen).trimEnd('-', '_')
            .ifBlank { "chat" }
    }
}
