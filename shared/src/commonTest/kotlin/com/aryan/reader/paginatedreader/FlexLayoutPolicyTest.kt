package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Flex box policy for the native engines: CSS initial values (`row`), `gap`,
 * `align-self`, and the space distribution `justify-content` asks for.
 */
class FlexLayoutPolicyTest {

    @Test
    fun `flex without a direction is a row`() {
        val implicit = parseStyle("display: flex")
        assertTrue(implicit.isFlexRow)
        assertEquals("row", implicit.resolvedFlexDirection)

        assertFalse(parseStyle("display: flex; flex-direction: column").isFlexRow)
        assertTrue(parseStyle("display: flex; flex-direction: ROW").isFlexRow)
        assertTrue(parseStyle("display: flex; flex-direction: row-reverse").isFlexRow)
        assertFalse(parseStyle("display: flex; flex-direction: column-reverse").isFlexRow)
    }

    @Test
    fun `only a flex container lays its children out in a row`() {
        // `isFlexRow` decides between a Row and a Column in every native engine, and
        // `flex-direction` defaults to `row`, so a plain block box (a window, a card, a
        // list) must not qualify. Treating it as a row squeezed its paragraphs into
        // one-character columns and measured the box as tall as its tallest child.
        assertFalse(parseStyle("display: block").isFlexRow)
        assertFalse(parseStyle("margin: 0").isFlexRow)
        assertFalse(parseStyle("display: block; flex-direction: row").isFlexRow)
        assertFalse(parseStyle("display: inline").isFlexRow)
        assertTrue(parseStyle("display: flex").isFlexContainer)
        assertTrue(parseStyle("display: flex; flex-direction: row").isFlexRow)
        assertTrue(parseStyle("display: inline-flex").isFlexRow)
        // The chant parser marks its flow boxes in `display` instead of `flex`.
        assertTrue(parseStyle("display: reader-chant-nonbreaking; flex-direction: row").isFlexRow)
        assertFalse(parseStyle("display: reader-chant-unit; flex-direction: column").isFlexRow)
    }

    @Test
    fun `gap shorthand fills both axes and per-axis rules override it`() {
        val both = parseStyle("display: flex; flex-direction: column; gap: 6px")
        assertEquals(6f, both.gap.value)
        assertEquals(6f, both.flexMainAxisGap.value)

        val split = parseStyle("display: flex; flex-direction: column; gap: 4px 8px")
        assertEquals(4f, split.flexMainAxisGap.value)
        val splitRow = parseStyle("display: flex; flex-direction: row; gap: 4px 8px")
        assertEquals(8f, splitRow.flexMainAxisGap.value)

        val overridden = parseStyle("display: flex; flex-direction: column; gap: 6px; row-gap: 3px")
        assertEquals(3f, overridden.flexMainAxisGap.value)
        val rowOverride = parseStyle("display: flex; flex-direction: row; gap: 6px; column-gap: 2px")
        assertEquals(2f, rowOverride.flexMainAxisGap.value)
    }

    @Test
    fun `gap percentage resolves against the container width`() {
        val style = parseStyle("display: flex; gap: 10%")
        // Constraints below are 1000px wide, so 10% is 100px at density 1.
        assertEquals(100f, style.flexMainAxisGap.value)
    }

    @Test
    fun `align self overrides the container alignment only when set`() {
        val selfAligned = parseStyle("display: flex; flex-direction: column; align-self: flex-end")
        assertTrue(selfAligned.hasSelfAlignment())
        assertEquals("flex-end", selfAligned.alignSelf)

        assertFalse(parseStyle("display: flex; align-self: auto").hasSelfAlignment())
        assertFalse(parseStyle("display: flex; align-self: stretch").hasSelfAlignment())

        assertEquals(Alignment.End, nativeFlexColumnChildAlignment("flex-end", null))
        assertEquals(Alignment.CenterHorizontally, nativeFlexColumnChildAlignment("center", null))
        // `auto` defers to the container's `align-items`.
        assertEquals(Alignment.End, nativeFlexColumnChildAlignment("auto", "flex-end"))
        assertEquals(Alignment.Bottom, nativeFlexRowChildVerticalAlignment("flex-end", null))
        assertEquals(Alignment.Top, nativeFlexRowChildVerticalAlignment("self-start", null))
    }

    @Test
    fun `gap and justify content place stacked flex items`() {
        // Three 10px items in 100px, 6px gap: packed at the top, leftover at the end.
        assertPositions("flex-start", totalSize = 100, sizes = listOf(10, 10, 10)) {
            listOf(0, 16, 32)
        }
        // Centered: (100 - 30 - 12) / 2 = 29 leading.
        assertPositions("center", totalSize = 100, sizes = listOf(10, 10, 10)) {
            listOf(29, 45, 61)
        }
        // space-between spreads the remaining 58px over the two gaps: 29 each.
        assertPositions("space-between", totalSize = 100, sizes = listOf(10, 10, 10)) {
            listOf(0, 45, 90)
        }
        // space-around gives each item 58/3, so the first starts half a share in.
        assertPositions("space-around", totalSize = 100, sizes = listOf(10, 10, 10)) {
            listOf(9, 44, 79)
        }
        // space-evenly splits the leftover into count + 1 equal units: 58 / 4 = 14
        // before, between and after the items.
        assertPositions("space-evenly", totalSize = 100, sizes = listOf(10, 10, 10)) {
            listOf(14, 44, 74)
        }
    }

    @Test
    fun `row gap mirrors for right to left layouts`() {
        val ltr = arrangeRow("flex-start", listOf(10, 20), LayoutDirection.Ltr)
        assertEquals(listOf(0, 16), ltr)
        // Right to left lays the first item against the right edge instead.
        val rtl = arrangeRow("flex-start", listOf(10, 20), LayoutDirection.Rtl)
        assertEquals(listOf(90, 64), rtl)
    }

    private fun assertPositions(
        justifyContent: String,
        totalSize: Int,
        sizes: List<Int>,
        expected: () -> List<Int>
    ) {
        val arrangement = nativeFlexColumnVerticalArrangement(justifyContent, 6.dp)
        assertEquals(expected(), arrange(arrangement, totalSize, sizes))
    }

    private fun arrange(
        arrangement: androidx.compose.foundation.layout.Arrangement.Vertical,
        totalSize: Int,
        sizes: List<Int>
    ): List<Int> {
        val out = IntArray(sizes.size)
        with(arrangement) {
            with(Density(1f)) {
                arrange(totalSize, sizes.toIntArray(), out)
            }
        }
        return out.toList()
    }

    private fun arrangeRow(
        justifyContent: String,
        sizes: List<Int>,
        direction: LayoutDirection
    ): List<Int> {
        val out = IntArray(sizes.size)
        val arrangement = nativeFlexRowHorizontalArrangement(justifyContent, 6.dp)
        with(arrangement) {
            with(Density(1f)) {
                arrange(100, sizes.toIntArray(), direction, out)
            }
        }
        return out.toList()
    }

    private fun parseStyle(declarations: String): BlockStyle = CssParser.parseProperties(
        properties = declarations,
        baseFontSizeSp = 16f,
        density = 1f,
        constraints = Constraints(maxWidth = 1000, maxHeight = 1000),
        onlyImportant = false,
        isDarkTheme = false
    ).blockStyle
}