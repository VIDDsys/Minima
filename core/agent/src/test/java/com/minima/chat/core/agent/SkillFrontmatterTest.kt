package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.skills.SkillDoc
import com.minima.chat.core.agent.skills.SkillFrontmatter
import com.minima.chat.core.agent.skills.SkillsLoader
import com.minima.chat.core.agent.tools.ReadSkillTool
import org.junit.Test

class SkillFrontmatterTest {

    private val sample = """
        ---
        name: summarize-doc
        description: Turn long notes into a short summary.
        always_apply: false
        ---
        # Summarize

        1. Read the file.
        2. Write TL;DR.
    """.trimIndent()

    @Test
    fun parsesNameDescriptionAndBody() {
        val p = SkillFrontmatter.parse(sample)
        assertThat(p.hadFence).isTrue()
        assertThat(p.name).isEqualTo("summarize-doc")
        assertThat(p.description).isEqualTo("Turn long notes into a short summary.")
        assertThat(p.alwaysApply).isFalse()
        assertThat(p.instructions).contains("# Summarize")
        assertThat(p.instructions).doesNotContain("---")
    }

    @Test
    fun alwaysApplyTrueVariants() {
        val raw = """
            ---
            name: x
            description: d
            alwaysApply: yes
            ---
            body
        """.trimIndent()
        assertThat(SkillFrontmatter.parse(raw).alwaysApply).isTrue()
    }

    @Test
    fun noFenceUsesWholeBody() {
        val p = SkillFrontmatter.parse("# Just markdown\n\nHello")
        assertThat(p.hadFence).isFalse()
        assertThat(p.name).isNull()
        assertThat(p.instructions).contains("Just markdown")
    }

    @Test
    fun unclosedFenceFallsBack() {
        val p = SkillFrontmatter.parse("---\nname: broken\nno close")
        assertThat(p.hadFence).isFalse()
        assertThat(p.instructions).contains("name: broken")
    }

    @Test
    fun stripsSimpleQuotes() {
        val raw = """
            ---
            name: "quoted-name"
            description: 'A quoted desc'
            ---
            body
        """.trimIndent()
        val p = SkillFrontmatter.parse(raw)
        assertThat(p.name).isEqualTo("quoted-name")
        assertThat(p.description).isEqualTo("A quoted desc")
    }

    @Test
    fun toDocPrefersFrontmatter() {
        val doc = SkillsLoader.toDoc("file-stem", sample, "sandbox:file-stem.md")
        assertThat(doc.name).isEqualTo("file-stem")
        assertThat(doc.displayName).isEqualTo("summarize-doc")
        assertThat(doc.description).contains("short summary")
        assertThat(doc.instructions).startsWith("# Summarize")
    }

    @Test
    fun findByNameMatchesStemOrDisplay() {
        val a = SkillsLoader.toDoc("summarize-doc", sample, "sandbox:a.md")
        val b = SkillDoc(name = "daily-notes", body = "x", source = "s", displayName = "Daily Notes")
        assertThat(SkillFrontmatter.findByName(listOf(a, b), "summarize-doc")!!.name)
            .isEqualTo("summarize-doc")
        assertThat(SkillFrontmatter.findByName(listOf(a, b), "Daily Notes")!!.name)
            .isEqualTo("daily-notes")
        assertThat(SkillFrontmatter.findByName(listOf(a, b), "missing")).isNull()
    }

    @Test
    fun formatSkillBodyIncludesMetaAndCaps() {
        val doc = SkillsLoader.toDoc("summarize-doc", sample, "sandbox:x.md")
        val out = ReadSkillTool.formatSkillBody(doc)
        assertThat(out).contains("# Skill: summarize-doc")
        assertThat(out).contains("id: summarize-doc")
        assertThat(out).contains("source: sandbox:x.md")
        assertThat(out).contains("# Summarize")
        val huge = SkillDoc(
            name = "big",
            body = "x".repeat(20_000),
            source = "s",
            instructions = "y".repeat(20_000),
        )
        val capped = ReadSkillTool.formatSkillBody(huge)
        assertThat(capped).contains("…[truncated]")
        assertThat(capped.length).isLessThan(ReadSkillTool.MAX_OUT + 200)
    }

    @Test
    fun indexPromptMentionsReadSkill() {
        val skills = listOf(SkillsLoader.toDoc("summarize-doc", sample, "sandbox:x.md"))
        val index = SkillsLoader.indexPrompt(skills)
        assertThat(index).contains("read_skill")
        assertThat(index).contains("summarize-doc")
        assertThat(index).contains("short summary")
        assertThat(index).doesNotContain("# Summarize") // body not inlined
    }
}
