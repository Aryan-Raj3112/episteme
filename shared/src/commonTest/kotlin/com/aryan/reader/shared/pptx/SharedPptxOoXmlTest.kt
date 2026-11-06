package com.aryan.reader.shared.pptx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Single-source contract for the OOXML helpers shared by the JVM (Android/desktop)
 * and iOS PPTX parsers. Android is the benchmark: these expectations pin the
 * behavior both parsers must produce.
 */
class SharedPptxOoXmlTest {

    @Test
    fun `truthy flags accept powerpoint spellings`() {
        assertTrue(SharedPptxOoXml.isTruthyFlag("1"))
        assertTrue(SharedPptxOoXml.isTruthyFlag("true"))
        assertTrue(SharedPptxOoXml.isTruthyFlag("TRUE"))
        assertTrue(SharedPptxOoXml.isTruthyFlag("on"))
        assertFalse(SharedPptxOoXml.isTruthyFlag("0"))
        assertFalse(SharedPptxOoXml.isTruthyFlag("false"))
        assertFalse(SharedPptxOoXml.isTruthyFlag(null))
        assertFalse(SharedPptxOoXml.isTruthyFlag(""))
    }

    @Test
    fun `emu conversions round like android`() {
        assertEquals(1f, SharedPptxOoXml.emuToPoint(12_700f))
        // 6350 EMU = 0.5pt rounds up to 1pt (JVM roundToInt, not truncation).
        assertEquals(1, SharedPptxOoXml.emuToPointInt(6_350f))
        assertEquals(720, SharedPptxOoXml.emuToPointInt(9_144_000f))
    }

    @Test
    fun `placeholder aliases map to style families`() {
        assertEquals("title", SharedPptxOoXml.placeholderFamily("ctrTitle"))
        assertEquals("subtitle", SharedPptxOoXml.placeholderFamily("subTtl"))
        assertEquals("body", SharedPptxOoXml.placeholderFamily("body"))
    }

    @Test
    fun `image content types cover svg`() {
        assertEquals("image/jpeg", SharedPptxOoXml.imageContentTypeForTarget("ppt/media/image1.jpg"))
        assertEquals("image/png", SharedPptxOoXml.imageContentTypeForTarget("ppt/media/image2.PNG"))
        assertEquals("image/svg+xml", SharedPptxOoXml.imageContentTypeForTarget("ppt/media/vector1.svg"))
        assertEquals("application/octet-stream", SharedPptxOoXml.imageContentTypeForTarget("ppt/media/blob.bin"))
    }

    @Test
    fun `relationship targets resolve like android`() {
        assertEquals(
            "ppt/slides/slide1.xml",
            SharedPptxOoXml.resolveRelationshipTarget(
                "ppt/presentation.xml",
                "slides/slide1.xml",
                null
            )
        )
        assertEquals(
            "ppt/media/image1.png",
            SharedPptxOoXml.resolveRelationshipTarget(
                "ppt/slides/slide1.xml",
                "../media/image1.png",
                null
            )
        )
        assertEquals(
            "https://example.com/a.png",
            SharedPptxOoXml.resolveRelationshipTarget(
                "ppt/slides/slide1.xml",
                "https://example.com/a.png",
                "External"
            )
        )
        assertEquals(
            "ppt/slideLayouts/slideLayout1.xml",
            SharedPptxOoXml.relationshipsPathFor("ppt/slides/slide1.xml").let {
                // Sanity: rels path helper feeds back into resolution.
                assertEquals("ppt/slides/_rels/slide1.xml.rels", it)
                "ppt/slideLayouts/slideLayout1.xml"
            }
        )
    }

    @Test
    fun `preset colors are a superset of both parsers`() {
        assertEquals(SharedPptxColor.CYAN, SharedPptxOoXml.presetColor("aqua"))
        assertEquals(SharedPptxColor.MAGENTA, SharedPptxOoXml.presetColor("fuchsia"))
        assertEquals(SharedPptxColor.DKGRAY, SharedPptxOoXml.presetColor("dkgrey"))
        assertEquals(SharedPptxColor.rgb(255, 165, 0), SharedPptxOoXml.presetColor("orange"))
        assertEquals(SharedPptxColor.rgb(128, 0, 128), SharedPptxOoXml.presetColor("purple"))
        assertEquals(SharedPptxColor.rgb(165, 42, 42), SharedPptxOoXml.presetColor("brown"))
        assertNull(SharedPptxOoXml.presetColor("not-a-color"))
    }

    @Test
    fun `hex colors parse six digit values`() {
        assertEquals(SharedPptxColor.rgb(0x33, 0x66, 0xCC), SharedPptxOoXml.hexColorOrNull("3366CC"))
        assertNull(SharedPptxOoXml.hexColorOrNull("FFF"))
        assertNull(SharedPptxOoXml.hexColorOrNull("not-a-color"))
    }

    @Test
    fun `auto numbers format across schemes`() {
        assertEquals("3.", SharedPptxOoXml.formatAutoNumber(3, "arabicPeriod"))
        assertEquals("c)", SharedPptxOoXml.formatAutoNumber(3, "alphaLcParenR"))
        assertEquals("C)", SharedPptxOoXml.formatAutoNumber(3, "alphaUcParenR"))
        assertEquals("iv", SharedPptxOoXml.formatAutoNumber(4, "romanLcPeriod").trimEnd('.'))
        assertEquals("(IV)", SharedPptxOoXml.formatAutoNumber(4, "romanUcParenBoth"))
        assertEquals("AA", SharedPptxOoXml.formatAutoNumber(27, "alphaUcPeriod").trimEnd('.'))
    }

    @Test
    fun `wingdings bullets normalize like android`() {
        assertEquals("\u2022", SharedPptxOoXml.normalizeBulletGlyph("§", "Wingdings"))
        assertEquals("§", SharedPptxOoXml.normalizeBulletGlyph("§", "Calibri"))
        assertEquals("•", SharedPptxOoXml.normalizeBulletGlyph("•", "Symbol"))
    }

    @Test
    fun `slide paths sort numerically`() {
        val paths = listOf(
            "ppt/slides/slide10.xml",
            "ppt/slides/slide2.xml",
            "ppt/slides/slide1.xml"
        ).sortedWith(SharedPptxOoXml.slidePathComparator)
        assertEquals(
            listOf("ppt/slides/slide1.xml", "ppt/slides/slide2.xml", "ppt/slides/slide10.xml"),
            paths
        )
        assertTrue(SharedPptxOoXml.isSlidePart("ppt/slides/slide12.xml"))
        assertFalse(SharedPptxOoXml.isSlidePart("ppt/notesSlides/notesSlide1.xml"))
    }
}
