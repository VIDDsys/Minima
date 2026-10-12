package com.minima.chat.core.agent.tools

import com.minima.chat.core.agent.loop.AgentLoop
import com.minima.chat.core.agent.loop.FocusRunContext
import com.minima.chat.core.network.dto.ChatMessageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Bounded nested AgentLoop ("focus task" / sub-agent).
 * Uses parent provider config + remaining budget; tools exclude focus_task (no recursion).
 * Optional max_steps / max_seconds further tighten the nested envelope (never expand).
 */
@Singleton
class FocusTaskTool @Inject constructor(
    private val agentLoop: Provider<AgentLoop>,
    private val focusRunContext: FocusRunContext,
) : AgentTool {
    override val spec = ToolSpec(
        name = FocusTaskLogic.TOOL_NAME,
        description = "Run a short focused sub-agent on a single task with a tight budget. " +
            "Use for multi-step research or file work you want isolated. Returns a concise result " +
            "plus a nested tools transcript. Optional max_steps (1–4) and max_seconds (5–60) " +
            "tighten the budget further.",
        parameters = JsonSchema.obj(
            "task" to JsonSchema.string("Clear, self-contained task for the sub-agent"),
            "max_steps" to JsonSchema.integer("Optional max nested steps (1–4); omit for default cap"),
            "max_seconds" to JsonSchema.integer("Optional max nested wall seconds (5–60); omit for default cap"),
            required = listOf("task"),
        ),
        requiresGrant = Grant.None,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val task = FocusTaskLogic.sanitizeTask(parseString(argumentsJson, "task"))
            ?: return ToolResult(false, "task required")

        val requestedSteps = FocusTaskLogic.parseRequestedMaxSteps(
            parseString(argumentsJson, "max_steps") ?: parseOptionalNumberString(argumentsJson, "max_steps"),
        )
        val requestedSeconds = FocusTaskLogic.parseRequestedMaxSeconds(
            parseString(argumentsJson, "max_seconds") ?: parseOptionalNumberString(argumentsJson, "max_seconds"),
        )

        val frame = focusRunContext.current()
            ?: return ToolResult(false, "focus_task is only available during an agent run")

        if (!focusRunContext.tryEnterNested()) {
            return ToolResult(false, "Nested or concurrent focus_task is not allowed")
        }
        return try {
            val budget = FocusTaskLogic.nestedBudget(
                remainingWallMs = frame.remainingWallMs(),
                remainingIterations = frame.remainingIterations(),
                parentToolTimeoutMs = frame.toolTimeoutMs,
                requestedMaxSteps = requestedSteps,
                requestedMaxSeconds = requestedSeconds,
            ) ?: return ToolResult(
                false,
                "Not enough parent budget for focus_task " +
                    "(need ≥${FocusTaskLogic.MIN_NESTED_WALL_MS / 1000}s wall and ≥1 step left)",
            )

            val history = listOf(
                ChatMessageDto(role = "system", content = FocusTaskLogic.focusSystemPrompt()),
                ChatMessageDto(role = "user", content = task),
            )
            val events = try {
                agentLoop.get().run(
                    config = frame.config,
                    history = history,
                    budget = budget,
                    preferStreaming = false,
                    toolsOverride = frame.nestedTools,
                ).toList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ToolResult(false, e.message ?: "focus_task failed")
            }

            val summary = FocusTaskLogic.summarize(events)
            ToolResult(summary.ok, summary.text)
        } finally {
            focusRunContext.leaveNested()
        }
    }
}
