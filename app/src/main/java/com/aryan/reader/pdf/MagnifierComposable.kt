/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import android.graphics.Rect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun MagnifierComposable(
    sourceBitmap: ImageBitmap,
    tiles: List<PdfTile>,
    currentScale: Float,
    magnifierCenterOnBitmap: Offset,
    modifier: Modifier = Modifier,
    magnifierWidth: Dp = 120.dp,
    magnifierHeight: Dp = 60.dp,
    zoomFactor: Float = 1.5f,
    selectionRectsInBitmapCoords: List<Rect>,
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

            Timber.d("Magnifier: START. scale=$currentScale, centerOnBitmap=$magnifierCenterOnBitmap")

            val relevantTile = if (currentScale > 1f) {
                tiles.find {
                    it.renderRect.contains(magnifierCenterOnBitmap.x.toInt(), magnifierCenterOnBitmap.y.toInt())
                }
            } else null

            if (relevantTile != null) {
                // --- HIGH-RES TILE PATH ---
                Timber.d("Magnifier: Using HIGH-RES TILE path.")
                Timber.d("Magnifier: Tile.renderRect=${relevantTile.renderRect}, Tile.bitmap.size=${relevantTile.bitmap.width}x${relevantTile.bitmap.height}")
                val bitmapToUse = relevantTile.bitmap.asImageBitmap()

                val tileBitmapWidth = relevantTile.bitmap.width.toFloat()
                val tileRenderRectWidth = relevantTile.renderRect.width().toFloat()

                val tileScale = if (tileRenderRectWidth > 0) {
                    tileBitmapWidth / tileRenderRectWidth
                } else {
                    1f
                }
                Timber.d("Magnifier: Using derived tileScale=$tileScale instead of parent's currentScale=$currentScale")


                val centerInTileBitmap = Offset(
                    x = (magnifierCenterOnBitmap.x - relevantTile.renderRect.left) * tileScale,
                    y = (magnifierCenterOnBitmap.y - relevantTile.renderRect.top) * tileScale
                )

                Timber.d("Magnifier: Calculated centerInTileBitmap=$centerInTileBitmap")

                val sourceRectWidth = magnifierWidthPx / zoomFactor
                val sourceRectHeight = magnifierHeightPx / zoomFactor
                Timber.d("Magnifier: Desired sourceRect size=${sourceRectWidth}x$sourceRectHeight")

                val srcLeft = (centerInTileBitmap.x - sourceRectWidth / 2f)
                val srcTop = (centerInTileBitmap.y - sourceRectHeight / 2f)
                Timber.d("Magnifier: Calculated source top-left=($srcLeft, $srcTop)")

                val clampedSrcLeft = srcLeft.coerceIn(0f, bitmapToUse.width.toFloat() - sourceRectWidth.coerceAtLeast(1f))
                val clampedSrcTop = srcTop.coerceIn(0f, bitmapToUse.height.toFloat() - sourceRectHeight.coerceAtLeast(1f))
                Timber.d("Magnifier: Clamped source top-left=($clampedSrcLeft, $clampedSrcTop)")

                val finalSrcLeftInt = clampedSrcLeft.roundToInt()
                val finalSrcTopInt = clampedSrcTop.roundToInt()

                val finalSrcWidthInt = (bitmapToUse.width - finalSrcLeftInt)
                    .coerceAtMost(sourceRectWidth.roundToInt()).coerceAtLeast(1)
                val finalSrcHeightInt = (bitmapToUse.height - finalSrcTopInt)
                    .coerceAtMost(sourceRectHeight.roundToInt()).coerceAtLeast(1)
                Timber.d("Magnifier: Final source rect to draw from tile: offset=($finalSrcLeftInt, $finalSrcTopInt), size=${finalSrcWidthInt}x$finalSrcHeightInt")

                if (finalSrcWidthInt <= 0 || finalSrcHeightInt <= 0 || finalSrcLeftInt >= bitmapToUse.width || finalSrcTopInt >= bitmapToUse.height) {
                    Timber.w("Magnifier: Final source rect is invalid, returning.")
                    return@Canvas
                }

                drawImage(
                    image = bitmapToUse,
                    srcOffset = IntOffset(finalSrcLeftInt, finalSrcTopInt),
                    srcSize = IntSize(finalSrcWidthInt, finalSrcHeightInt),
                    dstSize = IntSize(magnifierWidthPx.roundToInt(), magnifierHeightPx.roundToInt()),
                    colorFilter = colorFilter
                )

                selectionRectsInBitmapCoords.forEach { rectInBitmap ->
                    val translatedLeft = (rectInBitmap.left - relevantTile.renderRect.left) * tileScale
                    val translatedTop = (rectInBitmap.top - relevantTile.renderRect.top) * tileScale
                    val translatedRight = (rectInBitmap.right - relevantTile.renderRect.left) * tileScale
                    val translatedBottom = (rectInBitmap.bottom - relevantTile.renderRect.top) * tileScale

                    val finalLeft = translatedLeft - clampedSrcLeft
                    val finalTop = translatedTop - clampedSrcTop
                    val finalRight = translatedRight - clampedSrcLeft
                    val finalBottom = translatedBottom - clampedSrcTop

                    val magnifiedLeft = finalLeft * zoomFactor
                    val magnifiedTop = finalTop * zoomFactor
                    val magnifiedRight = finalRight * zoomFactor
                    val magnifiedBottom = finalBottom * zoomFactor

                    if (magnifiedRight > 0 && magnifiedLeft < magnifierWidthPx && magnifiedBottom > 0 && magnifiedTop < magnifierHeightPx) {
                        drawRect(
                            color = highlightColor,
                            topLeft = Offset(magnifiedLeft, magnifiedTop),
                            size = androidx.compose.ui.geometry.Size(
                                width = magnifiedRight - magnifiedLeft,
                                height = magnifiedBottom - magnifiedTop
                            )
                        )
                    }
                }

            } else {
                // --- LOW-RES / NO-ZOOM PATH ---
                Timber.d("Magnifier: Using LOW-RES (base bitmap) path.")
                val sourceRectWidth = magnifierWidthPx / zoomFactor
                val sourceRectHeight = magnifierHeightPx / zoomFactor
                Timber.d("Magnifier: Desired sourceRect size=${sourceRectWidth}x$sourceRectHeight")

                val srcLeft = (magnifierCenterOnBitmap.x - sourceRectWidth / 2f)
                val srcTop = (magnifierCenterOnBitmap.y - sourceRectHeight / 2f)
                Timber.d("Magnifier: Calculated source top-left=($srcLeft, $srcTop)")

                val clampedSrcLeft = srcLeft.coerceIn(0f, sourceBitmap.width.toFloat() - sourceRectWidth.coerceAtLeast(1f))
                val clampedSrcTop = srcTop.coerceIn(0f, sourceBitmap.height.toFloat() - sourceRectHeight.coerceAtLeast(1f))
                Timber.d("Magnifier: Clamped source top-left=($clampedSrcLeft, $clampedSrcTop)")

                val finalSrcLeftInt = clampedSrcLeft.roundToInt()
                val finalSrcTopInt = clampedSrcTop.roundToInt()

                val finalSrcWidthInt = (sourceBitmap.width - finalSrcLeftInt)
                    .coerceAtMost(sourceRectWidth.roundToInt()).coerceAtLeast(1)
                val finalSrcHeightInt = (sourceBitmap.height - finalSrcTopInt)
                    .coerceAtMost(sourceRectHeight.roundToInt()).coerceAtLeast(1)
                Timber.d("Magnifier: Final source rect to draw from base: offset=($finalSrcLeftInt, $finalSrcTopInt), size=${finalSrcWidthInt}x$finalSrcHeightInt")

                if (finalSrcWidthInt <= 0 || finalSrcHeightInt <= 0 || finalSrcLeftInt >= sourceBitmap.width || finalSrcTopInt >= sourceBitmap.height) {
                    Timber.w("Magnifier: Final source rect is invalid, returning.")
                    return@Canvas
                }

                drawImage(
                    image = sourceBitmap,
                    srcOffset = IntOffset(finalSrcLeftInt, finalSrcTopInt),
                    srcSize = IntSize(finalSrcWidthInt, finalSrcHeightInt),
                    dstSize = IntSize(magnifierWidthPx.roundToInt(), magnifierHeightPx.roundToInt()),
                    colorFilter = colorFilter
                )

                selectionRectsInBitmapCoords.forEach { rectInBitmap ->
                    val translatedLeft = rectInBitmap.left - clampedSrcLeft
                    val translatedTop = rectInBitmap.top - clampedSrcTop
                    val rectWidthInBitmap = rectInBitmap.width().toFloat()
                    val rectHeightInBitmap = rectInBitmap.height().toFloat()

                    val magnifiedLeft = translatedLeft * zoomFactor
                    val magnifiedTop = translatedTop * zoomFactor
                    val magnifiedWidth = rectWidthInBitmap * zoomFactor
                    val magnifiedHeight = rectHeightInBitmap * zoomFactor

                    if (magnifiedLeft + magnifiedWidth > 0 && magnifiedLeft < magnifierWidthPx &&
                        magnifiedTop + magnifiedHeight > 0 && magnifiedTop < magnifierHeightPx) {
                        drawRect(
                            color = highlightColor,
                            topLeft = Offset(magnifiedLeft, magnifiedTop),
                            size = androidx.compose.ui.geometry.Size(
                                width = magnifiedWidth,
                                height = magnifiedHeight
                            )
                        )
                    }
                }
            }
        }
    }
}