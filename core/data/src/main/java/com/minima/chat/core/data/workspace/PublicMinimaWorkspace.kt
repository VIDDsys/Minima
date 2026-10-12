package com.minima.chat.core.data.workspace

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Public on-device workspace at `/storage/emulated/0/Minima` (shared storage).
 *
 * Layout (Agent Skills / AGENTS.md convention):
 * ```
 * Minima/
 *   AGENTS.md          # always-on context (case-insensitive name)
 *   skills/
 *     my-skill/
 *       SKILL.md       # or skill.md — name + description injected
 * ```
 *
 * Created on first launch when storage permits. Failures are soft (no crash).
 */
object PublicMinimaWorkspace {
    const val DIR_NAME = "Minima"
    const val SKILLS_DIR = "skills"
    const val MAX_AGENTS_CHARS = 24_000
    const val MAX_SKILL_INDEX = 64
    const val MAX_SKILL_BYTES = 64 * 1024

    data class SkillIndexEntry(
        val name: String,
        val description: String,
        val folderName: String,
        val skillFile: File,
    )

    /** Absolute path users expect: /storage/emulated/0/Minima */
    fun rootDir(): File? {
        val external = Environment.getExternalStorageDirectory() ?: return null
        return File(external, DIR_NAME)
    }

    /**
     * Ensure Minima/ + skills/ exist and seed a starter AGENTS.md when missing.
     * @return root if creatable/usable, else null.
     */
    fun ensureCreated(context: Context? = null): File? {
        val root = rootDir()
        return runCatching {
            if (root != null) {
                if (!root.exists()) {
                    root.mkdirs()
                }
                if (root.isDirectory) {
                    val skills = File(root, SKILLS_DIR)
                    if (!skills.exists()) skills.mkdirs()
                    seedAgentsIfMissing(root)
                    return@runCatching root
                }
            }
            // Fallback: app-specific external (still named Minima) when public root blocked
            val alt = context?.getExternalFilesDir(null)?.let { File(it, DIR_NAME) }
                ?: return@runCatching null
            alt.mkdirs()
            File(alt, SKILLS_DIR).mkdirs()
            seedAgentsIfMissing(alt)
            alt.takeIf { it.isDirectory }
        }.getOrNull()
    }

    private fun seedAgentsIfMissing(root: File) {
        if (listAgentsFiles(root).isNotEmpty()) return
        val seed = File(root, "AGENTS.md")
        if (seed.exists()) return
        runCatching {
            seed.writeText(
                """
                # Minima workspace

                This folder is your on-device agent home (`/storage/emulated/0/Minima`).

                - Put standing instructions in any `AGENTS.md` / `agents.md` here (all are injected).
                - Add skills under `skills/<name>/SKILL.md` (or `skill.md`). Only name + description
                  load into context; the agent calls `read_skill` for full instructions.

                Edit these files with any file manager — Minima reloads them on the next agent turn.
                """.trimIndent() + "\n",
            )
        }
    }

    /** Case-insensitive match for AGENTS.md / agents.md (and Agents.md etc.). */
    fun isAgentsFileName(name: String): Boolean =
        name.equals("AGENTS.md", ignoreCase = true)

    fun listAgentsFiles(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isFile && isAgentsFileName(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    /**
     * Concatenate every AGENTS.md (case-insensitive) under [root], capped.
     */
    fun loadAllAgentsMarkdown(root: File?): String? {
        if (root == null || !root.isDirectory) return null
        val files = listAgentsFiles(root)
        if (files.isEmpty()) return null
        val out = StringBuilder()
        for (f in files) {
            val body = readTextCapped(f, MAX_AGENTS_CHARS) ?: continue
            if (out.isNotEmpty()) out.append("\n\n")
            out.append("### ").append(f.name).append('\n')
            out.append(body.trim())
            if (out.length >= MAX_AGENTS_CHARS) break
        }
        val text = out.toString().trim()
        return text.takeIf { it.isNotEmpty() }?.take(MAX_AGENTS_CHARS)
    }

    /**
     * Discover `skills/<folder>/(SKILL.md|skill.md)` — Agent Skills progressive-disclosure index.
     */
    fun loadSkillIndex(root: File?): List<SkillIndexEntry> {
        if (root == null || !root.isDirectory) return emptyList()
        val skillsRoot = File(root, SKILLS_DIR)
        if (!skillsRoot.isDirectory) return emptyList()
        val out = ArrayList<SkillIndexEntry>(16)
        val folders = skillsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        for (folder in folders) {
            if (out.size >= MAX_SKILL_INDEX) break
            val skillFile = findSkillMd(folder) ?: continue
            val body = readTextCapped(skillFile, MAX_SKILL_BYTES) ?: continue
            val (name, desc) = parseNameDescription(folder.name, body)
            out += SkillIndexEntry(
                name = name,
                description = desc,
                folderName = folder.name,
                skillFile = skillFile,
            )
        }
        return out
    }

    fun findSkillMd(folder: File): File? {
        val children = folder.listFiles() ?: return null
        children.firstOrNull { it.isFile && it.name == "SKILL.md" }?.let { return it }
        return children.firstOrNull { it.isFile && it.name.equals("skill.md", ignoreCase = true) }
    }

    fun indexPrompt(skills: List<SkillIndexEntry>): String {
        if (skills.isEmpty()) return ""
        return buildString {
            appendLine("Available skills from /Minima/skills (call tool read_skill with name=… to load full instructions):")
            for (s in skills) {
                append("- ${s.name}")
                if (s.description.isNotBlank()) append(": ${s.description}")
                append(" (public:skills/${s.folderName})")
                appendLine()
            }
            appendLine("When a skill matches the user request, call read_skill first, then follow its instructions.")
        }
    }

    /** Minimal frontmatter name/description (no dependency on agent module). */
    fun parseNameDescription(folderName: String, body: String): Pair<String, String> {
        val text = body.replace("\r\n", "\n").trimStart()
        var name: String? = null
        var description: String? = null
        if (text.startsWith("---")) {
            val after = text.removePrefix("---").trimStart('\n', ' ')
            val close = after.indexOf("\n---")
            if (close >= 0) {
                val yaml = after.substring(0, close)
                for (line in yaml.lineSequence()) {
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) continue
                    val idx = t.indexOf(':')
                    if (idx <= 0) continue
                    val key = t.substring(0, idx).trim().lowercase().replace('-', '_')
                    var value = t.substring(idx + 1).trim()
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))
                    ) {
                        value = value.substring(1, value.length - 1)
                    }
                    when (key) {
                        "name" -> name = value.takeIf { it.isNotBlank() }
                        "description" -> description = value.takeIf { it.isNotBlank() }
                    }
                }
            }
        }
        val display = name?.takeIf { it.isNotBlank() } ?: folderName
        val desc = description?.takeIf { it.isNotBlank() }
            ?: body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("---") }
                ?.take(120)
            ?: ""
        return display to desc
    }

    fun readTextCapped(file: File, maxBytes: Int): String? {
        if (!file.isFile || !file.canRead()) return null
        if (file.length() <= 0L) return null
        return runCatching {
            file.inputStream().use { input ->
                val size = minOf(maxBytes, file.length().toInt().coerceAtLeast(1))
                val buf = ByteArray(size)
                var read = 0
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read <= 0) return@use null
                String(buf, 0, read, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** Resolve skill body by name for read_skill (public pack). */
    fun readSkillBody(name: String, root: File?): String? {
        val q = name.trim()
        if (q.isEmpty() || root == null) return null
        val entry = loadSkillIndex(root).firstOrNull {
            it.name.equals(q, ignoreCase = true) || it.folderName.equals(q, ignoreCase = true)
        } ?: return null
        return readTextCapped(entry.skillFile, MAX_SKILL_BYTES)
    }
}
