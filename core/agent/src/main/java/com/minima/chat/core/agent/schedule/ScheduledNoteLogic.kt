package com.minima.chat.core.agent.schedule

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure sandbox note writer for [ScheduledNoteWorker].
 * Survives missing / unwritable workspace without throwing to the caller.
 */
object ScheduledNoteLogic {

    sealed class Outcome {
        data class Written(val file: File, val stamp: String) : Outcome()
        data class Unavailable(val reason: String) : Outcome()
    }

    fun writeScheduledNote(workspaceRoot: File?, nowMs: Long = System.currentTimeMillis()): Outcome {
        if (workspaceRoot == null) {
            return Outcome.Unavailable("workspace root is null")
        }
        if (!ensureWritableDir(workspaceRoot)) {
            return Outcome.Unavailable("workspace not writable: ${workspaceRoot.absolutePath}")
        }
        val notes = File(workspaceRoot, "notes")
        if (!ensureWritableDir(notes)) {
            return Outcome.Unavailable("notes dir not writable: ${notes.absolutePath}")
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(nowMs))
        val file = File(notes, "scheduled_$stamp.md")
        val body = buildString {
            appendLine("# Scheduled note")
            appendLine()
            appendLine("Created by Minima WorkManager at $stamp.")
            appendLine("Edit or delete freely — this is your app workspace.")
        }
        return try {
            file.writeText(body)
            if (!file.isFile || file.length() == 0L) {
                Outcome.Unavailable("write produced empty file: ${file.absolutePath}")
            } else {
                Outcome.Written(file, stamp)
            }
        } catch (t: Throwable) {
            Outcome.Unavailable("write failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Create dir if needed; require existing directory that canAccept writes. */
    fun ensureWritableDir(dir: File): Boolean {
        return try {
            if (dir.exists()) {
                dir.isDirectory && dir.canWrite()
            } else {
                dir.mkdirs() && dir.isDirectory && dir.canWrite()
            }
        } catch (_: Throwable) {
            false
        }
    }
}
