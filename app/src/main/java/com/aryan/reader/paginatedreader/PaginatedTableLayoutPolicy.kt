package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val StackedDramaTableMinRows = 4
private const val StackedDramaTableMinTextChars = 160
private const val StackedDramaTableMaxLeadChars = 40
private val StackedDramaCellTopGap = 24.dp

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
    return if (shouldStackRowsForNarrowPagination()) {
        rows.flatMap { row ->
            row.mapIndexed { cellIndex, cell ->
                listOf(cell.withStackedDramaCellSpacing(row, cellIndex))
            }
        }
    } else {
        rows
    }
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

internal fun TableCell.contentForStackedPaginationMeasurement(): List<ContentBlock> {
    return content.map { it.withStackedPaginationCellLayout() }
}

private fun TableCell.withStackedDramaCellSpacing(row: List<TableCell>, cellIndex: Int): TableCell {
    val needsTopGap = isLikelyDramaSpeakerCell() || isStageDirectionOnlyCell(row, cellIndex)
    if (!needsTopGap) return this
    val blockStyle = style.blockStyle
    return copy(
        style = style.copy(
            blockStyle = blockStyle.copy(
                padding = blockStyle.padding.copy(top = StackedDramaCellTopGap)
            )
        )
    )
}

private fun TableCell.isStageDirectionOnlyCell(row: List<TableCell>, cellIndex: Int): Boolean {
    if (cellIndex <= 0) return false
    if (content.sumOf { it.paginationTextCharCount() } == 0) return false
    return row.take(cellIndex).all { cell ->
        cell.content.sumOf { it.paginationTextCharCount() } == 0
    }
}

internal fun TableCell.withoutStackedDramaCellTopGap(): TableCell {
    val blockStyle = style.blockStyle
    if (blockStyle.padding.top == 0.dp) return this
    return copy(
        style = style.copy(
            blockStyle = blockStyle.copy(
                padding = blockStyle.padding.copy(top = 0.dp)
            )
        )
    )
}

internal fun FlexContainerBlock.childrenForFlexPaginationMeasurement(): List<ContentBlock> {
    return children.map { it.withoutFlexPaginationChildMargin() }
}

private fun ContentBlock.withoutFlexPaginationChildMargin(): ContentBlock {
    val newStyle = style.withoutStackedTableCellChildMargins()
    return when (this) {
        is ParagraphBlock -> copy(style = newStyle)
        is HeaderBlock -> copy(style = newStyle)
        is ImageBlock -> copy(style = newStyle)
        is SpacerBlock -> copy(style = newStyle)
        is QuoteBlock -> copy(style = newStyle)
        is ListItemBlock -> copy(style = newStyle)
        is WrappingContentBlock -> copy(style = newStyle)
        is TableBlock -> copy(style = newStyle)
        is FlexContainerBlock -> copy(style = newStyle)
        is MathBlock -> copy(style = newStyle)
    }
}

internal fun AnnotatedString.withStackedPaginationTextStartAlignment(): AnnotatedString {
    if (paragraphStyles.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withStackedPaginationTextStartAlignment)
        this@withStackedPaginationTextStartAlignment.paragraphStyles.forEach { range ->
            addStyle(
                style = range.item.copy(textAlign = TextAlign.Left),
                start = range.start,
                end = range.end
            )
        }
    }
}

private fun BlockStyle.withoutStackedTableCellChildMargins(): BlockStyle {
    return copy(margin = BoxBorders())
}

private fun ContentBlock.withStackedPaginationCellLayout(): ContentBlock {
    return when (this) {
        is ParagraphBlock -> copy(
            content = content.withStackedPaginationTextStartAlignment(),
            textAlign = TextAlign.Left,
            style = style.withoutStackedTableCellChildMargins()
        )
        is HeaderBlock -> copy(
            content = content.withStackedPaginationTextStartAlignment(),
            textAlign = TextAlign.Left,
            style = style.withoutStackedTableCellChildMargins()
        )
        is QuoteBlock -> copy(
            content = content.withStackedPaginationTextStartAlignment(),
            textAlign = TextAlign.Left,
            style = style.withoutStackedTableCellChildMargins()
        )
        is ListItemBlock -> copy(
            content = content.withStackedPaginationTextStartAlignment(),
            style = style.withoutStackedTableCellChildMargins()
        )
        is WrappingContentBlock -> copy(
            style = style.withoutStackedTableCellChildMargins(),
            paragraphsToWrap = paragraphsToWrap.map { paragraph ->
                paragraph.copy(
                    content = paragraph.content.withStackedPaginationTextStartAlignment(),
                    textAlign = TextAlign.Left,
                    style = paragraph.style.withoutStackedTableCellChildMargins()
                )
            }
        )
        else -> this
    }
}