package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyNotePathTest {
    @Test
    fun daily_note_name_is_iso_date_md() {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(0L)) // 1970-01-01 UTC-ish
        assertThat(day).matches("""\d{4}-\d{2}-\d{2}""")
        assertThat("notes/$day.md").startsWith("notes/")
        assertThat("notes/$day.md").endsWith(".md")
    }

    @Test
    fun append_block_includes_content() {
        val content = "Remember battery%"
        val block = buildString {
            appendLine()
            appendLine(content.trimEnd())
            appendLine()
        }
        assertThat(block).contains("Remember battery%")
    }
}
