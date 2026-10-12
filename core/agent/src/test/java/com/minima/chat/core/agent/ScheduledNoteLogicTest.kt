package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.schedule.ScheduledNoteLogic
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScheduledNoteLogicTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun nullRoot_unavailable_noThrow() {
        val out = ScheduledNoteLogic.writeScheduledNote(null, nowMs = 1_700_000_000_000L)
        assertThat(out).isInstanceOf(ScheduledNoteLogic.Outcome.Unavailable::class.java)
    }

    @Test
    fun writableSandbox_writesMarkdown() {
        val root = tmp.newFolder("minima_workspace")
        val out = ScheduledNoteLogic.writeScheduledNote(root, nowMs = 1_700_000_000_000L)
        assertThat(out).isInstanceOf(ScheduledNoteLogic.Outcome.Written::class.java)
        val written = out as ScheduledNoteLogic.Outcome.Written
        assertThat(written.file.exists()).isTrue()
        assertThat(written.file.readText()).contains("# Scheduled note")
        assertThat(written.file.name).startsWith("scheduled_")
        assertThat(written.file.parentFile!!.name).isEqualTo("notes")
    }

    @Test
    fun fileBlockingNotesPath_unavailable() {
        val root = tmp.newFolder("ws")
        // Create a FILE named "notes" so mkdirs of notes/ fails
        File(root, "notes").writeText("blocker")
        val out = ScheduledNoteLogic.writeScheduledNote(root, nowMs = 1L)
        assertThat(out).isInstanceOf(ScheduledNoteLogic.Outcome.Unavailable::class.java)
        val reason = (out as ScheduledNoteLogic.Outcome.Unavailable).reason
        assertThat(reason).contains("notes")
    }

    @Test
    fun ensureWritableDir_rejectsFile() {
        val f = tmp.newFile("notadir")
        assertThat(ScheduledNoteLogic.ensureWritableDir(f)).isFalse()
    }
}
