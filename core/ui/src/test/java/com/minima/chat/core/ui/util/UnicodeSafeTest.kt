package com.minima.chat.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UnicodeSafeTest {
    @Test
    fun sanitizesUnpairedHighSurrogate() {
        val unpaired = Character.MIN_HIGH_SURROGATE
        val s = UnicodeSafe.sanitize(String(charArrayOf('a', unpaired, 'b')))
        assertEquals(3, s.length)
        assertEquals('\uFFFD', s[1])
        assertEquals('a', s[0])
        assertEquals('b', s[2])
    }

    @Test
    fun keepsValidEmojiPair() {
        val grinning = String(Character.toChars(0x1F600))
        val out = UnicodeSafe.sanitize("x" + grinning + "y")
        assertEquals("x" + grinning + "y", out)
    }

    @Test
    fun takeIsCodePointSafeForEmoji() {
        val grinning = String(Character.toChars(0x1F600))
        val emoji = "hi $grinning!"
        val t = UnicodeSafe.take(emoji, 4)
        assertEquals(4, t.codePointCount(0, t.length))
        assertFalse(t.isNotEmpty() && t.last().isHighSurrogate())
    }

    @Test
    fun nullAndEmpty() {
        assertEquals("", UnicodeSafe.sanitize(null))
        assertEquals("", UnicodeSafe.take("", 10))
    }
}
