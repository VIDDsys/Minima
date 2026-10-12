package com.minima.chat.core.agent.tools

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Pure text search over memory / workspace files.
 * Case-insensitive substring; returns ranked path:line snippets.
 *
 * Hardened (v2.23): symlink/path escape checks, binary sniff, per-file + total
 * byte caps, bounded stream reads, max line length.
 */
object MemorySearch {
    const val DEFAULT_MAX_RESULTS = 20
    const val HARD_MAX_RESULTS = 50
    const val MAX_FILE_BYTES = 96 * 1024
    const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
    const val MAX_FILES_SCANNED = 200
    const val MAX_DEPTH = 8
    const val SNIPPET_RADIUS = 60
    const val MAX_OUTPUT_CHARS = 24_000
    const val MAX_LINE_CHARS = 4_000
    const val BINARY_SNIFF_BYTES = 512

    private val TEXT_EXT = setOf(
        "md", "txt", "json", "csv", "log", "yml", "yaml", "toml",
        "xml", "html", "htm", "kt", "java", "js", "ts", "py", "sh",
        "cfg", "conf", "ini", "properties",
    )

    data class Hit(
        val path: String,
        val line: Int,
        val snippet: String,
        val score: Int,
    )

    data class SearchResult(
        val hits: List<Hit>,
        val filesScanned: Int,
        val truncated: Boolean,
        val note: String = "",
    )

    sealed class CappedRead {
        data class Text(val value: String, val bytesRead: Int, val hitCap: Boolean) : CappedRead()
        data object Binary : CappedRead()
        data object Empty : CappedRead()
        data object Error : CappedRead()
    }

    fun isTextFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isEmpty()) {
            return name.equals("LICENSE", ignoreCase = true) ||
                name.equals("README", ignoreCase = true) ||
                name.equals("AGENT", ignoreCase = true)
        }
        return ext in TEXT_EXT
    }

    fun clampMaxResults(raw: Int?): Int {
        val n = raw ?: DEFAULT_MAX_RESULTS
        return n.coerceIn(1, HARD_MAX_RESULTS)
    }

    /**
     * True if [sample] looks like binary (NUL byte or high control-char ratio).
     * Empty sample is not binary.
     */
    fun looksBinary(sample: ByteArray): Boolean {
        if (sample.isEmpty()) return false
        if (sample.any { it == 0.toByte() }) return true
        var nonText = 0
        for (b in sample) {
            val c = b.toInt() and 0xFF
            // Allow tab/LF/CR; reject other C0 controls and DEL
            if (c == 0x7F || (c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D)) {
                nonText++
            }
        }
        return nonText * 100 / sample.size > 30
    }

    /**
     * Read at most [maxBytes] from [input], sniff binary on the first chunk.
     * Never trusts declared length — always caps the stream.
     */
    fun readStreamCapped(input: InputStream, maxBytes: Int = MAX_FILE_BYTES): CappedRead {
        if (maxBytes <= 0) return CappedRead.Empty
        return try {
            val sniffLen = minOf(BINARY_SNIFF_BYTES, maxBytes)
            val head = ByteArray(sniffLen)
            val nHead = readFully(input, head)
            if (nHead <= 0) return CappedRead.Empty
            val sample = if (nHead == head.size) head else head.copyOf(nHead)
            if (looksBinary(sample)) return CappedRead.Binary

            val out = ByteArrayOutputStream(minOf(maxBytes, 8192).coerceAtLeast(nHead))
            out.write(sample)
            var remaining = maxBytes - nHead
            val buf = ByteArray(8192)
            var hitCap = false
            while (remaining > 0) {
                val want = minOf(buf.size, remaining)
                val r = input.read(buf, 0, want)
                if (r < 0) break
                out.write(buf, 0, r)
                remaining -= r
            }
            if (remaining == 0) {
                // One more byte? if available we hit the cap
                hitCap = input.read() >= 0
            }
            val text = out.toString(Charsets.UTF_8.name())
            CappedRead.Text(text, out.size(), hitCap)
        } catch (_: Exception) {
            CappedRead.Error
        }
    }

    fun readFileCapped(file: File, maxBytes: Int = MAX_FILE_BYTES): CappedRead {
        if (!file.isFile) return CappedRead.Error
        return try {
            file.inputStream().use { readStreamCapped(it, maxBytes) }
        } catch (_: Exception) {
            CappedRead.Error
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    /** Search [text] for [query] (case-insensitive). */
    fun searchText(pathLabel: String, text: String, query: String, maxPerFile: Int = 8): List<Hit> {
        if (query.isBlank() || text.isEmpty()) return emptyList()
        val q = query.lowercase()
        val hits = mutableListOf<Hit>()
        text.lineSequence().forEachIndexed { idx, rawLine ->
            val line = if (rawLine.length > MAX_LINE_CHARS) rawLine.take(MAX_LINE_CHARS) else rawLine
            val lower = line.lowercase()
            val at = lower.indexOf(q)
            if (at < 0) return@forEachIndexed
            hits += Hit(
                path = pathLabel,
                line = idx + 1,
                snippet = snippetAround(line, at, q.length),
                score = scoreHit(line, q, at),
            )
            if (hits.size >= maxPerFile) return hits
        }
        return hits
    }

    fun snippetAround(line: String, matchAt: Int, matchLen: Int): String {
        val start = (matchAt - SNIPPET_RADIUS).coerceAtLeast(0)
        val end = (matchAt + matchLen + SNIPPET_RADIUS).coerceAtMost(line.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < line.length) "…" else ""
        return (prefix + line.substring(start, end).trim() + suffix).take(200)
    }

    fun scoreHit(line: String, queryLower: String, at: Int): Int {
        var s = 10
        s += (120 - line.length).coerceIn(0, 40) / 4
        s += (40 - at).coerceIn(0, 40) / 4
        if (line.trimStart().lowercase().startsWith(queryLower)) s += 8
        return s
    }

    /**
     * Walk a File tree under [root], search text files.
     * [relBase] prefixes path labels (e.g. "sandbox" or "agent.md").
     * Symlinks / paths that leave [root] are skipped.
     */
    fun searchFileTree(
        root: File,
        query: String,
        relBase: String = "",
        maxResults: Int = DEFAULT_MAX_RESULTS,
        maxFiles: Int = MAX_FILES_SCANNED,
        maxDepth: Int = MAX_DEPTH,
        maxTotalBytes: Int = MAX_TOTAL_BYTES,
    ): SearchResult {
        if (query.isBlank()) {
            return SearchResult(emptyList(), 0, false, "query is blank")
        }
        if (!root.exists()) {
            return SearchResult(emptyList(), 0, false, "root missing")
        }
        val rootCanon = try {
            root.canonicalFile
        } catch (_: Exception) {
            return SearchResult(emptyList(), 0, false, "root unreadable")
        }

        val allHits = mutableListOf<Hit>()
        var scanned = 0
        var bytesBudget = maxTotalBytes
        var truncated = false

        fun considerFile(file: File, label: String) {
            if (truncated || allHits.size >= maxResults) {
                truncated = true
                return
            }
            if (bytesBudget <= 0) {
                truncated = true
                return
            }
            if (!isTextFileName(file.name)) return
            val canon = try {
                file.canonicalFile
            } catch (_: Exception) {
                return
            }
            if (!PathSafety.isUnderRoot(rootCanon, canon)) return
            if (scanned >= maxFiles) {
                truncated = true
                return
            }
            scanned++
            val read = readFileCapped(file, minOf(MAX_FILE_BYTES, bytesBudget))
            when (read) {
                is CappedRead.Text -> {
                    bytesBudget -= read.bytesRead
                    if (read.hitCap && bytesBudget <= 0) truncated = true
                    allHits += searchText(label, read.value, query)
                    if (allHits.size >= maxResults) truncated = true
                }
                CappedRead.Binary, CappedRead.Empty, CappedRead.Error -> Unit
            }
        }

        fun walk(dir: File, rel: String, depth: Int) {
            if (truncated || allHits.size >= maxResults) {
                truncated = true
                return
            }
            if (bytesBudget <= 0) {
                truncated = true
                return
            }
            if (depth > maxDepth) return
            val dirCanon = try {
                dir.canonicalFile
            } catch (_: Exception) {
                return
            }
            if (!PathSafety.isUnderRoot(rootCanon, dirCanon)) return
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                if (truncated || allHits.size >= maxResults || bytesBudget <= 0) {
                    truncated = true
                    return
                }
                val childCanon = try {
                    child.canonicalFile
                } catch (_: Exception) {
                    continue
                }
                // Symlink / hard escape out of sandbox
                if (!PathSafety.isUnderRoot(rootCanon, childCanon)) continue
                val childRel = if (rel.isEmpty()) child.name else "$rel/${child.name}"
                val label = if (relBase.isEmpty()) childRel else "$relBase/$childRel"
                when {
                    child.isDirectory -> walk(child, childRel, depth + 1)
                    child.isFile -> considerFile(child, label)
                }
            }
        }

        when {
            root.isFile -> {
                val label = relBase.ifEmpty { root.name }
                considerFile(root, label)
            }
            root.isDirectory -> walk(root, "", 0)
        }

        val ranked = allHits.sortedByDescending { it.score }.take(maxResults)
        if (allHits.size > maxResults) truncated = true
        return SearchResult(ranked, scanned, truncated)
    }

    fun format(result: SearchResult): String {
        if (result.hits.isEmpty()) {
            val note = result.note.ifBlank { "No matches" }
            return "$note (scanned ${result.filesScanned} files)"
        }
        val body = buildString {
            result.hits.forEach { h ->
                append(h.path)
                append(':')
                append(h.line)
                append(": ")
                append(h.snippet)
                appendLine()
            }
            appendLine()
            append("(${result.hits.size} hits, scanned ${result.filesScanned} files")
            if (result.truncated) append(", truncated")
            append(')')
            if (result.note.isNotBlank()) {
                appendLine()
                append(result.note)
            }
        }
        return body.take(MAX_OUTPUT_CHARS)
    }

    fun merge(parts: List<SearchResult>, maxResults: Int): SearchResult {
        val hits = parts.flatMap { it.hits }.sortedByDescending { it.score }.take(maxResults)
        val scanned = parts.sumOf { it.filesScanned }
        val truncated = parts.any { it.truncated } || parts.sumOf { it.hits.size } > maxResults
        val note = parts.map { it.note }.filter { it.isNotBlank() }.distinct().joinToString("; ")
        return SearchResult(hits, scanned, truncated, note)
    }
}
