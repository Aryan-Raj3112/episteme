package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aryan.reader.pdf.MagnifierContentSource
import com.aryan.reader.pdf.MagnifierTileSource
import com.aryan.reader.pdf.calculateMagnifierSampleGeometry
import com.aryan.reader.pdf.magnifierTileIndexAt
import com.aryan.reader.pdf.mapContentBoundsToMagnifier
import com.aryan.reader.shared.pdf.PdfZoomTileRequest
import kotlin.math.roundToInt

/**
 * The region a tile covers, expressed in the magnifier's content space (the page's on-screen fit
 * size — the canvas size at scale 1).
 *
 * Shared's tile requests name regions in *full render* space (`PdfZoomTileRequest.leftPx`), so
 * they are divided down here on the way into the magnifier. Android's `PdfTile.renderRect` is
 * already in content space and skips this, which is why the conversion lives at the call site
 * rather than inside the composable.
 */
fun pdfMagnifierTileContentRect(
    request: PdfZoomTileRequest,
    contentWidthPx: Int,
    contentHeightPx: Int,
): Rect {
    val scaleX = contentWidthPx.toFloat() / request.fullWidthPx.coerceAtLeast(1)
    val scaleY = contentHeightPx.toFloat() / request.fullHeightPx.coerceAtLeast(1)
    return Rect(
        left = request.leftPx * scaleX,
        top = request.topPx * scaleY,
        right = (request.leftPx + request.widthPx) * scaleX,
        bottom = (request.topPx + request.heightPx) * scaleY
    )
}

/**
 * Bitmap-sampling magnifier lens for PDF text selection, shared by both hosts.
 *
 * Android benchmark metrics: 120x60dp lens at zoom 1.5 for the PDF viewer. [tiles] are consulted
 * only above base scale so a freshly-loaded page samples its full bitmap rather than a single
 * high-res tile; pass an empty list to always use [sourceBitmap].
 *
 * [onDebug] exists because Android's magnifier logs its tile/sample choice per frame. Shared has
 * no logging dependency, so the host supplies the sink.
 */
@Composable
fun SharedPdfMagnifier(
    sourceBitmap: ImageBitmap,
    tiles: List<MagnifierTileSource>,
    currentScale: Float,
    magnifierCenterOnBitmap: Offset,
    contentWidthPx: Int = sourceBitmap.width,
    contentHeightPx: Int = sourceBitmap.height,
    modifier: Modifier = Modifier,
    magnifierWidth: Dp = 120.dp,
    magnifierHeight: Dp = 60.dp,
    zoomFactor: Float = 2f,
    selectionRectsInContentCoords: List<Rect>,
    highlightColor: Color,
    colorFilter: ColorFilter? = null,
    onDebug: ((String) -> Unit)? = null,
) {
    val stadiumShape = RoundedCornerShape(magnifierHeight / 2)

    Box(
        modifier = modifier
            .width(magnifierWidth)
            .height(magnifierHeight)
            .shadow(4.dp, stadiumShape)
            .clip(stadiumShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val magnifierWidthPx = size.width
            val magnifierHeightPx = size.height
            if (magnifierWidthPx <= 0f || magnifierHeightPx <= 0f || zoomFactor <= 0f) {
                return@Canvas
            }

            onDebug?.invoke(
                "Magnifier: START. scale=$currentScale, centerOnBitmap=$magnifierCenterOnBitmap"
            )

            val tile = magnifierTileIndexAt(
                tileContentRects = tiles.map { it.contentRect },
                centerX = magnifierCenterOnBitmap.x,
                centerY = magnifierCenterOnBitmap.y,
                currentScale = currentScale,
            )?.let(tiles::get)

            val bitmapToUse: ImageBitmap
            val contentSource: MagnifierContentSource
            if (tile != null) {
                onDebug?.invoke("Magnifier: Using HIGH-RES TILE path.")
                bitmapToUse = tile.bitmap
                contentSource = MagnifierContentSource(
                    sourceWidth = bitmapToUse.width,
                    sourceHeight = bitmapToUse.height,
                    contentLeft = tile.contentRect.left,
                    contentTop = tile.contentRect.top,
                    contentWidth = tile.contentRect.width,
                    contentHeight = tile.contentRect.height
                )
            } else {
                onDebug?.invoke("Magnifier: Using LOW-RES (base bitmap) path.")
                bitmapToUse = sourceBitmap
                contentSource = MagnifierContentSource(
                    sourceWidth = sourceBitmap.width,
                    sourceHeight = sourceBitmap.height,
                    contentLeft = 0f,
                    contentTop = 0f,
                    contentWidth = contentWidthPx.toFloat(),
                    contentHeight = contentHeightPx.toFloat()
                )
            }

            val sample = calculateMagnifierSampleGeometry(
                centerContentX = magnifierCenterOnBitmap.x,
                centerContentY = magnifierCenterOnBitmap.y,
                contentSource = contentSource,
                magnifierWidthPx = magnifierWidthPx,
                magnifierHeightPx = magnifierHeightPx,
                zoomFactor = zoomFactor
            ) ?: run {
                onDebug?.invoke("Magnifier: Source geometry is invalid, returning.")
                return@Canvas
            }
            onDebug?.invoke(
                "Magnifier: Final source rect offset=(${sample.srcLeft}, ${sample.srcTop}), " +
                    "size=${sample.srcWidth}x${sample.srcHeight}"
            )

            drawImage(
                image = bitmapToUse,
                srcOffset = IntOffset(sample.srcLeft, sample.srcTop),
                srcSize = IntSize(sample.srcWidth, sample.srcHeight),
                dstSize = IntSize(
                    magnifierWidthPx.roundToInt().coerceAtLeast(1),
                    magnifierHeightPx.roundToInt().coerceAtLeast(1)
                ),
                colorFilter = colorFilter
            )

            selectionRectsInContentCoords.forEach { contentRect ->
                val magnifierRect = mapContentBoundsToMagnifier(
                    left = contentRect.left,
                    top = contentRect.top,
                    right = contentRect.right,
                    bottom = contentRect.bottom,
                    contentSource = contentSource,
                    sample = sample
                )
                if (
                    magnifierRect.width > 0f && magnifierRect.height > 0f &&
                    magnifierRect.right > 0f && magnifierRect.left < magnifierWidthPx &&
                    magnifierRect.bottom > 0f && magnifierRect.top < magnifierHeightPx
                ) {
                    drawRect(
                        color = highlightColor,
                        topLeft = magnifierRect.topLeft,
                        size = magnifierRect.size
                    )
                }
            }
        }
    }
}
