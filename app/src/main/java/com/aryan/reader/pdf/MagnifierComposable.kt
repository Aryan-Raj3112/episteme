/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import android.graphics.Rect
import android.graphics.RectF
import timber.log.Timber
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

internal data class MagnifierContentSource(
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

internal data class MagnifierSampleGeometry(
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val outputScaleX: Float,
    val outputScaleY: Float
)

internal fun calculateMagnifierSampleGeometry(
    centerContentX: Float,
    centerContentY: Float,
    contentSource: MagnifierContentSource,
    magnifierWidthPx: Float,
    magnifierHeightPx: Float,
    zoomFactor: Float
): MagnifierSampleGeometry? {
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

    return MagnifierSampleGeometry(
        srcLeft = srcLeftInt,
        srcTop = srcTopInt,
        srcWidth = srcWidthInt,
        srcHeight = srcHeightInt,
        outputScaleX = magnifierWidthPx / srcWidthInt,
        outputScaleY = magnifierHeightPx / srcHeightInt
    )
}

internal fun mapContentRectToMagnifier(
    contentRect: Rect,
    contentSource: MagnifierContentSource,
    sample: MagnifierSampleGeometry
): RectF {
    val sourceLeft = contentSource.sourceX(contentRect.left.toFloat())
    val sourceTop = contentSource.sourceY(contentRect.top.toFloat())
    val sourceRight = contentSource.sourceX(contentRect.right.toFloat())
    val sourceBottom = contentSource.sourceY(contentRect.bottom.toFloat())

    return RectF(
        (sourceLeft - sample.srcLeft) * sample.outputScaleX,
        (sourceTop - sample.srcTop) * sample.outputScaleY,
        (sourceRight - sample.srcLeft) * sample.outputScaleX,
        (sourceBottom - sample.srcTop) * sample.outputScaleY
    )
}

@Composable
fun MagnifierComposable(
    sourceBitmap: ImageBitmap,
    tiles: List<PdfTile>,
    currentScale: Float,
    magnifierCenterOnBitmap: Offset,
    contentWidthPx: Int = sourceBitmap.width,
    contentHeightPx: Int = sourceBitmap.height,
    modifier: Modifier = Modifier,
    magnifierWidth: Dp = 120.dp,
    magnifierHeight: Dp = 60.dp,
    zoomFactor: Float = 1.5f,
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

            Timber.d("Magnifier: START. scale=$currentScale, centerOnBitmap=$magnifierCenterOnBitmap")

            val relevantTile = if (currentScale > 1f) {
                tiles.find {
                    it.renderRect.contains(magnifierCenterOnBitmap.x.toInt(), magnifierCenterOnBitmap.y.toInt())
                }
            } else null

            val bitmapToUse: ImageBitmap
            val contentSource: MagnifierContentSource
            if (relevantTile != null && !relevantTile.bitmap.isRecycled) {
                Timber.d("Magnifier: Using HIGH-RES TILE path.")
                Timber.d("Magnifier: Tile.renderRect=${relevantTile.renderRect}, Tile.bitmap.size=${relevantTile.bitmap.width}x${relevantTile.bitmap.height}")
                bitmapToUse = relevantTile.bitmap.asImageBitmap()
                contentSource = MagnifierContentSource(
                    sourceWidth = bitmapToUse.width,
                    sourceHeight = bitmapToUse.height,
                    contentLeft = relevantTile.renderRect.left.toFloat(),
                    contentTop = relevantTile.renderRect.top.toFloat(),
                    contentWidth = relevantTile.renderRect.width().toFloat(),
                    contentHeight = relevantTile.renderRect.height().toFloat()
                )
            } else {
                Timber.d("Magnifier: Using LOW-RES (base bitmap) path.")
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
                Timber.w("Magnifier: Source geometry is invalid, returning.")
                return@Canvas
            }
            Timber.d("Magnifier: Final source rect offset=(${sample.srcLeft}, ${sample.srcTop}), size=${sample.srcWidth}x${sample.srcHeight}")

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
                val magnifierRect = mapContentRectToMagnifier(contentRect, contentSource, sample)
                if (magnifierRect.width() > 0f && magnifierRect.height() > 0f &&
                    magnifierRect.right > 0f && magnifierRect.left < magnifierWidthPx &&
                    magnifierRect.bottom > 0f && magnifierRect.top < magnifierHeightPx
                ) {
                    drawRect(
                        color = highlightColor,
                        topLeft = Offset(magnifierRect.left, magnifierRect.top),
                        size = Size(
                            width = magnifierRect.width(),
                            height = magnifierRect.height()
                        )
                    )
                }
            }
        }
    }
}
