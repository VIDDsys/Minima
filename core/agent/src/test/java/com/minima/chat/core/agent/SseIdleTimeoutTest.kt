package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.loop.AgentLoop
import org.junit.Test

class SseIdleTimeoutTest {
    @Test
    fun idleTimeoutIsFiniteAndReasonable() {
        assertThat(AgentLoop.SSE_IDLE_TIMEOUT_MS).isAtLeast(30_000L)
        assertThat(AgentLoop.SSE_IDLE_TIMEOUT_MS).isAtMost(180_000L)
    }
}
