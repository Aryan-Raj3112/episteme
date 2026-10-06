package com.aryan.reader.mediaoverlay

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.datasource.DataSource
import com.aryan.reader.shared.reader.SharedJvmEpubArchiveReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Playing an EPUB's audio out of the archive.
 *
 * Instrumented rather than a JVM unit test because `DataSpec` carries an `android.net.Uri`, which is
 * stubbed to throw in JVM tests. The cases are worth the cost of a device because this is the only
 * part of the feature the media framework drives directly, and its failure modes — a clip whose
 * `clipBegin` is not the start of the file, many clips over one file — are exactly what a unit test
 * over the archive would not have covered.
 */
@RunWith(AndroidJUnit4::class)
class SharedMediaOverlayDataSourceInstrumentedTest {

    private lateinit var archiveFile: File
    private lateinit var archive: SharedJvmEpubArchiveReader

    private val audioBytes = ByteArray(4096) { (it % 251).toByte() }
    private val audioPath = "OEBPS/Audio/chapter1.mp3"

    @Before
    fun setUp() {
        archiveFile = File.createTempFile("mo-ds", ".epub")
        ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
            zip.putNextEntry(ZipEntry(audioPath))
            zip.write(audioBytes)
            zip.closeEntry()
        }
        archive = SharedJvmEpubArchiveReader(archiveFile)
    }

    @After
    fun tearDown() {
        archive.close()
        archiveFile.delete()
    }

    private fun dataSource() = SharedEpubZipEntryDataSource(archive, audioPath)

    private fun readFully(source: DataSource, spec: DataSpec): ByteArray {
        source.open(spec)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(512)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        source.close()
        return out.toByteArray()
    }

    private fun spec(position: Long? = null) = DataSpec.Builder()
        .setUri(MediaOverlayUri.uriFor(audioPath))
        .apply { position?.let { setPosition(it) } }
        .build()

    @Test
    fun theWholeEntryReadsBackByteForByte() {
        val read = readFully(dataSource(), spec())
        assertEquals(audioBytes.size, read.size)
        assertTrue(audioBytes.contentEquals(read))
    }

    /**
     * The case that makes this design necessary.
     *
     * A clip's `clipBegin` is an offset into the *file*, not the start of it — every clip in a chapter
     * but the first starts somewhere in the middle. A data source that could not honour `position`
     * would play the beginning of the audio under a highlight that says otherwise.
     */
    @Test
    fun aSeekPositionIsHonoured() {
        val from = 1000L
        val read = readFully(dataSource(), spec(from))
        assertEquals(audioBytes.size - from.toInt(), read.size)
        assertTrue(audioBytes.copyOfRange(from.toInt(), audioBytes.size).contentEquals(read))
    }

    /** Many `par`s share one audio file, so one archive has to serve many opens. */
    @Test
    fun theArchiveServesManyOpens() {
        repeat(8) {
            assertTrue(audioBytes.contentEquals(readFully(dataSource(), spec())))
        }
    }

    /**
     * A missing entry fails loudly.
     *
     * Silently returning zero bytes would present as a player that never makes a sound, with nothing
     * in any log to say why.
     */
    @Test
    fun aMissingEntryFailsLoudly() {
        val source = SharedEpubZipEntryDataSource(archive, "OEBPS/Audio/absent.mp3")
        var failed = false
        runCatching { source.open(DataSpec(MediaOverlayUri.uriFor("OEBPS/Audio/absent.mp3"))) }
            .onFailure { failed = true }
        source.close()
        assertTrue(failed)
    }

    /**
     * The zip-slip guard, shared with package loading rather than reimplemented.
     *
     * A SMIL document is publisher-controlled content inside a book the reader opened, so an
     * `audio src` of `../../../../databases/app.db` must not read outside the archive. Without it this
     * is a file read of anything the app can reach.
     */
    @Test
    fun aPathEscapingTheArchiveIsRefused() {
        val source = SharedEpubZipEntryDataSource(archive, "../../../etc/passwd")
        var failed = false
        runCatching { source.open(DataSpec(MediaOverlayUri.uriFor("etc/passwd"))) }
            .onFailure { failed = true }
        source.close()
        assertTrue(failed)
    }

    /** The router only intercepts our own scheme; anything else reaches the delegate. */
    @Test
    fun aForeignSchemeIsNotIntercepted() {
        assertEquals(
            null,
            MediaOverlayUri.entryPathOf(android.net.Uri.parse("file:///tmp/a.mp3"))
        )
        assertEquals(audioPath, MediaOverlayUri.entryPathOf(MediaOverlayUri.uriFor(audioPath)))
    }
}