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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.pdf.MagnifierTileSource
import com.aryan.reader.shared.ui.SharedPdfMagnifier
import timber.log.Timber

/**
 * Android's adapter onto the shared magnifier lens. The lens, its sampling geometry and its tile
 * hit-test all live in `shared` (`SharedPdfMagnifier` / `PdfMagnifierGeometry`); this file only
 * converts Android's types.
 *
 * `PdfTile.renderRect` is already in content space — it is built in `actualBitmapWidthPx` units,
 * which is the same value handed to the lens as `contentWidthPx` and the same space the magnifier
 * center is in — so tiles pass through unconverted. Shared's `PdfZoomTileRequest` names regions in
 * full-render space and is converted at its own call site instead.
 */
@Composable
fun MagnifierComposable(
    sourceBitmap: ImageBitmap,
    tiles: List<PdfTile>,
    currentScale: Float,
    magnifierCenterOnBitmap: androidx.compose.ui.geometry.Offset,
    contentWidthPx: Int = sourceBitmap.width,
    contentHeightPx: Int = sourceBitmap.height,
    modifier: Modifier = Modifier,
    magnifierWidth: Dp = 120.dp,
    magnifierHeight: Dp = 60.dp,
    zoomFactor: Float = 1.5f,
    selectionRectsInContentCoords: List<android.graphics.Rect>,
    highlightColor: Color,
    colorFilter: ColorFilter? = null
) {
    val magnifierTiles = tiles.mapNotNull { tile ->
        // A recycled bitmap must never be handed to drawImage; the shared lens has no way to know.
        if (tile.bitmap.isRecycled) return@mapNotNull null
        MagnifierTileSource(
            bitmap = tile.bitmap.asImageBitmap(),
            contentRect = Rect(
                left = tile.renderRect.left.toFloat(),
                top = tile.renderRect.top.toFloat(),
                right = tile.renderRect.right.toFloat(),
                bottom = tile.renderRect.bottom.toFloat(),
            )
        )
    }

    SharedPdfMagnifier(
        sourceBitmap = sourceBitmap,
        tiles = magnifierTiles,
        currentScale = currentScale,
        magnifierCenterOnBitmap = magnifierCenterOnBitmap,
        contentWidthPx = contentWidthPx,
        contentHeightPx = contentHeightPx,
        modifier = modifier,
        magnifierWidth = magnifierWidth,
        magnifierHeight = magnifierHeight,
        zoomFactor = zoomFactor,
        selectionRectsInContentCoords = selectionRectsInContentCoords.map { rect ->
            Rect(
                left = rect.left.toFloat(),
                top = rect.top.toFloat(),
                right = rect.right.toFloat(),
                bottom = rect.bottom.toFloat(),
            )
        },
        highlightColor = highlightColor,
        colorFilter = colorFilter,
        onDebug = { Timber.d(it) },
    )
}
