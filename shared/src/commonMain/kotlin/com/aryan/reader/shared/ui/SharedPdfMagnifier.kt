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
import com.aryan.reader.shared.pdf.PdfZoomTileRequest
import kotlin.math.max
import kotlin.math.roundToInt

internal data class SharedPdfMagnifierContentSource(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float
) {
    val scaleX: Float
        get() = if (contentWidth > 0f) sourceWidth.toFloat() / contentWidth else 1f

    val scaleY: Float
        get() = if (contentHeight > 0f) sourceHeight.toFloat() / contentHeight else 1f

    fun sourceX(contentX: Float): Float = (contentX - contentLeft) * scaleX

    fun sourceY(contentY: Float): Float = (contentY - contentTop) * scaleY
}

internal data class SharedPdfMagnifierSampleGeometry(
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val outputScaleX: Float,
    val outputScaleY: Float
)

internal fun calculateSharedPdfMagnifierSampleGeometry(
    centerContentX: Float,
    centerContentY: Float,
    contentSource: SharedPdfMagnifierContentSource,
    magnifierWidthPx: Float,
    magnifierHeightPx: Float,
    zoomFactor: Float
): SharedPdfMagnifierSampleGeometry? {
    if (
        contentSource.sourceWidth <= 0 ||
        contentSource.sourceHeight <= 0 ||
        contentSource.contentWidth <= 0f ||
        contentSource.contentHeight <= 0f ||
        magnifierWidthPx <= 0f ||
        magnifierHeightPx <= 0f ||
        zoomFactor <= 0f
    ) {
        return null
    }

    val sourceCenterX = contentSource.sourceX(centerContentX)
    val sourceCenterY = contentSource.sourceY(centerContentY)
    val sourceRectWidth = (magnifierWidthPx / zoomFactor * contentSource.scaleX).coerceAtLeast(1f)
    val sourceRectHeight = (magnifierHeightPx / zoomFactor * contentSource.scaleY).coerceAtLeast(1f)

    val maxSrcLeft = max(0f, contentSource.sourceWidth.toFloat() - sourceRectWidth)
    val maxSrcTop = max(0f, contentSource.sourceHeight.toFloat() - sourceRectHeight)
    val srcLeft = (sourceCenterX - sourceRectWidth / 2f).coerceIn(0f, maxSrcLeft)
    val srcTop = (sourceCenterY - sourceRectHeight / 2f).coerceIn(0f, maxSrcTop)

    val srcLeftInt = srcLeft.roundToInt().coerceIn(0, contentSource.sourceWidth - 1)
    val srcTopInt = srcTop.roundToInt().coerceIn(0, contentSource.sourceHeight - 1)
    val srcWidthInt = (contentSource.sourceWidth - srcLeftInt)
        .coerceAtMost(sourceRectWidth.roundToInt().coerceAtLeast(1))
        .coerceAtLeast(1)
    val srcHeightInt = (contentSource.sourceHeight - srcTopInt)
        .coerceAtMost(sourceRectHeight.roundToInt().coerceAtLeast(1))
        .coerceAtLeast(1)

    return SharedPdfMagnifierSampleGeometry(
        srcLeft = srcLeftInt,
        srcTop = srcTopInt,
        srcWidth = srcWidthInt,
        srcHeight = srcHeightInt,
        outputScaleX = magnifierWidthPx / srcWidthInt,
        outputScaleY = magnifierHeightPx / srcHeightInt
    )
}

internal fun mapSharedPdfContentRectToMagnifier(
    contentRect: Rect,
    contentSource: SharedPdfMagnifierContentSource,
    sample: SharedPdfMagnifierSampleGeometry
): Rect {
    val sourceLeft = contentSource.sourceX(contentRect.left)
    val sourceTop = contentSource.sourceY(contentRect.top)
    val sourceRight = contentSource.sourceX(contentRect.right)
    val sourceBottom = contentSource.sourceY(contentRect.bottom)

    return Rect(
        left = (sourceLeft - sample.srcLeft) * sample.outputScaleX,
        top = (sourceTop - sample.srcTop) * sample.outputScaleY,
        right = (sourceRight - sample.srcLeft) * sample.outputScaleX,
        bottom = (sourceBottom - sample.srcTop) * sample.outputScaleY
    )
}

/** Finds the high-res tile containing [centerOnBitmap] at zoomed scale, if any. */
internal fun sharedPdfMagnifierTileRequest(
    requests: List<PdfZoomTileRequest>,
    centerOnBitmap: Offset,
    currentScale: Float,
    contentWidthPx: Int,
    contentHeightPx: Int
): PdfZoomTileRequest? {
    if (currentScale <= 1f) return null
    return requests.firstOrNull { request ->
        if (request.fullWidthPx <= 0 || request.fullHeightPx <= 0) return@firstOrNull false
        val scaleX = request.fullWidthPx.toFloat() / contentWidthPx.coerceAtLeast(1)
        val scaleY = request.fullHeightPx.toFloat() / contentHeightPx.coerceAtLeast(1)
        val tileX = centerOnBitmap.x * scaleX
        val tileY = centerOnBitmap.y * scaleY
        tileX >= request.leftPx && tileX < request.leftPx + request.widthPx &&
            tileY >= request.topPx && tileY < request.topPx + request.heightPx
    }
}

/**
 * The region a tile covers, expressed in the magnifier's content space (the
 * page's on-screen fit size — the canvas size at scale 1). Mirrors Android,
 * where tile `renderRect` coordinates live in the target-width space.
 */
internal fun sharedPdfMagnifierTileRectInContentSpace(
    request: PdfZoomTileRequest,
    contentWidthPx: Int,
    contentHeightPx: Int
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

@Composable
internal fun SharedPdfMagnifier(
    sourceBitmap: ImageBitmap,
    tiles: List<SharedMobilePdfTileRender>,
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
    colorFilter: ColorFilter? = null
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

            val tileRequest = sharedPdfMagnifierTileRequest(
                requests = tiles.map { it.request },
                centerOnBitmap = magnifierCenterOnBitmap,
                currentScale = currentScale,
                contentWidthPx = contentWidthPx,
                contentHeightPx = contentHeightPx
            )
            val tile = tileRequest?.let { request ->
                tiles.firstOrNull { it.request.id == request.id }
            }
            val bitmapToUse: ImageBitmap
            val contentSource: SharedPdfMagnifierContentSource
            if (tile != null) {
                val request = tile.request
                bitmapToUse = tile.bitmap
                val tileRect = sharedPdfMagnifierTileRectInContentSpace(
                    request = request,
                    contentWidthPx = contentWidthPx,
                    contentHeightPx = contentHeightPx
                )
                contentSource = SharedPdfMagnifierContentSource(
                    sourceWidth = bitmapToUse.width,
                    sourceHeight = bitmapToUse.height,
                    contentLeft = tileRect.left,
                    contentTop = tileRect.top,
                    contentWidth = tileRect.width,
                    contentHeight = tileRect.height
                )
            } else {
                bitmapToUse = sourceBitmap
                contentSource = SharedPdfMagnifierContentSource(
                    sourceWidth = sourceBitmap.width,
                    sourceHeight = sourceBitmap.height,
                    contentLeft = 0f,
                    contentTop = 0f,
                    contentWidth = contentWidthPx.toFloat(),
                    contentHeight = contentHeightPx.toFloat()
                )
            }

            val sample = calculateSharedPdfMagnifierSampleGeometry(
                centerContentX = magnifierCenterOnBitmap.x,
                centerContentY = magnifierCenterOnBitmap.y,
                contentSource = contentSource,
                magnifierWidthPx = magnifierWidthPx,
                magnifierHeightPx = magnifierHeightPx,
                zoomFactor = zoomFactor
            ) ?: return@Canvas

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
                val magnifierRect = mapSharedPdfContentRectToMagnifier(contentRect, contentSource, sample)
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
