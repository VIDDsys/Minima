package com.minima.chat.core.data.util

import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole

/**
 * Pure helper: locate the last completed assistant turn and the user prompt
 * that produced it, so UI can delete trailing messages and re-run.
 *
 * Vision: [Plan.attachmentPath] / [Plan.attachmentMime] are carried so regenerate
 * re-sends the same on-disk image instead of dropping multimodal context.
 */
object RegeneratePlan {
    data class Plan(
        val userText: String,
        /** Inclusive lower bound for deleteFrom (user turn timestamp). */
        val afterCreatedAt: Long,
        val userMessageId: String,
        val attachmentPath: String? = null,
        val attachmentMime: String? = null,
    )

    fun from(messages: List<Message>): Plan? {
        if (messages.isEmpty()) return null
        val lastAssistantIdx = messages.indexOfLast {
            it.kind == MessageKind.Text &&
                it.role == MessageRole.Assistant &&
                it.content.isNotBlank() &&
                !it.isStreaming
        }
        if (lastAssistantIdx < 0) return null
        var userIdx = -1
        for (i in (lastAssistantIdx - 1) downTo 0) {
            val m = messages[i]
            if (m.kind == MessageKind.Text && m.role == MessageRole.User && isUserTurn(m)) {
                userIdx = i
                break
            }
        }
        if (userIdx < 0) return null
        val user = messages[userIdx]
        return Plan(
            userText = user.content,
            afterCreatedAt = user.createdAt,
            userMessageId = user.id,
            attachmentPath = user.attachmentPath,
            attachmentMime = user.attachmentMime,
        )
    }

    /** Text and/or an on-disk vision attachment counts as a user turn. */
    internal fun isUserTurn(m: Message): Boolean =
        m.content.isNotBlank() || !m.attachmentPath.isNullOrBlank()
}
