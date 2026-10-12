package com.minima.chat.core.agent.tools

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Public storage file tools.
 * Prefer SAF tree when granted; otherwise fall back to shared external storage root
 * (requires all-files / legacy storage access on device — no root).
 */
internal object PublicStorage {
    fun resolveSafe(root: File, rel: String): File? {
        val cleaned = rel.trim().removePrefix("/").replace('\\', '/')
        if (cleaned.contains("..")) return null
        val target = if (cleaned.isEmpty() || cleaned == ".") root else File(root, cleaned)
        val rootCanon = root.canonicalFile
        val targetCanon = runCatching { target.canonicalFile }.getOrNull() ?: return null
        if (!targetCanon.path.startsWith(rootCanon.path)) return null
        return targetCanon
    }

    fun externalRoot(): File? {
        val root = Environment.getExternalStorageDirectory() ?: return null
        return root.takeIf { it.exists() || it.mkdirs() }
    }
}

class ListPublicDirTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "list_dir",
        description = "List files in public storage (SAF workspace if granted, else shared external storage). Path is relative.",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative path under workspace; default '.'"),
        ),
        requiresGrant = Grant.PublicFiles,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: "."
        val treeUri = policyStore.policy.publicTreeUri
        if (!treeUri.isNullOrBlank()) {
            val root = SafWorkspace.tree(context, treeUri)
                ?: return ToolResult(false, "Cannot open granted folder. Re-pick it.")
            val target = SafWorkspace.resolve(root, rel)
                ?: return ToolResult(false, "Not found or path escapes workspace: $rel")
            if (!target.isDirectory) return ToolResult(false, "Not a directory: $rel")
            val listing = target.listFiles().sortedBy { it.name }.joinToString("\n") { f ->
                val kind = if (f.isDirectory) "dir" else "file"
                val len = if (f.isFile) f.length() else 0L
                "$kind\t${f.name}\t$len"
            }.ifEmpty { "(empty)" }
            return ToolResult(true, listing)
        }
        val root = PublicStorage.externalRoot()
            ?: return ToolResult(false, "No external storage available. Grant all-files access in system settings.")
        val target = PublicStorage.resolveSafe(root, rel)
            ?: return ToolResult(false, "Not found or path escapes storage: $rel")
        if (!target.isDirectory) return ToolResult(false, "Not a directory: $rel")
        val listing = (target.listFiles() ?: emptyArray()).sortedBy { it.name }.joinToString("\n") { f ->
            val kind = if (f.isDirectory) "dir" else "file"
            val len = if (f.isFile) f.length() else 0L
            "$kind\t${f.name}\t$len"
        }.ifEmpty { "(empty)" }
        return ToolResult(true, listing)
    }
}

class ReadPublicFileTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "read_file",
        description = "Read a UTF-8 text file from public storage (max 64KB).",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative path under workspace"),
            required = listOf("path"),
        ),
        requiresGrant = Grant.PublicFiles,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: return ToolResult(false, "path required")
        val treeUri = policyStore.policy.publicTreeUri
        if (!treeUri.isNullOrBlank()) {
            val root = SafWorkspace.tree(context, treeUri)
                ?: return ToolResult(false, "Cannot open granted folder.")
            val target = SafWorkspace.resolve(root, rel)
                ?: return ToolResult(false, "Not found: $rel")
            if (!target.isFile) return ToolResult(false, "Not a file: $rel")
            if (target.length() > 64 * 1024) return ToolResult(false, "File too large (>64KB)")
            return try {
                context.contentResolver.openInputStream(target.uri)?.use { stream ->
                    ToolResult(true, stream.bufferedReader().readText())
                } ?: ToolResult(false, "Cannot open stream")
            } catch (e: Exception) {
                ToolResult(false, e.message ?: "Read failed")
            }
        }
        val root = PublicStorage.externalRoot()
            ?: return ToolResult(false, "No external storage available.")
        val target = PublicStorage.resolveSafe(root, rel)
            ?: return ToolResult(false, "Not found: $rel")
        if (!target.isFile) return ToolResult(false, "Not a file: $rel")
        if (target.length() > 64 * 1024) return ToolResult(false, "File too large (>64KB)")
        return try {
            ToolResult(true, target.readText())
        } catch (e: Exception) {
            ToolResult(false, e.message ?: "Read failed")
        }
    }
}

class WritePublicFileTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "write_file",
        description = "Write UTF-8 text to a file in public storage. Creates parent dirs as needed.",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative path under workspace"),
            "content" to JsonSchema.string("File contents"),
            required = listOf("path", "content"),
        ),
        requiresGrant = Grant.PublicFiles,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: return ToolResult(false, "path required")
        val content = parseString(argumentsJson, "content") ?: return ToolResult(false, "content required")
        val treeUri = policyStore.policy.publicTreeUri
        if (!treeUri.isNullOrBlank()) {
            val root = SafWorkspace.tree(context, treeUri)
                ?: return ToolResult(false, "Cannot open granted folder.")
            val cleaned = rel.trim().removePrefix("/").replace('\\', '/')
            if (cleaned.isEmpty() || cleaned.contains("..")) return ToolResult(false, "Invalid path")
            val slash = cleaned.lastIndexOf('/')
            val parentRel = if (slash >= 0) cleaned.substring(0, slash) else "."
            val fileName = if (slash >= 0) cleaned.substring(slash + 1) else cleaned
            if (fileName.isBlank()) return ToolResult(false, "Missing file name")
            val parent = SafWorkspace.ensureDir(root, parentRel)
                ?: return ToolResult(false, "Cannot create parent dirs")
            val existing = parent.findFile(fileName)
            val doc = when {
                existing != null && existing.isFile -> existing
                existing != null -> return ToolResult(false, "Path exists and is not a file")
                else -> parent.createFile(SafWorkspace.mimeFor(fileName), fileName)
                    ?: return ToolResult(false, "Cannot create file")
            }
            return try {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                    out.bufferedWriter().use { it.write(content) }
                } ?: return ToolResult(false, "Cannot open output stream")
                ToolResult(true, "Wrote ${content.length} bytes to $cleaned")
            } catch (e: Exception) {
                ToolResult(false, e.message ?: "Write failed")
            }
        }
        val root = PublicStorage.externalRoot()
            ?: return ToolResult(false, "No external storage available.")
        val target = PublicStorage.resolveSafe(root, rel)
            ?: return ToolResult(false, "Invalid path: $rel")
        return try {
            target.parentFile?.mkdirs()
            target.writeText(content)
            ToolResult(true, "Wrote ${content.length} bytes to ${target.absolutePath}")
        } catch (e: Exception) {
            ToolResult(false, e.message ?: "Write failed")
        }
    }
}

class MkdirPublicTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "mkdir",
        description = "Create a directory under public storage.",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative directory path"),
            required = listOf("path"),
        ),
        requiresGrant = Grant.PublicFiles,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: return ToolResult(false, "path required")
        if (rel.contains("..")) return ToolResult(false, "Invalid path")
        val treeUri = policyStore.policy.publicTreeUri
        if (!treeUri.isNullOrBlank()) {
            val root = SafWorkspace.tree(context, treeUri)
                ?: return ToolResult(false, "Cannot open granted folder.")
            val dir = SafWorkspace.ensureDir(root, rel) ?: return ToolResult(false, "mkdir failed: $rel")
            return ToolResult(true, "Directory ready: ${dir.name}")
        }
        val root = PublicStorage.externalRoot()
            ?: return ToolResult(false, "No external storage available.")
        val target = PublicStorage.resolveSafe(root, rel)
            ?: return ToolResult(false, "Invalid path: $rel")
        return if (target.exists() || target.mkdirs()) {
            ToolResult(true, "Directory ready: ${target.absolutePath}")
        } else {
            ToolResult(false, "mkdir failed: $rel")
        }
    }
}
