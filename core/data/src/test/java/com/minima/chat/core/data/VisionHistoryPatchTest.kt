package com.minima.chat.core.data

import com.minima.chat.core.data.util.VisionHistoryPatch
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ImageAttachmentDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisionHistoryPatchTest {
    @Test
    fun patchesLastUserWhenMissingImages() {
        val msgs = listOf(
            ChatMessageDto(role = "system", content = "sys"),
            ChatMessageDto(role = "user", content = "see"),
            ChatMessageDto(role = "assistant", content = "ok"),
            ChatMessageDto(role = "user", content = "what is this?"),
        )
        val img = ImageAttachmentDto("image/jpeg", "abc")
        val out = VisionHistoryPatch.ensureLastUserImage(msgs, img)
        assertThat(out.last().images).hasSize(1)
        assertThat(out.last().images[0].base64).isEqualTo("abc")
        assertThat(out[1].images).isEmpty()
    }

    @Test
    fun doesNotOverwriteExistingImage() {
        val existing = ImageAttachmentDto("image/jpeg", "old")
        val msgs = listOf(
            ChatMessageDto(role = "user", content = "x", images = listOf(existing)),
        )
        val out = VisionHistoryPatch.ensureLastUserImage(
            msgs,
            ImageAttachmentDto("image/jpeg", "new"),
        )
        assertThat(out[0].images[0].base64).isEqualTo("old")
    }

    @Test
    fun nullImageNoop() {
        val msgs = listOf(ChatMessageDto(role = "user", content = "hi"))
        assertThat(VisionHistoryPatch.ensureLastUserImage(msgs, null)).isEqualTo(msgs)
    }
}
