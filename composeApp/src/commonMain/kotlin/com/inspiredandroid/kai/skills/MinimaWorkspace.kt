package com.inspiredandroid.kai.skills

/**
 * The phone's durable user workspace at `/storage/emulated/0/Minima`
 * (shell: `/sdcard/Minima`). Same bytes as the sandbox bind. Not a second copy of `/root`.
 *
 * Platforms without shared storage use [EmptyMinimaWorkspace].
 */
interface MinimaWorkspace {
    /** `Minima/AGENTS.md`, trimmed and capped, or null when missing or unreadable. */
    suspend fun readAgentsMd(): String? = null

    /**
     * `Minima/skills/<id>/SKILL.md`. Each manifest has [SkillManifest.fromPublicStorage] set.
     * Empty when All files access is off or the folder is absent.
     */
    suspend fun loadSkills(): List<SkillManifest> = emptyList()

    /** Deletes `Minima/skills/<id>`. False when there was nothing to remove. */
    suspend fun deleteSkill(id: String): Boolean = false
}

object EmptyMinimaWorkspace : MinimaWorkspace

/** Standing instructions injected into one prompt. Longer files are cut at the boundary. */
internal const val MAX_AGENTS_MD_CHARS = 6_000

expect fun createMinimaWorkspace(): MinimaWorkspace
