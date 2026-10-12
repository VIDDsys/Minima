package com.minima.chat.core.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToastRateLimiterTest {
    @Test
    fun suppressesDuplicateWithinWindow() {
        val lim = ToastRateLimiter(1000)
        assertTrue(lim.shouldShow("err", 1000))
        assertFalse(lim.shouldShow("err", 1500))
        assertTrue(lim.shouldShow("err", 2100))
    }

    @Test
    fun allowsDifferentMessage() {
        val lim = ToastRateLimiter(1000)
        assertTrue(lim.shouldShow("a", 1000))
        assertTrue(lim.shouldShow("b", 1100))
    }
}
