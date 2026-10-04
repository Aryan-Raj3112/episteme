package com.aryan.reader.shared

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import com.aryan.reader.shared.ui.SharedNativeHighlightPaintPlan

/**
 * The CSS a renderer should fill a highlight with, as `rgba(r, g, b, a)`.
 *
 * Every surface that paints a highlight has to agree on the colour, and they did not. The native and
 * annotated-string painters fill with [UserHighlight.renderColor], which applies the legacy fill alpha
 * — a highlight is a tint over the page, not a slab of colour. The WebView surfaces emitted the stored
 * colour as `#RRGGBB`, which carries no alpha, so a highlight painted there was fully opaque while the
 * same highlight in pagination was a soft tint. The hue matched, which is what made it read as a
 * rendering fault rather than a bug: the same highlight was a different *tone* depending on where you
 * looked at it.
 *
 * Deriving both from [renderColor] is the point. The rule lives in one place and the WebView cannot
 * drift from it, which is the failure this whole class of work came from.
 *
 * Alpha always travels with the colour, including for line styles, because that is what the other
 * painters do.
 */
fun UserHighlight.fillCssColor(
    legacyAlpha: Float = SharedNativeHighlightPaintPlan.LEGACY_HIGHLIGHT_ALPHA
): String = renderColor(legacyAlpha).toRgbaCss()

/** [Color] as a CSS `rgba()` string, preserving its alpha. */
fun Color.toRgbaCss(): String {
    fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).toInt()
    return "rgba(${channel(red)},${channel(green)},${channel(blue)},${alpha.roundForCss()})"
}

/**
 * Alpha to three decimals.
 *
 * Two decimals is not enough to distinguish a 40% fill from a 39% one, and the whole point is that the
 * tone matches the other painters exactly. Three is enough and is still valid CSS.
 */
private fun Float.roundForCss(): String {
    val clamped = coerceIn(0f, 1f)
    val scaled = (clamped * 1000f).roundToInt()
    val whole = scaled / 1000
    val frac = scaled % 1000
    return if (frac == 0) "$whole.0" else "$whole.${frac.toString().padStart(3, '0').trimEnd('0')}"
}