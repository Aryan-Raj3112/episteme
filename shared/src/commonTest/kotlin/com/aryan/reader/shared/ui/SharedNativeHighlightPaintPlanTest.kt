package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One plan, so Android and iOS paint the same thing.
 *
 * These tests are the reason the two platforms agreed with each other. Android filled one path per
 * range, so two translucent highlights over the same words compounded into a darker patch than either
 * alone; shared merged per colour and did not. Any rule here is a rule both platforms now follow, so
 * a change that alters one has to be made deliberately in this file.
 */
class SharedNativeHighlightPaintPlanTest {

    private val LEGACY_ALPHA = SharedNativeHighlightPaintPlan.LEGACY_HIGHLIGHT_ALPHA

    private fun highlight(
        id: String,
        color: HighlightColor = HighlightColor.YELLOW,
        style: HighlightStyle = HighlightStyle.BACKGROUND,
        colorArgb: Int? = null
    ) = UserHighlight(
        id = id,
        cfi = "/4/0",
        text = "beta",
        color = color,
        chapterIndex = 0,
        colorArgb = colorArgb,
        style = style,
        locator = ReaderLocator(chapterIndex = 0, textQuote = "beta", cfi = "/4/0")
    )

    private fun planOf(vararg entries: Pair<UserHighlight, List<IntRange>>) =
        SharedNativeHighlightPaintPlan.build(entries.map { PaintableHighlight(it.first, it.second) })

    @Test
    fun `nothing to paint yields an empty plan`() {
        assertTrue(SharedNativeHighlightPaintPlan.build(emptyList()).isEmpty)
        assertTrue(planOf().isEmpty)
    }

    @Test
    fun `one highlight paints exactly its own range`() {
        val plan = planOf(highlight("a") to listOf(6..10))

        assertEquals(1, plan.groups.size)
        assertEquals(listOf(6..10), plan.groups.single().ranges)
    }

    @Test
    fun `two overlapping same-colour backgrounds merge into one fill`() {
        // The case that differed by platform. One fill over the union: painting both ranges would
        // composite the colour twice across the overlap and leave it darker than the rest.
        val plan = planOf(
            highlight("a") to listOf(6..16),
            highlight("b") to listOf(11..21)
        )

        assertEquals(1, plan.groups.size)
        assertEquals(listOf(6..21), plan.groups.single().ranges)
    }

    @Test
    fun `a fully contained highlight does not change the merged fill`() {
        val plan = planOf(
            highlight("outer") to listOf(0..30),
            highlight("inner") to listOf(10..15)
        )

        assertEquals(listOf(0..30), plan.groups.single().ranges)
    }

    @Test
    fun `adjacent ranges stay separate`() {
        // Merging these would make editing one highlight silently extend to the other.
        val plan = planOf(
            highlight("a") to listOf(0..5),
            highlight("b") to listOf(6..10)
        )

        assertEquals(listOf(0..5, 6..10), plan.groups.single().ranges)
    }

    @Test
    fun `different colours each keep a fill`() {
        val plan = planOf(
            highlight("a", HighlightColor.YELLOW) to listOf(6..16),
            highlight("b", HighlightColor.GREEN) to listOf(11..21)
        )

        assertEquals(2, plan.groups.size)
        // Each colour fills only its own range: neither is widened to cover the other's text.
        assertEquals(
            mapOf(
                HighlightColor.YELLOW.color.copy(alpha = LEGACY_ALPHA) to listOf(6..16),
                HighlightColor.GREEN.color.copy(alpha = LEGACY_ALPHA) to listOf(11..21)
            ),
            plan.groups.associate { it.color to it.ranges }
        )
    }

    @Test
    fun `a background and a line style of the same colour do not merge`() {
        // Same colour, different style: one fills and one decorates. Merging them would turn an
        // underline into a fill, or drop the fill.
        val plan = planOf(
            highlight("fill", style = HighlightStyle.BACKGROUND) to listOf(0..10),
            highlight("line", style = HighlightStyle.UNDERLINE) to listOf(4..8)
        )

        assertEquals(2, plan.groups.size)
        assertEquals(
            setOf(HighlightStyle.BACKGROUND, HighlightStyle.UNDERLINE),
            plan.groups.map { it.style }.toSet()
        )
    }

    @Test
    fun `backgrounds paint before decorations`() {
        // A decoration buried under a fill is invisible, so the order is part of the result, not an
        // implementation detail.
        val plan = planOf(
            highlight("line", style = HighlightStyle.STRIKETHROUGH) to listOf(4..8),
            highlight("fill") to listOf(4..8)
        )

        assertEquals(HighlightStyle.BACKGROUND, plan.groups.first().style)
        assertEquals(HighlightStyle.STRIKETHROUGH, plan.groups.last().style)
    }

    @Test
    fun `a three-way overlap merges to the union`() {
        val plan = planOf(
            highlight("a") to listOf(0..10),
            highlight("b") to listOf(5..15),
            highlight("c") to listOf(12..20)
        )

        assertEquals(listOf(0..20), plan.groups.single().ranges)
    }

    @Test
    fun `a range naming no characters is dropped`() {
        // An inverted range must not widen the group across a gap the reader never highlighted, so it
        // is dropped rather than merged. A one-character range is real and is kept.
        val plan = planOf(
            highlight("a") to listOf(6..10, 20..15, 30..30)
        )

        assertEquals(listOf(6..10, 30..30), plan.groups.single().ranges)
    }

    @Test
    fun `a highlight with several ranges contributes all of them`() {
        val plan = planOf(highlight("multi") to listOf(30..40, 0..10))

        assertEquals(listOf(0..10, 30..40), plan.groups.single().ranges)
    }

    @Test
    fun `identical input always yields the same group order`() {
        val forwards = planOf(
            highlight("a", HighlightColor.GREEN) to listOf(0..5),
            highlight("b", HighlightColor.YELLOW) to listOf(5..10)
        )
        val backwards = planOf(
            highlight("b", HighlightColor.YELLOW) to listOf(5..10),
            highlight("a", HighlightColor.GREEN) to listOf(0..5)
        )

        assertEquals(forwards.groups.map { it.color }, backwards.groups.map { it.color })
    }

    @Test
    fun `a read-aloud band paints like a highlight`() {
        // The plan has no opinion about ownership: the band is a fill and belongs in it. Deciding it
        // is not tappable happens where the plan is consumed.
        val band = UserHighlight(
            id = "tts_42_7",
            cfi = "/4/0",
            text = "beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(chapterIndex = 0, textQuote = "beta", cfi = "/4/0")
        )

        val plan = planOf(band to listOf(6..10))

        assertEquals(1, plan.groups.size)
        assertTrue(band.isTransientPlaybackBand)
    }

    @Test
    fun `a stored colour takes precedence over the legacy alpha`() {
        val stored = 0x8034AF72.toInt()
        val plan = planOf(highlight("a", colorArgb = stored) to listOf(0..5))

        assertEquals(Color(stored), plan.groups.single().color)
    }
}