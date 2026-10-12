package com.minima.chat.core.agent.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * SAF tree helpers for user-granted Documents/Downloads folders.
 * All public file tools go through here — never raw /sdcard paths.
 */
object SafWorkspace {
    fun tree(context: Context, treeUri: String?): DocumentFile? {
        if (treeUri.isNullOrBlank()) return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        } catch (_: Exception) {
            null
        }
    }

    /** Resolve a relative path like "notes/todo.md" under the tree. */
    fun resolve(root: DocumentFile, relative: String): DocumentFile? {
        val cleaned = relative.trim().removePrefix("/").replace('\\', '/')
        if (cleaned.isEmpty() || cleaned == ".") return root
        var current: DocumentFile = root
        for (segment in cleaned.split('/').filter { it.isNotEmpty() }) {
            if (segment == "..") return null
            val next = current.findFile(segment) ?: return null
            current = next
        }
        return current
    }

    fun ensureDir(root: DocumentFile, relative: String): DocumentFile? {
        val cleaned = relative.trim().removePrefix("/").replace('\\', '/')
        if (cleaned.isEmpty() || cleaned == ".") return root
        var current: DocumentFile = root
        for (segment in cleaned.split('/').filter { it.isNotEmpty() }) {
            if (segment == "..") return null
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment) ?: return null
                existing.isDirectory -> existing
                else -> return null
            }
        }
        return current
    }

    fun mimeFor(name: String): String = when {
        name.endsWith(".md", true) || name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".json", true) -> "application/json"
        name.endsWith(".html", true) -> "text/html"
        name.endsWith(".csv", true) -> "text/csv"
        else -> "application/octet-stream"
    }
}
