package com.minima.chat.core.data.util

import com.minima.chat.core.data.db.MessageEntity
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus

/**
 * Bidirectional mapping between Room [MessageEntity] rows and domain [Message].
 * Kept public so JVM tests can simulate DB → LLM history without Room.
 */
object MessageEntityMapper {

    fun toModel(entity: MessageEntity): Message = Message(
        id = entity.id,
        sessionId = entity.sessionId,
        role = runCatching { MessageRole.valueOf(entity.role) }.getOrDefault(MessageRole.Assistant),
        content = entity.content,
        createdAt = entity.createdAt,
        kind = if (entity.kind == "tool") MessageKind.Tool else MessageKind.Text,
        toolName = entity.toolName,
        toolArgs = entity.toolArgs,
        toolResult = entity.toolResult,
        toolStatus = when (entity.toolStatus) {
            "running" -> ToolMessageStatus.Running
            "ok" -> ToolMessageStatus.Ok
            "error" -> ToolMessageStatus.Error
            else -> null
        },
        toolCallId = entity.toolCallId,
        agentStep = entity.agentStep,
        inputTokens = entity.inputTokens,
        outputTokens = entity.outputTokens,
        attachmentPath = entity.attachmentPath,
        attachmentMime = entity.attachmentMime,
    )

    fun toEntity(message: Message): MessageEntity = MessageEntity(
        id = message.id,
        sessionId = message.sessionId,
        role = message.role.name,
        content = message.content,
        createdAt = message.createdAt,
        kind = if (message.kind == MessageKind.Tool) "tool" else "text",
        toolName = message.toolName,
        toolArgs = message.toolArgs,
        toolResult = message.toolResult,
        toolStatus = when (message.toolStatus) {
            ToolMessageStatus.Running -> "running"
            ToolMessageStatus.Ok -> "ok"
            ToolMessageStatus.Error -> "error"
            null -> null
        },
        toolCallId = message.toolCallId,
        agentStep = message.agentStep,
        inputTokens = message.inputTokens,
        outputTokens = message.outputTokens,
        attachmentPath = message.attachmentPath,
        attachmentMime = message.attachmentMime,
    )
}
