package com.inspiredandroid.kai.linux

import java.io.File
import java.io.IOException

/**
 * Guest paths where the chat sandbox bind-mounts the phone's shared storage
 * (`Environment.getExternalStorageDirectory()`, normally `/storage/emulated/0`).
 * Both names are the same directory. `/root` is not one of them.
 */
const val PUBLIC_STORAGE_GUEST_SDCARD = "/sdcard"
const val PUBLIC_STORAGE_GUEST_EMULATED = "/storage/emulated/0"

/**
 * Host paths the native tool accepts. Each one is rewritten onto the device's
 * actual shared-storage root before the containment check.
 */
internal val PUBLIC_STORAGE_ALIASES = listOf(
    "/sdcard",
    "/mnt/sdcard",
    "/storage/self/primary",
    "/storage/emulated/0",
)

private const val MAX_LIST_ENTRIES = 500
private const val MAX_READ_BYTES = 32_768
private const val MAX_WRITE_CHARS = 1_000_000

/**
 * Resolves a user path onto [root] and refuses anything that canonicalizes outside it,
 * including `..` and symlinks that leave the tree.
 */
class PublicStorageGuard(private val root: File) {

    fun resolve(rawPath: String): File? {
        val relative = relativeParts(rawPath) ?: return null
        return safeChild(root, relative)
    }

    private fun relativeParts(rawPath: String): List<String>? {
        val normalized = normalizeAbsolute(rawPath) ?: return null
        val rootPath = root.absolutePath.trimEnd('/', '\\')
        val suffix = when {
            normalized == rootPath -> ""
            normalized.startsWith("$rootPath/") -> normalized.removePrefix(rootPath)
            else -> aliasSuffix(normalized) ?: return null
        }
        if (suffix.isEmpty()) return emptyList()
        return suffix.trimStart('/').split('/').filter { it.isNotEmpty() }
    }
}

/** List/read/write/mkdir/delete under a shared-storage root. No Android APIs. */
internal class PublicStorageSession(private val root: File) {
    private val guard = PublicStorageGuard(root)

    fun execute(action: String, rawPath: String, content: String?, recursive: Boolean): Map<String, Any> {
        val file = guard.resolve(rawPath)
            ?: return error(
                "Path is outside shared storage. Use /storage/emulated/0 or an alias " +
                    "(${PUBLIC_STORAGE_ALIASES.joinToString(", ")}). " +
                    "Linux sandbox paths such as /root are a different filesystem — use execute_shell_command for those.",
            )
        return try {
            when (action) {
                "list" -> list(file)
                "read" -> read(file)
                "write" -> write(file, content)
                "mkdir" -> mkdir(file)
                "delete" -> delete(file, recursive)
                else -> error("action must be one of list, read, write, mkdir, delete")
            }
        } catch (e: SecurityException) {
            error("Permission denied: ${e.message ?: "shared storage is not readable"}")
        } catch (e: IOException) {
            error(e.message ?: "I/O error")
        }
    }

    private fun list(dir: File): Map<String, Any> {
        if (!dir.exists()) return error("No such directory: ${dir.absolutePath}")
        if (!dir.isDirectory) return error("Path is a file. Use action=read. ${dir.absolutePath}")
        val children = dir.listFiles()
            ?: return error("Cannot list ${dir.absolutePath}. All files access may have been revoked.")
        val sorted = children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
        val shown = sorted.take(MAX_LIST_ENTRIES)
        return mapOf(
            "success" to true,
            "path" to dir.absolutePath,
            "truncated" to (sorted.size > shown.size),
            "entries" to shown.map { child ->
                mapOf(
                    "name" to child.name,
                    "path" to child.absolutePath,
                    "is_directory" to child.isDirectory,
                    "size_bytes" to if (child.isFile) child.length() else 0L,
                )
            },
        )
    }

    private fun read(file: File): Map<String, Any> {
        if (!file.exists()) return error("No such file: ${file.absolutePath}")
        if (file.isDirectory) return error("Path is a directory. Use action=list. ${file.absolutePath}")
        if (!file.isFile) return error("Not a regular file: ${file.absolutePath}")
        val size = file.length()
        file.inputStream().use { input ->
            val buf = ByteArray(MAX_READ_BYTES + 1)
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) break
                off += n
            }
            val count = minOf(off, MAX_READ_BYTES)
            if ((0 until count).any { buf[it] == 0.toByte() }) {
                return error("Binary file ($size bytes). public_storage read returns text only.")
            }
            val text = String(buf, 0, count, Charsets.UTF_8)
            return mapOf(
                "success" to true,
                "path" to file.absolutePath,
                "content" to text,
                "truncated" to (off > MAX_READ_BYTES || size > MAX_READ_BYTES),
                "size_bytes" to size,
            )
        }
    }

    private fun write(file: File, content: String?): Map<String, Any> {
        if (content == null) return error("content is required for action=write")
        if (content.length > MAX_WRITE_CHARS) return error("content exceeds $MAX_WRITE_CHARS characters")
        if (isRoot(file)) return error("Refusing to write the shared-storage root. Pass a file path under it.")
        if (file.isDirectory) return error("Path is a directory: ${file.absolutePath}")
        val parent = file.parentFile ?: return error("Path has no parent: ${file.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) return error("Could not create parent directory: ${parent.absolutePath}")
        file.writeText(content, Charsets.UTF_8)
        return mapOf("success" to true, "path" to file.absolutePath, "bytes" to file.length())
    }

    private fun mkdir(dir: File): Map<String, Any> {
        if (dir.isFile) return error("A file already exists at ${dir.absolutePath}")
        if (dir.isDirectory) return mapOf("success" to true, "path" to dir.absolutePath, "created" to false)
        if (!dir.mkdirs()) return error("Could not create directory: ${dir.absolutePath}")
        return mapOf("success" to true, "path" to dir.absolutePath, "created" to true)
    }

    private fun delete(file: File, recursive: Boolean): Map<String, Any> {
        if (isRoot(file)) return error("Refusing to delete the shared-storage root.")
        if (!file.exists()) return error("No such file: ${file.absolutePath}")
        val removed = when {
            file.isDirectory && !recursive -> {
                val empty = file.list()?.isEmpty() != false
                if (!empty) return error("Directory is not empty. Pass recursive=true to delete it. ${file.absolutePath}")
                file.delete()
            }

            file.isDirectory -> file.deleteRecursively()

            else -> file.delete()
        }
        return if (removed) {
            mapOf("success" to true, "path" to file.absolutePath)
        } else {
            error("Could not delete ${file.absolutePath}")
        }
    }

    private fun isRoot(file: File): Boolean = file.canonicalPath == root.canonicalPath

    private fun error(message: String): Map<String, Any> = mapOf("success" to false, "error" to message)
}

private fun normalizeAbsolute(raw: String): String? {
    val path = raw.trim()
    if (path.isEmpty() || !path.startsWith("/")) return null
    if ('\u0000' in path || '\\' in path) return null
    val parts = path.split('/').filter { it.isNotEmpty() }
    if (parts.any { it == "." || it == ".." }) return null
    return "/" + parts.joinToString("/")
}

private fun aliasSuffix(normalized: String): String? {
    for (alias in PUBLIC_STORAGE_ALIASES) {
        if (normalized == alias) return ""
        if (normalized.startsWith("$alias/")) return normalized.removePrefix(alias)
    }
    return null
}
