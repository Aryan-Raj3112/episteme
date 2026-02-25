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
package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp


fun parseCssDimensionToTextUnit(
    value: String,
    containerWidthPx: Int,
    density: Float
): TextUnit {
    if (density <= 0) return TextUnit.Unspecified
    val sanitizedValue = value.trim().lowercase()
    return when {
        sanitizedValue.endsWith("rem") -> sanitizedValue.removeSuffix("rem").toFloatOrNull()?.em ?: TextUnit.Unspecified
        sanitizedValue.endsWith("em") -> sanitizedValue.removeSuffix("em").toFloatOrNull()?.em ?: TextUnit.Unspecified
        sanitizedValue.endsWith("px") -> {
            val px = sanitizedValue.removeSuffix("px").toFloatOrNull() ?: 0f
            (px / density).sp
        }
        sanitizedValue.endsWith("pt") -> {
            val pt = sanitizedValue.removeSuffix("pt").toFloatOrNull() ?: 0f
            val px = pt * (4f / 3f)
            (px / density).sp
        }
        sanitizedValue.endsWith("%") -> {
            val percentage = sanitizedValue.removeSuffix("%").toFloatOrNull() ?: 0f
            if (containerWidthPx > 0) {
                val px = (percentage / 100f) * containerWidthPx
                (px / density).sp
            } else {
                TextUnit.Unspecified
            }
        }
        else -> TextUnit.Unspecified
    }
}

fun parseCssSizeToDp(
    value: String,
    baseFontSizeSp: Float,
    density: Float,
    containerWidthPx: Int
): Dp {
    if (density <= 0) return 0.dp
    val sanitizedValue = value.trim().lowercase()

    return when {
        sanitizedValue.endsWith("px") -> {
            val px = sanitizedValue.removeSuffix("px").toFloatOrNull() ?: 0f
            (px / density).dp
        }
        sanitizedValue.endsWith("rem") -> {
            val rem = sanitizedValue.removeSuffix("rem").toFloatOrNull() ?: 0f
            (rem * baseFontSizeSp).dp
        }
        sanitizedValue.endsWith("em") -> {
            val em = sanitizedValue.removeSuffix("em").toFloatOrNull() ?: 0f
            (em * baseFontSizeSp).dp
        }
        sanitizedValue.endsWith("pt") -> {
            val pt = sanitizedValue.removeSuffix("pt").toFloatOrNull() ?: 0f
            val px = pt * (4f / 3f)
            (px / density).dp
        }
        sanitizedValue.endsWith("%") -> {
            val percentage = sanitizedValue.removeSuffix("%").toFloatOrNull() ?: 0f
            if (containerWidthPx > 0) {
                val px = (percentage / 100f) * containerWidthPx
                (px / density).dp
            } else {
                0.dp
            }
        }
        else -> 0.dp
    }
}