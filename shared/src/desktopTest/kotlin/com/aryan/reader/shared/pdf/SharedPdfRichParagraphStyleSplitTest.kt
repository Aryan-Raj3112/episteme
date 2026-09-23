package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Probe + regression: ParagraphStyle ranges become separate MultiParagraph
 * segments via [androidx.compose.ui.text.normalizedParagraphStyles]. A segment
 * whose text edge sits on `'\n'` can lay out an extra line (phantom).
 *
 * Emission rules live in [richParagraphAlignRuns]:
 * - merge consecutive same-align paras into one range (`\n` is interior → no phantom);
 * - LEFT→non-LEFT / non-LEFT→LEFT boundaries may still cost one line (Approach B).
 */
class SharedPdfRichParagraphStyleSplitTest {

    private val density = Density(1f)
    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = density,
        defaultLayoutDirection = LayoutDirection.Ltr,
    )

    private fun measure(text: String, paragraphStyles: List<AnnotatedString.Range<ParagraphStyle>>): Int {
        val annotated = AnnotatedString(text = text, paragraphStyles = paragraphStyles)
        return measurer.measure(
            text = annotated,
            style = TextStyle(fontSize = 16.sp),
            constraints = Constraints(minWidth = 400, maxWidth = 400),
        ).multiParagraph.lineCount
    }

    private fun center(start: Int, end: Int) =
        AnnotatedString.Range(ParagraphStyle(textAlign = TextAlign.Center), start, end)

    @Test
    fun `baseline line count equals newlines plus one`() {
        assertEquals(3, measure("​\n\nEhrbrbdb", emptyList()))
        assertEquals(2, measure("left\ncenter", emptyList()))
        assertEquals(4, measure("a\nb\nc\nd", emptyList()))
        assertEquals(3, measure("aa\nbb\ncc", emptyList()))
        assertEquals(3, measure("hello\n\nworld", emptyList()))
        assertEquals(2, measure("hello\n", emptyList()))
    }

    @Test
    fun `per paragraph style ranges invent lines versus merged run`() {
        // Old emit: separate ranges per CENTER para (gap "\n" between them).
        // Merge: one range — interior `\n` does not split segments.
        val text = "aa\nbb\ncc"
        val baseline = measure(text, emptyList())
        assertEquals(3, baseline)
        val separate = measure(text, listOf(center(3, 5), center(6, 8)))
        val merged = measure(text, listOf(center(3, 8)))
        val fromZero = measure(text, listOf(center(0, 8)))
        assertEquals(baseline, fromZero, "full coverage from 0 must equal baseline")
        // LEFT→CENTER boundary: gap "aa\n" ends with \n → exactly one extra line.
        // Merge still collapses N per-para phantoms down to this single boundary.
        assertEquals(baseline + 1, merged, "merged mid run = baseline + one LEFT boundary")
        assertTrue(
            separate >= merged,
            "fragmented per-para ranges must not beat merge (separate=$separate merged=$merged)",
        )
    }

    @Test
    fun `mid-text style start after LEFT gap may cost one boundary line`() {
        // Gap segment "left\n" ends with \n → +1 vs baseline (documented Approach A cost).
        // Merge reduces N per-para phantoms to this single boundary.
        assertEquals(3, measure("left\ncenter", listOf(center(5, 11))))
        // Same string fully covered → baseline (no boundary).
        assertEquals(2, measure("left\ncenter", listOf(center(0, 11))))
        assertEquals(3, measure("​\n\nEhrbrbdb", listOf(center(0, 11))))
        assertEquals(4, measure("​\n\nEhrbrbdb", listOf(center(3, 11))))
    }

    @Test
    fun `merged emission from shared runner equals baseline for all center`() {
        val text = "one\ntwo\nthree\nfour"
        val paragraphs = List(4) { SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER) }
        val runs = richParagraphAlignRuns(text, paragraphs)
        assertEquals(1, runs.size, "adjacent CENTER paras must merge to one run")
        assertEquals(0, runs[0].start)
        assertEquals(text.length, runs[0].end)
        val styled = measure(text, runs.map { center(it.start, it.end) })
        assertEquals(measure(text, emptyList()), styled, "merged CENTER run must not invent lines")
    }

    @Test
    fun `mark carries style include terminating newline in range`() {
        // Emission includes the mark so the run covers through the paragraph break.
        // Full-text single run from 0: same line count as baseline (EOF edge).
        assertEquals(3, measure("aa\nbb\ncc", listOf(center(0, 8))))
        assertEquals(2, measure("hello\n", listOf(center(0, 6))))
        // Only first para styled with mark + rest LEFT: boundary at next para may +1.
        // Documents why same-align merge (interior marks) matters more than mark alone.
        assertEquals(4, measure("aa\nbb\ncc", listOf(center(0, 3))))
    }

    @Test
    fun `empty mid mark style range docs cost of isolating a bare newline`() {
        // Isolating just the middle \n as its own style range explodes line count
        // (documents why empty mid paragraphs must merge with neighbors when possible).
        val lines = measure("hello\n\nworld", listOf(center(6, 7)))
        assertEquals(5, lines)
    }

    @Test
    fun `trailing newline must sit inside styled segment when whole text styled`() {
        // Style covering entire "hello\n" keeps one segment (2 lines = baseline).
        // Style stopping before final \n splits → gap "\n" adds a line.
        assertEquals(2, measure("hello\n", listOf(center(0, 6))))
        assertEquals(3, measure("hello\n", listOf(center(0, 5))))
    }
}
