package com.minima.chat.core.ui.util

/**
 * Suppress duplicate toast/snackbar spam within a short window.
 */
class ToastRateLimiter(
    private val windowMs: Long = 2_500L,
) {
    private var lastMessage: String? = null
    private var lastAt: Long = 0L

    /** Returns true if this message should be shown. */
    fun shouldShow(message: String, now: Long = System.currentTimeMillis()): Boolean {
        val same = message == lastMessage
        if (same && now - lastAt < windowMs) return false
        lastMessage = message
        lastAt = now
        return true
    }

    fun reset() {
        lastMessage = null
        lastAt = 0L
    }
}
