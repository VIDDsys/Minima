package com.minima.chat.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("toolCallId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    /** text | tool */
    val kind: String = "text",
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    /** running | ok | error */
    val toolStatus: String? = null,
    val toolCallId: String? = null,
    /**
     * 1-based agent iteration for this tool card within a user turn.
     * 0 = legacy / unknown. Used by LlmHistoryMapper to split multi-iteration rounds.
     */
    val agentStep: Int = 0,
    /** Optional provider or estimated prompt tokens. */
    val inputTokens: Int = 0,
    /** Optional provider or estimated completion tokens. */
    val outputTokens: Int = 0,
    /** Relative path under filesDir or absolute file path for a vision attachment. */
    val attachmentPath: String? = null,
    /** MIME for [attachmentPath], e.g. image/jpeg. */
    val attachmentMime: String? = null,
)
