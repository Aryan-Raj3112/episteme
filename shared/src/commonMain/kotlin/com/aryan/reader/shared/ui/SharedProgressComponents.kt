package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The library's progress and waiting surfaces, in one place.
 *
 * The bar is a plain linear one on purpose. The Material 3 Expressive wavy indicator was tried here
 * first and rejected: on a card that already carries a cover, a title and a percentage, a travelling
 * wave reads as decoration competing with the artwork, and at 0% it collapses into a flat line that
 * looks like a rendering bug. Both themes want the same quiet bar, so every progress surface in the
 * app - Home's hero, Listen, OPDS downloads, the desktop grid - goes through here and cannot drift.
 *
 * [SharedLoadingIndicator] stays expressive: a morphing shape is the one place where the animation
 * carries information (something is happening) instead of repeating it.
 */

/** A determinate bar, inset to the height the caller asks for and rounded at both ends. */
@Composable
fun SharedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = activeColor.copy(alpha = 0.24f),
    // Thin on purpose: M3's default is 4dp and this bar only carries position, not emphasis.
    barHeight: Dp = 4.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { clamped },
        modifier = modifier.height(barHeight).clip(CircleShape),
        color = activeColor,
        trackColor = trackColor,
        // Butt caps and no stop dot: M3 draws a dot at each end, so an unopened book showed two
        // pinholes on an otherwise empty track. The `clip` above still rounds the bar itself.
        strokeCap = StrokeCap.Butt,
        drawStopIndicator = {},
    )
}

/** The same bar before a transfer has reported a length. */
@Composable
fun SharedIndeterminateProgressBar(
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = activeColor.copy(alpha = 0.24f),
    barHeight: Dp = 4.dp,
) {
    LinearProgressIndicator(
        modifier = modifier.height(barHeight).clip(CircleShape),
        color = activeColor,
        trackColor = trackColor,
    )
}

/**
 * The morphing-shape spinner.
 *
 * Used wherever the app used to spin a `CircularProgressIndicator`: the library's own shape language
 * (a polygon that shifts between forms) reads as "this app is working" rather than "a website is
 * loading", and it inherits the surrounding content colour through [color].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(modifier = modifier, color = color)
}
