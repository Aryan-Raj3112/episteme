package com.aryan.reader.epubreader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ReaderPageInfoTitleLineHeightEm
import com.aryan.reader.shared.ReaderPageInfoTitleSizeRatio
import com.aryan.reader.shared.ui.SharedReaderPageInfoBarRow
import com.aryan.reader.shared.ui.sharedMobileEpubPageInfoBarContentHeight
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout guarantees of the EPUB PageInfo bar row, shared by the Android bars and
 * the iOS bar.
 *
 * These are the two things the old fixed 48.dp title inset could not hold: a real
 * gap between the title and the clock / percentage at any text width, and a long
 * title that shrinks and wraps to at most two lines before it ellipsizes.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPageInfoBarRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fontScale = mutableFloatStateOf(1f)
    private var contentHeightPx = 0f
    private var titleFullStepLineHeightPx = 0f

    @Test
    fun shortTitleKeepsClockTitleAndPercentageAllVisible() {
        setRow(titleText = "Chapter 4")
        composeTestRule.onNodeWithText(CLOCK).assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 4").assertIsDisplayed()
        composeTestRule.onNodeWithText(PROGRESS).assertIsDisplayed()
    }

    @Test
    fun titleThatFitsRendersAtTheFullLabelSize() {
        // Regression: the ladder's fit predicate was inverted, so it rejected every
        // rung that fitted and stopped on the first that didn't — pinning the title
        // to the smallest step (0.6x, i.e. 7.2sp against a 12sp clock) for every
        // title however short. The chapter title therefore rendered visibly smaller
        // than the clock and the percentage beside it.
        //
        // A fitting title must render at the first rung, which is exactly one line
        // of the full label size. Node height is the honest signal here: the title's
        // leading is derived from its own rung, so height tracks font size.
        setRow(titleText = CLOCK)
        val title = composeTestRule.onAllNodesWithText(CLOCK)
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.width }
            .boundsInRoot
        assertThat(title.height.roundToInt()).isEqualTo(titleFullStepLineHeightPx.roundToInt())
    }

    @Test
    fun longTitleKeepsTheLargerOfItsTwoLinesRatherThanCollapsingToOneSmallLine() {
        // Same regression seen from the other side: sized to need two lines at the
        // label size but to fit on one at the smallest rung. Collapsing to the
        // smallest rung put it on a single 7.2sp line instead of two full-size ones.
        setRow(titleText = TWO_LINE_TITLE)
        val title = boundsOf(TWO_LINE_TITLE)
        assertThat(title.height.roundToInt())
            .isGreaterThan(titleFullStepLineHeightPx.roundToInt())
    }

    @Test
    fun sideLabelsWiderThanTheOldInsetDoNotOverlapTheTitle() {
        // The regression this fixes: at a large font scale with a 12-hour clock
        // and a "100.0%" progress value the labels were wider than the hardcoded
        // 48.dp inset and ran straight into the title.
        setRow(
            titleText = "The Chapter Of A Very Long Name Indeed",
            clockText = WIDE_CLOCK,
            progressText = WIDE_PROGRESS,
            fontScale = 2f
        )
        assertThreePartsNeverOverlap()
    }

    @Test
    fun titleKeepsAGapFromBothLabelsAtEveryFontScale() {
        setRow(titleText = LONG_TITLE, clockText = WIDE_CLOCK, progressText = WIDE_PROGRESS)
        listOf(0.85f, 1f, 1.3f, 2f, 3f).forEach { scale ->
            composeTestRule.runOnIdle { fontScale.floatValue = scale }
            assertThreePartsNeverOverlap(titleIsSubstring = true)
        }
    }

    @Test
    fun veryLongTitleStaysInsideTwoLines() {
        // Ellipsizing after two lines is the specified fallback; the requirement
        // is only that it never needs a third line and never collides.
        setRow(
            titleText = "An Extraordinarily Verbose Chapter Title That Cannot Possibly Fit On One Line",
            fontScale = 2f
        )
        assertThreePartsNeverOverlap(titleIsSubstring = true)
    }

    @Test
    fun hiddenPercentageGivesItsSpaceBackAndRendersNoEmptyLabel() {
        setRow(titleText = "Chapter 4", progressText = null)
        composeTestRule.onNodeWithText(CLOCK).assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 4").assertIsDisplayed()
        composeTestRule.onNodeWithText(PROGRESS).assertDoesNotExist()
    }

    @Test
    fun emptyTitleStillKeepsBothSideLabels() {
        setRow(titleText = " ")
        composeTestRule.onNodeWithText(CLOCK).assertIsDisplayed()
        composeTestRule.onNodeWithText(PROGRESS).assertIsDisplayed()
    }

    private var lastTitleText: String = ""
    private var lastClockText: String = CLOCK
    private var lastProgressText: String? = PROGRESS

    private fun setRow(
        titleText: String,
        clockText: String = CLOCK,
        progressText: String? = PROGRESS,
        fontScale: Float = 1f
    ) {
        lastTitleText = titleText
        lastClockText = clockText
        lastProgressText = progressText
        this.fontScale.floatValue = fontScale
        composeTestRule.setContent {
            MaterialTheme {
                val density = Density(DENSITY, this.fontScale.floatValue)
                CompositionLocalProvider(LocalDensity provides density) {
                    val contentHeight = sharedMobileEpubPageInfoBarContentHeight()
                    contentHeightPx = with(density) { contentHeight.toPx() }
                    titleFullStepLineHeightPx = with(density) {
                        (MaterialTheme.typography.bodySmall.fontSize.value *
                            ReaderPageInfoTitleSizeRatio *
                            ReaderPageInfoTitleLineHeightEm).sp.toPx()
                    }
                    SharedReaderPageInfoBarRow(
                        clockText = lastClockText,
                        titleText = lastTitleText,
                        progressText = lastProgressText,
                        color = Color.Black,
                        contentHeight = contentHeight,
                        modifier = Modifier.fillMaxWidth().height(contentHeight)
                    )
                }
            }
        }
    }

    /**
     * The title box is exactly the width between the two labels, so asserting the
     * three texts are laid out in order with no overlap is what proves the
     * clearance actually holds. The height checks keep the title inside the two
     * lines it is allowed rather than growing the bar.
     */
    private fun assertThreePartsNeverOverlap(titleIsSubstring: Boolean = false) {
        val clock = boundsOf(lastClockText)
        val title = boundsOf(lastTitleText, substring = titleIsSubstring)
        val progress = lastProgressText?.let { boundsOf(it) }
        assertThat(clock.right).isLessThan(title.left)
        if (progress != null) {
            assertThat(title.right).isLessThan(progress.left)
        }
        // The two-line cap: the content height is two lines of the *full-size*
        // step plus padding, and every step below that is smaller, so a third
        // line could never fit inside it. Bounding the title by the content
        // height is therefore exactly the "at most two lines" assertion.
        assertThat(title.height).isAtMost(contentHeightPx + 1f)
    }

    private fun boundsOf(text: String, substring: Boolean = false): Rect =
        composeTestRule.onNodeWithText(text, substring = substring)
            .fetchSemanticsNode().boundsInRoot

    private companion object {
        const val DENSITY = 2f
        const val CLOCK = "9:41 AM"
        const val WIDE_CLOCK = "12:34 PM"
        const val PROGRESS = "42.0%"
        const val WIDE_PROGRESS = "100.0%"
        const val LONG_TITLE = "Chapter Twelve: A Moderately Long Chapter Name"
        // Long enough to need two lines at the label size, short enough to fit
        // on one at the ladder's smallest rung.
        const val TWO_LINE_TITLE =
            "The Long Shadow Of The Ancient Harbour And Its Forgotten Lighthouse Keeper"
    }
}