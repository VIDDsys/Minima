package com.minima.chat.core.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Exponential backoff for transient HTTP failures (429 / 5xx).
 * Pure helpers — easy to unit test without OkHttp.
 */
object HttpRetry {
    const val DEFAULT_MAX_ATTEMPTS = 3
    const val DEFAULT_INITIAL_DELAY_MS = 400L
    const val DEFAULT_MAX_DELAY_MS = 4_000L

    fun isTransientStatus(code: Int): Boolean =
        code == 429 || code in 500..599

    fun isTransientMessage(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("rate limited") ||
            m.contains("(429)") ||
            m.contains("http 429") ||
            Regex("""server error \(5\d\d\)""").containsMatchIn(m) ||
            Regex("""http 5\d\d""").containsMatchIn(m)
    }

    /** Delay before attempt index (0-based retry count after first failure). */
    fun backoffDelayMs(
        attemptAfterFailure: Int,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    ): Long {
        val exp = initialDelayMs * 2.0.pow(attemptAfterFailure.coerceAtLeast(0).toDouble())
        return min(exp.toLong(), maxDelayMs)
    }

    suspend fun <T> withBackoff(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        isRetryable: (Throwable) -> Boolean,
        block: suspend (attempt: Int) -> T,
    ): T {
        var last: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
                val more = attempt < maxAttempts - 1 && isRetryable(e)
                if (!more) throw e
                delay(backoffDelayMs(attempt, initialDelayMs, maxDelayMs))
            }
        }
        throw last ?: IllegalStateException("Retry exhausted")
    }
}
