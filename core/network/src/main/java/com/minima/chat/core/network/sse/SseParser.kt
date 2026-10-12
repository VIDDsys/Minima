package com.minima.chat.core.network.sse

import com.minima.chat.core.network.dto.ChatCompletionResponse
import com.minima.chat.core.network.dto.ToolCallDeltaDto
import com.minima.chat.core.network.dto.UsageDto
import kotlinx.serialization.json.Json

data class SseChunk(
    val content: String? = null,
    val toolCalls: List<ToolCallDeltaDto>? = null,
    val finishReason: String? = null,
    val usage: UsageDto? = null,
)

object SseParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Parse one SSE `data:` payload line (without the `data:` prefix).
     * Returns content delta, or null if [DONE] / empty / non-content.
     */
    fun parseDataLine(data: String): String? = parseChunk(data)?.content

    fun parseChunk(data: String): SseChunk? {
        val trimmed = data.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return null
        return try {
            val resp = json.decodeFromString(ChatCompletionResponse.serializer(), trimmed)
            val choice = resp.choices.firstOrNull()
            val delta = choice?.delta
            val content = delta?.content ?: choice?.message?.content
            val toolCalls = delta?.toolCalls ?: choice?.message?.toolCalls?.mapIndexed { i, tc ->
                ToolCallDeltaDto(
                    index = i,
                    id = tc.id,
                    type = tc.type,
                    function = com.minima.chat.core.network.dto.ToolCallFunctionDeltaDto(
                        name = tc.function.name,
                        arguments = tc.function.arguments,
                    ),
                )
            }
            SseChunk(
                content = content,
                toolCalls = toolCalls,
                finishReason = choice?.finishReason,
                usage = resp.usage,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun extractContentDeltas(sseBody: String): String {
        val sb = StringBuilder()
        for (line in sseBody.lineSequence()) {
            when {
                line.startsWith("data:") -> {
                    val payload = line.removePrefix("data:").trim()
                    parseDataLine(payload)?.let { sb.append(it) }
                }
            }
        }
        return sb.toString()
    }
}
