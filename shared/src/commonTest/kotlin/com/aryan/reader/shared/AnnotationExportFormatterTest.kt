package com.aryan.reader.shared

import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationComment
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