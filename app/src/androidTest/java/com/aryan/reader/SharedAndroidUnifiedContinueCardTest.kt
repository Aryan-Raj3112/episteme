package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.SharedAndroidHomeDensity
import com.aryan.reader.shared.ui.SharedAndroidUnifiedContinueCard
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAndroidUnifiedContinueCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun progressIndicatorFillsFromTheRightInRtl() {
        assertProgressStartsOnExpectedEdge(LayoutDirection.Rtl)
    }

    @Test
    fun progressIndicatorFillsFromTheLeftInLtr() {
        assertProgressStartsOnExpectedEdge(LayoutDirection.Ltr)
    }

    @Test
    fun cardMirrorsCoverAndContentForRtl() {
        setCardContent(LayoutDirection.Rtl)
        composeTestRule.waitForIdle()

        val coverBounds = composeTestRule
            .onNodeWithTag("UnifiedLibraryContinueReadingCover", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val contentBounds = composeTestRule
            .onNodeWithTag("UnifiedLibraryContinueReadingContent", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertThat(coverBounds.left.value).isGreaterThan(contentBounds.right.value)
    }

    /**
     * The hero used to be a fixed 178dp tall, which cut the progress bar in half as soon as the title
     * wrapped. The card now floors at that height and grows only if its content needs more, so a
     * two-line title must stay fully inside it - on today's thin bar it fits without growth, which is
     * fine: nothing is clipped, and that is the invariant.
     */
    @Test
    fun twoLineTitleStaysInsideTheCard() {
        setCardContent(title = "The Curious Incident of the Dog in the Night-Time")
        composeTestRule.waitForIdle()

        val card = cardBounds()
        val progress = progressBounds()

        assertThat((card.bottom - card.top).value)
            .isAtLeast(SharedAndroidHomeDensity.Standard.continueCardHeight.value - 0.5f)
        assertThat(progress.top.value).isAtLeast(card.top.value)
        assertThat(progress.bottom.value).isAtMost(card.bottom.value)
    }

    @Test
    fun oneLineTitleKeepsTheMinimumCardHeight() {
        setCardContent()
        composeTestRule.waitForIdle()

        val card = cardBounds()
        val progress = progressBounds()

        assertThat((card.bottom - card.top).value)
            .isWithin(1f)
            .of(SharedAndroidHomeDensity.Standard.continueCardHeight.value)
        assertThat(progress.bottom.value).isAtMost(card.bottom.value)
    }

    private fun cardBounds() = composeTestRule
        .onNodeWithTag("UnifiedLibraryContinueReading")
        .getUnclippedBoundsInRoot()

    private fun progressBounds() = composeTestRule
        .onNodeWithTag("UnifiedLibraryContinueReadingProgress", useUnmergedTree = true)
        .getUnclippedBoundsInRoot()

    private fun assertProgressStartsOnExpectedEdge(layoutDirection: LayoutDirection) {
        setCardContent(layoutDirection)

        composeTestRule.waitForIdle()
        val pixels = composeTestRule
            .onNodeWithTag("UnifiedLibraryContinueReadingProgress", useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        // Mean over a band, not a single pixel: the bar is a wave, so any one column can fall on a
        // crest and read as background. Averaging the whole height of each end measures the ink.
        val leadingLuminance = meanLuminance(pixels, from = 0.05f, to = 0.20f)
        val trailingLuminance = meanLuminance(pixels, from = 0.80f, to = 0.95f)

        assertThat(abs(leadingLuminance - trailingLuminance)).isGreaterThan(0.05f)
        // A quarter of the book is read, so the darker half is the filled one and it has to sit on
        // the side the language reads from.
        assertThat(leadingLuminance < trailingLuminance)
            .isEqualTo(layoutDirection == LayoutDirection.Ltr)
    }

    private fun meanLuminance(pixels: PixelMap, from: Float, to: Float): Float {
        val startX = (pixels.width * from).toInt()
        val endX = (pixels.width * to).toInt()
        var total = 0.0
        var count = 0
        for (x in startX until endX) {
            for (y in 0 until pixels.height) {
                total += android.graphics.Color.luminance(pixels[x, y].toArgb())
                count++
            }
        }
        return (total / count).toFloat()
    }

    private fun setCardContent(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        title: String = "Test book",
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    SharedAndroidUnifiedContinueCard(
                        sectionLabel = "Continue reading",
                        title = title,
                        author = "Test author",
                        progressPercent = 25f,
                        progressLabel = "25% complete",
                        sourceLabel = null,
                        coverTone = Color.Magenta,
                        cardLayoutDirection = layoutDirection,
                        onClick = {},
                        cover = { coverModifier ->
                            Box(coverModifier.background(Color.Magenta))
                        },
                        fileTypeBadge = {},
                    )
                }
            }
        }
    }
}
