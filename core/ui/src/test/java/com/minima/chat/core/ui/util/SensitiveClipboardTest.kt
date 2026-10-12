package com.minima.chat.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveClipboardTest {
    @Test
    fun redactsSkKeys() {
        val raw = "Authorization Bearer sk-abcDEF1234567890xyz"
        val out = SensitiveClipboard.redact(raw)
        assertFalse(out.contains("sk-abcDEF1234567890xyz"))
        assertTrue(out.contains("***") || out.contains("…"))
    }

    @Test
    fun leavesNormalText() {
        assertEquals("hello world", SensitiveClipboard.redact("hello world"))
    }

    @Test
    fun detectsApiKeyShape() {
        assertTrue(SensitiveClipboard.looksLikeApiKey("sk-abcdefghijklmnopqrstuvwxyz"))
        assertFalse(SensitiveClipboard.looksLikeApiKey("hi"))
    }

    @Test
    fun redactsAuthorizationHeader() {
        val raw = "Authorization: Bearer sk-abcdefghijklmnopqrstuv"
        val out = SensitiveClipboard.redact(raw)
        assertFalse(out.contains("sk-abcdefghijklmnopqrstuv"))
    }

    @Test
    fun redactsApiKeyAssignment() {
        val raw = "api_key=sk-abcdefghijklmnopqrstuv"
        val out = SensitiveClipboard.redact(raw)
        assertFalse(out.contains("sk-abcdefghijklmnopqrstuv"))
    }


    @Test
    fun containsSecret_findsEmbeddedKey() {
        val para = "please use sk-abcdefghijklmnopqrstuvwxyz0123 tomorrow"
        assertTrue(SensitiveClipboard.containsSecret(para))
        assertFalse(SensitiveClipboard.looksLikeApiKey(para)) // whole-string shape only
        val redacted = SensitiveClipboard.redact(para)
        assertFalse(SensitiveClipboard.containsSecret(redacted))
    }
}
