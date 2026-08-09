package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.aryan.reader.pdf.PdfPageIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfRichTextLayoutPolicyTest {
    @Test
    fun `renderability ignores whitespace and explicit page breaks`() {
        assertFalse(" \n\t$SHARED_PDF_PAGE_BREAK_CHAR".hasRenderableSharedPdfRichText())
        assertTrue("$SHARED_PDF_PAGE_BREAK_CHAR\nVisible".hasRenderableSharedPdfRichText())
    }

    @Test
    fun `layout insertion keeps Android measured-boundary rules`() {
        val text = "Page 1Page 2"
        val split = "Page 1".length
        val layouts = listOf(
            layout(0, AnnotatedString("Page 1"), 0, split),
            layout(1, AnnotatedString("Page 2"), split, text.length),
        )

        assertEquals(split, sharedPdfRichTextInsertionIndexForPage(1, layouts, text.length))
        assertEquals(2, sharedPdfRichTextBlankInsertBreakCount(text, split))
        assertEquals(1, sharedPdfRichTextBlankInsertBreakCount("Page 1$SHARED_PDF_PAGE_BREAK_CHAR", 7))
    }

    @Test
    fun `layout remap inserts blank page while preserving annotated text spans`() {
        val styledPageTwo = AnnotatedString(
            text = "Page 2",
            spanStyles = listOf(AnnotatedString.Range(SpanStyle(color = Color.Red), 0, 6)),
        )
        val remapped = remapSharedPdfRichTextForLayoutChange(
            currentLayout = listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Pdf(1)),
            updatedLayout = listOf(
                PdfPageIdentity.Pdf(0),
                PdfPageIdentity.Blank("blank"),
                PdfPageIdentity.Pdf(1),
            ),
            pageLayouts = listOf(
                layout(0, AnnotatedString("Page 1$SHARED_PDF_PAGE_BREAK_CHAR"), 0, 7),
                layout(1, styledPageTwo, 7, 13),
            ),
        )

        assertEquals("Page 1${SHARED_PDF_PAGE_BREAK_CHAR}${SHARED_PDF_PAGE_BREAK_CHAR}Page 2", remapped.text)
        assertEquals(Color.Red, remapped.spanStyles.single().item.color)
        assertEquals(8, remapped.spanStyles.single().start)
        assertEquals(14, remapped.spanStyles.single().end)
    }

    @Test
    fun `trailing page break produces one stable blank page`() {
        val text = AnnotatedString("A$SHARED_PDF_PAGE_BREAK_CHAR")
        val once = listOf(layout(0, AnnotatedString("A"), 0, 1))
            .withTrailingBlankRichTextPageIfNeeded(text, 900f)
        val twice = once.withTrailingBlankRichTextPageIfNeeded(text, 900f)

        assertEquals(2, once.size)
        assertEquals(once, twice)
        assertEquals(text.length, once.last().globalStartIndex)
    }

    private fun layout(
        page: Int,
        text: AnnotatedString,
        start: Int,
        end: Int,
    ) = SharedPdfRichPageLayout(page, text, start, end, 1_000f)
}
