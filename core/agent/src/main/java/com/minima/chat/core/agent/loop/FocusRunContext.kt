package com.minima.chat.core.agent.loop

import com.minima.chat.core.agent.tools.ToolRuntime
import com.minima.chat.core.network.dto.ChatProviderConfig
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the active parent [AgentLoop] environment so [com.minima.chat.core.agent.tools.FocusTaskTool]
 * can spawn a bounded nested run with the same provider config and remaining budget.
 *
 * Nesting depth is capped at 1 (no focus_task → focus_task).
 */
@Singleton
class FocusRunContext @Inject constructor() {
    data class Frame(
        val config: ChatProviderConfig,
        val runStartedAtMs: Long,
        val wallClockMs: Long,
        val maxIterations: Int,
        val toolTimeoutMs: Long,
        val nestedTools: ToolRuntime,
        val iterationsUsed: AtomicInteger = AtomicInteger(0),
    ) {
        fun remainingWallMs(nowMs: Long = System.currentTimeMillis()): Long =
            (wallClockMs - (nowMs - runStartedAtMs)).coerceAtLeast(0L)

        fun remainingIterations(): Int =
            (maxIterations - iterationsUsed.get()).coerceAtLeast(0)
    }

    private val frame = AtomicReference<Frame?>(null)
    private val nestDepth = AtomicInteger(0)

    fun install(
        config: ChatProviderConfig,
        budget: AgentBudget,
        nestedTools: ToolRuntime,
        runStartedAtMs: Long = System.currentTimeMillis(),
    ): Frame? {
        val prev = frame.get()
        frame.set(
            Frame(
                config = config,
                runStartedAtMs = runStartedAtMs,
                wallClockMs = budget.wallClockMs,
                maxIterations = budget.maxIterations,
                toolTimeoutMs = budget.toolTimeoutMs,
                nestedTools = nestedTools,
            ),
        )
        return prev
    }

    fun restore(previous: Frame?) {
        frame.set(previous)
    }

    fun updateIterations(used: Int) {
        frame.get()?.iterationsUsed?.set(used)
    }

    fun current(): Frame? = frame.get()

    /** Returns false if a nested focus is already running (or depth would exceed 1). */
    fun tryEnterNested(): Boolean = nestDepth.compareAndSet(0, 1)

    fun leaveNested() {
        nestDepth.set(0)
    }

    fun nestDepth(): Int = nestDepth.get()
}
