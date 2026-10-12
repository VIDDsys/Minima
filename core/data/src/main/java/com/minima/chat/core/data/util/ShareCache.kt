package com.minima.chat.core.data.util

import java.io.File

/**
 * Cache-only share transcripts under `cacheDir/shares/`.
 * Cap by count + age so old .md files (possibly from older builds) do not linger.
 */
object ShareCache {
    const val DIR_NAME = "shares"
    const val MAX_FILES = 8
    /** Drop share files older than 24h even if under the count cap. */
    const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun dir(cacheDir: File): File = File(cacheDir, DIR_NAME)

    fun ensureDir(cacheDir: File): File =
        dir(cacheDir).also { it.mkdirs() }

    /**
     * Delete aged files first, then excess newest-kept.
     * @return number of files deleted
     */
    fun cleanup(
        sharesDir: File,
        nowMs: Long = System.currentTimeMillis(),
        maxFiles: Int = MAX_FILES,
        maxAgeMs: Long = MAX_AGE_MS,
    ): Int {
        if (!sharesDir.isDirectory) return 0
        val files = sharesDir.listFiles()?.filter { it.isFile }?.toMutableList() ?: return 0
        var deleted = 0
        val aged = files.filter { nowMs - it.lastModified() > maxAgeMs }
        for (f in aged) {
            if (runCatching { f.delete() }.getOrDefault(false)) {
                deleted++
                files.remove(f)
            }
        }
        if (maxFiles >= 0 && files.size > maxFiles) {
            files.sortedByDescending { it.lastModified() }
                .drop(maxFiles)
                .forEach { f ->
                    if (runCatching { f.delete() }.getOrDefault(false)) deleted++
                }
        }
        return deleted
    }

    fun newShareFile(
        sharesDir: File,
        stem: String,
        stampMs: Long = System.currentTimeMillis(),
    ): File {
        val safe = SessionTranscript.sanitizeFilename(stem)
        return File(sharesDir, "minima-$safe-$stampMs.md")
    }
}
