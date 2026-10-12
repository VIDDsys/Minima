package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.tools.PathSafety
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathSafetyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun resolvesRelativeInsideRoot() {
        val root = tmp.newFolder("ws")
        val f = PathSafety.resolveSafe(root, "notes/a.txt")
        assertThat(f).isNotNull()
        assertThat(PathSafety.isUnderRoot(root, f!!)).isTrue()
    }

    @Test
    fun blocksEscapeWithDotDot() {
        val root = tmp.newFolder("ws")
        val escaped = PathSafety.resolveSafe(root, "../secret.txt")
        assertThat(escaped).isNull()
    }

    @Test
    fun blocksAbsoluteLookingEscape() {
        val root = tmp.newFolder("ws")
        val f = PathSafety.resolveSafe(root, "../../etc/passwd")
        assertThat(f).isNull()
    }

    @Test
    fun allowsDotCurrent() {
        val root = tmp.newFolder("ws")
        val f = PathSafety.resolveSafe(root, ".")
        assertThat(f).isNotNull()
        assertThat(f!!.canonicalPath).isEqualTo(root.canonicalPath)
    }

    @Test
    fun nestedOk() {
        val root = tmp.newFolder("ws")
        File(root, "a/b").mkdirs()
        val f = PathSafety.resolveSafe(root, "a/b/c.txt")
        assertThat(f).isNotNull()
        assertThat(f!!.path).contains("a/b/c.txt")
    }

    @Test
    fun rejectsNullByte() {
        assertThat(PathSafety.sanitizeRelative("a\u0000b")).isNull()
        val root = tmp.newFolder("ws")
        assertThat(PathSafety.resolveSafe(root, "a\u0000b")).isNull()
    }

    @Test
    fun rejectsAbsoluteUnix() {
        assertThat(PathSafety.sanitizeRelative("/etc/passwd")).isNull()
        val root = tmp.newFolder("ws")
        assertThat(PathSafety.resolveSafe(root, "/etc/passwd")).isNull()
    }

    @Test
    fun rejectsWindowsDrive() {
        assertThat(PathSafety.sanitizeRelative("C:/Windows/System32")).isNull()
    }

    @Test
    fun rejectsUriScheme() {
        assertThat(PathSafety.sanitizeRelative("file://etc/passwd")).isNull()
    }

    @Test
    fun normalizesBackslashes() {
        val root = tmp.newFolder("ws")
        File(root, "notes").mkdirs()
        val f = PathSafety.resolveSafe(root, "notes\\a.txt")
        assertThat(f).isNotNull()
        assertThat(PathSafety.isUnderRoot(root, f!!)).isTrue()
    }

    @Test
    fun emptyBecomesRoot() {
        val root = tmp.newFolder("ws")
        val f = PathSafety.resolveSafe(root, "  ")
        assertThat(f).isNotNull()
        assertThat(f!!.canonicalPath).isEqualTo(root.canonicalPath)
    }

    @Test
    fun sanitizeRejectsDotDotSegments() {
        assertThat(PathSafety.sanitizeRelative("../secret")).isNull()
        assertThat(PathSafety.sanitizeRelative("a/../b")).isNull()
        assertThat(PathSafety.sanitizeRelative("a/./b")).isNull()
    }

    @Test
    fun sanitizeAllowsSimpleRelative() {
        assertThat(PathSafety.sanitizeRelative("notes/todo.md")).isEqualTo("notes/todo.md")
    }
}
