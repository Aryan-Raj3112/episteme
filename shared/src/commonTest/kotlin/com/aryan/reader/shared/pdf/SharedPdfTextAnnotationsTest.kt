package com.aryan.reader.shared.pdf

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfTextAnnotationsTest {

    @Test
    fun `createAnnotation applies Android-style text config`() {
        val style = SharedPdfTextStyleConfig(
            colorArgb = 0xFF123456.toInt(),
            backgroundColorArgb = 0x8CFFEB3B.toInt(),
            fontSize = 20f,
            isBold = true,
            isItalic = true,
            isUnderline = true,
            isStrikeThrough = true,
            fontPath = "asset:fonts/lora.ttf",
            fontName = "Lora"
        )

        val annotation = SharedPdfTextAnnotationDefaults.createAnnotation(
            id = "text-1",
            pageIndex = 3,
            anchor = PdfPagePoint(0.8f, 0.92f, 42L),
            canvasSize = IntSize(1_000, 1_400),
            text = "  Styled note  ",
            style = style,
            createdAt = 99L
        )

        assertEquals(PdfAnnotationKind.TEXT, annotation.kind)
        assertEquals(PdfInkTool.TEXT, annotation.tool)
        assertEquals("Styled note", annotation.text)
        assertEquals(style.copy(pageRelativeFontSize = 0.04f), annotation.sharedPdfTextStyle())
        assertEquals(0.04f, annotation.pageRelativeFontSize ?: 0f, 0.0001f)
        assertEquals(99L, annotation.createdAt)
        assertTrue(annotation.bounds!!.left >= 0f)
        assertTrue(annotation.bounds.right <= 1f)
        assertTrue(annotation.bounds.top >= 0f)
        assertTrue(annotation.bounds.bottom <= 1f)
    }

    @Test
    fun `withSharedPdfTextStyle replaces all style fields only`() {
        val original = SharedPdfAnnotation(
            id = "text-2",
            pageIndex = 1,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            bounds = PdfPageBounds(0.1f, 0.2f, 0.5f, 0.3f),
            text = "Keep me",
            colorArgb = 0xFF000000.toInt(),
            backgroundArgb = 0x00000000,
            fontSize = 16f,
            createdAt = 5L
        )
        val style = SharedPdfTextStyleConfig(
            colorArgb = 0xFFFF0000.toInt(),
            backgroundColorArgb = 0x8C64B5F6.toInt(),
            fontSize = 24f,
            isBold = true,
            fontName = "Roboto Mono",
            fontPath = "asset:fonts/roboto_mono.ttf"
        )

        val updated = original.withSharedPdfTextStyle(style)

        assertEquals("text-2", updated.id)
        assertEquals("Keep me", updated.text)
        assertEquals(original.bounds, updated.bounds)
        assertEquals(5L, updated.createdAt)
        assertEquals(style.copy(pageRelativeFontSize = 0.048f), updated.sharedPdfTextStyle())
    }

    @Test
    fun `page relative font size drives Android-compatible text rendering size`() {
        val canvasSize = IntSize(1_000, 1_500)
        val style = SharedPdfTextStyleConfig(fontSize = 20f, pageRelativeFontSize = 0.03f)

        assertEquals(45f, style.sharedPdfTextFontSizePx(canvasSize), 0.0001f)

        val annotation = SharedPdfAnnotation(
            id = "text-android-size",
            pageIndex = 0,
            kind = PdfAnnotationKind.TEXT,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.4f, 0.2f),
            text = "Sized like Android",
            colorArgb = 0xFF000000.toInt(),
            fontSize = 20f,
            pageRelativeFontSize = 0.03f
        )

        assertEquals(45f, annotation.sharedPdfTextFontSizePx(canvasSize), 0.0001f)
    }

    @Test
    fun `text bounds grow for wrapped content and stay on page`() {
        val style = SharedPdfTextStyleConfig(fontSize = 18f)
        val shortBounds = SharedPdfTextAnnotationDefaults.boundsForPlacedText(
            anchor = PdfPagePoint(0.1f, 0.1f),
            canvasSize = IntSize(800, 1_200),
            text = "Short",
            style = style
        )
        val longBounds = SharedPdfTextAnnotationDefaults.boundsForPlacedText(
            anchor = PdfPagePoint(0.92f, 0.96f),
            canvasSize = IntSize(800, 1_200),
            text = "This is a much longer text annotation that should wrap across multiple lines.",
            style = style
        )

        assertTrue(longBounds.bottom - longBounds.top > shortBounds.bottom - shortBounds.top)
        assertTrue(longBounds.right <= 1f)
        assertTrue(longBounds.bottom <= 1f)
    }

    @Test
    fun `draft starts empty at click location and commits as text annotation`() {
        val style = SharedPdfTextStyleConfig(
            colorArgb = 0xFF4A148C.toInt(),
            backgroundColorArgb = 0x8CFFEB3B.toInt(),
            fontSize = 18f,
            isBold = true
        )
        val draft = SharedPdfTextAnnotationDefaults.createDraft(
            id = "text-draft",
            pageIndex = 2,
            anchor = PdfPagePoint(0.2f, 0.3f, 7L),
            canvasSize = IntSize(1_000, 1_400),
            style = style,
            createdAt = 7L
        ).withText("  Inline note  ", IntSize(1_000, 1_400))

        val annotation = draft.toAnnotation()

        assertEquals(PdfAnnotationKind.TEXT, annotation.kind)
        assertEquals(PdfInkTool.TEXT, annotation.tool)
        assertEquals("Inline note", annotation.text)
        assertEquals(style.copy(pageRelativeFontSize = 0.036f), annotation.sharedPdfTextStyle())
        assertEquals(draft.bounds, annotation.bounds)
    }

    @Test
    fun `draft reflows when text or style changes`() {
        val canvasSize = IntSize(800, 1_200)
        val draft = SharedPdfTextAnnotationDefaults.createDraft(
            id = "text-draft-2",
            pageIndex = 0,
            anchor = PdfPagePoint(0.82f, 0.9f),
            canvasSize = canvasSize,
            style = SharedPdfTextStyleConfig(fontSize = 14f),
            createdAt = 11L
        )
        val expanded = draft.withText(
            "A longer inline text annotation that wraps across more than one row.",
            canvasSize
        )
        val restyled = expanded.withStyle(expanded.style.copy(fontSize = 24f), canvasSize)

        assertTrue(expanded.bounds.bottom - expanded.bounds.top > draft.bounds.bottom - draft.bounds.top)
        assertTrue(restyled.bounds.bottom - restyled.bounds.top > expanded.bounds.bottom - expanded.bounds.top)
        assertTrue(restyled.bounds.right <= 1f)
        assertTrue(restyled.bounds.bottom <= 1f)
    }

    @Test
    fun `manually sized draft preserves bounds while typing and styling`() {
        val canvasSize = IntSize(800, 1_200)
        val resizedBounds = PdfPageBounds(0.2f, 0.3f, 0.7f, 0.48f)
        val draft = SharedPdfTextAnnotationDefaults.createDraft(
            id = "text-draft-3",
            pageIndex = 0,
            anchor = PdfPagePoint(0.2f, 0.3f),
            canvasSize = canvasSize,
            style = SharedPdfTextStyleConfig(fontSize = 14f),
            createdAt = 12L
        ).withBounds(resizedBounds)

        val typed = draft.withText("Manual size should stay fixed", canvasSize)
        val styled = typed.withStyle(typed.style.copy(fontSize = 24f), canvasSize)

        assertEquals(resizedBounds, typed.bounds)
        assertEquals(resizedBounds, styled.bounds)
        assertTrue(styled.isManuallySized)
    }

    @Test
    fun `resize handle updates normalized bounds and keeps box on page`() {
        val resized = PdfPageBounds(0.2f, 0.2f, 0.5f, 0.4f).resizedBy(
            handle = SharedPdfTextResizeHandle.BOTTOM_RIGHT,
            deltaXPx = 160f,
            deltaYPx = 120f,
            canvasSize = IntSize(1_000, 1_000)
        )
        val clamped = resized.resizedBy(
            handle = SharedPdfTextResizeHandle.TOP_LEFT,
            deltaXPx = -1_000f,
            deltaYPx = -1_000f,
            canvasSize = IntSize(1_000, 1_000)
        )

        assertTrue(abs(resized.right - 0.66f) < 0.001f)
        assertTrue(abs(resized.bottom - 0.52f) < 0.001f)
        assertEquals(0f, clamped.left)
        assertEquals(0f, clamped.top)
        assertTrue(clamped.right <= 1f)
        assertTrue(clamped.bottom <= 1f)
    }

    @Test
    fun `move keeps text box size and clamps to page`() {
        val moved = PdfPageBounds(0.2f, 0.3f, 0.5f, 0.45f).movedBy(
            deltaXPx = 100f,
            deltaYPx = -120f,
            canvasSize = IntSize(1_000, 1_000)
        )
        val clamped = moved.movedBy(
            deltaXPx = 1_000f,
            deltaYPx = 1_000f,
            canvasSize = IntSize(1_000, 1_000)
        )

        assertTrue(abs((moved.right - moved.left) - 0.3f) < 0.001f)
        assertTrue(abs((moved.bottom - moved.top) - 0.15f) < 0.001f)
        assertTrue(abs(moved.left - 0.3f) < 0.001f)
        assertTrue(abs(moved.top - 0.18f) < 0.001f)
        assertTrue(abs(clamped.left - 0.7f) < 0.001f)
        assertTrue(abs(clamped.top - 0.85f) < 0.001f)
        assertEquals(1f, clamped.right)
        assertEquals(1f, clamped.bottom)
    }

    @Test
    fun `normalizeTextDraft trims and normalizes line endings`() {
        assertEquals(
            "Line one\nLine two",
            SharedPdfTextAnnotationDefaults.normalizeTextDraft(" \r\nLine one\r\nLine two\n ")
        )
    }
}
