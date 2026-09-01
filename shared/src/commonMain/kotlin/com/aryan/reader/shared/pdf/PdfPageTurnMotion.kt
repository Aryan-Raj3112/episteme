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
