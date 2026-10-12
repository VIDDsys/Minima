package com.minima.chat.core.network.api

import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ImageAttachmentDto
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolDefinitionDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Encodes chat messages for OpenAI-compatible APIs, including multimodal
 * user content (`text` + `image_url` parts) when [ChatMessageDto.images] is set.
 */
object MultimodalContent {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encodeMessages(messages: List<ChatMessageDto>): JsonArray = buildJsonArray {
        messages.forEach { add(encodeMessage(it)) }
    }

    fun encodeMessage(msg: ChatMessageDto): JsonObject = buildJsonObject {
        put("role", msg.role)
        val images = msg.images.filter { it.isSendable() }
        when {
            images.isNotEmpty() -> {
                put(
                    "content",
                    buildJsonArray {
                        val text = msg.content
                        if (!text.isNullOrEmpty()) {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", text)
                                },
                            )
                        }
                        for (img in images) {
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    put(
                                        "image_url",
                                        buildJsonObject {
                                            put("url", img.dataUrl)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
            msg.content != null -> put("content", msg.content)
        }
        msg.toolCalls?.takeIf { it.isNotEmpty() }?.let { calls ->
            put("tool_calls", json.encodeToJsonElement(ListSerializer(ToolCallDto.serializer()), calls))
        }
        msg.toolCallId?.let { put("tool_call_id", it) }
        msg.name?.let { put("name", it) }
    }

    fun encodeRequestBody(
        model: String,
        messages: List<ChatMessageDto>,
        stream: Boolean,
        tools: List<ToolDefinitionDto>?,
        includeStreamUsage: Boolean,
        temperature: Double? = null,
        maxTokens: Int? = null,
        topP: Double? = null,
    ): String {
        val root = buildJsonObject {
            put("model", model)
            put("messages", encodeMessages(messages))
            put("stream", stream)
            // Defense-in-depth: never emit NaN/Inf/out-of-range even if callers skip AppSettings clamps.
            SamplingParams.clampTemperature(temperature)?.let { put("temperature", it) }
            SamplingParams.clampMaxTokens(maxTokens)?.let { put("max_tokens", it) }
            SamplingParams.clampTopP(topP)?.let { put("top_p", it) }
            if (!tools.isNullOrEmpty()) {
                put(
                    "tools",
                    json.encodeToJsonElement(ListSerializer(ToolDefinitionDto.serializer()), tools),
                )
                put("tool_choice", "auto")
            }
            if (stream && includeStreamUsage) {
                put(
                    "stream_options",
                    buildJsonObject { put("include_usage", true) },
                )
            }
        }
        return root.toString()
    }

    /** Pure helper for tests / callers building a single multimodal user message. */
    fun userWithImages(text: String?, images: List<ImageAttachmentDto>): ChatMessageDto =
        ChatMessageDto(
            role = "user",
            content = text?.takeIf { it.isNotBlank() },
            images = images.filter { it.isSendable() },
        )
}
