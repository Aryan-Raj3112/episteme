package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import com.aryan.reader.pdf.magnifierTileIndexAt
import com.aryan.reader.shared.pdf.PdfZoomTileRequest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D11 claimed Android's magnifier tile hit-test and shared's disagree at `scale > 1`, because
 * shared converts the center from content space into tile space and Android does not. That claim
 * is wrong, and this test is the evidence.
 *
 * The two differ only in which space they name:
 * - Android builds `PdfTile.renderRect` in `actualBitmapWidthPx` space and passes that same value
 *   as `contentWidthPx`, so its raw comparison already has both operands in one space.
 * - Shared's `PdfZoomTileRequest.leftPx` is in *full render* space while its content space is the
 *   canvas fit size, so it scales the center up before comparing.
 *
 * Converting the tile into content space instead of the center into tile space is therefore an
 * identity in exact arithmetic. These tests pin that, so the hit-test can be single-sourced
 * without silently changing either platform's tile choice at a boundary.
 */
class MagnifierTileSpaceEquivalenceTest {

    /**
     * Guard against a future "optimization" that drops the conversion because it looks redundant
     * at `scale == 1`. With a real zoom render the tile grid is larger than the content box, so
     * skipping the conversion picks the tile one grid cell too far down-right. This is the concrete
     * case; the randomized test above is what proves it generalizes.
     */
    @Test
    fun `converting the tile matters once the tile grid outgrows the content box`() {
        // 2x2 grid over a 2400x3200 render, viewed at a 1200x1600 content size.
        val requests = listOf(
            request(0, 0, 0, 1200, 1600, 2400, 3200, 2f),
            request(1, 1200, 0, 1200, 1600, 2400, 3200, 2f),
            request(2, 0, 1600, 1200, 1600, 2400, 3200, 2f),
            request(3, 1200, 1600, 1200, 1600, 2400, 3200, 2f),
        )
        // Content (900, 1400) -> render (1800, 2800) -> bottom-right tile.
        val center = Offset(900f, 1400f)

        assertEquals(
            3,
            tileConvertedLookup(requests, center, 2f, 1200, 1600),
            "the converted form must find the bottom-right tile",
        )
        assertEquals(
            3,
            centerScaledLookup(requests, center, 2f, 1200, 1600),
            "and the scaled form must agree with it",
        )

        // Android skips the conversion because its renderRect is already content space; doing that
        // here, against a grid that is in render space, lands on the top-left tile instead.
        val unconverted = requests.first { req ->
            0f <= center.x && center.x < req.leftPx + req.widthPx &&
                0f <= center.y && center.y < req.topPx + req.heightPx
        }
        assertEquals(0, unconverted.id)
    }

    private fun request(
        id: Int,
        leftPx: Int,
        topPx: Int,
        widthPx: Int,
        heightPx: Int,
        fullWidthPx: Int,
        fullHeightPx: Int,
        renderScale: Float,
    ) = PdfZoomTileRequest(
        id = id,
        column = 0,
        row = 0,
        leftPx = leftPx,
        topPx = topPx,
        widthPx = widthPx,
        heightPx = heightPx,
        fullWidthPx = fullWidthPx,
        fullHeightPx = fullHeightPx,
        renderScale = renderScale,
    )

    /** Shared's current form: scale the center into tile space, compare there. */
    private fun centerScaledLookup(
        requests: List<PdfZoomTileRequest>,
        center: Offset,
        currentScale: Float,
        contentWidthPx: Int,
        contentHeightPx: Int,
    ): Int? {
        if (currentScale <= 1f) return null
        return requests.firstOrNull { req ->
            if (req.fullWidthPx <= 0 || req.fullHeightPx <= 0) return@firstOrNull false
            val scaleX = req.fullWidthPx.toFloat() / contentWidthPx.coerceAtLeast(1)
            val scaleY = req.fullHeightPx.toFloat() / contentHeightPx.coerceAtLeast(1)
            val tileX = center.x * scaleX
            val tileY = center.y * scaleY
            tileX >= req.leftPx && tileX < req.leftPx + req.widthPx &&
                tileY >= req.topPx && tileY < req.topPx + req.heightPx
        }?.id
    }

    /**
     * The form the single-sourced hit-test uses: convert each tile into content space, then
     * compare the center where it already is. This is Android's comparison verbatim.
     */
    private fun tileConvertedLookup(
        requests: List<PdfZoomTileRequest>,
        center: Offset,
        currentScale: Float,
        contentWidthPx: Int,
        contentHeightPx: Int,
    ): Int? = magnifierTileIndexAt(
        tileContentRects = requests.map { req ->
            pdfMagnifierTileContentRect(req, contentWidthPx, contentHeightPx)
        },
        centerX = center.x,
        centerY = center.y,
        currentScale = currentScale,
    )?.let(requests::get)?.id

    @Test
    fun `both formulations pick the same tile across randomized zoom grids`() {
        val random = Random(20261003)
        var compared = 0
        repeat(400) {
            val contentWidth = random.nextInt(600, 1600)
            val contentHeight = random.nextInt(800, 2200)
            val renderScale = random.nextFloat() * 2f + 1f
            val fullWidth = (contentWidth * renderScale).toInt().coerceAtLeast(1)
            val fullHeight = (contentHeight * renderScale).toInt().coerceAtLeast(1)
            val tileSize = random.nextInt(120, 900)

            val columns = ((fullWidth + tileSize - 1) / tileSize).coerceAtLeast(1)
            val rows = ((fullHeight + tileSize - 1) / tileSize).coerceAtLeast(1)
            val requests = buildList {
                for (row in 0 until rows) for (col in 0 until columns) {
                    val left = col * tileSize
                    val top = row * tileSize
                    add(
                        request(
                            id = row * columns + col,
                            leftPx = left,
                            topPx = top,
                            widthPx = minOf(tileSize, fullWidth - left),
                            heightPx = minOf(tileSize, fullHeight - top),
                            fullWidthPx = fullWidth,
                            fullHeightPx = fullHeight,
                            renderScale = renderScale,
                        )
                    )
                }
            }

            // Include centers on and around tile seams, where the two forms could diverge.
            val centerX = if (random.nextInt(4) == 0) {
                (random.nextInt(columns) * tileSize / renderScale).toFloat()
            } else {
                random.nextFloat() * contentWidth
            }
            val centerY = if (random.nextInt(4) == 0) {
                (random.nextInt(rows) * tileSize / renderScale).toFloat()
            } else {
                random.nextFloat() * contentHeight
            }

            val scaled = centerScaledLookup(
                requests, Offset(centerX, centerY), 2f, contentWidth, contentHeight,
            )
            val converted = tileConvertedLookup(
                requests, Offset(centerX, centerY), 2f, contentWidth, contentHeight,
            )
            assertEquals(
                scaled,
                converted,
                "diverged at content=(${contentWidth}x$contentHeight) render=$renderScale " +
                    "tile=$tileSize center=($centerX, $centerY)",
            )
            compared++
        }
        assertEquals(400, compared)
    }

    /**
     * Android truncates the float center with `toInt()` before its `Rect.contains` check, so the
     * single-sourced form has to truncate identically to stay byte-for-byte equivalent on Android.
     * A center just below a seam must therefore resolve to the same tile it does today.
     */
    @Test
    fun `truncating the center matches Android's Rect contains`() {
        val requests = listOf(
            request(0, 0, 0, 100, 100, 200, 200, 2f),
            request(1, 100, 0, 100, 100, 200, 200, 2f),
        )
        // 99.9 truncates to 99 -> tile 0. 100.0 -> tile 1.
        assertEquals(
            0,
            tileConvertedLookup(requests, Offset(99.9f, 50f), 2f, 200, 200),
        )
        assertEquals(
            1,
            tileConvertedLookup(requests, Offset(100f, 50f), 2f, 200, 200),
        )
    }

    /**
     * The residual difference between the two forms is float rounding in the content rect, which
     * can move a seam by well under a pixel. Bound it so a future refactor cannot quietly widen it.
     */
    @Test
    fun `content rect conversion is within a subpixel of the exact scale factor`() {
        val random = Random(4242)
        repeat(500) {
            val full = random.nextInt(800, 6000)
            val content = random.nextInt(200, 1800)
            val leftPx = random.nextInt(0, full)
            val widthPx = random.nextInt(1, (full - leftPx).coerceAtLeast(1))
            val rect = pdfMagnifierTileContentRect(
                request = request(0, leftPx, 0, widthPx, 100, full, 100, 2f),
                contentWidthPx = content,
                contentHeightPx = 100,
            )
            val exact = leftPx.toFloat() * content / full
            assertTrue(
                abs(rect.left - exact) <= content.toFloat() / full + 1e-3f,
                "left ${rect.left} vs exact $exact (full=$full content=$content)",
            )
        }
    }
}
