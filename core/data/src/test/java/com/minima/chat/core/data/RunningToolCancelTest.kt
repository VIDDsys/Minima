package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.util.RunningToolCancel
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RunningToolCancelTest {
    @Test
    fun ceilingKeepsNewerTools() {
        val ceiling = 1_000L
        assertThat(RunningToolCancel.shouldCancel(999, ceiling)).isTrue()
        assertThat(RunningToolCancel.shouldCancel(1_000, ceiling)).isTrue()
        assertThat(RunningToolCancel.shouldCancel(1_001, ceiling)).isFalse()
    }

    @Test
    fun maxCeilingCancelsAll() {
        assertThat(RunningToolCancel.shouldCancel(Long.MAX_VALUE, Long.MAX_VALUE)).isTrue()
        assertThat(RunningToolCancel.shouldCancel(0, Long.MAX_VALUE)).isTrue()
    }

    @Test
    fun nonAgentSystemPrompt_onlyWhenNonBlank() {
        assertThat(RunningToolCancel.nonAgentSystemPrompt("")).isNull()
        assertThat(RunningToolCancel.nonAgentSystemPrompt("  ")).isNull()
        assertThat(RunningToolCancel.nonAgentSystemPrompt(" Be terse ")).isEqualTo("Be terse")
    }

    @Test
    fun runCatchingExceptCancel_rethrowsCancellation() {
        try {
            RunningToolCancel.runCatchingExceptCancel<Unit> {
                throw CancellationException("stop")
            }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("stop")
        }
    }

    @Test
    fun runCatchingExceptCancel_capturesOtherFailures() {
        val r = RunningToolCancel.runCatchingExceptCancel<Unit> {
            error("boom")
        }
        assertThat(r.isFailure).isTrue()
        assertThat(r.exceptionOrNull()?.message).contains("boom")
    }
}
