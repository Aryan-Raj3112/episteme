package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfTextDraftParagraphsTest {

    private fun draft(text: String = "", manuallySized: Boolean = true) = SharedPdfTextDraft(
        id = "d1",
        pageIndex = 0,
        bounds = PdfPageBounds(0.3f, 0.45f, 0.7f, 0.55f),
        text = text,
        isManuallySized = manuallySized,
    )

    @Test
    fun `withTextAndParagraphs normalizes newlines and trims paragraphs`() {
        val result = draft().withTextAndParagraphs(
            "a\r\nb\rc",
            listOf(
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
                SharedPdfRichParagraph(),
                SharedPdfRichParagraph(),
            ),
            IntSize(1000, 1414),
        )
        assertEquals("a\nb\nc", result.text)
        assertEquals(
            listOf(SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER)),
            result.paragraphs,
        )
    }

    @Test
    fun `withTextAndParagraphs keeps manual bounds like withText`() {
        val before = draft().bounds
        val result = draft().withTextAndParagraphs(
            "hello",
            listOf(SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.RIGHT)),
            IntSize(1000, 1414),
        )
        assertEquals(before, result.bounds)
        assertEquals("hello", result.text)
    }

    @Test
    fun `toAnnotation persists trimmed paragraphs for export`() {
        val annotation = draft().withTextAndParagraphs(
            "• a",
            listOf(
                SharedPdfRichParagraph(
                    alignment = SharedPdfRichTextAlign.CENTER,
                    listType = SharedPdfRichListType.BULLET,
                )
            ),
            IntSize(1000, 1414),
        ).toAnnotation()
        assertEquals("• a", annotation.text)
        assertEquals(
            listOf(
                SharedPdfRichParagraph(
                    alignment = SharedPdfRichTextAlign.CENTER,
                    listType = SharedPdfRichListType.BULLET,
                )
            ),
            annotation.paragraphs,
        )
    }

    @Test
    fun `annotated builder emits alignment runs for committed rendering`() {
        val annotated = sharedPdfTextBoxAnnotatedString(
            "a\nb",
            listOf(
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
                SharedPdfRichParagraph(),
            ),
            SpanStyle(),
        )
        val centers = annotated.paragraphStyles.filter {
            it.item.textAlign == SharedPdfRichTextAlign.CENTER.toComposeTextAlign()
        }
        assertTrue(centers.isNotEmpty())
        // Plain documents stay sparse: no runs for all-default paragraphs.
        val plain = sharedPdfTextBoxAnnotatedString("a\nb", emptyList(), SpanStyle())
        assertTrue(plain.paragraphStyles.isEmpty())
    }

    @Test
    fun `toggle selection contract feeds the one-shot token`() {
        val annotated = sharedPdfTextBoxAnnotatedString("", emptyList(), SpanStyle())
        val result = sharedPdfToggleTextBoxList(
            annotated,
            TextRange(0),
            SharedPdfRichListType.NUMBERED,
        )
        val pending = SharedPdfTextBoxPendingSelection(result.selection, 1L)
        assertEquals("1. ", result.text)
        assertEquals(TextRange(sharedPdfRichNumberedMarker(1).length), pending.range)
        assertEquals(1L, pending.token)
    }
}
