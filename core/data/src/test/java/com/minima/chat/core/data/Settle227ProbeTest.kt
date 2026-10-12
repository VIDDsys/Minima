package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.media.ImageAttachLogic
import com.minima.chat.core.data.util.HistoryWindow
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ImageAttachmentDto
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolCallFunctionDto
import org.junit.Test
import java.io.File

/**
 * Settle probes for v2.27.0 — HistoryWindow tool integrity + attachment confine.
 * Not a version bump; adversarial checks only.
 */
class Settle227ProbeTest {

    private fun sys(text: String) = ChatMessageDto(role = "system", content = text)
    private fun user(text: String, images: List<ImageAttachmentDto> = emptyList()) =
        ChatMessageDto(role = "user", content = text, images = images)
    private fun assistant(text: String) = ChatMessageDto(role = "assistant", content = text)
    private fun toolCall(id: String, name: String, args: String = "{}") = ToolCallDto(
        id = id,
        function = ToolCallFunctionDto(name = name, arguments = args),
    )
    private fun assistantTools(vararg calls: ToolCallDto) =
        ChatMessageDto(role = "assistant", content = null, toolCalls = calls.toList())
    private fun tool(callId: String, name: String, body: String) =
        ChatMessageDto(role = "tool", content = body, toolCallId = callId, name = name)

    @Test
    fun fit_always_preserves_toolIntegrity_when_input_was_ok() {
        val scenarios = listOf(
            // multi-tool round + later chat under tight budget
            listOf(
                sys("sys"),
                user("old " + "z".repeat(5_000)),
                assistant("old-a " + "z".repeat(5_000)),
                user("use tools"),
                assistantTools(toolCall("c1", "now"), toolCall("c2", "device_info"), toolCall("c3", "search_memory", """{"q":"${"x".repeat(200)}}""")),
                tool("c1", "now", "2026-10-12"),
                tool("c2", "device_info", "Pixel"),
                tool("c3", "search_memory", "hit " + "y".repeat(3_000)),
                assistant("done"),
            ),
            // only giant tool round (last-unit shrink path)
            listOf(
                sys("sys"),
                user("go"),
                assistantTools(
                    toolCall("c1", "http_get", """{"q":"${"x".repeat(12_000)}"}"""),
                    toolCall("c2", "http_get", """{"q":"${"w".repeat(12_000)}"}"""),
                ),
                tool("c1", "http_get", "y".repeat(12_000)),
                tool("c2", "http_get", "z".repeat(12_000)),
            ),
            // many sequential tool rounds — drop oldest units
            buildList {
                add(sys("You are Minima."))
                repeat(8) { i ->
                    add(user("u$i " + "a".repeat(800)))
                    add(assistantTools(toolCall("c$i", "now")))
                    add(tool("c$i", "now", "t$i " + "b".repeat(800)))
                    add(assistant("a$i"))
                }
            },
            // image strip + tool round coexist
            listOf(
                sys("sys"),
                user("pic1", listOf(ImageAttachmentDto("image/jpeg", "B".repeat(20_000)))),
                assistant("saw"),
                user("tools"),
                assistantTools(toolCall("t1", "now")),
                tool("t1", "now", "ok"),
                assistant("done"),
            ),
        )
        val budgets = listOf(2_000, 2_500, 4_000, 8_000, 15_000)
        for (msgs in scenarios) {
            assertThat(HistoryWindow.toolIntegrityOk(msgs)).isTrue()
            for (b in budgets) {
                val r = HistoryWindow.fit(msgs, maxChars = b)
                assertThat(HistoryWindow.toolIntegrityOk(r.messages)).isTrue()
                // If any tool remains, an assistant(tool_calls) must too
                val hasTool = r.messages.any { it.role.equals("tool", ignoreCase = true) }
                if (hasTool) {
                    assertThat(r.messages.any { !it.toolCalls.isNullOrEmpty() }).isTrue()
                }
            }
        }
    }

    @Test
    fun shrink_does_not_drop_assistant_tool_calls_message() {
        val msgs = listOf(
            sys("sys"),
            user("go"),
            assistantTools(toolCall("c1", "http_get", """{"q":"${"x".repeat(20_000)}"}""")),
            tool("c1", "http_get", "y".repeat(20_000)),
        )
        val r = HistoryWindow.fit(msgs, maxChars = 2_200)
        assertThat(HistoryWindow.toolIntegrityOk(r.messages)).isTrue()
        val asst = r.messages.firstOrNull { !it.toolCalls.isNullOrEmpty() }
        val tools = r.messages.filter { it.role.equals("tool", ignoreCase = true) }
        // Either both present (stubbed) or the whole tool unit dropped with older stuff —
        // but never tools without assistant.
        if (tools.isNotEmpty()) {
            assertThat(asst).isNotNull()
            assertThat(asst!!.toolCalls).isNotEmpty()
        }
    }

    @Test
    fun resolveAttachmentFile_symlink_escape_rejected() {
        val tmp = java.nio.file.Files.createTempDirectory("minima-settle-att").toFile()
        try {
            val filesDir = File(tmp, "files").also { it.mkdirs() }
            val attachments = File(filesDir, "attachments").also { it.mkdirs() }
            val secret = File(filesDir, "secret.txt").also { it.writeText("key") }
            val link = File(attachments, "evil.jpg")
            // Symlink into filesDir secret
            java.nio.file.Files.createSymbolicLink(link.toPath(), secret.toPath())
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "evil.jpg", filesDir)
            ).isNull()
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "attachments/evil.jpg", filesDir)
            ).isNull()
            // Absolute symlink path also rejected (canonical outside)
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, link.absolutePath, filesDir)
            ).isNull()
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun resolveAttachmentFile_absolute_under_attachments_ok() {
        val tmp = java.nio.file.Files.createTempDirectory("minima-settle-abs").toFile()
        try {
            val filesDir = File(tmp, "files").also { it.mkdirs() }
            val attachments = File(filesDir, "attachments").also { it.mkdirs() }
            val good = File(attachments, "uuid.jpg").also { it.writeText("jpeg") }
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, good.absolutePath, filesDir)
                    ?.canonicalFile
            ).isEqualTo(good.canonicalFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun resolveAttachmentFile_prefix_sibling_dir_rejected() {
        val tmp = java.nio.file.Files.createTempDirectory("minima-settle-prefix").toFile()
        try {
            val filesDir = File(tmp, "files").also { it.mkdirs() }
            val attachments = File(filesDir, "attachments").also { it.mkdirs() }
            // Sibling whose name starts with attachments prefix
            val sibling = File(filesDir, "attachments_evil").also { it.mkdirs() }
            val bait = File(sibling, "ok.jpg").also { it.writeText("nope") }
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, bait.absolutePath, filesDir)
            ).isNull()
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "attachments_evil/ok.jpg", filesDir)
            ).isNull()
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun resolveAttachmentFile_nul_and_blank_rejected() {
        val tmp = java.nio.file.Files.createTempDirectory("minima-settle-nul").toFile()
        try {
            val filesDir = File(tmp, "files").also { it.mkdirs() }
            val attachments = File(filesDir, "attachments").also { it.mkdirs() }
            File(attachments, "ok.jpg").writeText("jpeg")
            assertThat(ImageAttachLogic.resolveAttachmentFile(attachments, "", filesDir)).isNull()
            assertThat(ImageAttachLogic.resolveAttachmentFile(attachments, "   ", filesDir)).isNull()
            assertThat(
                ImageAttachLogic.resolveAttachmentFile(attachments, "ok\u0000.jpg", filesDir)
            ).isNull()
        } finally {
            tmp.deleteRecursively()
        }
    }
}
