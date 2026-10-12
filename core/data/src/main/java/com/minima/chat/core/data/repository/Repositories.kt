package com.minima.chat.core.data.repository

import android.content.Context
import com.minima.chat.core.data.media.ImageAttachPrep
import com.minima.chat.core.data.util.VisionHistoryPatch
import com.minima.chat.core.network.dto.ImageAttachmentDto
import dagger.hilt.android.qualifiers.ApplicationContext
import com.minima.chat.core.data.db.MessageDao
import com.minima.chat.core.data.db.MessageEntity
import com.minima.chat.core.data.util.MessageEntityMapper
import com.minima.chat.core.data.db.SessionDao
import com.minima.chat.core.data.db.SessionEntity
import com.minima.chat.core.data.export.ExportedMessage
import com.minima.chat.core.data.export.ExportedSession
import com.minima.chat.core.data.export.SessionExportBundle
import com.minima.chat.core.data.export.SessionExportCodec
import com.minima.chat.core.data.util.HistoryWindow
import com.minima.chat.core.data.util.RunningToolCancel
import com.minima.chat.core.data.model.AppSettings
import com.minima.chat.core.data.model.AppearanceSetting
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.Session
import com.minima.chat.core.data.util.SessionTitle
import com.minima.chat.core.data.prefs.SettingsStore
import com.minima.chat.core.network.api.ChatApiClient
import com.minima.chat.core.network.api.ChatStreamEvent
import com.minima.chat.core.network.dto.TokenUsage
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {
    fun observeSessions(): Flow<List<Session>> =
        sessionDao.observeActive().map { list -> list.map { it.toModel() } }

    fun observeArchived(): Flow<List<Session>> =
        sessionDao.observeArchived().map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): Session? = sessionDao.getById(id)?.toModel()

    suspend fun create(title: String = "New chat"): Session {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            pinned = false,
            archived = false,
        )
        sessionDao.upsert(session)
        return session.toModel()
    }

    suspend fun rename(id: String, title: String) {
        val existing = sessionDao.getById(id) ?: return
        sessionDao.update(
            existing.copy(
                title = SessionTitle.normalize(title),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun touch(id: String) {
        val existing = sessionDao.getById(id) ?: return
        sessionDao.update(existing.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        sessionDao.setPinned(id, pinned, System.currentTimeMillis())
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        sessionDao.setArchived(id, archived, System.currentTimeMillis())
    }

    suspend fun delete(id: String) {
        sessionDao.delete(id)
    }

    private fun SessionEntity.toModel() = Session(id, title, createdAt, updatedAt, pinned, archived)
}

@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val messageDao: MessageDao,
    private val sessionRepository: SessionRepository,
    private val settingsStore: SettingsStore,
    private val chatApiClient: ChatApiClient,
) {
    fun observeMessages(sessionId: String): Flow<List<Message>> =
        messageDao.observeBySession(sessionId).map { list ->
            list.map { it.toModel() }
        }

    suspend fun list(sessionId: String): List<Message> =
        messageDao.listBySession(sessionId).map { it.toModel() }

    /**
     * OpenAI-compatible history for the next request, including prior tool rounds
     * (assistant tool_calls + tool role results). See [LlmHistoryMapper].
     */
    suspend fun listForLlm(
        sessionId: String,
        excludeMessageIds: Set<String> = emptySet(),
    ): List<ChatMessageDto> =
        com.minima.chat.core.data.util.LlmHistoryMapper.toChatMessages(
            list(sessionId),
            excludeMessageIds,
            imageLoader = { m ->
                ImageAttachPrep.loadAllForReplay(appContext, m.attachmentPath, m.attachmentMime)
            },
        )

    suspend fun appendLocal(
        sessionId: String,
        role: MessageRole,
        content: String,
        id: String = UUID.randomUUID().toString(),
        attachmentPath: String? = null,
        attachmentMime: String? = null,
    ): Message {
        val msg = MessageEntity(
            id = id,
            sessionId = sessionId,
            role = role.name,
            content = content,
            createdAt = System.currentTimeMillis(),
            kind = "text",
            attachmentPath = attachmentPath,
            attachmentMime = attachmentMime,
        )
        messageDao.upsert(msg)
        sessionRepository.touch(sessionId)
        if (role == MessageRole.User) {
            val session = sessionRepository.get(sessionId)
            if (session != null && (session.title == "New chat" || session.title == "Untitled")) {
                val title = content.trim().lineSequence().firstOrNull()?.take(48)
                    ?: if (attachmentPath != null) "Image" else "New chat"
                sessionRepository.rename(sessionId, title)
            }
        }
        return msg.toModel()
    }

    suspend fun appendToolStart(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        argsSummary: String,
        agentStep: Int = 0,
        id: String = UUID.randomUUID().toString(),
    ): Message {
        val existing = messageDao.findByToolCallId(sessionId, toolCallId)
        if (existing != null) return existing.toModel()
        val msg = MessageEntity(
            id = id,
            sessionId = sessionId,
            role = MessageRole.Tool.name,
            content = "🔧 $toolName",
            createdAt = System.currentTimeMillis(),
            kind = "tool",
            toolName = toolName,
            toolArgs = argsSummary,
            toolResult = null,
            toolStatus = "running",
            toolCallId = toolCallId,
            agentStep = agentStep.coerceAtLeast(0),
        )
        messageDao.upsert(msg)
        sessionRepository.touch(sessionId)
        return msg.toModel()
    }

    suspend fun finishTool(
        sessionId: String,
        toolCallId: String,
        result: String,
        ok: Boolean,
    ) {
        val existing = messageDao.findByToolCallId(sessionId, toolCallId) ?: return
        val status = if (ok) "ok" else "error"
        val stored = result
            .ifBlank { "(empty tool result)" }
            .take(com.minima.chat.core.data.util.LlmHistoryMapper.MAX_TOOL_PAYLOAD_CHARS)
        messageDao.updateToolResult(
            id = existing.id,
            result = stored,
            status = status,
            content = "🔧 ${existing.toolName ?: "tool"} — $status",
        )
    }

    /**
     * Mark in-flight tool cards as cancelled/error so Stop / process death
     * cannot leave stuck Running rows that block edit/regenerate UX.
     *
     * [createdAtCeilingMs] limits cancellation to tools started at or before that
     * instant so a fire-and-forget cleanup (onCleared / Stop) cannot clobber a
     * newer run that already appended tool cards in the same session.
     */
    suspend fun cancelRunningTools(
        sessionId: String,
        createdAtCeilingMs: Long = Long.MAX_VALUE,
    ) {
        val running = messageDao.listRunningTools(sessionId, createdAtCeilingMs)
            .filter { RunningToolCancel.shouldCancel(it.createdAt, createdAtCeilingMs) }
        for (m in running) {
            messageDao.updateToolResult(
                id = m.id,
                result = "(cancelled)",
                status = "error",
                content = "🔧 ${m.toolName ?: "tool"} — cancelled",
            )
        }
    }

    suspend fun updateContent(id: String, content: String) {
        messageDao.updateContent(id, content)
    }

    suspend fun updateContentAndUsage(id: String, content: String, usage: TokenUsage?) {
        if (usage != null && usage.isPresent()) {
            messageDao.updateContentAndUsage(
                id = id,
                content = content,
                inputTokens = usage.promptTokens,
                outputTokens = usage.completionTokens,
            )
        } else {
            messageDao.updateContent(id, content)
        }
    }

    suspend fun updateUsage(id: String, usage: TokenUsage) {
        if (!usage.isPresent()) return
        messageDao.updateUsage(id, usage.promptTokens, usage.completionTokens)
    }

    /** Delete messages in session with createdAt strictly after [afterCreatedAt]. */
    suspend fun deleteAfter(sessionId: String, afterCreatedAt: Long) {
        messageDao.deleteAfter(sessionId, afterCreatedAt)
    }

    /** Delete a single message by id. */
    suspend fun deleteById(id: String) {
        messageDao.deleteById(id)
    }

    /** Delete messages in session with createdAt >= [fromCreatedAt] (inclusive). */
    suspend fun deleteFrom(sessionId: String, fromCreatedAt: Long) {
        messageDao.deleteFrom(sessionId, fromCreatedAt)
    }

    /**
     * Send user text, persist it, stream assistant reply (SSE with non-stream fallback).
     */
    fun sendAndStream(
        sessionId: String,
        userText: String,
        assistantMessageId: String,
        attachmentPath: String? = null,
        attachmentMime: String? = null,
        /** In-memory base64 for the just-attached image (belt-and-suspenders vs disk replay). */
        attachmentBase64: String? = null,
        /** Multi-image in-memory payloads (preferred when >1). */
        attachmentBase64s: List<ImageAttachmentDto> = emptyList(),
    ): Flow<ChatStreamEvent> = kotlinx.coroutines.flow.flow {
        val settings = settingsStore.settings.first()
        val displayText = when {
            userText.isNotBlank() -> userText
            attachmentPath != null -> "(image)"
            else -> userText
        }
        appendLocal(
            sessionId,
            MessageRole.User,
            displayText,
            attachmentPath = attachmentPath,
            attachmentMime = attachmentMime,
        )
        appendLocal(sessionId, MessageRole.Assistant, "", id = assistantMessageId)

        val pendingDtos = when {
            attachmentBase64s.isNotEmpty() -> attachmentBase64s.filter { it.isSendable() }
            else -> attachmentBase64?.takeIf { it.isNotBlank() }?.let {
                listOf(
                    ImageAttachmentDto(
                        attachmentMime?.lineSequence()?.firstOrNull()?.takeIf { m -> m.startsWith("image/") }
                            ?: "image/jpeg",
                        it,
                    ),
                )
            }.orEmpty()
        }
        val history = HistoryWindow.fit(
            VisionHistoryPatch.ensureLastUserImages(
                buildList {
                    RunningToolCancel.nonAgentSystemPrompt(settings.systemPrompt)?.let { sys ->
                        add(ChatMessageDto(role = "system", content = sys))
                    }
                    addAll(listForLlm(sessionId, excludeMessageIds = setOf(assistantMessageId)))
                },
                pendingDtos,
            ),
        ).messages

        val config = ChatProviderConfig(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.modelId,
            temperature = settings.clampedTemperature,
            maxTokens = settings.clampedMaxTokens,
            topP = settings.clampedTopP,
        )

        var full = StringBuilder()
        var usedFallback = false
        var lastFlushMs = 0L
        chatApiClient.streamChat(config, history).collect { event ->
            when (event) {
                is ChatStreamEvent.Delta -> {
                    full.append(event.text)
                    val now = System.currentTimeMillis()
                    if (now - lastFlushMs >= 48L) {
                        lastFlushMs = now
                        updateContent(assistantMessageId, full.toString())
                    }
                    emit(event)
                }
                is ChatStreamEvent.Completed -> {
                    val finalText = event.fullText.ifEmpty { full.toString() }
                    if (finalText.isNotEmpty() || (event.usage != null && event.usage!!.isPresent())) {
                        updateContentAndUsage(assistantMessageId, finalText, event.usage)
                    }
                    sessionRepository.touch(sessionId)
                    emit(event)
                }
                is ChatStreamEvent.Error -> {
                    if (full.isEmpty() && !usedFallback && settings.hasApiKey) {
                        usedFallback = true
                        chatApiClient.completeChat(config, history).collect { fb ->
                            when (fb) {
                                is ChatStreamEvent.Delta -> {
                                    full.append(fb.text)
                                    updateContent(assistantMessageId, full.toString())
                                    emit(fb)
                                }
                                is ChatStreamEvent.Completed -> {
                                    updateContentAndUsage(
                                        assistantMessageId,
                                        fb.fullText.ifEmpty { full.toString() },
                                        fb.usage,
                                    )
                                    sessionRepository.touch(sessionId)
                                    emit(fb)
                                }
                                is ChatStreamEvent.Error -> {
                                    updateContent(
                                        assistantMessageId,
                                        "⚠️ ${fb.message}",
                                    )
                                    emit(fb)
                                }
                            }
                        }
                    } else if (full.isEmpty()) {
                        updateContent(assistantMessageId, "⚠️ ${event.message}")
                        emit(event)
                    } else {
                        emit(ChatStreamEvent.Completed(full.toString()))
                    }
                }
            }
        }
    }

    private fun MessageEntity.toModel() = MessageEntityMapper.toModel(this)
}

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsStore: SettingsStore,
) {
    val settings: Flow<AppSettings> = settingsStore.settings

    suspend fun setBaseUrl(v: String) = settingsStore.updateBaseUrl(v)
    suspend fun setModelId(v: String) = settingsStore.updateModelId(v)
    suspend fun setApiKey(v: String) = settingsStore.updateApiKey(v)
    suspend fun setAppearance(v: AppearanceSetting) = settingsStore.updateAppearance(v)
    suspend fun setAgentEnabled(v: Boolean) = settingsStore.updateAgentEnabled(v)
    suspend fun setSandboxOnly(v: Boolean) = settingsStore.updateSandboxOnly(v)
    suspend fun setSkillsTreeUri(v: String) = settingsStore.updateSkillsTreeUri(v)
    suspend fun setMcpConfigJson(v: String) = settingsStore.updateMcpConfigJson(v)
    suspend fun setMcpLastHealthJson(v: String) = settingsStore.updateMcpLastHealthJson(v)
    suspend fun setScheduledNoteEnabled(v: Boolean) = settingsStore.updateScheduledNoteEnabled(v)
    suspend fun setMaxAgentSteps(v: Int) = settingsStore.updateMaxAgentSteps(v)
    suspend fun setAgentWallClockSec(v: Int) = settingsStore.updateAgentWallClockSec(v)
    suspend fun setAgentToolTimeoutSec(v: Int) = settingsStore.updateAgentToolTimeoutSec(v)
    suspend fun setOnboardingCompleted(v: Boolean) = settingsStore.updateOnboardingCompleted(v)
    suspend fun setSystemPrompt(v: String) = settingsStore.updateSystemPrompt(v)
    suspend fun setShowUsageEstimate(v: Boolean) = settingsStore.updateShowUsageEstimate(v)
    suspend fun setCertPins(v: String) = settingsStore.updateCertPins(v)
    suspend fun setTemperature(v: Double?) = settingsStore.updateTemperature(v)
    suspend fun setMaxTokens(v: Int?) = settingsStore.updateMaxTokens(v)
    suspend fun setTopP(v: Double?) = settingsStore.updateTopP(v)

    /** Apply a settings backup (sessions not included). API key only if bundle included it. */
    suspend fun applyBackup(partial: AppSettings, applyApiKey: Boolean) {
        setBaseUrl(partial.baseUrl)
        setModelId(partial.modelId)
        if (applyApiKey) setApiKey(partial.apiKey)
        setAppearance(partial.appearance)
        setAgentEnabled(partial.agentEnabled)
        setSandboxOnly(partial.sandboxOnly)
        setSkillsTreeUri(partial.skillsTreeUri)
        setMcpConfigJson(partial.mcpConfigJson)
        setScheduledNoteEnabled(partial.scheduledNoteEnabled)
        setMaxAgentSteps(partial.maxAgentSteps)
        setAgentWallClockSec(partial.agentWallClockSec)
        setAgentToolTimeoutSec(partial.agentToolTimeoutSec)
        setSystemPrompt(partial.systemPrompt)
        setShowUsageEstimate(partial.showUsageEstimate)
        setCertPins(partial.certPins)
        setTemperature(partial.temperature)
        setMaxTokens(partial.maxTokens)
        setTopP(partial.topP)
    }
}

