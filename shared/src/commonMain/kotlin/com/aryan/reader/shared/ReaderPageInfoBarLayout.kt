package com.aryan.reader.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout contract for the EPUB reader PageInfo bar: clock on the leading edge,
 * percentage on the trailing edge, chapter title centred between them.
 *
 * The bar used to inset the title by a hardcoded `48.dp` per side and render a
 * single ellipsized line. That inset is a guess, and it is wrong as soon as
 * either side label is wider than it — a 12-hour clock ("12:34 PM"), "100.0%",
 * or any larger system font scale — so the labels then run into the title. This
 * file holds the parts of the fix that are pure arithmetic, so both platforms
 * share them and they can be unit tested without a Compose runtime.
 *
 * [readerPageInfoTitleSideReservePx] measures the side labels instead of
 * guessing, and [readerPageInfoTitleFontStep] walks a ladder of font sizes so a
 * long title shrinks before it wraps, then ellipsizes past
 * [ReaderPageInfoTitleMaxLines] lines. The composable that drives all of this
 * lives in mobileMain (`SharedReaderPageInfoBarRow`).
 */

/** Minimum air kept between the chapter title and the clock / percentage labels. */
val ReaderPageInfoTitleMinGap = 8.dp

/**
 * Per-side inset the title keeps even when both side labels are narrow.
 *
 * This is the inset the bar always used, so short labels keep the established
 * spacing instead of the title growing into it; [readerPageInfoTitleSideReservePx]
 * only goes above it when the measured labels actually need more room.
 */
val ReaderPageInfoTitleMinSideReserve = 48.dp

/**
 * Share of the bar width the title keeps even when the side labels are very
 * wide. Past this the labels win and the title shrinks further — the gap
 * guarantee matters more than a title that crowds them.
 */
const val ReaderPageInfoTitleMinWidthShare = 0.25f

/** Lines the title may wrap to before it ellipsizes. */
const val ReaderPageInfoTitleMaxLines = 2

/**
 * Size of the PageInfo chapter title relative to the clock and percentage.
 *
 * The title is the subject of the bar and its metadata; at parity it reads as
 * just another label. A slight increase puts it above the clock and the
 * percentage without the bar looking unbalanced.
 *
 * Expressed as a ratio of the label size rather than an absolute sp value so the
 * title can never end up *smaller* than the labels beside it, whatever the
 * typography or system font scale does.
 */
const val ReaderPageInfoTitleSizeRatio = 1.08f

/**
 * Content height of the PageInfo bar row, in pixels.
 *
 * Tall enough for [maxLines] lines of the title at its full size: the title may
 * wrap, and only the first (largest) rung of [ReaderPageInfoTitleFontSteps] has to
 * fit on its own — a single-line bar would clip the second line.
 */
fun readerPageInfoBarContentHeightPx(
    titleLineHeightPx: Int,
    maxLines: Int,
    verticalPaddingPx: Int,
    minHeightPx: Int
): Int = (titleLineHeightPx * maxLines + verticalPaddingPx).coerceAtLeast(minHeightPx)

/** Leading used for the title, as a multiple of its font size. */
const val ReaderPageInfoTitleLineHeightEm = 1.15f

/**
 * Font-size steps tried for the title, largest first, as multiples of the bar's
 * base text size. The bar's content height is sized for the tallest step at two
 * lines, so the first entry always wins on height and the ladder only decides
 * how many lines a long title needs.
 */
val ReaderPageInfoTitleFontSteps = listOf(1f, 0.92f, 0.85f, 0.78f, 0.72f, 0.66f, 0.6f)

/**
 * Horizontal space the centred title keeps on each side of the bar.
 *
 * Symmetric on purpose: the title stays centred on the bar while both side
 * labels keep at least [minGapPx] of clearance. [minReservePx] floors the value
 * at the inset the bar always used, so narrow labels leave the established
 * spacing untouched. The result is capped so the title always keeps
 * [ReaderPageInfoTitleMinWidthShare] of the bar, which is what stops the two
 * labels from pushing the title off a very narrow screen entirely.
 */
fun readerPageInfoTitleSideReservePx(
    leftLabelWidthPx: Int,
    rightLabelWidthPx: Int,
    minGapPx: Int,
    minReservePx: Int,
    barWidthPx: Int
): Int {
    if (barWidthPx <= 0) return 0
    val widestLabelPx = maxOf(leftLabelWidthPx, rightLabelWidthPx, 0)
    val reservePx = maxOf(widestLabelPx + minGapPx, minReservePx)
    val minTitleWidthPx = (barWidthPx * ReaderPageInfoTitleMinWidthShare).toInt()
    val maxReservePx = ((barWidthPx - minTitleWidthPx) / 2).coerceAtLeast(0)
    return reservePx.coerceIn(0, maxReservePx)
}

/**
 * Horizontal rounded-corner clearance for each edge of the PageInfo bar.
 *
 * A data class so the value compares by content: it recomposes whenever the
 * window insets change, and an identity comparison would make every recomposition
 * look like a change and re-run the bar's layout.
 */
data class ReaderPageInfoCornerClearance(val start: Dp, val end: Dp)

/**
 * Extra horizontal inset, in pixels, that keeps the PageInfo bar's edge-pinned
 * clock and percentage clear of the screen's rounded corners.
 *
 * Compose's `WindowInsets.safeDrawing` is the union of the system bars, the display
 * cutout and the waterfall; it carries nothing about the corner curve, so a
 * bezel-less phone in portrait reports no horizontal inset at all while the corners
 * still curve into the bar. The platform guideline
 * (https://developer.android.com/develop/ui/views/layout/insets/rounded-corners) is
 * to inset the content edge by the radius of the corners on that edge, less the
 * margin and padding already there, never below zero.
 *
 * Two details from that guideline matter here and are easy to get wrong:
 *
 *  * The radius is resolved **per side**, `start = max(topStart, bottomStart)` and
 *    `end = max(topEnd, bottomEnd)`, not as one maximum over all four. The two
 *    sides genuinely differ — in landscape the gesture pill sits on one edge only,
 *    and a bar spanning both edges must not borrow the far corner's radius for the
 *    near one, or it shifts off-centre.
 *  * Both the margin and the padding are subtracted. [startInsetPx]/[endInsetPx]
 *    are the system insets already applied as padding, and those count toward the
 *    clearance the corner needs; leaving them out double-counts the inset and
 *    pushes the labels further in than the guideline asks.
 *
 * Insets are reported in window coordinates, so a window that does not fill the
 * display still gets the radius measured from the window's own edge.
 */
fun readerPageInfoCornerClearance(
    startTopRadius: Dp,
    startBottomRadius: Dp,
    endTopRadius: Dp,
    endBottomRadius: Dp,
    startInset: Dp,
    endInset: Dp,
    barSidePadding: Dp
): ReaderPageInfoCornerClearance = ReaderPageInfoCornerClearance(
    start = readerPageInfoCornerSidePadding(
        radius = maxOf(startTopRadius, startBottomRadius),
        inset = startInset,
        barSidePadding = barSidePadding
    ),
    end = readerPageInfoCornerSidePadding(
        radius = maxOf(endTopRadius, endBottomRadius),
        inset = endInset,
        barSidePadding = barSidePadding
    )
)

/**
 * The guideline's `calculatePadding`: the edge's corner radius less the margin and
 * padding already present, floored at zero.
 */
private fun readerPageInfoCornerSidePadding(
    radius: Dp,
    inset: Dp,
    barSidePadding: Dp
): Dp = (radius - inset - barSidePadding).coerceAtLeast(0.dp)

/**
 * Width the centred title can occupy inside a bar once [sideReservePx] is taken
 * off both sides.
 */
fun readerPageInfoTitleWidthPx(barWidthPx: Int, sideReservePx: Int): Int =
    (barWidthPx - 2 * sideReservePx).coerceAtLeast(0)

/**
 * Largest [ReaderPageInfoTitleFontSteps] entry whose measured title fits, and
 * the smallest entry when even that one overflows — the caller then renders at
 * two lines with an ellipsis.
 *
 * [fitsAtStep] measures the real text at the given step (width, wrapping and
 * height together), which is why the decision lives here but the measurement
 * stays in the composable.
 */
fun readerPageInfoTitleFontStep(fitsAtStep: (Float) -> Boolean): Float {
    ReaderPageInfoTitleFontSteps.forEach { step ->
        if (fitsAtStep(step)) return step
    }
    return ReaderPageInfoTitleFontSteps.last()
}