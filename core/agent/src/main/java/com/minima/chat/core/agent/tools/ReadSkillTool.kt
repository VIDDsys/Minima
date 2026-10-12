package com.minima.chat.core.agent.tools

import com.minima.chat.core.agent.skills.SkillDoc
import com.minima.chat.core.agent.skills.SkillFrontmatter
import com.minima.chat.core.agent.skills.SkillNameSafety
import com.minima.chat.core.agent.skills.SkillsLoader
import javax.inject.Inject

/**
 * Lazy-load a skill's instruction body by name.
 *
 * Skills are only summarized in the system prompt index; the agent must call this
 * tool to receive the full markdown instructions (industrial: keeps context lean).
 *
 * Hardened (v2.26): query path-safety via [SkillNameSafety]; output size capped.
 */
class ReadSkillTool @Inject constructor(
    private val skillsLoader: SkillsLoader,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "read_skill",
        description = "Load full instructions for an installed skill by name " +
            "(file stem or frontmatter name). Call when a listed skill matches the task.",
        parameters = JsonSchema.obj(
            "name" to JsonSchema.string("Skill id, e.g. summarize-doc or daily-notes"),
            required = listOf("name"),
        ),
        requiresGrant = Grant.None,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val raw = parseString(argumentsJson, "name").orEmpty()
        SkillNameSafety.rejectQueryReason(raw)?.let { return ToolResult(false, it) }
        val name = raw.trim()

        val saf = policyStore.policy.publicTreeUri
        val skills = try {
            skillsLoader.loadAll(saf)
        } catch (e: Exception) {
            return ToolResult(false, "Failed to load skills: ${e.message ?: "error"}")
        }
        val doc = SkillFrontmatter.findByName(skills, name)
        if (doc != null) {
            return ToolResult(true, formatSkillBody(doc))
        }
        // Public /storage/emulated/0/Minima/skills/<name>/SKILL.md
        val publicBody = runCatching {
            val root = com.minima.chat.core.data.workspace.PublicMinimaWorkspace.rootDir()
            com.minima.chat.core.data.workspace.PublicMinimaWorkspace.readSkillBody(name, root)
        }.getOrNull()
        if (!publicBody.isNullOrBlank()) {
            val capped = if (publicBody.length > MAX_OUT) {
                publicBody.take(MAX_OUT) + "\n…[truncated]"
            } else publicBody
            return ToolResult(
                true,
                buildString {
                    appendLine("# Skill: $name")
                    appendLine("source: public:/Minima/skills")
                    appendLine()
                    append(capped.trimEnd())
                    appendLine()
                }.trimEnd(),
            )
        }
        val available = buildList {
            addAll(skills.map { it.name })
            runCatching {
                val root = com.minima.chat.core.data.workspace.PublicMinimaWorkspace.rootDir()
                addAll(
                    com.minima.chat.core.data.workspace.PublicMinimaWorkspace.loadSkillIndex(root)
                        .map { it.name },
                )
            }
        }.distinct()
        if (available.isEmpty()) {
            return ToolResult(
                false,
                "No skills installed. Add SKILL.md under app skills/ or /storage/emulated/0/Minima/skills/<name>/.",
            )
        }
        return ToolResult(
            false,
            "Unknown skill: $name. Available: ${available.joinToString(", ")}",
        )
    }

    companion object {
        /** Output body cap (characters) for the model. */
        const val MAX_OUT = 14_000

        fun formatSkillBody(doc: SkillDoc): String {
            val body = doc.instructions.ifBlank { doc.body.trim() }
            val capped = if (body.length > MAX_OUT) {
                body.take(MAX_OUT) + "\n…[truncated]"
            } else {
                body
            }
            return buildString {
                appendLine("# Skill: ${doc.displayName}")
                appendLine("id: ${doc.name}")
                appendLine("source: ${doc.source}")
                if (doc.description.isNotBlank()) {
                    appendLine("description: ${doc.description}")
                }
                appendLine()
                append(capped.trimEnd())
                appendLine()
            }.trimEnd()
        }
    }
}
