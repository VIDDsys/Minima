package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.SamplingParams
import org.junit.Test

class SamplingParamsTest {
    @Test
    fun temperature_blankAndInvalid_omit() {
        assertThat(SamplingParams.parseTemperature(null)).isNull()
        assertThat(SamplingParams.parseTemperature("")).isNull()
        assertThat(SamplingParams.parseTemperature("  ")).isNull()
        assertThat(SamplingParams.parseTemperature("nope")).isNull()
        assertThat(SamplingParams.parseTemperature("NaN")).isNull()
        assertThat(SamplingParams.parseTemperature("Infinity")).isNull()
        assertThat(SamplingParams.parseTemperature("-Infinity")).isNull()
        assertThat(SamplingParams.parseTemperature("1.2.3")).isNull()
    }

    @Test
    fun temperature_clamps() {
        assertThat(SamplingParams.parseTemperature("0")).isEqualTo(0.0)
        assertThat(SamplingParams.parseTemperature("0.7")).isEqualTo(0.7)
        assertThat(SamplingParams.parseTemperature("2")).isEqualTo(2.0)
        assertThat(SamplingParams.parseTemperature("-1")).isEqualTo(0.0)
        assertThat(SamplingParams.parseTemperature("9")).isEqualTo(2.0)
        assertThat(SamplingParams.parseTemperature("+1.25")).isEqualTo(1.25)
    }

    @Test
    fun temperature_locale_comma_decimal() {
        // KeyboardType.Decimal on many locales inserts ',' 
        assertThat(SamplingParams.parseTemperature("0,7")).isEqualTo(0.7)
        assertThat(SamplingParams.parseTemperature("1,5")).isEqualTo(1.5)
        assertThat(SamplingParams.parseTemperature(" 0,0 ")).isEqualTo(0.0)
        // Mixed / multi-comma stays invalid
        assertThat(SamplingParams.parseTemperature("0,7,1")).isNull()
        assertThat(SamplingParams.parseTemperature("0.7,1")).isNull()
    }

    @Test
    fun maxTokens_blankZero_omit() {
        assertThat(SamplingParams.parseMaxTokens(null)).isNull()
        assertThat(SamplingParams.parseMaxTokens("")).isNull()
        assertThat(SamplingParams.parseMaxTokens("0")).isNull()
        assertThat(SamplingParams.parseMaxTokens("-3")).isNull()
        assertThat(SamplingParams.parseMaxTokens("abc")).isNull()
        assertThat(SamplingParams.parseMaxTokens("0.5")).isNull()
        assertThat(SamplingParams.parseMaxTokens("Infinity")).isNull()
    }

    @Test
    fun maxTokens_clamps() {
        assertThat(SamplingParams.parseMaxTokens("1")).isEqualTo(1)
        assertThat(SamplingParams.parseMaxTokens("4096")).isEqualTo(4096)
        assertThat(SamplingParams.parseMaxTokens("9999999")).isEqualTo(SamplingParams.MAX_TOKENS_MAX)
    }

    @Test
    fun maxTokens_decimal_and_scientific() {
        assertThat(SamplingParams.parseMaxTokens("4096.0")).isEqualTo(4096)
        assertThat(SamplingParams.parseMaxTokens("4096,0")).isEqualTo(4096)
        assertThat(SamplingParams.parseMaxTokens("1.9")).isEqualTo(1)
        assertThat(SamplingParams.parseMaxTokens("2e3")).isEqualTo(2000)
        assertThat(SamplingParams.parseMaxTokens("1e6")).isEqualTo(SamplingParams.MAX_TOKENS_MAX)
    }

    @Test
    fun format_roundtrip_friendly() {
        assertThat(SamplingParams.formatTemperature(null)).isEmpty()
        assertThat(SamplingParams.formatTemperature(1.0)).isEqualTo("1")
        assertThat(SamplingParams.formatTemperature(0.7)).isEqualTo("0.7")
        assertThat(SamplingParams.formatMaxTokens(null)).isEmpty()
        assertThat(SamplingParams.formatMaxTokens(0)).isEmpty()
        assertThat(SamplingParams.formatMaxTokens(2048)).isEqualTo("2048")
    }

    @Test
    fun clamp_helpers() {
        assertThat(SamplingParams.clampTemperature(1.5)).isEqualTo(1.5)
        assertThat(SamplingParams.clampTemperature(Double.NaN)).isNull()
        assertThat(SamplingParams.clampTemperature(Double.POSITIVE_INFINITY)).isNull()
        assertThat(SamplingParams.clampTemperature(-0.5)).isEqualTo(0.0)
        assertThat(SamplingParams.clampTemperature(3.0)).isEqualTo(2.0)
        assertThat(SamplingParams.clampMaxTokens(0)).isNull()
        assertThat(SamplingParams.clampMaxTokens(-1)).isNull()
        assertThat(SamplingParams.clampMaxTokens(100)).isEqualTo(100)
        assertThat(SamplingParams.clampMaxTokens(Int.MAX_VALUE)).isEqualTo(SamplingParams.MAX_TOKENS_MAX)
    }

    @Test
    fun topP_blankAndInvalid_omit() {
        assertThat(SamplingParams.parseTopP(null)).isNull()
        assertThat(SamplingParams.parseTopP("")).isNull()
        assertThat(SamplingParams.parseTopP("  ")).isNull()
        assertThat(SamplingParams.parseTopP("nope")).isNull()
        assertThat(SamplingParams.parseTopP("NaN")).isNull()
        assertThat(SamplingParams.parseTopP("Infinity")).isNull()
    }

    @Test
    fun topP_clamps() {
        assertThat(SamplingParams.parseTopP("0")).isEqualTo(0.0)
        assertThat(SamplingParams.parseTopP("0.9")).isEqualTo(0.9)
        assertThat(SamplingParams.parseTopP("1")).isEqualTo(1.0)
        assertThat(SamplingParams.parseTopP("-0.2")).isEqualTo(0.0)
        assertThat(SamplingParams.parseTopP("1.5")).isEqualTo(1.0)
        assertThat(SamplingParams.parseTopP("0,95")).isEqualTo(0.95)
    }

    @Test
    fun topP_clamp_helpers() {
        assertThat(SamplingParams.clampTopP(0.5)).isEqualTo(0.5)
        assertThat(SamplingParams.clampTopP(Double.NaN)).isNull()
        assertThat(SamplingParams.clampTopP(-1.0)).isEqualTo(0.0)
        assertThat(SamplingParams.clampTopP(2.0)).isEqualTo(1.0)
        assertThat(SamplingParams.formatTopP(null)).isEmpty()
        assertThat(SamplingParams.formatTopP(1.0)).isEqualTo("1")
        assertThat(SamplingParams.formatTopP(0.9)).isEqualTo("0.9")
    }

    @Test
    fun filterDecimalDraft_allows_comma() {
        assertThat(SamplingParams.filterDecimalDraft("0,7")).isEqualTo("0,7")
        assertThat(SamplingParams.filterDecimalDraft("0.7")).isEqualTo("0.7")
        assertThat(SamplingParams.filterDecimalDraft("0.7.1")).isEqualTo("0.71")
        assertThat(SamplingParams.filterDecimalDraft("abc1.2")).isEqualTo("1.2")
        // parse round-trip after locale comma draft
        assertThat(SamplingParams.parseTemperature(SamplingParams.filterDecimalDraft("0,7"))).isEqualTo(0.7)
        assertThat(SamplingParams.parseTopP(SamplingParams.filterDecimalDraft("0,9"))).isEqualTo(0.9)
    }
}
