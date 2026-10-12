package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.util.HistoryWindow
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ImageAttachmentDto
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolCallFunctionDto
import org.junit.Test

class HistoryWindowTest {

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
    fun underBudget_unchanged() {
        val msgs = listOf(sys("s"), user("hi"), assistant("hello"))
        val r = HistoryWindow.fit(msgs, maxChars = 50_000)
        assertThat(r.didTrim).isFalse()
        assertThat(r.messages).isEqualTo(msgs)
        assertThat(r.omittedUnits).isEqualTo(0)
        assertThat(r.strippedImages).isEqualTo(0)
    }

    @Test
    fun dropsOldestTurns_keepsRecent_andInsertsOmissionNote() {
        val msgs = listOf(
            sys("You are Minima."),
            user("turn1 " + "x".repeat(2_000)),
            assistant("a1 " + "y".repeat(2_000)),
            user("turn2 " + "x".repeat(2_000)),
            assistant("a2 " + "y".repeat(2_000)),
            user("latest"),
            assistant("recent"),
        )
        val r = HistoryWindow.fit(msgs, maxChars = 6_000)
        assertThat(r.omittedUnits).isGreaterThan(0)
        assertThat(r.messages.first().role).isEqualTo("system")
        assertThat(r.messages.first().content).isEqualTo("You are Minima.")
        assertThat(r.messages.any { it.content == HistoryWindow.OMISSION_NOTE }).isTrue()
        assertThat(r.messages.last().content).isEqualTo("recent")
        assertThat(r.charsAfter).isAtMost(6_000)
        // Must keep the latest exchange
        assertThat(r.messages.map { it.content }).contains("latest")
    }

    @Test
    fun neverSplitsToolCallRound() {
        val msgs = listOf(
            sys("sys"),
            user("old " + "z".repeat(3_000)),
            assistant("old-a " + "z".repeat(3_000)),
            user("use tools"),
            assistantTools(toolCall("c1", "now"), toolCall("c2", "device_info")),
            tool("c1", "now", "2026-10-12"),
            tool("c2", "device_info", "Pixel"),
            assistant("done"),
        )
        val r = HistoryWindow.fit(msgs, maxChars = 4_500)
        assertThat(r.charsAfter).isAtMost(4_500)
        val roles = r.messages.map { it.role }
        // If any tool role survived, its assistant(tool_calls) must precede it in the same window
        val toolIdx = roles.indexOf("tool")
        if (toolIdx >= 0) {
            assertThat(roles[toolIdx - 1]).isEqualTo("assistant")
            assertThat(r.messages[toolIdx - 1].toolCalls).isNotEmpty()
            // Both tools from the same round should be present together
            val afterAssistant = r.messages.drop(toolIdx - 1)
            val toolRoles = afterAssistant.takeWhile {
                it.role == "assistant" && !it.toolCalls.isNullOrEmpty() || it.role == "tool"
            }
            assertThat(toolRoles.count { it.role == "tool" }).isEqualTo(2)
        }
        // Final assistant kept when possible
        assertThat(r.messages.last().content).isEqualTo("done")
    }

    @Test
    fun stripsOlderImages_keepsMostRecent() {
        val big = "B".repeat(8_000)
        val msgs = listOf(
            sys("sys"),
            user("pic1", listOf(ImageAttachmentDto("image/jpeg", big))),
            assistant("saw1"),
            user("pic2", listOf(ImageAttachmentDto("image/jpeg", big))),
            assistant("saw2"),
            user("pic3 latest", listOf(ImageAttachmentDto("image/jpeg", big))),
        )
        val r = HistoryWindow.fit(msgs, maxChars = 20_000, keepRecentImages = 1)
        assertThat(r.strippedImages).isAtLeast(1)
        val users = r.messages.filter { it.role == "user" }
        // Oldest image turns should be stripped; latest kept if budget allows
        val withImg = users.filter { it.images.isNotEmpty() }
        assertThat(withImg.size).isAtMost(1)
        if (withImg.isNotEmpty()) {
            assertThat(withImg.last().content).contains("latest")
        }
        assertThat(r.charsAfter).isAtMost(20_000)
    }

    @Test
    fun partitionUnits_groupsAssistantToolsWithResults() {
        val msgs = listOf(
            user("u"),
            assistantTools(toolCall("a", "now")),
            tool("a", "now", "t"),
            assistant("ok"),
        )
        val units = HistoryWindow.partitionUnits(msgs)
        assertThat(units).hasSize(3)
        assertThat(units[0]).hasSize(1)
        assertThat(units[1]).hasSize(2)
        assertThat(units[1][0].toolCalls).hasSize(1)
        assertThat(units[1][1].role).isEqualTo("tool")
        assertThat(units[2][0].content).isEqualTo("ok")
    }

    @Test
    fun systemsAlwaysFirst_omissionIsSystem() {
        val msgs = buildList {
            add(sys("A"))
            add(sys("B"))
            repeat(20) { i ->
                add(user("u$i " + "x".repeat(500)))
                add(assistant("a$i " + "y".repeat(500)))
            }
        }
        val r = HistoryWindow.fit(msgs, maxChars = 5_000)
        assertThat(r.messages[0].role).isEqualTo("system")
        assertThat(r.messages[0].content).isEqualTo("A")
        val noteIdx = r.messages.indexOfFirst { it.content == HistoryWindow.OMISSION_NOTE }
        assertThat(noteIdx).isGreaterThan(0)
        assertThat(r.messages[noteIdx].role).isEqualTo("system")
        // No tool orphan: every tool preceded by assistant tools (none here)
        assertThat(r.messages.none { it.role == "tool" }).isTrue()
    }

    @Test
    fun estimateChars_countsImagesAndToolArgs() {
        val m = ChatMessageDto(
            role = "user",
            content = "hi",
            images = listOf(ImageAttachmentDto("image/png", "abcd")),
        )
        assertThat(HistoryWindow.estimateChars(m)).isGreaterThan(4)
        val t = assistantTools(toolCall("id1", "http_get", """{"url":"https://example.com"}"""))
        assertThat(HistoryWindow.estimateChars(t)).isGreaterThan(20)
    }

    @Test
    fun giantToolRound_neverOrphansTools() {
        val hugeArgs = "x".repeat(8_000)
        val hugeResult = "y".repeat(8_000)
        val msgs = listOf(
            sys("sys"),
            user("go"),
            assistantTools(toolCall("c1", "http_get", """{"q":"$hugeArgs"}""")),
            tool("c1", "http_get", hugeResult),
        )
        val r = HistoryWindow.fit(msgs, maxChars = 2_500)
        assertThat(HistoryWindow.toolIntegrityOk(r.messages)).isTrue()
        // Must still have assistant(tool_calls) if any tool role remains
        val hasTool = r.messages.any { it.role == "tool" }
        if (hasTool) {
            assertThat(r.messages.any { !it.toolCalls.isNullOrEmpty() }).isTrue()
        }
        assertThat(r.charsAfter).isAtMost(2_500)
    }

    @Test
    fun toolIntegrityOk_detectsOrphan() {
        val orphan = listOf(
            tool("c1", "now", "orphan"),
        )
        assertThat(HistoryWindow.toolIntegrityOk(orphan)).isFalse()
        val ok = listOf(
            assistantTools(toolCall("c1", "now")),
            tool("c1", "now", "t"),
        )
        assertThat(HistoryWindow.toolIntegrityOk(ok)).isTrue()
    }
}
