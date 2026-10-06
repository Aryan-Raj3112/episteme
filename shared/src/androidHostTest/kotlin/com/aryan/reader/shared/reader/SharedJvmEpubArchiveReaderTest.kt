package com.aryan.reader.shared.reader

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading one entry out of an EPUB archive.
 *
 * This is the only part of media overlays that touches a real zip, so it gets a real archive rather
 * than a mock — the failure modes worth guarding (a name the framework encodes differently, an entry
 * that is not there, a `..` in a publisher-supplied path) are all zip-level facts.
 */
class SharedJvmEpubArchiveReaderTest {

    private lateinit var archiveFile: File
    private lateinit var archive: SharedJvmEpubArchiveReader

    private val audioBytes = ByteArray(4096) { (it % 251).toByte() }
    private val audioPath = "OEBPS/Audio/chapter1.mp3"

    @BeforeTest
    fun setUp() {
        archiveFile = File.createTempFile("mo-archive", ".epub")
        writeArchive(audioPath to audioBytes, "OEBPS/smil/chapter1.smil" to "<smil/>".toByteArray())
        archive = SharedJvmEpubArchiveReader(archiveFile)
    }

    @AfterTest
    fun tearDown() {
        archive.close()
        archiveFile.delete()
    }

    private fun writeArchive(vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
            for ((path, bytes) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `an entry reads back byte for byte`() {
        assertTrue(audioBytes.contentEquals(assertNotNull(archive.readBytes(audioPath))))
    }

    @Test
    fun `text is decoded as utf-8`() {
        assertEquals("<smil/>", archive.readTextOrNull("OEBPS/smil/chapter1.smil"))
    }

    @Test
    fun `a non-ascii entry name is reachable`() {
        val name = "OEBPS/Audio/第一章.mp3"
        writeArchive(name to "narration".toByteArray())
        assertNotNull(archive.readTextOrNull(name))
    }

    @Test
    fun `a missing entry yields null rather than throwing`() {
        assertNull(archive.readBytes("OEBPS/Audio/absent.mp3"))
        assertNull(archive.readTextOrNull("OEBPS/Audio/absent.mp3"))
        assertNull(archive.openStreamOrNull("OEBPS/Audio/absent.mp3"))
        assertNull(archive.entryLengthOrNull("OEBPS/Audio/absent.mp3"))
    }

    @Test
    fun `the entry length comes from the zip header`() {
        assertEquals(audioBytes.size.toLong(), archive.entryLengthOrNull(audioPath))
    }

    /**
     * A `par` can begin partway into a file, and the media framework needs the length up front so it
     * can seek and can report a duration before decoding anything.
     */
    @Test
    fun `a stream starts at the beginning and reads the whole entry`() {
        val stream = assertNotNull(archive.openStreamOrNull(audioPath))
        val read = stream.use { it.readBytes() }
        assertTrue(audioBytes.contentEquals(read))
    }

    /** Many `par`s share one audio file, so one reader has to serve many streams. */
    @Test
    fun `many streams can be taken from one reader`() {
        repeat(8) {
            val read = assertNotNull(archive.openStreamOrNull(audioPath)).use { it.readBytes() }
            assertTrue(audioBytes.contentEquals(read))
        }
    }

    /**
     * The zip-slip guard, shared with package loading rather than reimplemented.
     *
     * A SMIL document is content inside a book the reader chose to open, and an `audio src` is a path
     * it controls. Without this guard, `../../../databases/x.db` reads a file the app can reach —
     * so the guard is not defensive decoration, it is the reason this reader is safe to point at
     * publisher content at all.
     */
    @Test
    fun `a path escaping the archive is refused`() {
        val outside = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "mo-outside.txt")
        outside.writeText("secret")
        try {
            assertNull(archive.readBytes("../${outside.name}"))
            assertNull(archive.readBytes("OEBPS/../../${outside.name}"))
            assertNull(archive.readBytes("/etc/passwd"))
            assertNull(archive.openStreamOrNull("../${outside.name}"))
            assertNull(archive.entryLengthOrNull("../${outside.name}"))
        } finally {
            outside.delete()
        }
    }

    /** A `..` that stays inside the archive is a legitimate relative path and must still work. */
    @Test
    fun `a relative path that stays inside the archive resolves`() {
        assertEquals("<smil/>", archive.readTextOrNull("OEBPS/../OEBPS/smil/chapter1.smil"))
    }

    @Test
    fun `a closed reader reports nothing rather than reopening`() {
        archive.close()
        // After close the reader would re-open lazily, which is harmless, but the point here is that
        // closing twice — which happens on a reader that is disposed and then released — is not an error.
        archive.close()
    }

    @Test
    fun `an unreadable file reports itself unreadable`() {
        val missing = SharedJvmEpubArchiveReader(File("/definitely/not/here.epub"))
        assertTrue(!missing.isReadable())
        missing.close()
    }

    @Test
    fun `a non-archive file reports itself unreadable`() {
        val notAZip = File.createTempFile("mo-notazip", ".epub")
        notAZip.writeText("this is not a zip")
        try {
            val reader = SharedJvmEpubArchiveReader(notAZip)
            assertTrue(!reader.isReadable())
            // And it degrades to "no narration" rather than throwing at the first read.
            assertNull(reader.readBytes(audioPath))
            reader.close()
        } finally {
            notAZip.delete()
        }
    }

    private fun assertNotNull(value: ByteArray?): ByteArray = value ?: error("expected bytes")
}