package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private fun assertProgressStartsOnExpectedEdge(layoutDirection: LayoutDirection) {
        setCardContent(layoutDirection)

        composeTestRule.waitForIdle()
        val pixels = composeTestRule
            .onNodeWithTag("UnifiedLibraryContinueReadingProgress", useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        val centerY = pixels.height / 2
        val leftLuminance = android.graphics.Color.luminance(
            pixels[pixels.width / 8, centerY].toArgb()
        )
        val rightLuminance = android.graphics.Color.luminance(
            pixels[pixels.width * 7 / 8, centerY].toArgb()
        )

        assertThat(abs(leftLuminance - rightLuminance)).isGreaterThan(0.05f)
        assertThat(leftLuminance > rightLuminance)
            .isEqualTo(layoutDirection == LayoutDirection.Ltr)
    }

    private fun setCardContent(layoutDirection: LayoutDirection) {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    SharedAndroidUnifiedContinueCard(
                        sectionLabel = "Continue reading",
                        title = "Test book",
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
