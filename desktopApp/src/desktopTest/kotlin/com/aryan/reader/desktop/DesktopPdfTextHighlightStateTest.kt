package com.aryan.reader.desktop

import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPdfTextHighlightStateTest {
    @Test
    fun `text selection highlight keeps chosen text selection mode after creation`() {
        val annotation = textSelectionHighlight()
        val state = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.TextSelectionModeChanged(true))

        val next = state.withDesktopPdfTextSelectionHighlightAdded(annotation)

        assertEquals(listOf(annotation), next.annotations)
        assertTrue(next.isTextSelectionMode)
        assertEquals(PdfInkTool.NONE, next.selectedTool)
        assertNull(next.selectedAnnotationId)
    }

    @Test
    fun `dismissing selected text highlight sheet keeps chosen text selection mode`() {
        val annotation = textSelectionHighlight()
        val state = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.TextSelectionModeChanged(true))
            .copy(annotations = listOf(annotation), selectedAnnotationId = annotation.id)

        val next = state.withDesktopPdfTextHighlightSheetDismissed()

        assertTrue(next.isTextSelectionMode)
        assertEquals(PdfInkTool.NONE, next.selectedTool)
        assertNull(next.selectedAnnotationId)
    }

    @Test
    fun `dismissing non text highlight annotation keeps text selection mode unchanged`() {
        val annotation = textSelectionHighlight().copy(rangeStartIndex = null, rangeEndIndex = null)
        val state = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.TextSelectionModeChanged(true))
            .copy(annotations = listOf(annotation), selectedAnnotationId = annotation.id)

        val next = state.withDesktopPdfTextHighlightSheetDismissed()

        assertTrue(next.isTextSelectionMode)
        assertNull(next.selectedAnnotationId)
    }

    private fun textSelectionHighlight(): SharedPdfAnnotation {
        return SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            text = "selected text",
            colorArgb = 0x55FFEB3B,
            rangeStartIndex = 1,
            rangeEndIndex = 12,
            createdAt = 1L
        )
    }
}
