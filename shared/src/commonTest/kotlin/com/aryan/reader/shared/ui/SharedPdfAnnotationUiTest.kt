package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfAnnotationUiTest {
    @Test
    fun `text highlight annotations render with readable highlighter blending`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            colorArgb = Color.Yellow.copy(alpha = 0.9f).toArgb()
        )

        val style = sharedPdfHighlightAnnotationOverlayStyle(annotation)

        assertEquals(BlendMode.Multiply, style.blendMode)
        assertEquals(SharedPdfAndroidHighlightColors.RenderAlpha, style.color.alpha)
    }

    @Test
    fun `text highlight annotations preserve lower custom opacity`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            colorArgb = Color.Yellow.copy(alpha = 0.18f).toArgb()
        )

        val style = sharedPdfHighlightAnnotationOverlayStyle(annotation)

        assertEquals(0.18f, style.color.alpha, 0.005f)
    }

    @Test
    fun `interaction dock keeps reading modes before markup actions`() {
        assertEquals(
            listOf(
                SharedPdfInteractionDockItem.PAN,
                SharedPdfInteractionDockItem.SELECT_TEXT,
                SharedPdfInteractionDockItem.PEN,
                SharedPdfInteractionDockItem.HIGHLIGHTER,
                SharedPdfInteractionDockItem.TEXT_NOTE,
                SharedPdfInteractionDockItem.ERASER,
                SharedPdfInteractionDockItem.UNDO,
                SharedPdfInteractionDockItem.REDO,
                SharedPdfInteractionDockItem.CLEAR_PAGE
            ),
            sharedPdfInteractionDockItems()
        )
    }

    @Test
    fun `interaction dock only exposes available markup groups`() {
        assertEquals(
            listOf(
                SharedPdfInteractionDockItem.PAN,
                SharedPdfInteractionDockItem.SELECT_TEXT,
                SharedPdfInteractionDockItem.TEXT_NOTE,
                SharedPdfInteractionDockItem.UNDO,
                SharedPdfInteractionDockItem.REDO,
                SharedPdfInteractionDockItem.CLEAR_PAGE
            ),
            sharedPdfInteractionDockItems(tools = listOf(PdfInkTool.TEXT))
        )
    }
}
