package com.minima.chat.core.agent.skills

/**
 * Minimal YAML-ish frontmatter for SKILL.md packs.
 *
 * Recognizes a leading `---` … `---` block and extracts `name` / `description`
 * (and optional `always_apply` / `alwaysApply`). Body after the closing fence
 * is the instruction text the agent should follow after [read_skill].
 *
 * Not a full YAML parser — line-oriented key: value only (enough for Agent skills).
 */
object SkillFrontmatter {

    data class Parsed(
        val name: String?,
        val description: String?,
        /** True when frontmatter says always_apply / alwaysApply. */
        val alwaysApply: Boolean,
        /** Markdown after the closing `---` (trimmed). Empty if none. */
        val instructions: String,
        /** Raw full document (unchanged). */
        val raw: String,
        val hadFence: Boolean,
    )

    private val KEY_VALUE = Regex("""^([A-Za-z_][A-Za-z0-9_-]*)\s*:\s*(.*)$""")

    fun parse(raw: String): Parsed {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        val trimmedStart = text.trimStart()
        if (!trimmedStart.startsWith("---")) {
            return Parsed(
                name = null,
                description = null,
                alwaysApply = false,
                instructions = text.trim(),
                raw = raw,
                hadFence = false,
            )
        }
        // Drop opening ---
        val afterOpen = trimmedStart.removePrefix("---").trimStart('\n', ' ')
        val closeIdx = afterOpen.indexOf("\n---")
        if (closeIdx < 0) {
            // Unclosed fence — treat entire doc as body
            return Parsed(
                name = null,
                description = null,
                alwaysApply = false,
                instructions = text.trim(),
                raw = raw,
                hadFence = false,
            )
        }
        val yamlBlock = afterOpen.substring(0, closeIdx)
        var rest = afterOpen.substring(closeIdx + 1) // starts with ---
        rest = rest.removePrefix("---").trimStart('\n', ' ', '\t')
        val map = linkedMapOf<String, String>()
        for (line in yamlBlock.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val m = KEY_VALUE.matchEntire(t) ?: continue
            val key = m.groupValues[1].lowercase().replace('-', '_')
            var value = m.groupValues[2].trim()
            // Strip simple quotes
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))
            ) {
                value = value.substring(1, value.length - 1)
            }
            map[key] = value
        }
        val name = map["name"]?.takeIf { it.isNotBlank() }
        val description = map["description"]?.takeIf { it.isNotBlank() }
        val always = when (map["always_apply"]?.lowercase() ?: map["alwaysapply"]?.lowercase()) {
            "true", "yes", "1" -> true
            else -> false
        }
        return Parsed(
            name = name,
            description = description,
            alwaysApply = always,
            instructions = rest.trim(),
            raw = raw,
            hadFence = true,
        )
    }

    /** Resolve a display/description for the skill index. */
    fun describe(fileStem: String, body: String): Pair<String, String> {
        val p = parse(body)
        val display = p.name?.takeIf { it.isNotBlank() } ?: fileStem
        val desc = p.description?.takeIf { it.isNotBlank() }
            ?: p.instructions.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("---") }
                ?.take(120)
            ?: ""
        return display to desc
    }

    /**
     * Case-insensitive lookup by skill id (file stem) or frontmatter name.
     */
    fun findByName(skills: List<SkillDoc>, query: String): SkillDoc? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val exact = skills.firstOrNull { it.name.equals(q, ignoreCase = true) }
        if (exact != null) return exact
        return skills.firstOrNull { it.displayName.equals(q, ignoreCase = true) }
    }
}
