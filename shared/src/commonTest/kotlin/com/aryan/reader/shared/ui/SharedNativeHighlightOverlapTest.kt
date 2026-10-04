package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Overlapping highlights must both stay visible.
 *
 * Background highlights are translucent, so applying two of them over the same characters used to
 * compound the alpha and leave the shared stretch darker than the rest, which reads as a rendering
 * fault rather than as two highlights. The ranges are now merged per colour before painting.
 */
class SharedNativeHighlightOverlapTest {

    private val text = "alpha beta gamma delta"

    private fun highlight(
        id: String,
        start: Int,
        end: Int,
        color: HighlightColor = HighlightColor.YELLOW,
        style: HighlightStyle = HighlightStyle.BACKGROUND
    ) = UserHighlight(
        id = id,
        cfi = "/4/2:$start",
        text = text.substring(start, end),
        color = color,
        chapterIndex = 0,
        style = style,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            textQuote = text.substring(start, end),
            cfi = "/4/2:$start"
        )
    )

    private fun render(vararg highlights: UserHighlight): AnnotatedString = buildAnnotatedString {
        append(text)
        applyHighlightsToTextRanges(
            highlights = highlights.toList(),
            chapterIndex = 0,
            pageIndex = 0,
            blockCfi = "/4/2",
            blockIndex = 1,
            blockCharOffset = 0,
            textStartOffset = 0,
            textLength = text.length,
            text = text
        )
    }

    private fun AnnotatedString.backgroundSpans(): List<AnnotatedString.Range<Color>> =
        spanStyles.filter { it.item.background.isSpecified }.map {
            AnnotatedString.Range(it.item.background, it.start, it.end)
        }

    @Test
    fun `single highlight paints exactly its own range`() {
        val spans = render(highlight("a", 6, 10)).backgroundSpans()

        assertEquals(1, spans.size)
        assertEquals(6, spans[0].start)
        // "beta" ends at 10 exclusive.
        assertEquals(10, spans[0].end)
    }

    @Test
    fun `two overlapping highlights of the same colour merge into one painted span`() {
        val spans = render(
            highlight("a", 6, 16),
            highlight("b", 11, 21)
        ).backgroundSpans()

        // One span over the union, so the overlap is painted once rather than twice.
        assertEquals(1, spans.size)
        assertEquals(6, spans[0].start)
        assertEquals(21, spans[0].end)
    }

    @Test
    fun `two overlapping highlights of different colours each keep a span`() {
        val spans = render(
            highlight("a", 6, 16, HighlightColor.YELLOW),
            highlight("b", 11, 21, HighlightColor.GREEN)
        ).backgroundSpans()

        assertTrue(spans.size >= 2, "expected one span per colour, got ${spans.size}")
        // Both highlights still cover the shared stretch, so neither disappears.
        assertEquals(2, countHighlightAnnotations(render(
            highlight("a", 6, 16, HighlightColor.YELLOW),
            highlight("b", 11, 21, HighlightColor.GREEN)
        )))
    }

    @Test
    fun `adjacent highlights are not merged`() {
        val spans = render(
            highlight("a", 6, 10),
            highlight("b", 10, 16)
        ).backgroundSpans()

        // They only touch. Merging them would make recolouring one silently affect the other.
        assertEquals(2, spans.size)
        assertEquals(6, spans[0].start)
        assertEquals(10, spans[0].end)
        assertEquals(10, spans[1].start)
        assertEquals(16, spans[1].end)
    }

    @Test
    fun `three-way overlap keeps every highlight addressable`() {
        val built = render(
            highlight("a", 0, 12),
            highlight("b", 6, 16),
            highlight("c", 10, 21)
        )

        assertEquals(3, countHighlightAnnotations(built))
    }

    @Test
    fun `background and line-style highlights coexist`() {
        val built = render(
            highlight("bg", 6, 16, HighlightColor.YELLOW, HighlightStyle.BACKGROUND),
            highlight("ul", 10, 21, HighlightColor.GREEN, HighlightStyle.UNDERLINE)
        )

        assertTrue(built.backgroundSpans().isNotEmpty())
        val decorated = built.spanStyles.filter {
            it.item.textDecoration != null
        }
        assertTrue(decorated.isNotEmpty(), "expected an underline decoration span")
    }

    @Test
    fun `line style span carries the highlight colour`() {
        val built = render(
            highlight("ul", 6, 16, HighlightColor.GREEN, HighlightStyle.UNDERLINE)
        )

        val decoration = built.spanStyles.first { it.item.textDecoration != null }
        val expected = UserHighlight(
            id = "ul",
            cfi = "",
            text = "",
            color = HighlightColor.GREEN,
            chapterIndex = 0
        ).renderColor(legacyAlpha = SharedNativeHighlightPaintPlan.LEGACY_HIGHLIGHT_ALPHA)
        assertEquals(expected, decoration.item.color)
    }

    @Test
    fun `no highlights means no spans`() {
        assertTrue(render().backgroundSpans().isEmpty())
        assertEquals(0, countHighlightAnnotations(render()))
    }

    private fun countHighlightAnnotations(annotated: AnnotatedString): Int =
        annotated.getStringAnnotations(ReaderNativeAnnotationHighlight, 0, annotated.length)
            .map { it.item }
            .distinct()
            .size
}