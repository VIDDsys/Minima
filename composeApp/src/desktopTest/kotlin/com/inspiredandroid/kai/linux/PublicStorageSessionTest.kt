package com.inspiredandroid.kai.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicStorageSessionTest {

    private lateinit var base: File
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        base = Files.createTempDirectory("public-storage").toFile()
        root = File(base, "emulated0").apply { mkdirs() }
        File(root, "Download").mkdirs()
        File(root, "Download/note.txt").writeText("hello")
    }

    @AfterTest
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun session() = PublicStorageSession(root)

    private fun guard() = PublicStorageGuard(root)

    @Test
    fun `aliases resolve inside the configured root`() {
        assertEquals(File(root, "Download/note.txt"), guard().resolve("/sdcard/Download/note.txt"))
        assertEquals(File(root, "Download/note.txt"), guard().resolve("/mnt/sdcard/Download/note.txt"))
        assertEquals(File(root, "Download/note.txt"), guard().resolve("/storage/self/primary/Download/note.txt"))
        assertEquals(File(root, "Download/note.txt"), guard().resolve("/storage/emulated/0/Download/note.txt"))
        assertEquals(File(root, "Download/note.txt"), guard().resolve("${root.absolutePath}/Download/note.txt"))
        assertEquals(root, guard().resolve("/sdcard"))
    }

    @Test
    fun `escapes and relative paths are rejected`() {
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "secret").writeText("nope")
        assertNull(guard().resolve("/sdcard/../outside/secret"))
        assertNull(guard().resolve("/data/data/secrets"))
        assertNull(guard().resolve("Download/note.txt"))
        assertNull(guard().resolve("/sdcard/./note.txt"))
        assertNull(guard().resolve("/storage/emulated"))
        assertNull(guard().resolve("/storage/emulated/0evil"))
    }

    @Test
    fun `a symlink that leaves the root does not resolve`() {
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "secret").writeText("nope")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        assertNull(guard().resolve("/sdcard/escape/secret"))
        assertNull(guard().resolve("${root.absolutePath}/escape/secret"))
    }

    @Test
    fun `list read write mkdir and delete stay inside the root`() {
        val listed = session().execute("list", "/sdcard/Download", null, false)
        assertEquals(true, listed["success"])
        val entries = listed["entries"] as List<*>
        assertEquals(1, entries.size)

        val read = session().execute("read", "/storage/emulated/0/Download/note.txt", null, false)
        assertEquals("hello", read["content"])

        val written = session().execute("write", "/sdcard/Minima/out.txt", "world", false)
        assertEquals(true, written["success"])
        assertEquals("world", File(root, "Minima/out.txt").readText())

        val made = session().execute("mkdir", "/sdcard/Documents/notes", null, false)
        assertEquals(true, made["success"])
        assertTrue(File(root, "Documents/notes").isDirectory)

        val blocked = session().execute("delete", "/sdcard/Minima", null, false)
        assertEquals(false, blocked["success"])
        assertTrue(File(root, "Minima").isDirectory)

        val removed = session().execute("delete", "/sdcard/Minima", null, true)
        assertEquals(true, removed["success"])
        assertFalse(File(root, "Minima").exists())
    }

    @Test
    fun `the storage root itself cannot be deleted or overwritten`() {
        val deleted = session().execute("delete", "/storage/emulated/0", null, true)
        assertEquals(false, deleted["success"])
        assertTrue(root.isDirectory)
        val written = session().execute("write", "/sdcard", "nope", false)
        assertEquals(false, written["success"])
        assertTrue(File(root, "Download/note.txt").exists())
    }

    @Test
    fun `binary files are refused and missing paths do not throw`() {
        File(root, "Download/blob.bin").writeBytes(byteArrayOf(0, 1, 2))
        val binary = session().execute("read", "/sdcard/Download/blob.bin", null, false)
        assertEquals(false, binary["success"])
        val missing = session().execute("read", "/sdcard/Download/missing.txt", null, false)
        assertEquals(false, missing["success"])
        val outside = session().execute("write", "/root/secret.txt", "x", false)
        assertEquals(false, outside["success"])
    }
}
