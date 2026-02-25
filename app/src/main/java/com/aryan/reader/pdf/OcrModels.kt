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
package com.aryan.reader.pdf.ocr

import android.graphics.Rect

/**
 * Platform-agnostic OCR result models to decouple the app from Google ML Kit.
 */
data class OcrResult(
    val text: String,
    val textBlocks: List<OcrBlock>
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrLine>
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val elements: List<OcrElement>
)

data class OcrElement(
    val text: String,
    val boundingBox: Rect?,
    val symbols: List<OcrSymbol>
)

data class OcrSymbol(
    val text: String,
    val boundingBox: Rect?
)