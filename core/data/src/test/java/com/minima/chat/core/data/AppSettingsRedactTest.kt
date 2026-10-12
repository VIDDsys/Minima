package com.minima.chat.core.data

import com.minima.chat.core.data.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsRedactTest {
    @Test
    fun toStringHidesApiKey() {
        val s = AppSettings(apiKey = "sk-super-secret-key-value-12345")
        val text = s.toString()
        assertFalse(text.contains("sk-super-secret"))
        assertTrue(text.contains("<redacted>") || text.contains("redacted"))
    }

    @Test
    fun emptyKeyMarked() {
        val text = AppSettings().toString()
        assertTrue(text.contains("<empty>"))
    }
}
