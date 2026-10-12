package com.minima.chat.core.data

import com.minima.chat.core.data.util.SessionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTitleTest {
    @Test
    fun blankBecomesUntitled() {
        assertEquals(SessionTitle.DEFAULT, SessionTitle.normalize(""))
        assertEquals(SessionTitle.DEFAULT, SessionTitle.normalize("   "))
        assertEquals(SessionTitle.DEFAULT, SessionTitle.normalize("\n\t"))
        assertEquals(SessionTitle.DEFAULT, SessionTitle.normalize(null))
    }

    @Test
    fun stripsControlsAndCollapsesSpaces() {
        assertEquals("Hello world", SessionTitle.normalize("  Hello\u0000  world\n"))
        assertEquals("A B", SessionTitle.normalize("A\t\tB"))
    }

    @Test
    fun clampsLength() {
        val long = "x".repeat(200)
        val out = SessionTitle.normalize(long)
        assertTrue(out.length <= SessionTitle.MAX_LEN)
        assertEquals("x".repeat(SessionTitle.MAX_LEN), out)
    }

    @Test
    fun rejectsApiKeyLookingTitles() {
        assertEquals(SessionTitle.DEFAULT, SessionTitle.normalize("sk-abcdefghijklmnopqrstuv"))
        assertEquals(SessionTitle.DEFAULT, SessionTitle.normalize("sk-proj-abcdefghijklmnop"))
    }

    @Test
    fun keepsNormalTitles() {
        assertEquals("Plan lunch", SessionTitle.normalize("Plan lunch"))
        assertEquals("中文标题", SessionTitle.normalize("  中文标题  "))
    }
}
