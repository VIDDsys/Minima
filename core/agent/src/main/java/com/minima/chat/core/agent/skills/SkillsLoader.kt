package com.minima.chat.core.agent.skills

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.minima.chat.core.agent.tools.MemorySearch
import com.minima.chat.core.agent.tools.PathSafety
import javax.inject.Inject
import javax.inject.Singleton

data class SkillDoc(
    /** Stable id — usually file stem (e.g. summarize-doc). */
    val name: String,
    /** Full raw markdown including frontmatter. */
    val body: String,
    val source: String,
    /** Frontmatter name or [name]. */
    val displayName: String = name,
    /** One-line description for the skill index. */
    val description: String = "",
    /** Instruction body after frontmatter (what read_skill returns). */
    val instructions: String = body.trim(),
    /** When true, inject a short always-on hint into the system prompt. */
    val alwaysApply: Boolean = false,
)

/**
 * Loads markdown skills from:
 * 1) app-private files/skills/
 * 2) bundled assets/skills/**/SKILL.md (seeded once into files/skills)
 * 3) optional SAF tree (skills/ subfolder or root *.md)
 */
@Singleton
class SkillsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureBundledSkills() {
        val local = File(context.filesDir, "skills")
        if (!local.exists()) local.mkdirs()
        try {
            val assetMgr = context.assets
            val roots = assetMgr.list("skills") ?: return
            for (name in roots) {
                // flat .md or folder/SKILL.md
                if (name.endsWith(".md")) {
                    if (!SkillNameSafety.isSafeMarkdownFileName(name)) continue
                    val dest = PathSafety.resolveSafe(local, name) ?: continue
                    if (!dest.exists()) {
                        assetMgr.open("skills/$name").use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                    }
                } else {
                    val children = assetMgr.list("skills/$name") ?: continue
                    if (children.contains("SKILL.md")) {
                        val destName = "$name.md"
                        if (!SkillNameSafety.isSafeMarkdownFileName(destName)) continue
                        if (!SkillNameSafety.isSafeStem(name)) continue
                        val dest = PathSafety.resolveSafe(local, destName) ?: continue
                        if (!dest.exists()) {
                            assetMgr.open("skills/$name/SKILL.md").use { input ->
                                dest.outputStream().use { input.copyTo(it) }
                            }
                        }
                    }
                    // Declarative HTTP tools for this skill pack
                    if (children.contains("tools.json")) {
                        if (!SkillNameSafety.isSafeStem(name)) continue
                        val packDir = PathSafety.resolveSafe(local, name) ?: continue
                        if (!packDir.exists()) packDir.mkdirs()
                        val toolsDest = File(packDir, "tools.json")
                        if (!PathSafety.isUnderRoot(local, toolsDest)) continue
                        if (!toolsDest.exists()) {
                            assetMgr.open("skills/$name/tools.json").use { input ->
                                toolsDest.outputStream().use { input.copyTo(it) }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun loadAll(safTreeUri: String? = null): List<SkillDoc> {
        ensureBundledSkills()
        val out = mutableListOf<SkillDoc>()
        val local = File(context.filesDir, "skills")
        if (!local.exists()) local.mkdirs()
        val localFiles = local.listFiles()
            ?.filter { it.isFile && SkillNameSafety.isSafeMarkdownFileName(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        for (f in localFiles) {
            if (out.size >= SkillNameSafety.MAX_SKILLS) break
            val safe = PathSafety.resolveSafe(local, f.name) ?: continue
            if (!PathSafety.isUnderRoot(local, safe)) continue
            when (val read = MemorySearch.readFileCapped(safe, SkillNameSafety.MAX_SKILL_BYTES)) {
                is MemorySearch.CappedRead.Text -> {
                    val body = SkillNameSafety.capInstructions(read.value)
                    out += toDoc(safe.nameWithoutExtension, body, "sandbox:${safe.name}")
                }
                else -> { /* skip binary / error / empty */ }
            }
        }
        if (!safTreeUri.isNullOrBlank() && out.size < SkillNameSafety.MAX_SKILLS) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri))
                val skillsDir = tree?.findFile("skills")?.takeIf { it.isDirectory } ?: tree
                val safFiles = skillsDir?.listFiles()
                    ?.filter { it.isFile && SkillNameSafety.isSafeMarkdownFileName(it.name.orEmpty()) }
                    ?.sortedBy { it.name?.lowercase().orEmpty() }
                    .orEmpty()
                for (doc in safFiles) {
                    if (out.size >= SkillNameSafety.MAX_SKILLS) break
                    val name = doc.name ?: continue
                    context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                        when (val read = MemorySearch.readStreamCapped(
                            stream,
                            SkillNameSafety.MAX_SKILL_BYTES,
                        )) {
                            is MemorySearch.CappedRead.Text -> {
                                val body = SkillNameSafety.capInstructions(read.value)
                                val stem = name.removeSuffix(".md").removeSuffix(".MD")
                                if (SkillNameSafety.isSafeStem(stem)) {
                                    out += toDoc(stem, body, "saf:$name")
                                }
                            }
                            else -> { }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out.distinctBy { it.name.lowercase() }.take(SkillNameSafety.MAX_SKILLS)
    }

    /**
     * Load global AGENT.md memory from (first hit wins):
     * 1) SAF tree root AGENT.md
     * 2) SAF tree skills/AGENT.md
     * 3) app filesDir/AGENT.md
     * 4) app filesDir/skills/AGENT.md
     */
    fun loadAgentMd(safTreeUri: String? = null): String? {
        fun readDoc(doc: DocumentFile?): String? {
            if (doc == null || !doc.isFile) return null
            return try {
                context.contentResolver.openInputStream(doc.uri)?.use {
                    when (val r = MemorySearch.readStreamCapped(it, SkillNameSafety.MAX_SKILL_BYTES)) {
                        is MemorySearch.CappedRead.Text -> SkillNameSafety.capInstructions(r.value)
                        else -> null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
        fun readFile(f: File): String? {
            if (!f.isFile) return null
            return try {
                when (val r = MemorySearch.readFileCapped(f, SkillNameSafety.MAX_SKILL_BYTES)) {
                    is MemorySearch.CappedRead.Text -> SkillNameSafety.capInstructions(r.value)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        if (!safTreeUri.isNullOrBlank()) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri))
                readDoc(tree?.findFile("AGENT.md"))?.let { return it }
                val skillsDir = tree?.findFile("skills")?.takeIf { it.isDirectory }
                readDoc(skillsDir?.findFile("AGENT.md"))?.let { return it }
            } catch (_: Exception) {
            }
        }
        readFile(File(context.filesDir, "AGENT.md"))?.let { return it }
        readFile(File(context.filesDir, "skills/AGENT.md"))?.let { return it }
        return null
    }

    /**
     * Compact skill index for the system prompt. Full bodies are **not** inlined —
     * the agent must call `read_skill` when a skill applies (lazy-load).
     * Skills with always_apply get a one-line reminder only (still use read_skill for body).
     */
    fun indexPrompt(skills: List<SkillDoc>): String = Companion.indexPrompt(skills)

    companion object {
        fun indexPrompt(skills: List<SkillDoc>): String {
            if (skills.isEmpty()) return ""
            return buildString {
                appendLine("Available skills (call tool read_skill with name=… to load full instructions):")
                skills.forEach { s ->
                    append("- ${s.name}")
                    if (s.displayName != s.name) append(" [${s.displayName}]")
                    if (s.description.isNotBlank()) append(": ${s.description}")
                    if (s.alwaysApply) append(" (always_apply)")
                    append(" (")
                    append(s.source)
                    appendLine(")")
                }
                appendLine("When a skill matches the user request, call read_skill first, then follow its instructions.")
            }
        }

        fun toDoc(stem: String, body: String, source: String): SkillDoc {
            val parsed = SkillFrontmatter.parse(body)
            val display = parsed.name?.takeIf { it.isNotBlank() } ?: stem
            val desc = parsed.description?.takeIf { it.isNotBlank() }
                ?: parsed.instructions.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    ?.take(120)
                ?: ""
            val instructions = if (parsed.hadFence) {
                parsed.instructions.ifBlank { body.trim() }
            } else {
                body.trim()
            }
            return SkillDoc(
                name = stem,
                body = body,
                source = source,
                displayName = display,
                description = desc,
                instructions = SkillNameSafety.capInstructions(instructions),
                alwaysApply = parsed.alwaysApply,
            )
        }
    }
}
