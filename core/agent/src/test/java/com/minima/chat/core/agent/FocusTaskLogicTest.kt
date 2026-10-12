package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.loop.AgentEvent
import com.minima.chat.core.agent.tools.FocusTaskLogic
import com.minima.chat.core.agent.tools.ToolResult
import com.minima.chat.core.agent.tools.ToolRuntime
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolCallFunctionDto
import com.minima.chat.core.network.dto.ToolDefinitionDto
import com.minima.chat.core.network.dto.ToolFunctionDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

class FocusTaskLogicTest {
    @Test
    fun sanitizeTask_trimsAndRejectsBlank() {
        assertThat(FocusTaskLogic.sanitizeTask("  hello  ")).isEqualTo("hello")
        assertThat(FocusTaskLogic.sanitizeTask("   ")).isNull()
        assertThat(FocusTaskLogic.sanitizeTask(null)).isNull()
    }

    @Test
    fun sanitizeTask_capsLength() {
        val long = "x".repeat(FocusTaskLogic.MAX_TASK_CHARS + 50)
        assertThat(FocusTaskLogic.sanitizeTask(long)!!.length)
            .isEqualTo(FocusTaskLogic.MAX_TASK_CHARS)
    }

    @Test
    fun nestedBudget_nullWhenTooLittleWall() {
        assertThat(
            FocusTaskLogic.nestedBudget(
                remainingWallMs = 2_000,
                remainingIterations = 5,
                parentToolTimeoutMs = 60_000,
            ),
        ).isNull()
    }

    @Test
    fun nestedBudget_nullWhenNoIterations() {
        assertThat(
            FocusTaskLogic.nestedBudget(
                remainingWallMs = 60_000,
                remainingIterations = 0,
                parentToolTimeoutMs = 60_000,
            ),
        ).isNull()
    }

    @Test
    fun nestedBudget_clampsToCaps() {
        val b = FocusTaskLogic.nestedBudget(
            remainingWallMs = 180_000,
            remainingIterations = 12,
            parentToolTimeoutMs = 60_000,
        )!!
        assertThat(b.maxIterations).isEqualTo(FocusTaskLogic.MAX_NESTED_ITERS)
        assertThat(b.wallClockMs).isEqualTo(FocusTaskLogic.MAX_NESTED_WALL_MS)
        assertThat(b.toolTimeoutMs).isEqualTo(FocusTaskLogic.MAX_NESTED_TOOL_TIMEOUT_MS)
    }

    @Test
    fun nestedBudget_respectsParentToolTimeoutEnvelope() {
        val b = FocusTaskLogic.nestedBudget(
            remainingWallMs = 180_000,
            remainingIterations = 8,
            parentToolTimeoutMs = 20_000,
        )!!
        assertThat(b.wallClockMs).isEqualTo(20_000)
        assertThat(b.toolTimeoutMs).isAtMost(20_000)
    }

    @Test
    fun nestedBudget_honorsRequestedTightenOnly() {
        val b = FocusTaskLogic.nestedBudget(
            remainingWallMs = 180_000,
            remainingIterations = 12,
            parentToolTimeoutMs = 60_000,
            requestedMaxSteps = 2,
            requestedMaxSeconds = 15,
        )!!
        assertThat(b.maxIterations).isEqualTo(2)
        assertThat(b.wallClockMs).isEqualTo(15_000)
    }

    @Test
    fun nestedBudget_requestedCannotExpandPastParent() {
        val b = FocusTaskLogic.nestedBudget(
            remainingWallMs = 10_000,
            remainingIterations = 2,
            parentToolTimeoutMs = 8_000,
            requestedMaxSteps = 4,
            requestedMaxSeconds = 60,
        )!!
        assertThat(b.maxIterations).isEqualTo(2)
        assertThat(b.wallClockMs).isEqualTo(8_000)
    }

    @Test
    fun parseRequested_clampsAndRejectsJunk() {
        assertThat(FocusTaskLogic.parseRequestedMaxSteps("3")).isEqualTo(3)
        assertThat(FocusTaskLogic.parseRequestedMaxSteps("99")).isEqualTo(FocusTaskLogic.MAX_NESTED_ITERS)
        assertThat(FocusTaskLogic.parseRequestedMaxSteps("0")).isNull()
        assertThat(FocusTaskLogic.parseRequestedMaxSteps("nope")).isNull()
        assertThat(FocusTaskLogic.parseRequestedMaxSeconds("12")).isEqualTo(12)
        assertThat(FocusTaskLogic.parseRequestedMaxSeconds("999")).isEqualTo(60)
        assertThat(FocusTaskLogic.parseRequestedMaxSeconds("2")).isEqualTo(5)
        assertThat(FocusTaskLogic.parseRequestedMaxSeconds(null)).isNull()
    }

    @Test
    fun withoutFocusTask_filtersDefinitionsAndDispatch() = runBlocking {
        val inner = object : ToolRuntime {
            override fun definitions() = listOf(
                ToolDefinitionDto(function = ToolFunctionDto("now", "t", buildJsonObject {})),
                ToolDefinitionDto(function = ToolFunctionDto(FocusTaskLogic.TOOL_NAME, "t", buildJsonObject {})),
            )
            override suspend fun dispatch(name: String, argumentsJson: String) =
                ToolResult(true, name)
        }
        val filtered = FocusTaskLogic.withoutFocusTask(inner)
        assertThat(filtered.definitions().map { it.function.name }).containsExactly("now")
        assertThat(filtered.dispatch(FocusTaskLogic.TOOL_NAME, "{}").ok).isFalse()
        assertThat(filtered.dispatch("now", "{}").output).isEqualTo("now")
    }

    @Test
    fun summarize_prefersCompletedAndCountsTools() {
        val call = ToolCallDto(
            id = "c1",
            function = ToolCallFunctionDto(name = "now", arguments = "{}"),
        )
        val events = listOf(
            AgentEvent.Step(1, 4),
            AgentEvent.ToolStarted(call),
            AgentEvent.ToolFinished(call, "ok", true),
            AgentEvent.AssistantDelta("Hel"),
            AgentEvent.AssistantDelta("lo"),
            AgentEvent.AssistantCompleted("Hello final"),
            AgentEvent.Done(),
        )
        val s = FocusTaskLogic.summarize(events)
        assertThat(s.ok).isTrue()
        assertThat(s.steps).isEqualTo(1)
        assertThat(s.toolsUsed).isEqualTo(1)
        assertThat(s.text).contains("Hello final")
        assertThat(s.text).contains("tools=1")
        assertThat(s.text).contains("tools:")
        assertThat(s.text).contains("- now ok:")
        assertThat(s.toolNames).containsExactly("now")
    }

    @Test
    fun summarize_includesFailedToolsAndRedactsSecrets() {
        val call = ToolCallDto(
            id = "c2",
            function = ToolCallFunctionDto(name = "http_get", arguments = "{}"),
        )
        val s = FocusTaskLogic.summarize(
            listOf(
                AgentEvent.ToolFinished(call, "Authorization: Bearer sk-secret-abc123xyz", false),
                AgentEvent.AssistantCompleted("done"),
                AgentEvent.Done(),
            ),
        )
        assertThat(s.ok).isTrue()
        assertThat(s.text).contains("- http_get err:")
        assertThat(s.text.lowercase()).doesNotContain("sk-secret-abc123xyz")
    }

    @Test
    fun summarize_errorWithoutBody_notOk() {
        val s = FocusTaskLogic.summarize(
            listOf(AgentEvent.Error("boom"), AgentEvent.Done()),
        )
        assertThat(s.ok).isFalse()
        assertThat(s.text).contains("boom")
    }
}
