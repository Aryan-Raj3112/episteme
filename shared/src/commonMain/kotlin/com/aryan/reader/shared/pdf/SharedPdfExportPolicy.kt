package com.aryan.reader.shared.pdf

enum class SharedPdfExportMode {
    ORIGINAL,
    ANNOTATED,
    UNSUPPORTED_VIRTUAL_PAGES,
    UNSUPPORTED_TEXT_CONTENT,
}

/**
 * Decides whether Save Copy may use the source bytes or must render reader-owned content.
 * Unsupported content is never silently discarded from an exported copy.
 */
fun sharedPdfExportMode(state: SharedPdfReaderState): SharedPdfExportMode {
    if (state.blankPageInsertions.isNotEmpty()) return SharedPdfExportMode.UNSUPPORTED_VIRTUAL_PAGES

    val richDocument = SharedPdfRichTextSerializer.decode(state.richTextDocumentJson)
    val hasTextContent = richDocument.text.any { !it.isWhitespace() } ||
        state.annotations.any { it.kind == PdfAnnotationKind.TEXT || it.tool == PdfInkTool.TEXT }
    if (hasTextContent) return SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT

    return if (SharedPdfAnnotationExportMapper.build(state.annotations).hasPdfAnnotations) {
        SharedPdfExportMode.ANNOTATED
    } else {
        SharedPdfExportMode.ORIGINAL
    }
}
