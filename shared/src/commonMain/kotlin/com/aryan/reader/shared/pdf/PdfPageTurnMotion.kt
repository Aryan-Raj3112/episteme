package com.aryan.reader.shared.pdf

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.aryan.reader.shared.ReaderTheme
import kotlin.math.abs

/**
 * Android-benchmark rule for the realistic page curl in paginated PDF mode: only
 * single pager-step turns (edge taps, manual page turns) animate with the curl;
 * multi-page jumps (slider, TOC, links, TTS) keep their existing motion. The
 * animation must also be user-enabled.
 */
fun shouldPlayRealisticPdfPageTurn(
    animationEnabled: Boolean,
    fromPagerPage: Int,
    toPagerPage: Int,
): Boolean = animationEnabled && abs(toPagerPage - fromPagerPage) == 1

/**
 * Draw-order bucket for a pager slot that is mid-turn.
 *
 * Android reads the continuous pager offset in composition (`zIndex(-turnPageOffset)`).
 * Re-reading it in composition on iOS invalidates the whole pager slot on every
 * animation frame, which is what made realistic turns stutter. Bucketing the offset
 * to its sign keeps the ordering that matters — the curled sheet above the page
 * sliding in beneath it, and the un-curling page above the one it covers — while
 * only invalidating when a turn starts and when it settles. Slots sharing a sign are
 * ordered by pager slot index instead, which is visually identical because at most
 * one slot per sign is inside the viewport.
 */
fun pdfPagerTurnStackOrder(offsetFraction: Float): Float = when {
    offsetFraction < 0f -> 1f
    offsetFraction > 0f -> -1f
    else -> 0f
}

/**
 * Turn duration/easing for a realistic page turn, matching the Android benchmark
 * pager snap (`tween(700)` with FastOutSlowIn easing).
 */
val RealisticPdfPageTurnAnimationSpec: AnimationSpec<Float> =
    tween(durationMillis = 700, easing = FastOutSlowInEasing)

/**
 * Color of the PDF page sheet itself under [theme]. The realistic page curl paints
 * this color for the folded flap (the physical back of the sheet).
 */
fun pdfPaginatedPagePaperColor(theme: ReaderTheme): Color = when (theme.id) {
    "no_theme", "system" -> Color.White
    "reverse" -> Color.Black
    else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
}
