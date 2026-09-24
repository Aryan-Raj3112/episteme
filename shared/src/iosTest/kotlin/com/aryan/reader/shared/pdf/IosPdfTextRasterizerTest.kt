package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPdfTextRasterizerTest {

    @Test
    fun cropKeepsOpaqueGlyphBoundsAndDropsTransparentMargins() {
        // 4x3 BGRA; opaque only in a 2x2 block at (1,1)
        val width = 4
        val height = 3
        val pixels = ByteArray(width * height * 4)
        fun put(x: Int, y: Int, b: Int, g: Int, r: Int, a: Int) {
            val o = (y * width + x) * 4
            pixels[o] = b.toByte()
            pixels[o + 1] = g.toByte()
            pixels[o + 2] = r.toByte()
            pixels[o + 3] = a.toByte()
        }
        put(1, 1, 0x10, 0x20, 0x30, 0xFF)
        put(2, 1, 0x40, 0x50, 0x60, 0xFF)
        put(1, 2, 0x70, 0x80, 0x90, 0xFF)
        put(2, 2, 0xA0, 0xB0, 0xC0, 0xFF)

        val cropped = cropIosPdfRasterOverlay(
            pageIndex = 2,
            bounds = PdfPageBounds(0.25f, 0.2f, 0.75f, 0.8f),
            width = width,
            height = height,
            bgraPixels = pixels,
        )

        assertNotNull(cropped)
        assertEquals(2, cropped.width)
        assertEquals(2, cropped.height)
        assertEquals(2, cropped.pageIndex)
        assertEquals(0xFF, cropped.bgraPixels[3].toInt() and 0xFF)
        // Crop rect tight to opaque block: minX=1, minY=1, maxX=2, maxY=2
        assertEquals(0.25f + 0.5f * (1f / 4f), cropped.bounds.left, 1e-5f)
        assertEquals(0.2f + 0.6f * (1f / 3f), cropped.bounds.top, 1e-5f)
        assertEquals(0.25f + 0.5f * (3f / 4f), cropped.bounds.right, 1e-5f)
        assertEquals(0.2f + 0.6f * (3f / 3f), cropped.bounds.bottom, 1e-5f)
    }

    @Test
    fun cropReturnsNullWhenEveryTexelIsTransparentOrBufferIsShort() {
        val transparent = ByteArray(2 * 2 * 4)
        assertNull(cropIosPdfRasterOverlay(0, PdfPageBounds(0f, 0f, 1f, 1f), 2, 2, transparent))
        assertNull(cropIosPdfRasterOverlay(0, PdfPageBounds(0f, 0f, 1f, 1f), 2, 2, ByteArray(4)))
        assertNull(cropIosPdfRasterOverlay(0, PdfPageBounds(0f, 0f, 1f, 1f), 0, 0, ByteArray(0)))
    }

    @Test
    fun fontRegistryLoadsBundledAssetPresetAndFallsBackForUnknownAssetPaths() = kotlinx.coroutines.test.runTest {
        val lora = SharedPdfAnnotation(
            id = "text-lora",
            pageIndex = 0,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.5f, 0.3f),
            text = "Hello",
            colorArgb = 0xFF000000.toInt(),
            fontPath = "asset:fonts/lora.ttf",
            fontName = "Lora",
        )
        val unknown = lora.copy(
            id = "text-unknown",
            fontPath = "asset:fonts/not-bundled.ttf",
            fontName = null,
        )

        val registry = buildIosPdfTextFontRegistry(listOf(lora, unknown))

        val loraAlias = registry.familyName("asset:fonts/lora.ttf", "Lora")
        assertNotNull(loraAlias)
        assertTrue(loraAlias.startsWith("ReaderPdfImportedFont"), "expected typeface alias, got $loraAlias")
        assertEquals(loraAlias, registry.familyName(null, "Lora"))
        // Unknown asset paths have no preset family (matches sharedPdfFontFamily /
        // sharedPdfFallbackExportFontFamily); export then falls back to Arial.
        assertNull(registry.familyName("asset:fonts/not-bundled.ttf", null))
    }
}
