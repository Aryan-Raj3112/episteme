package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.HighlightStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfAnnotationExportMapperTest {

    @Test
    fun `mapper exports ink annotations and skips non-drawing tools`() {
        val ink = SharedPdfAnnotation(
            id = "ink-1",
            pageIndex = 2,
            kind = PdfAnnotationKind.INK,
            tool = PdfInkTool.PEN,
            points = listOf(PdfPagePoint(0.1f, 0.2f), PdfPagePoint(0.3f, 0.4f)),
            note = "Check curve",
            colorArgb = 0xFF336699.toInt(),
            strokeWidth = 0.0125f
        )
        val noneTool = ink.copy(id = "none", tool = PdfInkTool.NONE)
        val eraser = ink.copy(id = "eraser", tool = PdfInkTool.ERASER)
        val textTool = ink.copy(id = "text-tool", tool = PdfInkTool.TEXT)
        val tooShort = ink.copy(id = "short", points = listOf(PdfPagePoint(0.1f, 0.2f)))

        val payload = SharedPdfAnnotationExportMapper.build(listOf(ink, noneTool, eraser, textTool, tooShort))

        assertTrue(payload.hasPdfAnnotations)
        assertEquals(listOf("ink-1"), payload.inkAnnotations.map { it.id })
        assertEquals(PdfInkTool.PEN, payload.inkAnnotations.single().tool)
        assertEquals("Check curve", payload.inkAnnotations.single().contents)
    }

    @Test
    fun `mapper preserves highlight bounds order without adding fake contents`() {
        val highlight = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 1,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            boundsList = listOf(
                PdfPageBounds(0.1f, 0.2f, 0.4f, 0.25f),
                PdfPageBounds(0.5f, 0.3f, 0.8f, 0.35f)
            ),
            text = "Selected text",
            colorArgb = 0x8C64B5F6.toInt(),
            highlightStyle = HighlightStyle.WAVY_UNDERLINE
        )

        val payload = SharedPdfAnnotationExportMapper.build(listOf(highlight))

        assertEquals(listOf("highlight-1"), payload.highlightAnnotations.map { it.id })
        assertEquals(highlight.boundsList, payload.highlightAnnotations.single().boundsList)
        assertEquals(HighlightStyle.WAVY_UNDERLINE, payload.highlightAnnotations.single().style)
        assertEquals("", payload.highlightAnnotations.single().contents)
        assertEquals(
            "Actual note",
            SharedPdfAnnotationExportMapper.build(listOf(highlight.copy(note = " Actual note ")))
                .highlightAnnotations
                .single()
                .contents
        )
    }

    @Test
    fun `mapper exports text highlight comment threads with stable ids and parent order`() {
        val highlight = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 1,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            boundsList = listOf(PdfPageBounds(0.1f, 0.2f, 0.4f, 0.25f)),
            text = "Selected text",
            comments = listOf(
                SharedPdfAnnotationComment(
                    id = "reply-1",
                    parentId = "root-1",
                    author = "Bea",
                    contents = " Reply body ",
                    createdAt = 20L
                ),
                SharedPdfAnnotationComment(
                    id = "blank",
                    author = "Nope",
                    contents = " "
                ),
                SharedPdfAnnotationComment(
                    id = "root-1",
                    author = "Ada",
                    contents = "Root body",
                    createdAt = 10L,
                    modifiedAt = 15L
                )
            ),
            colorArgb = 0x8C64B5F6.toInt()
        )

        val comments = SharedPdfAnnotationExportMapper.build(listOf(highlight))
            .highlightAnnotations
            .single()
            .comments

        val comment = comments.single()
        assertEquals("highlight-1_comments", comment.id)
        assertEquals(null, comment.parentId)
        assertEquals("Ada", comment.author)
        assertEquals(
            "Ada:\nRoot body\n\n  Bea:\n  Reply body",
            comment.contents
        )
        assertEquals(10L, comment.createdAt)
        assertEquals(20L, comment.modifiedAt)
    }

    @Test
    fun `mapper folds multiple top level highlight comments into one export thread`() {
        val highlight = SharedPdfAnnotation(
            id = "highlight-1",
            pageIndex = 1,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            boundsList = listOf(PdfPageBounds(0.1f, 0.2f, 0.4f, 0.25f)),
            text = "Selected text",
            comments = listOf(
                SharedPdfAnnotationComment(id = "root-1", contents = "First"),
                SharedPdfAnnotationComment(id = "root-2", contents = "Second")
            ),
            colorArgb = 0x8C64B5F6.toInt()
        )

        val comments = SharedPdfAnnotationExportMapper.build(listOf(highlight))
            .highlightAnnotations
            .single()
            .comments

        val comment = comments.single()
        assertEquals("highlight-1_comments", comment.id)
        assertEquals(null, comment.parentId)
        assertEquals("Reader", comment.author)
        assertEquals("Reader:\nFirst\n\nReader:\nSecond", comment.contents)
    }

    @Test
    fun `mapper uses resolver bounds and skips malformed highlights`() {
        val missingBounds = SharedPdfAnnotation(
            id = "resolved",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            text = "Resolved",
            colorArgb = 0x8CFFEB3B.toInt(),
            rangeStartIndex = 2,
            rangeEndIndex = 9
        )
        val malformed = missingBounds.copy(
            id = "bad",
            bounds = PdfPageBounds(1.1f, 0.2f, 1.2f, 0.3f)
        )
        val malformedWithRange = missingBounds.copy(
            id = "fallback",
            bounds = PdfPageBounds(1.1f, 0.2f, 1.2f, 0.3f)
        )
        val textAnnotation = missingBounds.copy(
            id = "text",
            kind = PdfAnnotationKind.TEXT,
            bounds = PdfPageBounds(0.1f, 0.2f, 0.2f, 0.3f)
        )

        val payload = SharedPdfAnnotationExportMapper.build(
            listOf(missingBounds, malformed, malformedWithRange, textAnnotation)
        ) { annotation ->
            when (annotation.id) {
                "resolved" -> listOf(PdfPageBounds(0.2f, 0.4f, 0.6f, 0.45f))
                "fallback" -> listOf(PdfPageBounds(0.3f, 0.5f, 0.7f, 0.55f))
                else -> emptyList()
            }
        }

        assertEquals(listOf("resolved", "fallback"), payload.highlightAnnotations.map { it.id })
        assertEquals(
            listOf(PdfPageBounds(0.2f, 0.4f, 0.6f, 0.45f)),
            payload.highlightAnnotations.first().boundsList
        )
        assertEquals(
            listOf(PdfPageBounds(0.3f, 0.5f, 0.7f, 0.55f)),
            payload.highlightAnnotations.last().boundsList
        )
        assertFalse(SharedPdfAnnotationExportMapper.build(listOf(malformed, textAnnotation)).hasPdfAnnotations)
    }

    @Test
    fun `mapper normalizes reversed highlight bounds`() {
        val highlight = SharedPdfAnnotation(
            id = "highlight",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            bounds = PdfPageBounds(0.6f, 0.4f, 0.2f, 0.3f),
            text = "Selected",
            colorArgb = 0x8CFFEB3B.toInt()
        )

        val payload = SharedPdfAnnotationExportMapper.build(listOf(highlight))

        assertEquals(
            PdfPageBounds(0.2f, 0.3f, 0.6f, 0.4f),
            payload.highlightAnnotations.single().boundsList.single()
        )
    }

    @Test
    fun `ink appearance trims chisel highlighter endpoints only`() {
        val chisel = SharedPdfInkAnnotationExport(
            id = "highlight",
            pageIndex = 0,
            tool = PdfInkTool.HIGHLIGHTER,
            points = listOf(PdfPagePoint(0.1f, 0.2f), PdfPagePoint(0.9f, 0.2f)),
            colorArgb = 0x8CFFEB3B.toInt(),
            strokeWidth = 0.1f,
            contents = ""
        )
        val round = chisel.copy(tool = PdfInkTool.HIGHLIGHTER_ROUND)
        val trimmed = chisel.pdfInkAppearancePoints(pageWidth = 100f, pageHeight = 100f)
        val duplicatedEndpointTrimmed = chisel.copy(
            points = listOf(
                PdfPagePoint(0.1f, 0.2f),
                PdfPagePoint(0.1f, 0.2f),
                PdfPagePoint(0.9f, 0.2f),
                PdfPagePoint(0.9f, 0.2f)
            )
        ).pdfInkAppearancePoints(pageWidth = 100f, pageHeight = 100f)

        assertEquals(0.165f, trimmed.first().x, 0.0001f)
        assertEquals(0.2f, trimmed.first().y, 0.0001f)
        assertEquals(0.835f, trimmed.last().x, 0.0001f)
        assertEquals(0.2f, trimmed.last().y, 0.0001f)
        assertEquals(0.165f, duplicatedEndpointTrimmed[1].x, 0.0001f)
        assertEquals(0.835f, duplicatedEndpointTrimmed[2].x, 0.0001f)
        assertEquals(round.points, round.pdfInkAppearancePoints(pageWidth = 100f, pageHeight = 100f))
    }
}
