package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure path segment logic mirroring SafWorkspace.resolve rules. */
class SafPathLogicTest {
    private fun segments(relative: String): List<String>? {
        val cleaned = relative.trim().removePrefix("/").replace('\\', '/')
        if (cleaned.isEmpty() || cleaned == ".") return emptyList()
        val parts = cleaned.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." }) return null
        return parts
    }

    @Test
    fun rejectsDotDot() {
        assertThat(segments("a/../b")).isNull()
        assertThat(segments("../x")).isNull()
    }

    @Test
    fun acceptsNested() {
        assertThat(segments("notes/2026/todo.md")).containsExactly("notes", "2026", "todo.md").inOrder()
    }

    @Test
    fun rootIsEmpty() {
        assertThat(segments(".")).isEmpty()
        assertThat(segments("")).isEmpty()
    }

    @Test
    fun normalizesSlashes() {
        assertThat(segments("a\\\\b")).containsExactly("a", "b").inOrder()
    }
}
