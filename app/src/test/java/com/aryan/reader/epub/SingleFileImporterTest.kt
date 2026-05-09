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
        assertTrue(File(book.extractionBasePath, "book_metadata.json").isFile)
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
        assertEquals(first.chapters.single().plainTextContent, second.chapters.single().plainTextContent)
        assertTrue(second.chapters.single().plainTextContent.contains("Cached content"))
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
