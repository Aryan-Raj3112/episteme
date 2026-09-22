package com.aryan.reader.epubreader

import android.content.Context
import com.aryan.reader.R
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.epub.hasReadableExtractedContent
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.LocatorConverter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubReaderContentTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `loadChapterContent removes scripts keeps head and chunks body nodes by twenty`() = runTest {
        val root = temp.newFolder("content")
        val body = (1..21).joinToString("") { index ->
            if (index == 3) "<script>bad()</script><p>Paragraph $index</p>" else "<p>Paragraph $index</p>"
        }
        writeChapter(root, "chapter.xhtml", "<html><head><style>.x{}</style></head><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = false,
            cfiToLoad = null,
            locatorConverter = mockk()
        )

        assertTrue(result.isSuccess)
        assertEquals("<style>.x{}</style>", result.head.trim())
        assertEquals(2, result.chunks.size)
        assertFalse(result.chunks.joinToString().contains("<script>"))
        assertTrue(result.chunks[0].contains("Paragraph 20"))
        assertTrue(result.chunks[1].contains("Paragraph 21"))
        assertEquals(listOf(0, 20), result.chunkElementStartIndices)
        assertEquals(listOf(20, 1), result.chunkElementCounts)
        assertEquals(0, result.startChunkIndex)
    }

    @Test
    fun `loadChapterContent preserves txt preformatted whitespace when chunking body`() = runTest {
        val root = temp.newFolder("txt-preformatted")
        val body = """
            <p class="reader-txt-preformatted" style="white-space: pre-wrap !important; text-indent: 0 !important;">[ID]          72694621
[Title]       DAMN.
[Artists]     Kendrick Lamar

===========CD 1=============
[1]     BLOOD.</p>
        """.trimIndent()
        writeChapter(root, "part_1.html", "<html><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("part_1.html")))

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = false,
            cfiToLoad = null,
            locatorConverter = mockk()
        )

        val chunk = result.chunks.single()
        assertTrue(chunk.contains("class=\"reader-txt-preformatted\""))
        assertTrue(chunk.contains("[ID]          72694621\n[Title]       DAMN."))
        assertTrue(chunk.contains("[Artists]     Kendrick Lamar\n\n===========CD 1============="))
        assertFalse(chunk.contains("[ID] 72694621 [Title] DAMN."))
    }

    @Test
    fun `loadChapterContent records element starts independently from whitespace text nodes`() = runTest {
        val root = temp.newFolder("content-whitespace")
        val body = (1..25).joinToString(separator = "\n", prefix = "\n", postfix = "\n") { index ->
            "<p>Paragraph $index</p>"
        }
        writeChapter(root, "chapter.xhtml", "<html><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = false,
            cfiToLoad = null,
            locatorConverter = mockk()
        )

        assertEquals(listOf(0, 10, 20), result.chunkElementStartIndices)
        assertEquals(listOf(10, 10, 5), result.chunkElementCounts)
        assertEquals(
            "data-chunk-index='1' data-element-start-index='10' data-element-count='10'",
            readerChunkContainerAttributes(1, result.chunkElementStartIndices, result.chunkElementCounts)
        )
    }

    @Test
    fun `loadChapterContent clamps explicit chunk override into available chunk range`() = runTest {
        val root = temp.newFolder("override")
        val body = (1..5).joinToString("") { "<p>Only $it</p>" }
        writeChapter(root, "chapter.xhtml", "<html><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))

        val high = loadChapterContent(contextWithStrings(), book, 0, 99, false, null, mockk())
        val low = loadChapterContent(contextWithStrings(), book, 0, -5, false, null, mockk())

        assertEquals(0, high.startChunkIndex)
        assertEquals(0, low.startChunkIndex)
    }

    @Test
    fun `loadChapterContent ignores fragment and query in chapter file path`() = runTest {
        val root = temp.newFolder("path-fragment")
        writeChapter(root, "chapter.xhtml", "<html><body><p>Found chapter</p></body></html>")
        val book = epubBook(root, listOf(chapter("chapter.xhtml#anchor?ignored")))

        val result = loadChapterContent(contextWithStrings(), book, 0, null, false, null, mockk())

        assertTrue(book.hasReadableExtractedContent())
        assertTrue(result.isSuccess)
        assertEquals(listOf("<p>Found chapter</p>"), result.chunks)
    }

    @Test
    fun `loadChapterContent calculates initial chunk from cfi locator block index`() = runTest {
        val root = temp.newFolder("cfi")
        val body = (1..60).joinToString("") { "<p>Paragraph $it</p>" }
        writeChapter(root, "chapter.xhtml", "<html><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))
        val locatorConverter = mockk<LocatorConverter>()
        coEvery { locatorConverter.getLocatorFromCfi(book, 0, "/4/2:10") } returns Locator(0, blockIndex = 45, charOffset = 0)

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = true,
            cfiToLoad = "/4/2:10",
            locatorConverter = locatorConverter
        )

        assertEquals(2, result.startChunkIndex)
    }

    @Test
    fun `loadChapterContent falls back to last chunk when cfi cannot be resolved`() = runTest {
        val root = temp.newFolder("cfi-missing")
        val body = (1..45).joinToString("") { "<p>Paragraph $it</p>" }
        writeChapter(root, "chapter.xhtml", "<html><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))
        val locatorConverter = mockk<LocatorConverter>()
        coEvery { locatorConverter.getLocatorFromCfi(book, 0, "/missing") } returns null

        val result = loadChapterContent(contextWithStrings(), book, 0, null, true, "/missing", locatorConverter)

        assertEquals(2, result.startChunkIndex)
    }

    @Test
    fun `loadChapterContent returns localized empty and missing chapter placeholders`() = runTest {
        val root = temp.newFolder("placeholders")
        writeChapter(root, "empty.xhtml", "<html><body></body></html>")
        val book = epubBook(root, listOf(chapter("empty.xhtml"), chapter("missing.xhtml")))

        val empty = loadChapterContent(contextWithStrings(), book, 0, null, false, null, mockk())
        val missing = loadChapterContent(contextWithStrings(), book, 1, null, false, null, mockk())

        assertEquals(listOf("<body><p>Empty chapter</p></body>"), empty.chunks)
        assertEquals(listOf("<h1>Chapter not found</h1>"), missing.chunks)
        assertTrue(missing.isSuccess)
    }

    @Test
    fun `loadChapterContent reports out of bounds chapter index`() = runTest {
        val root = temp.newFolder("bounds")
        val result = loadChapterContent(contextWithStrings(), epubBook(root, emptyList()), 0, null, false, null, mockk())

        assertFalse(result.isSuccess)
        assertEquals("Chapter index out of bounds", result.errorMessage)
        assertEquals(emptyList<String>(), result.chunks)
    }

    @Test
    fun `initialReaderLoadedChunkCount caps deep webview starts`() {
        assertEquals(8, initialReaderLoadedChunkCount(totalChunks = 100, targetChunkIndex = 80))
        assertEquals(3, initialReaderLoadedChunkCount(totalChunks = 3, targetChunkIndex = 1))
        assertEquals(0, initialReaderLoadedChunkCount(totalChunks = 0, targetChunkIndex = 10))
    }

    @Test
    fun `shouldInlineInitialReaderChunk only inlines bounded prefix`() {
        assertTrue(shouldInlineInitialReaderChunk(index = 7, totalChunks = 100, targetChunkIndex = 80))
        assertFalse(shouldInlineInitialReaderChunk(index = 8, totalChunks = 100, targetChunkIndex = 80))
        assertTrue(shouldInlineInitialReaderChunk(index = 2, totalChunks = 3, targetChunkIndex = 1))
    }

    @Test
    fun `readerChunkPlaceholderHeightPx uses chunk element count estimate`() {
        assertEquals(720, readerChunkPlaceholderHeightPx(index = 1, chunkElementCounts = listOf(20, 10)))
        assertEquals(72, readerChunkPlaceholderHeightPx(index = 0, chunkElementCounts = listOf(0)))
        assertEquals(1440, readerChunkPlaceholderHeightPx(index = 5, chunkElementCounts = emptyList()))
    }

    @Test
    fun `loadChapterContent descends into lone section wrapper for chunking`() = runTest {
        val root = temp.newFolder("wrapped")
        val inner = (1..45).joinToString("") { "<p>Paragraph $it</p>" }
        writeChapter(
            root,
            "chapter.xhtml",
            "<html><head></head><body><section id=\"the-path-to-rome\">$inner</section></body></html>"
        )
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = false,
            cfiToLoad = null,
            locatorConverter = mockk()
        )

        assertTrue(result.isSuccess)
        assertEquals(3, result.chunks.size)
        assertTrue(result.chunks[0].contains("Paragraph 1"))
        assertTrue(result.chunks[2].contains("Paragraph 45"))
        assertEquals(listOf(0, 20, 40), result.chunkElementStartIndices)
    }

    @Test
    fun `loadChapterContent keeps small lone wrapper intact`() = runTest {
        val root = temp.newFolder("smallwrap")
        val inner = (1..3).joinToString("") { "<p>Q $it</p>" }
        writeChapter(
            root,
            "chapter.xhtml",
            "<html><head></head><body><div class=\"wrapper\">$inner</div></body></html>"
        )
        val book = epubBook(root, listOf(chapter("chapter.xhtml")))

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = false,
            cfiToLoad = null,
            locatorConverter = mockk()
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.chunks.size)
        assertTrue(result.chunks[0].contains("<div"))
    }

    @Test
    fun `loadChapterContent preserves asides figures and image src through chunking`() = runTest {
        val root = temp.newFolder("standard-ebooks")
        val images = java.io.File(root, "images").apply { mkdirs() }
        java.io.File(images, "illustration-1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val text = java.io.File(root, "text").apply { mkdirs() }
        val body = """
            <section id="the-path-to-rome">
            <div class="aside">The Difficulty of Beginning</div>
            <p>First paragraph.</p>
            <figure id="illustration-1"><img alt="Sketch" src="../images/illustration-1.jpg"/></figure>
            <p>Second paragraph.</p>
            </section>
        """.trimIndent()
        java.io.File(text, "chapter.xhtml").writeText("<html><head></head><body>$body</body></html>")
        val book = epubBook(root, listOf(chapter("text/chapter.xhtml")))

        val result = loadChapterContent(
            context = contextWithStrings(),
            epubBook = book,
            chapterIndex = 0,
            chunkTargetOverride = null,
            isInitialCfiLoad = false,
            cfiToLoad = null,
            locatorConverter = mockk()
        )

        assertTrue(result.isSuccess)
        val joined = result.chunks.joinToString("\n")
        assertTrue(joined.contains("class=\"aside\""))
        assertTrue(joined.contains("The Difficulty of Beginning"))
        assertTrue(joined.contains("<figure"))
        assertTrue(joined.contains("../images/illustration-1.jpg"))
    }

    @Test
    fun `readerImageFileForHintsDiag resolves chapter relative paths inside extraction root`() {
        val root = temp.newFolder("resolve").canonicalFile
        val images = java.io.File(root, "images").apply { mkdirs() }
        java.io.File(images, "a.png").writeBytes(byteArrayOf(1))
        val chapterDir = java.io.File(root, "text").apply { mkdirs() }

        val found = readerImageFileForHintsDiag("../images/a.png", chapterDir, root)
        assertTrue(found?.isFile == true)
        assertEquals(null, readerImageFileForHintsDiag("https://example.com/a.png", chapterDir, root))
    }

    @Test
    fun `pruneUnresolvableSrcset drops missing file candidates only`() {
        val root = temp.newFolder("srcset")
        val images = java.io.File(root, "images").apply { mkdirs() }
        java.io.File(images, "a.png").writeBytes(byteArrayOf(1, 2, 3))
        val chapterDir = java.io.File(root, "text").apply { mkdirs() }

        val pruned = pruneUnresolvableSrcset(
            "../images/a-2x.png 2x, ../images/a.png 1x",
            chapterDir,
            root
        )

        assertEquals("../images/a.png 1x", pruned)
    }

    @Test
    fun `pruneUnresolvableSrcset keeps fully resolvable sets untouched`() {
        val root = temp.newFolder("srcset-ok")
        val images = java.io.File(root, "images").apply { mkdirs() }
        java.io.File(images, "a-2x.png").writeBytes(byteArrayOf(1))
        java.io.File(images, "a.png").writeBytes(byteArrayOf(1))
        val chapterDir = java.io.File(root, "text").apply { mkdirs() }

        assertEquals(
            null,
            pruneUnresolvableSrcset(
                "../images/a-2x.png 2x, ../images/a.png 1x",
                chapterDir,
                root
            )
        )
    }

    @Test
    fun `readerChunkPlaceholderHeightPx reserves illustration space`() {
        assertEquals(
            720 + 560,
            readerChunkPlaceholderHeightPx(
                index = 1,
                chunkElementCounts = listOf(20, 10),
                chunkImageCounts = listOf(0, 2)
            )
        )
        assertEquals(
            72 + 280,
            readerChunkPlaceholderHeightPx(
                index = 0,
                chunkElementCounts = listOf(1),
                chunkImageCounts = listOf(1)
            )
        )
    }

    private fun writeChapter(root: java.io.File, relativePath: String, html: String) {
        val file = java.io.File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(html)
    }

    private fun epubBook(root: java.io.File, chapters: List<EpubChapter>): EpubBook =
        EpubBook(
            fileName = "book.epub",
            title = "Book",
            author = "Author",
            language = "en",
            coverImage = null,
            chapters = chapters,
            extractionBasePath = root.absolutePath
        )

    private fun chapter(path: String): EpubChapter =
        EpubChapter(
            chapterId = path,
            absPath = path,
            title = path,
            htmlFilePath = path,
            plainTextContent = "",
            htmlContent = ""
        )

    private fun contextWithStrings(): Context {
        val context = mockk<Context>()
        every { context.getString(R.string.chapter_empty) } returns "Empty chapter"
        every { context.getString(R.string.chapter_not_found) } returns "Chapter not found"
        every { context.getString(R.string.error_loading_chapter) } returns "Error loading chapter"
        return context
    }
}
