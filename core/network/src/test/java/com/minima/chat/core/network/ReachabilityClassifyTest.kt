package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.ReachabilityClassify
import com.minima.chat.core.network.api.SecretRedact
import org.junit.Test

class ReachabilityClassifyTest {
    @Test
    fun blankUrl() {
        val r = ReachabilityClassify.fromBlankUrl()
        assertThat(r.ok).isFalse()
        assertThat(r.message).contains("Base URL")
    }

    @Test
    fun probeAppendsModels() {
        assertThat(ReachabilityClassify.probeUrl("https://api.openai.com/v1/"))
            .isEqualTo("https://api.openai.com/v1/models")
        assertThat(ReachabilityClassify.probeUrl("https://api.openai.com/v1"))
            .isEqualTo("https://api.openai.com/v1/models")
    }

    @Test
    fun httpAuthChallengeStillReachable() {
        val r = ReachabilityClassify.fromHttp(401, "api.openai.com")
        assertThat(r.ok).isTrue()
        assertThat(r.message).contains("401")
    }

    @Test
    fun httpNotFoundStillReachable() {
        assertThat(ReachabilityClassify.fromHttp(404, "example.com").ok).isTrue()
    }

    @Test
    fun httpOkReachable() {
        assertThat(ReachabilityClassify.fromHttp(200, "example.com").ok).isTrue()
    }

    @Test
    fun httpServerErrorNotOk() {
        assertThat(ReachabilityClassify.fromHttp(502, "example.com").ok).isFalse()
    }

    @Test
    fun ioMessageTruncated() {
        val long = "x".repeat(200)
        val r = ReachabilityClassify.fromIo(long)
        assertThat(r.ok).isFalse()
        assertThat(r.message.length).isAtMost(120)
    }

    @Test
    fun ioMessageRedactsSkKeyInUrl() {
        val r = ReachabilityClassify.fromIo(
            "Failed to connect to https://proxy.example/v1?api_key=sk-abcdefghijklmnopqrstuv",
        )
        assertThat(r.message).doesNotContain("sk-abcdefghijklmnopqrstuv")
        assertThat(r.message.lowercase()).contains("api")
    }

    @Test
    fun invalidUrlRedactsBearer() {
        val r = ReachabilityClassify.fromInvalidUrl(
            "Unexpected char in Authorization: Bearer sk-abcdefghijklmnopqrstuv",
        )
        assertThat(r.message).doesNotContain("sk-abcdefghijklmnopqrstuv")
    }

    @Test
    fun secretRedactQueryParam() {
        val raw = "https://x.com/v1?api_key=sk-supersecretkeyvalue99&q=1"
        val out = SecretRedact.redact(raw)
        assertThat(out).doesNotContain("sk-supersecretkeyvalue99")
        assertThat(out).contains("api_key=***")
    }
}
