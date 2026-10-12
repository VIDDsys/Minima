package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.workspace.PublicMinimaWorkspace
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PublicMinimaWorkspaceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun agentsFileName_caseInsensitive() {
        assertThat(PublicMinimaWorkspace.isAgentsFileName("AGENTS.md")).isTrue()
        assertThat(PublicMinimaWorkspace.isAgentsFileName("agents.md")).isTrue()
        assertThat(PublicMinimaWorkspace.isAgentsFileName("Agents.MD")).isTrue()
        assertThat(PublicMinimaWorkspace.isAgentsFileName("AGENT.md")).isFalse()
        assertThat(PublicMinimaWorkspace.isAgentsFileName("notes.md")).isFalse()
    }

    @Test
    fun loadAllAgents_concatenates() {
        val root = tmp.newFolder("Minima")
        File(root, "AGENTS.md").writeText("alpha rules")
        File(root, "agents.md").writeText("beta rules")
        val text = PublicMinimaWorkspace.loadAllAgentsMarkdown(root)
        assertThat(text).isNotNull()
        assertThat(text!!).contains("alpha rules")
    }

    @Test
    fun skillIndex_readsFrontmatter() {
        val root = tmp.newFolder("Minima")
        val skillDir = File(root, "skills/summarize").also { it.mkdirs() }
        File(skillDir, "SKILL.md").writeText(
            """
            ---
            name: summarize-doc
            description: Summarize long documents
            ---
            # Body
            Do the work.
            """.trimIndent(),
        )
        val idx = PublicMinimaWorkspace.loadSkillIndex(root)
        assertThat(idx).hasSize(1)
        assertThat(idx[0].name).isEqualTo("summarize-doc")
        assertThat(idx[0].description).contains("Summarize")
        val prompt = PublicMinimaWorkspace.indexPrompt(idx)
        assertThat(prompt).contains("summarize-doc")
        assertThat(prompt).contains("read_skill")
    }

    @Test
    fun skillMd_lowercaseAccepted() {
        val root = tmp.newFolder("Minima2")
        val skillDir = File(root, "skills/demo").also { it.mkdirs() }
        File(skillDir, "skill.md").writeText("plain skill body without fence")
        val idx = PublicMinimaWorkspace.loadSkillIndex(root)
        assertThat(idx).hasSize(1)
        assertThat(idx[0].name).isEqualTo("demo")
    }

    @Test
    fun parseNameDescription_fallback() {
        val (n, d) = PublicMinimaWorkspace.parseNameDescription("fold", "# Title\n\nHello world detail")
        assertThat(n).isEqualTo("fold")
        assertThat(d).contains("Hello")
    }

    @Test
    fun writeAgents_roundTrip() {
        val root = tmp.newFolder("MinimaWrite")
        val path = PublicMinimaWorkspace.writeAgentsMarkdown(root, "hello agents")
        assertThat(path).isNotNull()
        assertThat(PublicMinimaWorkspace.loadAgentsEditorText(root)).contains("hello agents")
        assertThat(PublicMinimaWorkspace.loadAllAgentsMarkdown(root)).contains("hello agents")
    }

    @Test
    fun primaryAgents_prefersExisting() {
        val root = tmp.newFolder("MinimaPrimary")
        File(root, "AGENTS.md").writeText("canon")
        val f = PublicMinimaWorkspace.primaryAgentsFile(root)
        assertThat(f!!.name.equals("AGENTS.md", ignoreCase = true)).isTrue()
        assertThat(PublicMinimaWorkspace.loadAgentsEditorText(root).trim()).isEqualTo("canon")
    }
}
