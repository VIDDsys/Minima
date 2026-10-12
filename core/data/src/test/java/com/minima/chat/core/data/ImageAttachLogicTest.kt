package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.media.AttachmentCodec
import com.minima.chat.core.data.media.ImageAttachLogic
import com.minima.chat.core.data.media.ImageAttachPrep
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.data.util.LlmHistoryMapper
import com.minima.chat.core.network.dto.ImageAttachmentDto
import org.junit.Test

class ImageAttachLogicTest {
    @Test
    fun inSampleSize_halves_until_under_max() {
        assertThat(ImageAttachLogic.computeInSampleSize(4000, 3000, 1280)).isEqualTo(2)
        assertThat(ImageAttachLogic.computeInSampleSize(1280, 720, 1280)).isEqualTo(1)
        assertThat(ImageAttachLogic.computeInSampleSize(10_000, 10_000, 1280)).isAtLeast(4)
    }

    @Test
    fun scaledSize_preserves_aspect() {
        val (w, h) = ImageAttachLogic.scaledSize(4000, 2000, 1280)
        assertThat(w).isEqualTo(1280)
        assertThat(h).isEqualTo(640)
    }

    @Test
    fun chipLabel_truncates() {
        assertThat(ImageAttachLogic.chipLabel("short.jpg")).isEqualTo("short.jpg")
        val long = "a".repeat(40) + ".jpg"
        assertThat(ImageAttachLogic.chipLabel(long).length).isAtMost(28)
        assertThat(ImageAttachLogic.chipLabel(long)).endsWith("…")
    }

    @Test
    fun llmHistory_attaches_image_via_loader() {
        val msgs = listOf(
            Message("1", "s", MessageRole.User, "see this", 1, kind = MessageKind.Text, attachmentPath = "attachments/x.jpg"),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs) { m ->
            if (m.attachmentPath != null) listOf(ImageAttachmentDto("image/jpeg", "qq")) else emptyList()
        }
        assertThat(dto).hasSize(1)
        assertThat(dto[0].images).hasSize(1)
        assertThat(dto[0].content).isEqualTo("see this")
    }

    @Test
    fun llmHistory_imageOnly_user() {
        val msgs = listOf(
            Message("1", "s", MessageRole.User, "(image)", 1, kind = MessageKind.Text, attachmentPath = "a.jpg"),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs) {
            listOf(ImageAttachmentDto("image/jpeg", "ab"))
        }
        assertThat(dto[0].images).hasSize(1)
        assertThat(dto[0].content).isNull()
    }

    @Test
    fun maxEdge_constant() {
        assertThat(ImageAttachPrep.MAX_EDGE_PX).isEqualTo(1280)
    }

    @Test
    fun estimatedArgbBytes_huge_source_stays_under_budget() {
        // 48MP phone photo → after sample+scale to 1280 edge, ARGB peak << 50MB
        val bytes = ImageAttachLogic.estimatedArgbBytesAfterDownscale(8000, 6000, ImageAttachPrep.MAX_EDGE_PX)
        assertThat(bytes).isAtMost(1280L * 1280L * 4L)
        assertThat(bytes).isLessThan(8L * 1024L * 1024L)
    }

    @Test
    fun llmHistory_image_then_tools_preserves_multimodal_user() {
        val msgs = listOf(
            Message(
                "u", "s", MessageRole.User, "what is this?", 1,
                kind = MessageKind.Text,
                attachmentPath = "attachments/a.jpg",
                attachmentMime = "image/jpeg",
            ),
            Message("a", "s", MessageRole.Assistant, "looked", 2, kind = MessageKind.Text),
            Message(
                "t", "s", MessageRole.Tool, "tool", 3,
                kind = MessageKind.Tool,
                toolName = "now",
                toolCallId = "c1",
                toolArgs = "{}",
                toolResult = "ok",
                toolStatus = ToolMessageStatus.Ok,
                agentStep = 1,
            ),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs) { m ->
            if (m.attachmentPath != null) listOf(ImageAttachmentDto("image/jpeg", "qq")) else emptyList()
        }
        assertThat(dto.first().role).isEqualTo("user")
        assertThat(dto.first().images).hasSize(1)
        assertThat(dto.any { it.toolCalls != null }).isTrue()
    }

    @Test
    fun resolveAttachmentFile_rejectsEscape() {
        val tmp = java.nio.file.Files.createTempDirectory("minima-att").toFile()
        try {
            val filesDir = java.io.File(tmp, "files").also { it.mkdirs() }
            val attachments = java.io.File(filesDir, "attachments").also { it.mkdirs() }
            val good = java.io.File(attachments, "ok.jpg").also { it.writeText("jpeg") }
            val secret = java.io.File(filesDir, "secret.txt").also { it.writeText("key") }

            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "ok.jpg", filesDir)?.absolutePath
            ).isEqualTo(good.canonicalFile.absolutePath)

            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "attachments/ok.jpg", filesDir)?.absolutePath
            ).isEqualTo(good.canonicalFile.absolutePath)

            // Absolute outside attachments
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, secret.absolutePath, filesDir)
            ).isNull()

            // Relative escape
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "../secret.txt", filesDir)
            ).isNull()

            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "attachments/../secret.txt", filesDir)
            ).isNull()
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun attachmentCodec_roundTrip_and_legacy() {
        val packed = AttachmentCodec.encode(
            listOf(
                AttachmentCodec.Ref("photo one.jpg", "attachments/a.jpg"),
                AttachmentCodec.Ref("vacation.png", "attachments/b.jpg"),
            ),
        )
        val decoded = AttachmentCodec.decode(packed)
        assertThat(decoded).hasSize(2)
        assertThat(decoded[0].displayName).isEqualTo("photo one.jpg")
        assertThat(decoded[1].path).isEqualTo("attachments/b.jpg")
        assertThat(AttachmentCodec.bubbleLabel(packed)).contains("photo")
        // Legacy bare path
        val legacy = AttachmentCodec.decode("attachments/uuid-digits.jpg")
        assertThat(legacy).hasSize(1)
        assertThat(legacy[0].path).isEqualTo("attachments/uuid-digits.jpg")
    }

}
