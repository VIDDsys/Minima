package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.db.MessageEntity
import com.minima.chat.core.data.util.LlmHistoryMapper
import com.minima.chat.core.data.util.MessageEntityMapper
import org.junit.Test

/**
 * End-to-end JVM path: sample Room entities → domain → OpenAI chat messages.
 * Mirrors what MessageRepository.listForLlm does without Android/Room runtime.
 */
class HistoryRoundtripTest {

    private fun text(
        id: String,
        role: String,
        content: String,
        at: Long,
        sessionId: String = "sess-e2e",
    ) = MessageEntity(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        createdAt = at,
        kind = "text",
    )

    private fun tool(
        id: String,
        callId: String,
        name: String,
        args: String,
        result: String,
        at: Long,
        status: String = "ok",
        agentStep: Int = 1,
        sessionId: String = "sess-e2e",
    ) = MessageEntity(
        id = id,
        sessionId = sessionId,
        role = "Tool",
        content = "🔧 $name",
        createdAt = at,
        kind = "tool",
        toolName = name,
        toolArgs = args,
        toolResult = result,
        toolStatus = status,
        toolCallId = callId,
        agentStep = agentStep,
    )

    @Test
    fun entityToModelToLlm_userToolAssistant_roundtrip() {
        // DB order as ChatViewModel persists: user → assistant(final) → tool card(s)
        val entities = listOf(
            text("u1", "User", "What time is it?", 1),
            text("a1", "Assistant", "It is morning in Shanghai.", 2),
            tool("t1", "call_now", "now", "{}", "2026-10-12T03:30:00+08:00", 3, agentStep = 1),
            text("u2", "User", "Thanks — continue.", 4),
        )

        val models = entities.map(MessageEntityMapper::toModel)
        // Round-trip entity ↔ model preserves tool fields
        val back = models.map(MessageEntityMapper::toEntity)
        assertThat(back.map { it.toolCallId }).isEqualTo(entities.map { it.toolCallId })
        assertThat(back.map { it.agentStep }).isEqualTo(entities.map { it.agentStep })
        assertThat(back[2].toolStatus).isEqualTo("ok")

        val dto = LlmHistoryMapper.toChatMessages(models)
        assertThat(dto.map { it.role }).containsExactly(
            "user",
            "assistant", // tool_calls
            "tool",
            "assistant", // final text
            "user",
        ).inOrder()

        assertThat(dto[0].content).isEqualTo("What time is it?")
        assertThat(dto[1].toolCalls!!.single().id).isEqualTo("call_now")
        assertThat(dto[1].toolCalls!!.single().function.name).isEqualTo("now")
        assertThat(dto[1].content).isNull()
        assertThat(dto[2].role).isEqualTo("tool")
        assertThat(dto[2].toolCallId).isEqualTo("call_now")
        assertThat(dto[2].content).isEqualTo("2026-10-12T03:30:00+08:00")
        assertThat(dto[3].content).isEqualTo("It is morning in Shanghai.")
        assertThat(dto[4].content).isEqualTo("Thanks — continue.")
    }

    @Test
    fun multiStepFromEntities_twoAgentStepsSurvive() {
        val entities = listOf(
            text("u1", "User", "time then device", 1),
            text("a1", "Assistant", "Here you go.", 2),
            tool("t1", "c1", "now", "{}", "noon", 3, agentStep = 1),
            tool("t2", "c2", "device_info", "{}", "sdk=34", 4, agentStep = 2),
        )
        val dto = LlmHistoryMapper.toChatMessages(entities.map(MessageEntityMapper::toModel))
        assertThat(dto.map { it.role }).containsExactly(
            "user",
            "assistant", "tool",
            "assistant", "tool",
            "assistant",
        ).inOrder()
        assertThat(dto[1].toolCalls!!.single().id).isEqualTo("c1")
        assertThat(dto[3].toolCalls!!.single().id).isEqualTo("c2")
        assertThat(dto.last().content).isEqualTo("Here you go.")
    }

    @Test
    fun runningToolEntity_skippedInReplay() {
        val entities = listOf(
            text("u1", "User", "go", 1),
            text("a1", "Assistant", "", 2),
            tool("t1", "c1", "now", "{}", "", 3, status = "running", agentStep = 1),
        )
        val dto = LlmHistoryMapper.toChatMessages(entities.map(MessageEntityMapper::toModel))
        assertThat(dto).hasSize(1)
        assertThat(dto[0].role).isEqualTo("user")
    }

    @Test
    fun entityMapper_unknownRoleDefaultsAssistant() {
        val e = text("x", "NotARole", "weird", 1)
        val m = MessageEntityMapper.toModel(e)
        assertThat(m.role.name).isEqualTo("Assistant")
    }
}
