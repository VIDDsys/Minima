package com.minima.chat.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minima.chat.core.agent.mcp.McpAuthHeaders
import com.minima.chat.core.agent.mcp.McpConfigParser
import com.minima.chat.core.agent.mcp.McpHealthCodec
import com.minima.chat.core.agent.mcp.McpServerConfig
import com.minima.chat.core.agent.mcp.McpServerHealth
import com.minima.chat.core.agent.schedule.ScheduledNoteScheduler
import com.minima.chat.core.agent.skills.SkillDoc
import com.minima.chat.core.agent.skills.SkillsLoader
import com.minima.chat.core.agent.tools.ToolRegistry
import com.minima.chat.core.data.export.SessionBackup
import com.minima.chat.core.data.export.SettingsBackupCodec
import com.minima.chat.core.data.model.AppSettings
import com.minima.chat.core.data.model.AgentTimingParams
import com.minima.chat.core.data.model.AppearanceSetting
import com.minima.chat.core.data.model.ProviderPreset
import com.minima.chat.core.network.api.SamplingParams
import com.minima.chat.core.data.repository.SettingsRepository
import com.minima.chat.core.network.api.EndpointReachability
import com.minima.chat.core.network.api.ModelsListClient
import com.minima.chat.core.network.api.SecretRedact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    private val scheduledNoteScheduler: ScheduledNoteScheduler,
    private val sessionBackup: SessionBackup,
    private val skillsLoader: SkillsLoader,
    private val endpointReachability: EndpointReachability,
    private val modelsListClient: ModelsListClient,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _draftBaseUrl = MutableStateFlow<String?>(null)
    private val _draftModel = MutableStateFlow<String?>(null)
    private val _draftApiKey = MutableStateFlow<String?>(null)
    private val _draftMcp = MutableStateFlow<String?>(null)
    private val _draftMaxSteps = MutableStateFlow<String?>(null)
    private val _draftWallClock = MutableStateFlow<String?>(null)
    private val _draftToolTimeout = MutableStateFlow<String?>(null)
    private val _draftSystemPrompt = MutableStateFlow<String?>(null)
    private val _draftCertPins = MutableStateFlow<String?>(null)
    private val _draftTemperature = MutableStateFlow<String?>(null)
    private val _draftMaxTokens = MutableStateFlow<String?>(null)
    private val _draftTopP = MutableStateFlow<String?>(null)

    val draftBaseUrl: StateFlow<String?> = _draftBaseUrl.asStateFlow()
    val draftModel: StateFlow<String?> = _draftModel.asStateFlow()
    val draftApiKey: StateFlow<String?> = _draftApiKey.asStateFlow()
    val draftMcp: StateFlow<String?> = _draftMcp.asStateFlow()
    val draftMaxSteps: StateFlow<String?> = _draftMaxSteps.asStateFlow()
    val draftWallClock: StateFlow<String?> = _draftWallClock.asStateFlow()
    val draftToolTimeout: StateFlow<String?> = _draftToolTimeout.asStateFlow()
    val draftSystemPrompt: StateFlow<String?> = _draftSystemPrompt.asStateFlow()
    val draftCertPins: StateFlow<String?> = _draftCertPins.asStateFlow()
    val draftTemperature: StateFlow<String?> = _draftTemperature.asStateFlow()
    val draftMaxTokens: StateFlow<String?> = _draftMaxTokens.asStateFlow()
    val draftTopP: StateFlow<String?> = _draftTopP.asStateFlow()

    private val _mcpHealth = MutableStateFlow<List<McpServerHealth>>(emptyList())
    val mcpHealth: StateFlow<List<McpServerHealth>> = _mcpHealth.asStateFlow()

    private val _mcpProbing = MutableStateFlow(false)
    val mcpProbing: StateFlow<Boolean> = _mcpProbing.asStateFlow()

    private val _mcpLastCheckedAtMs = MutableStateFlow<Long?>(null)
    val mcpLastCheckedAtMs: StateFlow<Long?> = _mcpLastCheckedAtMs.asStateFlow()

    private val _dataMessage = MutableStateFlow<String?>(null)
    val dataMessage: StateFlow<String?> = _dataMessage.asStateFlow()

    private val _skills = MutableStateFlow<List<SkillDoc>>(emptyList())
    val skills: StateFlow<List<SkillDoc>> = _skills.asStateFlow()

    private val _skillsLoading = MutableStateFlow(false)
    val skillsLoading: StateFlow<Boolean> = _skillsLoading.asStateFlow()

    private val _hydrated = MutableStateFlow(false)
    val hydrated: StateFlow<Boolean> = _hydrated.asStateFlow()

    private val _previewSkill = MutableStateFlow<SkillDoc?>(null)
    val previewSkill: StateFlow<SkillDoc?> = _previewSkill.asStateFlow()

    private val _toolInventory = MutableStateFlow(ToolRegistry.ToolInventory(emptyList(), emptyList()))
    val toolInventory: StateFlow<ToolRegistry.ToolInventory> = _toolInventory.asStateFlow()

    private val _reachabilityMessage = MutableStateFlow<String?>(null)
    val reachabilityMessage: StateFlow<String?> = _reachabilityMessage.asStateFlow()

    private val _reachabilityChecking = MutableStateFlow(false)
    val reachabilityChecking: StateFlow<Boolean> = _reachabilityChecking.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _modelsFetching = MutableStateFlow(false)
    val modelsFetching: StateFlow<Boolean> = _modelsFetching.asStateFlow()

    private val _modelsMessage = MutableStateFlow<String?>(null)
    val modelsMessage: StateFlow<String?> = _modelsMessage.asStateFlow()

    private var mcpHealthHydrated = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                if (!_hydrated.value) _hydrated.value = true
                if (!mcpHealthHydrated) {
                    mcpHealthHydrated = true
                    val cached = McpHealthCodec.decode(s.mcpLastHealthJson)
                    val live = toolRegistry.lastMcpHealth()
                    when {
                        live.isNotEmpty() -> {
                            _mcpHealth.value = live
                            val at = toolRegistry.lastMcpHealthCheckedAtMs()
                            _mcpLastCheckedAtMs.value = at.takeIf { it > 0L }
                                ?: live.maxOfOrNull { it.checkedAtMs }?.takeIf { it > 0L }
                        }
                        cached.isNotEmpty() -> {
                            _mcpHealth.value = cached
                            _mcpLastCheckedAtMs.value =
                                cached.maxOfOrNull { it.checkedAtMs }?.takeIf { it > 0L }
                        }
                    }
                }
                reloadSkills()
                refreshToolInventory()
            }
        }
    }

    fun reloadSkills() {
        viewModelScope.launch {
            _skillsLoading.value = true
            val uri = settings.value.skillsTreeUri.ifBlank { null }
            val list = withContext(Dispatchers.IO) {
                runCatching { toolRegistry.refreshSkillHttp(uri) }
                runCatching { skillsLoader.loadAll(uri) }.getOrDefault(emptyList())
            }
            _skills.value = list
            _skillsLoading.value = false
            refreshToolInventory()
        }
    }

    fun openSkillPreview(skill: SkillDoc) {
        _previewSkill.value = skill
    }

    fun closeSkillPreview() {
        _previewSkill.value = null
    }

    private var persistJob: Job? = null

    /** Debounced flush of ALL drafts (one job — typing field A must not cancel field B). */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(350)
            withContext(NonCancellable) { persistAllDrafts() }
        }
    }

    private suspend fun persistAllDrafts() {
        // Primary provider fields: persist but KEEP draft sticky so the TextField
        // never flashes empty while waiting for DataStore/encrypted prefs to re-emit.
        _draftBaseUrl.value?.let { v ->
            settingsRepository.setBaseUrl(v)
        }
        _draftModel.value?.let { v ->
            settingsRepository.setModelId(v)
        }
        _draftApiKey.value?.let { v ->
            settingsRepository.setApiKey(v)
        }
        _draftMcp.value?.let { v ->
            settingsRepository.setMcpConfigJson(v)
            if (_draftMcp.value == v) _draftMcp.value = null
        }
        _draftMaxSteps.value?.let { raw ->
            val n = raw.toIntOrNull() ?: return@let
            settingsRepository.setMaxAgentSteps(n)
            if (_draftMaxSteps.value == raw) _draftMaxSteps.value = null
        }
        _draftWallClock.value?.let { raw ->
            val n = raw.toIntOrNull() ?: return@let
            settingsRepository.setAgentWallClockSec(AgentTimingParams.clampWallClockSec(n))
            if (_draftWallClock.value == raw) _draftWallClock.value = null
        }
        _draftToolTimeout.value?.let { raw ->
            val n = raw.toIntOrNull() ?: return@let
            settingsRepository.setAgentToolTimeoutSec(n)
            if (_draftToolTimeout.value == raw) _draftToolTimeout.value = null
        }
        _draftSystemPrompt.value?.let { v ->
            // Sticky draft — same as provider fields so the editor never flashes empty.
            settingsRepository.setSystemPrompt(v)
        }
        _draftCertPins.value?.let { v ->
            settingsRepository.setCertPins(v)
            if (_draftCertPins.value == v) _draftCertPins.value = null
        }
        _draftTemperature.value?.let { raw ->
            settingsRepository.setTemperature(SamplingParams.parseTemperature(raw))
            if (_draftTemperature.value == raw) _draftTemperature.value = null
        }
        _draftMaxTokens.value?.let { raw ->
            settingsRepository.setMaxTokens(SamplingParams.parseMaxTokens(raw))
            if (_draftMaxTokens.value == raw) _draftMaxTokens.value = null
        }
        _draftTopP.value?.let { raw ->
            settingsRepository.setTopP(SamplingParams.parseTopP(raw))
            if (_draftTopP.value == raw) _draftTopP.value = null
        }
    }

    fun onBaseUrlChange(v: String) {
        _draftBaseUrl.value = v
        schedulePersist()
    }
    fun onModelChange(v: String) {
        _draftModel.value = v
        schedulePersist()
    }
    fun onApiKeyChange(v: String) {
        _draftApiKey.value = v
        schedulePersist()
    }
    fun onMcpChange(v: String) {
        _draftMcp.value = v
        schedulePersist()
    }

    /** Parsed servers from draft or saved MCP JSON (empty if invalid). */
    fun mcpServersDraft(): List<McpServerConfig> {
        val raw = _draftMcp.value ?: settings.value.mcpConfigJson
        return McpConfigParser.parse(raw).servers
    }

    /** Current MCP JSON text (draft preferred). */
    fun mcpJsonNow(): String = _draftMcp.value ?: settings.value.mcpConfigJson

    /**
     * Update one server's bearerToken and/or custom headers in the MCP JSON draft.
     * Pass null to leave that field unchanged. Does not auto-save.
     */
    fun updateMcpServerAuth(
        serverName: String,
        bearerToken: String? = null,
        headersLines: String? = null,
    ) {
        val raw = mcpJsonNow()
        val headers = headersLines?.let { McpAuthHeaders.linesToHeaders(it) }
        val patched = McpAuthHeaders.patchServerAuth(
            rawJson = raw,
            serverName = serverName,
            bearerToken = bearerToken,
            headers = headers,
        )
        _draftMcp.value = patched
        schedulePersist()
    }
    fun onMaxStepsChange(v: String) {
        _draftMaxSteps.value = v.filter { it.isDigit() }.take(2)
        schedulePersist()
    }
    fun onWallClockChange(v: String) {
        _draftWallClock.value = v.filter { it.isDigit() }.take(3)
        schedulePersist()
    }
    fun onToolTimeoutChange(v: String) {
        _draftToolTimeout.value = v.filter { it.isDigit() }.take(3)
        schedulePersist()
    }
    fun onSystemPromptChange(v: String) {
        _draftSystemPrompt.value = v
        schedulePersist()
    }
    fun onCertPinsChange(v: String) {
        _draftCertPins.value = v
        schedulePersist()
    }
    fun onTemperatureChange(v: String) {
        // Digits + one `.` or `,` (locale IME); empty = provider default.
        _draftTemperature.value = SamplingParams.filterDecimalDraft(v)
        schedulePersist()
    }
    fun onTopPChange(v: String) {
        _draftTopP.value = SamplingParams.filterDecimalDraft(v)
        schedulePersist()
    }
    fun onMaxTokensChange(v: String) {
        _draftMaxTokens.value = v.filter { it.isDigit() }.take(6)
        schedulePersist()
    }

    fun saveBaseUrl() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val v = _draftBaseUrl.value ?: return@withContext
                settingsRepository.setBaseUrl(v)
                // Keep draft sticky (do not null) so UI never blanks.
            }
        }
    }

    fun saveModel() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val v = _draftModel.value ?: return@withContext
                settingsRepository.setModelId(v)
            }
        }
    }

    fun saveApiKey() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val v = _draftApiKey.value ?: return@withContext
                settingsRepository.setApiKey(v)
                // Keep draft — clearing caused ~0.5s vanishing while Flow lagged.
            }
        }
    }

    fun saveMcp() {
        viewModelScope.launch {
            val v = _draftMcp.value
            if (v != null) {
                withContext(NonCancellable) {
                    settingsRepository.setMcpConfigJson(v)
                    _draftMcp.value = null
                }
                probeMcp(v)
            }
        }
    }

    fun saveMaxSteps() {
        viewModelScope.launch {
            val raw = _draftMaxSteps.value ?: return@launch
            val n = raw.toIntOrNull() ?: return@launch
            settingsRepository.setMaxAgentSteps(n)
            _draftMaxSteps.value = null
        }
    }

    fun saveWallClock() {
        viewModelScope.launch {
            val raw = _draftWallClock.value ?: return@launch
            val n = raw.toIntOrNull() ?: return@launch
            settingsRepository.setAgentWallClockSec(AgentTimingParams.clampWallClockSec(n))
            _draftWallClock.value = null
            // Re-clamp tool timeout against new wall if draft empty
            val toolDraft = _draftToolTimeout.value
            if (toolDraft == null) {
                val current = settings.value.agentToolTimeoutSec
                settingsRepository.setAgentToolTimeoutSec(current)
            }
        }
    }

    fun saveToolTimeout() {
        viewModelScope.launch {
            val raw = _draftToolTimeout.value ?: return@launch
            val n = raw.toIntOrNull() ?: return@launch
            settingsRepository.setAgentToolTimeoutSec(n)
            _draftToolTimeout.value = null
        }
    }

    fun saveTemperature() {
        viewModelScope.launch {
            val raw = _draftTemperature.value ?: return@launch
            settingsRepository.setTemperature(SamplingParams.parseTemperature(raw))
            _draftTemperature.value = null
        }
    }

    fun saveMaxTokens() {
        viewModelScope.launch {
            val raw = _draftMaxTokens.value ?: return@launch
            settingsRepository.setMaxTokens(SamplingParams.parseMaxTokens(raw))
            _draftMaxTokens.value = null
        }
    }

    fun saveTopP() {
        viewModelScope.launch {
            val raw = _draftTopP.value ?: return@launch
            settingsRepository.setTopP(SamplingParams.parseTopP(raw))
            _draftTopP.value = null
        }
    }

    fun saveSystemPrompt() {
        viewModelScope.launch {
            val v = _draftSystemPrompt.value ?: return@launch
            settingsRepository.setSystemPrompt(v)
            _draftSystemPrompt.value = null
        }
    }

    fun saveCertPins() {
        viewModelScope.launch {
            val v = _draftCertPins.value ?: return@launch
            settingsRepository.setCertPins(v)
            _draftCertPins.value = null
        }
    }

    /** Fill base URL + model hint; never touches API key. Custom is a no-op. */
    fun applyProviderPreset(preset: ProviderPreset) {
        if (!preset.fillsFields) return
        viewModelScope.launch {
            settingsRepository.setBaseUrl(preset.baseUrl)
            settingsRepository.setModelId(preset.modelHint)
            _draftBaseUrl.value = preset.baseUrl
            _draftModel.value = preset.modelHint
        }
    }

    fun probeMcp(jsonOverride: String? = null) {
        viewModelScope.launch {
            _mcpProbing.value = true
            val json = jsonOverride ?: (_draftMcp.value ?: settings.value.mcpConfigJson)
            val health = withContext(Dispatchers.IO) {
                runCatching { toolRegistry.probeMcp(json) }.getOrDefault(emptyList())
            }
            _mcpHealth.value = health.map { h ->
                h.copy(message = SecretRedact.redact(h.message))
            }
            val checkedAt = health.maxOfOrNull { it.checkedAtMs }?.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            _mcpLastCheckedAtMs.value = checkedAt
            withContext(Dispatchers.IO) {
                runCatching {
                    settingsRepository.setMcpLastHealthJson(McpHealthCodec.encode(health))
                }
            }
            _mcpProbing.value = false
            refreshToolInventory()
        }
    }

    fun refreshToolInventory() {
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure MCP map matches current JSON without network if already loaded
            runCatching { toolRegistry.refreshMcp(settings.value.mcpConfigJson) }
            runCatching {
                toolRegistry.refreshSkillHttp(settings.value.skillsTreeUri.ifBlank { null })
            }
            _toolInventory.value = toolRegistry.inventory()
        }
    }

    fun setAppearance(v: AppearanceSetting) {
        viewModelScope.launch { settingsRepository.setAppearance(v) }
    }

    fun setAgentEnabled(v: Boolean) {
        // Sandbox/agent toggles removed — always max permissions.
        viewModelScope.launch { settingsRepository.setAgentEnabled(true) }
    }

    fun setSandboxOnly(v: Boolean) {
        viewModelScope.launch { settingsRepository.setSandboxOnly(false) }
    }

    fun setSkillsTreeUri(v: String) {
        viewModelScope.launch {
            settingsRepository.setSkillsTreeUri(v)
            reloadSkills()
        }
    }

    fun setShowUsageEstimate(v: Boolean) {
        viewModelScope.launch { settingsRepository.setShowUsageEstimate(v) }
    }

    fun setScheduledNoteEnabled(v: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScheduledNoteEnabled(v)
            scheduledNoteScheduler.setEnabled(v)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }

    suspend fun exportSessionsJson(): String = withContext(Dispatchers.IO) {
        sessionBackup.exportJson()
    }

    fun importSessionsJson(raw: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { sessionBackup.importJson(raw) }
            _dataMessage.value = result.fold(
                onSuccess = { "已导入 $it 个会话" },
                onFailure = { "导入失败：${it.message}" },
            )
        }
    }

    /** Settings-only backup. [includeApiKey]=true embeds the key in plaintext JSON — warn in UI. */
    suspend fun exportSettingsBackup(includeApiKey: Boolean): String = withContext(Dispatchers.IO) {
        SettingsBackupCodec.encode(
            SettingsBackupCodec.fromSettings(settings.value, includeApiKey = includeApiKey),
        )
    }

    fun importSettingsBackup(raw: String) {
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) { SettingsBackupCodec.decode(raw) }
            decoded.fold(
                onSuccess = { bundle ->
                    val partial = SettingsBackupCodec.toPartialSettings(bundle)
                    settingsRepository.applyBackup(partial, applyApiKey = bundle.includeApiKey && !bundle.apiKey.isNullOrBlank())
                    _draftBaseUrl.value = null
                    _draftModel.value = null
                    _draftApiKey.value = null
                    _draftMcp.value = null
                    _draftMaxSteps.value = null
                    _draftWallClock.value = null
                    _draftToolTimeout.value = null
                    _draftSystemPrompt.value = null
                    _draftCertPins.value = null
                    _draftTemperature.value = null
                    _draftMaxTokens.value = null
                    _dataMessage.value = if (bundle.includeApiKey) {
                        "设置已导入（含 API 密钥）"
                    } else {
                        "设置已导入（API 密钥未改）"
                    }
                },
                onFailure = { _dataMessage.value = "设置导入失败：${it.message}" },
            )
        }
    }


    fun checkEndpointReachability() {
        viewModelScope.launch {
            _reachabilityChecking.value = true
            _reachabilityMessage.value = null
            val url = (_draftBaseUrl.value ?: settings.value.baseUrl).trim()
            val result = withContext(Dispatchers.IO) {
                runCatching { endpointReachability.check(url) }
                    .getOrElse {
                        com.minima.chat.core.network.api.ReachabilityResult(
                            false,
                            SecretRedact.redact(it.message ?: "Check failed").take(120).ifBlank { "Check failed" },
                        )
                    }
            }
            _reachabilityMessage.value = SecretRedact.redact(result.message)
            _reachabilityChecking.value = false
        }
    }


    fun fetchModels() {
        viewModelScope.launch {
            _modelsFetching.value = true
            _modelsMessage.value = null
            // Persist drafts first so key/url match what user typed
            val urlDraft = _draftBaseUrl.value
            if (urlDraft != null) {
                settingsRepository.setBaseUrl(urlDraft)
            }
            val keyDraft = _draftApiKey.value
            if (keyDraft != null) {
                settingsRepository.setApiKey(keyDraft)
                // keep draft sticky
            }
            val snap = settings.value
            val url = (urlDraft ?: snap.baseUrl).trim()
            val key = when {
                !keyDraft.isNullOrEmpty() -> keyDraft
                else -> snap.apiKey
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { modelsListClient.fetch(url, key) }
                    .getOrElse {
                        com.minima.chat.core.network.api.ModelsListResult(
                            false,
                            message = SecretRedact.redact(it.message ?: "Fetch failed")
                                .take(120).ifBlank { "Fetch failed" },
                        )
                    }
            }
            if (result.ok) {
                _availableModels.value = result.ids
                _modelsMessage.value = SecretRedact.redact(result.message)
            } else {
                _availableModels.value = emptyList()
                _modelsMessage.value = SecretRedact.redact(result.message)
            }
            _modelsFetching.value = false
        }
    }

    /** Put a fetched model id into the Model field and persist. */
    fun selectModel(id: String) {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return
        _draftModel.value = trimmed
        viewModelScope.launch {
            settingsRepository.setModelId(trimmed)
            // keep draft sticky so Model field stays filled
            _modelsMessage.value = "已使用 $trimmed"
        }
    }

    fun clearModelsMessage() {
        _modelsMessage.value = null
    }

    fun clearReachabilityMessage() {
        _reachabilityMessage.value = null
    }


    /** Immediate flush of all drafts (back / dispose). Survives ViewModel clear. */
    fun flushAllPending() {
        persistJob?.cancel()
        viewModelScope.launch {
            withContext(NonCancellable) { persistAllDrafts() }
        }
    }

    fun clearDataMessage() {
        _dataMessage.value = null
    }
}
