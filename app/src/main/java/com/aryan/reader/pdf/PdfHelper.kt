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

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aryan.reader.OcrEngine
import com.aryan.reader.R
import com.aryan.reader.pdf.ocr.OcrElement
import com.aryan.reader.pdf.ocr.OcrLine
import com.aryan.reader.pdf.ocr.OcrResult
import com.aryan.reader.pdf.ocr.OcrSymbol
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import timber.log.Timber
import java.util.UUID

enum class OcrLanguage(val displayName: String) {
    LATIN("English, Spanish, French, etc."),
    DEVANAGARI("Hindi, Marathi, Sanskrit + English"),
    CHINESE("Chinese + English"),
    JAPANESE("Japanese + English"),
    KOREAN("Korean + English")
}

internal data class OcrSymbolInfo(
    val symbol: OcrSymbol,
    val parentElement: OcrElement,
    val parentLine: OcrLine
)

enum class PdfHighlightColor(val color: Color) {
    YELLOW(Color(0xFFFBC02D)),
    GREEN(Color(0xFF388E3C)),
    BLUE(Color(0xFF1976D2)),
    RED(Color(0xFFD32F2F));
}

data class PdfUserHighlight(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val bounds: List<RectF>,
    val color: PdfHighlightColor,
    val text: String,
    val range: Pair<Int, Int>
)

internal data class CustomPdfMenuState(
    val selectedText: String,
    val anchorRect: Rect,
    val charRange: Pair<Int, Int>,
    val isExistingHighlight: Boolean = false,
    val highlightId: String? = null
)

internal enum class PdfSelectionMethod {
    PDFIUM, OCR
}

internal object OcrHelper {
    fun init(language: OcrLanguage) {
        OcrEngine.init(language)
    }

    suspend fun extractTextFromBitmap(
        bitmap: Bitmap,
        onModelDownloading: () -> Unit
    ): OcrResult? {
        return OcrEngine.extractTextFromBitmap(bitmap, onModelDownloading)
    }
}

internal suspend fun findWordBoundaries(
    textPage: PdfTextPageKt,
    initialCharIndex: Int,
    pageCharCount: Int
): Pair<Int, Int>? {
    if (initialCharIndex !in 0..<pageCharCount) return null
    val initialChar = textPage.textPageGetUnicode(initialCharIndex)
    if (!initialChar.isLetterOrDigit()) {
        Timber.d("Initial char '$initialChar' at index $initialCharIndex is not letter/digit.")
        return null
    }
    var wordStartIndex = initialCharIndex
    while (wordStartIndex > 0) {
        val char = textPage.textPageGetUnicode(wordStartIndex - 1)
        if (!char.isLetterOrDigit()) {
            break
        }
        wordStartIndex--
    }
    var wordEndIndex = initialCharIndex
    while (wordEndIndex < pageCharCount) {
        val char = textPage.textPageGetUnicode(wordEndIndex)
        if (!char.isLetterOrDigit()) {
            break
        }
        wordEndIndex++
    }
    return if (wordStartIndex < wordEndIndex) {
        Timber.d("Word boundaries: $wordStartIndex to $wordEndIndex (exclusive)")
        Pair(wordStartIndex, wordEndIndex)
    } else {
        Timber.w("Word boundary detection resulted in startIndex >= endIndex ($wordStartIndex >= $wordEndIndex)")
        null
    }
}

@Composable
internal fun PdfSelectionMenuPopup(
    menuState: CustomPdfMenuState,
    popupPositionProvider: PopupPositionProvider,
    onCopy: (String) -> Unit,
    onAiDefine: (String) -> Unit,
    onSelectAll: () -> Unit,
    onColorSelected: (PdfHighlightColor) -> Unit,
    onDelete: () -> Unit
) {
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = null,
        properties = PopupProperties(
            focusable = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                // Color Row
                Row(
                    modifier = Modifier
                        .padding(vertical = 12.dp, horizontal = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PdfHighlightColor.entries.forEach { colorEnum ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(32.dp)
                                .background(colorEnum.color, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    Timber.tag("PdfHighlightDebug").d("Color box clicked: $colorEnum")
                                    onColorSelected(colorEnum)
                                }
                        )
                    }
                }

                HorizontalDivider()

                // Delete Option (Only for existing)
                if (menuState.isExistingHighlight) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDelete() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                }

                // Standard Options
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Copy
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCopy(menuState.selectedText) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CopyAll, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Dictionary
                    if (menuState.selectedText.length <= 2000) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAiDefine(menuState.selectedText) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.dictionary),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Dictionary", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Select All (Only for new selection)
                    if (!menuState.isExistingHighlight) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectAll() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(id = R.drawable.select_all), contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Select All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun mergeRectsIntoLines(rects: List<Rect>): List<Rect> {
    if (rects.isEmpty()) return emptyList()

    val sortedRects = rects.sortedWith(compareBy({ it.top }, { it.left }))

    val mergedLines = mutableListOf<Rect>()
    var currentLineCombinedRect: Rect? = null

    for (rect in sortedRects) {
        if (currentLineCombinedRect == null) {
            currentLineCombinedRect = Rect(rect)
        } else {
            val isSameLine = (maxOf(currentLineCombinedRect.top, rect.top) <
                    minOf(currentLineCombinedRect.bottom, rect.bottom))

            if (isSameLine) {
                currentLineCombinedRect.union(rect)
            } else {
                mergedLines.add(currentLineCombinedRect)
                currentLineCombinedRect = Rect(rect)
            }
        }
    }

    currentLineCombinedRect?.let { mergedLines.add(it) }
    return mergedLines
}

internal fun findRectsForTextChunkInOcrVisual(
    visionText: OcrResult,
    textChunkToHighlight: String
): List<Rect> {
    if (textChunkToHighlight.isBlank()) return emptyList()

    val allOcrElements = visionText.textBlocks.flatMap { tb -> tb.lines.flatMap { l -> l.elements } }
    if (allOcrElements.isEmpty()) return emptyList()

    val targetWords = textChunkToHighlight.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (targetWords.isEmpty()) return emptyList()

    val matchedRects = mutableListOf<Rect>()

    for (i in 0 .. allOcrElements.size - targetWords.size) {
        var currentMatch = true
        val tempRects = mutableListOf<Rect>()
        var ocrTextCombined = ""

        for (j in targetWords.indices) {
            val ocrElement = allOcrElements[i + j]
            ocrTextCombined += ocrElement.text + " "
            if (!ocrElement.text.equals(targetWords[j], ignoreCase = true) &&
                !ocrElement.text.replace(Regex("[.,;:!?\"')$]"), "").equals(targetWords[j], ignoreCase = true) &&
                !targetWords[j].replace(Regex("[.,;:!?\"'(]$"), "").equals(ocrElement.text, ignoreCase = true)
            ) {
                currentMatch = false
                break
            }
            ocrElement.boundingBox?.let {
                tempRects.add(
                    Rect(
                        it.left,
                        it.top,
                        it.right,
                        it.bottom
                    )
                )
            }
        }

        if (currentMatch) {
            Timber.d("OCR Highlight Match: Found sequence for '$textChunkToHighlight' starting with '${allOcrElements[i].text}' -> Combined: $ocrTextCombined")
            matchedRects.addAll(tempRects)
            return matchedRects
        }
    }
    Timber.d("OCR Highlight No Match: Could not find sequence for '$textChunkToHighlight'")
    return emptyList()
}

internal data class ProcessedText(
    val cleanText: String,
    val indexMap: List<Int>
)

internal sealed class TtsHighlightData {
    data class Pdfium(val startIndex: Int, val length: Int) : TtsHighlightData()
    data class Ocr(val text: String) : TtsHighlightData()
}

internal fun preprocessTextForTts(rawText: String): ProcessedText {
    if (rawText.isBlank()) {
        return ProcessedText("", emptyList())
    }

    val cleanTextBuilder = StringBuilder(rawText.length)
    val indexMap = mutableListOf<Int>()

    rawText.forEachIndexed { index, char ->
        when (char) {
            '\n' -> {
                val lastChar = cleanTextBuilder.trimEnd().lastOrNull()
                if (lastChar != null && lastChar !in ".?!") {
                    if (cleanTextBuilder.isNotEmpty() && !cleanTextBuilder.last().isWhitespace()) {
                        cleanTextBuilder.append(' ')
                        indexMap.add(index)
                    }
                }
            }
            '\r' -> {
                // Ignore carriage returns completely
            }
            else -> {
                cleanTextBuilder.append(char)
                indexMap.add(index)
            }
        }
    }
    return ProcessedText(cleanTextBuilder.toString().trim(), indexMap)
}

internal fun mergePdfRectsIntoLines(rects: List<RectF>): List<RectF> {
    if (rects.isEmpty()) return emptyList()

    val normalized = rects.map { r ->
        floatArrayOf(
            minOf(r.left, r.right),
            minOf(r.top, r.bottom),
            maxOf(r.left, r.right),
            maxOf(r.top, r.bottom)
        )
    }

    val sorted = normalized.sortedWith(compareBy({ -it[3] }, { it[0] }))

    val merged = mutableListOf<FloatArray>()
    var current: FloatArray? = null

    for (r in sorted) {
        if (current == null) {
            current = r.clone()
        } else {
            val cMinY = current[1]
            val cMaxY = current[3]
            val rMinY = r[1]
            val rMaxY = r[3]

            val overlapHeight = minOf(cMaxY, rMaxY) - maxOf(cMinY, rMinY)
            val minHeight = minOf(cMaxY - cMinY, rMaxY - rMinY)

            if (overlapHeight > 0 && overlapHeight >= minHeight * 0.1f) {
                current[0] = minOf(current[0], r[0])
                current[1] = minOf(current[1], r[1])
                current[2] = maxOf(current[2], r[2])
                current[3] = maxOf(current[3], r[3])
            } else {
                merged.add(current)
                current = r.clone()
            }
        }
    }
    current?.let { merged.add(it) }

    return merged.map { m ->
        RectF(m[0], m[3], m[2], m[1])
    }
}