package com.aryan.reader.shared.pdf

data class SharedPdfAnnotationExportPayload(
    val inkAnnotations: List<SharedPdfInkAnnotationExport> = emptyList(),
    val highlightAnnotations: List<SharedPdfHighlightAnnotationExport> = emptyList()
) {
    val hasPdfAnnotations: Boolean
        get() = inkAnnotations.isNotEmpty() || highlightAnnotations.isNotEmpty()
}

data class SharedPdfInkAnnotationExport(
    val id: String,
    val pageIndex: Int,
    val tool: PdfInkTool,
    val points: List<PdfPagePoint>,
    val colorArgb: Int,
    val strokeWidth: Float,
    val contents: String
)

data class SharedPdfHighlightAnnotationExport(
    val id: String,
    val pageIndex: Int,
    val boundsList: List<PdfPageBounds>,
    val colorArgb: Int,
    val contents: String
)

object SharedPdfAnnotationExportMapper {
    fun build(
        annotations: List<SharedPdfAnnotation>,
        resolveHighlightBounds: (SharedPdfAnnotation) -> List<PdfPageBounds> = { emptyList() }
    ): SharedPdfAnnotationExportPayload {
        return SharedPdfAnnotationExportPayload(
            inkAnnotations = annotations.mapNotNull { it.toInkExportOrNull() },
            highlightAnnotations = annotations.mapNotNull { it.toHighlightExportOrNull(resolveHighlightBounds) }
        )
    }

    private fun SharedPdfAnnotation.toInkExportOrNull(): SharedPdfInkAnnotationExport? {
        if (kind != PdfAnnotationKind.INK) return null
        if (tool == PdfInkTool.NONE ||
            tool == PdfInkTool.ERASER ||
            tool == PdfInkTool.TEXT ||
            points.size < 2
        ) return null

        return SharedPdfInkAnnotationExport(
            id = id,
            pageIndex = pageIndex,
            tool = tool,
            points = points,
            colorArgb = colorArgb,
            strokeWidth = strokeWidth,
            contents = note?.trim()?.takeIf { it.isNotBlank() } ?: "Ink"
        )
    }

    private fun SharedPdfAnnotation.toHighlightExportOrNull(
        resolveHighlightBounds: (SharedPdfAnnotation) -> List<PdfPageBounds>
    ): SharedPdfHighlightAnnotationExport? {
        if (kind != PdfAnnotationKind.HIGHLIGHT) return null

        val storedBounds = boundsList.ifEmpty { listOfNotNull(bounds) }
            .mapNotNull { it.normalizedForExportOrNull() }
        val exportBounds = storedBounds.ifEmpty {
            resolveHighlightBounds(this).mapNotNull { it.normalizedForExportOrNull() }
        }
        if (exportBounds.isEmpty()) return null

        return SharedPdfHighlightAnnotationExport(
            id = id,
            pageIndex = pageIndex,
            boundsList = exportBounds,
            colorArgb = colorArgb,
            contents = note?.trim()?.takeIf { it.isNotBlank() }
                ?: text.trim().takeIf { it.isNotBlank() }
                ?: "Highlight"
        )
    }

    private fun PdfPageBounds.normalizedForExportOrNull(): PdfPageBounds? {
        val normalized = PdfPageBounds(
            left = minOf(left, right),
            top = minOf(top, bottom),
            right = maxOf(left, right),
            bottom = maxOf(top, bottom)
        )
        return normalized.takeIf {
            it.left in 0f..1f &&
                it.top in 0f..1f &&
                it.right in 0f..1f &&
                it.bottom in 0f..1f &&
                it.right > it.left &&
                it.bottom > it.top
        }
    }
}
