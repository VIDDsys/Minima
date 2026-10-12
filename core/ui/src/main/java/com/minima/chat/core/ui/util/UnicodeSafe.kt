package com.minima.chat.core.ui.util

/**
 * Display/truncate helpers that avoid unpaired surrogates (common crash source
 * when slicing emoji / weird Unicode for UI).
 */
object UnicodeSafe {
    /** Strip unpaired surrogates; never throws. */
    fun sanitize(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isHighSurrogate()) {
                if (i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                    out.append(c).append(text[i + 1])
                    i += 2
                } else {
                    out.append('\uFFFD')
                    i++
                }
            } else if (c.isLowSurrogate()) {
                out.append('\uFFFD')
                i++
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** Code-point-safe prefix (not Char-index). */
    fun take(text: String, maxCodePoints: Int): String {
        if (maxCodePoints <= 0) return ""
        val s = sanitize(text)
        if (s.isEmpty()) return ""
        var count = 0
        var i = 0
        while (i < s.length && count < maxCodePoints) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            count++
        }
        return s.substring(0, i)
    }
}
