package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.style.TextAlign

private const val StackedDramaTableMinRows = 4
private const val StackedDramaTableMinTextChars = 160
private const val StackedDramaTableMaxLeadChars = 40

internal fun TableBlock.shouldStackRowsForNarrowPagination(): Boolean {
    if (rows.isEmpty()) return false
    if (rows.any { it.isEmpty() || it.size > 2 }) return false

    val textChars = rows.sumOf { row -> row.sumOf { cell -> cell.content.sumOf { it.paginationTextCharCount() } } }
    val twoCellRows = rows.count { it.size == 2 }
    val shortLeadRows = rows.count { row ->
        row.size == 2 && row.first().isLikelyDramaSpeakerCell() && row.last().content.sumOf { it.paginationTextCharCount() } > 0
    }

    if (twoCellRows > 0) {
        if (textChars < StackedDramaTableMinTextChars || twoCellRows < StackedDramaTableMinRows) return false
        return shortLeadRows * 10 >= twoCellRows * 6
    }

    return rows.size > 1 || rows.any { it.single().isLikelyDramaSpeakerCell() }
}

internal fun TableBlock.rowsForNarrowPaginationLayout(): List<List<TableCell>> {
    return if (shouldStackRowsForNarrowPagination()) rows.flatMap { row -> row.map { cell -> listOf(cell) } } else rows
}

internal fun TableCell.isLikelyDramaSpeakerCell(): Boolean {
    val leadChars = content.sumOf { it.paginationTextCharCount() }
    if (leadChars !in 1..StackedDramaTableMaxLeadChars) return false
    return style.paragraphStyle.textAlign in setOf(TextAlign.End, TextAlign.Right) || style.hyphens == "none"
}

internal fun ContentBlock.paginationTextCharCount(): Int {
    return when (this) {
        is TextContentBlock -> content.text.length
        is WrappingContentBlock -> paragraphsToWrap.sumOf { it.content.text.length }
        is TableBlock -> rows.flatten().sumOf { cell ->
            cell.content.sumOf { it.paginationTextCharCount() }
        }
        is FlexContainerBlock -> children.sumOf { it.paginationTextCharCount() }
        else -> 0
    }
}
