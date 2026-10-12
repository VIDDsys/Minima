package com.minima.chat.core.ui.util

/**
 * Ink-minimal tool result / args preview for [com.minima.chat.core.ui.components.ToolCard].
 * Unicode-safe truncation + honest "truncated" marker when clipped.
 */
data class ToolPreviewSlice(
    val text: String,
    val truncated: Boolean,
    /** Original code-point length (sanitized). */
    val originalCodePoints: Int,
)

object ToolPreviewText {
    const val ARGS_COLLAPSED = 160
    const val ARGS_EXPANDED = 800
    const val RESULT_EXPANDED = 800
    const val RESULT_FULL = 8_000

    fun slice(raw: String?, maxCodePoints: Int): ToolPreviewSlice {
        val safe = UnicodeSafe.sanitize(raw)
        if (safe.isEmpty()) {
            return ToolPreviewSlice("", truncated = false, originalCodePoints = 0)
        }
        val cps = codePointCount(safe)
        if (cps <= maxCodePoints) {
            return ToolPreviewSlice(safe, truncated = false, originalCodePoints = cps)
        }
        val taken = UnicodeSafe.take(safe, maxCodePoints)
        return ToolPreviewSlice(taken + "…", truncated = true, originalCodePoints = cps)
    }

    fun truncationHint(slice: ToolPreviewSlice): String? =
        if (slice.truncated) "truncated · ${slice.originalCodePoints} chars" else null

    private fun codePointCount(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            n++
        }
        return n
    }
}
