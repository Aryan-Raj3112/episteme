package com.aryan.reader.shared.reader

/**
 * Perception lines: a pair of vertical rules drawn over the reading area, inset from both edges of
 * the text column.
 *
 * The reader starts each line at the rule (not at the margin) and lets peripheral vision pick up the
 * word sitting outside it, so the eye is trained to take in more per fixation. KOReader ships the
 * same aid as its "Perception Expander" plugin; the evidence for the peripheral-vision claim is
 * thin (see `docs/perception-lines.md`), so this is modelled and labelled as a *reading aid* rather
 * than a speed feature: opt-in, off by default, and purely decorative.
 *
 * Decorative is also the engineering constraint: nothing here may reflow text. The overlay is drawn
 * above the rendered page, so these values must never enter [ReaderLayoutSignature] or any
 * pagination/measurement key. Changing them costs one recomposition, never a re-paginate.
 */
data class ReaderPerceptionLinesSettings(
    val enabled: Boolean = false,
    /** Opaque ARGB. Alpha is carried by [opacity] so the RGB picker result is stored verbatim. */
    val colorArgb: Long = DefaultPerceptionLinesColorArgb,
    /** Draw-time alpha, 0..1. Kept apart from the colour so picking a colour never changes it. */
    val opacity: Float = DefaultPerceptionLinesOpacity,
    val lineWidthDp: Float = DefaultPerceptionLinesWidthDp,
    /**
     * How far each rule sits from the edge of the reading area, as a fraction of its width. The two
     * rules always move symmetrically: the band is what matters, not the individual rails.
     */
    val insetFraction: Float = DefaultPerceptionLinesInsetFraction,
) {
    val safeOpacity: Float get() = sanitizePerceptionLinesFloat(opacity, DefaultPerceptionLinesOpacity)
        .coerceIn(MinPerceptionLinesOpacity, MaxPerceptionLinesOpacity)

    val safeLineWidthDp: Float get() = sanitizePerceptionLinesFloat(lineWidthDp, DefaultPerceptionLinesWidthDp)
        .coerceIn(MinPerceptionLinesLineWidthDp, MaxPerceptionLinesLineWidthDp)

    val safeInsetFraction: Float get() = sanitizePerceptionLinesFloat(insetFraction, DefaultPerceptionLinesInsetFraction)
        .coerceIn(MinPerceptionLinesInsetFraction, MaxPerceptionLinesInsetFraction)

    /** Opaque ARGB: a colour persisted before alpha was separated out must not draw as invisible. */
    val safeColorArgb: Long get() = (colorArgb and 0x00FFFFFFL) or 0xFF000000L

    fun sanitized(): ReaderPerceptionLinesSettings = copy(
        colorArgb = safeColorArgb,
        opacity = safeOpacity,
        lineWidthDp = safeLineWidthDp,
        insetFraction = safeInsetFraction,
    )
}

/**
 * Restores the three slider defaults (opacity, width, position) and keeps everything else.
 *
 * Deliberately not a full reset: the rail colour and the on/off state are separate choices a reader
 * makes, and the sheet's Reset is scoped to the sliders it sits above.
 */
fun ReaderPerceptionLinesSettings.withDefaultSliderValues(): ReaderPerceptionLinesSettings = copy(
    opacity = DefaultPerceptionLinesOpacity,
    lineWidthDp = DefaultPerceptionLinesWidthDp,
    insetFraction = DefaultPerceptionLinesInsetFraction,
)

/** Whether any of the three sliders differs from its default, i.e. whether Reset would do anything. */
val ReaderPerceptionLinesSettings.hasCustomSliderValues: Boolean
    get() = safeOpacity != DefaultPerceptionLinesOpacity ||
        safeLineWidthDp != DefaultPerceptionLinesWidthDp ||
        safeInsetFraction != DefaultPerceptionLinesInsetFraction

const val DefaultPerceptionLinesColorArgb: Long = 0xFF7A7A7AL

/**
 * Mid-grey at 70% is the one default that reads on both a white page and a dark theme without
 * becoming the loudest thing on screen.
 */
const val DefaultPerceptionLinesOpacity: Float = 0.7f
const val DefaultPerceptionLinesWidthDp: Float = 2f
const val DefaultPerceptionLinesInsetFraction: Float = 0.08f

const val MinPerceptionLinesOpacity: Float = 0.1f
const val MaxPerceptionLinesOpacity: Float = 1f
const val MinPerceptionLinesLineWidthDp: Float = 1f
const val MaxPerceptionLinesLineWidthDp: Float = 8f
const val MinPerceptionLinesInsetFraction: Float = 0f

/**
 * The rules stop well before the middle: past ~45% each the band is narrower than a word and the
 * aid becomes a keyhole. KOReader caps its own margin shift at 0.37 for the same reason.
 */
const val MaxPerceptionLinesInsetFraction: Float = 0.45f

/** `coerceIn` propagates NaN; a corrupted preference must fall back rather than poison the draw. */
private fun sanitizePerceptionLinesFloat(value: Float, fallback: Float): Float =
    if (value.isNaN() || value.isInfinite()) fallback else value

/**
 * Centre x of the left and right rules inside a reading area [contentWidthPx] wide.
 *
 * Pure so both hosts (and the tests) agree on where the rules land: Compose density only scales the
 * stroke, never the geometry.
 */
fun perceptionLineCenterXs(contentWidthPx: Float, insetFraction: Float): Pair<Float, Float> {
    if (contentWidthPx <= 0f) return 0f to 0f
    val fraction = sanitizePerceptionLinesFloat(insetFraction, DefaultPerceptionLinesInsetFraction)
        .coerceIn(MinPerceptionLinesInsetFraction, MaxPerceptionLinesInsetFraction)
    val inset = contentWidthPx * fraction
    return inset to (contentWidthPx - inset)
}

/** Whether a rule pair is far enough apart to be worth drawing at all. */
fun hasPerceptionLineBand(contentWidthPx: Float, insetFraction: Float): Boolean {
    val (left, right) = perceptionLineCenterXs(contentWidthPx, insetFraction)
    return right - left > 0f
}
