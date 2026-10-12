package com.minima.chat.core.agent

import com.minima.chat.core.agent.run.ChatRunGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRunGateTest {
    @Test
    fun doubleBeginRejected() {
        val g = ChatRunGate()
        val a = g.tryBegin()
        assertNotNull(a)
        assertNull(g.tryBegin())
        assertTrue(g.isActive())
        g.finish(a!!)
        assertFalse(g.isActive())
        assertNotNull(g.tryBegin())
    }

    @Test
    fun finishStaleGenDoesNotClearNewerRun() {
        val g = ChatRunGate()
        val old = g.tryBegin()!!
        g.cancelAll()
        val neu = g.tryBegin()!!
        g.finish(old) // stale
        assertTrue(g.isActive())
        g.finish(neu)
        assertFalse(g.isActive())
    }

    @Test
    fun cancelAllAllowsImmediateRebegin() {
        val g = ChatRunGate()
        val a = g.tryBegin()!!
        g.cancelAll()
        val b = g.tryBegin()
        assertNotNull(b)
        assertTrue(b!! > a)
    }

    @Test
    fun finishWrongGenIgnoredWhenStillSame() {
        val g = ChatRunGate()
        val a = g.tryBegin()!!
        g.finish(a - 1)
        assertTrue(g.isActive())
        g.finish(a)
        assertFalse(g.isActive())
        assertEquals(false, g.isActive())
    }

    @Test
    fun stopHoldBlocksTryBeginUntilEnd() {
        val g = ChatRunGate()
        val a = g.tryBegin()!!
        g.beginStopHold()
        assertTrue(g.isStopHeld())
        assertTrue(g.isActive())
        assertNull(g.tryBegin())
        g.finish(a) // stale after beginStopHold bumped generation
        assertTrue(g.isActive())
        g.endStopHold()
        assertFalse(g.isStopHeld())
        assertFalse(g.isActive())
        assertNotNull(g.tryBegin())
    }

    @Test
    fun cancelAllClearsStopHold() {
        val g = ChatRunGate()
        g.beginStopHold()
        assertTrue(g.isStopHeld())
        g.cancelAll()
        assertFalse(g.isStopHeld())
        assertFalse(g.isActive())
        g.endStopHold() // idempotent
        assertFalse(g.isActive())
    }
}
