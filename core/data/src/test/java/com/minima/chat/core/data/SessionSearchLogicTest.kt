package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.Session
import org.junit.Test

class SessionSearchLogicTest {
    private fun filter(sessions: List<Session>, query: String): List<Session> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return sessions
        return sessions.filter { it.title.lowercase().contains(needle) }
    }

    @Test
    fun emptyQueryReturnsAll() {
        val list = listOf(
            Session("1", "Alpha", 1, 2),
            Session("2", "Beta", 1, 3),
        )
        assertThat(filter(list, "")).hasSize(2)
        assertThat(filter(list, "  ")).hasSize(2)
    }

    @Test
    fun caseInsensitiveSubstring() {
        val list = listOf(
            Session("1", "Kotlin Notes", 1, 2),
            Session("2", "Java tips", 1, 3),
        )
        assertThat(filter(list, "kotlin")).hasSize(1)
        assertThat(filter(list, "TIP")).hasSize(1)
        assertThat(filter(list, "python")).isEmpty()
    }
}
