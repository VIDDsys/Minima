package com.minima.chat.core.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SessionExportBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val sessions: List<ExportedSession> = emptyList(),
)

@Serializable
data class ExportedSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val messages: List<ExportedMessage> = emptyList(),
)

@Serializable
data class ExportedMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val kind: String = "text",
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolStatus: String? = null,
    val toolCallId: String? = null,
    val agentStep: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val attachmentPath: String? = null,
    val attachmentMime: String? = null,
)

object SessionExportCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(bundle: SessionExportBundle): String = json.encodeToString(bundle)

    fun decode(raw: String): Result<SessionExportBundle> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Empty export" }
        json.decodeFromString(SessionExportBundle.serializer(), trimmed)
    }.recoverCatching { e ->
        throw IllegalArgumentException("Invalid Minima export JSON: ${e.message}", e)
    }
}
