package com.minima.chat.core.data.util

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus

/**
 * Locate the last user turn that can be pulled back into the composer for edit+resend.
 * "Clean" = not mid-stream and no tool still Running after that user message.
 *
 * Vision: preserves [Plan.attachmentPath] / [Plan.attachmentMime] so the composer
 * can restore the pending image chip (file is not deleted until user clears it).
 */
object EditLastUserPlan {
    data class Plan(
        val userText: String,
        val userMessageId: String,
        /** Inclusive — delete messages with createdAt >= this. */
        val fromCreatedAt: Long,
        val attachmentPath: String? = null,
        val attachmentMime: String? = null,
    )

    fun from(messages: List<Message>, isStreaming: Boolean): Plan? {
        if (isStreaming || messages.isEmpty()) return null
        val lastUserIdx = messages.indexOfLast {
            it.kind == MessageKind.Text &&
                it.role == MessageRole.User &&
                isUserTurn(it)
        }
        if (lastUserIdx < 0) return null
        val user = messages[lastUserIdx]
        val trailing = messages.subList(lastUserIdx + 1, messages.size)
        val dirtyTool = trailing.any {
            it.kind == MessageKind.Tool && it.toolStatus == ToolMessageStatus.Running
        }
        if (dirtyTool) return null
        // Composer draft: strip the synthetic "(image)" placeholder when vision-only.
        val draft = when {
            user.content == "(image)" && !user.attachmentPath.isNullOrBlank() -> ""
            else -> user.content
        }
        return Plan(
            userText = draft,
            userMessageId = user.id,
            fromCreatedAt = user.createdAt,
            attachmentPath = user.attachmentPath,
            attachmentMime = user.attachmentMime,
        )
    }

    internal fun isUserTurn(m: Message): Boolean =
        m.content.isNotBlank() || !m.attachmentPath.isNullOrBlank()
}
