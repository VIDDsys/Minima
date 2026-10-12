package com.minima.chat.core.agent.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.minima.chat.core.network.api.SecretRedact
import javax.inject.Inject

/**
 * Search AGENT.md + app workspace notes (+ public SAF tree when granted).
 * Read-only. Max permissions: public storage is always allowed when a tree URI exists.
 *
 * Hardened (v2.23): path sanitize + resolveSafe, binary skip, capped stream reads,
 * total byte budget shared across sources.
 */
class SearchMemoryTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "search_memory",
        description = "Search device memory and workspace text files (AGENT.md, app workspace notes, " +
            "minima_workspace, and public SAF folder when granted). Returns path:line snippets. " +
            "Use before read_sandbox_file / read_file when looking for a fact or prior note.",
        parameters = JsonSchema.obj(
            "query" to JsonSchema.string("Case-insensitive substring to find"),
            "path" to JsonSchema.string("Optional relative path under sandbox (default: entire workspace)"),
            "scope" to JsonSchema.string("workspace | all (default all: workspace + AGENT.md + public files if granted)"),
            "max_results" to JsonSchema.string("Max hits 1–50 (default 20)"),
            required = listOf("query"),
        ),
        requiresGrant = Grant.Sandbox,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val query = parseString(argumentsJson, "query")?.trim().orEmpty()
        if (query.isEmpty()) return ToolResult(false, "query required")
        if (query.length > 200) return ToolResult(false, "query too long (max 200)")

        val maxResults = MemorySearch.clampMaxResults(
            parseString(argumentsJson, "max_results")?.toIntOrNull(),
        )
        val scope = (parseString(argumentsJson, "scope") ?: "all").trim().lowercase()
        val rawPath = parseString(argumentsJson, "path")?.trim()?.takeIf { it.isNotEmpty() && it != "." }
        val relPath = rawPath?.let { path ->
            val cleaned = PathSafety.sanitizeRelative(path)
                ?: return ToolResult(false, "Unsafe path: $path")
            if (cleaned == ".") null else cleaned
        }

        val parts = mutableListOf<MemorySearch.SearchResult>()
        var bytesLeft = MemorySearch.MAX_TOTAL_BYTES

        // 1) AGENT.md locations (app-private)
        if (scope == "all" || scope == "agent" || scope == "memory") {
            val agentPart = searchAgentMdFiles(query, maxResults, bytesLeft)
            parts += agentPart.first
            bytesLeft = agentPart.second
        }

        // 2) Sandbox workspace
        if (scope == "all" || scope == "sandbox" || scope == "memory") {
            val root = workspaceRoot(context)
            val target = if (relPath == null) {
                root
            } else {
                resolveSafe(root, relPath) ?: return ToolResult(false, "Path escapes workspace: $relPath")
            }
            val labelPrefix = if (relPath == null) "workspace" else "workspace/$relPath"
            parts += MemorySearch.searchFileTree(
                root = target,
                query = query,
                relBase = labelPrefix,
                maxResults = maxResults,
                maxTotalBytes = bytesLeft.coerceAtLeast(0),
            )
            // Approximate remaining budget from scanned files (best-effort for merge order)
            bytesLeft = (bytesLeft - MemorySearch.MAX_FILE_BYTES * parts.last().filesScanned)
                .coerceAtLeast(0)
        }

        // 3) SAF public tree when granted (max permissions — sandboxOnly always false)
        val policy = policyStore.policy
        val includeSaf = scope == "all" || scope == "saf" || scope == "workspace"
        if (includeSaf && !policy.publicTreeUri.isNullOrBlank()) {
            parts += searchSafTree(policy.publicTreeUri!!, query, maxResults, relPath, bytesLeft)
        } else if (includeSaf && scope == "saf" && policy.publicTreeUri.isNullOrBlank()) {
            parts += MemorySearch.SearchResult(
                emptyList(), 0, false,
                "SAF skipped (no public folder URI yet — grant storage access once)",
            )
        }

        val merged = MemorySearch.merge(parts, maxResults)
        return ToolResult(true, SecretRedact.redact(MemorySearch.format(merged)))
    }

    private fun searchAgentMdFiles(
        query: String,
        maxResults: Int,
        bytesBudget: Int,
    ): Pair<MemorySearch.SearchResult, Int> {
        val hits = mutableListOf<MemorySearch.Hit>()
        var scanned = 0
        var remaining = bytesBudget
        val candidates = listOf(
            File(context.filesDir, "AGENT.md") to "AGENT.md",
            File(context.filesDir, "skills/AGENT.md") to "skills/AGENT.md",
            File(workspaceRoot(context), "AGENT.md") to "workspace/AGENT.md",
        )
        for ((file, label) in candidates) {
            if (!file.isFile) continue
            if (remaining <= 0) break
            scanned++
            when (val read = MemorySearch.readFileCapped(file, minOf(MemorySearch.MAX_FILE_BYTES, remaining))) {
                is MemorySearch.CappedRead.Text -> {
                    remaining -= read.bytesRead
                    hits += MemorySearch.searchText(label, read.value, query)
                }
                else -> Unit
            }
        }
        val ranked = hits.sortedByDescending { it.score }.take(maxResults)
        return MemorySearch.SearchResult(ranked, scanned, hits.size > maxResults) to remaining
    }

    private fun searchSafTree(
        treeUri: String,
        query: String,
        maxResults: Int,
        relPath: String?,
        bytesBudget: Int,
    ): MemorySearch.SearchResult {
        val tree = try {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        } catch (_: Exception) {
            null
        } ?: return MemorySearch.SearchResult(emptyList(), 0, false, "SAF tree unavailable")

        val start = if (relPath == null) {
            tree
        } else {
            val cleaned = PathSafety.sanitizeRelative(relPath)
                ?: return MemorySearch.SearchResult(emptyList(), 0, false, "Unsafe SAF path: $relPath")
            if (cleaned.split('/').any { it == ".." }) {
                return MemorySearch.SearchResult(emptyList(), 0, false, "SAF path escapes: $relPath")
            }
            SafWorkspace.resolve(tree, cleaned)
                ?: return MemorySearch.SearchResult(emptyList(), 0, false, "SAF path not found: $relPath")
        }
        val labelPrefix = if (relPath == null) "saf" else "saf/$relPath"
        return walkSaf(start, labelPrefix, query, maxResults, bytesBudget)
    }

    private fun walkSaf(
        start: DocumentFile,
        labelPrefix: String,
        query: String,
        maxResults: Int,
        bytesBudget: Int,
    ): MemorySearch.SearchResult {
        val hits = mutableListOf<MemorySearch.Hit>()
        var scanned = 0
        var truncated = false
        var remaining = bytesBudget

        fun readDoc(doc: DocumentFile): MemorySearch.CappedRead {
            if (remaining <= 0) return MemorySearch.CappedRead.Empty
            val declared = doc.length()
            // length() may be -1 (unknown) — still cap the stream
            if (declared > MemorySearch.MAX_FILE_BYTES) return MemorySearch.CappedRead.Empty
            return try {
                context.contentResolver.openInputStream(doc.uri)?.use {
                    MemorySearch.readStreamCapped(it, minOf(MemorySearch.MAX_FILE_BYTES, remaining))
                } ?: MemorySearch.CappedRead.Error
            } catch (_: Exception) {
                MemorySearch.CappedRead.Error
            }
        }

        fun walk(doc: DocumentFile, rel: String, depth: Int) {
            if (truncated || hits.size >= maxResults) {
                truncated = true
                return
            }
            if (remaining <= 0) {
                truncated = true
                return
            }
            if (depth > MemorySearch.MAX_DEPTH) return
            if (doc.isFile) {
                val name = doc.name ?: return
                if (!MemorySearch.isTextFileName(name)) return
                if (scanned >= MemorySearch.MAX_FILES_SCANNED) {
                    truncated = true
                    return
                }
                scanned++
                when (val read = readDoc(doc)) {
                    is MemorySearch.CappedRead.Text -> {
                        remaining -= read.bytesRead
                        val label = if (rel.isEmpty()) "$labelPrefix/$name" else "$labelPrefix/$rel"
                        hits += MemorySearch.searchText(label, read.value, query)
                        if (hits.size >= maxResults) truncated = true
                    }
                    else -> Unit
                }
                return
            }
            if (!doc.isDirectory) return
            val children = doc.listFiles().sortedBy { it.name ?: "" }
            for (child in children) {
                if (truncated || hits.size >= maxResults || remaining <= 0) {
                    truncated = true
                    return
                }
                val childName = child.name ?: continue
                // Reject path-ish names that look like traversal
                if (childName == ".." || childName.contains('\u0000')) continue
                val childRel = if (rel.isEmpty()) childName else "$rel/$childName"
                walk(child, childRel, depth + 1)
            }
        }

        if (start.isFile) {
            walk(start, start.name ?: "file", 0)
        } else {
            walk(start, "", 0)
        }
        val ranked = hits.sortedByDescending { it.score }.take(maxResults)
        if (hits.size > maxResults) truncated = true
        return MemorySearch.SearchResult(ranked, scanned, truncated)
    }
}
