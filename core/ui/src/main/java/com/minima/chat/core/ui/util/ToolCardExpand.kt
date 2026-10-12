package com.minima.chat.core.ui.util

/**
 * Expand-level state machine for [com.minima.chat.core.ui.components.ToolCard].
 * Levels: 0 collapsed · 1 expanded (full args + result).
 * Single tap toggles — never requires a second tap to collapse.
 */
object ToolCardExpand {
    const val COLLAPSED = 0
    const val PREVIEW = 1
    /** @deprecated Kept for binary compat; same as PREVIEW (single expand level). */
    const val FULL = 1

    /**
     * Toggle: collapsed ↔ expanded. [resultTruncatedAtPreview] is ignored —
     * expanded always shows the full capped result.
     */
    fun next(current: Int, resultTruncatedAtPreview: Boolean = false): Int =
        if (current == COLLAPSED) PREVIEW else COLLAPSED
}
