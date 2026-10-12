package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.data.util.LlmHistoryMapper
import org.junit.Test

class LlmHistoryMapperTest {

    private fun user(id: String, content: String, at: Long) =
        Message(id, "s", MessageRole.User, content, at, kind = MessageKind.Text)

    private fun assistant(id: String, content: String, at: Long) =
        Message(id, "s", MessageRole.Assistant, content, at, kind = MessageKind.Text)

    private fun tool(
        id: String,
        callId: String,
        name: String,
        args: String,
        result: String,
        at: Long,
        status: ToolMessageStatus = ToolMessageStatus.Ok,
        agentStep: Int = 0,
    ) = Message(
        id = id,
        sessionId = "s",
        role = MessageRole.Tool,
        content = "🔧 $name",
        createdAt = at,
        kind = MessageKind.Tool,
        toolName = name,
        toolArgs = args,
        toolResult = result,
        toolStatus = status,
        toolCallId = callId,
        agentStep = agentStep,
    )

    @Test
    fun plainTurn_userAssistantOnly() {
        val msgs = listOf(
            user("1", "hi", 1),
            assistant("2", "hello", 2),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto).hasSize(2)
        assertThat(dto[0].role).isEqualTo("user")
        assertThat(dto[0].content).isEqualTo("hi")
        assertThat(dto[1].role).isEqualTo("assistant")
        assertThat(dto[1].content).isEqualTo("hello")
        assertThat(dto[1].toolCalls).isNull()
    }

    @Test
    fun toolRound_survivesIntoNextPayload_asAssistantToolCallsThenToolThenFinal() {
        // DB order: user → assistant(final) → tool card(s)
        val msgs = listOf(
            user("u1", "what time?", 1),
            assistant("a1", "It is Monday morning.", 2),
            tool("t1", "call_1", "now", "{}", "2026-10-12T03:00:00+08:00", 3),
            user("u2", "thanks, and the date?", 4),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto.map { it.role }).containsExactly(
            "user",
            "assistant",
            "tool",
            "assistant",
            "user",
        ).inOrder()

        val toolCallMsg = dto[1]
        assertThat(toolCallMsg.toolCalls).hasSize(1)
        assertThat(toolCallMsg.toolCalls!![0].id).isEqualTo("call_1")
        assertThat(toolCallMsg.toolCalls!![0].function.name).isEqualTo("now")
        assertThat(toolCallMsg.toolCalls!![0].function.arguments).isEqualTo("{}")
        assertThat(toolCallMsg.content).isNull()

        val toolResult = dto[2]
        assertThat(toolResult.role).isEqualTo("tool")
        assertThat(toolResult.toolCallId).isEqualTo("call_1")
        assertThat(toolResult.name).isEqualTo("now")
        assertThat(toolResult.content).isEqualTo("2026-10-12T03:00:00+08:00")

        assertThat(dto[3].content).isEqualTo("It is Monday morning.")
        assertThat(dto[4].content).isEqualTo("thanks, and the date?")
    }

    @Test
    fun parallelTools_groupedInOneAssistantToolCalls() {
        val msgs = listOf(
            user("u1", "check both", 1),
            assistant("a1", "Done.", 2),
            tool("t1", "c1", "now", "{}", "t", 3),
            tool("t2", "c2", "device_info", "{}", "app=com.minima.chat", 4),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto[1].toolCalls!!.map { it.id }).containsExactly("c1", "c2").inOrder()
        assertThat(dto.map { it.role }).containsExactly(
            "user", "assistant", "tool", "tool", "assistant",
        ).inOrder()
        assertThat(dto[2].toolCallId).isEqualTo("c1")
        assertThat(dto[3].toolCallId).isEqualTo("c2")
    }

    @Test
    fun runningTool_skipped_notReplayed() {
        val msgs = listOf(
            user("u1", "go", 1),
            assistant("a1", "", 2),
            tool("t1", "c1", "now", "{}", "", 3, status = ToolMessageStatus.Running),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        // empty assistant + running tool → nothing usable
        assertThat(dto).hasSize(1)
        assertThat(dto[0].role).isEqualTo("user")
    }

    @Test
    fun excludeMessageIds_dropsPlaceholderAssistant() {
        val msgs = listOf(
            user("u1", "hi", 1),
            assistant("a_old", "yo", 2),
            user("u2", "again", 3),
            assistant("a_new", "", 4),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs, excludeMessageIds = setOf("a_new"))
        assertThat(dto.map { it.content }).containsExactly("hi", "yo", "again").inOrder()
    }

    @Test
    fun orphanToolCluster_synthesizesAssistantToolCalls() {
        val msgs = listOf(
            tool("t1", "c1", "now", "{\"x\":1}", "ok", 1),
            user("u1", "next", 2),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto[0].role).isEqualTo("assistant")
        assertThat(dto[0].toolCalls!![0].function.arguments).isEqualTo("{\"x\":1}")
        assertThat(dto[1].role).isEqualTo("tool")
        assertThat(dto[2].role).isEqualTo("user")
    }

    @Test
    fun blankAssistantWithoutTools_omitted() {
        val msgs = listOf(
            user("u1", "hi", 1),
            assistant("a1", "   ", 2),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto).hasSize(1)
        assertThat(dto[0].role).isEqualTo("user")
    }

    @Test
    fun toolResult_truncatedToMaxPayload() {
        val huge = "x".repeat(LlmHistoryMapper.MAX_TOOL_PAYLOAD_CHARS + 500)
        val msgs = listOf(
            user("u1", "read", 1),
            assistant("a1", "here", 2),
            tool("t1", "c1", "read_file", "{}", huge, 3),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        val toolMsg = dto.first { it.role == "tool" }
        assertThat(toolMsg.content!!.length).isAtMost(LlmHistoryMapper.MAX_TOOL_PAYLOAD_CHARS)
        assertThat(toolMsg.content!!).contains("truncated tool result")
    }

    @Test
    fun multiTurn_twoToolRounds_bothSurvive() {
        val msgs = listOf(
            user("u1", "t1", 1),
            assistant("a1", "first done", 2),
            tool("t1", "c1", "now", "{}", "noon", 3),
            user("u2", "t2", 4),
            assistant("a2", "second done", 5),
            tool("t2", "c2", "device_info", "{}", "sdk=34", 6),
            user("u3", "continue", 7),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        val toolRoles = dto.filter { it.role == "tool" }
        assertThat(toolRoles).hasSize(2)
        assertThat(toolRoles[0].content).isEqualTo("noon")
        assertThat(toolRoles[1].content).isEqualTo("sdk=34")
        // Last message is the new user turn ready for the model
        assertThat(dto.last().role).isEqualTo("user")
        assertThat(dto.last().content).isEqualTo("continue")
        // Every tool result is preceded by an assistant with matching tool_calls
        var seenToolCalls = 0
        for (m in dto) {
            if (m.toolCalls != null) seenToolCalls++
        }
        assertThat(seenToolCalls).isEqualTo(2)
    }

    @Test
    fun sameTurn_twoIterations_emitsTwoAssistantToolCallRounds() {
        // Same user turn: step1 now → step2 device_info → final text
        val msgs = listOf(
            user("u1", "time then device", 1),
            assistant("a1", "Here is what I found.", 2),
            tool("t1", "c1", "now", "{}", "2026-10-12T03:00+08:00", 3, agentStep = 1),
            tool("t2", "c2", "device_info", "{}", "sdk=34", 4, agentStep = 2),
            user("u2", "ok continue", 5),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto.map { it.role }).containsExactly(
            "user",
            "assistant", // tool_calls step 1
            "tool",
            "assistant", // tool_calls step 2
            "tool",
            "assistant", // final text
            "user",
        ).inOrder()

        assertThat(dto[1].toolCalls!!.map { it.id }).containsExactly("c1")
        assertThat(dto[2].toolCallId).isEqualTo("c1")
        assertThat(dto[2].content).isEqualTo("2026-10-12T03:00+08:00")

        assertThat(dto[3].toolCalls!!.map { it.id }).containsExactly("c2")
        assertThat(dto[4].toolCallId).isEqualTo("c2")
        assertThat(dto[4].content).isEqualTo("sdk=34")

        assertThat(dto[5].content).isEqualTo("Here is what I found.")
        assertThat(dto[5].toolCalls).isNull()
    }

    @Test
    fun sameTurn_twoIterations_parallelThenSingle() {
        // step1: two parallel tools; step2: one tool; then final
        val msgs = listOf(
            user("u1", "gather", 1),
            assistant("a1", "Summary ready.", 2),
            tool("t1", "c1", "now", "{}", "noon", 3, agentStep = 1),
            tool("t2", "c2", "device_info", "{}", "Pixel", 4, agentStep = 1),
            tool("t3", "c3", "list_dir", "{\"path\":\".\"}", "a.md\nb.md", 5, agentStep = 2),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto.map { it.role }).containsExactly(
            "user",
            "assistant", "tool", "tool",
            "assistant", "tool",
            "assistant",
        ).inOrder()
        assertThat(dto[1].toolCalls!!.map { it.id }).containsExactly("c1", "c2").inOrder()
        assertThat(dto[4].toolCalls!!.map { it.id }).containsExactly("c3")
        assertThat(dto[6].content).isEqualTo("Summary ready.")
    }

    @Test
    fun legacyZeroAgentStep_stillFlattensToOneGroup() {
        val msgs = listOf(
            user("u1", "go", 1),
            assistant("a1", "done", 2),
            tool("t1", "c1", "now", "{}", "t", 3, agentStep = 0),
            tool("t2", "c2", "device_info", "{}", "d", 4, agentStep = 0),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto[1].toolCalls!!.map { it.id }).containsExactly("c1", "c2").inOrder()
        assertThat(dto.map { it.role }).containsExactly(
            "user", "assistant", "tool", "tool", "assistant",
        ).inOrder()
    }

    @Test
    fun groupToolsByStep_splitsOnIterationChange() {
        val tools = listOf(
            tool("t1", "c1", "now", "{}", "a", 1, agentStep = 1),
            tool("t2", "c2", "now", "{}", "b", 2, agentStep = 1),
            tool("t3", "c3", "now", "{}", "c", 3, agentStep = 2),
        )
        val groups = LlmHistoryMapper.groupToolsByStep(tools)
        assertThat(groups).hasSize(2)
        assertThat(groups[0].map { it.toolCallId }).containsExactly("c1", "c2").inOrder()
        assertThat(groups[1].map { it.toolCallId }).containsExactly("c3")
    }

    @Test
    fun emptyToolResult_replacedWithPlaceholder() {
        val msgs = listOf(
            user("u1", "run", 1),
            assistant("a1", "ok", 2),
            tool("t1", "c1", "now", "{}", "", 3),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        val toolMsg = dto.first { it.role == "tool" }
        assertThat(toolMsg.content).isEqualTo("(empty tool result)")
    }

    @Test
    fun cancelledTool_errorStatus_isReplayed() {
        val msgs = listOf(
            user("u1", "go", 1),
            assistant("a1", "_(Stopped)_", 2),
            tool("t1", "c1", "now", "{}", "(cancelled)", 3, status = ToolMessageStatus.Error),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto.map { it.role }).containsExactly(
            "user", "assistant", "tool", "assistant",
        ).inOrder()
        assertThat(dto[2].content).isEqualTo("(cancelled)")
        assertThat(dto[3].content).isEqualTo("_(Stopped)_")
    }

    @Test
    fun regenerateScenario_priorMultiStepSurvivesAfterDeletingLastTurn() {
        // Simulate deleteFrom(last user): remaining history must keep agentStep groups.
        val remaining = listOf(
            user("u1", "time then device", 1),
            assistant("a1", "Found it.", 2),
            tool("t1", "c1", "now", "{}", "noon", 3, agentStep = 1),
            tool("t2", "c2", "device_info", "{}", "sdk=34", 4, agentStep = 2),
            // u2+a2+tools deleted by regenerate — not present
        )
        val dto = LlmHistoryMapper.toChatMessages(remaining)
        assertThat(dto.map { it.role }).containsExactly(
            "user",
            "assistant", "tool",
            "assistant", "tool",
            "assistant",
        ).inOrder()
        assertThat(dto[1].toolCalls!!.single().id).isEqualTo("c1")
        assertThat(dto[3].toolCalls!!.single().id).isEqualTo("c2")
        assertThat(dto.last().content).isEqualTo("Found it.")
    }

    @Test
    fun interleavedPreToolText_thenTools_thenFinalAssistant() {
        // UI chronological: A(pre) → tool → A(final)
        val msgs = listOf(
            user("u1", "search then answer", 1),
            assistant("a_pre", "Let me check.", 2),
            tool("t1", "c1", "now", "{}", "2026-10-12", 3),
            assistant("a_post", "It is Monday.", 4),
        )
        val dto = LlmHistoryMapper.toChatMessages(msgs)
        assertThat(dto.map { it.role }).containsExactly(
            "user", "assistant", "tool", "assistant",
        ).inOrder()
        assertThat(dto[1].toolCalls).hasSize(1)
        assertThat(dto[1].content).isEqualTo("Let me check.")
        assertThat(dto[3].content).isEqualTo("It is Monday.")
        assertThat(dto[3].toolCalls).isNull()
    }

}
