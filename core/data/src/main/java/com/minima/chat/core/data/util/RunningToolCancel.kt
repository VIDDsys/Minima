package com.minima.chat.core.data.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Pure helpers for Stop / onCleared tool cleanup and cancel-safe I/O.
 * Tools created after [createdAtCeilingMs] belong to a newer run and must stay.
 */
object RunningToolCancel {
    fun shouldCancel(toolCreatedAtMs: Long, createdAtCeilingMs: Long): Boolean =
        toolCreatedAtMs <= createdAtCeilingMs

    /** Non-agent path: custom system prompt only when non-blank (mirrors sendAndStream). */
    fun nonAgentSystemPrompt(systemPrompt: String): String? =
        systemPrompt.trim().takeIf { it.isNotEmpty() }

    /**
     * Like [runCatching] but never swallows [CancellationException].
     * Use inside agent collect loops (and tests) so Stop can abort cleanly.
     */
    inline fun <T> runCatchingExceptCancel(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    /** Suspend variant for Room / network calls in the agent collect loop. */
    suspend fun <T> runCatchingExceptCancelSuspending(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
}
