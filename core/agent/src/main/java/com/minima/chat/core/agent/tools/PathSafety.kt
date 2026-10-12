package com.minima.chat.core.agent.tools

import java.io.File

/**
 * Sandbox path helpers — keep file tools from escaping the workspace root.
 * Pure JVM logic so unit tests do not need Android.
 */
object PathSafety {
    fun resolveSafe(root: File, relative: String): File? {
        val cleaned = sanitizeRelative(relative) ?: return null
        val target = try {
            File(root, cleaned).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val rootCanon = try {
            root.canonicalFile
        } catch (_: Exception) {
            return null
        }
        return if (isUnderRoot(rootCanon, target)) target else null
    }

    /**
     * Normalize a relative path input. Returns null if clearly unsafe
     * (null bytes, absolute, Windows drive, `..` segments, empty after strip).
     */
    fun sanitizeRelative(relative: String): String? {
        var cleaned = relative.trim()
            .replace('\\', '/')
            .removePrefix("./")
        if (cleaned.isEmpty()) cleaned = "."
        // Null bytes / control chars
        if (cleaned.any { it.code < 32 }) return null
        // Absolute / URI-ish
        if (cleaned.startsWith("/") || cleaned.startsWith("~")) return null
        if (cleaned.length >= 2 && cleaned[1] == ':') return null // C:\...
        if (cleaned.contains("://")) return null
        // Reject traversal / self segments in the middle (`.` alone means root)
        if (cleaned != ".") {
            val parts = cleaned.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            if (parts.any { it == ".." || it == "." }) return null
        }
        return cleaned
    }

    fun isUnderRoot(root: File, candidate: File): Boolean {
        val rootCanon = try {
            root.canonicalFile
        } catch (_: Exception) {
            return false
        }
        val cand = try {
            candidate.canonicalFile
        } catch (_: Exception) {
            return false
        }
        val rootPath = rootCanon.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        return cand == rootCanon || cand.path.startsWith(rootPath)
    }
}
