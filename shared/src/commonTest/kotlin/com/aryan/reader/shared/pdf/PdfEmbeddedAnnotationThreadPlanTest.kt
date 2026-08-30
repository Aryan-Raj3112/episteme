package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfEmbeddedAnnotationThreadPlanTest {
    @Test
    fun `pdf coordinates normalize into top-left shared coordinates`() {
        assertEquals(
            PdfPageBounds(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.6f),
            normalizedPdfPageBounds(
                left = 10f,
                bottom = 40f,
                right = 30f,
                top = 80f,
                pageWidth = 100f,
                pageHeight = 100f,
            ),
        )
        assertNull(normalizedPdfPageBounds(10f, 20f, 10f, 30f, 100f, 100f))
        assertNull(normalizedPdfPageBounds(10f, 20f, 30f, 40f, 0f, 100f))
    }

    @Test
    fun `named replies attach to the last annotation with the matching name`() {
        val plan = buildPdfEmbeddedAnnotationThreadPlan(
            annotations = listOf(
                item(name = "duplicate", left = 0f),
                item(name = "duplicate", left = 100f),
                item(inReplyTo = "duplicate", contents = true, left = 200f),
            ),
            geometryTolerance = 10f,
        )

        assertEquals(listOf(PdfEmbeddedAnnotationReplyEdge(1, 2)), plan.replyEdges)
        assertEquals(listOf(0, 1), plan.displayGroups.map { it.rootIndex })
    }

    @Test
    fun `geometric grouping compares each orphan with the first root using Android tolerance`() {
        val plan = buildPdfEmbeddedAnnotationThreadPlan(
            annotations = listOf(
                item(contents = false, left = 0f, right = 20f),
                item(contents = true, left = 25f, right = 40f),
                item(contents = true, left = 44f, right = 60f),
                item(contents = false, left = 200f, right = 220f),
            ),
            geometryTolerance = 10f,
        )

        assertEquals(
            listOf(
                PdfEmbeddedAnnotationDisplayGroup(rootIndex = 0, geometricReplyIndices = listOf(1)),
                PdfEmbeddedAnnotationDisplayGroup(rootIndex = 2, geometricReplyIndices = emptyList()),
            ),
            plan.displayGroups,
        )
    }

    @Test
    fun `visibility matches Android direct reply filtering`() {
        val plan = buildPdfEmbeddedAnnotationThreadPlan(
            annotations = listOf(
                item(name = "root", contents = false, left = 0f),
                item(name = "child", inReplyTo = "root", contents = false, left = 100f),
                item(inReplyTo = "child", contents = true, left = 200f),
            ),
            geometryTolerance = 10f,
        )

        assertEquals(emptyList(), plan.displayGroups)
    }

    @Test
    fun `popup attachments never root a display group even when listed first`() {
        val plan = buildPdfEmbeddedAnnotationThreadPlan(
            annotations = listOf(
                item(contents = true, left = 0f, isPopup = true),
                item(contents = true, left = 0f),
            ),
            geometryTolerance = 10f,
        )

        assertEquals(
            listOf(PdfEmbeddedAnnotationDisplayGroup(rootIndex = 1, geometricReplyIndices = emptyList())),
            plan.displayGroups,
        )
    }

    @Test
    fun `popup attachments overlapping a real annotation are dropped from display`() {
        val plan = buildPdfEmbeddedAnnotationThreadPlan(
            annotations = listOf(
                item(contents = true, left = 0f),
                item(contents = true, left = 5f, isPopup = true),
            ),
            geometryTolerance = 10f,
        )

        assertEquals(
            listOf(PdfEmbeddedAnnotationDisplayGroup(rootIndex = 0, geometricReplyIndices = emptyList())),
            plan.displayGroups,
        )
    }

    @Test
    fun `standalone popup attachments with no parent are dropped`() {
        val plan = buildPdfEmbeddedAnnotationThreadPlan(
            annotations = listOf(item(contents = true, left = 200f, isPopup = true)),
            geometryTolerance = 10f,
        )

        assertEquals(emptyList(), plan.displayGroups)
    }

    private fun item(
        name: String? = null,
        inReplyTo: String? = null,
        contents: Boolean = true,
        left: Float,
        right: Float = left + 10f,
        isPopup: Boolean = false,
    ): PdfEmbeddedAnnotationThreadItem = PdfEmbeddedAnnotationThreadItem(
        name = name,
        inReplyTo = inReplyTo,
        bounds = PdfPageBounds(left, 0f, right, 20f),
        hasVisibleText = contents,
        isPopupAttachment = isPopup,
    )
}
