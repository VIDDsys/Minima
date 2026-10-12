package com.inspiredandroid.kai.skills

import android.content.Context
import com.inspiredandroid.kai.linux.publicStorageRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.File

actual fun createMinimaWorkspace(): MinimaWorkspace = AndroidMinimaWorkspace()

/**
 * Reads `/storage/emulated/0/Minima` with Android file APIs. Does not follow a path
 * that canonicalizes outside that folder. No symlink from `~/skills` — shared storage
 * is not where the sandbox runs binaries, and a link would dangle when All files access
 * is off.
 */
internal class AndroidMinimaWorkspace : MinimaWorkspace {
    private val context: Context by inject(Context::class.java)

    override suspend fun readAgentsMd(): String? = withContext(Dispatchers.IO) {
        val file = minimaDir()?.let { File(it, AGENTS_FILE) } ?: return@withContext null
        if (!file.isFile) return@withContext null
        val text = runCatching { readCapped(file, MAX_AGENTS_MD_CHARS * 4) }.getOrNull() ?: return@withContext null
        val cleaned = text.replace("\u0000", "").trim()
        if (cleaned.isEmpty()) return@withContext null
        if (cleaned.length <= MAX_AGENTS_MD_CHARS) {
            cleaned
        } else {
            cleaned.take(MAX_AGENTS_MD_CHARS).trimEnd() + "\n…(truncated)"
        }
    }

    override suspend fun loadSkills(): List<SkillManifest> = withContext(Dispatchers.IO) {
        val dir = minimaDir()?.let { File(it, SKILLS_DIR) } ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()
        dir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { folder -> parseSkillDir(folder) }
            .sortedBy { it.id }
    }

    override suspend fun deleteSkill(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSafeSkillId(id)) return@withContext false
        val skills = minimaDir()?.let { File(it, SKILLS_DIR) } ?: return@withContext false
        val target = File(skills, id)
        if (!target.isDirectory) return@withContext false
        val root = runCatching { skills.canonicalFile }.getOrNull() ?: return@withContext false
        val canonical = runCatching { target.canonicalFile }.getOrNull() ?: return@withContext false
        if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) return@withContext false
        canonical.deleteRecursively()
    }

    private fun minimaDir(): File? {
        val root = context.publicStorageRoot() ?: return null
        return File(root, WORKSPACE_DIR)
    }

    private fun parseSkillDir(folder: File): SkillManifest? {
        if (!isSafeSkillId(folder.name)) return null
        val md = File(folder, "SKILL.md")
        if (!md.isFile) return null
        val text = runCatching { readCapped(md, MAX_SKILL_MD_BYTES) }.getOrNull() ?: return null
        val parsed = SkillFrontmatterParser.parse(text) as? SkillFrontmatterParser.Result.Ok ?: return null
        // The folder name is the id uninstall deletes. A mismatch would remove the wrong tree.
        if (parsed.id != folder.name) return null
        val files = folder.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name != "SKILL.md" && !it.name.contains('/') }
            .map { it.name }
            .sorted()
        return SkillManifest(
            id = parsed.id,
            displayName = SkillFrontmatterParser.displayName(parsed.id),
            description = parsed.description,
            body = parsed.body,
            bundledFilePaths = files,
            fromPublicStorage = true,
        )
    }

    private fun readCapped(file: File, maxBytes: Int): String {
        file.inputStream().use { input ->
            val buf = ByteArray(maxBytes)
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            return buf.copyOf(read).decodeToString()
        }
    }

    private companion object {
        const val WORKSPACE_DIR = "Minima"
        const val SKILLS_DIR = "skills"
        const val AGENTS_FILE = "AGENTS.md"
        const val MAX_SKILL_MD_BYTES = 256 * 1024
    }
}

private fun isSafeSkillId(id: String): Boolean {
    if (id.length !in 1..64) return false
    if (!id.all { (it in 'a'..'z') || (it in '0'..'9') || it == '-' }) return false
    return !id.startsWith('-') && !id.endsWith('-') && "--" !in id
}
