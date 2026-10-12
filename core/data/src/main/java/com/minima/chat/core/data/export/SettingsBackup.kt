package com.minima.chat.core.data.export

import com.minima.chat.core.data.model.AppSettings
import com.minima.chat.core.data.model.AgentTimingParams
import com.minima.chat.core.data.model.AppearanceSetting
import com.minima.chat.core.network.api.SamplingParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SettingsBackupBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val includeApiKey: Boolean = false,
    val baseUrl: String = "",
    val modelId: String = "",
    val appearance: String = AppearanceSetting.System.name,
    val agentEnabled: Boolean = true,
    val sandboxOnly: Boolean = false,
    val skillsTreeUri: String = "",
    val mcpConfigJson: String = """{"servers":[]}""",
    val scheduledNoteEnabled: Boolean = false,
    val maxAgentSteps: Int = 10_000,
    val agentWallClockSec: Int = AgentTimingParams.WALL_CLOCK_SEC_DEFAULT,
    val agentToolTimeoutSec: Int = AgentTimingParams.TOOL_TIMEOUT_SEC_DEFAULT,
    val systemPrompt: String = "",
    val showUsageEstimate: Boolean = false,
    val certPins: String = "",
    /** Null / absent = provider default (omit from API). */
    val temperature: Double? = null,
    /** Null / absent / <=0 = provider default (omit from API). */
    val maxTokens: Int? = null,
    /** Null / absent = provider default (omit from API). */
    val topP: Double? = null,
    /** Present only when includeApiKey=true. Treat as secret. */
    val apiKey: String? = null,
)

object SettingsBackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(bundle: SettingsBackupBundle): String = json.encodeToString(bundle)

    fun decode(raw: String): Result<SettingsBackupBundle> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Empty settings backup" }
        json.decodeFromString(SettingsBackupBundle.serializer(), trimmed)
    }.recoverCatching { e ->
        throw IllegalArgumentException("Invalid Minima settings backup: ${e.message}", e)
    }

    fun fromSettings(s: AppSettings, includeApiKey: Boolean): SettingsBackupBundle =
        SettingsBackupBundle(
            includeApiKey = includeApiKey,
            baseUrl = s.baseUrl,
            modelId = s.modelId,
            appearance = s.appearance.name,
            agentEnabled = s.agentEnabled,
            sandboxOnly = s.sandboxOnly,
            skillsTreeUri = s.skillsTreeUri,
            mcpConfigJson = s.mcpConfigJson,
            scheduledNoteEnabled = s.scheduledNoteEnabled,
            maxAgentSteps = s.maxAgentSteps,
            agentWallClockSec = s.clampedWallClockSec,
            agentToolTimeoutSec = s.clampedToolTimeoutSec,
            systemPrompt = s.systemPrompt,
            showUsageEstimate = s.showUsageEstimate,
            certPins = s.certPins,
            temperature = s.clampedTemperature,
            maxTokens = s.clampedMaxTokens,
            topP = s.clampedTopP,
            apiKey = if (includeApiKey && s.apiKey.isNotBlank()) s.apiKey else null,
        )

    fun toPartialSettings(bundle: SettingsBackupBundle): AppSettings =
        AppSettings(
            baseUrl = bundle.baseUrl,
            modelId = bundle.modelId,
            apiKey = if (bundle.includeApiKey) bundle.apiKey.orEmpty() else "",
            appearance = runCatching {
                AppearanceSetting.valueOf(bundle.appearance)
            }.getOrDefault(AppearanceSetting.System),
            agentEnabled = bundle.agentEnabled,
            sandboxOnly = bundle.sandboxOnly,
            skillsTreeUri = bundle.skillsTreeUri,
            mcpConfigJson = bundle.mcpConfigJson,
            scheduledNoteEnabled = bundle.scheduledNoteEnabled,
            maxAgentSteps = bundle.maxAgentSteps.coerceIn(1, 10_000),
            agentWallClockSec = AgentTimingParams.clampWallClockSec(bundle.agentWallClockSec),
            agentToolTimeoutSec = AgentTimingParams.clampToolTimeoutSec(
                bundle.agentToolTimeoutSec,
                wallClockSec = AgentTimingParams.clampWallClockSec(bundle.agentWallClockSec),
            ),
            systemPrompt = bundle.systemPrompt,
            showUsageEstimate = bundle.showUsageEstimate,
            certPins = bundle.certPins,
            temperature = SamplingParams.clampTemperature(bundle.temperature),
            maxTokens = SamplingParams.clampMaxTokens(bundle.maxTokens),
            topP = SamplingParams.clampTopP(bundle.topP),
        )
}
