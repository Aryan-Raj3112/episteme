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
package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.epub.EpubChapter

object PageCountEstimator {
    /**
     * Calculates an approximate page count instantly without rendering.
     */
    fun estimateChapterPageCount(
        chapter: EpubChapter,
        constraints: Constraints,
        textStyle: TextStyle,
        density: Density
    ): Int {
        val fontSizePx = with(density) { textStyle.fontSize.toPx() }
        val lineHeightPx = if (textStyle.lineHeight.isSpecified) {
            with(density) { textStyle.lineHeight.toPx() }
        } else {
            fontSizePx * 1.4f
        }
        return com.aryan.reader.shared.reader.estimateSharedChapterPageCount(
            htmlLength = chapter.htmlContent.length,
            viewportWidthPx = constraints.maxWidth,
            viewportHeightPx = constraints.maxHeight,
            fontSizePx = fontSizePx,
            lineHeightPx = lineHeightPx,
        )
    }
}
