package com.aryan.reader.epub

import android.content.Context
import com.aryan.reader.FileType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class SingleFileImporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `metadata-only import returns lightweight book for supported text formats`() = runTest {
        val importer = SingleFileImporter(contextWithCache(temp.newFolder("metadata-cache")))

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream("ignored".toByteArray()),
            type = FileType.TXT,
            originalBookNameHint = "Notes.txt",
            bookId = "notes",
            parseContent = false
        )

        assertEquals("Notes.txt", book.fileName)
        assertEquals("Notes", book.title)
        assertEquals("Unknown", book.author)
        assertEquals("en", book.language)
        assertEquals(emptyList<EpubChapter>(), book.chapters)
        assertEquals("", book.extractionBasePath)
    }

    @Test
    fun `plain text import escapes html groups paragraphs and writes cached metadata`() = runTest {
        val cache = temp.newFolder("txt-cache")
        val importer = SingleFileImporter(contextWithCache(cache))

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream("First <line>\ncontinues\n\nSecond & final".toByteArray()),
            type = FileType.TXT,
            originalBookNameHint = "Plain.txt",
            bookId = "plain-book"
        )

        assertEquals("Plain", book.title)
        assertEquals(1, book.chapters.size)
        assertEquals("Part 1", book.chapters.single().title)
        assertTrue(book.chapters.single().plainTextContent.contains("First <line> continues"))
        assertTrue(File(book.extractionBasePath, "part_1.html").readText().contains("First &lt;line&gt;"))
        val metadata = File(book.extractionBasePath, "book_metadata.json")
        assertTrue(metadata.isFile)
        val metadataText = metadata.readText()
        assertFalse(metadataText.contains("First <line> continues"))
        assertTrue(metadataText.contains("plainTextLength"))
    }

    @Test
    fun `plain text import reuses cached metadata before reading the stream`() = runTest {
        val cache = temp.newFolder("txt-cache-reuse")
        val importer = SingleFileImporter(contextWithCache(cache))

        val first = importer.importSingleFile(
            inputStream = ByteArrayInputStream("Cached content".toByteArray()),
            type = FileType.TXT,
            originalBookNameHint = "Cached.txt",
            bookId = "cached-book"
        )

        val second = importer.importSingleFile(
            inputStream = ByteArrayInputStream("Different content that should not be parsed".toByteArray()),
            type = FileType.TXT,
            originalBookNameHint = "Cached.txt",
            bookId = "cached-book"
        )

        assertEquals(first.title, second.title)
        assertEquals(first.chapters.single().plainTextLength, second.chapters.single().plainTextLength)
        assertEquals("", second.chapters.single().plainTextContent)
        assertTrue(File(second.extractionBasePath, second.chapters.single().htmlFilePath).readText().contains("Cached content"))
    }

    @Test
    fun `plain text import ignores oversized legacy cached metadata before reading it`() = runTest {
        val cache = temp.newFolder("txt-cache-oversized")
        val context = contextWithCache(cache)
        val bookId = "oversized-cache-book"
        val extractionDir = ImportedFileCache.ensureActiveBookDir(context, bookId)
        File(extractionDir, "book_metadata.json").writeText("x".repeat((2L * 1024L * 1024L + 1L).toInt()))
        val importer = SingleFileImporter(context)

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream("Fresh content after oversized cache".toByteArray()),
            type = FileType.TXT,
            originalBookNameHint = "Fresh.txt",
            bookId = bookId
        )

        assertEquals("Fresh", book.title)
        assertTrue(book.chapters.single().plainTextContent.contains("Fresh content"))
    }

    @Test
    fun `html import extracts title author style skips scripts and splits page breaks`() = runTest {
        val importer = SingleFileImporter(contextWithCache(temp.newFolder("html-cache")))
        val html = """
            <html>
              <head>
                <title>HTML Title</title>
                <meta name="author" content="HTML Author">
                <style>p { color: red; }</style>
              </head>
              <body>
                <p>First page</p>
                <script>bad()</script>
                <page-break></page-break>
                <p>Second page</p>
              </body>
            </html>
        """.trimIndent()

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream(html.toByteArray()),
            type = FileType.HTML,
            originalBookNameHint = "fallback.html",
            bookId = "html-book"
        )

        assertEquals("HTML Title", book.title)
        assertEquals("HTML Author", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("HTML Title", book.chapters[0].title)
        assertEquals("Page 2", book.chapters[1].title)
        assertTrue(book.chapters[0].plainTextContent.contains("First page"))
        assertTrue(book.chapters[1].plainTextContent.contains("Second page"))
        assertFalse(File(book.extractionBasePath, "page_1.html").readText().contains("bad()"))
        assertTrue(File(book.extractionBasePath, "page_1.html").readText().contains("p { color: red; }"))
    }

    @Test
    fun `html import chunks very long lines into bounded chapters`() = runTest {
        val importer = SingleFileImporter(contextWithCache(temp.newFolder("html-long-line-cache")))
        val longParagraph = "word ".repeat(260_000)
        val html = "<html><body><p>$longParagraph</p></body></html>"

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream(html.toByteArray()),
            type = FileType.HTML,
            originalBookNameHint = "long.html",
            bookId = "long-html-book"
        )

        assertTrue(book.chapters.size > 1)
        book.chapters.forEach { chapter ->
            val chapterFile = File(book.extractionBasePath, chapter.htmlFilePath)
            assertTrue(chapterFile.isFile)
            assertTrue(chapterFile.length() < 1_200_000L)
        }
    }

    @Test
    fun `csv txt wrapper imports as html table`() = runTest {
        val importer = SingleFileImporter(contextWithCache(temp.newFolder("csv-cache")))

        val book = importer.importSingleFile(
            inputStream = ByteArrayInputStream("Name,Value\nA & B,<tag>".toByteArray()),
            type = FileType.HTML,
            originalBookNameHint = "data.csv.txt",
            bookId = "csv-book"
        )

        val html = File(book.extractionBasePath, "page_1.html").readText()
        assertEquals("data.csv", book.title)
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("A &amp; B"))
        assertTrue(html.contains("&lt;tag&gt;"))
    }

    private fun contextWithCache(cacheDir: File): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return context
    }
}
