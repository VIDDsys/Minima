package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Contract: tool / HTTP layers must rethrow CancellationException so
 * withTimeout and Stop work. Catching Exception alone swallows CE
 * (CE extends IllegalStateException → Exception on JVM).
 */
class CancellationPropagationTest {
    @Test
    fun catchExceptionAloneSwallowsCancellation() {
        val swallowed = try {
            try {
                throw CancellationException("x")
            } catch (e: Exception) {
                "swallowed:${e.message}"
            }
        } catch (_: CancellationException) {
            "propagated"
        }
        assertThat(swallowed).isEqualTo("swallowed:x")
    }

    @Test
    fun catchCancellationFirstPropagates() {
        val outcome = try {
            try {
                throw CancellationException("stop")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "swallowed"
            }
        } catch (e: CancellationException) {
            "propagated:${e.message}"
        }
        assertThat(outcome).isEqualTo("propagated:stop")
    }

    @Test
    fun runBlockingSeesPropagation() = runBlocking {
        try {
            try {
                throw CancellationException("from-tool")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error("should not swallow")
            }
            error("unreachable")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("from-tool")
        }
    }
}
