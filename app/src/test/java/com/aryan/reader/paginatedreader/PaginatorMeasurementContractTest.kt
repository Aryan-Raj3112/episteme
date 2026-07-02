package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    fun centeredTextSafetyPaddingCanBeDisabledForStackedTableCells() {
        val style = TextStyle(
            fontSize = 16.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
        val density = Density(2f)

        assertEquals(40, centeredTextSafetyPaddingPx(style, density))
        assertEquals(0, centeredTextSafetyPaddingPx(style, density, enabled = false))
    }

    @Test
    fun effectiveTopMarginPxForPagination_dropsTopMarginAtPageStart() {
        val margin = effectiveTopMarginPxForPagination(
            isPageStart = true,
            currentTopMarginPx = 96f
        )

        assertEquals(0f, margin, 0.001f)
    }

    @Test
    fun effectiveTopMarginPxForPagination_preservesTopMarginBetweenBlocks() {
        val margin = effectiveTopMarginPxForPagination(
            isPageStart = false,
            currentTopMarginPx = 96f
        )

        assertEquals(96f, margin, 0.001f)
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
    fun stackedDramaRowsPreserveMobileCellTopGaps() {
        val table = TableBlock(
            rows = listOf(
                listOf(tableCell(""), tableCell("Enter Francisco at his post.")),
                dialogueRow("Bernardo", "Who's there?"),
                dialogueRow("Francisco", "Nay, answer me. Stand and unfold yourself."),
                dialogueRow("Bernardo", "Long live the king."),
                dialogueRow("Francisco", "Bernardo? You come most carefully upon your hour."),
                dialogueRow("Marcellus", "This row gives the sample enough body text to identify drama dialogue.")
            ),
            blockIndex = 2
        )

        val rows = table.rowsForNarrowPaginationLayout()

        assertEquals(0.dp, rows[0].single().style.blockStyle.padding.top)
        assertEquals(24.dp, rows[1].single().style.blockStyle.padding.top)
        assertEquals(24.dp, rows[2].single().style.blockStyle.padding.top)
        assertEquals(0.dp, rows[3].single().style.blockStyle.padding.top)
        assertEquals(0.dp, rows[1].single().withoutStackedDramaCellTopGap().style.blockStyle.padding.top)
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

    @Test
    fun stackedTableMeasurementNormalizesCellTextAlignmentAndMarginsToRenderedCellLayout() {
        val centeredText = buildAnnotatedString {
            append("A centered stage direction should be measured as start aligned when stacked.")
            addStyle(ParagraphStyle(textAlign = TextAlign.Center), 0, length)
        }
        val cell = TableCell(
            content = listOf(
                ParagraphBlock(
                    content = centeredText,
                    textAlign = TextAlign.End,
                    style = BlockStyle(margin = BoxBorders(top = 42.dp, bottom = 42.dp)),
                    blockIndex = 7
                )
            )
        )

        val normalized = cell.contentForStackedPaginationMeasurement().filterIsInstance<ParagraphBlock>().single()

        assertEquals(TextAlign.Left, normalized.textAlign)
        assertTrue(normalized.content.paragraphStyles.any { it.item.textAlign == TextAlign.Left })
        assertEquals(0.dp, normalized.style.margin.top)
        assertEquals(0.dp, normalized.style.margin.bottom)
    }

    @Test
    fun flexPaginationMeasurementDropsChildMarginsToMatchPaginatedFlexRenderer() {
        val flex = FlexContainerBlock(
            children = listOf(
                ParagraphBlock(
                    content = AnnotatedString("One"),
                    style = BlockStyle(margin = BoxBorders(top = 42.dp, bottom = 21.dp)),
                    blockIndex = 11
                ),
                ParagraphBlock(
                    content = AnnotatedString("Two"),
                    style = BlockStyle(margin = BoxBorders(top = 84.dp, bottom = 42.dp)),
                    blockIndex = 12
                )
            ),
            blockIndex = 10
        )

        val normalized = flex.childrenForFlexPaginationMeasurement().map { it as ParagraphBlock }

        assertEquals(0.dp, normalized[0].style.margin.top)
        assertEquals(0.dp, normalized[0].style.margin.bottom)
        assertEquals(0.dp, normalized[1].style.margin.top)
        assertEquals(0.dp, normalized[1].style.margin.bottom)
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
