package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.util.ToolResultLimiter
import org.junit.Test

class ToolResultLimiterTest {
    @Test
    fun short_unchanged() {
        assertThat(ToolResultLimiter.forLlm("hello")).isEqualTo("hello")
    }

    @Test
    fun blank_becomesEmptyMarker() {
        assertThat(ToolResultLimiter.forLlm("")).isEqualTo("(empty tool result)")
        assertThat(ToolResultLimiter.forLlm("   ")).isEqualTo("(empty tool result)")
    }

    @Test
    fun huge_truncatedWithMarkerUnderCap() {
        val huge = "x".repeat(ToolResultLimiter.MAX_CHARS + 2_000)
        val out = ToolResultLimiter.forLlm(huge)
        assertThat(out.length).isAtMost(ToolResultLimiter.MAX_CHARS)
        assertThat(ToolResultLimiter.isTruncated(out)).isTrue()
        assertThat(out).contains("of ${huge.length} chars")
        assertThat(out).startsWith("x")
    }

    @Test
    fun customCap_respected() {
        val out = ToolResultLimiter.forLlm("abcdefghij", maxChars = 40)
        // short enough
        assertThat(out).isEqualTo("abcdefghij")
        val big = ToolResultLimiter.forLlm("y".repeat(200), maxChars = 80)
        assertThat(big.length).isAtMost(80)
        assertThat(ToolResultLimiter.isTruncated(big)).isTrue()
    }

    @Test
    fun zeroCap_returnsEmptyMarker() {
        assertThat(ToolResultLimiter.forLlm("abc", maxChars = 0)).isEqualTo("(empty tool result)")
    }
}
