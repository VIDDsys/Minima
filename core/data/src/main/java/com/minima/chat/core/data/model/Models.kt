package com.minima.chat.core.data.model

import com.minima.chat.core.network.api.SamplingParams

data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

enum class MessageRole { User, Assistant, System, Tool }

enum class MessageKind { Text, Tool }

enum class ToolMessageStatus { Running, Ok, Error }

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val isStreaming: Boolean = false,
    val kind: MessageKind = MessageKind.Text,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolStatus: ToolMessageStatus? = null,
    val toolCallId: String? = null,
    /**
     * 1-based AgentLoop iteration that requested this tool (same user turn).
     * 0 = unknown / legacy row — mapper treats consecutive 0s as one group.
     */
    val agentStep: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val attachmentPath: String? = null,
    val attachmentMime: String? = null,
)

enum class AppearanceSetting { System, Light, Dark }

data class AppSettings(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val modelId: String = "deepseek-flash",
    val apiKey: String = "",
    val appearance: AppearanceSetting = AppearanceSetting.System,
    /** When true, chat uses AgentLoop with local tools. */
    val agentEnabled: Boolean = true,
    /** Deprecated: sandbox gating removed; always false (max public-file access). */
    val sandboxOnly: Boolean = false,
    /** Persisted SAF tree URI string for skills / public files. */
    val skillsTreeUri: String = "",
    /** JSON list of remote MCP servers. */
    val mcpConfigJson: String = """{"servers":[]}""",
    /** Last MCP probe results (JSON array of per-server health). Empty = never probed. */
    val mcpLastHealthJson: String = "[]",
    /** WorkManager daily sandbox note. */
    val scheduledNoteEnabled: Boolean = false,
    /** Max tool-loop iterations per agent turn (clamped 1..10000; UI step limit removed). */
    val maxAgentSteps: Int = 10_000,
    /**
     * Overall agent-turn wall-clock budget in seconds (clamped 30..900).
     * Default 180 matches historical AgentBudget.
     */
    val agentWallClockSec: Int = 600,
    /**
     * Per-tool-call timeout in seconds (clamped 5..300, also ≤ wall clock).
     * Default 60 matches historical AgentBudget.
     */
    val agentToolTimeoutSec: Int = 60,
    /** First-run onboarding dismissed / completed. */
    val onboardingCompleted: Boolean = false,
    /**
     * Global system / AGENT prompt. Blank = built-in Minima default.
     * Merged with on-disk AGENT.md from skills folder when present.
     */
    val systemPrompt: String = "",
    /** Show cheap ~chars/4 token estimate in chat chrome. */
    val showUsageEstimate: Boolean = false,
    /**
     * Optional OkHttp certificate pins. Blank = off (default).
     * Lines: `host|sha256/BASE64`
     */
    val certPins: String = "",
    /**
     * Sampling temperature for chat/completions. Null = omit (provider default).
     * Clamped 0.0..2.0 when set.
     */
    val temperature: Double? = null,
    /**
     * max_tokens for chat/completions. Null / <=0 = omit (provider default).
     * Clamped 1..200000 when set.
     */
    val maxTokens: Int? = null,
    /**
     * top_p (nucleus) for chat/completions. Null = omit (provider default).
     * Clamped 0.0..1.0 when set.
     */
    val topP: Double? = null,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    val clampedMaxAgentSteps: Int get() = maxAgentSteps.coerceIn(1, 10_000)

    val clampedWallClockSec: Int
        get() = AgentTimingParams.clampWallClockSec(agentWallClockSec)

    val clampedToolTimeoutSec: Int
        get() = AgentTimingParams.clampToolTimeoutSec(
            agentToolTimeoutSec,
            wallClockSec = clampedWallClockSec,
        )

    val clampedTemperature: Double? get() = SamplingParams.clampTemperature(temperature)

    val clampedMaxTokens: Int? get() = SamplingParams.clampMaxTokens(maxTokens)

    val clampedTopP: Double? get() = SamplingParams.clampTopP(topP)

    /** Never leak the API key via logs / crash toString. */
    override fun toString(): String =
        "AppSettings(baseUrl=$baseUrl, modelId=$modelId, apiKey=${if (apiKey.isBlank()) "<empty>" else "<redacted>"}, " +
            "appearance=$appearance, agentEnabled=$agentEnabled, sandboxOnly=$sandboxOnly, " +
            "skillsTreeUri=${skillsTreeUri.take(24)}, mcpConfigJson=<${mcpConfigJson.length} chars>, " +
            "mcpLastHealthJson=<${mcpLastHealthJson.length} chars>, " +
            "scheduledNoteEnabled=$scheduledNoteEnabled, maxAgentSteps=$maxAgentSteps, " +
            "agentWallClockSec=$agentWallClockSec, agentToolTimeoutSec=$agentToolTimeoutSec, " +
            "onboardingCompleted=$onboardingCompleted, systemPrompt=<${systemPrompt.length} chars>, " +
            "showUsageEstimate=$showUsageEstimate, certPins=<${certPins.length} chars>, " +
            "temperature=$temperature, maxTokens=$maxTokens, topP=$topP)"
}

/**
 * Pure clamps for agent wall-clock / per-tool timeouts (seconds).
 * Keeps Settings + AgentLoop budgets consistent without circular modules.
 */
object AgentTimingParams {
    const val WALL_CLOCK_SEC_DEFAULT = 600
    const val WALL_CLOCK_SEC_MIN = 30
    const val WALL_CLOCK_SEC_MAX = 900
    const val TOOL_TIMEOUT_SEC_DEFAULT = 60
    const val TOOL_TIMEOUT_SEC_MIN = 5
    const val TOOL_TIMEOUT_SEC_MAX = 300

    fun clampWallClockSec(v: Int): Int = v.coerceIn(WALL_CLOCK_SEC_MIN, WALL_CLOCK_SEC_MAX)

    /**
     * Clamp tool timeout into [TOOL_TIMEOUT_SEC_MIN, TOOL_TIMEOUT_SEC_MAX],
     * then never above [wallClockSec] (so a single tool cannot outlive the turn).
     */
    fun clampToolTimeoutSec(v: Int, wallClockSec: Int = WALL_CLOCK_SEC_DEFAULT): Int {
        val wall = clampWallClockSec(wallClockSec)
        val capped = v.coerceIn(TOOL_TIMEOUT_SEC_MIN, TOOL_TIMEOUT_SEC_MAX)
        return capped.coerceAtMost(wall)
    }

    fun parseSec(raw: String?, default: Int): Int? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return t.toIntOrNull()
    }
}
