package com.minima.chat.core.ui.util

/**
 * Pure stick-to-bottom logic for a reverseLayout LazyColumn
 * (index 0 = visual bottom = newest).
 */
object ChatListAutoscroll {
    fun isAtBottom(firstVisibleIndex: Int, firstVisibleOffset: Int): Boolean =
        firstVisibleIndex == 0 && firstVisibleOffset == 0

    /**
     * Update pin state after a scroll snapshot.
     * - At bottom → stick
     * - User dragging/flinging away → unstick
     * - Settled away from bottom → unstick
     */
    fun nextStuck(
        firstVisibleIndex: Int,
        firstVisibleOffset: Int,
        currentlyStuck: Boolean,
        userScrolling: Boolean,
    ): Boolean {
        val atBottom = isAtBottom(firstVisibleIndex, firstVisibleOffset)
        if (atBottom) return true
        if (userScrolling) return false
        return false
    }

    /** Whether new content should force a scroll to index 0. */
    fun shouldAutoScroll(stuckToBottom: Boolean, itemCount: Int): Boolean =
        stuckToBottom && itemCount > 0
}
