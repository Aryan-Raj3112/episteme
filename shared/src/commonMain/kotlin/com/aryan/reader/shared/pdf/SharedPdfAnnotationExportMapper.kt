package com.aryan.reader.shared.pdf

import kotlin.math.sqrt

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
    val contents: String,
    val comments: List<SharedPdfHighlightCommentExport> = emptyList()
)

data class SharedPdfHighlightCommentExport(
    val id: String,
    val parentId: String?,
    val author: String,
    val contents: String,
    val createdAt: Long,
    val modifiedAt: Long
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
            contents = note?.trim().orEmpty()
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
            contents = note?.trim().orEmpty(),
            comments = comments.toHighlightCommentExports(highlightId = id)
        )
    }

    private fun List<SharedPdfAnnotationComment>.toHighlightCommentExports(
        highlightId: String
    ): List<SharedPdfHighlightCommentExport> {
        val sourceItems = mapIndexedNotNull { index, comment ->
            val contents = comment.contents.trim()
            if (contents.isBlank()) return@mapIndexedNotNull null
            val sourceId = comment.id.takeIf { it.isNotBlank() }
            val exportId = sourceId ?: "${highlightId}_comment_$index"
            IndexedExportComment(
                sourceId = sourceId,
                sourceParentId = comment.parentId?.takeIf { it.isNotBlank() },
                export = SharedPdfHighlightCommentExport(
                    id = exportId,
                    parentId = null,
                    author = comment.author.trim(),
                    contents = contents,
                    createdAt = comment.createdAt,
                    modifiedAt = comment.modifiedAt.takeIf { it > 0L } ?: comment.createdAt
                )
            )
        }
        if (sourceItems.isEmpty()) return emptyList()

        val stableIds = mutableSetOf<String>()
        val sourceIdToExportId = mutableMapOf<String, String>()
        val uniqueItems = sourceItems.mapIndexed { index, item ->
            val uniqueId = item.export.id.uniqueCommentId(stableIds, index)
            item.sourceId?.let { sourceIdToExportId.putIfAbsent(it, uniqueId) }
            item.copy(export = item.export.copy(id = uniqueId))
        }
        val parentAwareItems = uniqueItems.map { item ->
            val parentId = item.sourceParentId?.let(sourceIdToExportId::get)
            item.copy(export = item.export.copy(parentId = parentId))
        }

        val byParent = parentAwareItems.groupBy { it.export.parentId }
        val result = mutableListOf<SharedPdfHighlightCommentExport>()
        val emittedIds = mutableSetOf<String>()

        fun appendThread(parentId: String?, visitedIds: Set<String>) {
            byParent[parentId].orEmpty().forEach { item ->
                val export = item.export
                if (export.id in emittedIds || export.id in visitedIds) return@forEach
                emittedIds += export.id
                result += export
                appendThread(export.id, visitedIds + export.id)
            }
        }

        appendThread(parentId = null, visitedIds = emptySet())
        parentAwareItems.forEach { item ->
            if (item.export.id !in emittedIds) {
                val root = item.export.copy(parentId = null)
                emittedIds += root.id
                result += root
                appendThread(root.id, setOf(root.id))
            }
        }
        return result.toSingleVisiblePdfCommentThread(highlightId)
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

private data class IndexedExportComment(
    val sourceId: String?,
    val sourceParentId: String?,
    val export: SharedPdfHighlightCommentExport
)

private fun String.uniqueCommentId(usedIds: MutableSet<String>, index: Int): String {
    val base = ifBlank { "comment_$index" }
    var candidate = base
    var suffix = 2
    while (!usedIds.add(candidate)) {
        candidate = "${base}_$suffix"
        suffix += 1
    }
    return candidate
}

private fun List<SharedPdfHighlightCommentExport>.toSingleVisiblePdfCommentThread(
    highlightId: String
): List<SharedPdfHighlightCommentExport> {
    if (isEmpty()) return emptyList()

    val threadContents = formatAsPdfCommentThread()
    if (threadContents.isBlank()) return emptyList()

    val createdAt = mapNotNull { it.createdAt.takeIf { timestamp -> timestamp > 0L } }
        .minOrNull()
        ?: 0L
    val modifiedAt = mapNotNull { comment ->
        (comment.modifiedAt.takeIf { it > 0L } ?: comment.createdAt).takeIf { it > 0L }
    }.maxOrNull() ?: createdAt
    val root = firstOrNull { it.parentId == null } ?: first()

    return listOf(
        SharedPdfHighlightCommentExport(
            id = "${highlightId}_comments",
            parentId = null,
            author = root.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR },
            contents = threadContents,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    )
}

private fun List<SharedPdfHighlightCommentExport>.formatAsPdfCommentThread(): String {
    val commentsByParent = groupBy { it.parentId }
    val ids = map { it.id }.toSet()
    val roots = filter { it.parentId == null || it.parentId !in ids }
    val visitedIds = mutableSetOf<String>()
    val lines = mutableListOf<String>()

    fun appendComment(comment: SharedPdfHighlightCommentExport, depth: Int) {
        if (!visitedIds.add(comment.id)) return

        if (lines.isNotEmpty()) lines += ""
        val indent = "  ".repeat(depth)
        val author = comment.author.ifBlank { DEFAULT_SHARED_PDF_COMMENT_AUTHOR }
        lines += "$indent$author:"
        comment.contents.lines().forEach { line ->
            lines += "$indent$line"
        }
        commentsByParent[comment.id].orEmpty().forEach { child ->
            appendComment(child, depth + 1)
        }
    }

    roots.forEach { appendComment(it, 0) }
    forEach { comment ->
        if (comment.id !in visitedIds) appendComment(comment, 0)
    }

    return lines.joinToString("\n").trim()
}

fun SharedPdfInkAnnotationExport.pdfInkAppearancePoints(pageWidth: Float, pageHeight: Float): List<PdfPagePoint> {
    if (tool != PdfInkTool.HIGHLIGHTER || points.size < 2 || pageWidth <= 0f || pageHeight <= 0f) return points

    // PDF ink annotations are usually rendered with rounded caps; trim chisel highlighter endpoints to match our butt-cap UI.
    val trimPdfUnits = (strokeWidth * pageWidth) * 0.65f
    if (trimPdfUnits <= 0f) return points

    val firstTargetIndex = points.firstDistinctIndexAfter(index = 0, pageWidth = pageWidth, pageHeight = pageHeight)
        ?: return points
    val lastIndex = points.lastIndex
    val lastTargetIndex = points.lastDistinctIndexBefore(index = lastIndex, pageWidth = pageWidth, pageHeight = pageHeight)
        ?: return points
    val adjusted = points.toMutableList()
    val adjustedStart = adjusted[0].movedToward(points[firstTargetIndex], trimPdfUnits, pageWidth, pageHeight)
    for (pointIndex in 0 until firstTargetIndex) {
        adjusted[pointIndex] = adjustedStart
    }
    val adjustedEnd = adjusted[lastIndex].movedToward(points[lastTargetIndex], trimPdfUnits, pageWidth, pageHeight)
    for (pointIndex in lastTargetIndex + 1..lastIndex) {
        adjusted[pointIndex] = adjustedEnd
    }
    return adjusted
}

private fun List<PdfPagePoint>.firstDistinctIndexAfter(
    index: Int,
    pageWidth: Float,
    pageHeight: Float
): Int? {
    val source = getOrNull(index) ?: return null
    for (targetIndex in index + 1..lastIndex) {
        val target = this[targetIndex]
        if (source.pdfDistanceTo(target, pageWidth, pageHeight) > 0f) return targetIndex
    }
    return null
}

private fun List<PdfPagePoint>.lastDistinctIndexBefore(
    index: Int,
    pageWidth: Float,
    pageHeight: Float
): Int? {
    val source = getOrNull(index) ?: return null
    for (targetIndex in index - 1 downTo 0) {
        val target = this[targetIndex]
        if (source.pdfDistanceTo(target, pageWidth, pageHeight) > 0f) return targetIndex
    }
    return null
}

private fun PdfPagePoint.movedToward(
    target: PdfPagePoint,
    distancePdfUnits: Float,
    pageWidth: Float,
    pageHeight: Float
): PdfPagePoint {
    val dx = (target.x - x) * pageWidth
    val dy = (target.y - y) * pageHeight
    val length = pdfDistanceTo(target, pageWidth, pageHeight)
    if (length <= 0f) return this

    val trim = minOf(distancePdfUnits, length * 0.49f)
    return copy(
        x = x + (dx / length) * (trim / pageWidth),
        y = y + (dy / length) * (trim / pageHeight)
    )
}

private fun PdfPagePoint.pdfDistanceTo(target: PdfPagePoint, pageWidth: Float, pageHeight: Float): Float {
    val dx = (target.x - x) * pageWidth
    val dy = (target.y - y) * pageHeight
    return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}
