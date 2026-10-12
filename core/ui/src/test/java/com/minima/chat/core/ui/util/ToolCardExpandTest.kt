package com.minima.chat.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCardExpandTest {
    @Test
    fun collapsed_toExpanded() {
        assertEquals(ToolCardExpand.PREVIEW, ToolCardExpand.next(0, resultTruncatedAtPreview = false))
        assertEquals(ToolCardExpand.PREVIEW, ToolCardExpand.next(0, resultTruncatedAtPreview = true))
    }

    @Test
    fun expanded_collapses_inOneTap() {
        assertEquals(ToolCardExpand.COLLAPSED, ToolCardExpand.next(1, resultTruncatedAtPreview = true))
        assertEquals(ToolCardExpand.COLLAPSED, ToolCardExpand.next(1, resultTruncatedAtPreview = false))
        assertEquals(ToolCardExpand.COLLAPSED, ToolCardExpand.next(2, resultTruncatedAtPreview = true))
    }
}
