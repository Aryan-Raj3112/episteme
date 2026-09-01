package com.aryan.reader.shared

import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnotationExportFormatterTest {

    @Test
    fun `epub highlight with note exports markdown and text`() {
        val document = AnnotationExportFormatter.fromEpubHighlights(
            bookTitle = "Example Book",
            sourceType = FileType.EPUB,
            highlights = listOf(
                UserHighlight(
                    id = "h1",
                    cfi = "epubcfi(/6/2)",
                    text = "A highlighted passage.",
                    color = HighlightColor.YELLOW,
                    chapterIndex = 2,
                    note = "Remember this point."
                )
            )
        )

        val markdown = AnnotationExportFormatter.render(document, AnnotationExportFormat.MARKDOWN)
        val text = AnnotationExportFormatter.render(document, AnnotationExportFormat.TEXT)

        assertTrue(markdown.contains("# Example Book"))
        assertTrue(markdown.contains("- Location: Chapter 3"))
        assertTrue(markdown.contains("> A highlighted passage."))
        assertTrue(markdown.contains("Remember this point."))
        assertTrue(text.contains("1. Chapter 3"))
        assertTrue(text.contains("Highlight:\n  A highlighted passage."))
        assertTrue(text.contains("Note:\n  Remember this point."))
    }


    @Test
    fun `epub custom highlight color exports as hex label`() {
        val document = AnnotationExportFormatter.fromEpubHighlights(
            bookTitle = "Example Book",
            sourceType = FileType.EPUB,
            highlights = listOf(
                UserHighlight(
                    id = "custom",
                    cfi = "desktop:0:1:5",
                    text = "Custom color passage.",
                    color = HighlightColor.YELLOW,
                    chapterIndex = 0,
                    colorArgb = 0xFF12ABEF.toInt()
                )
            )
        )

        val markdown = AnnotationExportFormatter.render(document, AnnotationExportFormat.MARKDOWN)
        val text = AnnotationExportFormatter.render(document, AnnotationExportFormat.TEXT)

        assertTrue(markdown.contains("- Color: #12ABEF"))
        assertTrue(text.contains("Color: #12ABEF"))
    }

    @Test
    fun `pdf highlight with note and comment thread exports in page order`() {
        val annotations = listOf(
            pdfHighlight(
                id = "p2",
                pageIndex = 1,
                text = "Second page text",
                note = null
            ),
            pdfHighlight(
                id = "p1",
                pageIndex = 0,
                text = "First page text",
                note = "Important",
                comments = listOf(
                    SharedPdfAnnotationComment(
                        id = "reply",
                        parentId = "root",
                        author = "Bea",
                        contents = "Reply",
                        createdAt = 2L
                    ),
                    SharedPdfAnnotationComment(
                        id = "root",
                        author = "Ada",
                        contents = "Root",
                        createdAt = 1L
                    )
                )
            )
        )

        val markdown = AnnotationExportFormatter.render(
            AnnotationExportFormatter.fromPdfAnnotations("PDF Book", annotations = annotations),
            AnnotationExportFormat.MARKDOWN
        )

        assertTrue(markdown.indexOf("First page text") < markdown.indexOf("Second page text"))
        assertTrue(markdown.contains("- Location: Page 1"))
        assertTrue(markdown.contains("**Note**\n\nImportant"))
        assertTrue(markdown.contains("- **Ada**: Root"))
        assertTrue(markdown.contains("  - **Bea**: Reply"))
    }

    @Test
    fun `empty and blank annotations render no content`() {
        val empty = AnnotationExportFormatter.fromEpubHighlights(
            bookTitle = "Empty",
            sourceType = FileType.EPUB,
            highlights = emptyList()
        )
        val blank = AnnotationExportFormatter.fromPdfAnnotations(
            bookTitle = "Blank PDF",
            annotations = listOf(pdfHighlight(id = "blank", pageIndex = 0, text = " ", note = " "))
        )

        assertFalse(empty.hasAnnotations)
        assertFalse(blank.hasAnnotations)
        assertEquals("", AnnotationExportFormatter.render(empty, AnnotationExportFormat.MARKDOWN))
        assertEquals("", AnnotationExportFormatter.render(blank, AnnotationExportFormat.TEXT))
        assertEquals("", AnnotationExportFormatter.render(empty, AnnotationExportFormat.JSON))
        assertEquals("", AnnotationExportFormatter.render(blank, AnnotationExportFormat.CSV))
    }

    @Test
    fun `epub highlight with note exports json document`() {
        val document = AnnotationExportFormatter.fromEpubHighlights(
            bookTitle = "Example Book",
            sourceType = FileType.EPUB,
            highlights = listOf(
                UserHighlight(
                    id = "h1",
                    cfi = "epubcfi(/6/2)",
                    text = "A highlighted passage.",
                    color = HighlightColor.YELLOW,
                    chapterIndex = 2,
                    note = "Remember this point."
                )
            )
        )

        val json = AnnotationExportFormatter.render(document, AnnotationExportFormat.JSON)
        val root = Json.parseToJsonElement(json).jsonObject

        assertEquals("Example Book", root["bookTitle"]!!.jsonPrimitive.content)
        assertEquals("EPUB", root["sourceType"]!!.jsonPrimitive.content)
        assertEquals(1, root["annotationCount"]!!.jsonPrimitive.int)
        val entry = root["annotations"]!!.jsonArray.single().jsonObject
        assertEquals("Chapter 3", entry["location"]!!.jsonPrimitive.content)
        assertEquals("A highlighted passage.", entry["highlightedText"]!!.jsonPrimitive.content)
        assertEquals("yellow", entry["color"]!!.jsonPrimitive.content)
        assertEquals("Remember this point.", entry["note"]!!.jsonPrimitive.content)
        assertTrue(entry["comments"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `pdf comment thread exports json comments with depth`() {
        val annotations = listOf(
            pdfHighlight(
                id = "p1",
                pageIndex = 0,
                text = "First page text",
                note = null,
                comments = listOf(
                    SharedPdfAnnotationComment(
                        id = "reply",
                        parentId = "root",
                        author = "Bea",
                        contents = "Reply",
                        createdAt = 2L
                    ),
                    SharedPdfAnnotationComment(
                        id = "root",
                        author = "",
                        contents = "Root",
                        createdAt = 1L
                    )
                )
            )
        )

        val json = AnnotationExportFormatter.render(
            AnnotationExportFormatter.fromPdfAnnotations("PDF Book", annotations = annotations),
            AnnotationExportFormat.JSON
        )
        val entry = Json.parseToJsonElement(json).jsonObject["annotations"]!!.jsonArray.single().jsonObject
        val comments = entry["comments"]!!.jsonArray

        assertEquals(2, comments.size)
        val root = comments[0].jsonObject
        assertEquals("Reader", root["author"]!!.jsonPrimitive.content)
        assertEquals("Root", root["contents"]!!.jsonPrimitive.content)
        assertEquals(0, root["depth"]!!.jsonPrimitive.int)
        val reply = comments[1].jsonObject
        assertEquals("Bea", reply["author"]!!.jsonPrimitive.content)
        assertEquals("Reply", reply["contents"]!!.jsonPrimitive.content)
        assertEquals(1, reply["depth"]!!.jsonPrimitive.int)
    }

    @Test
    fun `csv escapes commas quotes and newlines per rfc 4180`() {
        val document = AnnotationExportFormatter.fromEpubHighlights(
            bookTitle = "Example Book",
            sourceType = FileType.EPUB,
            highlights = listOf(
                UserHighlight(
                    id = "h1",
                    cfi = "desktop:0:0:4",
                    text = "He said \"hi\", twice\r\non a new line",
                    color = HighlightColor.YELLOW,
                    chapterIndex = 0,
                    note = "Plain note"
                )
            )
        )

        val csv = AnnotationExportFormatter.render(document, AnnotationExportFormat.CSV)

        assertTrue(csv.startsWith("Location,Highlighted Text,Color,Note,Comments\r\n"))
        assertTrue(
            csv.contains(
                "Chapter 1,\"He said \"\"hi\"\", twice\non a new line\",yellow,Plain note,\r\n"
            )
        )
    }

    @Test
    fun `markdown normalizes headings and preserves multiline notes`() {
        val document = AnnotationExportFormatter.fromEpubHighlights(
            bookTitle = "# Heading Book",
            sourceType = FileType.EPUB,
            highlights = listOf(
                UserHighlight(
                    id = "h1",
                    cfi = "desktop:0:0:4",
                    text = "Quote",
                    color = HighlightColor.BLUE,
                    chapterIndex = 0,
                    note = "Line one\r\nLine two"
                )
            )
        )

        val markdown = AnnotationExportFormatter.render(document, AnnotationExportFormat.MARKDOWN)

        assertTrue(markdown.startsWith("# \\# Heading Book"))
        assertTrue(markdown.contains("Line one\nLine two"))
    }

    private fun pdfHighlight(
        id: String,
        pageIndex: Int,
        text: String,
        note: String?,
        comments: List<SharedPdfAnnotationComment> = emptyList()
    ): SharedPdfAnnotation {
        return SharedPdfAnnotation(
            id = id,
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.5f, 0.2f),
            text = text,
            note = note,
            comments = comments,
            colorArgb = 0x8CFFEB3B.toInt()
        )
    }
}