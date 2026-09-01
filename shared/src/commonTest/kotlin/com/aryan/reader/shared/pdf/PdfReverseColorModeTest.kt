package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfReverseColorModeTest {
    @Test
    fun `legacy rgb mode remains a channel negative and keeps alpha`() {
        assertEquals(0x71DCBAA9, invertPdfArgb(0x71234556, PdfReverseColorMode.RGB))
    }

    @Test
    fun `lightness inversion matches Okular HSL construction`() {
        assertEquals(intArrayOf(225, 235, 245).toList(), invertPdfLightnessPixel(10, 20, 30).toList())
        assertEquals(intArrayOf(245, 235, 225).toList(), invertPdfLightnessPixel(30, 20, 10).toList())
        assertEquals(intArrayOf(200, 200, 200).toList(), invertPdfLightnessPixel(55, 55, 55).toList())
    }

    @Test
    fun `luma modes invert neutral colors exactly`() {
        for (mode in listOf(PdfReverseColorMode.LUMA_SRGB_LINEAR, PdfReverseColorMode.LUMA_SYMMETRIC)) {
            assertEquals(0xFF000000.toInt(), invertPdfArgb(0xFFFFFFFF.toInt(), mode))
            assertEquals(0xFFFFFFFF.toInt(), invertPdfArgb(0xFF000000.toInt(), mode))
            assertEquals(0xFF7F7F7F.toInt(), invertPdfArgb(0xFF808080.toInt(), mode))
        }
    }

    @Test
    fun `luma transforms retain ordering of channels and stay in gamut`() {
        val source = 0xFF3A78C2.toInt()
        for (mode in listOf(PdfReverseColorMode.LUMA_SRGB_LINEAR, PdfReverseColorMode.LUMA_SYMMETRIC)) {
            val output = invertPdfArgb(source, mode)
            val red = (output ushr 16) and 0xFF
            val green = (output ushr 8) and 0xFF
            val blue = output and 0xFF
            assertTrue(red in 0..255 && green in 0..255 && blue in 0..255)
            assertTrue(blue > green && green > red, "mode=$mode output=$output")
        }
    }

    @Test
    fun `golden pixels match the independent Okular piecewise calculation`() {
        assertEquals(0xFFE6EDF5.toInt(), invertPdfArgb(0xFF0A141E.toInt(), PdfReverseColorMode.LUMA_SRGB_LINEAR))
        assertEquals(0xFFE1EBF5.toInt(), invertPdfArgb(0xFF0A141E.toInt(), PdfReverseColorMode.LUMA_SYMMETRIC))
        assertEquals(0xFFFFBABA.toInt(), invertPdfArgb(0xFFFF0000.toInt(), PdfReverseColorMode.LUMA_SRGB_LINEAR))
        assertEquals(0xFFFF8080.toInt(), invertPdfArgb(0xFFFF0000.toInt(), PdfReverseColorMode.LUMA_SYMMETRIC))
        assertEquals(0xFFBAD3ED.toInt(), invertPdfArgb(0xFF123456.toInt(), PdfReverseColorMode.LUMA_SRGB_LINEAR))
        assertEquals(0xFFA9CBED.toInt(), invertPdfArgb(0xFF123456.toInt(), PdfReverseColorMode.LUMA_SYMMETRIC))
    }

    @Test
    fun `mode ids are forward compatible with rgb fallback`() {
        PdfReverseColorMode.entries.forEach { assertEquals(it, PdfReverseColorMode.fromId(it.id)) }
        assertEquals(PdfReverseColorMode.RGB, PdfReverseColorMode.fromId("unknown"))
        assertEquals(PdfReverseColorMode.RGB, PdfReverseColorMode.fromId(null))
    }

    @Test
    fun `protected image pixels keep source color while page pixels transform`() {
        val source = 0xFF123456.toInt()
        val image = PdfReverseColorRect(left = 1, top = 0, right = 2, bottom = 1)

        assertEquals(source, invertPdfArgbIfUnprotected(source, 1, 0, PdfReverseColorMode.LIGHTNESS, listOf(image)))
        assertEquals(
            invertPdfArgb(source, PdfReverseColorMode.LIGHTNESS),
            invertPdfArgbIfUnprotected(source, 0, 0, PdfReverseColorMode.LIGHTNESS, listOf(image)),
        )
    }
}
