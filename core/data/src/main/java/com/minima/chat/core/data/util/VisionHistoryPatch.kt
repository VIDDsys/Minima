package com.minima.chat.core.data.util

import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ImageAttachmentDto

/**
 * Ensures the latest user turn carries multimodal image(s) even when on-disk
 * replay via [com.minima.chat.core.data.media.ImageAttachPrep.loadForReplay]
 * fails (path quirks / race). Call after [LlmHistoryMapper] / listForLlm.
 */
object VisionHistoryPatch {
    fun ensureLastUserImage(
        messages: List<ChatMessageDto>,
        image: ImageAttachmentDto?,
    ): List<ChatMessageDto> = ensureLastUserImages(messages, listOfNotNull(image))

    fun ensureLastUserImages(
        messages: List<ChatMessageDto>,
        images: List<ImageAttachmentDto>,
    ): List<ChatMessageDto> {
        val sendable = images.filter { it.isSendable() }
        if (sendable.isEmpty()) return messages
        val idx = messages.indexOfLast { it.role == "user" }
        if (idx < 0) return messages
        val m = messages[idx]
        if (m.images.any { it.isSendable() }) return messages
        return messages.toMutableList().also {
            it[idx] = m.copy(images = sendable)
        }
    }
}
