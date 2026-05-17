package com.aryan.reader.epubreader

import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubReaderImagesTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `readerImageReferencesForDrawer extracts images in reading order with chunk targets`() {
        val root = temp.newFolder("book")
        val imageFile = File(root, "OPS/Images/cover.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val firstImage = """<p>Intro <img id="cover" src="../Images/cover.png" alt="Cover art" width="640" height="480"/></p>"""
        val filler = (1..20).joinToString(separator = "") { "<p>Paragraph $it</p>" }
        writeFile(
            root,
            "OPS/Text/chapter.xhtml",
            "<html><body>$firstImage$filler<img src=\"../Images/cover.png\" alt=\"Second cover\"/></body></html>"
        )
        val book = epubBook(
            root,
            listOf(chapter(path = "OPS/Text/chapter.xhtml", title = "Chapter One"))
        )

        val images = book.readerImageReferencesForDrawer()

        assertEquals(2, images.size)
        assertEquals("Cover art", images[0].displayTitle)
        assertEquals(imageFile.canonicalPath, images[0].sourcePath)
        assertEquals(0, images[0].ordinalInChapter)
        assertEquals(0, images[0].chunkIndex)
        assertEquals(640, images[0].intrinsicWidth)
        assertEquals(480, images[0].intrinsicHeight)
        assertEquals(1, images[1].ordinalInChapter)
        assertEquals(1, images[1].chunkIndex)
        assertEquals("Chapter One", images[1].chapterTitle)
    }

    @Test
    fun `readerImageReference supports data uri download bytes and safe names`() {
        val root = temp.newFolder("data-uri")
        val book = epubBook(
            root,
            listOf(
                chapter(
                    path = "chapter.xhtml",
                    title = "Inline",
                    htmlContent = """<html><body><img src="data:image/png;base64,SGk=" alt="bad/name"/></body></html>"""
                )
            )
        )

        val image = book.readerImageReferencesForDrawer().single()

        assertEquals("bad_name.png", image.suggestedDownloadFileName())
        assertEquals("image/png", image.mimeType())
        assertArrayEquals("Hi".toByteArray(), image.readDownloadBytes())
    }

    private fun writeFile(root: File, relativePath: String, content: String) {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun epubBook(root: File, chapters: List<EpubChapter>): EpubBook =
        EpubBook(
            fileName = "book.epub",
            title = "Book",
            author = "Author",
            language = "en",
            coverImage = null,
            chapters = chapters,
            extractionBasePath = root.absolutePath
        )

    private fun chapter(
        path: String,
        title: String,
        htmlContent: String = ""
    ): EpubChapter =
        EpubChapter(
            chapterId = path,
            absPath = path,
            title = title,
            htmlFilePath = path,
            plainTextContent = "",
            htmlContent = htmlContent
        )
}
