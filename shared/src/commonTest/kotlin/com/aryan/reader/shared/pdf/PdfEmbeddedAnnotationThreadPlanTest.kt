package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfEmbeddedAnnotationThreadPlanTest {
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

    private fun item(
        name: String? = null,
        inReplyTo: String? = null,
        contents: Boolean = true,
        left: Float,
        right: Float = left + 10f,
    ): PdfEmbeddedAnnotationThreadItem = PdfEmbeddedAnnotationThreadItem(
        name = name,
        inReplyTo = inReplyTo,
        bounds = PdfPageBounds(left, 0f, right, 20f),
        hasVisibleText = contents,
    )
}
