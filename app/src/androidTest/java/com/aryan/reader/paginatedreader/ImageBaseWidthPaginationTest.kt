package com.aryan.reader.paginatedreader

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parity item B1 — the image base width, verified through the real Android paginator on a device.
 *
 * `PaginatorImageSizingTest` pins `computeImageRenderSizePx` and `SharedNativeImageStyleTest` pins
 * the shared render half, but both are pure arithmetic on a JVM. This drives the production
 * `paginate` over an image block and reads back the height the paginator stored, which is the value
 * the renderer then uses as its own bound. That is the data flow the item's earlier bug lived in:
 * "Android's guarantee was not its arithmetic."
 *
 * Android is the benchmark and must not move. A 60x20 ornament has to be measured at its intrinsic
 * size, not at the column width — which is what it already did here, and what shared did not.
 */
@RunWith(AndroidJUnit4::class)
class ImageBaseWidthPaginationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDensity = Density(density = 2f, fontScale = 1f)
    private val pageWidthPx = 800
    private val pageHeightPx = 2000

    private fun paginateImage(
        intrinsicWidth: Float,
        intrinsicHeight: Float,
        cssWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified
    ): Page {
        var pages: List<Page>? = null
        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                val block = ImageBlock(
                    path = "images/ornament.png",
                    altText = null,
                    intrinsicWidth = intrinsicWidth,
                    intrinsicHeight = intrinsicHeight,
                    style = BlockStyle(width = cssWidth),
                    blockIndex = 0
                )
                val provider = SuspendingAndroidBlockMeasurementProvider(
                    textMeasurer = textMeasurer,
                    constraints = Constraints(maxWidth = pageWidthPx, maxHeight = pageHeightPx),
                    textStyle = TextStyle(),
                    density = testDensity,
                    imageSizeMultiplier = 1f
                )
                pages = paginate(listOf(block), pageHeightPx, provider, testDensity)
            }
        }
        composeTestRule.waitForIdle()
        val result = requireNotNull(pages) { "pagination did not run" }
        assertThat(result).hasSize(1)
        return result.single()
    }

    /** The placed block's height the paginator stored, which the renderer reads back as its bound. */
    private fun Page.placedImageHeight(): Int =
        content.filterIsInstance<ImageBlock>().single().expectedHeight

    @Test
    fun anImageNarrowerThanTheColumnIsMeasuredAtItsIntrinsicSize() {
        // 60x20 at density 2 = 120x40 px. Measured at the 800px column it would be 800x267.
        assertThat(paginateImage(60f, 20f).placedImageHeight()).isEqualTo(40)
    }

    @Test
    fun anImageWiderThanTheColumnIsMeasuredAtTheColumnWidth() {
        // 2000x1000 saturates to the 800px column, aspect intact: 800 x 400.
        assertThat(paginateImage(2000f, 1000f).placedImageHeight()).isEqualTo(400)
    }

    @Test
    fun anExplicitCssWidthWinsOverTheIntrinsicAttribute() {
        // 300dp CSS width at density 2 is 600px, aspect 20:60 preserved -> 200px tall. The 60-unit
        // intrinsic attribute is ignored entirely once a CSS width is present.
        assertThat(paginateImage(60f, 20f, cssWidth = 300.dp).placedImageHeight()).isEqualTo(200)
    }
}