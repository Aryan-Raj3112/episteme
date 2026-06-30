package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorMeasurementContractTest {
    @Test
    fun measuredTextHeightForPagination_keepsLayoutHeightWhenItContainsLastLineBottom() {
        val measuredHeight = measuredTextHeightForPagination(
            layoutHeightPx = 120,
            lastLineBottomPx = 119.2f
        )

        assertEquals(120, measuredHeight)
    }

    @Test
    fun measuredTextHeightForPagination_usesCeiledLastLineBottomWhenItExceedsLayoutHeight() {
        val measuredHeight = measuredTextHeightForPagination(
            layoutHeightPx = 120,
            lastLineBottomPx = 132.1f
        )

        assertEquals(133, measuredHeight)
    }

    @Test
    fun collapsedVerticalMarginPxForPagination_clampsNegativeFirstMarginToRenderedZero() {
        val margin = collapsedVerticalMarginPxForPagination(
            previousBottomMarginPx = null,
            currentTopMarginPx = -48f
        )

        assertEquals(0, margin)
    }

    @Test
    fun collapsedVerticalMarginPxForPagination_clampsNegativeCollapsedMarginsToRenderedZero() {
        val margin = collapsedVerticalMarginPxForPagination(
            previousBottomMarginPx = -12f,
            currentTopMarginPx = -48f
        )

        assertEquals(0, margin)
    }

    @Test
    fun collapsedVerticalMarginPxForPagination_preservesPositiveCollapsedMargin() {
        val margin = collapsedVerticalMarginPxForPagination(
            previousBottomMarginPx = 14.4f,
            currentTopMarginPx = 20.2f
        )

        assertEquals(20, margin)
    }

    @Test
    fun availableBlockWidthPxForPagination_subtractsRenderedHorizontalMargins() {
        val width = availableBlockWidthPxForPagination(
            containerWidthPx = 996,
            marginLeftPx = 50f,
            marginRightPx = 50f,
            isCenterAligned = false
        )

        assertEquals(896f, width, 0.001f)
    }

    @Test
    fun availableBlockWidthPxForPagination_keepsFullWidthForCenteredBlocks() {
        val width = availableBlockWidthPxForPagination(
            containerWidthPx = 996,
            marginLeftPx = 50f,
            marginRightPx = 50f,
            isCenterAligned = true
        )

        assertEquals(996f, width, 0.001f)
    }

    @Test
    fun dramaStyleTablesStackRowsForNarrowPagination() {
        val table = TableBlock(
            rows = listOf(
                dialogueRow("Bernardo", "Who's there?"),
                dialogueRow("Francisco", "Nay, answer me. Stand and unfold yourself."),
                dialogueRow("Bernardo", "Long live the king."),
                dialogueRow("Francisco", "Bernardo? You come most carefully upon your hour."),
                dialogueRow("Marcellus", "And liegemen to the Dane. This row gives the sample enough body text to identify drama dialogue.")
            ),
            blockIndex = 1
        )

        assertTrue(table.shouldStackRowsForNarrowPagination())
    }

    @Test
    fun splitDramaTableFragmentsStayStackedForNarrowPagination() {
        val table = TableBlock(
            rows = listOf(
                listOf(speakerCell("Bernardo")),
                listOf(tableCell("Who's there? This is the continuation cell after a page split."))
            ),
            blockIndex = 3
        )

        assertTrue(table.shouldStackRowsForNarrowPagination())
        assertEquals(2, table.rowsForNarrowPaginationLayout().size)
    }

    @Test
    fun dataTablesDoNotStackRowsForNarrowPagination() {
        val table = TableBlock(
            rows = listOf(
                listOf(tableCell("Name"), tableCell("Role"), tableCell("Count")),
                listOf(tableCell("Bernardo"), tableCell("Guard"), tableCell("1")),
                listOf(tableCell("Francisco"), tableCell("Guard"), tableCell("2")),
                listOf(tableCell("Marcellus"), tableCell("Officer"), tableCell("3"))
            ),
            blockIndex = 3
        )

        assertFalse(table.shouldStackRowsForNarrowPagination())
    }

    private fun dialogueRow(speaker: String, dialogue: String): List<TableCell> {
        return listOf(speakerCell(speaker), tableCell(dialogue))
    }

    private fun speakerCell(text: String): TableCell {
        return tableCell(
            text = text,
            style = CssStyle(
                paragraphStyle = ParagraphStyle(textAlign = TextAlign.End),
                hyphens = "none"
            )
        )
    }

    private fun tableCell(text: String, style: CssStyle = CssStyle()): TableCell {
        return TableCell(
            content = listOf(
                ParagraphBlock(
                    content = AnnotatedString(text),
                    blockIndex = text.hashCode()
                )
            ),
            style = style
        )
    }
}
