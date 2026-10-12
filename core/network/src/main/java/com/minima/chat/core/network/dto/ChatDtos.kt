package com.minima.chat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class StreamOptionsDto(
    @SerialName("include_usage") val includeUsage: Boolean = true,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    val tools: List<ToolDefinitionDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    /** OpenAI-compatible: ask the provider to emit usage on the final stream chunk. */
    @SerialName("stream_options") val streamOptions: StreamOptionsDto? = null,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
    /**
     * Request-only multimodal images. Never serialized by kotlinx defaults —
     * [com.minima.chat.core.network.api.MultimodalContent] encodes them as
     * OpenAI `image_url` content parts when building the HTTP body.
     */
    @Transient val images: List<ImageAttachmentDto> = emptyList(),
)

@Serializable
data class ToolDefinitionDto(
    val type: String = "function",
    val function: ToolFunctionDto,
)

@Serializable
data class ToolFunctionDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunctionDto,
)

@Serializable
data class ToolCallFunctionDto(
    val name: String,
    val arguments: String,
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

/** Aggregated prompt/completion tokens from a provider response (when present). */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
) {
    val total: Int get() = promptTokens + completionTokens
    fun isPresent(): Boolean = promptTokens > 0 || completionTokens > 0
    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
    )

    companion object {
        fun from(dto: UsageDto?): TokenUsage? {
            if (dto == null) return null
            val p = dto.promptTokens.coerceAtLeast(0)
            val c = dto.completionTokens.coerceAtLeast(0)
            if (p > 0 || c > 0) return TokenUsage(p, c)
            val t = dto.totalTokens.coerceAtLeast(0)
            return if (t > 0) TokenUsage(promptTokens = t, completionTokens = 0) else null
        }
    }
}

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: UsageDto? = null,
    val error: ApiError? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessageDto? = null,
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDeltaDto>? = null,
)

@Serializable
data class ToolCallDeltaDto(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunctionDeltaDto? = null,
)

@Serializable
data class ToolCallFunctionDeltaDto(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

data class ChatProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** Null = omit (provider default). */
    val temperature: Double? = null,
    /** Null = omit (provider default). */
    val maxTokens: Int? = null,
    /** Null = omit (provider default). Nucleus sampling 0..1. */
    val topP: Double? = null,
)
