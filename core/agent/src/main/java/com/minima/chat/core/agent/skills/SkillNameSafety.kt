package com.minima.chat.core.agent.skills

/**
 * Path / name hardening for skill packs and [read_skill] queries.
 * Pure JVM — no Android.
 *
 * Skills are identified by file stem or frontmatter display name; never treat
 * the query as a filesystem path.
 */
object SkillNameSafety {
    const val MAX_QUERY_LEN = 120
    const val MAX_STEM_LEN = 80
    /** Cap bytes read per skill file (stream-capped; never trust length). */
    const val MAX_SKILL_BYTES = 24 * 1024
    /** Cap instruction / body characters after UTF-8 decode. */
    const val MAX_INSTRUCTION_CHARS = 16_000
    /** Soft cap on how many skill docs we keep in memory. */
    const val MAX_SKILLS = 64

    /**
     * Safe [read_skill] name argument (stem or display name).
     * Rejects path separators, traversal, absolute/URI forms, controls.
     * Allows spaces and common punctuation for display names (e.g. "Daily Notes").
     */
    fun isSafeQuery(name: String): Boolean {
        val t = name.trim()
        if (t.isEmpty() || t.length > MAX_QUERY_LEN) return false
        if (t.any { it.code < 32 }) return false
        if (t.contains('/') || t.contains('\\')) return false
        if (t.contains("..")) return false
        if (t.contains("://")) return false
        if (t.startsWith("~") || t.startsWith(".")) return false
        if (t.length >= 2 && t[1] == ':') return false // C:\...
        // No path-like trailing dots / separators after trim
        if (t.endsWith(".") || t.endsWith(" ")) return false
        return true
    }

    /** Human-readable reject reason, or null if [name] is usable. */
    fun rejectQueryReason(name: String): String? {
        val t = name.trim()
        if (t.isEmpty()) return "name required"
        if (t.length > MAX_QUERY_LEN) return "name too long (max $MAX_QUERY_LEN)"
        if (!isSafeQuery(t)) return "Unsafe skill name (no paths or control characters)"
        return null
    }

    /** File stem only: [A-Za-z0-9._-]{1..MAX_STEM_LEN}, no leading dot. */
    fun isSafeStem(stem: String): Boolean {
        if (stem.isEmpty() || stem.length > MAX_STEM_LEN) return false
        if (stem.startsWith(".")) return false
        if (stem.any { it.code < 32 }) return false
        if (stem.contains("..") || stem.contains('/') || stem.contains('\\')) return false
        return stem.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
    }

    /** Accept only `stem.md` with a safe stem (case-insensitive .md). */
    fun isSafeMarkdownFileName(fileName: String): Boolean {
        val n = fileName.trim()
        if (!n.endsWith(".md", ignoreCase = true)) return false
        val stem = n.dropLast(3)
        return isSafeStem(stem)
    }

    fun capInstructions(text: String): String =
        if (text.length <= MAX_INSTRUCTION_CHARS) text
        else text.take(MAX_INSTRUCTION_CHARS)
}
