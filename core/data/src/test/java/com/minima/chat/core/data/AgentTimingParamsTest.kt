package com.minima.chat.core.data

import com.minima.chat.core.data.model.AgentTimingParams
import com.minima.chat.core.data.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentTimingParamsTest {
    @Test
    fun wallClock_clamps() {
        assertEquals(30, AgentTimingParams.clampWallClockSec(1))
        assertEquals(180, AgentTimingParams.clampWallClockSec(180))
        assertEquals(900, AgentTimingParams.clampWallClockSec(9999))
    }

    @Test
    fun toolTimeout_clampsAndRespectsWall() {
        assertEquals(5, AgentTimingParams.clampToolTimeoutSec(1, wallClockSec = 180))
        assertEquals(60, AgentTimingParams.clampToolTimeoutSec(60, wallClockSec = 180))
        assertEquals(300, AgentTimingParams.clampToolTimeoutSec(999, wallClockSec = 900))
        // Never above wall clock
        assertEquals(45, AgentTimingParams.clampToolTimeoutSec(120, wallClockSec = 45))
        assertEquals(30, AgentTimingParams.clampToolTimeoutSec(60, wallClockSec = 1)) // wall floors to 30
    }

    @Test
    fun appSettings_clampedGetters() {
        val s = AppSettings(agentWallClockSec = 45, agentToolTimeoutSec = 120)
        assertEquals(45, s.clampedWallClockSec)
        assertEquals(45, s.clampedToolTimeoutSec) // capped to wall
        val defaults = AppSettings()
        assertEquals(600, defaults.clampedWallClockSec)
        assertEquals(60, defaults.clampedToolTimeoutSec)
    }
}
