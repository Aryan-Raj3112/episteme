package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.reader.DefaultPdfReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfAnnotationPreferencesTest {
    @Test
    fun `highlighter palette and snap setting survive reader state round trip`() {
        val palette = SharedPdfHighlighterPalette(
            listOf(
                0x8C112233.toInt(),
                0x8C445566.toInt(),
                0x8C778899.toInt(),
                0x8CAABBCC.toInt(),
                0x8CFFEEDD.toInt(),
            ),
        ).sanitized()
        val state = SharedPdfReaderState.initial(pageCount = 4)
            .copy(
                displayMode = PdfDisplayMode.VERTICAL_SCROLL,
                highlighterPalette = palette.colors,
                isHighlighterSnapEnabled = true,
            )

        val restored = SharedPdfReaderStateSerializer.decode(
            SharedPdfReaderStateSerializer.encode(state),
            fallbackPageCount = 1,
        )

        requireNotNull(restored)
        assertEquals(palette.colors, restored.highlighterPalette)
        assertTrue(restored.isHighlighterSnapEnabled)
        assertEquals(PdfDisplayMode.VERTICAL_SCROLL, restored.displayMode)
    }

    @Test
    fun `legacy reader state defaults new annotation preferences safely`() {
        val restored = SharedPdfReaderStateSerializer.decode(
            """
            {
              "version": 2,
              "pageCount": 2,
              "pageIndex": 1,
              "selectedTool": "NONE"
            }
            """.trimIndent(),
        )

        requireNotNull(restored)
        assertEquals(SharedPdfHighlighterPalette.defaultColors, restored.highlighterPalette)
        assertFalse(restored.isHighlighterSnapEnabled)
    }

    @Test
    fun `global pdf annotation preferences survive library snapshot round trip`() {
        val palette = SharedPdfHighlighterPalette(
            SharedPdfHighlighterPalette.defaultColors.mapIndexed { index, color ->
                color xor (index + 1)
            },
        ).sanitized()
        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(
            SharedLibrarySnapshotJson.encode(
                SharedLibrarySnapshot(
                    pdfHighlighterPalette = palette,
                    pdfHighlighterSnapEnabled = true,
                ),
            ),
        )

        assertEquals(palette.colors, decoded.pdfHighlighterPalette.colors)
        assertTrue(decoded.pdfHighlighterSnapEnabled)
    }

    @Test
    fun `old library snapshot defaults snap preference off`() {
        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(
            """{"schemaVersion":30,"pdfReaderDefaultSettings":{"themeId":"${DefaultPdfReaderSettings.themeId}"}}""",
        )

        assertFalse(decoded.pdfHighlighterSnapEnabled)
        assertEquals(SharedPdfHighlighterPalette.defaultColors, decoded.pdfHighlighterPalette.colors)
    }

    @Test
    fun `highlighter gesture snapping matches android angle threshold`() {
        val start = PdfPagePoint(0.2f, 0.3f)
        val horizontal = sharedPdfSnapHighlighterPoint(
            pageAspectRatio = 1f,
            currentPoint = PdfPagePoint(0.8f, 0.34f),
            startPoint = start,
        )
        val vertical = sharedPdfSnapHighlighterPoint(
            pageAspectRatio = 1f,
            currentPoint = PdfPagePoint(0.24f, 0.9f),
            startPoint = start,
        )
        val diagonal = sharedPdfSnapHighlighterPoint(
            pageAspectRatio = 1f,
            currentPoint = PdfPagePoint(0.7f, 0.8f),
            startPoint = start,
        )

        assertEquals(start.y, horizontal.y)
        assertEquals(0.8f, horizontal.x)
        assertEquals(start.x, vertical.x)
        assertEquals(0.9f, vertical.y)
        assertEquals(0.7f, diagonal.x)
        assertEquals(0.8f, diagonal.y)
    }
}
