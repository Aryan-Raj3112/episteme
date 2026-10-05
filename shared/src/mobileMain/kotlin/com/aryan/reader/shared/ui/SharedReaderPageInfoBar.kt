package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderPageInfoTitleFontSteps
import com.aryan.reader.shared.ReaderPageInfoTitleLineHeightEm
import com.aryan.reader.shared.ReaderPageInfoTitleMaxLines
import com.aryan.reader.shared.ReaderPageInfoTitleMinGap
import com.aryan.reader.shared.ReaderPageInfoTitleMinSideReserve
import com.aryan.reader.shared.ReaderPageInfoTitleSizeRatio
import com.aryan.reader.shared.readerPageInfoBarContentHeightPx
import com.aryan.reader.shared.readerPageInfoTitleFontStep
import com.aryan.reader.shared.readerPageInfoTitleSideReservePx
import com.aryan.reader.shared.readerPageInfoTitleWidthPx

/** Vertical breathing room above and below the PageInfo bar's text. */
private val ReaderPageInfoBarVerticalPadding = 3.dp

/**
 * Baseline side padding of the PageInfo bar's content row, on both platforms.
 *
 * The rounded-corner clearance is reported *in addition* to this, so the corner
 * radius only has to make up the difference rather than being paid twice.
 */
val SharedReaderPageInfoBarSidePadding = 16.dp

/**
 * Floor for the bar's content row, so a small system font scale never shrinks the
 * bar below the height Android shipped with.
 */
private val ReaderPageInfoBarMinContentHeight = 25.dp

/**
 * Content height of the shared mobile PageInfo bar.
 *
 * Sized for two lines of the title at its full size, because the title may wrap
 * to [ReaderPageInfoTitleMaxLines] lines: [readerPageInfoTitleFontStep] shrinks
 * the font and lets the text wrap, and only the first (largest) step has to fit
 * on its own. A single-line bar would clip the second line.
 *
 * Deliberately depends on nothing but the typography and the system font scale,
 * never on the chapter title or the chrome state: the WebView reserve and the
 * paginator viewport are both derived from this value, so a title that needs two
 * lines must not repaginate the book when the chapter changes. It scales with the
 * system font scale so large-accessibility-font settings are not clipped.
 *
 * Android benchmark (`PAGE_INFO_BAR_HEIGHT`) adds a rounded-corner allowance on
 * top of this; the safe-area background extension below covers the iOS home
 * indicator and curved corners instead of growing the content row.
 */
@Composable
fun sharedMobileEpubPageInfoBarContentHeight(): Dp {
    val density = LocalDensity.current
    val titleFontSize = MaterialTheme.typography.bodySmall.fontSize * ReaderPageInfoTitleSizeRatio
    return with(density) {
        readerPageInfoBarContentHeightPx(
            titleLineHeightPx = (titleFontSize * ReaderPageInfoTitleLineHeightEm).roundToPx(),
            maxLines = ReaderPageInfoTitleMaxLines,
            verticalPaddingPx = ReaderPageInfoBarVerticalPadding.roundToPx(),
            minHeightPx = ReaderPageInfoBarMinContentHeight.roundToPx()
        ).toDp()
    }
}

/**
 * Title style for one rung of [ReaderPageInfoTitleFontSteps].
 *
 * Sized up from the side labels by [ReaderPageInfoTitleSizeRatio], and both the
 * glyph size and the leading scale with the rung: holding the leading fixed while
 * the glyphs shrink leaves a 0.6x title floating in a full-size line box, which
 * both looks wrong and hides the real glyph size from any layout check.
 */
private fun TextStyle.readerPageInfoTitleAtStep(step: Float): TextStyle {
    val steppedFontSize = fontSize * ReaderPageInfoTitleSizeRatio * step
    return copy(
        fontSize = steppedFontSize,
        lineHeight = steppedFontSize * ReaderPageInfoTitleLineHeightEm
    )
}

/**
 * The PageInfo bar's three-part row: clock, centred chapter title, percentage.
 *
 * Shared by the Android bars and the iOS bar so the two can't drift apart.
 *
 * The title used to be inset by a fixed [ReaderPageInfoTitleMinSideReserve] per
 * side, which is only a guess: with a 12-hour clock, a "100.0%" progress value,
 * or a larger system font scale the side labels are wider than that guess and
 * they ran straight into the title. Here the labels are measured, and the title
 * is given exactly the width left over between them — so the [ReaderPageInfoTitleMinGap]
 * clearance holds at every font scale instead of at one lucky size.
 *
 * A long title walks down [ReaderPageInfoTitleFontSteps] (measuring the real text
 * at each rung, wrapping included) and takes the first size that fits within
 * [ReaderPageInfoTitleMaxLines] lines, so it shrinks before it wraps and
 * ellipsizes only once even the smallest step overflows.
 *
 * @param contentHeight must be [sharedMobileEpubPageInfoBarContentHeight], passed
 *   in so the row and the caller's content reserve are the same number.
 * @param progressText null hides the trailing label and gives its space back to
 *   the title.
 */
@Composable
fun SharedReaderPageInfoBarRow(
    clockText: String,
    titleText: String,
    progressText: String?,
    color: Color,
    contentHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val minGapPx = with(density) { ReaderPageInfoTitleMinGap.roundToPx() }
    val minReservePx = with(density) { ReaderPageInfoTitleMinSideReserve.roundToPx() }
    val contentHeightPx = with(density) { contentHeight.roundToPx() }

    BoxWithConstraints(modifier) {
        val barWidthPx = constraints.maxWidth
        val titleWidthPx = remember(barWidthPx, clockText, progressText, textStyle, density) {
            readerPageInfoTitleWidthPx(
                barWidthPx = barWidthPx,
                sideReservePx = readerPageInfoTitleSideReservePx(
                    leftLabelWidthPx = textMeasurer.measure(clockText, textStyle).size.width,
                    rightLabelWidthPx = progressText
                        ?.let { textMeasurer.measure(it, textStyle).size.width }
                        ?: 0,
                    minGapPx = minGapPx,
                    minReservePx = minReservePx,
                    barWidthPx = barWidthPx
                )
            )
        }
        // Measured, not guessed: a rung "fits" only when the real layout at that
        // size has no overflow left in either direction, so word wrapping and the
        // two-line cap are both honoured before the rung is accepted.
        val titleStep = remember(titleText, titleWidthPx, contentHeightPx, textStyle, density) {
            if (titleWidthPx <= 0 || contentHeightPx <= 0) {
                ReaderPageInfoTitleFontSteps.last()
            } else {
                readerPageInfoTitleFontStep { step ->
                    // measureTitle reports whether the text FIT; the ladder wants
                    // "fits", so pass it through directly.
                    textMeasurer.measureTitle(
                        text = titleText,
                        style = textStyle.readerPageInfoTitleAtStep(step),
                        maxWidthPx = titleWidthPx,
                        maxHeightPx = contentHeightPx
                    )
                }
            }
        }

        Text(
            text = titleText,
            style = textStyle.readerPageInfoTitleAtStep(titleStep),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = ReaderPageInfoTitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .width(with(density) { titleWidthPx.toDp() })
        )
        if (progressText != null) {
            Text(
                text = progressText,
                style = textStyle,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Text(
            text = clockText,
            style = textStyle,
            color = color,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

/**
 * Lays out [text] inside the title box and reports whether it fit without
 * overflow. Wrapping, the line cap and the ellipsis are the same ones the title
 * is finally rendered with, so "fits" here means "fits when drawn".
 */
private fun TextMeasurer.measureTitle(
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int
): Boolean {
    val result = measure(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = ReaderPageInfoTitleMaxLines,
        constraints = Constraints(maxWidth = maxWidthPx, maxHeight = maxHeightPx)
    )
    return !result.hasVisualOverflow && result.size.height <= maxHeightPx
}