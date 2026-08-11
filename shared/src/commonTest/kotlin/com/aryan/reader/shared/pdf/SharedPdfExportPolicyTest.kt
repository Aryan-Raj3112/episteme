package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfExportPolicyTest {
    @Test
    fun originalIsUsedOnlyWhenThereIsNoExportableReaderContent() {
        assertEquals(SharedPdfExportMode.ORIGINAL, sharedPdfExportMode(SharedPdfReaderState()))
    }

    @Test
    fun inkAndHighlightsRequireAnAnnotatedExport() {
        val ink = SharedPdfAnnotation(
            id = "ink",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            points = listOf(PdfPagePoint(0.1f, 0.2f), PdfPagePoint(0.3f, 0.4f)),
            colorArgb = 0xFF112233.toInt(),
        )

        assertEquals(
            SharedPdfExportMode.ANNOTATED,
            sharedPdfExportMode(SharedPdfReaderState(annotations = listOf(ink))),
        )
    }

    @Test
    fun unsupportedContentCannotFallBackToTheUnmodifiedSource() {
        val text = SharedPdfAnnotation(
            id = "text",
            pageIndex = 0,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            text = "Keep this",
            colorArgb = 0xFF000000.toInt(),
        )
        assertEquals(
            SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT,
            sharedPdfExportMode(SharedPdfReaderState(annotations = listOf(text))),
        )
        assertEquals(
            SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT,
            sharedPdfExportMode(
                SharedPdfReaderState(
                    richTextDocumentJson = SharedPdfRichTextSerializer.encode(SharedPdfRichDocument("Keep this")),
                ),
            ),
        )
        assertEquals(
            SharedPdfExportMode.UNSUPPORTED_VIRTUAL_PAGES,
            sharedPdfExportMode(
                SharedPdfReaderState(blankPageInsertions = listOf(SharedPdfBlankPageInsertion(0))),
            ),
        )
        assertEquals(
            SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT,
            sharedPdfExportMode(
                SharedPdfReaderState(
                    annotations = listOf(
                        text.copy(
                            kind = PdfAnnotationKind.HIGHLIGHT,
                            tool = PdfInkTool.HIGHLIGHTER,
                            bounds = PdfPageBounds(0.1f, 0.1f, 0.2f, 0.2f),
                            comments = listOf(SharedPdfAnnotationComment("comment", contents = "Do not lose this")),
                        ),
                    ),
                ),
            ),
        )
    }
}
