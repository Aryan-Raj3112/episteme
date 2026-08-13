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
import kotlin.math.roundToInt

internal fun mapContentRectToMagnifier(
    contentRect: Rect,
    contentSource: MagnifierContentSource,
    sample: MagnifierSampleGeometry
): RectF {
    val mapped = mapContentBoundsToMagnifier(
        contentRect.left.toFloat(), contentRect.top.toFloat(),
        contentRect.right.toFloat(), contentRect.bottom.toFloat(), contentSource, sample,
    )
    return RectF(mapped.left, mapped.top, mapped.right, mapped.bottom)
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
