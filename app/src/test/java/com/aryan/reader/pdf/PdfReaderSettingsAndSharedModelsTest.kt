package com.aryan.reader.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.BuildConfig
import com.aryan.reader.FileType
import com.aryan.reader.pdf.data.AnnotationSettingsRepository
import com.aryan.reader.pdf.data.AnnotationToolSettings
import com.aryan.reader.pdf.data.TextStyleConfig
import com.aryan.reader.pdf.data.ToolConfig
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReaderSettingsAndSharedModelsTest {

    @Test
    fun `AnnotationToolSettings falls back safely for invalid selected and last tools`() {
        val settings = AnnotationToolSettings(
            selectedToolName = "BROKEN",
            lastActivePenType = "MISSING",
            lastActiveHighlighterType = "NOPE"
        )

        assertEquals(InkType.PEN, settings.getActiveTool())
        assertEquals(InkType.PEN, settings.getLastPenTool())
        assertEquals(InkType.HIGHLIGHTER, settings.getLastHighlighterTool())
    }

    @Test
    fun `AnnotationToolSettings returns custom configs and palettes`() {
        val settings = AnnotationToolSettings(
            selectedToolName = InkType.TEXT.name,
            toolConfigs = mapOf(InkType.PEN.name to ToolConfig(Color.Cyan.toArgb(), 0.25f)),
            penPaletteArgb = listOf(Color.Red.toArgb(), Color.Green.toArgb()),
            highlighterPaletteArgb = listOf(Color.Yellow.toArgb()),
            textStyle = TextStyleConfig(colorArgb = Color.Magenta.toArgb(), fontSize = 22f),
            isHighlighterSnapEnabled = true
        )

        assertEquals(InkType.TEXT, settings.getActiveTool())
        assertEquals(Color.Cyan.toArgb(), settings.getToolColor(InkType.PEN).toArgb())
        assertEquals(0.25f, settings.getToolThickness(InkType.PEN), 0.0001f)
        assertEquals(Color.Red.toArgb(), settings.getPenPalette().first().toArgb())
        assertEquals(Color.Yellow.toArgb(), settings.getHighlighterPalette().single().toArgb())
        assertTrue(settings.isHighlighterSnapEnabled)
        assertEquals(22f, settings.textStyle.fontSize, 0.0001f)
    }

    @Test
    fun `AnnotationSettingsRepository default configs cover every ink type`() {
        InkType.entries.forEach { type ->
            val config = AnnotationSettingsRepository.getDefaultConfig(type)

            assertTrue("Expected positive thickness for $type", config.thickness > 0f)
        }
    }

    @Test
    fun `SharedPdfAnnotationSerializer round trips current store shape`() {
        val annotation = SharedPdfAnnotation(
            id = "ann-1",
            pageIndex = 7,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            points = listOf(PdfPagePoint(0.1f, 0.2f, 100L)),
            bounds = PdfPageBounds(0.1f, 0.2f, 0.3f, 0.4f),
            text = "Margin note",
            colorArgb = 0xFF112233.toInt(),
            backgroundArgb = 0x66112233,
            strokeWidth = 1.5f,
            fontSize = 19f,
            isBold = true,
            isItalic = true,
            createdAt = 1234L
        )

        val decoded = SharedPdfAnnotationSerializer.decode(
            SharedPdfAnnotationSerializer.encode(listOf(annotation))
        )

        assertEquals(listOf(annotation), decoded)
        assertEquals(emptyList<SharedPdfAnnotation>(), SharedPdfAnnotationSerializer.decode(""))
        assertEquals(emptyList<SharedPdfAnnotation>(), SharedPdfAnnotationSerializer.decode("bad json"))
    }

    @Test
    fun `SharedPdfAnnotationSerializer decodes legacy bare annotation array`() {
        val legacyJson = """
            [
              {
                "id": "legacy",
                "pageIndex": 1,
                "kind": "INK",
                "tool": "PEN",
                "points": [{"x":0.2,"y":0.3,"timestamp":9}],
                "colorArgb": -1
              }
            ]
        """.trimIndent()

        val decoded = SharedPdfAnnotationSerializer.decode(legacyJson).single()

        assertEquals("legacy", decoded.id)
        assertEquals(1, decoded.pageIndex)
        assertEquals(PdfAnnotationKind.INK, decoded.kind)
        assertEquals(PdfInkTool.PEN, decoded.tool)
        assertEquals(PdfPagePoint(0.2f, 0.3f, 9L), decoded.points.single())
    }

    @Test
    fun `SharedPdfAnnotationDefaults supplies expected tool defaults and palettes`() {
        assertEquals(5, SharedPdfAnnotationDefaults.penPalette.size)
        assertEquals(SharedPdfHighlighterPalette.MaxColors, SharedPdfAnnotationDefaults.highlighterPalette.size)

        val pen = SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN)
        val eraser = SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER)
        val highlighter = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER)

        assertTrue(pen.strokeWidth > 0f)
        assertEquals(0x00000000, eraser.colorArgb)
        assertTrue(highlighter.strokeWidth > pen.strokeWidth)
    }

    @Test
    fun `SharedPdfHighlighterPalette preserves slots and normalizes alpha`() {
        val palette = SharedPdfHighlighterPalette(
            colors = listOf(0xFFFF0000.toInt())
        ).sanitized()

        assertEquals(SharedPdfHighlighterPalette.MaxColors, palette.colors.size)
        assertEquals(0x8CFF0000.toInt(), palette.colors.first())
        assertTrue(palette.colors.all { (it ushr 24) == SharedPdfHighlighterPalette.DefaultAlpha })

        val updated = palette.withColorAt(2, 0xFF123456.toInt())

        assertEquals(0x8C123456.toInt(), updated.colors[2])
    }

    @Test
    fun `PdfZoomSpec clamps scale and keeps render size under pixel budget`() {
        val spec = PdfZoomSpec(min = 0.5f, max = 4f, default = 1f, maxRenderPixels = 1_000_000)

        assertEquals(0.5f, spec.clamp(0.1f), 0.0001f)
        assertEquals(4f, spec.clamp(10f), 0.0001f)

        val safeScale = spec.safeRenderScale(pageWidth = 2_000f, pageHeight = 2_000f, requestedScale = 4f)
        assertTrue(safeScale < 1f)
        assertTrue(safeScale >= 0.1f)

        val (width, height) = spec.renderSize(pageWidth = 2_000f, pageHeight = 2_000f, requestedScale = 4f)
        assertTrue(width * height <= 1_000_000)
        assertTrue(width >= 1)
        assertTrue(height >= 1)
    }

    @Test
    fun `pdf toolbar reset defaults match first-run toolbar defaults`() {
        assertEquals(
            setOf(
                PdfReaderTool.SCREEN_ORIENTATION.name,
                PdfReaderTool.HIGHLIGHT_ALL.name,
                PdfReaderTool.BRIGHTNESS.name
            ),
            defaultPdfHiddenTools()
        )
        val expectedToolOrder = PdfReaderTool.entries.filter(::isPdfReaderToolAvailable)

        assertEquals(expectedToolOrder, defaultPdfToolOrder())
        assertEquals(
            expectedToolOrder.filter { it.category == "Bottom Bar" }.map { it.name }.toSet(),
            defaultPdfBottomTools()
        )

        val defaultItems = buildPdfToolbarItems(
            hiddenTools = defaultPdfHiddenTools(),
            toolOrder = defaultPdfToolOrder(),
            bottomTools = defaultPdfBottomTools()
        )

        assertEquals(
            PdfToolbarSection.HIDDEN,
            defaultItems.single { it.tool == PdfReaderTool.SCREEN_ORIENTATION }.section
        )
        assertEquals(
            PdfToolbarSection.HIDDEN,
            defaultItems.single { it.tool == PdfReaderTool.HIGHLIGHT_ALL }.section
        )
        assertEquals(
            PdfToolbarSection.HIDDEN,
            defaultItems.single { it.tool == PdfReaderTool.BRIGHTNESS }.section
        )
        assertEquals(
            PdfToolbarSection.BOTTOM,
            defaultItems.single { it.tool == PdfReaderTool.SLIDER }.section
        )

        val customPlacementItems = buildPdfToolbarItems(
            hiddenTools = emptySet(),
            toolOrder = defaultPdfToolOrder(),
            bottomTools = setOf(PdfReaderTool.THEME.name)
        )
        assertEquals(
            PdfToolbarSection.BOTTOM,
            customPlacementItems.single { it.tool == PdfReaderTool.THEME }.section
        )

        val expectedMoreTools = buildSet {
            addAll(
                setOf(
                    PdfReaderTool.FILE_INFO,
                    PdfReaderTool.VISUAL_OPTIONS,
                    PdfReaderTool.TAP_TO_TURN,
                    PdfReaderTool.READING_MODE,
                    PdfReaderTool.KEEP_SCREEN_ON,
                    PdfReaderTool.AUTO_SCROLL,
                    PdfReaderTool.TTS_SETTINGS,
                    PdfReaderTool.TTS_REPLACEMENTS,
                    PdfReaderTool.BOOKMARK,
                    PdfReaderTool.PAGE_MANAGEMENT,
                    PdfReaderTool.REFLOW,
                    PdfReaderTool.SHARE,
                    PdfReaderTool.SAVE_COPY,
                    PdfReaderTool.PRINT
                )
            )
            if (BuildConfig.IS_PRO) add(PdfReaderTool.OCR_LANGUAGE)
        }

        assertEquals(
            expectedMoreTools,
            defaultItems
                .filter { it.type == PdfFlatItemType.MORE_TOOL }
                .mapNotNull { it.tool }
                .toSet()
        )
        assertFalse(defaultItems.any { it.tool?.name == "FULL_SCREEN" })
        if (!BuildConfig.IS_PRO) {
            assertFalse(defaultItems.any { it.tool == PdfReaderTool.OCR_LANGUAGE })
        }
    }

    @Test
    fun `pdf overflow sections end at reflow when all file actions are hidden`() {
        val sections = pdfOverflowMenuSections(
            hiddenTools = setOf(
                PdfReaderTool.SHARE.name,
                PdfReaderTool.SAVE_COPY.name,
                PdfReaderTool.PRINT.name
            ),
            hasHiddenToolbarTools = false,
            isPro = false,
            effectiveFileType = FileType.PDF,
            hasFileInfo = false
        )

        assertEquals(PdfOverflowMenuSection.REFLOW, sections.last())
        assertTrue(PdfOverflowMenuSection.FILE_ACTIONS !in sections)
    }

    @Test
    fun `pdf overflow sections keep file actions when only print is hidden`() {
        val sections = pdfOverflowMenuSections(
            hiddenTools = setOf(PdfReaderTool.PRINT.name),
            hasHiddenToolbarTools = false,
            isPro = false,
            effectiveFileType = FileType.PDF,
            hasFileInfo = false
        )

        assertEquals(PdfOverflowMenuSection.FILE_ACTIONS, sections.last())
    }

    @Test
    fun `pdf overflow sections expose file info only when available and visible`() {
        val visibleSections = pdfOverflowMenuSections(
            hiddenTools = emptySet(),
            hasHiddenToolbarTools = false,
            isPro = false,
            effectiveFileType = FileType.PDF,
            hasFileInfo = true
        )
        val missingItemSections = pdfOverflowMenuSections(
            hiddenTools = emptySet(),
            hasHiddenToolbarTools = false,
            isPro = false,
            effectiveFileType = FileType.PDF,
            hasFileInfo = false
        )
        val hiddenSections = pdfOverflowMenuSections(
            hiddenTools = setOf(PdfReaderTool.FILE_INFO.name),
            hasHiddenToolbarTools = false,
            isPro = false,
            effectiveFileType = FileType.PDF,
            hasFileInfo = true
        )

        assertTrue(PdfOverflowMenuSection.FILE_INFO in visibleSections)
        assertEquals(PdfOverflowMenuSection.FILE_INFO, visibleSections.last())
        assertFalse(PdfOverflowMenuSection.FILE_INFO in missingItemSections)
        assertFalse(PdfOverflowMenuSection.FILE_INFO in hiddenSections)
    }
}
