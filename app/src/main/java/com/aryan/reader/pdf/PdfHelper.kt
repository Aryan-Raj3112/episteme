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

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aryan.reader.countWords
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.aryan.reader.OcrEngine
import com.aryan.reader.pdf.ocr.OcrElement
import com.aryan.reader.pdf.ocr.OcrLine
import com.aryan.reader.pdf.ocr.OcrResult
import com.aryan.reader.pdf.ocr.OcrSymbol

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

internal data class CustomPdfMenuState(
    val selectedText: String,
    val anchorRect: Rect,
    val charRange: Pair<Int, Int>
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
    isProUser: Boolean,
    onShowUpsellDialog: () -> Unit,
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
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { onCopy(menuState.selectedText) }) {
                    Text("Copy")
                }
                if (menuState.selectedText.length <= 2000) {
                    TextButton(onClick = {
                        if (isProUser || countWords(menuState.selectedText) <= 1) {
                            onAiDefine(menuState.selectedText)
                        } else {
                            onShowUpsellDialog()
                        }
                    }) {
                        Text("Dictionary")
                    }
                }
                TextButton(onClick = onSelectAll) {
                    Text("Select All")
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