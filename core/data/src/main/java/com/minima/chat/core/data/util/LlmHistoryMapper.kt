package com.minima.chat.core.data.util

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ImageAttachmentDto
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolCallFunctionDto

/**
 * Rebuilds OpenAI-compatible chat messages from persisted Room rows so that
 * prior tool rounds survive into the next request.
 *
 * DB order for an agent turn is either:
 *   Legacy: user → assistant(all text) → tool card(s)…
 *   Interleaved: user → assistant(pre) → tool… → assistant(post) → …
 * which must become OpenAI shape:
 *   user → assistant(tool_calls[, leading content]) → tool… → […]* → assistant(final text)
 *
 * Same-turn multi-iteration tools carry [Message.agentStep] (1-based). Groups with
 * different steps emit separate assistant(tool_calls)+tool rounds, matching in-turn
 * AgentLoop memory. Legacy rows with agentStep == 0 stay one flat group.
 *
 * Incomplete (Running) tool cards are skipped. Orphan tool clusters
 * (no preceding assistant) still emit a synthetic assistant(tool_calls).
 *
 * Vision: optional [imageLoader] attaches OpenAI `image_url` parts (0..N) for user rows
 * that still have readable on-disk attachment(s).
 */
object LlmHistoryMapper {

    /** Cap stored/replayed tool payloads so a huge dump cannot blow the next request. */
    const val MAX_TOOL_PAYLOAD_CHARS = ToolResultLimiter.MAX_CHARS

    fun toChatMessages(
        messages: List<Message>,
        excludeMessageIds: Set<String> = emptySet(),
        imageLoader: (Message) -> List<ImageAttachmentDto> = { emptyList() },
    ): List<ChatMessageDto> {
        val out = mutableListOf<ChatMessageDto>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            if (m.id in excludeMessageIds) {
                i++
                continue
            }
            when (m.kind) {
                MessageKind.Tool -> {
                    val (tools, next) = collectCompletedTools(messages, i, excludeMessageIds)
                    if (tools.isNotEmpty()) {
                        emitToolRounds(out, tools, leadingAssistantContent = null, finalAssistantContent = null)
                    }
                    i = next
                }
                MessageKind.Text -> when (m.role) {
                    MessageRole.User -> {
                        val imgs = imageLoader(m).filter { it.isSendable() }
                        val text = m.content
                        if (text.isNotBlank() || imgs.isNotEmpty()) {
                            val contentText = when {
                                imgs.isNotEmpty() && (text.isBlank() || text == "(image)") -> null
                                text.isNotBlank() -> text
                                else -> null
                            }
                            out += ChatMessageDto(
                                role = "user",
                                content = contentText,
                                images = imgs,
                            )
                        }
                        i++
                    }
                    MessageRole.System -> {
                        if (m.content.isNotBlank()) {
                            out += ChatMessageDto(role = "system", content = m.content)
                        }
                        i++
                    }
                    MessageRole.Assistant -> {
                        val (tools, next) = collectCompletedTools(messages, i + 1, excludeMessageIds)
                        if (tools.isNotEmpty()) {
                            // Interleaved UI: A(pre) → tools → A(post). Leading text rides on
                            // the tool_calls message; trailing assistant is emitted later.
                            // Legacy single-buffer: A(all text) → tools with no trailing A →
                            // keep text as final after tools (old OpenAI replay shape).
                            val leading = m.content.trim().takeIf { it.isNotEmpty() }
                            val trailingIsAssistant = next < messages.size &&
                                messages[next].id !in excludeMessageIds &&
                                messages[next].kind == MessageKind.Text &&
                                messages[next].role == MessageRole.Assistant
                            if (trailingIsAssistant) {
                                emitToolRounds(
                                    out,
                                    tools,
                                    leadingAssistantContent = leading,
                                    finalAssistantContent = null,
                                )
                            } else {
                                emitToolRounds(
                                    out,
                                    tools,
                                    leadingAssistantContent = null,
                                    finalAssistantContent = leading,
                                )
                            }
                            i = next
                        } else {
                            if (m.content.isNotBlank()) {
                                out += ChatMessageDto(role = "assistant", content = m.content)
                            }
                            i++
                        }
                    }
                    MessageRole.Tool -> {
                        // Unexpected text-kind tool role — skip
                        i++
                    }
                }
            }
        }
        return out
    }

    private fun collectCompletedTools(
        messages: List<Message>,
        start: Int,
        excludeMessageIds: Set<String>,
    ): Pair<List<Message>, Int> {
        val tools = mutableListOf<Message>()
        var j = start
        while (j < messages.size) {
            val t = messages[j]
            if (t.id in excludeMessageIds) {
                j++
                continue
            }
            if (t.kind != MessageKind.Tool) break
            if (isReplayableTool(t)) {
                tools += t
            }
            j++
        }
        return tools to j
    }

    private fun isReplayableTool(t: Message): Boolean {
        if (t.toolStatus == ToolMessageStatus.Running) return false
        if (t.toolCallId.isNullOrBlank()) return false
        if (t.toolName.isNullOrBlank()) return false
        return true
    }

    /**
     * Split consecutive tool cards into AgentLoop iteration groups.
     * All agentStep <= 0 → single legacy group. Otherwise group by consecutive equal steps.
     */
    internal fun groupToolsByStep(tools: List<Message>): List<List<Message>> {
        if (tools.isEmpty()) return emptyList()
        if (tools.all { it.agentStep <= 0 }) {
            return listOf(tools)
        }
        val groups = mutableListOf<MutableList<Message>>()
        for (t in tools) {
            val step = t.agentStep.coerceAtLeast(0)
            if (groups.isEmpty() || groups.last().last().agentStep.coerceAtLeast(0) != step) {
                groups += mutableListOf(t)
            } else {
                groups.last() += t
            }
        }
        return groups
    }

    private fun emitToolRounds(
        out: MutableList<ChatMessageDto>,
        tools: List<Message>,
        leadingAssistantContent: String?,
        finalAssistantContent: String?,
    ) {
        val groups = groupToolsByStep(tools)
        groups.forEachIndexed { index, group ->
            val isFirst = index == 0
            val isLast = index == groups.lastIndex
            emitToolRound(
                out,
                group,
                leadingAssistantContent = if (isFirst) leadingAssistantContent else null,
                finalAssistantContent = if (isLast) finalAssistantContent else null,
            )
        }
    }

    private fun emitToolRound(
        out: MutableList<ChatMessageDto>,
        tools: List<Message>,
        leadingAssistantContent: String?,
        finalAssistantContent: String?,
    ) {
        out += ChatMessageDto(
            role = "assistant",
            content = leadingAssistantContent,
            toolCalls = tools.map { toolCallFrom(it) },
        )
        for (t in tools) {
            val body = ToolResultLimiter.forLlm(
                t.toolResult ?: t.content,
                MAX_TOOL_PAYLOAD_CHARS,
            )
            out += ChatMessageDto(
                role = "tool",
                content = body,
                toolCallId = t.toolCallId,
                name = t.toolName,
            )
        }
        if (!finalAssistantContent.isNullOrBlank()) {
            out += ChatMessageDto(role = "assistant", content = finalAssistantContent)
        }
    }

    private fun toolCallFrom(t: Message): ToolCallDto {
        val args = t.toolArgs
            ?.take(MAX_TOOL_PAYLOAD_CHARS)
            ?.takeIf { it.isNotBlank() }
            ?: "{}"
        return ToolCallDto(
            id = t.toolCallId!!,
            function = ToolCallFunctionDto(
                name = t.toolName!!,
                arguments = args,
            ),
        )
    }
}
