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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private class MenuActionItem(
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
    val label: String,
    val onClick: () -> Unit,
    val isError: Boolean = false
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
    val range: Pair<Int, Int>,
    val note: String? = null
)

internal data class CustomPdfMenuState(
    val selectedText: String,
    val anchorRect: Rect,
    val charRange: Pair<Int, Int>,
    val isExistingHighlight: Boolean = false,
    val highlightId: String? = null,
    val isComment: Boolean = false,
    val author: String? = null,
    val annotation: EmbeddedAnnotation? = null,
    val note: String? = null,
    val selectedColor: PdfHighlightColor? = null
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
    textPage: ReaderTextPage,
    initialCharIndex: Int,
    pageCharCount: Int
): Pair<Int, Int>? {
    if (initialCharIndex !in 0..<pageCharCount) return null
    val initialChar = textPage.textPageGetUnicode(initialCharIndex).toChar()
    if (!initialChar.isLetterOrDigit()) {
        Timber.d("Initial char '$initialChar' at index $initialCharIndex is not letter/digit.")
        return null
    }
    var wordStartIndex = initialCharIndex
    while (wordStartIndex > 0) {
        val char = textPage.textPageGetUnicode(wordStartIndex - 1).toChar()
        if (!char.isLetterOrDigit()) {
            break
        }
        wordStartIndex--
    }
    var wordEndIndex = initialCharIndex
    while (wordEndIndex < pageCharCount) {
        val char = textPage.textPageGetUnicode(wordEndIndex).toChar()
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
private fun CommentThread(replies: List<EmbeddedAnnotation>, depth: Int) {
    Timber.tag("PdfCommentDebug").v("Rendering CommentThread: Depth=$depth, ReplyCount=${replies.size}")
    replies.forEach { reply ->
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        CommentItem(author = reply.author, text = reply.contents ?: "", depth = depth)
        if (reply.replies.isNotEmpty()) {
            CommentThread(reply.replies, depth + 1)
        }
    }
}

@Composable
internal fun PdfSelectionMenuPopup(
    menuState: CustomPdfMenuState,
    popupPositionProvider: PopupPositionProvider,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
    onPaletteClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onAiDefine: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onColorSelected: (PdfHighlightColor) -> Unit,
    onDelete: () -> Unit,
    onTts: (() -> Unit)? = null,
    onNote: (() -> Unit)? = null
) {
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = if (menuState.isComment) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max)) {
                if (!menuState.note.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Note",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Note",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = menuState.note,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }

                if (menuState.isComment) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        CommentItem(author = menuState.author, text = menuState.selectedText, depth = 0)
                        menuState.annotation?.replies?.let { CommentThread(it, 1) }
                    }

                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            val fullText = buildString {
                                append("${menuState.author ?: "Unknown"}: ${menuState.selectedText}\n")
                                fun appendReplies(replies: List<EmbeddedAnnotation>, indent: String) {
                                    for (r in replies) {
                                        append("$indent${r.author ?: "Unknown"}: ${r.contents ?: ""}\n")
                                        appendReplies(r.replies, "$indent  ")
                                    }
                                }
                                menuState.annotation?.replies?.let { appendReplies(it, "  ") }
                            }.trimEnd()
                            onCopy(fullText)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CopyAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Thread")
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PdfHighlightColor.entries.forEach { colorEnum ->
                            val displayColor = customHighlightColors[colorEnum] ?: colorEnum.color
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp).size(32.dp)
                                    .background(displayColor, CircleShape).clip(CircleShape)
                                    .clickable {
                                        Timber.tag("PdfHighlightDebug")
                                            .d("Color box clicked: $colorEnum")
                                        onColorSelected(colorEnum)
                                    })
                        }
                        if (onPaletteClick != null) {
                            val rainbowColors = listOf(
                                Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Brush.sweepGradient(rainbowColors))
                                    .clickable { onPaletteClick() },
                                contentAlignment = Alignment.Center
                            ) {}
                        }
                    }

                    HorizontalDivider()

                    val actions = mutableListOf<MenuActionItem>()
                    actions.add(MenuActionItem(iconRes = R.drawable.copy, label = "Copy", onClick = { onCopy(menuState.selectedText) }))
                    if (onTts != null) {
                        actions.add(MenuActionItem(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = "Speak", onClick = onTts))
                    }
                    if (menuState.selectedText.length <= 2000) {
                        actions.add(MenuActionItem(iconRes = R.drawable.dictionary, label = "Dict", onClick = { onAiDefine(menuState.selectedText) }))
                        actions.add(MenuActionItem(iconRes = R.drawable.translate, label = "Translate", onClick = { onTranslate(menuState.selectedText) }))
                        actions.add(MenuActionItem(imageVector = Icons.Default.Search, label = "Search", onClick = { onSearch(menuState.selectedText) }))
                    }

                    if (onNote != null) {
                        val noteLabel = if (menuState.note.isNullOrBlank()) "Note" else "Edit"
                        actions.add(MenuActionItem(imageVector = Icons.Default.Edit, label = noteLabel, onClick = onNote))
                    }

                    if (!menuState.isExistingHighlight) {
                        actions.add(MenuActionItem(iconRes = R.drawable.select_all, label = "Select All", onClick = { onSelectAll() }))
                    }
                    if (menuState.isExistingHighlight) {
                        actions.add(MenuActionItem(imageVector = Icons.Default.Delete, label = "Remove", onClick = { onDelete() }, isError = true))
                    }

                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        actions.chunked(3).forEach { rowActions ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowActions.forEach { action ->
                                    val tint = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    Column(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .clickable { action.onClick() }
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (action.imageVector != null) {
                                            Icon(imageVector = action.imageVector, contentDescription = action.label, tint = tint, modifier = Modifier.size(24.dp))
                                        } else if (action.iconRes != null) {
                                            Icon(painter = painterResource(id = action.iconRes), contentDescription = action.label, tint = tint, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = action.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                                    }
                                }
                                repeat(3 - rowActions.size) {
                                    Spacer(modifier = Modifier.width(64.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(author: String?, text: String, depth: Int) {
    val indentSize = (depth * 16).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentSize, top = 4.dp, bottom = 4.dp)
    ) {
        if (depth > 0) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            if (!author.isNullOrBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (depth > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
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

@Composable
fun PdfHighlightColorRow(
    modifier: Modifier = Modifier,
    selectedColor: PdfHighlightColor? = null,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
    onColorSelect: (PdfHighlightColor) -> Unit,
    onPaletteClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PdfHighlightColor.entries.forEach { colorEnum ->
            val displayColor = customHighlightColors[colorEnum] ?: colorEnum.color
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(displayColor)
                    .clickable { onColorSelect(colorEnum) }
                    .border(
                        width = if (selectedColor == colorEnum) 3.dp else 1.dp,
                        color = if (selectedColor == colorEnum) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            ) {
                if (selectedColor == colorEnum) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (displayColor.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (onPaletteClick != null) {
            val rainbowColors = listOf(
                Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(rainbowColors))
                    .clickable { onPaletteClick() },
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotationBottomSheet(
    highlight: PdfUserHighlight,
    effectiveBg: Color,
    effectiveText: Color,
    customHighlightColors: Map<PdfHighlightColor, Color> = emptyMap(),
    onPaletteClick: (() -> Unit)? = null,
    onColorChange: (PdfHighlightColor) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onSearch: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteText by remember { mutableStateOf(highlight.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = effectiveBg,
        contentColor = effectiveText,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            val displayColor = customHighlightColors[highlight.color] ?: highlight.color.color

            PdfHighlightColorRow(
                selectedColor = highlight.color,
                customHighlightColors = customHighlightColors,
                onColorSelect = onColorChange,
                onPaletteClick = onPaletteClick,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Surface(
                color = displayColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, displayColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(displayColor))
                    Text(
                        text = "\"${highlight.text}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = effectiveText.copy(alpha = 0.9f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PdfBottomSheetToolButton(icon = R.drawable.copy, label = "Copy", effectiveText = effectiveText, onClick = onCopy)
                PdfBottomSheetToolButton(icon = R.drawable.dictionary, label = "Dict", effectiveText = effectiveText, onClick = onDictionary)
                PdfBottomSheetToolButton(icon = R.drawable.translate, label = "Translate", effectiveText = effectiveText, onClick = onTranslate)
                PdfBottomSheetToolButton(icon = R.drawable.search, label = "Search", effectiveText = effectiveText, onClick = onSearch)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("Add a note...", color = effectiveText.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = effectiveText.copy(alpha = 0.3f),
                    focusedTextColor = effectiveText,
                    unfocusedTextColor = effectiveText
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
                Button(
                    onClick = { onSave(noteText) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save Note")
                }
            }
        }
    }
}

@Composable
private fun PdfBottomSheetToolButton(
    icon: Int,
    label: String,
    effectiveText: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = effectiveText.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effectiveText.copy(alpha = 0.8f)
        )
    }
}