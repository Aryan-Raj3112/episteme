package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfAnnotationCommentsTest {
    @Test
    fun `visible comments filter blanks and promote orphan replies`() {
        val comments = listOf(
            SharedPdfAnnotationComment(id = "root", contents = "Root"),
            SharedPdfAnnotationComment(id = "reply", parentId = "root", contents = "Reply"),
            SharedPdfAnnotationComment(id = "blank-parent", contents = ""),
            SharedPdfAnnotationComment(id = "orphan", parentId = "blank-parent", contents = "Orphan")
        )

        val visible = comments.visiblePdfAnnotationComments()

        assertEquals(listOf("root", "reply", "orphan"), visible.map { it.id })
        assertEquals("root", visible.single { it.id == "reply" }.parentId)
        assertEquals(null, visible.single { it.id == "orphan" }.parentId)
    }

    @Test
    fun `comment helpers preserve nested thread behavior`() {
        val comments = listOf(
            SharedPdfAnnotationComment(id = "undated", contents = "Undated"),
            SharedPdfAnnotationComment(id = "newer", contents = "Newer", createdAt = 30L),
            SharedPdfAnnotationComment(id = "older", contents = "Older", createdAt = 10L),
            SharedPdfAnnotationComment(id = "child", parentId = "older", contents = "Child"),
            SharedPdfAnnotationComment(id = "grandchild", parentId = "child", contents = "Grandchild"),
            SharedPdfAnnotationComment(id = "sibling", contents = "Sibling", createdAt = 20L)
        )

        assertEquals(
            listOf("older", "sibling", "newer", "undated"),
            comments.pdfCommentChildren(parentId = null).map { it.id }
        )
        assertEquals(
            listOf("undated", "newer", "older", "sibling"),
            comments.withoutPdfCommentThread("child").map { it.id }
        )
    }
}
