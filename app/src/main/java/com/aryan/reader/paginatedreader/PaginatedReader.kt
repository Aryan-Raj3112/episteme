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
// PaginatedReader.kt
package com.aryan.reader.paginatedreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest.Builder
import com.aryan.reader.R
import com.aryan.reader.countWords
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.PaletteManagerDialog
import com.aryan.reader.epubreader.ReaderTextAlign
import com.aryan.reader.epubreader.SpectrumButton
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class PaginatedSelection(
    val blockIndex: Int,
    val baseCfi: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val rect: Rect
)

private class SmartPopupPositionProvider(
    private val contentRect: Rect, private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val padding = with(density) { 8.dp.roundToPx() }
        val popupWidth = popupContentSize.width
        val popupHeight = popupContentSize.height

        var x = (contentRect.center.x - popupWidth / 2).toInt()
        x = x.coerceIn(0, windowSize.width - popupWidth)

        val topY = (contentRect.top - popupHeight - padding).toInt()
        val bottomY = (contentRect.bottom + padding).toInt()

        var y = topY
        if (y < 0) {
            y = if (bottomY + popupHeight <= windowSize.height) {
                bottomY
            } else {
                val spaceTop = contentRect.top
                val spaceBottom = windowSize.height - contentRect.bottom
                if (spaceBottom > spaceTop) {
                    bottomY.coerceAtMost(windowSize.height - popupHeight)
                } else {
                    topY.coerceAtLeast(0)
                }
            }
        }

        return IntOffset(x, y)
    }
}

internal object CfiUtils {
    fun compare(cfi1: String, cfi2: String): Int {
        val path1 = cfi1.split(':').first()
        val path2 = cfi2.split(':').first()

        val parts1 = path1.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val parts2 = path2.split('/').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

        val length = minOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val cmp = parts1[i].compareTo(parts2[i])
            if (cmp != 0) return cmp
        }

        if (parts1.size != parts2.size) {
            return parts1.size.compareTo(parts2.size)
        }

        val offset1 = cfi1.substringAfter(':', "0").toIntOrNull() ?: 0
        val offset2 = cfi2.substringAfter(':', "0").toIntOrNull() ?: 0
        return offset1.compareTo(offset2)
    }

    fun getPath(cfi: String): String = cfi.split(':').first()
    fun getOffset(cfi: String): Int = cfi.substringAfter(':', "0").toIntOrNull() ?: 0
}

private fun highlightQueryInText(
    text: AnnotatedString, query: String, highlightColor: Color
): AnnotatedString {
    if (query.length < 3) return text

    return buildAnnotatedString {
        append(text)
        val textString = text.text
        var startIndex = 0
        while (startIndex < textString.length) {
            val index = textString.indexOf(query, startIndex, ignoreCase = true)
            if (index == -1) break
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = index,
                end = index + query.length
            )
            startIndex = index + query.length
        }
    }
}

@Composable
private fun WrappingContentLayout(
    block: WrappingContentBlock,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    searchQuery: String,
    ttsHighlightInfo: TtsHighlightInfo?,
    searchHighlightColor: Color,
    ttsHighlightColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val fullText = remember(block.paragraphsToWrap, searchQuery, ttsHighlightInfo) {
        buildAnnotatedString {
            block.paragraphsToWrap.forEachIndexed { index, p ->
                val searchHighlighted =
                    highlightQueryInText(p.content, searchQuery, searchHighlightColor)
                val finalContent = if (ttsHighlightInfo != null && p.cfi == ttsHighlightInfo.cfi) {
                    buildAnnotatedString {
                        append(searchHighlighted)
                        val blockStartAbs = p.startCharOffsetInSource
                        val blockEndAbs = p.startCharOffsetInSource + searchHighlighted.length
                        val highlightStartAbs = ttsHighlightInfo.offset
                        val highlightEndAbs = ttsHighlightInfo.offset + ttsHighlightInfo.text.length
                        val intersectionStartAbs = maxOf(blockStartAbs, highlightStartAbs)
                        val intersectionEndAbs = minOf(blockEndAbs, highlightEndAbs)

                        if (intersectionStartAbs < intersectionEndAbs) {
                            val highlightStartRelative = intersectionStartAbs - blockStartAbs
                            val highlightEndRelative = intersectionEndAbs - blockStartAbs
                            addStyle(
                                style = SpanStyle(
                                    background = ttsHighlightColor
                                ), start = highlightStartRelative, end = highlightEndRelative
                            )
                        }
                    }
                } else {
                    searchHighlighted
                }
                append(finalContent)
                if (index < block.paragraphsToWrap.lastIndex) append("\n\n")
            }
        }
    }
    val (paragraphStartOffsets, paragraphEndOffsetMap) = remember(block.paragraphsToWrap) {
        val starts = mutableSetOf<Int>()
        val endMap = mutableMapOf<Int, Int>()
        var currentOffset = 0
        block.paragraphsToWrap.forEachIndexed { index, p ->
            starts.add(currentOffset)
            currentOffset += p.content.length
            endMap[currentOffset - 1] = index
            if (index < block.paragraphsToWrap.lastIndex) {
                currentOffset += 2
            }
        }
        starts to endMap
    }
    val density = LocalDensity.current
    var textLayouts by remember {
        mutableStateOf<List<Pair<TextLayoutResult, Offset>>>(emptyList())
    }
    var totalHeight by remember { mutableIntStateOf(0) }

    Layout(content = {
        AsyncImage(
            model = Builder(LocalContext.current).data(File(block.floatedImage.path)).build(),
            contentDescription = block.floatedImage.altText,
            contentScale = ContentScale.Fit
        )
    }, modifier = modifier.drawBehind {
        textLayouts.forEach { (layout, offset) ->
            drawText(layout, topLeft = offset)
        }
    }) { measurables, constraints ->
        val (imageRenderWidthPx, imageRenderHeightPx) = run {
            val imageStyle = block.floatedImage.style
            val intrinsicWidth = block.floatedImage.intrinsicWidth
            val intrinsicHeight = block.floatedImage.intrinsicHeight

            if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f) {
                0f to 0f
            } else {
                val aspectRatio = intrinsicHeight / intrinsicWidth
                val renderWidth = with(density) {
                    var w = intrinsicWidth

                    if (imageStyle.width != Dp.Unspecified) {
                        w = imageStyle.width.toPx()
                    }

                    if (imageStyle.maxWidth != Dp.Unspecified) {
                        w = w.coerceAtMost(imageStyle.maxWidth.toPx())
                    }

                    w.coerceAtMost(constraints.maxWidth.toFloat())
                }
                renderWidth to (renderWidth * aspectRatio)
            }
        }

        val imagePlacable = if (imageRenderWidthPx > 0 && imageRenderHeightPx > 0) {
            measurables.first().measure(
                Constraints.fixed(
                    imageRenderWidthPx.roundToInt(), imageRenderHeightPx.roundToInt()
                )
            )
        } else {
            null
        }

        val effectiveImageWidth = imagePlacable?.width ?: 0
        val effectiveImageHeight = imagePlacable?.height ?: 0

        var currentY = 0f
        var textOffset = 0
        val layouts = mutableListOf<Pair<TextLayoutResult, Offset>>()

        while (textOffset < fullText.length) {
            val isBesideImage = currentY < effectiveImageHeight
            val floatLeft = block.floatedImage.style.float == "left"

            val currentMaxWidth = if (isBesideImage) {
                (constraints.maxWidth - effectiveImageWidth).coerceAtLeast(0)
            } else {
                constraints.maxWidth
            }

            if (currentMaxWidth <= 0) break

            val lineConstraints = constraints.copy(minWidth = 0, maxWidth = currentMaxWidth)
            val remainingText = fullText.subSequence(textOffset, fullText.length)

            val styleForMeasure =
                remainingText.spanStyles.firstOrNull { it.item.fontFamily != null }?.item?.fontFamily?.let {
                    textStyle.copy(fontFamily = it)
                } ?: textStyle

            val layoutResult = textMeasurer.measure(
                remainingText, style = styleForMeasure, constraints = lineConstraints
            )

            val firstLineEndOffset = layoutResult.getLineEnd(0, visibleEnd = true)
            if (firstLineEndOffset == 0 && remainingText.isNotEmpty()) {
                textOffset++
                continue
            }
            if (firstLineEndOffset == 0) break
            val lineText = remainingText.subSequence(0, firstLineEndOffset)
            val isStartOfParagraph = paragraphStartOffsets.contains(textOffset)
            val finalLineText = if (isStartOfParagraph) {
                lineText
            } else {
                val stylesWithIndent =
                    lineText.paragraphStyles.filter { it.item.textIndent != null }
                if (stylesWithIndent.isNotEmpty()) {
                    buildAnnotatedString {
                        append(lineText)
                        stylesWithIndent.forEach {
                            addStyle(
                                it.item.copy(textIndent = TextIndent(0.sp, 0.sp)), it.start, it.end
                            )
                        }
                    }
                } else {
                    lineText
                }
            }

            val lineLayout = textMeasurer.measure(
                finalLineText, style = styleForMeasure, constraints = lineConstraints
            )
            val xOffset = if (isBesideImage && floatLeft) effectiveImageWidth.toFloat() else 0f

            layouts.add(lineLayout to Offset(xOffset, currentY))

            currentY += lineLayout.size.height
            val endOfLineVisibleCharIndex = textOffset + firstLineEndOffset - 1
            val paraIndex = paragraphEndOffsetMap[endOfLineVisibleCharIndex]

            if (paraIndex != null && paraIndex < block.paragraphsToWrap.lastIndex) {
                val currentPara = block.paragraphsToWrap[paraIndex]
                val nextPara = block.paragraphsToWrap[paraIndex + 1]

                val gap = with(density) {
                    val marginBottom = currentPara.style.margin.bottom.toPx()
                    val marginTop = nextPara.style.margin.top.toPx()
                    maxOf(marginBottom, marginTop)
                }
                currentY += gap
            }
            textOffset += firstLineEndOffset
            while (textOffset < fullText.length && fullText[textOffset].isWhitespace()) {
                textOffset++
            }
        }
        textLayouts = layouts
        totalHeight = maxOf(currentY, effectiveImageHeight.toFloat()).roundToInt()
        layout(constraints.maxWidth, totalHeight) {
            if (imagePlacable != null) {
                val imageX = if (block.floatedImage.style.float == "left") 0
                else constraints.maxWidth - effectiveImageWidth
                imagePlacable.placeRelative(x = imageX, y = 0)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalFoundationApi::class, ExperimentalSerializationApi::class, FlowPreview::class)
@Composable
fun PaginatedReaderScreen(
    book: EpubBook,
    isDarkTheme: Boolean,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    searchQuery: String,
    fontSizeMultiplier: Float,
    lineHeightMultiplier: Float,
    fontFamily: FontFamily,
    textAlign: ReaderTextAlign,
    ttsHighlightInfo: TtsHighlightInfo?,
    initialChapterIndexInBook: Int?,
    modifier: Modifier = Modifier,
    onPaginatorReady: (IPaginator) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit
) {
    LaunchedEffect(userHighlights) {
        Timber.d("PaginatedReaderScreen: Received ${userHighlights.size} highlights.")
        userHighlights.forEach {
            Timber.d(" -> Received Highlight: CFI=${it.cfi}, Text='${it.text.take(20)}...'")
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val textMeasurer = rememberTextMeasurer()
        val textColor = if (isDarkTheme) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.onSurface
        val baseTextStyle = MaterialTheme.typography.bodyLarge

        var debouncedFontSizeMult by remember { mutableFloatStateOf(fontSizeMultiplier) }
        var debouncedLineHeightMult by remember { mutableFloatStateOf(lineHeightMultiplier) }
        var debouncedFontFamily by remember { mutableStateOf(fontFamily) }
        var debouncedTextAlign by remember { mutableStateOf(textAlign) }

        var anchorLocatorForReconfig by remember { mutableStateOf<Locator?>(null) }

        val textStyle = remember(
            baseTextStyle,
            textColor,
            debouncedFontSizeMult,
            debouncedLineHeightMult,
            debouncedFontFamily
        ) {
            val adjustedFontSize = baseTextStyle.fontSize * debouncedFontSizeMult
            val adjustedLineHeight = adjustedFontSize * debouncedLineHeightMult

            baseTextStyle.copy(
                color = textColor,
                fontSize = adjustedFontSize,
                lineHeight = adjustedLineHeight,
                fontFamily = debouncedFontFamily,
                lineBreak = LineBreak.Paragraph,
                letterSpacing = TextUnit.Unspecified
            )
        }

        val currentPaginatorRef = remember { mutableStateOf<IPaginator?>(null) }

        LaunchedEffect(fontSizeMultiplier, lineHeightMultiplier, fontFamily, textAlign) {
            if (fontSizeMultiplier != debouncedFontSizeMult || lineHeightMultiplier != debouncedLineHeightMult || fontFamily != debouncedFontFamily || textAlign != debouncedTextAlign) {
                Timber.d("Formatting changed. Waiting for debounce.")
                delay(400L)

                val activePaginator = currentPaginatorRef.value
                if (activePaginator is BookPaginator) {
                    val currentPage = pagerState.currentPage
                    val locator = activePaginator.getLocatorForPage(currentPage)
                    if (locator != null) {
                        anchorLocatorForReconfig = locator
                    }
                }

                debouncedFontSizeMult = fontSizeMultiplier
                debouncedLineHeightMult = lineHeightMultiplier
                debouncedFontFamily = fontFamily
                debouncedTextAlign = textAlign
                Timber.d("Debounce complete. Applying new format settings.")
            }
        }

        val userTextAlign = remember(debouncedTextAlign) {
            when (debouncedTextAlign) {
                ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                ReaderTextAlign.LEFT -> TextAlign.Left
                ReaderTextAlign.DEFAULT -> null
            }
        }

        val density = LocalDensity.current
        val horizontalPadding = 16.dp
        val verticalPadding = 16.dp

        val textConstraints =
            remember(this.constraints, density, horizontalPadding, verticalPadding) {
                val horizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                val finalConstraints = this.constraints.copy(
                    minWidth = 0,
                    maxWidth = this.constraints.maxWidth - (2 * horizontalPaddingPx),
                    minHeight = 0,
                    maxHeight = this.constraints.maxHeight - (2 * verticalPaddingPx)
                )
                finalConstraints
            }

        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val mathMLRenderer = remember { MathMLRenderer(context.applicationContext) }

        DisposableEffect(Unit) {
            onDispose {
                mathMLRenderer.destroy()
                Timber.d("PaginatedReaderScreen disposed, MathMLRenderer destroyed.")
            }
        }

        val effectiveInitialChapter =
            remember(initialChapterIndexInBook, anchorLocatorForReconfig) {
                anchorLocatorForReconfig?.chapterIndex ?: initialChapterIndexInBook ?: 0
            }
        val paginator = remember(book, textConstraints, isDarkTheme, textStyle, userTextAlign) {
            val userAgentStylesheet = UserAgentStylesheet.default
            var allRules = OptimizedCssRules()
            val allFontFaces = mutableListOf<FontFaceInfo>()

            val uaResult = CssParser.parse(
                cssContent = userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = textStyle.fontSize.value,
                density = density.density,
                constraints = textConstraints,
                isDarkTheme = isDarkTheme
            )
            allRules = allRules.merge(uaResult.rules)
            allFontFaces.addAll(uaResult.fontFaces)

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = textStyle.fontSize.value,
                    density = density.density,
                    constraints = textConstraints,
                    isDarkTheme = isDarkTheme
                )
                allRules = allRules.merge(bookCssResult.rules)
                allFontFaces.addAll(bookCssResult.fontFaces)
            }
            val fontFamilyMap = loadFontFamilies(
                fontFaces = allFontFaces, extractionPath = book.extractionBasePath
            )
            book.title
            val bookCacheDao =
                BookCacheDatabase.getDatabase(context.applicationContext).bookCacheDao()
            val proto = ProtoBuf { serializersModule = semanticBlockModule }

            Timber.d("Recreating BookPaginator. TextAlign: $userTextAlign")

            BookPaginator(
                coroutineScope = coroutineScope,
                chapters = book.chaptersForPagination,
                textMeasurer = textMeasurer,
                constraints = textConstraints,
                textStyle = textStyle,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = fontFamilyMap,
                isDarkTheme = isDarkTheme,
                bookId = book.title,
                bookCacheDao = bookCacheDao,
                proto = proto,
                initialChapterToPaginate = effectiveInitialChapter,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = userTextAlign
            )
        }

        LaunchedEffect(paginator) {
            onPaginatorReady(paginator)
            currentPaginatorRef.value = paginator
        }

        LaunchedEffect(paginator) {
            if (anchorLocatorForReconfig != null) {
                Timber.d("Waiting for paginator to initialize before restoring anchor...")

                // Suspend until isLoading becomes false
                snapshotFlow { paginator.isLoading }.filter { !it }.first()

                val targetLocator = anchorLocatorForReconfig
                if (targetLocator != null) {
                    Timber.d(
                        "Paginator initialized. Restoring anchor: Chapter=${targetLocator.chapterIndex}, Block=${targetLocator.blockIndex}, Offset=${targetLocator.charOffset}"
                    )

                    val page = paginator.findPageForLocator(targetLocator)
                    if (page != null) {
                        pagerState.scrollToPage(page)
                        Timber.d("Restored to page: $page")
                    } else {
                        val startPage =
                            paginator.chapterStartPageIndices[targetLocator.chapterIndex]
                        if (startPage != null) {
                            Timber.w(
                                "Exact locator not found. Falling back to start of chapter at page $startPage"
                            )
                            pagerState.scrollToPage(startPage)
                        } else {
                            Timber.e("Failed to restore position. Chapter start index not found.")
                        }
                    }
                    anchorLocatorForReconfig = null
                }
            }
        }

        // FIX 2: Replace property delegates with local state and a LaunchedEffect observer.
        var isLoading by remember { mutableStateOf(true) }
        var totalPageCount by remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        LaunchedEffect(paginator) {
            launch { snapshotFlow { paginator.isLoading }.collect { isLoading = it } }
            launch {
                snapshotFlow { paginator.totalPageCount }.collect { newTotalPageCount ->
                    totalPageCount = newTotalPageCount
                }
            }
            launch { snapshotFlow { paginator.generation }.collect { generation = it } }
        }

        LaunchedEffect(pagerState, paginator) {
            snapshotFlow { pagerState.currentPage }.debounce(500) // Wait for scrolling to settle
                .collectLatest { page -> paginator.onUserScrolledTo(page) }
        }

        LaunchedEffect(paginator, pagerState) {
            paginator.pageShiftRequest.collect { shiftAmount ->
                val newPage = pagerState.currentPage + shiftAmount
                pagerState.scrollToPage(newPage)
            }
        }

        val uiState = PaginatedReaderUiState(
            isLoading = isLoading, totalPageCount = totalPageCount, generation = generation
        )

        PaginatedReaderContent(
            uiState = uiState,
            pagerState = pagerState,
            isPageTurnAnimationEnabled = isPageTurnAnimationEnabled,
            searchQuery = searchQuery,
            ttsHighlightInfo = ttsHighlightInfo,
            textStyle = textStyle,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            onGetPage = { pageIndex -> paginator.getPageContent(pageIndex) },
            onGetChapterPath = { pageIndex -> paginator.getChapterPathForPage(pageIndex) },
            onGetChapterInfo = { pageIndex ->
                paginator.findChapterIndexForPage(pageIndex)?.let { chapterIndex ->
                    val chapter = book.chaptersForPagination.getOrNull(chapterIndex)
                    val estimatedPages = paginator.chapterPageCounts[chapterIndex]
                    if (chapter != null) {
                        Pair(chapter.title, estimatedPages)
                    } else {
                        null
                    }
                }
            },
            onLinkClick = { currentChapterPath, href, onNavComplete ->
                paginator.navigateToHref(currentChapterPath, href, onNavComplete)
            },
            onTap = onTap,
            isProUser = isProUser,
            isOss = isOss,
            onShowDictionaryUpsellDialog = onShowDictionaryUpsellDialog,
            onWordSelectedForAiDefinition = onWordSelectedForAiDefinition,
            userHighlights = userHighlights,
            onHighlightCreated = onHighlightCreated,
            onHighlightDeleted = onHighlightDeleted,
            isDarkTheme = isDarkTheme,
            activeHighlightPalette = activeHighlightPalette,
            onUpdatePalette = onUpdatePalette
        )
    }
}

private data class PaginatedMenuState(
    val rect: Rect, val onCopy: () -> Unit, val onHide: () -> Unit, val onSelectAll: (() -> Unit)?
)

private class CustomPaginatedTextToolbar(
    private val onShow: (Rect, () -> Unit, (() -> Unit)?) -> Unit, private val onHide: () -> Unit
) : TextToolbar {
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        if (onCopyRequested != null) {
            onShow(rect, onCopyRequested, onSelectAllRequested)
        }
    }

    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() {
        onHide()
    }
}

private fun parseEmphasisAnnotation(annotation: String, defaultColor: Color): TextEmphasis {
    Timber.d("Parsing annotation string: '$annotation'")
    val map = annotation.split(';').filter { it.isNotBlank() }.associate {
        val (key, value) = it.split(':', limit = 2)
        key to value
    }
    val emphasis = TextEmphasis(
        style = map["s"],
        fill = map["f"],
        color = map["c"]?.toULongOrNull()?.let { Color(it) } ?: defaultColor,
        position = map["p"])
    Timber.d("Parsed annotation to object: $emphasis")
    return emphasis
}

private fun findFuzzyMatch(source: String, target: String, ignoreCase: Boolean = true): IntRange? {
    if (target.isBlank()) return null
    val targetWords = target.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (targetWords.isEmpty()) return null

    var searchStart = 0
    while (searchStart < source.length) {
        val firstIdx = source.indexOf(targetWords[0], searchStart, ignoreCase = ignoreCase)
        if (firstIdx == -1) return null

        var currentIdx = firstIdx + targetWords[0].length
        var allMatch = true

        for (i in 1 until targetWords.size) {
            while (currentIdx < source.length && source[currentIdx].isWhitespace()) {
                currentIdx++
            }
            if (currentIdx >= source.length) {
                allMatch = false
                break
            }

            val word = targetWords[i]
            if (source.regionMatches(currentIdx, word, 0, word.length, ignoreCase = ignoreCase)) {
                currentIdx += word.length
            } else {
                allMatch = false
                break
            }
        }

        if (allMatch) return firstIdx until currentIdx
        searchStart = firstIdx + 1
    }
    return null
}

private fun getHighlightOffsetsInBlock(
    block: TextContentBlock, highlight: UserHighlight
): IntRange? {
    if (block.cfi == null) return null

    val blockPath = CfiUtils.getPath(block.cfi!!)
    val parts = highlight.cfi.split('|')
    val startCfi = parts.firstOrNull() ?: highlight.cfi
    val endCfi = parts.lastOrNull()

    @Suppress("REDUNDANT_ELSE_IN_WHEN") val blockStartAbs = when (block) {
        is ParagraphBlock -> block.startCharOffsetInSource
        is HeaderBlock -> block.startCharOffsetInSource
        is QuoteBlock -> block.startCharOffsetInSource
        is ListItemBlock -> block.startCharOffsetInSource
        else -> 0
    }

    Timber.d(
        "getHighlightOffsetsInBlock: Checking Block=${block.cfi} (AbsStart=$blockStartAbs) against Highlight=${highlight.cfi}"
    )

    val relevantPart = parts.find { cfiPart ->
        val highlightPath = CfiUtils.getPath(cfiPart)

        if (highlightPath.startsWith(blockPath)) return@find true

        val highlightSegments = highlightPath.split('/').filter { it.isNotEmpty() }
        val blockSegments = blockPath.split('/').filter { it.isNotEmpty() }

        if (highlightSegments.size > blockSegments.size) {
            val pathWithoutFirst = "/" + highlightSegments.drop(1).joinToString("/")
            if (pathWithoutFirst.startsWith(blockPath)) return@find true
        }

        if (highlightSegments.isNotEmpty() && blockSegments.isNotEmpty()) {
            if (highlightSegments[0] != blockSegments[0]) {
                val highlightTail = highlightSegments.drop(1)
                val blockTail = blockSegments.drop(1)
                if (blockTail.isNotEmpty() && highlightTail.size >= blockTail.size) {
                    var match = true
                    for (i in blockTail.indices) {
                        if (blockTail[i] != highlightTail[i]) {
                            match = false
                            break
                        }
                    }
                    if (match) return@find true
                }
            }
        }
        false
    }

    if (relevantPart != null) {
        Timber.d(
            " -> Block ${block.cfi} matches specific part of multipart highlight: $relevantPart"
        )
    }

    var isAfterStart = false
    var isBeforeEnd = true

    if (relevantPart == null) {
        if (startCfi.isNotEmpty()) {
            try {
                if (CfiUtils.compare(block.cfi!!, startCfi) > 0) {
                    isAfterStart = true
                }
            } catch (_: Exception) {
            }
        }

        if (endCfi != null && endCfi != startCfi) {
            try {
                val endPath = CfiUtils.getPath(endCfi)
                val cmp = CfiUtils.compare(blockPath, endPath)
                Timber.d(" -> Comparing BlockPath ($blockPath) vs EndPath ($endPath). Result: $cmp")
                if (CfiUtils.compare(blockPath, endPath) > 0) {
                    isBeforeEnd = false
                }
            } catch (_: Exception) {
            }
        }
    }

    Timber.d(" -> relevantPart=$relevantPart, isAfterStart=$isAfterStart, isBeforeEnd=$isBeforeEnd")

    if (relevantPart == null && (!isAfterStart || !isBeforeEnd)) {
        return null
    }

    val blockText = block.content.text
    val highlightText = highlight.text

    if (blockText.isEmpty() || highlightText.isEmpty()) return null
    if (highlightText.contains(blockText, ignoreCase = false)) return 0 until blockText.length
    if (highlightText.contains(blockText, ignoreCase = true)) return 0 until blockText.length

    var startIndex = blockText.indexOf(highlightText, ignoreCase = false)
    if (startIndex == -1) {
        startIndex = blockText.indexOf(highlightText, ignoreCase = true)
    }

    if (relevantPart != null) {
        fun arePathsEquivalent(path1: String, path2: String): Boolean {
            val p1 = CfiUtils.getPath(path1).split('/').filter { it.isNotEmpty() }
            val p2 = CfiUtils.getPath(path2).split('/').filter { it.isNotEmpty() }

            if (p1 == p2) return true

            if (p1.size == p2.size && p1.isNotEmpty()) {
                return p1.drop(1) == p2.drop(1)
            }
            return false
        }

        val startMatches = arePathsEquivalent(startCfi, block.cfi!!)
        val endMatches = if (endCfi != null) arePathsEquivalent(endCfi, block.cfi!!) else false

        Timber.d(" -> Path Equivalence: StartMatches=$startMatches, EndMatches=$endMatches")

        if (startMatches || endMatches) {
            var s = 0
            var e = blockText.length

            if (startMatches) {
                val absOffset = CfiUtils.getOffset(startCfi)
                val relOffset = absOffset - blockStartAbs

                if (relOffset < 0) {
                    s = 0
                } else {
                    val safeStart = (relOffset - 50).coerceAtLeast(0)
                    val safeEnd = (relOffset + 50).coerceAtMost(blockText.length)

                    if (safeStart < safeEnd) {
                        val windowText = blockText.substring(safeStart, safeEnd)
                        val prefix = highlightText.trim().take(20).trim()

                        var snapped = false
                        if (prefix.isNotEmpty()) {
                            val matches = mutableListOf<Int>()
                            var idx = windowText.indexOf(prefix, ignoreCase = true)
                            while (idx != -1) {
                                matches.add(idx)
                                idx = windowText.indexOf(prefix, idx + 1, ignoreCase = true)
                            }

                            if (matches.isNotEmpty()) {
                                val targetRel = relOffset - safeStart
                                val bestRel = matches.minByOrNull { abs(it - targetRel) }!!
                                val newS = safeStart + bestRel
                                Timber.d(
                                    "Snapped start offset from rel $relOffset to $newS based on prefix '$prefix'"
                                )
                                s = newS
                                snapped = true
                            }
                        }

                        if (!snapped) {
                            s = relOffset
                        }
                    } else {
                        s = relOffset
                    }
                }
            }

            if (endMatches) {
                val absOffset = CfiUtils.getOffset(endCfi!!)
                val relOffset = absOffset - blockStartAbs

                Timber.d(
                    " -> EndCFI Match. AbsOffset: $absOffset. RelOffset: $relOffset. Block Length: ${blockText.length}"
                )

                e = if (relOffset > blockText.length) {
                    blockText.length
                } else {
                    relOffset
                }
            }

            s = s.coerceIn(0, blockText.length)
            e = e.coerceIn(0, blockText.length)

            if (s < e) {
                Timber.d("Fallback to CFI offsets for block ${block.cfi}. Range: $s..$e")
                return s until e
            } else {
                Timber.w(
                    " -> Invalid Range detected (likely highlight is on other split part): $s..$e"
                )
                return null
            }
        }
    }

    if (startIndex >= 0) {
        return startIndex until (startIndex + highlightText.length)
    }

    if (relevantPart == null) {
        @Suppress("KotlinConstantConditions") if (isAfterStart) {
            val normBlock = blockText.filter { !it.isWhitespace() }
            val normHighlight = highlightText.filter { !it.isWhitespace() }
            if (normHighlight.contains(normBlock, ignoreCase = true)) {
                return 0 until blockText.length
            }
        }
    }

    val match = findFuzzyMatch(blockText, highlightText)
    if (match != null) return match

    if (relevantPart != null) {
        Timber.d(
            "Failed to match highlight text in block despite CFI match. " + "BlockCfi=${block.cfi}, HighlightCfi=${highlight.cfi}. "
        )
    }

    return null
}

@Composable
private fun TextWithEmphasis(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    @Suppress("unused") textMeasurer: TextMeasurer,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit,
    block: TextContentBlock,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    @Suppress("unused") onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    @Suppress("unused") isDarkTheme: Boolean,
    onRegisterLayout: ((TextLayoutResult, LayoutCoordinates) -> Unit)? = null
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val viewConfiguration = LocalViewConfiguration.current
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    var pressedHighlightCfi by remember { mutableStateOf<String?>(null) }

    val customDrawer = Modifier.drawBehind {
        textLayoutResult?.let { layoutResult ->
            if (activeSelection != null && activeSelection.blockIndex == block.blockIndex) {
                val path = layoutResult.getPathForRange(
                    activeSelection.startOffset, activeSelection.endOffset
                )
                drawPath(path, Color(0xFF1976D2).copy(alpha = 0.3f))
            }

            if (block.cfi != null && userHighlights.isNotEmpty()) {
                userHighlights.forEach { highlight ->
                    val range = getHighlightOffsetsInBlock(block, highlight)

                    if (range != null) {
                        try {
                            val path = layoutResult.getPathForRange(
                                range.first, range.last + 1
                            )

                            drawPath(
                                path,
                                highlight.color.color.copy(alpha = 0.4f),
                                blendMode = BlendMode.SrcOver
                            )

                            if (highlight.cfi == pressedHighlightCfi) {
                                drawPath(
                                    path,
                                    Color.Black.copy(alpha = 0.1f),
                                    blendMode = BlendMode.SrcOver
                                )
                            }
                        } catch (_: Exception) { }
                    }
                }
            }

            val emphasisAnnotations = text.getStringAnnotations("TextEmphasis", 0, text.length)
            if (emphasisAnnotations.isNotEmpty()) {
                emphasisAnnotations.forEach { annotation ->
                    val emphasis = parseEmphasisAnnotation(annotation.item, style.color)
                    val markColor = if (emphasis.color.isSpecified) emphasis.color else style.color
                    val markSize = layoutResult.layoutInput.style.fontSize.toPx() * 0.3f
                    for (offset in annotation.start until annotation.end) {
                        if (offset >= text.text.length || text.text[offset].isWhitespace()) continue
                        try {
                            val boundingBox = layoutResult.getBoundingBox(offset)
                            val center = Offset(
                                boundingBox.center.x,
                                if (emphasis.position == "under") boundingBox.bottom + markSize * 0.1f
                                else boundingBox.top - markSize * 0.1f
                            )
                            drawCircle(markColor, markSize / 2, center, style = Stroke(1f))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun getHighlightAt(offset: Offset, layout: TextLayoutResult): Pair<UserHighlight, Rect>? {
        if (block.cfi == null) return null

        // Optimization: Quick bounds check
        val charOffset = layout.getOffsetForPosition(offset)
        val lineIndex = layout.getLineForOffset(charOffset)
        val lineLeft = layout.getLineLeft(lineIndex)
        val lineRight = layout.getLineRight(lineIndex)
        if (offset.x < minOf(lineLeft, lineRight) - 50 || offset.x > maxOf(
                lineLeft, lineRight
            ) + 50
        ) {
            return null
        }

        // Iterate highlights reversed (topmost first)
        for (highlight in userHighlights.reversed()) {
            val range = getHighlightOffsetsInBlock(block, highlight) ?: continue

            if (charOffset in range) {
                val path = layout.getPathForRange(range.first, range.last)
                val bounds = path.getBounds()
                return highlight to bounds
            }
        }
        return null
    }

    Text(text = text, style = style, modifier = modifier
        .onGloballyPositioned {
            layoutCoordinates = it
            if (textLayoutResult != null && block.cfi != null) {
                onRegisterLayout?.invoke(textLayoutResult!!, it)
            }
        }
        .then(customDrawer)
        .pointerInput(userHighlights, text) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    pass = PointerEventPass.Initial, requireUnconsumed = false
                )
                val layout = textLayoutResult
                if (layout != null) {
                    val hit = getHighlightAt(down.position, layout)
                    if (hit != null) {
                        down.consume()
                        val startPosition = down.position
                        var dragDistance = 0f
                        var isFinished = false

                        val longPressJob = scope.launch {
                            delay(500)
                            pressedHighlightCfi = hit.first.cfi
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent(
                                    pass = PointerEventPass.Initial
                                )
                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                }
                                if (change == null) {
                                    longPressJob.cancel()
                                    pressedHighlightCfi = null
                                    break
                                }
                                change.consume()
                                if (change.pressed) {
                                    dragDistance = (change.position - startPosition).getDistance()
                                    if (dragDistance >= viewConfiguration.touchSlop) {
                                        longPressJob.cancel()
                                        pressedHighlightCfi = null
                                    }
                                } else {
                                    isFinished = true
                                    longPressJob.cancel()
                                    pressedHighlightCfi = null
                                    break
                                }
                            }
                        } catch (_: Exception) {
                            longPressJob.cancel()
                            pressedHighlightCfi = null
                        }

                        if (isFinished && dragDistance < viewConfiguration.touchSlop) {
                            val (highlight, localRect) = hit
                            val globalRect = layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val topLeft = coords.localToWindow(
                                        localRect.topLeft
                                    )
                                    val bottomRight = coords.localToWindow(
                                        localRect.bottomRight
                                    )
                                    Rect(topLeft, bottomRight)
                                } else null
                            } ?: localRect
                            onHighlightClick(highlight, globalRect)
                        }
                    }
                }
            }
        }
        .pointerInput(text) {
            detectTapGestures(
                onTap = { offset ->
                    textLayoutResult?.let { layout ->
                        val charOffset = layout.getOffsetForPosition(offset)
                        val urlAnnotation = text.getStringAnnotations(
                            "URL", charOffset, charOffset
                        ).firstOrNull()
                        if (urlAnnotation != null) onLinkClick(urlAnnotation.item)
                        else onGeneralTap(offset)
                    }
                })
        }, onTextLayout = {
        textLayoutResult = it
        if (layoutCoordinates != null && block.cfi != null) {
            onRegisterLayout?.invoke(it, layoutCoordinates!!)
        }
    })
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun PaginatedReaderContent(
    uiState: PaginatedReaderUiState,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    searchQuery: String,
    ttsHighlightInfo: TtsHighlightInfo?,
    textStyle: TextStyle,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    onGetPage: (Int) -> Page?,
    onGetChapterPath: (Int) -> String?,
    onLinkClick: (currentChapterPath: String, href: String, onNavComplete: (Int) -> Unit) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onGetChapterInfo: (Int) -> Pair<String, Int?>?,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    isDarkTheme: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var menuState by remember { mutableStateOf<PaginatedMenuState?>(null) }
    var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val textMeasurer = rememberTextMeasurer()
    var activeSelection by remember { mutableStateOf<PaginatedSelection?>(null) }
    var activeHighlightForMenu by remember { mutableStateOf<Pair<UserHighlight, Rect>?>(null) }
    val blockLayoutMap = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Triple<TextLayoutResult, LayoutCoordinates, Int>>()
    }
    var showColorPickerDialog by remember { mutableStateOf<Int?>(null) }
    var showPaletteManager by remember { mutableStateOf(false) }

    if (showExternalLinkDialog != null) {
        val urlToShow = showExternalLinkDialog!!
        AlertDialog(
            onDismissRequest = { showExternalLinkDialog = null },
            title = { Text("External Link") },
            text = {
                Text(
                    "You clicked on an external link:\n\n$urlToShow\n\nWhat would you like to do?"
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Copied Link", urlToShow)
                            clipboard.setPrimaryClip(clip)
                            showExternalLinkDialog = null
                        }) { Text("Copy") }
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Timber.e(
                                    e, "No activity found to handle intent for URL: $urlToShow"
                                )
                                Toast.makeText(
                                    context, "No browser found to open the link.", Toast.LENGTH_LONG
                                ).show()
                            }
                            showExternalLinkDialog = null
                        }) { Text("Open") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalLinkDialog = null }) { Text("Cancel") }
            })
    }

    val textToolbar = remember {
        CustomPaginatedTextToolbar(onShow = { rect, onCopy, onSelectAll ->
            if (activeHighlightForMenu == null) {
                menuState = PaginatedMenuState(
                    rect, onCopy, onHide = { menuState = null }, onSelectAll
                )
            }
        }, onHide = { menuState = null })
    }

    var pageTurnTouchY by remember { mutableStateOf<Float?>(null) }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        if (uiState.totalPageCount > 0) {
            uiState.generation

            val realClipboard: Clipboard = LocalClipboard.current
            var isForDictionary by remember { mutableStateOf(false) }
            var isForHighlight by remember { mutableStateOf(false) }
            var capturedTextForAction by remember { mutableStateOf<String?>(null) }

            val dictionaryClipboard = remember(realClipboard) {
                object : Clipboard {
                    override val nativeClipboard: ClipboardManager
                        get() = realClipboard.nativeClipboard

                    override suspend fun getClipEntry(): ClipEntry? = realClipboard.getClipEntry()

                    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                        val text = clipEntry?.clipData?.getItemAt(0)?.text?.toString()
                        Timber.d(
                            "Clipboard intercept: setClipEntry called. Text: '$text', isForHighlight: $isForHighlight"
                        )
                        capturedTextForAction = text

                        if (isForDictionary) {
                            if (!text.isNullOrBlank()) {
                                if (isProUser || countWords(text) <= 1) {
                                    if (text.length <= 2000) {
                                        onWordSelectedForAiDefinition(text)
                                    }
                                } else {
                                    onShowDictionaryUpsellDialog()
                                }
                            }
                        } else if (isForHighlight) {
                            // Do not copy to real clipboard
                        } else {
                            realClipboard.setClipEntry(clipEntry)
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalTextToolbar provides textToolbar, LocalClipboard provides dictionaryClipboard
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) {
                                        pageTurnTouchY = down.position.y
                                    }
                                }
                            }
                        },
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    val zIndex = -pageOffset

                    val pageModifier = if (isPageTurnAnimationEnabled) {
                        Modifier
                            .zIndex(zIndex)
                            .realisticBookPage(pagerState, pageIndex, isDarkTheme, pageTurnTouchY) // UPDATED
                    } else {
                        Modifier
                    }

                    var pageContent by remember { mutableStateOf<Page?>(null) }
                    var currentChapterPath by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(pageIndex, uiState.generation) {
                        pageContent = onGetPage(pageIndex)
                        onGetChapterPath(pageIndex)?.let { currentChapterPath = it }
                    }

                    Box(modifier = Modifier.fillMaxSize().then(pageModifier)) {
                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            Timber.d(
                                                "Tap detected on empty page area."
                                            )
                                            menuState?.onHide?.invoke()
                                            onTap(offset)
                                        })
                                }
                                .padding(
                                    horizontal = horizontalPadding, vertical = verticalPadding
                                ), contentAlignment = Alignment.TopStart) {
                                if (pageContent != null) {
                                    val onGeneralTapCallback: (Offset) -> Unit = { offset ->
                                        menuState?.onHide?.invoke()
                                        onTap(offset)
                                    }
                                    val onLinkClickCallback: (String) -> Unit = { href ->
                                        Timber.d("Link clicked: $href")
                                        if (href.startsWith("http://") || href.startsWith("https://")) {
                                            showExternalLinkDialog = href
                                        } else {
                                            currentChapterPath?.let { path ->
                                                onLinkClick(path, href) { targetPageIndex ->
                                                    coroutineScope.launch {
                                                        pagerState.scrollToPage(targetPageIndex)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        val searchHighlightColor =
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        val ttsHighlightColor =
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                        pageContent!!.content.forEach { block ->
                                            val marginModifier = Modifier.padding(
                                                top = block.style.margin.top.coerceAtLeast(0.dp),
                                                bottom = block.style.margin.bottom.coerceAtLeast(0.dp)
                                            )

                                            val alignModifier =
                                                if (block.style.horizontalAlign == "center") {
                                                    Modifier.align(Alignment.CenterHorizontally)
                                                } else {
                                                    Modifier.padding(
                                                        start = block.style.margin.left.coerceAtLeast(0.dp),
                                                        end = block.style.margin.right.coerceAtLeast(0.dp)
                                                    )
                                                }

                                            val widthModifier =
                                                if (block.style.width != Dp.Unspecified) {
                                                    Modifier.width(block.style.width)
                                                } else {
                                                    Modifier.fillMaxWidth()
                                                }

                                            val boxModifier = marginModifier
                                                .then(alignModifier)
                                                .then(
                                                    if (block.style.horizontalAlign == "center") widthModifier
                                                    else Modifier
                                                )
                                                .then(
                                                    if (block.style.borderRadius > 0.dp) {
                                                        Modifier.clip(RoundedCornerShape(block.style.borderRadius))
                                                    } else Modifier
                                                )
                                                .then(
                                                    if (block.style.backgroundColor.isSpecified) {
                                                        Modifier.background(
                                                            block.style.backgroundColor,
                                                            shape = if (block.style.borderRadius > 0.dp) RoundedCornerShape(
                                                                block.style.borderRadius
                                                            ) else androidx.compose.ui.graphics.RectangleShape
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .then(block.style.border?.let { border ->
                                                    Modifier.border(
                                                        BorderStroke(
                                                            border.width, border.color
                                                        ),
                                                        shape = if (block.style.borderRadius > 0.dp) RoundedCornerShape(
                                                            block.style.borderRadius
                                                        ) else androidx.compose.ui.graphics.RectangleShape
                                                    )
                                                } ?: Modifier)

                                            Box(modifier = boxModifier) {
                                                val paddingModifier = Modifier
                                                    .padding(
                                                        start = block.style.padding.left.coerceAtLeast(
                                                            0.dp
                                                        ),
                                                        top = block.style.padding.top.coerceAtLeast(
                                                            0.dp
                                                        ),
                                                        end = block.style.padding.right.coerceAtLeast(
                                                            0.dp
                                                        ),
                                                        bottom = block.style.padding.bottom.coerceAtLeast(
                                                            0.dp
                                                        )
                                                    )
                                                    .then(
                                                        if (block.style.horizontalAlign != "center") widthModifier
                                                        else Modifier.fillMaxWidth()
                                                    )

                                                @Suppress("DEPRECATION") when (block) {
                                                    is ParagraphBlock -> {
                                                        val searchHighlighted = highlightQueryInText(
                                                            block.content,
                                                            searchQuery,
                                                            searchHighlightColor
                                                        )
                                                        val finalContent =
                                                            if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                buildAnnotatedString {
                                                                    append(searchHighlighted)

                                                                    // Define absolute ranges
                                                                    val blockStartAbs =
                                                                        block.startCharOffsetInSource
                                                                    val blockEndAbs =
                                                                        block.startCharOffsetInSource + searchHighlighted.length
                                                                    val highlightStartAbs =
                                                                        ttsHighlightInfo.offset
                                                                    val highlightEndAbs =
                                                                        ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                    // Calculate intersection
                                                                    val intersectionStartAbs = maxOf(
                                                                        blockStartAbs, highlightStartAbs
                                                                    )
                                                                    val intersectionEndAbs = minOf(
                                                                        blockEndAbs, highlightEndAbs
                                                                    )

                                                                    // Check for overlap and apply
                                                                    // style
                                                                    if (intersectionStartAbs < intersectionEndAbs) {
                                                                        val highlightStartRelative =
                                                                            intersectionStartAbs - blockStartAbs
                                                                        val highlightEndRelative =
                                                                            intersectionEndAbs - blockStartAbs
                                                                        addStyle(
                                                                            style = SpanStyle(
                                                                                background = ttsHighlightColor
                                                                            ),
                                                                            start = highlightStartRelative,
                                                                            end = highlightEndRelative
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                searchHighlighted
                                                            }

                                                        val diagnosticModifier =
                                                            if (block.textAlign == TextAlign.Justify) {
                                                                Modifier.onGloballyPositioned { coordinates ->
                                                                    val width = coordinates.size.width
                                                                    Timber.d(
                                                                        """
                                                                [UI Render]
                                                                Block Index: ${block.blockIndex}
                                                                Text Start: ${
                                                                            block.content.text.take(
                                                                                20
                                                                            )
                                                                        }...
                                                                Actual Render Width Px: $width
                                                                ------------------------------------------------
                                                            """.trimIndent()
                                                                    )
                                                                }
                                                            } else {
                                                                Modifier
                                                            }

                                                        TextWithEmphasis(
                                                            text = finalContent,
                                                            style = textStyle,
                                                            modifier = paddingModifier.then(
                                                                diagnosticModifier
                                                            ),
                                                            textMeasurer = textMeasurer,
                                                            onLinkClick = onLinkClickCallback,
                                                            onGeneralTap = onGeneralTapCallback,
                                                            block = block,
                                                            userHighlights = userHighlights,
                                                            activeSelection = activeSelection,
                                                            onSelectionChange = { sel ->
                                                                activeSelection = sel
                                                            },
                                                            onHighlightClick = { highlight, rect ->
                                                                activeHighlightForMenu =
                                                                    highlight to rect
                                                                activeSelection = null
                                                                menuState = null
                                                            },
                                                            isDarkTheme = isDarkTheme,
                                                            onRegisterLayout = { layout, coords ->
                                                                if (block.cfi != null) blockLayoutMap[block.cfi] =
                                                                    Triple(
                                                                        layout,
                                                                        coords,
                                                                        block.startCharOffsetInSource
                                                                    )
                                                            })
                                                    }

                                                    is HeaderBlock -> {
                                                        val style = textStyle.copy(
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        val searchHighlighted = highlightQueryInText(
                                                            block.content,
                                                            searchQuery,
                                                            searchHighlightColor
                                                        )
                                                        val finalContent =
                                                            if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                buildAnnotatedString {
                                                                    append(searchHighlighted)

                                                                    val blockStartAbs =
                                                                        block.startCharOffsetInSource
                                                                    val blockEndAbs =
                                                                        block.startCharOffsetInSource + searchHighlighted.length
                                                                    val highlightStartAbs =
                                                                        ttsHighlightInfo.offset
                                                                    val highlightEndAbs =
                                                                        ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                    val intersectionStartAbs = maxOf(
                                                                        blockStartAbs, highlightStartAbs
                                                                    )
                                                                    val intersectionEndAbs = minOf(
                                                                        blockEndAbs, highlightEndAbs
                                                                    )

                                                                    if (intersectionStartAbs < intersectionEndAbs) {
                                                                        val highlightStartRelative =
                                                                            intersectionStartAbs - blockStartAbs
                                                                        val highlightEndRelative =
                                                                            intersectionEndAbs - blockStartAbs
                                                                        addStyle(
                                                                            style = SpanStyle(
                                                                                background = ttsHighlightColor
                                                                            ),
                                                                            start = highlightStartRelative,
                                                                            end = highlightEndRelative
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                searchHighlighted
                                                            }
                                                        TextWithEmphasis(
                                                            text = finalContent,
                                                            style = style,
                                                            modifier = paddingModifier,
                                                            textMeasurer = textMeasurer,
                                                            onLinkClick = onLinkClickCallback,
                                                            onGeneralTap = onGeneralTapCallback,
                                                            block = block,
                                                            userHighlights = userHighlights,
                                                            activeSelection = activeSelection,
                                                            onSelectionChange = { sel ->
                                                                activeSelection = sel
                                                            },
                                                            onHighlightClick = { highlight, rect ->
                                                                activeHighlightForMenu =
                                                                    highlight to rect
                                                                activeSelection = null
                                                                menuState = null
                                                            },
                                                            isDarkTheme = isDarkTheme,
                                                            onRegisterLayout = { layout, coords ->
                                                                if (block.cfi != null) blockLayoutMap[block.cfi] =
                                                                    Triple(
                                                                        layout,
                                                                        coords,
                                                                        block.startCharOffsetInSource
                                                                    )
                                                            })
                                                    }

                                                    is QuoteBlock -> {
                                                        val quoteModifier =
                                                            paddingModifier.padding(start = 16.dp)
                                                        val searchHighlighted = highlightQueryInText(
                                                            block.content,
                                                            searchQuery,
                                                            searchHighlightColor
                                                        )
                                                        val finalContent =
                                                            if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                buildAnnotatedString {
                                                                    append(searchHighlighted)

                                                                    val blockStartAbs =
                                                                        block.startCharOffsetInSource
                                                                    val blockEndAbs =
                                                                        block.startCharOffsetInSource + searchHighlighted.length
                                                                    val highlightStartAbs =
                                                                        ttsHighlightInfo.offset
                                                                    val highlightEndAbs =
                                                                        ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                    val intersectionStartAbs = maxOf(
                                                                        blockStartAbs, highlightStartAbs
                                                                    )
                                                                    val intersectionEndAbs = minOf(
                                                                        blockEndAbs, highlightEndAbs
                                                                    )

                                                                    if (intersectionStartAbs < intersectionEndAbs) {
                                                                        val highlightStartRelative =
                                                                            intersectionStartAbs - blockStartAbs
                                                                        val highlightEndRelative =
                                                                            intersectionEndAbs - blockStartAbs
                                                                        addStyle(
                                                                            style = SpanStyle(
                                                                                background = ttsHighlightColor
                                                                            ),
                                                                            start = highlightStartRelative,
                                                                            end = highlightEndRelative
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                searchHighlighted
                                                            }
                                                        TextWithEmphasis(
                                                            text = finalContent,
                                                            style = textStyle,
                                                            modifier = quoteModifier,
                                                            textMeasurer = textMeasurer,
                                                            onLinkClick = onLinkClickCallback,
                                                            onGeneralTap = onGeneralTapCallback,
                                                            block = block,
                                                            userHighlights = userHighlights,
                                                            activeSelection = activeSelection,
                                                            onSelectionChange = { sel ->
                                                                activeSelection = sel
                                                            },
                                                            onHighlightClick = { highlight, rect ->
                                                                activeHighlightForMenu =
                                                                    highlight to rect
                                                                activeSelection = null
                                                                menuState = null
                                                            },
                                                            isDarkTheme = isDarkTheme,
                                                            onRegisterLayout = { layout, coords ->
                                                                if (block.cfi != null) blockLayoutMap[block.cfi] =
                                                                    Triple(
                                                                        layout,
                                                                        coords,
                                                                        block.startCharOffsetInSource
                                                                    )
                                                            })
                                                    }

                                                    is ListItemBlock -> {
                                                        Row(
                                                            modifier = paddingModifier,
                                                            verticalAlignment = Alignment.Top
                                                        ) {
                                                            val markerAreaModifier =
                                                                Modifier
                                                                    .width(32.dp)
                                                                    .padding(end = 8.dp)

                                                            if (block.itemMarkerImage != null) {
                                                                val imageRequest =
                                                                    Builder(LocalContext.current).data(
                                                                        File(
                                                                            block.itemMarkerImage
                                                                        )
                                                                    ).crossfade(true).build()
                                                                val imageSize = with(density) {
                                                                    (textStyle.fontSize.value * 0.8f).sp.toDp()
                                                                }

                                                                AsyncImage(
                                                                    model = imageRequest,
                                                                    contentDescription = "List item marker",
                                                                    modifier = markerAreaModifier.height(
                                                                        imageSize
                                                                    ),
                                                                    alignment = Alignment.CenterEnd,
                                                                    contentScale = ContentScale.FillHeight
                                                                )
                                                            } else if (block.itemMarker != null) {
                                                                Text(
                                                                    text = block.itemMarker,
                                                                    style = textStyle.copy(
                                                                        textAlign = TextAlign.End
                                                                    ),
                                                                    modifier = markerAreaModifier
                                                                )
                                                            }
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs = minOf(
                                                                            blockEndAbs, highlightEndAbs
                                                                        )

                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }
                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = textStyle,
                                                                modifier = Modifier.weight(1f),
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = userHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, rect ->
                                                                    activeHighlightForMenu =
                                                                        highlight to rect
                                                                    activeSelection = null
                                                                    menuState = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap[block.cfi] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block.startCharOffsetInSource
                                                                        )
                                                                })
                                                        }
                                                    }

                                                    is WrappingContentBlock -> {
                                                        WrappingContentLayout(
                                                            block = block,
                                                            textStyle = textStyle,
                                                            modifier = paddingModifier,
                                                            searchQuery = searchQuery,
                                                            ttsHighlightInfo = ttsHighlightInfo,
                                                            searchHighlightColor = searchHighlightColor,
                                                            ttsHighlightColor = ttsHighlightColor
                                                        )
                                                    }

                                                    is FlexContainerBlock -> {
                                                        // Background, border, and padding are
                                                        // already applied by the outer Box wrapper.
                                                        // Only apply padding + width here.
                                                        val containerModifier = paddingModifier

                                                        if (block.style.flexDirection == "row") {
                                                            val horizontalArrangement =
                                                                when (block.style.justifyContent) {
                                                                    "center" -> Arrangement.Center
                                                                    "flex-end" -> Arrangement.End
                                                                    "space-between" -> Arrangement.SpaceBetween
                                                                    "space-around" -> Arrangement.SpaceAround
                                                                    else -> Arrangement.Start
                                                                }
                                                            val verticalAlignment =
                                                                when (block.style.alignItems) {
                                                                    "center" -> Alignment.CenterVertically
                                                                    "flex-end" -> Alignment.Bottom
                                                                    else -> Alignment.Top
                                                                }
                                                            Row(
                                                                modifier = containerModifier.fillMaxWidth(),
                                                                horizontalArrangement = horizontalArrangement,
                                                                verticalAlignment = verticalAlignment
                                                            ) {
                                                                block.children.forEach { childBlock ->
                                                                    RenderFlexChildBlock(
                                                                        childBlock = childBlock,
                                                                        textStyle = textStyle,
                                                                        searchQuery = searchQuery,
                                                                        searchHighlightColor = searchHighlightColor,
                                                                        ttsHighlightInfo = ttsHighlightInfo,
                                                                        ttsHighlightColor = ttsHighlightColor,
                                                                        textMeasurer = textMeasurer,
                                                                        onLinkClickCallback = onLinkClickCallback,
                                                                        onGeneralTapCallback = onGeneralTapCallback,
                                                                        userHighlights = userHighlights,
                                                                        activeSelection = activeSelection,
                                                                        onSelectionChange = { sel ->
                                                                            activeSelection = sel
                                                                        },
                                                                        onHighlightClick = { highlight, rect ->
                                                                            activeHighlightForMenu =
                                                                                highlight to rect
                                                                            activeSelection = null
                                                                            menuState = null
                                                                        },
                                                                        isDarkTheme = isDarkTheme,
                                                                        blockLayoutMap = blockLayoutMap,
                                                                        density = density,
                                                                        imageLoader = imageLoader
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            val verticalArrangement =
                                                                when (block.style.justifyContent) {
                                                                    "center" -> Arrangement.Center
                                                                    "flex-end" -> Arrangement.Bottom
                                                                    "space-between" -> Arrangement.SpaceBetween
                                                                    "space-around" -> Arrangement.SpaceAround
                                                                    else -> Arrangement.Top
                                                                }
                                                            val horizontalAlignment =
                                                                when (block.style.alignItems) {
                                                                    "center" -> Alignment.CenterHorizontally
                                                                    "flex-end" -> Alignment.End
                                                                    else -> Alignment.Start
                                                                }
                                                            Column(
                                                                modifier = containerModifier.fillMaxWidth(),
                                                                verticalArrangement = verticalArrangement,
                                                                horizontalAlignment = horizontalAlignment
                                                            ) {
                                                                block.children.forEach { childBlock ->
                                                                    RenderFlexChildBlock(
                                                                        childBlock = childBlock,
                                                                        textStyle = textStyle,
                                                                        searchQuery = searchQuery,
                                                                        searchHighlightColor = searchHighlightColor,
                                                                        ttsHighlightInfo = ttsHighlightInfo,
                                                                        ttsHighlightColor = ttsHighlightColor,
                                                                        textMeasurer = textMeasurer,
                                                                        onLinkClickCallback = onLinkClickCallback,
                                                                        onGeneralTapCallback = onGeneralTapCallback,
                                                                        userHighlights = userHighlights,
                                                                        activeSelection = activeSelection,
                                                                        onSelectionChange = { sel ->
                                                                            activeSelection = sel
                                                                        },
                                                                        onHighlightClick = { highlight, rect ->
                                                                            activeHighlightForMenu =
                                                                                highlight to rect
                                                                            activeSelection = null
                                                                            menuState = null
                                                                        },
                                                                        isDarkTheme = isDarkTheme,
                                                                        blockLayoutMap = blockLayoutMap,
                                                                        density = density,
                                                                        imageLoader = imageLoader
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    is MathBlock -> {
                                                        Timber.d(
                                                            "PaginatedReader: Rendering MathBlock. Alt: '${block.altText}', Has SVG: ${!block.svgContent.isNullOrBlank()}"
                                                        )
                                                        if (!block.svgContent.isNullOrBlank()) {
                                                            BoxWithConstraints(
                                                                modifier = paddingModifier
                                                            ) {
                                                                val localDensity = LocalDensity.current
                                                                val fontSizePx = with(localDensity) {
                                                                    textStyle.fontSize.toPx()
                                                                }
                                                                val containerWidthPx =
                                                                    with(localDensity) {
                                                                        maxWidth.roundToPx()
                                                                    }
                                                                val widthPx = parseSvgDimension(
                                                                    block.svgWidth,
                                                                    fontSizePx,
                                                                    containerWidthPx,
                                                                    localDensity
                                                                )
                                                                val imageModifier =
                                                                    if (widthPx != null) {
                                                                        val finalWidthDp =
                                                                            with(localDensity) {
                                                                                widthPx.toDp()
                                                                            }
                                                                        Timber.d(
                                                                            "Applying calculated width to MathBlock image: $finalWidthDp"
                                                                        )
                                                                        Modifier.width(finalWidthDp)
                                                                    } else {
                                                                        Timber.w(
                                                                            "Could not calculate a specific width for MathBlock. It will fill available space."
                                                                        )
                                                                        Modifier
                                                                    }

                                                                val imageRequest =
                                                                    Builder(LocalContext.current).data(
                                                                        SvgData(
                                                                            block.svgContent
                                                                        )
                                                                    ).listener(
                                                                        onError = { _, result ->
                                                                            Timber.e(
                                                                                result.throwable,
                                                                                "Coil failed to load SVG for MathBlock."
                                                                            )
                                                                        }).build()

                                                                val colorFilter =
                                                                    if (block.isFromMathJax) ColorFilter.tint(
                                                                        textStyle.color
                                                                    )
                                                                    else null

                                                                AsyncImage(
                                                                    model = imageRequest,
                                                                    contentDescription = block.altText
                                                                        ?: "Equation",
                                                                    modifier = imageModifier,
                                                                    contentScale = ContentScale.Fit,
                                                                    colorFilter = colorFilter,
                                                                    imageLoader = imageLoader
                                                                )
                                                            }
                                                        } else {
                                                            Timber.w(
                                                                "PaginatedReader: MathBlock has no SVG content, rendering alt text."
                                                            )
                                                            Text(
                                                                text = block.altText
                                                                    ?: "[Equation not available]",
                                                                style = textStyle,
                                                                modifier = paddingModifier
                                                            )
                                                        }
                                                    }

                                                    is ImageBlock -> {
                                                        val style = block.style
                                                        val finalImageModifier = Modifier
                                                            .then(
                                                                if (style.width != Dp.Unspecified) Modifier.width(
                                                                    style.width
                                                                )
                                                                else Modifier
                                                            )
                                                            .then(
                                                                if (style.maxWidth != Dp.Unspecified) Modifier.widthIn(
                                                                    max = style.maxWidth
                                                                )
                                                                else Modifier
                                                            )
                                                            .then(paddingModifier)

                                                        val colorFilter =
                                                            if (block.style.filter == "invert(100%)") {
                                                                val matrix = floatArrayOf(
                                                                    -1f,
                                                                    0f,
                                                                    0f,
                                                                    0f,
                                                                    255f,
                                                                    0f,
                                                                    -1f,
                                                                    0f,
                                                                    0f,
                                                                    255f,
                                                                    0f,
                                                                    0f,
                                                                    -1f,
                                                                    0f,
                                                                    255f,
                                                                    0f,
                                                                    0f,
                                                                    0f,
                                                                    1f,
                                                                    0f
                                                                )
                                                                ColorFilter.colorMatrix(
                                                                    ColorMatrix(matrix)
                                                                )
                                                            } else {
                                                                null
                                                            }
                                                        val context = LocalContext.current
                                                        val imageRequest =
                                                            Builder(context).data(File(block.path))
                                                                .listener(onSuccess = { _, _ ->
                                                                    Timber.d(
                                                                        "Coil successfully loaded image: ${block.path}"
                                                                    )
                                                                }, onError = { _, result ->
                                                                    Timber.e(
                                                                        result.throwable,
                                                                        "Coil FAILED to load image: ${block.path}"
                                                                    )
                                                                }).crossfade(true).build()

                                                        AsyncImage(
                                                            model = imageRequest,
                                                            contentDescription = block.altText
                                                                ?: "Image from EPUB",
                                                            modifier = finalImageModifier,
                                                            contentScale = ContentScale.Fit,
                                                            colorFilter = colorFilter
                                                        )
                                                    }

                                                    is SpacerBlock -> {
                                                        val border = block.style.border
                                                        if (border != null && border.width > 0.dp) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(
                                                                        border.width
                                                                    )
                                                                    .drawBehind {
                                                                        val strokeWidth =
                                                                            border.width.toPx()
                                                                        val pathEffect =
                                                                            when (border.style) {
                                                                                "dotted" -> PathEffect.dashPathEffect(
                                                                                    floatArrayOf(
                                                                                        strokeWidth,
                                                                                        strokeWidth * 2f
                                                                                    ), 0f
                                                                                )

                                                                                "dashed" -> PathEffect.dashPathEffect(
                                                                                    floatArrayOf(
                                                                                        strokeWidth * 3f,
                                                                                        strokeWidth * 2f
                                                                                    ), 0f
                                                                                )

                                                                                else -> null
                                                                            }
                                                                        drawLine(
                                                                            color = border.color,
                                                                            start = Offset(
                                                                                0f, strokeWidth / 2f
                                                                            ),
                                                                            end = Offset(
                                                                                size.width,
                                                                                strokeWidth / 2f
                                                                            ),
                                                                            strokeWidth = strokeWidth,
                                                                            pathEffect = pathEffect
                                                                        )
                                                                    })
                                                        } else {
                                                            Spacer(Modifier.height(block.height))
                                                        }
                                                    }

                                                    is TableBlock -> {
                                                        // Table-level margin/background/border/padding
                                                        // are already
                                                        // applied by the outer Box wrapper. Only set
                                                        // width here.
                                                        val tableModifier = paddingModifier

                                                        Column(modifier = tableModifier) {
                                                            block.rows.forEach { tableRow ->
                                                                Row(
                                                                    Modifier
                                                                        .fillMaxWidth()
                                                                        .height(
                                                                            IntrinsicSize.Min
                                                                        )
                                                                ) {
                                                                    val hasFixedWidths = tableRow.any {
                                                                        it.style.blockStyle.width != Dp.Unspecified
                                                                    }

                                                                    tableRow.forEach { cell ->
                                                                        val cellStyle =
                                                                            cell.style.blockStyle

                                                                        val cellContainerModifier =
                                                                            if (hasFixedWidths) {
                                                                                if (cellStyle.width != Dp.Unspecified) Modifier.width(
                                                                                    cellStyle.width
                                                                                )
                                                                                else Modifier.weight(
                                                                                    cell.colspan.toFloat(),
                                                                                    fill = true
                                                                                )
                                                                            } else {
                                                                                Modifier.weight(
                                                                                    cell.colspan.toFloat(),
                                                                                    fill = true
                                                                                )
                                                                            }

                                                                        val alignment =
                                                                            when (cell.style.paragraphStyle.textAlign) {
                                                                                TextAlign.Center -> Alignment.CenterHorizontally
                                                                                TextAlign.End -> Alignment.End
                                                                                else -> Alignment.Start
                                                                            }

                                                                        val cellModifier =
                                                                            cellContainerModifier
                                                                                .fillMaxHeight()
                                                                                .then(
                                                                                    if (cellStyle.backgroundColor.isSpecified) {
                                                                                        Modifier.background(
                                                                                            cellStyle.backgroundColor
                                                                                        )
                                                                                    } else {
                                                                                        Modifier
                                                                                    }
                                                                                )
                                                                                .then(cellStyle.border?.let { border ->
                                                                                    Modifier.border(
                                                                                        BorderStroke(
                                                                                            border.width,
                                                                                            border.color
                                                                                        )
                                                                                    )
                                                                                } ?: Modifier)
                                                                                .padding(
                                                                                    start = cellStyle.padding.left.coerceAtLeast(
                                                                                        0.dp
                                                                                    ),
                                                                                    top = cellStyle.padding.top.coerceAtLeast(
                                                                                        0.dp
                                                                                    ),
                                                                                    end = cellStyle.padding.right.coerceAtLeast(
                                                                                        0.dp
                                                                                    ),
                                                                                    bottom = cellStyle.padding.bottom.coerceAtLeast(
                                                                                        0.dp
                                                                                    )
                                                                                )

                                                                        Column(
                                                                            modifier = cellModifier,
                                                                            horizontalAlignment = alignment
                                                                        ) {
                                                                            val cellTextStyle =
                                                                                if (cell.isHeader) {
                                                                                    textStyle.copy(
                                                                                        fontWeight = FontWeight.Bold
                                                                                    )
                                                                                } else {
                                                                                    textStyle
                                                                                }

                                                                            cell.content.forEach { blockInCell ->
                                                                                when (blockInCell) {
                                                                                    is ParagraphBlock -> {
                                                                                        Text(
                                                                                            text = blockInCell.content,
                                                                                            style = cellTextStyle,
                                                                                            modifier = Modifier.fillMaxWidth()
                                                                                        )
                                                                                    }

                                                                                    is HeaderBlock -> {
                                                                                        Text(
                                                                                            text = blockInCell.content,
                                                                                            style = cellTextStyle.copy(
                                                                                                fontWeight = FontWeight.Bold
                                                                                            ),
                                                                                            modifier = Modifier.fillMaxWidth()
                                                                                        )
                                                                                    }

                                                                                    is ListItemBlock -> {
                                                                                        Row(
                                                                                            verticalAlignment = Alignment.Top
                                                                                        ) {
                                                                                            if (blockInCell.itemMarker != null) {
                                                                                                Text(
                                                                                                    text = blockInCell.itemMarker,
                                                                                                    style = cellTextStyle,
                                                                                                    modifier = Modifier.padding(
                                                                                                        end = 4.dp
                                                                                                    )
                                                                                                )
                                                                                            }
                                                                                            Text(
                                                                                                text = blockInCell.content,
                                                                                                style = cellTextStyle,
                                                                                                modifier = Modifier.weight(
                                                                                                    1f
                                                                                                )
                                                                                            )
                                                                                        }
                                                                                    }

                                                                                    is SpacerBlock -> {
                                                                                        val spacerModifier =
                                                                                            if (blockInCell.style.border != null) {
                                                                                                Modifier
                                                                                                    .fillMaxWidth()
                                                                                                    .height(
                                                                                                        blockInCell.style.border.width
                                                                                                    )
                                                                                                    .background(
                                                                                                        blockInCell.style.border.color
                                                                                                    )
                                                                                            } else {
                                                                                                Modifier.height(
                                                                                                    blockInCell.height
                                                                                                )
                                                                                            }
                                                                                        Spacer(
                                                                                            modifier = spacerModifier
                                                                                        )
                                                                                    }

                                                                                    is ImageBlock -> {
                                                                                        AsyncImage(
                                                                                            model = Builder(
                                                                                                LocalContext.current
                                                                                            ).data(
                                                                                                File(
                                                                                                    blockInCell.path
                                                                                                )
                                                                                            ).build(),
                                                                                            contentDescription = blockInCell.altText,
                                                                                            contentScale = ContentScale.Fit,
                                                                                            modifier = Modifier.fillMaxWidth()
                                                                                        )
                                                                                    }
                                                                                    // Catch-all for any
                                                                                    // other text-based
                                                                                    // content
                                                                                    is TextContentBlock -> {
                                                                                        Text(
                                                                                            text = blockInCell.content,
                                                                                            style = cellTextStyle,
                                                                                            modifier = Modifier.fillMaxWidth()
                                                                                        )
                                                                                    }

                                                                                    else -> {}
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    var chapterInfo by remember {
                                        mutableStateOf<Pair<String, Int?>?>(null)
                                    }
                                    LaunchedEffect(pageIndex) {
                                        chapterInfo = onGetChapterInfo(pageIndex)
                                    }

                                    ChapterLoadingPlaceholder(title = chapterInfo?.first)
                                }
                            }
                        }
                    }
                }
            }
            menuState?.let { state ->
                Popup(popupPositionProvider = remember(state.rect, density) {
                    SmartPopupPositionProvider(state.rect, density)
                }, onDismissRequest = { state.onHide() }) {
                    PaginatedTextSelectionMenu(
                        onCopy = {
                            isForDictionary = false
                            isForHighlight = false
                            state.onCopy()
                            state.onHide()
                        }, onSelectAll = {
                            state.onSelectAll?.invoke()
                            state.onHide()
                        }, onDictionary = {
                            isForDictionary = true
                            state.onCopy()
                            isForDictionary = false
                            state.onHide()
                        }, onHighlight = { color ->
                            Timber.d("Menu: Highlight option clicked. Color: ${color.id}")
                            isForHighlight = true
                            state.onCopy()
                            isForHighlight = false

                            capturedTextForAction?.let { text ->
                                val selectionRect = state.rect
                                Timber.d("Menu: Selection Rect: $selectionRect")

                                var geometricSuccess = false
                                val candidates = blockLayoutMap.filter { (_, triple) ->
                                    val (_, coords, _) = triple
                                    if (!coords.isAttached) return@filter false
                                    val pos = coords.positionInWindow()
                                    val size = coords.size.toSize()
                                    val blockRect = Rect(pos, size)
                                    val overlaps = blockRect.overlaps(selectionRect)
                                    overlaps
                                }

                                if (candidates.isNotEmpty()) {
                                    Timber.d(
                                        "Menu: Geometric candidates found: ${candidates.keys}"
                                    )
                                    try {
                                        val sorted = candidates.entries.sortedBy {
                                            it.value.second.positionInWindow().y
                                        }
                                        val (startCfi, startTriple) = sorted.first()
                                        val (endCfi, endTriple) = sorted.last()
                                        val (startLayout, startCoords, startAbsOffset) = startTriple
                                        val (endLayout, endCoords, endAbsOffset) = endTriple
                                        val localStart =
                                            startCoords.windowToLocal(selectionRect.topLeft)
                                        val localEnd = endCoords.windowToLocal(
                                            selectionRect.bottomRight
                                        )
                                        var finalStartOffset =
                                            startLayout.getOffsetForPosition(localStart)
                                        var finalEndOffset = endLayout.getOffsetForPosition(localEnd)
                                        var finalEndCfi = endCfi
                                        val startText = startLayout.layoutInput.text.text

                                        if (startCfi == endCfi) {
                                            val matches = mutableListOf<Int>()
                                            var idx = startText.indexOf(text)
                                            while (idx != -1) {
                                                matches.add(idx)
                                                idx = startText.indexOf(text, idx + 1)
                                            }
                                            if (matches.isNotEmpty()) {
                                                val bestMatch = matches.minBy {
                                                    abs(it - finalStartOffset)
                                                }
                                                finalStartOffset = bestMatch
                                                finalEndOffset = bestMatch + text.length
                                                Timber.d(
                                                    "Refined Single-Block Offset: $finalStartOffset"
                                                )
                                            }
                                        } else {
                                            val endText = endLayout.layoutInput.text.text

                                            fun findBestMatch(
                                                source: String,
                                                query: String,
                                                targetOffset: Int,
                                                isSuffix: Boolean
                                            ): Int {
                                                if (query.isEmpty()) return -1
                                                var bestIdx = -1
                                                var minDiff = Int.MAX_VALUE
                                                var idx = source.indexOf(query)
                                                while (idx != -1) {
                                                    val cmpPoint = if (isSuffix) idx + query.length
                                                    else idx
                                                    val diff = abs(cmpPoint - targetOffset)
                                                    if (diff < minDiff) {
                                                        minDiff = diff
                                                        bestIdx = idx
                                                    }
                                                    idx = source.indexOf(query, idx + 1)
                                                }
                                                return bestIdx
                                            }

                                            var sMatch = -1
                                            var eMatch = -1
                                            var usedSuffixLen = 0

                                            val maxChunk = minOf(text.length, 50)
                                            for (len in maxChunk downTo 3) {
                                                val prefix = text.take(len).trim()
                                                if (prefix.isNotEmpty()) {
                                                    val idx = findBestMatch(
                                                        startText,
                                                        prefix,
                                                        finalStartOffset,
                                                        isSuffix = false
                                                    )
                                                    if (idx != -1) {
                                                        sMatch = idx
                                                        Timber.d(
                                                            "Refined Start: Found prefix '$prefix' at $idx"
                                                        )
                                                        break
                                                    }
                                                }
                                            }

                                            for (len in maxChunk downTo 3) {
                                                val suffix = text.takeLast(len).trim()
                                                if (suffix.isNotEmpty()) {
                                                    val idx = findBestMatch(
                                                        endText,
                                                        suffix,
                                                        finalEndOffset,
                                                        isSuffix = true
                                                    )
                                                    if (idx != -1) {
                                                        eMatch = idx
                                                        usedSuffixLen = suffix.length
                                                        Timber.d(
                                                            "Refined End: Found suffix '$suffix' at $idx"
                                                        )
                                                        break
                                                    }
                                                }
                                            }

                                            if (sMatch != -1 && eMatch != -1) {
                                                finalStartOffset = sMatch
                                                finalEndOffset = eMatch + usedSuffixLen
                                            } else if (eMatch == -1) {
                                                Timber.d(
                                                    "Refined: Suffix not found in end block. Checking single block fit."
                                                )
                                                val fitIdx = startText.indexOf(text)
                                                if (fitIdx != -1) {
                                                    finalEndCfi = startCfi
                                                    finalStartOffset = fitIdx
                                                    finalEndOffset = fitIdx + text.length
                                                }
                                            }
                                        }

                                        val absStart = finalStartOffset + startAbsOffset
                                        val absEnd =
                                            finalEndOffset + if (startCfi == finalEndCfi) startAbsOffset
                                            else endAbsOffset

                                        val rangeCfi = if (startCfi == finalEndCfi) {
                                            val actualStart = minOf(absStart, absEnd)
                                            val actualEnd = maxOf(absStart, absEnd).coerceAtLeast(
                                                actualStart + 1
                                            )
                                            "$startCfi:$actualStart|$finalEndCfi:$actualEnd"
                                        } else {
                                            "$startCfi:$absStart|$finalEndCfi:$absEnd"
                                        }

                                        Timber.d("Menu: Geometric Success. CFI: $rangeCfi")
                                        onHighlightCreated(rangeCfi, text, color.id)
                                        geometricSuccess = true
                                    } catch (e: Exception) {
                                        Timber.e(e, "Menu: Geometric calculation failed.")
                                    }
                                }

                                if (!geometricSuccess) {
                                    Timber.w("Menu: Falling back to text search.")
                                    val pageContent = onGetPage(pagerState.currentPage)
                                    val textBlocks =
                                        pageContent?.content?.filterIsInstance<TextContentBlock>()
                                            ?.filter { it.cfi != null } ?: emptyList()

                                    var startBlock: TextContentBlock? = null
                                    var endBlock: TextContentBlock? = null
                                    var startOffsetRel = -1
                                    var endOffsetRel = -1

                                    for (block in textBlocks) {
                                        val content = block.content.text
                                        val idx = content.indexOf(text)
                                        if (idx != -1) {
                                            startBlock = block
                                            endBlock = block
                                            startOffsetRel = idx
                                            endOffsetRel = idx + text.length
                                            break
                                        }
                                    }

                                    endBlock = startBlock

                                    if (startBlock != null) {
                                        val startAbs =
                                            startBlock.startCharOffsetInSource + startOffsetRel
                                        val endAbs =
                                            endBlock.startCharOffsetInSource + (if (endOffsetRel != -1) endOffsetRel
                                            else startOffsetRel + text.length)
                                        onHighlightCreated(
                                            "${startBlock.cfi}:$startAbs|${endBlock.cfi}:$endAbs",
                                            text,
                                            color.id
                                        )
                                    }
                                }
                            }
                            state.onHide()
                        }, onDelete = null, isProUser = isProUser, isOss = isOss,
                        activeHighlightPalette = activeHighlightPalette,
                        onOpenPaletteManager = { showPaletteManager = true }
                    )
                }
            }

            if (activeSelection != null) {
                val sel = activeSelection!!
                Popup(popupPositionProvider = remember(sel.rect, density) {
                    SmartPopupPositionProvider(sel.rect, density)
                }, onDismissRequest = { activeSelection = null }) {
                    PaginatedTextSelectionMenu(
                        onCopy = {
                            val clipboardManager =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Copied Text", sel.text)
                            clipboardManager.setPrimaryClip(clip)
                            activeSelection = null
                        }, onSelectAll = null, onDictionary = {
                            if (isProUser || countWords(sel.text) <= 1) {
                                onWordSelectedForAiDefinition(sel.text)
                            } else {
                                onShowDictionaryUpsellDialog()
                            }
                            activeSelection = null
                        }, onHighlight = { color ->
                            Timber.d(
                                "CustomSelection: Highlight clicked. Text: '${sel.text}', BaseCFI: ${sel.baseCfi}, StartOffset: ${sel.startOffset}"
                            )
                            val finalCfi = if (sel.startOffset > 0) "${sel.baseCfi}:${sel.startOffset}"
                            else sel.baseCfi
                            onHighlightCreated(finalCfi, sel.text, color.id)
                            activeSelection = null
                        }, onDelete = null, isProUser = isProUser, isOss = isOss,
                        activeHighlightPalette = activeHighlightPalette,
                        onOpenPaletteManager = { showPaletteManager = true }
                    )
                }
            }

            if (showColorPickerDialog != null) {
                AlertDialog(
                    onDismissRequest = { showColorPickerDialog = null },
                    title = { Text("Select Color") },
                    text = {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(HighlightColor.entries) { colorOption ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(colorOption.color, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable {
                                            onUpdatePalette(showColorPickerDialog!!, colorOption)
                                            showColorPickerDialog = null
                                        }
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showColorPickerDialog = null }) { Text("Close") } }
                )
            }

            // Edit Menu (Delete)
            if (activeHighlightForMenu != null) {
                val (highlight, rect) = activeHighlightForMenu!!
                Popup(popupPositionProvider = remember(rect, density) {
                    SmartPopupPositionProvider(rect, density)
                }, onDismissRequest = { activeHighlightForMenu = null }) {
                    PaginatedTextSelectionMenu(
                        onCopy = {
                            val clipboardManager =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Copied Text", highlight.text)
                            clipboardManager.setPrimaryClip(clip)
                            activeHighlightForMenu = null
                        },
                        onSelectAll = null,
                        onDictionary = {
                            if (isProUser || countWords(highlight.text) <= 1) {
                                onWordSelectedForAiDefinition(highlight.text)
                            } else {
                                onShowDictionaryUpsellDialog()
                            }
                            activeHighlightForMenu = null
                        },
                        onHighlight = { color ->
                            Timber.d("Menu: Updating highlight color to ${color.id}")
                            onHighlightDeleted(highlight.cfi)
                            onHighlightCreated(highlight.cfi, highlight.text, color.id)
                            activeHighlightForMenu = null
                        },
                        onDelete = {
                            onHighlightDeleted(highlight.cfi)
                            activeHighlightForMenu = null
                        },
                        isProUser = isProUser,
                        isOss = isOss,
                        activeHighlightPalette = activeHighlightPalette,
                        onOpenPaletteManager = { showPaletteManager = true }
                    )
                }
            }

            if (showPaletteManager) {
                PaletteManagerDialog(
                    currentPalette = activeHighlightPalette,
                    onDismiss = { showPaletteManager = false },
                    onSave = { newPalette ->
                        newPalette.forEachIndexed { index, color ->
                            onUpdatePalette(index, color)
                        }
                        showPaletteManager = false
                    }
                )
            }
        } else {
            Timber.w("Book has no pages to display.")
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This book has no content to display.")
            }
        }
    }
}

@Composable
private fun ChapterLoadingPlaceholder(title: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(16.dp))
            }
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Preparing chapter.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PaginatedTextSelectionMenu(
    onCopy: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onDictionary: () -> Unit,
    onHighlight: ((HighlightColor) -> Unit)?,
    onDelete: (() -> Unit)?,
    @Suppress("unused") isProUser: Boolean,
    isOss: Boolean,
    activeHighlightPalette: List<HighlightColor> = emptyList(),
    onOpenPaletteManager: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            // 1. Colors Row
            if (onHighlight != null) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 12.dp, horizontal = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeHighlightPalette.forEach { colorEnum ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(32.dp)
                                .background(colorEnum.color, CircleShape)
                                .clickable { onHighlight(colorEnum) }
                        )
                    }

                    if (onOpenPaletteManager != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SpectrumButton(
                            onClick = onOpenPaletteManager,
                            size = 32.dp
                        )
                    }
                }
                HorizontalDivider()
            }

            // 2. Delete Option
            if (onDelete != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDelete)
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
                        "Remove",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()
            }

            // 3. Copy Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.copy),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Copy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 4. Select All Option
            if (onSelectAll != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectAll)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.select_all),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Select All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 5. Dictionary Option
            if (!isOss) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDictionary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.dictionary),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Dictionary",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderFlexChildBlock(
    childBlock: ContentBlock,
    textStyle: TextStyle,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    textMeasurer: TextMeasurer,
    onLinkClickCallback: (String) -> Unit,
    onGeneralTapCallback: (Offset) -> Unit,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    blockLayoutMap: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, Int>>,
    density: Density,
    imageLoader: ImageLoader
) {
    @Composable
    fun renderTextBlock(block: TextContentBlock) {
        val searchHighlighted =
            highlightQueryInText(block.content, searchQuery, searchHighlightColor)
        val finalContent = if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
            buildAnnotatedString {
                append(searchHighlighted)
                val blockStartAbs = block.startCharOffsetInSource
                val blockEndAbs = block.startCharOffsetInSource + searchHighlighted.length
                val highlightStartAbs = ttsHighlightInfo.offset
                val highlightEndAbs = ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                val intersectionStartAbs = maxOf(blockStartAbs, highlightStartAbs)
                val intersectionEndAbs = minOf(blockEndAbs, highlightEndAbs)

                if (intersectionStartAbs < intersectionEndAbs) {
                    val highlightStartRelative = intersectionStartAbs - blockStartAbs
                    val highlightEndRelative = intersectionEndAbs - blockStartAbs
                    addStyle(
                        style = SpanStyle(background = ttsHighlightColor),
                        start = highlightStartRelative,
                        end = highlightEndRelative
                    )
                }
            }
        } else {
            searchHighlighted
        }

        // Apply block specific styles (like header font weight)
        val finalStyle = if (block is HeaderBlock) {
            textStyle.copy(
                fontWeight = FontWeight.Bold, fontSize = textStyle.fontSize * block.level.let {
                    when (it) {
                        1 -> 1.5f
                        2 -> 1.4f
                        3 -> 1.3f
                        4 -> 1.2f
                        5 -> 1.1f
                        else -> 1.0f
                    }
                })
        } else {
            textStyle
        }

        TextWithEmphasis(
            text = finalContent,
            style = finalStyle,
            modifier = Modifier,
            textMeasurer = textMeasurer,
            onLinkClick = onLinkClickCallback,
            onGeneralTap = onGeneralTapCallback,
            block = block,
            userHighlights = userHighlights,
            activeSelection = activeSelection,
            onSelectionChange = onSelectionChange,
            onHighlightClick = onHighlightClick,
            isDarkTheme = isDarkTheme,
            onRegisterLayout = { layout, coords ->
                block.cfi?.let { cfi ->
                    blockLayoutMap[cfi] = Triple(layout, coords, block.startCharOffsetInSource)
                }
            })
    }

    when (childBlock) {
        is ListItemBlock -> {
            Row(modifier = Modifier, verticalAlignment = Alignment.Top) {
                val markerAreaModifier = Modifier
                    .width(32.dp)
                    .padding(end = 8.dp)

                if (childBlock.itemMarkerImage != null) {
                    val imageRequest =
                        Builder(LocalContext.current).data(File(childBlock.itemMarkerImage))
                            .crossfade(true).build()
                    val imageSize = with(density) { (textStyle.fontSize.value * 0.8f).sp.toDp() }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "List item marker",
                        modifier = markerAreaModifier.height(imageSize),
                        alignment = Alignment.CenterEnd,
                        contentScale = ContentScale.FillHeight
                    )
                } else if (childBlock.itemMarker != null) {
                    Text(
                        text = childBlock.itemMarker,
                        style = textStyle.copy(textAlign = TextAlign.End),
                        modifier = markerAreaModifier
                    )
                }

                // Reuse text rendering logic
                renderTextBlock(childBlock)
            }
        }

        is ParagraphBlock -> renderTextBlock(childBlock)
        is HeaderBlock -> renderTextBlock(childBlock)
        is QuoteBlock -> renderTextBlock(childBlock)
        is TextContentBlock -> renderTextBlock(childBlock)
        is ImageBlock -> {
            val style = childBlock.style
            val imageModifier = Modifier
                .then(
                    if (style.width != Dp.Unspecified) Modifier.width(style.width)
                    else Modifier
                )
                .then(
                    if (style.maxWidth != Dp.Unspecified) Modifier.widthIn(max = style.maxWidth)
                    else Modifier
                )

            val colorFilter = if (childBlock.style.filter == "invert(100%)") {
                val matrix = floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
                ColorFilter.colorMatrix(ColorMatrix(matrix))
            } else null

            AsyncImage(
                model = Builder(LocalContext.current).data(File(childBlock.path)).crossfade(true)
                    .build(),
                contentDescription = childBlock.altText,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                imageLoader = imageLoader
            )
        }

        is SpacerBlock -> {
            val border = childBlock.style.border
            if (border != null && border.width > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(border.width)
                        .background(border.color)
                )
            } else {
                Spacer(Modifier.height(childBlock.height))
            }
        }

        is TableBlock -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                childBlock.rows.forEach { tableRow ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        val hasFixedWidths =
                            tableRow.any { it.style.blockStyle.width != Dp.Unspecified }

                        tableRow.forEach { cell ->
                            val cellStyle = cell.style.blockStyle
                            val cellModifier = Modifier
                                .fillMaxHeight()
                                .then(
                                    if (hasFixedWidths && cellStyle.width != Dp.Unspecified) Modifier.width(
                                        cellStyle.width
                                    )
                                    else Modifier.weight(
                                        cell.colspan.toFloat(), fill = true
                                    )
                                )
                                .then(
                                    if (cellStyle.backgroundColor.isSpecified) Modifier.background(
                                        cellStyle.backgroundColor
                                    )
                                    else Modifier
                                )
                                .then(cellStyle.border?.let {
                                    Modifier.border(
                                        BorderStroke(it.width, it.color)
                                    )
                                } ?: Modifier)
                                .padding(
                                    start = cellStyle.padding.left.coerceAtLeast(
                                        0.dp
                                    ),
                                    top = cellStyle.padding.top.coerceAtLeast(0.dp),
                                    end = cellStyle.padding.right.coerceAtLeast(
                                        0.dp
                                    ),
                                    bottom = cellStyle.padding.bottom.coerceAtLeast(
                                        0.dp
                                    )
                                )

                            val alignment = when (cell.style.paragraphStyle.textAlign) {
                                TextAlign.Center -> Alignment.CenterHorizontally
                                TextAlign.End -> Alignment.End
                                else -> Alignment.Start
                            }

                            Column(modifier = cellModifier, horizontalAlignment = alignment) {
                                val cellTextStyle =
                                    if (cell.isHeader) textStyle.copy(fontWeight = FontWeight.Bold)
                                    else textStyle
                                cell.content.forEach { blockInCell ->
                                    if (blockInCell is TextContentBlock) {
                                        Text(
                                            text = blockInCell.content,
                                            style = cellTextStyle,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else if (blockInCell is ImageBlock) {
                                        AsyncImage(
                                            model = Builder(LocalContext.current).data(
                                                File(
                                                    blockInCell.path
                                                )
                                            ).build(),
                                            contentDescription = blockInCell.altText,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        else -> {
            Timber.w(
                "FlexContainerBlock child type still not supported: ${childBlock::class.simpleName}"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.realisticBookPage(
    pagerState: PagerState,
    pageIndex: Int,
    isDarkTheme: Boolean,
    touchY: Float?
): Modifier = composed {
    val frontPath = remember { Path() }
    val backPath = remember { Path() }
    val reflectedScreenPath = remember { Path() }

    this
        .graphicsLayer {
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction

            if (pageOffset <= 1f && pageOffset > -1f) {
                translationX = -pageOffset * size.width
            }

            if (pageOffset != 0f) {
                shadowElevation = 10f
                shape = androidx.compose.ui.graphics.RectangleShape
                clip = false
            }
        }
        .drawWithContent {
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
            val paperColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFFFFFF)

            if (abs(pageOffset) < 0.001f) {
                drawRect(color = paperColor)
                drawContent()
            }
            else if (pageOffset < 0f && pageOffset > -1f) {
                val progress = -pageOffset
                val w = size.width
                val h = size.height

                val startY = touchY ?: h
                val centerDist = ((startY - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)

                val cornerY = if (centerDist >= 0) h else 0f

                val dragX = w - w * 2.2f * progress
                val dragY = cornerY - h * 0.5f * progress * centerDist

                val midX = (w + dragX) / 2f
                val midY = (cornerY + dragY) / 2f

                val dx = w - dragX
                val dy = cornerY - dragY
                val nLen = kotlin.math.sqrt(dx * dx + dy * dy)

                if (nLen > 0f) {
                    val nx = dx / nLen
                    val ny = dy / nLen

                    val huge = w * 3f
                    val vx = -ny

                    val p1X = midX + vx * huge
                    val p1Y = midY + nx * huge
                    val p2X = midX - vx * huge
                    val p2Y = midY - nx * huge

                    frontPath.rewind()
                    frontPath.moveTo(p1X, p1Y)
                    frontPath.lineTo(p2X, p2Y)
                    frontPath.lineTo(p2X - nx * huge, p2Y - ny * huge)
                    frontPath.lineTo(p1X - nx * huge, p1Y - ny * huge)
                    frontPath.close()

                    clipPath(frontPath) {
                        drawRect(color = paperColor)
                        this@drawWithContent.drawContent()
                    }

                    val shadowWidth = (40.dp.toPx() * (1f - progress)).coerceAtLeast(10.dp.toPx())
                    backPath.rewind()
                    backPath.moveTo(p1X, p1Y)
                    backPath.lineTo(p2X, p2Y)
                    backPath.lineTo(p2X + nx * huge, p2Y + ny * huge)
                    backPath.lineTo(p1X + nx * huge, p1Y + ny * huge)
                    backPath.close()

                    val dropShadowBrush = Brush.linearGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
                        start = Offset(midX, midY),
                        end = Offset(midX + nx * shadowWidth, midY + ny * shadowWidth)
                    )
                    clipRect(0f, 0f, w, h) {
                        drawPath(backPath, dropShadowBrush)
                    }

                    fun reflect(px: Float, py: Float): Offset {
                        val vX = px - midX
                        val vY = py - midY
                        val dist = vX * nx + vY * ny
                        return Offset(px - 2 * dist * nx, py - 2 * dist * ny)
                    }

                    val rTL = reflect(0f, 0f)
                    val rTR = reflect(w, 0f)
                    val rBR = reflect(w, h)
                    val rBL = reflect(0f, h)

                    reflectedScreenPath.rewind()
                    reflectedScreenPath.moveTo(rTL.x, rTL.y)
                    reflectedScreenPath.lineTo(rTR.x, rTR.y)
                    reflectedScreenPath.lineTo(rBR.x, rBR.y)
                    reflectedScreenPath.lineTo(rBL.x, rBL.y)
                    reflectedScreenPath.close()

                    clipRect(0f, 0f, w, h) {
                        clipPath(frontPath) {
                            val flapColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)
                            drawPath(reflectedScreenPath, color = flapColor)

                            val innerShadowWidth = shadowWidth * 0.7f
                            val innerShadowBrush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                start = Offset(midX, midY),
                                end = Offset(midX - nx * innerShadowWidth, midY - ny * innerShadowWidth)
                            )
                            drawPath(reflectedScreenPath, innerShadowBrush)

                            drawPath(
                                path = reflectedScreenPath,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        drawLine(
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            start = Offset(p1X, p1Y),
                            end = Offset(p2X, p2Y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                } else {
                    drawRect(color = paperColor)
                    drawContent()
                }
            }
            else if (pageOffset > 0f && pageOffset <= 1f) {
                drawRect(color = paperColor)
                drawContent()
                val dimAlpha = (0.25f * pageOffset).coerceIn(0f, 0.4f)
                drawRect(color = Color.Black.copy(alpha = dimAlpha))
            }
            else {
                drawRect(color = paperColor)
                drawContent()
            }
        }
}