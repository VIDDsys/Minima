package com.minima.chat.core.agent

import com.minima.chat.core.agent.loop.AgentBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBudgetTest {
    @Test
    fun defaultsIncludeToolTimeoutAndDeadline() {
        val b = AgentBudget()
        assertEquals(12, b.maxIterations)
        assertEquals(180_000L, b.wallClockMs)
        assertEquals(60_000L, b.toolTimeoutMs)
        assertTrue(b.toolTimeoutMs < b.wallClockMs)
    }

    @Test
    fun customTimeouts() {
        val b = AgentBudget(maxIterations = 3, wallClockMs = 5_000, toolTimeoutMs = 1_000)
        assertEquals(3, b.maxIterations)
        assertEquals(1_000L, b.toolTimeoutMs)
    }

    @Test
    fun fromSettingsSeconds() {
        // Document ChatViewModel wiring: sec → ms
        val wallSec = 240
        val toolSec = 90
        val b = AgentBudget(
            maxIterations = 16,
            wallClockMs = wallSec * 1_000L,
            toolTimeoutMs = toolSec * 1_000L,
        )
        assertEquals(240_000L, b.wallClockMs)
        assertEquals(90_000L, b.toolTimeoutMs)
        assertTrue(b.toolTimeoutMs < b.wallClockMs)
    }
}
