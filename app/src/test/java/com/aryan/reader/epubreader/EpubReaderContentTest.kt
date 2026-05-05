package com.aryan.reader.epubreader

import android.content.Context
import com.aryan.reader.R
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
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
        assertEquals(0, result.startChunkIndex)
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
