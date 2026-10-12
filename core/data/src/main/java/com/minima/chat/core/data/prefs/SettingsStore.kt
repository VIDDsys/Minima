package com.minima.chat.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.minima.chat.core.data.model.AppSettings
import com.minima.chat.core.data.model.AgentTimingParams
import com.minima.chat.core.data.model.AppearanceSetting
import com.minima.chat.core.network.api.SamplingParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("minima_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val modelKey = stringPreferencesKey("model_id")
    private val appearanceKey = stringPreferencesKey("appearance")
    private val agentEnabledKey = booleanPreferencesKey("agent_enabled")
    private val sandboxOnlyKey = booleanPreferencesKey("sandbox_only")
    private val skillsUriKey = stringPreferencesKey("skills_tree_uri")
    private val mcpJsonKey = stringPreferencesKey("mcp_config_json")
    private val mcpLastHealthKey = stringPreferencesKey("mcp_last_health_json")
    private val scheduledNoteKey = booleanPreferencesKey("scheduled_note")
    private val maxAgentStepsKey = intPreferencesKey("max_agent_steps")
    private val agentWallClockSecKey = intPreferencesKey("agent_wall_clock_sec")
    private val agentToolTimeoutSecKey = intPreferencesKey("agent_tool_timeout_sec")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    private val showUsageEstimateKey = booleanPreferencesKey("show_usage_estimate")
    private val certPinsKey = stringPreferencesKey("cert_pins")
    private val temperatureKey = stringPreferencesKey("temperature")
    private val maxTokensKey = intPreferencesKey("max_tokens")
    private val topPKey = stringPreferencesKey("top_p")
    /** Bumped on every API-key write so DataStore Flow always re-emits (empty edit is a no-op). */
    private val apiKeyRevKey = intPreferencesKey("api_key_rev")

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "minima_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[baseUrlKey] ?: "https://api.deepseek.com/v1",
            modelId = prefs[modelKey] ?: "deepseek-flash",
            apiKey = encryptedPrefs.getString(KEY_API, "") ?: "",
            appearance = runCatching {
                AppearanceSetting.valueOf(prefs[appearanceKey] ?: AppearanceSetting.System.name)
            }.getOrDefault(AppearanceSetting.System),
            agentEnabled = true, // hardcoded max: tools always on
            sandboxOnly = false, // hardcoded max: public storage allowed; ignore legacy pref
            skillsTreeUri = prefs[skillsUriKey] ?: "",
            mcpConfigJson = prefs[mcpJsonKey] ?: """{"servers":[]}""",
            mcpLastHealthJson = prefs[mcpLastHealthKey] ?: "[]",
            scheduledNoteEnabled = prefs[scheduledNoteKey] ?: false,
            maxAgentSteps = (prefs[maxAgentStepsKey] ?: 10_000).coerceIn(1, 10_000),
            agentWallClockSec = AgentTimingParams.clampWallClockSec(
                prefs[agentWallClockSecKey] ?: AgentTimingParams.WALL_CLOCK_SEC_DEFAULT,
            ),
            agentToolTimeoutSec = AgentTimingParams.clampToolTimeoutSec(
                prefs[agentToolTimeoutSecKey] ?: AgentTimingParams.TOOL_TIMEOUT_SEC_DEFAULT,
                wallClockSec = AgentTimingParams.clampWallClockSec(
                    prefs[agentWallClockSecKey] ?: AgentTimingParams.WALL_CLOCK_SEC_DEFAULT,
                ),
            ),
            onboardingCompleted = prefs[onboardingCompletedKey] ?: false,
            systemPrompt = prefs[systemPromptKey] ?: "",
            showUsageEstimate = prefs[showUsageEstimateKey] ?: false,
            certPins = prefs[certPinsKey] ?: "",
            temperature = SamplingParams.parseTemperature(prefs[temperatureKey]),
            maxTokens = SamplingParams.clampMaxTokens(prefs[maxTokensKey]),
            topP = SamplingParams.parseTopP(prefs[topPKey]),
        )
    }

    suspend fun updateBaseUrl(value: String) {
        context.settingsDataStore.edit { it[baseUrlKey] = value.trim() }
    }

    suspend fun updateModelId(value: String) {
        context.settingsDataStore.edit { it[modelKey] = value.trim() }
    }

    suspend fun updateApiKey(value: String) {
        // commit() so the key survives process death before DataStore tick lands
        encryptedPrefs.edit().putString(KEY_API, value.trim()).commit()
        // MUST mutate a real preference — empty edit {} does not emit, which cleared the
        // settings UI draft→saved race (typed key vanished ~0.5s after debounce).
        context.settingsDataStore.edit { prefs ->
            prefs[apiKeyRevKey] = (prefs[apiKeyRevKey] ?: 0) + 1
            // Also force-migrate legacy sandbox_only=true away.
            prefs[sandboxOnlyKey] = false
            prefs[agentEnabledKey] = true
        }
    }

    suspend fun updateAppearance(value: AppearanceSetting) {
        context.settingsDataStore.edit { it[appearanceKey] = value.name }
    }

    suspend fun updateAgentEnabled(value: Boolean) {
        // Max permissions: always keep agent on; ignore false writes.
        context.settingsDataStore.edit { it[agentEnabledKey] = true }
    }

    suspend fun updateSandboxOnly(value: Boolean) {
        // Sandbox feature removed: always permissive.
        context.settingsDataStore.edit { it[sandboxOnlyKey] = false }
    }

    suspend fun updateSkillsTreeUri(value: String) {
        context.settingsDataStore.edit { it[skillsUriKey] = value }
    }

    suspend fun updateMcpConfigJson(value: String) {
        context.settingsDataStore.edit { it[mcpJsonKey] = value }
    }

    suspend fun updateMcpLastHealthJson(value: String) {
        context.settingsDataStore.edit { it[mcpLastHealthKey] = value }
    }

    suspend fun updateScheduledNoteEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[scheduledNoteKey] = value }
    }

    suspend fun updateMaxAgentSteps(value: Int) {
        context.settingsDataStore.edit { it[maxAgentStepsKey] = value.coerceIn(1, 10_000) }
    }

    suspend fun updateAgentWallClockSec(value: Int) {
        context.settingsDataStore.edit {
            it[agentWallClockSecKey] = AgentTimingParams.clampWallClockSec(value)
        }
    }

    suspend fun updateAgentToolTimeoutSec(value: Int) {
        context.settingsDataStore.edit { prefs ->
            val wall = AgentTimingParams.clampWallClockSec(
                prefs[agentWallClockSecKey] ?: AgentTimingParams.WALL_CLOCK_SEC_DEFAULT,
            )
            prefs[agentToolTimeoutSecKey] = AgentTimingParams.clampToolTimeoutSec(value, wall)
        }
    }

    suspend fun updateOnboardingCompleted(value: Boolean) {
        context.settingsDataStore.edit { it[onboardingCompletedKey] = value }
    }

    /**
     * Legacy DataStore field kept for backup import only.
     * Global MD for the agent is `/storage/emulated/0/Minima/AGENTS.md` — never mirror here.
     */
    suspend fun updateSystemPrompt(value: String) {
        context.settingsDataStore.edit { it[systemPromptKey] = value }
    }

    suspend fun updateShowUsageEstimate(value: Boolean) {
        context.settingsDataStore.edit { it[showUsageEstimateKey] = value }
    }

    suspend fun updateCertPins(value: String) {
        context.settingsDataStore.edit { it[certPinsKey] = value.trim() }
    }

    suspend fun updateTemperature(value: Double?) {
        val clamped = SamplingParams.clampTemperature(value)
        context.settingsDataStore.edit {
            it[temperatureKey] = SamplingParams.formatTemperature(clamped)
        }
    }

    suspend fun updateMaxTokens(value: Int?) {
        val clamped = SamplingParams.clampMaxTokens(value)
        context.settingsDataStore.edit {
            it[maxTokensKey] = clamped ?: 0
        }
    }

    suspend fun updateTopP(value: Double?) {
        val clamped = SamplingParams.clampTopP(value)
        context.settingsDataStore.edit {
            it[topPKey] = SamplingParams.formatTopP(clamped)
        }
    }

    fun peekApiKey(): String = encryptedPrefs.getString(KEY_API, "") ?: ""

    companion object {
        private const val KEY_API = "api_key"
    }
}
