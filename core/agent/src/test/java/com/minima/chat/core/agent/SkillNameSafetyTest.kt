package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.skills.SkillNameSafety
import com.minima.chat.core.agent.tools.MemorySearch
import com.minima.chat.core.agent.tools.PathSafety
import com.minima.chat.core.agent.tools.ReadSkillTool
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkillNameSafetyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun query_allowsSimpleStemAndDisplayName() {
        assertThat(SkillNameSafety.isSafeQuery("summarize-doc")).isTrue()
        assertThat(SkillNameSafety.isSafeQuery("Daily Notes")).isTrue()
        assertThat(SkillNameSafety.rejectQueryReason("  daily-notes  ")).isNull()
    }

    @Test
    fun query_rejectsPathTraversalAndAbsolute() {
        assertThat(SkillNameSafety.isSafeQuery("../secret")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("a/../b")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("/etc/passwd")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("skills\\evil")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("file://x")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("C:/Windows")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("~/.ssh/id_rsa")).isFalse()
        assertThat(SkillNameSafety.isSafeQuery("a\u0000b")).isFalse()
        assertThat(SkillNameSafety.rejectQueryReason("../x")).isNotNull()
    }

    @Test
    fun query_rejectsEmptyAndTooLong() {
        assertThat(SkillNameSafety.rejectQueryReason("")).isEqualTo("name required")
        assertThat(SkillNameSafety.rejectQueryReason("   ")).isEqualTo("name required")
        assertThat(SkillNameSafety.rejectQueryReason("x".repeat(121))).contains("too long")
    }

    @Test
    fun stem_and_filename_rules() {
        assertThat(SkillNameSafety.isSafeStem("summarize-doc")).isTrue()
        assertThat(SkillNameSafety.isSafeStem("foo_bar.v2")).isTrue()
        assertThat(SkillNameSafety.isSafeStem("../x")).isFalse()
        assertThat(SkillNameSafety.isSafeStem(".hidden")).isFalse()
        assertThat(SkillNameSafety.isSafeStem("has space")).isFalse()
        assertThat(SkillNameSafety.isSafeMarkdownFileName("ok.md")).isTrue()
        assertThat(SkillNameSafety.isSafeMarkdownFileName("OK.MD")).isTrue()
        assertThat(SkillNameSafety.isSafeMarkdownFileName("../x.md")).isFalse()
        assertThat(SkillNameSafety.isSafeMarkdownFileName("x.txt")).isFalse()
    }

    @Test
    fun capInstructions_truncates() {
        val huge = "a".repeat(SkillNameSafety.MAX_INSTRUCTION_CHARS + 50)
        assertThat(SkillNameSafety.capInstructions(huge).length)
            .isEqualTo(SkillNameSafety.MAX_INSTRUCTION_CHARS)
    }

    @Test
    fun skillsDir_resolveSafe_blocksEscapeFileName() {
        val root = tmp.newFolder("skills")
        // Even if somehow listed, resolveSafe must refuse traversal names
        assertThat(PathSafety.resolveSafe(root, "../escape.md")).isNull()
        assertThat(PathSafety.sanitizeRelative("..\\escape.md")).isNull()
        val ok = File(root, "good.md").also { it.writeText("# hi") }
        val resolved = PathSafety.resolveSafe(root, "good.md")
        assertThat(resolved).isNotNull()
        assertThat(resolved!!.canonicalPath).isEqualTo(ok.canonicalPath)
    }

    @Test
    fun cappedRead_skipsBinaryDisguisedAsMd() {
        val root = tmp.newFolder("skills2")
        val fake = File(root, "evil.md")
        fake.writeBytes(byteArrayOf(0x00, 0x01, 0x02) + "SECRET".toByteArray())
        val read = MemorySearch.readFileCapped(fake, SkillNameSafety.MAX_SKILL_BYTES)
        assertThat(read).isInstanceOf(MemorySearch.CappedRead.Binary::class.java)
    }

    @Test
    fun cappedRead_limitsHugeSkillFile() {
        val root = tmp.newFolder("skills3")
        val huge = File(root, "big.md")
        huge.writeText("x".repeat(SkillNameSafety.MAX_SKILL_BYTES + 8_000))
        val read = MemorySearch.readFileCapped(huge, SkillNameSafety.MAX_SKILL_BYTES)
        assertThat(read).isInstanceOf(MemorySearch.CappedRead.Text::class.java)
        val text = read as MemorySearch.CappedRead.Text
        assertThat(text.hitCap).isTrue()
        assertThat(text.bytesRead).isAtMost(SkillNameSafety.MAX_SKILL_BYTES)
    }

    @Test
    fun formatSkillBody_stillCapsOutput() {
        val doc = com.minima.chat.core.agent.skills.SkillDoc(
            name = "big",
            body = "x".repeat(20_000),
            source = "s",
            instructions = "y".repeat(20_000),
        )
        val out = ReadSkillTool.formatSkillBody(doc)
        assertThat(out).contains("…[truncated]")
        assertThat(out.length).isLessThan(ReadSkillTool.MAX_OUT + 200)
    }
}
