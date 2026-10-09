package com.aryan.reader.shared.reader

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity item B1, the image base width — the **measure** half.
 *
 * `sharedNativeImageRenderSizePx` (see `SharedNativeImageStyleTest`) owns the render half. Both must
 * agree or the image is measured into one box and drawn into another, which is the same class of
 * bug as the tall-image height clamp this item already fixed.
 *
 * Android's `measureScaledImageSizePx` uses `intrinsicImageWidthPx` when there is no CSS `width` —
 * the `width` **attribute** read as dp, capped at the content width. Shared used the content width
 * outright, so a small inline logo, ornament or `<svg viewBox>` icon was measured full-column on iOS
 * while Android measured it at its intrinsic size.
 *
 * Lives in `commonTest` rather than the paginator's desktop suite so it runs on the mobile gates;
 * `measureImageSize` is `internal` in `commonMain` and needs no platform.
 */
class SharedMeasureImageSizeTest {

    private val settings = ReaderSettings(readingMode = ReaderReadingMode.PAGINATED)
    private val geometry = measuredPageGeometryFor(
        settings,
        ReaderViewportSpec(widthPx = 900, heightPx = 700)
    )

    private fun measure(intrinsicWidth: Float, intrinsicHeight: Float, cssWidth: Dp): Pair<Int, Int> =
        measureImageSize(
            SemanticImage(
                path = "ornament.png",
                altText = null,
                intrinsicWidth = intrinsicWidth,
                intrinsicHeight = intrinsicHeight,
                style = CssStyle(blockStyle = BlockStyle(width = cssWidth)),
                elementId = null,
                cfi = null
            ),
            geometry,
            settings,
            maxWidthPx = 804,
            density = Density(1f)
        )

    @Test
    fun `an image narrower than the page keeps its intrinsic width`() {
        // 60x20 on an 804px content column: 60 wide, not 804. This is the divergence. The height
        // comes back as 24 because `measureImageSize` floors measured height at 24px, which is
        // pre-existing and unrelated to the base width.
        assertEquals(60 to 24, measure(60f, 20f, Dp.Unspecified))
    }

    @Test
    fun `an image wider than the page still saturates to the content width`() {
        // The intrinsic cap in `intrinsicImageWidthPx` is what keeps wide images full-column.
        assertEquals(804 to 536, measure(1200f, 800f, Dp.Unspecified))
    }

    @Test
    fun `an explicit css width wins over the intrinsic attribute`() {
        assertEquals(300 to 100, measure(60f, 20f, 300.dp))
    }

    /**
     * The residual measure-vs-render disagreement for very short images: measure floors at 24px
     * and render does not, so a 20px-tall ornament reserves 4px more than it draws. Android floors
     * nowhere on the measure side (`measureScaledImageSizePx` has no minimum), so under Android-wins
     * this is a shared-side divergence, recorded here rather than silently left.
     */
    @Test
    fun `measure floors height at 24px where render does not`() {
        assertEquals(24, measure(60f, 20f, Dp.Unspecified).second)
        // At 30px the floor is not reached and the two halves agree exactly.
        assertEquals(30, measure(60f, 30f, Dp.Unspecified).second)
    }
}