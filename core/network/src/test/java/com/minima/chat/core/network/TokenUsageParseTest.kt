package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.dto.TokenUsage
import com.minima.chat.core.network.dto.UsageDto
import com.minima.chat.core.network.sse.SseParser
import org.junit.Test

class TokenUsageParseTest {
    @Test
    fun from_usageDto_prompt_and_completion() {
        val u = TokenUsage.from(UsageDto(promptTokens = 12, completionTokens = 34, totalTokens = 46))
        assertThat(u).isEqualTo(TokenUsage(12, 34))
        assertThat(u!!.total).isEqualTo(46)
    }

    @Test
    fun from_total_only_fallback() {
        val u = TokenUsage.from(UsageDto(totalTokens = 99))
        assertThat(u).isEqualTo(TokenUsage(promptTokens = 99, completionTokens = 0))
    }

    @Test
    fun from_null_or_zero() {
        assertThat(TokenUsage.from(null)).isNull()
        assertThat(TokenUsage.from(UsageDto())).isNull()
    }

    @Test
    fun plus_accumulates() {
        assertThat(TokenUsage(1, 2) + TokenUsage(3, 4)).isEqualTo(TokenUsage(4, 6))
    }

    @Test
    fun sseParser_extracts_usage_chunk() {
        val data = """{"id":"x","choices":[],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}"""
        val chunk = SseParser.parseChunk(data)
        assertThat(chunk).isNotNull()
        assertThat(chunk!!.content).isNull()
        assertThat(chunk.usage?.promptTokens).isEqualTo(10)
        assertThat(chunk.usage?.completionTokens).isEqualTo(20)
        assertThat(TokenUsage.from(chunk.usage)).isEqualTo(TokenUsage(10, 20))
    }

    @Test
    fun sseParser_usage_on_final_content_chunk() {
        val data = """{"choices":[{"delta":{"content":"hi"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1,"total_tokens":6}}"""
        val chunk = SseParser.parseChunk(data)!!
        assertThat(chunk.content).isEqualTo("hi")
        assertThat(chunk.usage?.promptTokens).isEqualTo(5)
    }
}
