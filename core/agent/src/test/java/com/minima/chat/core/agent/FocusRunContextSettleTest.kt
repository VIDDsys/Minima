package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.loop.AgentBudget
import com.minima.chat.core.agent.loop.FocusRunContext
import com.minima.chat.core.agent.tools.FocusTaskLogic
import com.minima.chat.core.agent.tools.ToolResult
import com.minima.chat.core.agent.tools.ToolRuntime
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.ToolDefinitionDto
import com.minima.chat.core.network.dto.ToolFunctionDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Settle probes for focus_task nesting / budget / no-recursion (v2.32.0 — no version bump).
 */
class FocusRunContextSettleTest {
    private val config = ChatProviderConfig(
        baseUrl = "https://example.com/v1",
        apiKey = "k",
        model = "m",
    )

    private fun emptyTools(): ToolRuntime = object : ToolRuntime {
        override fun definitions() = emptyList<ToolDefinitionDto>()
        override suspend fun dispatch(name: String, argumentsJson: String) =
            ToolResult(false, "none")
    }

    @Test
    fun nestDepth_casExclusive_andLeaveResets() {
        val ctx = FocusRunContext()
        assertThat(ctx.nestDepth()).isEqualTo(0)
        assertThat(ctx.tryEnterNested()).isTrue()
        assertThat(ctx.nestDepth()).isEqualTo(1)
        assertThat(ctx.tryEnterNested()).isFalse()
        assertThat(ctx.nestDepth()).isEqualTo(1)
        ctx.leaveNested()
        assertThat(ctx.nestDepth()).isEqualTo(0)
        assertThat(ctx.tryEnterNested()).isTrue()
        ctx.leaveNested()
    }

    @Test
    fun install_restore_preservesParentIterationCounter() {
        val ctx = FocusRunContext()
        val nested = FocusTaskLogic.withoutFocusTask(emptyTools())
        val parentBudget = AgentBudget(maxIterations = 8, wallClockMs = 120_000, toolTimeoutMs = 60_000)
        ctx.install(config, parentBudget, nested, runStartedAtMs = 1_000L)
        ctx.updateIterations(3)
        assertThat(ctx.current()!!.remainingIterations()).isEqualTo(5)

        val previous = ctx.install(
            config,
            AgentBudget(maxIterations = 4, wallClockMs = 30_000, toolTimeoutMs = 10_000),
            nested,
            runStartedAtMs = 2_000L,
        )
        ctx.updateIterations(2) // nested steps must not clobber parent AtomicInteger
        assertThat(ctx.current()!!.remainingIterations()).isEqualTo(2)

        ctx.restore(previous)
        assertThat(ctx.current()!!.remainingIterations()).isEqualTo(5)
        assertThat(ctx.current()!!.maxIterations).isEqualTo(8)
        assertThat(ctx.current()!!.wallClockMs).isEqualTo(120_000)
    }

    @Test
    fun remainingWallMs_clampsAtZero() {
        val ctx = FocusRunContext()
        ctx.install(
            config,
            AgentBudget(maxIterations = 4, wallClockMs = 10_000, toolTimeoutMs = 5_000),
            FocusTaskLogic.withoutFocusTask(emptyTools()),
            runStartedAtMs = 0L,
        )
        assertThat(ctx.current()!!.remainingWallMs(nowMs = 15_000L)).isEqualTo(0L)
        assertThat(ctx.current()!!.remainingWallMs(nowMs = 3_000L)).isEqualTo(7_000L)
    }

    @Test
    fun withoutFocusTask_blocksDispatchEvenIfListedElsewhere() = runBlocking {
        val calls = AtomicInteger(0)
        val inner = object : ToolRuntime {
            override fun definitions() = listOf(
                ToolDefinitionDto(
                    function = ToolFunctionDto(FocusTaskLogic.TOOL_NAME, "Focus", buildJsonObject {}),
                ),
            )
            override suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
                calls.incrementAndGet()
                return ToolResult(true, "leaked")
            }
        }
        val filtered = FocusTaskLogic.withoutFocusTask(inner)
        assertThat(filtered.definitions()).isEmpty()
        val r = filtered.dispatch(FocusTaskLogic.TOOL_NAME, """{"task":"x"}""")
        assertThat(r.ok).isFalse()
        assertThat(r.output).contains("not allowed")
        assertThat(calls.get()).isEqualTo(0)
    }

    @Test
    fun nestedBudget_oneIterLeft_allowsSingleStepNest() {
        val b = FocusTaskLogic.nestedBudget(
            remainingWallMs = 60_000,
            remainingIterations = 1,
            parentToolTimeoutMs = 60_000,
        )!!
        assertThat(b.maxIterations).isEqualTo(1)
        assertThat(b.wallClockMs).isEqualTo(FocusTaskLogic.MAX_NESTED_WALL_MS)
    }

    @Test
    fun nestedBudget_toolTimeoutNeverExceedsEffectiveWall() {
        val b = FocusTaskLogic.nestedBudget(
            remainingWallMs = 12_000,
            remainingIterations = 3,
            parentToolTimeoutMs = 60_000,
        )!!
        assertThat(b.wallClockMs).isEqualTo(12_000)
        assertThat(b.toolTimeoutMs).isAtMost(b.wallClockMs)
        assertThat(b.toolTimeoutMs).isAtMost(FocusTaskLogic.MAX_NESTED_TOOL_TIMEOUT_MS)
    }
}
