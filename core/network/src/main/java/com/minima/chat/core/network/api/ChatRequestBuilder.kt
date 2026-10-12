package com.minima.chat.core.network.api

import com.minima.chat.core.network.dto.ChatCompletionRequest
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.ToolDefinitionDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ChatRequestBuilder {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun normalizeBaseUrl(baseUrl: String): String {
        var u = baseUrl.trim().trimEnd('/')
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    fun chatCompletionsUrl(baseUrl: String): String {
        val base = normalizeBaseUrl(baseUrl)
        return when {
            base.isEmpty() -> ""
            base.endsWith("/v1") -> "$base/chat/completions"
            base.endsWith("/chat/completions") -> base
            else -> "$base/v1/chat/completions"
        }
    }

    fun build(
        config: ChatProviderConfig,
        messages: List<ChatMessageDto>,
        stream: Boolean,
        tools: List<ToolDefinitionDto>? = null,
    ): Request {
        val url = chatCompletionsUrl(config.baseUrl)
        require(url.isNotEmpty()) { "Base URL is empty" }
        CleartextPolicy.rejectDisallowedCleartext(config.baseUrl)?.let { throw IllegalArgumentException(it) }
        require(config.apiKey.isNotBlank()) { "API key is empty" }
        require(config.model.isNotBlank()) { "Model is empty" }

        // Always encode via MultimodalContent so image_url parts work when present.
        val payload = MultimodalContent.encodeRequestBody(
            model = config.model.trim(),
            messages = messages,
            stream = stream,
            tools = tools?.takeIf { it.isNotEmpty() },
            includeStreamUsage = stream,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
        )
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", if (stream) "text/event-stream" else "application/json")
            .post(payload.toRequestBody(mediaType))
            .build()
    }

    fun encode(request: ChatCompletionRequest): String = json.encodeToString(request)

    fun decodeResponse(raw: String) = json.decodeFromString(
        com.minima.chat.core.network.dto.ChatCompletionResponse.serializer(),
        raw,
    )
}
