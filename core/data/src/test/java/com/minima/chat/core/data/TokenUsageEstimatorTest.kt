package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.util.TokenUsageEstimator
import org.junit.Test

class TokenUsageEstimatorTest {
    @Test
    fun empty_is_zero() {
        assertThat(TokenUsageEstimator.estimateTokens("")).isEqualTo(0)
    }

    @Test
    fun four_chars_one_token() {
        assertThat(TokenUsageEstimator.estimateTokens("abcd")).isEqualTo(1)
        assertThat(TokenUsageEstimator.estimateTokens("abcde")).isEqualTo(2)
    }

    @Test
    fun session_sums() {
        assertThat(TokenUsageEstimator.estimateSession(listOf("abcd", "efgh"))).isEqualTo(2)
    }

    @Test
    fun format_and_subtitle() {
        assertThat(TokenUsageEstimator.formatEstimate(12)).isEqualTo("12")
        assertThat(TokenUsageEstimator.subtitle(1200)).contains("est. tokens")
        assertThat(TokenUsageEstimator.formatEstimate(1500)).isEqualTo("1.5k")
    }

    @Test
    fun sessionSubtitle_prefers_billed_over_estimate() {
        val billed = TokenUsageEstimator.sessionSubtitle(
            billedPairs = listOf(100 to 50, 0 to 0),
            textsForEstimate = listOf("abcd"), // would be 1 est token
        )
        assertThat(billed).contains("150 tokens")
        assertThat(billed).contains("100↑")
        assertThat(billed).contains("50↓")
        assertThat(billed).doesNotContain("est.")
    }

    @Test
    fun sessionSubtitle_falls_back_to_estimate() {
        val est = TokenUsageEstimator.sessionSubtitle(
            billedPairs = listOf(0 to 0),
            textsForEstimate = listOf("abcd"),
        )
        assertThat(est).contains("est. tokens")
        assertThat(est).contains("~1")
    }
}
