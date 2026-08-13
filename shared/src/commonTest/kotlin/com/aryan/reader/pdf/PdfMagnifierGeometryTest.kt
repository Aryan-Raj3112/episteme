package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfMagnifierGeometryTest {
    private val source = MagnifierContentSource(300, 600, 0f, 0f, 200f, 400f)

    @Test
    fun `sample and selection transforms preserve Android source scaling`() {
        val sample = requireNotNull(calculateMagnifierSampleGeometry(50f, 200f, source, 120f, 60f, 2f))
        assertEquals(MagnifierSampleGeometry(30, 278, 90, 45, 120f / 90f, 60f / 45f), sample)
        val mapped = mapContentBoundsToMagnifier(40f, 190f, 70f, 210f, source, sample)
        assertEquals(40f, mapped.left, 0.01f)
        assertEquals(100f, mapped.right, 0.01f)
        assertEquals(29.33f, mapped.center.y, 0.05f)
    }

    @Test
    fun `invalid geometry remains non renderable`() {
        assertEquals(null, calculateMagnifierSampleGeometry(0f, 0f, source.copy(sourceWidth = 0), 120f, 60f, 2f))
    }
}
