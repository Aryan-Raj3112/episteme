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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Bitmap-sampling magnifier lens for native reader text selection. Android's framework
 * magnifier has no Compose Multiplatform equivalent on iOS, so the lens samples the captured
 * reader content bitmap around [magnifierCenter] (root/reader coordinates, matching the
 * capture layer's coordinate space) and renders it zoomed inside a rounded lens.
 *
 * Android benchmark metrics for the EPUB readers: 140×48dp lens, zoom 1.5, corner radius
 * 24dp, elevation 4dp (framework magnifier samples the composited UI at the source center).
 */
@Composable
internal fun SharedTextMagnifier(
    sourceBitmap: ImageBitmap,
    magnifierCenter: Offset,
    modifier: Modifier = Modifier,
    magnifierWidth: Dp = 140.dp,
    magnifierHeight: Dp = 48.dp,
    zoomFactor: Float = 1.5f,
    cornerRadius: Dp = 24.dp
) {
    val lensShape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .width(magnifierWidth)
            .height(magnifierHeight)
            .shadow(4.dp, lensShape)
            .clip(lensShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lensWidthPx = size.width
            val lensHeightPx = size.height
            if (lensWidthPx <= 0f || lensHeightPx <= 0f || zoomFactor <= 0f) return@Canvas
            val sample = calculateSharedPdfMagnifierSampleGeometry(
                centerContentX = magnifierCenter.x,
                centerContentY = magnifierCenter.y,
                contentSource = SharedPdfMagnifierContentSource(
                    sourceWidth = sourceBitmap.width,
                    sourceHeight = sourceBitmap.height,
                    contentLeft = 0f,
                    contentTop = 0f,
                    contentWidth = sourceBitmap.width.toFloat(),
                    contentHeight = sourceBitmap.height.toFloat()
                ),
                magnifierWidthPx = lensWidthPx,
                magnifierHeightPx = lensHeightPx,
                zoomFactor = zoomFactor
            ) ?: return@Canvas
            drawImage(
                image = sourceBitmap,
                srcOffset = IntOffset(sample.srcLeft, sample.srcTop),
                srcSize = IntSize(sample.srcWidth, sample.srcHeight),
                dstSize = IntSize(
                    lensWidthPx.roundToInt().coerceAtLeast(1),
                    lensHeightPx.roundToInt().coerceAtLeast(1)
                )
            )
        }
    }
}