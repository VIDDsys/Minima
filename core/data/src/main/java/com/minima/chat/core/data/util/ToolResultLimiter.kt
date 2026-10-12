package com.minima.chat.core.data.util

/**
 * Caps tool outputs fed back into the LLM (live AgentLoop + history replay).
 * Prevents a single huge MCP / fetch / read from blowing the next request context.
 */
object ToolResultLimiter {
    /** Soft ceiling aligned with historical [LlmHistoryMapper.MAX_TOOL_PAYLOAD_CHARS]. */
    const val MAX_CHARS = 32_768

    private const val EMPTY = "(empty tool result)"

    /**
     * Returns [output] unchanged when within [maxChars]; otherwise a prefix plus a
     * clear truncation marker. Total length is always ≤ [maxChars] (when maxChars > 0).
     */
    fun forLlm(output: String, maxChars: Int = MAX_CHARS): String {
        val body = output.ifBlank { EMPTY }
        if (maxChars <= 0) return EMPTY
        if (body.length <= maxChars) return body

        val original = body.length
        // Reserve room for marker; numbers grow with size so pad generously.
        val reserve = 96.coerceAtMost(maxChars)
        var keep = (maxChars - reserve).coerceAtLeast(0)
        var truncated = body.take(keep)
        var suffix = marker(truncated.length, original)
        var result = truncated + suffix
        if (result.length > maxChars) {
            keep = (maxChars - suffix.length).coerceAtLeast(0)
            truncated = body.take(keep)
            suffix = marker(truncated.length, original)
            result = truncated + suffix
            if (result.length > maxChars) {
                result = result.take(maxChars)
            }
        }
        return result
    }

    fun isTruncated(text: String): Boolean =
        text.contains("[truncated tool result:", ignoreCase = false)

    private fun marker(kept: Int, original: Int): String =
        "\n\n[truncated tool result: showing first $kept of $original chars]"
}
