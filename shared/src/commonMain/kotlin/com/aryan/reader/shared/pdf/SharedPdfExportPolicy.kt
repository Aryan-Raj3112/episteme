package com.aryan.reader.shared.pdf

enum class SharedPdfExportMode {
    ORIGINAL,
    ANNOTATED,
    UNSUPPORTED_VIRTUAL_PAGES,
    UNSUPPORTED_TEXT_CONTENT,
}

data class SharedPdfExportSnapshot(
    val state: SharedPdfReaderState,
    val richTextPageLayouts: List<SharedPdfRichPageLayout> = emptyList(),
)

/**
 * Decides whether Save Copy may use the source bytes or must render reader-owned content.
 * Unsupported content is never silently discarded from an exported copy.
 */
fun sharedPdfExportMode(state: SharedPdfReaderState): SharedPdfExportMode {
    return sharedPdfExportMode(SharedPdfExportSnapshot(state))
}

fun sharedPdfExportMode(snapshot: SharedPdfExportSnapshot): SharedPdfExportMode {
    val state = snapshot.state
    val richDocument = SharedPdfRichTextSerializer.decode(state.richTextDocumentJson)
    val hasRichText = richDocument.text.any { !it.isWhitespace() }
    val hasRenderableRichText = snapshot.richTextPageLayouts.any { it.visibleText.any { char -> !char.isWhitespace() } }
    if (hasRichText && !hasRenderableRichText) return SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT

    val textAnnotations = state.annotations.filter { it.kind == PdfAnnotationKind.TEXT || it.tool == PdfInkTool.TEXT }
    val hasMalformedText = textAnnotations.any { it.text.isNotBlank() && it.bounds == null }
    if (hasMalformedText) return SharedPdfExportMode.UNSUPPORTED_TEXT_CONTENT

    return if (
        state.blankPageInsertions.isNotEmpty() ||
        SharedPdfAnnotationExportMapper.build(state.annotations).hasPdfAnnotations ||
        textAnnotations.any { it.text.isNotBlank() } || hasRenderableRichText
    ) {
        SharedPdfExportMode.ANNOTATED
    } else {
        SharedPdfExportMode.ORIGINAL
    }
}

/**
 * Android benchmark (`PdfViewerScreen.shareOriginalPdf` / `launchOriginalSaveCopy`):
 * an original export is a raw source-byte copy. Strip reader-owned content so
 * [sharedPdfExportMode] resolves to [SharedPdfExportMode.ORIGINAL] (and unsupported
 * rich-text states never block a pure original export when the format dialog was skipped).
 */
fun sharedPdfOriginalExportSnapshot(state: SharedPdfReaderState): SharedPdfExportSnapshot =
    SharedPdfExportSnapshot(
        state = state.copy(
            annotations = emptyList(),
            blankPageInsertions = emptyList(),
            richTextDocumentJson = "",
        ),
    )
