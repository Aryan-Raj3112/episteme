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

    @Test
    fun tocStyleTablesStackRowsForNarrowPagination() {
        val table = TableBlock(
            rows = listOf(
                listOf(tableCell("CHAPTER I.")),
                listOf(
                    tableCell("Paul's letter to his young friends, in which he prepares them for being lost in the jungle."),
                    tableCell("11")
                ),
                listOf(tableCell("CHAPTER II.")),
                listOf(
                    tableCell("A queer canoe on the Rembo and a deserted village with many strange happenings."),
                    tableCell("14")
                ),
                listOf(tableCell("CHAPTER III.")),
                listOf(
                    tableCell("Harpooning a manga, a great prize, and the description of the curious beast."),
                    tableCell("23")
                )
            ),
            blockIndex = 4
        )

        assertTrue(table.shouldStackTocRowsForNarrowPagination())
        assertTrue(table.shouldStackRowsForNarrowPagination())

        val stacked = table.rowsForNarrowPaginationLayout()
        assertEquals(9, stacked.size)
        // TOC stacking stays plain: page cells must not gain drama speaker gaps.
        stacked.forEach { row ->
            assertEquals(0.dp, row.single().style.blockStyle.padding.top)
        }
    }

    @Test
    fun illustrationsStyleTablesWithShortEntriesDoNotStackRowsForNarrowPagination() {
        // Real ILLUSTRATIONS shape (colspan PAGE header, short titles, Frontispiece ref):
        // entries fit beside their page number, so the table stays side-by-side instead
        // of stacking the page reference beneath the title.
        val table = TableBlock(
            rows = listOf(
                listOf(
                    TableCell(
                        content = listOf(
                            ParagraphBlock(
                                content = AnnotatedString("PAGE"),
                                blockIndex = 100
                            )
                        ),
                        colspan = 2
                    )
                ),
                listOf(tableCell("Shooting a Leopard"), tableCell("Frontispiece.")),
                listOf(tableCell("The Royal Canoe"), tableCell("15")),
                listOf(tableCell("The Manga"), tableCell("25")),
                listOf(tableCell("The Mpano"), tableCell("29")),
                listOf(tableCell("Felling Ebony-Trees"), tableCell("31")),
                listOf(tableCell("Bringing in the Wounded"), tableCell("43")),
                listOf(tableCell("Watching Birds and Monkeys"), tableCell("57"))
            ),
            blockIndex = 6
        )

        assertFalse(table.shouldStackTocRowsForNarrowPagination())
        assertFalse(table.shouldStackRowsForNarrowPagination())
        assertEquals(table.rows, table.rowsForNarrowPaginationLayout())
    }

    @Test
    fun splitTocTableFragmentsStackWithoutDramaGaps() {
        // Shape of a split head of a stacked TOC table: single-column rows (headings,
        // entries, page refs). Must keep the plain TOC layout — drama speaker gaps would
        // make the fragment remeasure taller than estimated and get it rejected.
        // Page cells are end-aligned like the real `align="right"` cells, which is what
        // trips the drama single-column fallback.
        val endAligned = CssStyle(paragraphStyle = ParagraphStyle(textAlign = TextAlign.End))
        val fragment = TableBlock(
            rows = listOf(
                listOf(tableCell("CHAPTER I.")),
                listOf(tableCell("Paul's letter to his young friends, in which he prepares them for being lost in the jungle.")),
                listOf(tableCell("11", endAligned)),
                listOf(tableCell("CHAPTER II.")),
                listOf(tableCell("A queer canoe on the Rembo and a deserted village with many strange happenings.")),
                listOf(tableCell("14", endAligned)),
                listOf(tableCell("CHAPTER III.")),
                listOf(tableCell("Harpooning a manga, a great prize, and the description of the curious beast.")),
                listOf(tableCell("23", endAligned)),
                listOf(tableCell("CHAPTER IV.")),
                listOf(tableCell("We go into the forest and hunt for ebony trees with the whole party.")),
                listOf(tableCell("28", endAligned)),
                listOf(tableCell("CHAPTER V."))
            ),
            blockIndex = 7
        )

        assertTrue(fragment.shouldStackRowsForNarrowPagination())

        val stacked = fragment.rowsForNarrowPaginationLayout()
        assertEquals(13, stacked.size)
        stacked.forEach { row ->
            assertEquals(0.dp, row.single().style.blockStyle.padding.top)
        }
    }

    @Test
    fun longTwoColumnTablesWithoutPageRefsDoNotStackAsToc() {
        val table = TableBlock(
            rows = List(8) {
                listOf(
                    tableCell("Left column entry number $it with a fair amount of descriptive text inside."),
                    tableCell("Right column entry number $it with a fair amount of descriptive text inside.")
                )
            },
            blockIndex = 5
        )

        assertFalse(table.shouldStackTocRowsForNarrowPagination())
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
