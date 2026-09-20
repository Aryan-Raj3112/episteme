package com.aryan.reader.paginatedreader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerticalTextEngineTest {

    private val fakeMeasurer = object : VerticalGlyphMeasurer {
        override fun measureUpright(text: String, fontSizePx: Float): Size = Size(fontSizePx, fontSizePx)
        override fun measureHorizontal(text: String, fontSizePx: Float): Size =
            Size(text.length * fontSizePx * 0.55f, fontSizePx)
    }

    private fun layout(
        text: String,
        rubies: List<RubyAnnotation> = emptyList(),
        tcyRanges: List<IntRange> = emptyList(),
        columnHeightPx: Float = 100f,
        maxColumns: Int = Int.MAX_VALUE
    ): VerticalParagraphLayout {
        var result: VerticalParagraphLayout? = null
        runTest { result = layoutVerticalParagraph(
        text = text,
        rubies = rubies,
        tcyRanges = tcyRanges,
        fontSizePx = 10f,
        lineHeightEm = 1.75f,
        columnHeightPx = columnHeightPx,
        maxColumns = maxColumns,
        measurer = fakeMeasurer,
        cache = verticalGlyphCache()
        ) }
        return result!!
    }

    @Test
    fun `columns flow right to left with one em per kana`() {
        // 10 chars at 10px em into 100px columns -> exactly one column.
        val result = layout("あいうえおかきくけこ")
        assertEquals(1, result.columns.size)
        assertEquals(17.5f, result.pitchPx)
        val column = result.columns.single()
        assertEquals(10, column.cells.size)
        assertEquals(0, column.cells.first().charOffset)
        // First column is rightmost: x decreases across columns.
        val twoColumns = layout("あいうえおかきくけこさしすせそたちつてと", columnHeightPx = 100f)
        assertEquals(2, twoColumns.columns.size)
        val firstX = twoColumns.columns[0].cells.first().xPx
        val secondX = twoColumns.columns[1].cells.first().xPx
        assertTrue(firstX > secondX)
    }

    @Test
    fun `ruby attaches to base and reserves pitch`() {
        val result = layout("昼下", rubies = listOf(RubyAnnotation(0, 1, "ひる")))
        val firstCell = result.columns.single().cells.first()
        assertEquals("ひる", firstCell.reading?.text)
        assertEquals(17.5f, result.pitchPx)
    }

    @Test
    fun `tcy range becomes one combined cell`() {
        val result = layout("パフェ!?", tcyRanges = listOf(3..4))
        val cells = result.columns.single().cells
        val combined = cells.last()
        assertEquals(VerticalCellOrientation.COMBINED, combined.orientation)
        assertEquals("!?", combined.text)
        assertEquals(2, combined.charLength)
    }

    @Test
    fun `kinsoku avoids prohibited column starts`() {
        // 10 per column; 11th char is 。which must not start a column.
        val text = "あいうえおかきくけこ。さ"
        val result = layout(text, columnHeightPx = 100f)
        assertEquals(2, result.columns.size)
        // 。 overhangs the first column instead of starting the second.
        assertEquals(11, result.columns[0].cells.size)
        assertEquals(11, result.columns[1].cells.first().charOffset)
    }

    @Test
    fun `explicit line feed forces column break`() {
        val result = layout("あいう\nえお", columnHeightPx = 100f)
        assertEquals(2, result.columns.size)
        assertEquals(4, result.columns[1].cells.first().charOffset)
    }

    @Test
    fun `column prefix reports consumed end offset`() {
        val result = layout("あいうえおかきくけこさしすせそたちつてと", columnHeightPx = 100f)
        assertEquals(2, result.columns.size)
        assertEquals(10, result.endOffsetForColumnPrefix(1))
        assertEquals(20, result.endOffsetForColumnPrefix(2))
    }

    @Test
    fun `ruby split helper keeps base atomic`() {
        val rubies = listOf(RubyAnnotation(4, 6, "ひる"))
        assertEquals(4, adjustPaginationSplitForRubies(rubies, 5))
        assertEquals(7, adjustPaginationSplitForRubies(rubies, 7))
        assertEquals(null, adjustPaginationSplitForRubies(listOf(RubyAnnotation(0, 2, "x")), 1))
    }

    @Test
    fun `ruby slicing clips and shifts`() {
        val rubies = listOf(RubyAnnotation(2, 4, "ab"), RubyAnnotation(8, 10, "cd"))
        assertEquals(listOf(RubyAnnotation(2, 4, "ab")), sliceRubyAnnotations(rubies, 0, 6))
        assertEquals(listOf(RubyAnnotation(0, 2, "cd")), sliceRubyAnnotations(rubies, 8, 12))
        assertEquals(emptyList(), sliceRubyAnnotations(rubies, 4, 8))
    }

    @Test
    fun `rects cover exactly the ranged cells`() {
        val result = layout("あいうえお", columnHeightPx = 100f)
        val cells = result.columns.single().cells
        val rects = result.rectsForRange(1, 3)
        assertEquals(2, rects.size)
        assertEquals(cells[1].let { it.xPx to it.yPx }, rects[0].topLeft.let { it.x to it.y })
        assertEquals(cells[2].let { it.xPx to it.yPx }, rects[1].topLeft.let { it.x to it.y })
        assertTrue(result.rectsForRange(2, 2).isEmpty())
        assertTrue(result.rectsForRange(3, 1).isEmpty())
    }

    @Test
    fun `offset resolves the covering cell`() {
        val result = layout("あいうえお", columnHeightPx = 100f)
        val cells = result.columns.single().cells
        val target = cells[2]
        val inside = Offset(
            target.xPx + target.widthPx / 2f,
            target.yPx + target.heightPx / 2f
        )
        assertEquals(2, result.offsetAt(inside))
        assertEquals(null, result.offsetAt(Offset(-1000f, -1000f)))
    }

    @Test
    fun `punctuation maps to encoded vertical presentation forms`() {
        assertEquals('\uFE41', '「'.verticalPresentationForm())
        assertEquals('\uFE42', '」'.verticalPresentationForm())
        assertEquals('\uFE35', '（'.verticalPresentationForm())
        assertEquals('\uFE36', '）'.verticalPresentationForm())
        assertEquals('\uFE11', '、'.verticalPresentationForm())
        assertEquals('\uFE12', '。'.verticalPresentationForm())
        assertEquals('\uFE15', '！'.verticalPresentationForm())
        assertEquals('\uFE16', '？'.verticalPresentationForm())
        assertEquals('\uFE19', '…'.verticalPresentationForm())
        // No encoded vertical form: prolonged sound mark and ASCII stay as-is.
        assertEquals('ー', 'ー'.verticalPresentationForm())
        assertEquals('あ', 'あ'.verticalPresentationForm())
        assertEquals('A', 'A'.verticalPresentationForm())
    }

    @Test
    fun `mapped presentation forms stay upright and fullwidth`() {
        for ((source, mapped) in VERTICAL_PRESENTATION_FORMS) {
            assertEquals(
                VerticalCellOrientation.UPRIGHT, verticalOrientationForChar(mapped),
                "mapped U+${mapped.code.toString(16)} must draw upright"
            )
            assertEquals(
                VerticalCellOrientation.UPRIGHT, verticalOrientationForChar(source)
            )
        }
    }

    @Test
    fun `segments draw mapped punctuation with original offsets`() {
        val result = layout("「あ」", columnHeightPx = 100f)
        val cells = result.columns.single().cells
        assertEquals(listOf("﹁", "あ", "﹂"), cells.map { it.text })
        assertEquals(listOf(0, 1, 2), cells.map { it.charOffset })
        assertEquals(
            VerticalCellOrientation.UPRIGHT,
            cells.first().orientation
        )
        // Advances match the unmapped layout: pagination breaks are unchanged.
        val mappedWidth = result.widthPx
        val plainWidth = layout("﹁あ﹂", columnHeightPx = 100f).widthPx
        assertEquals(plainWidth, mappedWidth)
        // Every mapped cell still advances exactly one em.
        for (cell in cells) {
            assertEquals(10f, cell.heightPx)
            assertEquals(10f, cell.widthPx)
        }
    }

    @Test
    fun `rotated runs and tcy never map`() {
        val latin = layout("ABC!?", columnHeightPx = 100f)
        val run = latin.columns.single().cells.single()
        assertEquals(VerticalCellOrientation.ROTATED, run.orientation)
        assertEquals("ABC!?", run.text)
        val tcy = layout("パフェ!?", tcyRanges = listOf(3..4), columnHeightPx = 100f)
        assertEquals("!?", tcy.columns.single().cells.last().text)
    }

    @Test
    fun `utr50 rotate classes draw rotated`() {
        // R classes: dashes, curly quotes, dot leaders.
        for (c in "\u2013\u2014\u2015\u2018\u2019\u201C\u201D\u2024\u2025\u2027") {
            assertEquals(VerticalCellOrientation.ROTATED, verticalOrientationForChar(c))
        }
        // Tr classes whose vertical variants are unencoded: the variants are
        // the rotated designs, so the Tr fallback (rotate) reproduces them.
        for (c in "ー〜～：〰") {
            assertEquals(VerticalCellOrientation.ROTATED, verticalOrientationForChar(c))
        }
        // Encoded-form punctuation stays upright (substituted at segmentation).
        for (c in "、。「」『』（）〈〉《》【】〔〕［］｛｝！？…，＿；") {
            assertEquals(VerticalCellOrientation.UPRIGHT, verticalOrientationForChar(c))
        }
    }

    @Test
    fun `rotated punctuation becomes rotated cells beside upright kana`() {
        val result = layout("あーき", columnHeightPx = 100f)
        val cells = result.columns.single().cells
        assertEquals(
            listOf(VerticalCellOrientation.UPRIGHT, VerticalCellOrientation.ROTATED, VerticalCellOrientation.UPRIGHT),
            cells.map { it.orientation }
        )
        assertEquals(listOf("あ", "ー", "き"), cells.map { it.text })
        assertEquals(listOf(0, 1, 2), cells.map { it.charOffset })
    }

    @Test
    fun `ruby readings stay inside the column pitch`() {
        // pitch = max(1.75, ruby reserve 1.55) * 10px em = 17.5px. The
        // unclamped reading would reach x = 8.75 + 5 + 0.8 + 5 = 19.55px,
        // past the slot; it must clamp to the slot's right edge instead so
        // first-column readings never paint into the neighboring block.
        val result = layout("昼下がり", rubies = listOf(RubyAnnotation(0, 1, "ひる")))
        val pitch = result.pitchPx
        assertEquals(17.5f, pitch)
        val reading = result.columns.single().cells.first().reading!!
        assertTrue(reading.xPx > 0f)
        assertTrue(reading.xPx + reading.widthPx <= pitch + 0.01f)
    }
}
