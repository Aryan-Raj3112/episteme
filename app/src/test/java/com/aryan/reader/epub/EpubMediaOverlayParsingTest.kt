package com.aryan.reader.epub

import android.content.Context
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The narrated-book path, end to end and through the real parser.
 *
 * The shared SMIL tests cover the grammar; this covers the *join*, which is where the feature can
 * silently do nothing: the OPF's `media-overlay` attribute has to survive Android's manifest parse,
 * land in `EpubBook.mediaOverlays`, and be enough for the reader to find and parse the right SMIL
 * body out of the archive. A book that parses perfectly in shared tests and produces an empty index
 * here is a book that shows no narration button, with nothing logged anywhere.
 */
class EpubMediaOverlayParsingTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a narrated epub yields the package index and the parsed smil body`() = runTest {
        val epub = writeFixture(narratedEpubBytes())
        val book = parse(epub)

        val overlays = book.mediaOverlays
        assertTrue(overlays.hasOverlays)
        assertEquals("Chris Hughes", overlays.narrator)
        assertEquals("mo-active", overlays.activeClass)
        assertEquals("mo-playing", overlays.playbackActiveClass)
        // The unrefined `media:duration` is the book's; the one refining `#smil1` is chapter one's.
        // Conflating them reports one chapter's runtime as the whole book's.
        assertEquals(12_000L, overlays.totalDurationMs)
        assertEquals(mapOf(0 to 5_000L), overlays.declaredDurationMsBySpineItem)
        assertEquals(mapOf(0 to "OEBPS/smil/chapter1.smil"), overlays.smilPathBySpineItem)
        assertEquals(mapOf(0 to "smil1"), overlays.smilIdBySpineItem)
        // Every spine item is mapped, narrated or not: this is the authoritative chapter <-> spine
        // link the reader needs, because the overlay's textref is only a hint.
        assertEquals(
            mapOf(
                0 to "OEBPS/chapters/chapter1.xhtml",
                1 to "OEBPS/chapters/chapter2.xhtml"
            ),
            overlays.contentPathBySpineItem
        )

        val cache = SharedMediaOverlayDocumentCache(
            index = overlays,
            readSmil = { path -> epub.readEntry(path) }
        )
        val chapter = cache.document(0)!!
        assertEquals(2, chapter.clips.size)
        assertEquals(listOf("p1", "p2"), chapter.clips.map { it.elementId })
        assertEquals("OEBPS/chapters/chapter1.xhtml", chapter.textHref)
        // `../Audio/...` is resolved against the SMIL's own directory, not the OPF's.
        assertEquals("OEBPS/Audio/chapter1.mp3", chapter.clips[0].audioPath)
        assertEquals(listOf(0L, 2_500L), chapter.clips.map { it.clipBeginMs })
        assertEquals(listOf(2_500L, 5_000L), chapter.clips.map { it.clipEndMs })
        assertEquals(5_000L, chapter.declaredDurationMs)
        // A spine item with no overlay is absent rather than an empty document, and the cache says so
        // without reading anything.
        assertNull(cache.document(1))

        // One chapter's body was read, not both, and a second ask is a cache hit.
        assertEquals(1, cache.parseCount)
        assertEquals(chapter, cache.document(0))
        assertEquals(1, cache.parseCount)
    }

    @Test
    fun `a book without overlays still maps every spine item and reports no narration`() = runTest {
        val book = parse(writeFixture(plainEpubBytes()))

        assertFalse(book.mediaOverlays.hasOverlays)
        assertNull(book.mediaOverlays.narrator)
        assertNull(book.mediaOverlays.totalDurationMs)
        assertEquals(emptyMap<Int, String>(), book.mediaOverlays.smilPathBySpineItem)
        // Populated anyway: the chapter -> spine mapping is needed by any narrated book and costs
        // nothing to build here, so a caller must not treat it as a has-narration signal.
        assertEquals(mapOf(0 to "OEBPS/chapters/chapter1.xhtml"), book.mediaOverlays.contentPathBySpineItem)
    }

    @Test
    fun `overlay discovery ignores the manifest attribute when the smil item is absent`() = runTest {
        // A book that declares `media-overlay` pointing at nothing is broken, and the reader's job is
        // to keep working: no overlays, no crash, no lost chapter.
        val book = parse(writeFixture(narratedEpubBytes(includeSmilItem = false)))

        assertFalse(book.mediaOverlays.hasOverlays)
        assertEquals(emptyMap<Int, String>(), book.mediaOverlays.smilPathBySpineItem)
        assertEquals(2, book.chapters.size)
    }

    private suspend fun parse(epub: File): EpubBook {
        val cacheDir = temp.newFolder("cache")
        val extractionDir = temp.newFolder("extract")
        val parser = EpubParser(contextWithCache(cacheDir))
        return parser.createEpubBook(
            inputStream = epub.inputStream(),
            bookId = "media-overlay-book",
            shouldUseToc = true,
            originalBookNameHint = "narrated.epub",
            parseContent = true,
            extractionDirOverride = extractionDir
        )
    }

    private fun contextWithCache(cacheDir: File): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return context
    }

    private fun writeFixture(bytes: ByteArray): File =
        temp.newFile("narrated.epub").apply { writeBytes(bytes) }

    private fun File.readEntry(path: String): String? = ZipFile(this).use { zip ->
        zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).readBytes().decodeToString() }
    }

    /** A two-chapter book; chapter one is narrated, chapter two is not. */
    private fun narratedEpubBytes(includeSmilItem: Boolean = true): ByteArray = zipBytes(
        "META-INF/container.xml" to """
            <container version="1.0">
                <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
            </container>
        """.trimIndent(),
        "OEBPS/content.opf" to """
            <package xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                <metadata>
                    <dc:title>Narrated Book</dc:title>
                    <dc:creator>A Writer</dc:creator>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-10-06T00:00:00Z</meta>
                    <meta property="media:duration">0:00:12.000</meta>
                    <meta refines="#smil1" property="media:duration">0:00:05.000</meta>
                    <meta property="media:narrator">Chris Hughes</meta>
                    <meta property="media:active-class">mo-active</meta>
                    <meta property="media:playback-active-class">mo-playing</meta>
                </metadata>
                <manifest>
                    <item id="ch1" href="chapters/chapter1.xhtml" media-type="application/xhtml+xml" media-overlay="smil1"/>
                    <item id="ch2" href="chapters/chapter2.xhtml" media-type="application/xhtml+xml"/>
                    ${if (includeSmilItem) """<item id="smil1" href="smil/chapter1.smil" media-type="application/smil+xml"/>""" else ""}
                </manifest>
                <spine>
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                </spine>
            </package>
        """.trimIndent(),
        "OEBPS/chapters/chapter1.xhtml" to """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>Chapter One</title></head>
                <body><p id="p1">One</p><p id="p2">Two</p></body>
            </html>
        """.trimIndent(),
        "OEBPS/chapters/chapter2.xhtml" to """
            <html xmlns="http://www.w3.org/1999/xhtml"><body><p id="p3">Three</p></body></html>
        """.trimIndent(),
        "OEBPS/smil/chapter1.smil" to """
            <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
                <body>
                    <seq epub:textref="../chapters/chapter1.xhtml" epub:type="bodymatter chapter">
                        <par id="par1">
                            <text src="../chapters/chapter1.xhtml#p1"/>
                            <audio clipBegin="0:00:00.000" clipEnd="0:00:02.500" src="../Audio/chapter1.mp3"/>
                        </par>
                        <par id="par2">
                            <text src="../chapters/chapter1.xhtml#p2"/>
                            <audio clipBegin="0:00:02.500" clipEnd="0:00:05.000" src="../Audio/chapter1.mp3"/>
                        </par>
                    </seq>
                </body>
            </smil>
        """.trimIndent(),
        "OEBPS/Audio/chapter1.mp3" to "not-real-audio"
    )

    private fun plainEpubBytes(): ByteArray = zipBytes(
        "META-INF/container.xml" to """
            <container version="1.0">
                <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
            </container>
        """.trimIndent(),
        "OEBPS/content.opf" to """
            <package xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                <metadata>
                    <dc:title>Plain Book</dc:title>
                    <dc:creator>A Writer</dc:creator>
                    <dc:language>en</dc:language>
                </metadata>
                <manifest>
                    <item id="ch1" href="chapters/chapter1.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="ch1"/></spine>
            </package>
        """.trimIndent(),
        "OEBPS/chapters/chapter1.xhtml" to "<html><body><p id=\"p1\">One</p></body></html>"
    )

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
