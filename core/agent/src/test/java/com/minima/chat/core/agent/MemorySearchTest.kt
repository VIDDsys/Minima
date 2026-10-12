package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.tools.MemorySearch
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class MemorySearchTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun searchText_finds_line_and_snippet() {
        val hits = MemorySearch.searchText(
            "notes/a.md",
            "Hello\nPrefer Chinese replies\nBye",
            "chinese",
        )
        assertThat(hits).hasSize(1)
        assertThat(hits[0].line).isEqualTo(2)
        assertThat(hits[0].path).isEqualTo("notes/a.md")
        assertThat(hits[0].snippet.lowercase()).contains("chinese")
    }

    @Test
    fun searchText_empty_query_returns_empty() {
        assertThat(MemorySearch.searchText("a.md", "hello", "  ")).isEmpty()
    }

    @Test
    fun searchText_caps_huge_line() {
        val huge = "x".repeat(MemorySearch.MAX_LINE_CHARS + 500) + "FINDME"
        // FINDME is past the cap — should not match after truncate
        val hitsPast = MemorySearch.searchText("a.md", huge, "FINDME")
        assertThat(hitsPast).isEmpty()
        val huge2 = "FINDME" + "y".repeat(MemorySearch.MAX_LINE_CHARS + 100)
        val hits = MemorySearch.searchText("a.md", huge2, "FINDME")
        assertThat(hits).hasSize(1)
    }

    @Test
    fun searchFileTree_scans_nested_md() {
        val root = tmp.newFolder("ws")
        File(root, "notes").mkdirs()
        File(root, "notes/2026-10-12.md").writeText("# Daily\nBuy milk\nCall mom")
        File(root, "AGENT.md").writeText("Prefer short answers")
        File(root, "bin.dat").writeBytes(ByteArray(8))

        val result = MemorySearch.searchFileTree(root, "milk", relBase = "sandbox")
        assertThat(result.filesScanned).isAtLeast(2)
        assertThat(result.hits).isNotEmpty()
        assertThat(result.hits[0].path).contains("notes/2026-10-12.md")
        assertThat(result.hits[0].snippet.lowercase()).contains("milk")
    }

    @Test
    fun searchFileTree_respects_max_results() {
        val root = tmp.newFolder("many")
        repeat(30) { i ->
            File(root, "f$i.md").writeText("token_$i unique_marker_$i")
        }
        val result = MemorySearch.searchFileTree(root, "unique_marker", maxResults = 5)
        assertThat(result.hits.size).isAtMost(5)
        assertThat(result.truncated).isTrue()
    }

    @Test
    fun searchFileTree_skips_binary_disguised_as_md() {
        val root = tmp.newFolder("binws")
        val fake = File(root, "notes.md")
        // NUL in payload → binary sniff
        fake.writeBytes(byteArrayOf(0x48, 0x49, 0x00, 0x01, 0x02) + "secret_token".toByteArray())
        File(root, "ok.md").writeText("secret_token in text")
        val result = MemorySearch.searchFileTree(root, "secret_token")
        assertThat(result.hits).hasSize(1)
        assertThat(result.hits[0].path).contains("ok.md")
        // binary may still count as scanned attempt depending on order — at least ok found
        assertThat(result.filesScanned).isAtLeast(1)
    }

    @Test
    fun searchFileTree_skips_symlink_escape() {
        val root = tmp.newFolder("sand")
        val outside = tmp.newFolder("outside")
        File(outside, "leak.md").writeText("LEAKED_SECRET_VALUE")
        File(root, "safe.md").writeText("safe content LEAKED_SECRET_VALUE no wait safe_only")
        // create symlink if supported
        val link = File(root, "escape_link")
        val created = try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        } catch (_: Exception) {
            false
        }
        if (!created) {
            // Environment may disallow symlinks — still assert safe file works
            val result = MemorySearch.searchFileTree(root, "safe_only")
            assertThat(result.hits).isNotEmpty()
            return
        }
        val result = MemorySearch.searchFileTree(root, "LEAKED_SECRET_VALUE")
        // Must not return hits from outside/leak.md
        assertThat(result.hits.none { it.path.contains("leak.md") }).isTrue()
        assertThat(result.hits.none { it.path.contains("outside") }).isTrue()
    }

    @Test
    fun searchFileTree_respects_total_byte_budget() {
        val root = tmp.newFolder("budget")
        // Three 40KB text files; total budget 50KB → cannot fully scan all
        val chunk = "needle " + "a".repeat(40 * 1024)
        repeat(3) { i ->
            File(root, "big$i.md").writeText(chunk)
        }
        val result = MemorySearch.searchFileTree(
            root,
            "needle",
            maxTotalBytes = 50 * 1024,
        )
        assertThat(result.truncated).isTrue()
        // Should still find at least one hit from first file(s)
        assertThat(result.hits).isNotEmpty()
        assertThat(result.filesScanned).isLessThan(3)
    }

    @Test
    fun searchFileTree_skips_oversized_via_capped_read() {
        val root = tmp.newFolder("bigone")
        // File larger than MAX_FILE_BYTES but with match only past cap — capped read still searches prefix
        val prefix = "hello visible\n"
        val rest = "z".repeat(MemorySearch.MAX_FILE_BYTES)
        File(root, "huge.md").writeText(prefix + rest + "\nhidden_marker_past_cap")
        val result = MemorySearch.searchFileTree(root, "visible")
        assertThat(result.hits).isNotEmpty()
        val miss = MemorySearch.searchFileTree(root, "hidden_marker_past_cap")
        assertThat(miss.hits).isEmpty()
    }

    @Test
    fun looksBinary_detects_nul_and_controls() {
        assertThat(MemorySearch.looksBinary(byteArrayOf(1, 2, 3, 0, 5))).isTrue()
        assertThat(MemorySearch.looksBinary("hello world\n".toByteArray())).isFalse()
        assertThat(MemorySearch.looksBinary(ByteArray(0))).isFalse()
        val controls = ByteArray(100) { 0x01 }
        assertThat(MemorySearch.looksBinary(controls)).isTrue()
    }

    @Test
    fun readStreamCapped_limits_and_sniffs() {
        val text = "abc".repeat(1000)
        val r = MemorySearch.readStreamCapped(ByteArrayInputStream(text.toByteArray()), maxBytes = 50)
        assertThat(r).isInstanceOf(MemorySearch.CappedRead.Text::class.java)
        val t = r as MemorySearch.CappedRead.Text
        assertThat(t.bytesRead).isAtMost(50)
        assertThat(t.hitCap).isTrue()

        val bin = MemorySearch.readStreamCapped(
            ByteArrayInputStream(byteArrayOf(0x00, 0x01, 0x02, 0x03)),
            maxBytes = 100,
        )
        assertThat(bin).isEqualTo(MemorySearch.CappedRead.Binary)
    }

    @Test
    fun clampMaxResults_bounds() {
        assertThat(MemorySearch.clampMaxResults(null)).isEqualTo(20)
        assertThat(MemorySearch.clampMaxResults(0)).isEqualTo(1)
        assertThat(MemorySearch.clampMaxResults(999)).isEqualTo(50)
    }

    @Test
    fun isTextFileName_filters_binaries() {
        assertThat(MemorySearch.isTextFileName("note.md")).isTrue()
        assertThat(MemorySearch.isTextFileName("data.json")).isTrue()
        assertThat(MemorySearch.isTextFileName("photo.png")).isFalse()
        assertThat(MemorySearch.isTextFileName("LICENSE")).isTrue()
        assertThat(MemorySearch.isTextFileName("lib.so")).isFalse()
        assertThat(MemorySearch.isTextFileName("app.apk")).isFalse()
    }

    @Test
    fun format_and_merge() {
        val a = MemorySearch.SearchResult(
            listOf(MemorySearch.Hit("a.md", 1, "hello world", 20)),
            filesScanned = 1,
            truncated = false,
        )
        val b = MemorySearch.SearchResult(
            listOf(MemorySearch.Hit("b.md", 3, "world peace", 15)),
            filesScanned = 2,
            truncated = false,
        )
        val merged = MemorySearch.merge(listOf(a, b), maxResults = 10)
        assertThat(merged.hits).hasSize(2)
        assertThat(merged.hits[0].path).isEqualTo("a.md")
        assertThat(merged.filesScanned).isEqualTo(3)
        val text = MemorySearch.format(merged)
        assertThat(text).contains("a.md:1:")
        assertThat(text).contains("2 hits")
    }

    @Test
    fun format_no_matches() {
        val text = MemorySearch.format(MemorySearch.SearchResult(emptyList(), 4, false))
        assertThat(text).contains("No matches")
        assertThat(text).contains("scanned 4")
    }

    @Test
    fun score_prefers_line_start() {
        val mid = MemorySearch.scoreHit("xxx query here", "query", 4)
        val start = MemorySearch.scoreHit("query at start", "query", 0)
        assertThat(start).isGreaterThan(mid)
    }

    @Test
    fun reserved_includes_search_memory() {
        assertThat(
            com.minima.chat.core.agent.httptools.DeclarativeHttpToolsParser.reservedNames,
        ).contains("search_memory")
    }
}
