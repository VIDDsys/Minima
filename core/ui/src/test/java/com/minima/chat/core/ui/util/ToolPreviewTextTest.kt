package com.minima.chat.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPreviewTextTest {
    @Test
    fun shortText_notTruncated() {
        val s = ToolPreviewText.slice("hello", 800)
        assertEquals("hello", s.text)
        assertFalse(s.truncated)
        assertNull(ToolPreviewText.truncationHint(s))
    }

    @Test
    fun longText_truncatedWithHint() {
        val raw = "a".repeat(1000)
        val s = ToolPreviewText.slice(raw, 800)
        assertTrue(s.truncated)
        assertTrue(s.text.endsWith("…"))
        assertEquals(1000, s.originalCodePoints)
        assertEquals("truncated · 1000 chars", ToolPreviewText.truncationHint(s))
    }

    @Test
    fun emoji_codePointSafe() {
        val emoji = "😀".repeat(10)
        val s = ToolPreviewText.slice(emoji, 4)
        assertTrue(s.truncated)
        assertEquals(10, s.originalCodePoints)
        // 4 emoji + ellipsis
        assertTrue(s.text.codePointCount(0, s.text.length) >= 4)
    }

    @Test
    fun nullAndBlank() {
        assertEquals("", ToolPreviewText.slice(null, 10).text)
        assertFalse(ToolPreviewText.slice("", 10).truncated)
    }
}
