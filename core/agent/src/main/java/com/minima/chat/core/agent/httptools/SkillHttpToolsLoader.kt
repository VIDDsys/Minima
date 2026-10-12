package com.minima.chat.core.agent.httptools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.minima.chat.core.agent.tools.AgentTool
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SkillHttpLoadResult(
    val tools: List<AgentTool>,
    val errors: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
)

/**
 * Discovers tools.json next to skill packs under app filesDir/skills and optional SAF tree
 * (nested tools.json or *.tools.json, depth-limited).
 */
@Singleton
class SkillHttpToolsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(safTreeUri: String? = null): SkillHttpLoadResult {
        val rawFiles = mutableListOf<Pair<String, String>>() // label to json
        val local = File(context.filesDir, "skills")
        if (local.isDirectory) {
            local.walkTopDown().maxDepth(4).forEach { f ->
                if (!f.isFile) return@forEach
                if (f.name == "tools.json" || f.name.endsWith(".tools.json")) {
                    runCatching { f.readText() }.getOrNull()?.let { rawFiles += "${f.relativeTo(local)}" to it }
                }
            }
        }
        if (!safTreeUri.isNullOrBlank()) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri))
                val skillsDir = tree?.findFile("skills")?.takeIf { it.isDirectory } ?: tree
                collectSafToolsJson(skillsDir, "saf", rawFiles)
            } catch (_: Exception) {
            }
        }

        val errors = mutableListOf<String>()
        val sources = mutableListOf<String>()
        val byName = linkedMapOf<String, AgentTool>()
        for ((label, raw) in rawFiles) {
            val parsed = DeclarativeHttpToolsParser.parse(raw, label)
            errors += parsed.errors
            if (parsed.tools.isNotEmpty()) sources += label
            for (def in parsed.tools) {
                byName[def.name] = DeclarativeHttpTool(def)
            }
        }
        return SkillHttpLoadResult(
            tools = byName.values.toList(),
            errors = errors,
            sources = sources,
        )
    }

    private fun collectSafToolsJson(
        dir: DocumentFile?,
        prefix: String,
        out: MutableList<Pair<String, String>>,
        depth: Int = 0,
    ) {
        if (dir == null || !dir.isDirectory || depth > 3) return
        dir.listFiles()?.forEach { child ->
            val name = child.name ?: return@forEach
            if (child.isFile && (name == "tools.json" || name.endsWith(".tools.json"))) {
                try {
                    context.contentResolver.openInputStream(child.uri)?.use { stream ->
                        out += "$prefix/$name" to stream.bufferedReader().readText()
                    }
                } catch (_: Exception) {
                }
            } else if (child.isDirectory) {
                collectSafToolsJson(child, "$prefix/$name", out, depth + 1)
            }
        }
    }
}
