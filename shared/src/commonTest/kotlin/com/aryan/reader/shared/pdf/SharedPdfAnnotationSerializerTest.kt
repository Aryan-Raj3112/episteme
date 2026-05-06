package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfAnnotationSerializerTest {

    @Test
    fun `serializer round trips text highlight annotations`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight",
            pageIndex = 3,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = PdfPageBounds(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.24f),
            text = "Selected text",
            colorArgb = 0x8CFFEB3B.toInt(),
            createdAt = 42L
        )

        val decoded = SharedPdfAnnotationSerializer.decode(
            SharedPdfAnnotationSerializer.encode(listOf(annotation))
        )

        assertEquals(listOf(annotation), decoded)
    }

    @Test
    fun `embedded annotation threads link replies and nearby orphan comments`() {
        val root = embeddedAnnotation(
            id = "root",
            index = 0,
            contents = "Root comment",
            name = "root-name",
            bounds = PdfPageBounds(0.1f, 0.1f, 0.2f, 0.2f)
        )
        val reply = embeddedAnnotation(
            id = "reply",
            index = 1,
            contents = "Reply comment",
            name = "reply-name",
            inReplyTo = "root-name",
            bounds = PdfPageBounds(0.11f, 0.11f, 0.21f, 0.21f)
        )
        val nearbyOrphan = embeddedAnnotation(
            id = "nearby",
            index = 2,
            contents = "Nearby comment",
            name = "nearby-name",
            bounds = PdfPageBounds(0.12f, 0.12f, 0.22f, 0.22f)
        )
        val empty = embeddedAnnotation(
            id = "empty",
            index = 3,
            contents = "",
            name = "empty-name",
            bounds = PdfPageBounds(0.8f, 0.8f, 0.9f, 0.9f)
        )

        val grouped = SharedPdfEmbeddedAnnotationThreads.group(listOf(root, reply, nearbyOrphan, empty))

        assertEquals(listOf("root"), grouped.map { it.id })
        assertEquals(listOf("reply", "nearby"), grouped.single().replies.map { it.id })
    }

    private fun embeddedAnnotation(
        id: String,
        index: Int,
        contents: String,
        name: String,
        bounds: PdfPageBounds,
        inReplyTo: String = ""
    ): SharedPdfEmbeddedAnnotation {
        return SharedPdfEmbeddedAnnotation(
            id = id,
            pageIndex = 0,
            index = index,
            subtype = PdfiumAnnotationSubtype.TEXT,
            bounds = bounds,
            contents = contents,
            author = "Reader",
            name = name,
            inReplyTo = inReplyTo
        )
    }
}
