package com.minima.chat.core.data.util

/**
 * Normalize user-entered session titles (rename dialog / imports).
 * Keeps UI ink-minimal: no empty, no control junk, bounded length.
 */
object SessionTitle {
    const val DEFAULT = "Untitled"
    const val MAX_LEN = 120

    fun normalize(raw: String?): String {
        if (raw == null) return DEFAULT
        val cleaned = buildString(raw.length) {
            for (ch in raw) {
                when {
                    // Whitespace (incl. tab/newline) → single space slot; checked before isISOControl
                    ch.isWhitespace() -> append(' ')
                    ch.isISOControl() -> Unit
                    ch == '\uFEFF' || ch == '\u200B' || ch == '\u200C' || ch == '\u200D' -> Unit
                    else -> append(ch)
                }
            }
        }
            .trim()
            .replace(Regex(" {2,}"), " ")
        if (cleaned.isEmpty()) return DEFAULT
        // Never store an API-key-looking title
        if (looksLikeSecret(cleaned)) return DEFAULT
        return if (cleaned.length <= MAX_LEN) cleaned else cleaned.take(MAX_LEN).trimEnd().ifEmpty { DEFAULT }
    }

    fun looksLikeSecret(text: String): Boolean {
        val t = text.trim()
        if (t.length < 12) return false
        return t.startsWith("sk-", ignoreCase = true) ||
            t.startsWith("sk-proj-", ignoreCase = true) ||
            t.startsWith("key-", ignoreCase = true)
    }
}
