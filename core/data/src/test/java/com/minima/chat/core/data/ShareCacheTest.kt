package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.util.ShareCache
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun cleanup_dropsAgedFiles() {
        val dir = tmp.newFolder("shares")
        val old = java.io.File(dir, "old.md").also {
            it.writeText("x")
            it.setLastModified(System.currentTimeMillis() - ShareCache.MAX_AGE_MS - 1000)
        }
        val fresh = java.io.File(dir, "fresh.md").also { it.writeText("y") }
        val n = ShareCache.cleanup(dir, nowMs = System.currentTimeMillis())
        assertThat(n).isAtLeast(1)
        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
    }

    @Test
    fun cleanup_keepsOnlyMaxNewest() {
        val dir = tmp.newFolder("shares2")
        val now = System.currentTimeMillis()
        repeat(12) { i ->
            java.io.File(dir, "f$i.md").also {
                it.writeText("$i")
                it.setLastModified(now - i * 1000L)
            }
        }
        ShareCache.cleanup(dir, nowMs = now, maxFiles = 8, maxAgeMs = ShareCache.MAX_AGE_MS)
        val left = dir.listFiles()!!.size
        assertThat(left).isEqualTo(8)
        // newest (f0) kept, oldest dropped
        assertThat(java.io.File(dir, "f0.md").exists()).isTrue()
        assertThat(java.io.File(dir, "f11.md").exists()).isFalse()
    }

    @Test
    fun newShareFile_sanitizesStem() {
        val dir = tmp.newFolder("shares3")
        val f = ShareCache.newShareFile(dir, "Chat:/secret*", stampMs = 42)
        assertThat(f.name).startsWith("minima-")
        assertThat(f.name).endsWith("-42.md")
        assertThat(f.name).doesNotContain("/")
        assertThat(f.name).doesNotContain("*")
    }
}
