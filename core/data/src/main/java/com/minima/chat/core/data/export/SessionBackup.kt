package com.minima.chat.core.data.export

import com.minima.chat.core.data.db.MessageDao
import com.minima.chat.core.data.db.MessageEntity
import com.minima.chat.core.data.db.SessionDao
import com.minima.chat.core.data.db.SessionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionBackup @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
) {
    suspend fun exportJson(): String {
        val sessions = sessionDao.listAll()
        val exported = sessions.map { s ->
            val msgs = messageDao.listBySession(s.id).map { m ->
                ExportedMessage(
                    id = m.id,
                    role = m.role,
                    content = m.content,
                    createdAt = m.createdAt,
                    kind = m.kind,
                    toolName = m.toolName,
                    toolArgs = m.toolArgs,
                    toolResult = m.toolResult,
                    toolStatus = m.toolStatus,
                    toolCallId = m.toolCallId,
                    agentStep = m.agentStep,
                    inputTokens = m.inputTokens,
                    outputTokens = m.outputTokens,
                    attachmentPath = m.attachmentPath,
                    attachmentMime = m.attachmentMime,
                )
            }
            ExportedSession(
                id = s.id,
                title = s.title,
                createdAt = s.createdAt,
                updatedAt = s.updatedAt,
                pinned = s.pinned,
                archived = s.archived,
                messages = msgs,
            )
        }
        return SessionExportCodec.encode(SessionExportBundle(sessions = exported))
    }

    /**
     * Import sessions/messages by id (replace). Returns count of sessions imported.
     */
    suspend fun importJson(raw: String): Result<Int> {
        val bundle = SessionExportCodec.decode(raw).getOrElse { return Result.failure(it) }
        if (bundle.sessions.isEmpty()) {
            return Result.failure(IllegalArgumentException("No sessions in export"))
        }
        for (s in bundle.sessions) {
            if (s.id.isBlank()) continue
            sessionDao.upsert(
                SessionEntity(
                    id = s.id,
                    title = s.title.ifBlank { "Imported" },
                    createdAt = s.createdAt,
                    updatedAt = s.updatedAt,
                    pinned = s.pinned,
                    archived = s.archived,
                ),
            )
            // Replace messages for this session
            messageDao.deleteBySession(s.id)
            val entities = s.messages.mapNotNull { m ->
                if (m.id.isBlank()) return@mapNotNull null
                MessageEntity(
                    id = m.id,
                    sessionId = s.id,
                    role = m.role.ifBlank { "Assistant" },
                    content = m.content,
                    createdAt = m.createdAt,
                    kind = m.kind.ifBlank { "text" },
                    toolName = m.toolName,
                    toolArgs = m.toolArgs,
                    toolResult = m.toolResult,
                    toolStatus = m.toolStatus,
                    toolCallId = m.toolCallId,
                    agentStep = m.agentStep,
                    inputTokens = m.inputTokens,
                    outputTokens = m.outputTokens,
                    attachmentPath = m.attachmentPath,
                    attachmentMime = m.attachmentMime,
                )
            }
            if (entities.isNotEmpty()) {
                messageDao.upsertAll(entities)
            }
        }
        return Result.success(bundle.sessions.count { it.id.isNotBlank() })
    }
}
