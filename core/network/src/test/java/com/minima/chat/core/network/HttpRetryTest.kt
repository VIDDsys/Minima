package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.HttpRetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HttpRetryTest {
    @Test
    fun transientStatuses() {
        assertThat(HttpRetry.isTransientStatus(429)).isTrue()
        assertThat(HttpRetry.isTransientStatus(500)).isTrue()
        assertThat(HttpRetry.isTransientStatus(503)).isTrue()
        assertThat(HttpRetry.isTransientStatus(401)).isFalse()
        assertThat(HttpRetry.isTransientStatus(404)).isFalse()
    }

    @Test
    fun transientMessages() {
        assertThat(HttpRetry.isTransientMessage("Rate limited (429) — wait and retry")).isTrue()
        assertThat(HttpRetry.isTransientMessage("Server error (502)")).isTrue()
        assertThat(HttpRetry.isTransientMessage("HTTP 503")).isTrue()
        assertThat(HttpRetry.isTransientMessage("Unauthorized (401)")).isFalse()
    }

    @Test
    fun backoffGrowsThenCaps() {
        val d0 = HttpRetry.backoffDelayMs(0, initialDelayMs = 100, maxDelayMs = 1000)
        val d1 = HttpRetry.backoffDelayMs(1, initialDelayMs = 100, maxDelayMs = 1000)
        val d2 = HttpRetry.backoffDelayMs(2, initialDelayMs = 100, maxDelayMs = 1000)
        val d5 = HttpRetry.backoffDelayMs(5, initialDelayMs = 100, maxDelayMs = 1000)
        assertThat(d0).isEqualTo(100)
        assertThat(d1).isEqualTo(200)
        assertThat(d2).isEqualTo(400)
        assertThat(d5).isEqualTo(1000)
    }

    @Test
    fun withBackoffRetriesThenSucceeds() = runBlocking {
        var attempts = 0
        val result = HttpRetry.withBackoff(
            maxAttempts = 3,
            initialDelayMs = 1,
            maxDelayMs = 2,
            isRetryable = { true },
        ) {
            attempts++
            if (attempts < 3) error("Rate limited (429)")
            "ok"
        }
        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun withBackoffDoesNotRetryPermanent() = runBlocking {
        var attempts = 0
        try {
            HttpRetry.withBackoff(
                maxAttempts = 3,
                initialDelayMs = 1,
                isRetryable = { HttpRetry.isTransientMessage(it.message.orEmpty()) },
            ) {
                attempts++
                error("Unauthorized (401)")
            }
            error("should throw")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("401")
        }
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun withBackoffPropagatesCancellation() = runBlocking {
        var attempts = 0
        try {
            HttpRetry.withBackoff(
                maxAttempts = 5,
                initialDelayMs = 1,
                isRetryable = { true },
            ) {
                attempts++
                throw CancellationException("stop")
            }
            error("should throw")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("stop")
        }
        assertThat(attempts).isEqualTo(1)
    }
}
