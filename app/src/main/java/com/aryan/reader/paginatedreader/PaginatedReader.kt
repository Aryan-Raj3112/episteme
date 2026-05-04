// PaginatedReader.kt
@file:Suppress("VariableNeverRead")

package com.aryan.reader.paginatedreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.unit.isSpecified
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest.Builder
import com.aryan.reader.R
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.countWords
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.PaginatedTextSelectionMenu
import com.aryan.reader.epubreader.PaletteManagerDialog
import com.aryan.reader.epubreader.ReaderTextAlign
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.net.URI
import java.net.URLDecoder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PaginatedSelection(
    val startBlockIndex: Int,
    val endBlockIndex: Int,
    val startBaseCfi: String,
    val endBaseCfi: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val rect: Rect,
    val startPageIndex: Int,
    val endPageIndex: Int,
    val startBlockCharOffset: Int = 0,
    val endBlockCharOffset: Int = 0,
    val textPerBlock: Map<String, String> = emptyMap()
)

private data class SelectionBlockKey(
    val pageIndex: Int,
    val blockIndex: Int,
    val blockCharOffset: Int
)

private fun buildSelectionBlockKey(
    pageIndex: Int,
    blockIndex: Int,
    blockCharOffset: Int
): String = "${pageIndex}_${blockIndex}_${blockCharOffset}"

private fun parseSelectionBlockKey(key: String): SelectionBlockKey? {
    val parts = key.split("_")
    if (parts.size != 3) return null
    return SelectionBlockKey(
        pageIndex = parts[0].toIntOrNull() ?: return null,
        blockIndex = parts[1].toIntOrNull() ?: return null,
        blockCharOffset = parts[2].toIntOrNull() ?: return null
    )
}

private fun compareSelectionBlockKeys(
    firstKey: String,
    secondKey: String
): Int {
    val first = parseSelectionBlockKey(firstKey)
    val second = parseSelectionBlockKey(secondKey)

    if (first == null && second == null) return firstKey.compareTo(secondKey)
    if (first == null) return 1
    if (second == null) return -1

    return compareValuesBy(
        first,
        second,
        SelectionBlockKey::pageIndex,
        SelectionBlockKey::blockIndex,
        SelectionBlockKey::blockCharOffset
    )
}

private fun getTextBlockCharOffset(block: TextContentBlock): Int = when (block) {
    is ParagraphBlock -> block.startCharOffsetInSource
    is HeaderBlock -> block.startCharOffsetInSource
    is QuoteBlock -> block.startCharOffsetInSource
    is ListItemBlock -> block.startCharOffsetInSource
}

private fun headerFontScale(level: Int): Float = when (level) {
    1 -> 1.5f
    2 -> 1.4f
    3 -> 1.3f
    4 -> 1.2f
    5 -> 1.1f
    else -> 1.0f
}

private const val WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER = 1.2f

private fun paginationLineHeightMultiplierForWebViewSetting(multiplier: Float): Float {
    return if (abs(multiplier - 1.0f) < 0.001f) WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER else multiplier
}

private fun createHeaderTextStyle(
    baseStyle: TextStyle,
    level: Int,
    textAlign: TextAlign?
): TextStyle {
    val scale = headerFontScale(level)
    val scaledFontSize = baseStyle.fontSize * scale
    val scaledLineHeight = if (baseStyle.lineHeight != TextUnit.Unspecified) {
        baseStyle.lineHeight * scale
    } else {
        scaledFontSize * 1.2f
    }

    return baseStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = scaledFontSize,
        lineHeight = scaledLineHeight,
        textAlign = textAlign ?: baseStyle.textAlign
    )
}

private fun compareBlockPositionsOnPage(
    firstBlockIndex: Int,
    firstBlockCharOffset: Int,
    secondBlockIndex: Int,
    secondBlockCharOffset: Int
): Int = when {
    firstBlockIndex != secondBlockIndex -> firstBlockIndex.compareTo(secondBlockIndex)
    else -> firstBlockCharOffset.compareTo(secondBlockCharOffset)
}

private fun isBlockSelectedOnPage(
    block: TextContentBlock,
    pageIndex: Int,
    selection: PaginatedSelection
): Boolean {
    if (pageIndex < selection.startPageIndex || pageIndex > selection.endPageIndex) return false
    if (pageIndex > selection.startPageIndex && pageIndex < selection.endPageIndex) return true

    val blockCharOffset = getTextBlockCharOffset(block)
    val afterStart = if (pageIndex == selection.startPageIndex) {
        compareBlockPositionsOnPage(
            block.blockIndex,
            blockCharOffset,
            selection.startBlockIndex,
            selection.startBlockCharOffset
        ) >= 0
    } else {
        true
    }
    val beforeEnd = if (pageIndex == selection.endPageIndex) {
        compareBlockPositionsOnPage(
            block.blockIndex,
            blockCharOffset,
            selection.endBlockIndex,
            selection.endBlockCharOffset
        ) <= 0
    } else {
        true
    }

    return afterStart && beforeEnd
}

class ReactiveBlockMap(
    private val delegate: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>> = mutableStateMapOf()
) : MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>> by delegate {
    var tick by mutableIntStateOf(0)

    override fun put(key: String, value: Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>): Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? {
        tick++
        return delegate.put(key, value)
    }

    override fun remove(key: String): Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? {
        tick++
        return delegate.remove(key)
    }

    override fun clear() {
        tick++
        delegate.clear()
    }
}

data class PendingCrossPageSelection(val fromPageIndex: Int)

enum class SelectionHandle { START, END }

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

private fun computeImageRenderSizePx(
    block: ImageBlock,
    density: Density,
    maxWidthPx: Float,
    imageSizeMultiplier: Float
): Pair<Float, Float> {
    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f || intrinsicHeight <= 0f) {
        return 0f to 0f
    }

    val aspectRatio = intrinsicHeight / intrinsicWidth
    val baseWidth = with(density) {
        if (block.style.width.isSpecified && block.style.width > 0.dp) {
            block.style.width.toPx()
        } else {
            maxWidthPx
        }
    }

    var scaledWidth = baseWidth * imageSizeMultiplier
    if (block.style.maxWidth.isSpecified && block.style.maxWidth > 0.dp) {
        scaledWidth = scaledWidth.coerceAtMost(with(density) { block.style.maxWidth.toPx() } * imageSizeMultiplier)
    }
    scaledWidth = scaledWidth.coerceAtMost(maxWidthPx)

    return scaledWidth to (scaledWidth * aspectRatio)
}

private fun computeImageRenderSizeDp(
    block: ImageBlock,
    density: Density,
    maxWidthDp: Dp,
    imageSizeMultiplier: Float
): Pair<Dp, Dp>? {
    val (widthPx, heightPx) = computeImageRenderSizePx(
        block = block,
        density = density,
        maxWidthPx = with(density) { maxWidthDp.toPx() },
        imageSizeMultiplier = imageSizeMultiplier
    )
    if (widthPx <= 0f || heightPx <= 0f) return null
    return with(density) { widthPx.toDp() to heightPx.toDp() }
}

private fun imageBlockContentAlignment(style: BlockStyle): Alignment {
    return when {
        style.float == "right" || style.horizontalAlign == "right" || style.horizontalAlign == "end" -> Alignment.CenterEnd
        style.float == "left" || style.horizontalAlign == "left" || style.horizontalAlign == "start" -> Alignment.CenterStart
        else -> Alignment.Center
    }
}

private fun tableCellImageModifier(
    block: ImageBlock,
    density: Density,
    imageSizeMultiplier: Float
): Modifier {
    val baseModifier = if (block.style.width.isSpecified && block.style.width > 0.dp) {
        Modifier.width(block.style.width * imageSizeMultiplier)
    } else {
        Modifier.fillMaxWidth(imageSizeMultiplier.coerceIn(0f, 1f))
    }

    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    val sizedModifier = if (
        intrinsicWidth != null &&
        intrinsicHeight != null &&
        intrinsicWidth > 0f &&
        intrinsicHeight > 0f
    ) {
        baseModifier.aspectRatio(intrinsicWidth / intrinsicHeight)
    } else {
        baseModifier.height(
            if (block.expectedHeight > 0) {
                with(density) { (block.expectedHeight * imageSizeMultiplier).toDp() }
            } else {
                250.dp
            }
        )
    }

    return if (block.style.maxWidth.isSpecified && block.style.maxWidth > 0.dp) {
        sizedModifier.widthIn(max = block.style.maxWidth * imageSizeMultiplier)
    } else {
        sizedModifier
    }
}

@Composable
private fun WrappingContentLayout(
    block: WrappingContentBlock,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
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
            computeImageRenderSizePx(
                block = block.floatedImage,
                density = density,
                maxWidthPx = constraints.maxWidth.toFloat(),
                imageSizeMultiplier = imageSizeMultiplier
            )
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
    modifier: Modifier = Modifier,
    book: EpubBook,
    isDarkTheme: Boolean,
    effectiveBg: Color,
    effectiveText: Color,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    searchQuery: String,
    fontSizeMultiplier: Float,
    lineHeightMultiplier: Float,
    paragraphGapMultiplier: Float,
    imageSizeMultiplier: Float,
    horizontalMarginMultiplier: Float,
    verticalMarginMultiplier: Float,
    fontFamily: FontFamily,
    textAlign: ReaderTextAlign,
    ttsHighlightInfo: TtsHighlightInfo?,
    initialChapterIndexInBook: Int?,
    onPaginatorReady: (IPaginator) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onFootnoteRequested: (String) -> Unit,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    activeTextureId: String? = null,
    activeTextureAlpha: Float = 0.55f
) {
    LaunchedEffect(userHighlights) {
        Timber.d("PaginatedReaderScreen: Received ${userHighlights.size} highlights.")
        userHighlights.forEach {
            Timber.d(" -> Received Highlight: CFI=${it.cfi}, Text='${it.text.take(20)}...'")
        }
    }

    val context = LocalContext.current
    val textureBitmap = remember(activeTextureId) {
        loadReaderTextureBitmap(context, activeTextureId)
    }

    val textureModifier = if (textureBitmap != null) {
        Modifier.drawBehind {
            val brush = ShaderBrush(
                ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)
            )
            drawRect(brush = brush, blendMode = BlendMode.SrcOver, alpha = activeTextureAlpha.coerceIn(0f, 1f))
        }
    } else Modifier

    var isNavigatingByLink by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(effectiveBg)) {
        val textMeasurer = rememberTextMeasurer()
        val baseTextStyle = MaterialTheme.typography.bodyLarge

        var debouncedFontSizeMult by remember { mutableFloatStateOf(fontSizeMultiplier) }
        var debouncedLineHeightMult by remember { mutableFloatStateOf(lineHeightMultiplier) }
        var debouncedParagraphGapMult by remember { mutableFloatStateOf(paragraphGapMultiplier) }
        var debouncedImageSizeMult by remember { mutableFloatStateOf(imageSizeMultiplier) }
        var debouncedHorizontalMarginMult by remember { mutableFloatStateOf(horizontalMarginMultiplier) }
        var debouncedVerticalMarginMult by remember { mutableFloatStateOf(verticalMarginMultiplier) }
        var debouncedFontFamily by remember { mutableStateOf(fontFamily) }
        var debouncedTextAlign by remember { mutableStateOf(textAlign) }

        var anchorLocatorForReconfig by remember { mutableStateOf<Locator?>(null) }
        val currentPaginatorRef = remember { mutableStateOf<IPaginator?>(null) }

        val previousState = remember {
            arrayOf<Any>(this.constraints, isDarkTheme, effectiveBg, effectiveText)
        }

        if (previousState[0] != this.constraints ||
            previousState[1] != isDarkTheme ||
            previousState[2] != effectiveBg ||
            previousState[3] != effectiveText
        ) {
            val activePaginator = currentPaginatorRef.value
            if (activePaginator is BookPaginator) {
                val currentPage = pagerState.currentPage
                val locator = activePaginator.getLocatorForPage(currentPage)
                anchorLocatorForReconfig = locator

                Timber.tag("ThemeReconfig").d("""
            RECONFIG DETECTED
            - Reason: ${if (previousState[0] != this.constraints) "Constraints" else "Theme/Colors"}
            - Current Page: $currentPage
            - Saved Locator: $locator
        """.trimIndent())
            }
            previousState[0] = this.constraints
            previousState[1] = isDarkTheme
            previousState[2] = effectiveBg
            previousState[3] = effectiveText
        }

        val textStyle = remember(
            baseTextStyle, effectiveText,
            debouncedFontSizeMult,
            debouncedLineHeightMult,
            debouncedFontFamily
        ) {
            val adjustedFontSize = baseTextStyle.fontSize * debouncedFontSizeMult
            val adjustedLineHeight = adjustedFontSize * paginationLineHeightMultiplierForWebViewSetting(debouncedLineHeightMult)

            baseTextStyle.copy(
                color = effectiveText,
                fontSize = adjustedFontSize,
                lineHeight = adjustedLineHeight,
                fontFamily = debouncedFontFamily,
                lineBreak = LineBreak.Paragraph,
                letterSpacing = TextUnit.Unspecified,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None
                )
            )
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                Timber.tag("PageTurnDiag").i("Pager Settled: Now on page $page at ${System.currentTimeMillis()}")
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
                Timber.tag("PageTurnDiag").d("Pager Scroll State: isScrolling=$isScrolling")
            }
        }

        LaunchedEffect(fontSizeMultiplier, lineHeightMultiplier, paragraphGapMultiplier, imageSizeMultiplier, horizontalMarginMultiplier, verticalMarginMultiplier, fontFamily, textAlign) {
            if (fontSizeMultiplier != debouncedFontSizeMult ||
                lineHeightMultiplier != debouncedLineHeightMult ||
                paragraphGapMultiplier != debouncedParagraphGapMult ||
                imageSizeMultiplier != debouncedImageSizeMult ||
                horizontalMarginMultiplier != debouncedHorizontalMarginMult ||
                verticalMarginMultiplier != debouncedVerticalMarginMult ||
                fontFamily != debouncedFontFamily ||
                textAlign != debouncedTextAlign
            ) {
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
                debouncedParagraphGapMult = paragraphGapMultiplier
                debouncedImageSizeMult = imageSizeMultiplier
                debouncedHorizontalMarginMult = horizontalMarginMultiplier
                debouncedVerticalMarginMult = verticalMarginMultiplier
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
        val requestedHorizontalPadding = 16.dp * debouncedHorizontalMarginMult
        val requestedVerticalPadding = 16.dp * debouncedVerticalMarginMult
        val effectiveReaderPadding =
            remember(this.constraints, density, requestedHorizontalPadding, requestedVerticalPadding) {
                val requestedHorizontalPaddingPx = with(density) { requestedHorizontalPadding.roundToPx() }
                val requestedVerticalPaddingPx = with(density) { requestedVerticalPadding.roundToPx() }
                val minReadableWidthPx = with(density) { 96.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxWidth)
                val minReadableHeightPx = with(density) { 160.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxHeight)
                val horizontalPaddingPx = requestedHorizontalPaddingPx.coerceAtMost(
                    ((this.constraints.maxWidth - minReadableWidthPx) / 2).coerceAtLeast(0)
                )
                val verticalPaddingPx = requestedVerticalPaddingPx.coerceAtMost(
                    ((this.constraints.maxHeight - minReadableHeightPx) / 2).coerceAtLeast(0)
                )
                with(density) {
                    horizontalPaddingPx.toDp() to verticalPaddingPx.toDp()
                }
            }
        val horizontalPadding = effectiveReaderPadding.first
        val verticalPadding = effectiveReaderPadding.second

        val textConstraints =
            remember(this.constraints, density, horizontalPadding, verticalPadding) {
                val horizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                val finalConstraints = this.constraints.copy(
                    minWidth = 0,
                    maxWidth = (this.constraints.maxWidth - (2 * horizontalPaddingPx)).coerceAtLeast(1),
                    minHeight = 0,
                    maxHeight = (this.constraints.maxHeight - (2 * verticalPaddingPx)).coerceAtLeast(1)
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
        val paginator = remember(book, textConstraints, isDarkTheme, textStyle, userTextAlign, effectiveBg, effectiveText, debouncedParagraphGapMult) {
        val userAgentStylesheet = UserAgentStylesheet.default
            var allRules = OptimizedCssRules()
            val allFontFaces = mutableListOf<FontFaceInfo>()

            val uaResult = CssParser.parse(
                cssContent = userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = textStyle.fontSize.value,
                density = density.density,
                constraints = textConstraints,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = effectiveBg,
                themeTextColor = effectiveText
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
                    isDarkTheme = isDarkTheme,
                    themeBackgroundColor = effectiveBg,
                    themeTextColor = effectiveText
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

            val uniqueBookId = if (book.fileName.length > 20) book.fileName else book.title

            Timber.d("Recreating BookPaginator for ID: $uniqueBookId. TextAlign: $userTextAlign")
            Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: Instantiating BookPaginator. book.chaptersForPagination.size=${book.chaptersForPagination.size}, initialChapter=$effectiveInitialChapter")

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
                themeBackgroundColor = effectiveBg,
                themeTextColor = effectiveText,
                bookId = uniqueBookId,
                bookCacheDao = bookCacheDao,
                proto = proto,
                initialChapterToPaginate = effectiveInitialChapter,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = userTextAlign,
                paragraphGapMultiplier = debouncedParagraphGapMult,
                imageSizeMultiplier = debouncedImageSizeMult,
                verticalMarginMultiplier = debouncedVerticalMarginMult
            )
        }

        LaunchedEffect(paginator) {
            onPaginatorReady(paginator)
            currentPaginatorRef.value = paginator
        }

        LaunchedEffect(paginator) {
            if (anchorLocatorForReconfig != null) {
                Timber.tag("POS_DIAG").d("Restoration Triggered. Anchor Locator: $anchorLocatorForReconfig")

                snapshotFlow { paginator.isLoading }.filter { !it }.first()

                val targetLocator = anchorLocatorForReconfig
                if (targetLocator != null) {
                    val page = paginator.findPageForLocator(targetLocator)

                    Timber.tag("POS_DIAG").d("Restoration Result: Paginator resolved locator to page: $page")

                    if (page != null) {
                        pagerState.scrollToPage(page)
                        Timber.tag("POS_DIAG").i("Restoration: Pager scrolled to $page")
                    } else {
                        val startPage = paginator.chapterStartPageIndices[targetLocator.chapterIndex]
                        if (startPage != null) {
                            Timber.tag("POS_DIAG").w("Restoration: Precise page not found, falling back to chapter start: $startPage")
                            pagerState.scrollToPage(startPage)
                        }
                    }
                    anchorLocatorForReconfig = null
                }
            }
        }

        var isLoading by remember { mutableStateOf(true) }
        var totalPageCount by remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        LaunchedEffect(paginator) {
            launch { snapshotFlow { paginator.isLoading }.collect {
                Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.isLoading=$it")
                isLoading = it
            } }
            launch {
                snapshotFlow { paginator.totalPageCount }.collect { newTotalPageCount ->
                    Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.totalPageCount=$newTotalPageCount")
                    totalPageCount = newTotalPageCount
                }
            }
            launch { snapshotFlow { paginator.generation }.collect {
                Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.generation=$it")
                generation = it
            } }
        }

        LaunchedEffect(pagerState, paginator) {
            snapshotFlow { pagerState.currentPage }.debounce(500)
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
            effectiveBg = effectiveBg,
            searchQuery = searchQuery,
            ttsHighlightInfo = ttsHighlightInfo,
            textStyle = textStyle,
            imageSizeMultiplier = debouncedImageSizeMult,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            onGetPage = { pageIndex ->
                val startTime = System.currentTimeMillis()
                val result = paginator.getPageContent(pageIndex)
                val duration = System.currentTimeMillis() - startTime
                if (duration > 16) {
                    Timber.tag("PageTurnDiag")
                        .w("HEAVY TASK: paginator.getPageContent($pageIndex) took ${duration}ms on Thread ${Thread.currentThread().name}")
                }
                result
            },
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
                coroutineScope.launch(Dispatchers.IO) {
                    isNavigatingByLink = true
                    var isFootnote = false
                    var footnoteHtml: String? = null

                    val sourceChapter =
                        book.chaptersForPagination.find { it.absPath == currentChapterPath }
                    if (sourceChapter != null) {
                        val sourceHtml = sourceChapter.htmlContent.ifEmpty {
                            try {
                                File(book.extractionBasePath, sourceChapter.htmlFilePath)
                                    .readText()
                            } catch (_: Exception) {
                                ""
                            }
                        }
                        if (sourceHtml.isNotEmpty()) {
                            val doc = Jsoup.parse(sourceHtml)
                            val safeHref = href.replace("\"", "\\\"")
                            val aTag = doc.select("a[href=\"$safeHref\"]").first()

                            if (aTag?.attr("epub:type") == "noteref" || href.startsWith("#")) {
                                isFootnote = true
                            }
                        } else if (href.startsWith("#")) {
                            isFootnote = true
                        }
                    } else if (href.startsWith("#")) {
                        isFootnote = true
                    }

                    if (isFootnote) {
                        val decodedHref = try {
                            URLDecoder.decode(href, "UTF-8")
                        } catch (_: Exception) {
                            href
                        }
                        val parts = decodedHref.split('#', limit = 2)
                        val pathPart = parts[0]
                        val anchor = if (parts.size > 1) parts[1] else null

                        if (anchor != null) {
                            val targetPath = if (pathPart.isBlank()) currentChapterPath else {
                                try {
                                    URI(currentChapterPath).resolve(pathPart)
                                        .normalize().path
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            if (targetPath != null) {
                                val targetChapter = book.chaptersForPagination.find {
                                    try {
                                        URI(it.absPath).normalize().path == targetPath
                                    } catch (_: Exception) {
                                        false
                                    }
                                }

                                if (targetChapter != null) {
                                    val targetHtml = targetChapter.htmlContent.ifEmpty {
                                        try {
                                            File(
                                                book.extractionBasePath,
                                                targetChapter.htmlFilePath
                                            ).readText()
                                        } catch (_: Exception) {
                                            ""
                                        }
                                    }
                                    if (targetHtml.isNotEmpty()) {
                                        val doc = Jsoup.parse(targetHtml)
                                        val noteEl = doc.getElementById(anchor)
                                        if (noteEl != null) {
                                            footnoteHtml = noteEl.html()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (!footnoteHtml.isNullOrBlank()) {
                            onFootnoteRequested(footnoteHtml)
                            isNavigatingByLink = false
                        } else {
                            paginator.navigateToHref(currentChapterPath, href) {
                                onNavComplete(it)
                                isNavigatingByLink = false
                            }
                        }
                    }
                }
            },
            onTap = onTap,
            isProUser = isProUser,
            isOss = isOss,
            onShowDictionaryUpsellDialog = onShowDictionaryUpsellDialog,
            onWordSelectedForAiDefinition = onWordSelectedForAiDefinition,
            onTranslate = onTranslate,
            onSearch = onSearch,
            onStartTtsFromSelection = onStartTtsFromSelection,
            onNoteRequested = onNoteRequested,
            userHighlights = userHighlights,
            onHighlightCreated = onHighlightCreated,
            onHighlightDeleted = onHighlightDeleted,
            isDarkTheme = isDarkTheme,
            activeHighlightPalette = activeHighlightPalette,
            onUpdatePalette = onUpdatePalette,
            effectiveText = effectiveText,
            pageTextureModifier = if (isPageTurnAnimationEnabled) Modifier else textureModifier,
            pageTextureBitmap = textureBitmap,
            pageTextureAlpha = activeTextureAlpha.coerceIn(0f, 1f)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isNavigatingByLink,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Navigating...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
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

private fun List<ContentBlock>.extractTextBlocks(): List<TextContentBlock> {
    val result = mutableListOf<TextContentBlock>()
    for (block in this) {
        when (block) {
            is WrappingContentBlock -> result.addAll(block.paragraphsToWrap)
            is FlexContainerBlock -> result.addAll(block.children.extractTextBlocks())
            is TableBlock -> {
                block.rows.forEach { row ->
                    row.forEach { cell ->
                        result.addAll(cell.content.extractTextBlocks())
                    }
                }
            }
            is TextContentBlock -> result.add(block)
            else -> {}
        }
    }
    return result
}

@Composable
private fun TextWithEmphasis(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    pageIndex: Int,
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
    val density = LocalDensity.current

    data class EmphasisMarkInfo(val center: Offset, val radius: Float, val color: Color)
    data class UnderlineDrawInfo(val path: Path?, val effect: PathEffect?, val minX: Float, val maxX: Float, val y: Float, val decoStyle: String, val decoColor: Color)

    // --- CACHING DECORATIONS FOR PERFORMANCE ---
    val cachedHighlights = remember(block, userHighlights, textLayoutResult, pressedHighlightCfi) {
        val startTime = System.currentTimeMillis()
        val paths = mutableListOf<Pair<Path, Color>>()
        val layout = textLayoutResult
        if (layout != null && block.cfi != null && userHighlights.isNotEmpty()) {
            userHighlights.forEach { highlight ->
                val range = getHighlightOffsetsInBlock(block, highlight)
                if (range != null) {
                    try {
                        val path = layout.getPathForRange(range.first, range.last + 1)
                        paths.add(path to highlight.color.color.copy(alpha = 0.4f))
                        if (highlight.cfi == pressedHighlightCfi) {
                            paths.add(path to Color.Black.copy(alpha = 0.1f))
                        }
                    } catch (e: Exception) {
                        Timber.tag("DecorationsDiag").e(e, "Highlight path out of bounds")
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated highlight paths for block ${block.blockIndex} in ${duration}ms")
        }
        paths
    }

    val cachedEmphasisMarks = remember(textLayoutResult, text, style.color, density) {
        val startTime = System.currentTimeMillis()
        val marks = mutableListOf<EmphasisMarkInfo>()
        val layout = textLayoutResult
        if (layout != null) {
            val emphasisAnnotations = text.getStringAnnotations("TextEmphasis", 0, text.length)
            if (emphasisAnnotations.isNotEmpty()) {
                with(density) { // Provides the scope for .toPx()
                    emphasisAnnotations.forEach { annotation ->
                        val emphasis = parseEmphasisAnnotation(annotation.item, style.color)
                        val markColor = if (emphasis.color.isSpecified) emphasis.color else style.color
                        val markSize = layout.layoutInput.style.fontSize.toPx() * 0.3f
                        for (offset in annotation.start until annotation.end) {
                            if (offset >= text.text.length || text.text[offset].isWhitespace()) continue
                            try {
                                val boundingBox = layout.getBoundingBox(offset)
                                val center = Offset(
                                    boundingBox.center.x,
                                    if (emphasis.position == "under") boundingBox.bottom + markSize * 0.1f
                                    else boundingBox.top - markSize * 0.1f
                                )
                                marks.add(EmphasisMarkInfo(center, markSize / 2, markColor))
                            } catch (e: Exception) {
                                Timber.tag("DecorationsDiag").e(e, "Emphasis mark out of bounds")
                            }
                        }
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated emphasis marks for block ${block.blockIndex} in ${duration}ms")
        }
        marks
    }

    val cachedUnderlines = remember(textLayoutResult, text, style.color, density) {
        val startTime = System.currentTimeMillis()
        val lines = mutableListOf<UnderlineDrawInfo>()
        val layout = textLayoutResult
        if (layout != null) {
            val customUnderlines = text.getStringAnnotations("CustomUnderline", 0, text.length)
            if (customUnderlines.isNotEmpty()) {
                val maxIdx = maxOf(0, text.length - 1)
                val groupedUnderlines = customUnderlines.groupBy { it.item }
                val mergedUnderlines = mutableListOf<AnnotatedString.Range<String>>()

                groupedUnderlines.forEach { (item, annotations) ->
                    val sorted = annotations.sortedBy { it.start }
                    var currentStart = -1
                    var currentEnd = -1

                    for (ann in sorted) {
                        if (currentStart == -1) {
                            currentStart = ann.start
                            currentEnd = ann.end
                        } else if (ann.start <= currentEnd) {
                            currentEnd = maxOf(currentEnd, ann.end)
                        } else {
                            mergedUnderlines.add(AnnotatedString.Range(item, currentStart, currentEnd))
                            currentStart = ann.start
                            currentEnd = ann.end
                        }
                    }
                    if (currentStart != -1) {
                        mergedUnderlines.add(AnnotatedString.Range(item, currentStart, currentEnd))
                    }
                }

                with(density) {
                    mergedUnderlines.forEach { annotation ->
                        val parts = annotation.item.split('|')
                        val decoStyle = parts.getOrNull(0) ?: "solid"
                        val colorStr = parts.getOrNull(1) ?: "Unspecified"
                        val decoColor = if (colorStr != "Unspecified") Color(colorStr.toULong()) else style.color

                        val safeStart = annotation.start.coerceIn(0, text.length)
                        val safeEnd = annotation.end.coerceIn(0, text.length)
                        if (safeStart < safeEnd) {
                            val startLine = layout.getLineForOffset(safeStart.coerceIn(0, maxIdx))
                            val endLine = layout.getLineForOffset((safeEnd - 1).coerceIn(0, maxIdx))

                            for (line in startLine..endLine) {
                                val lineStart = layout.getLineStart(line)
                                val lineEnd = layout.getLineEnd(line, visibleEnd = true)

                                val intersectionStart = maxOf(safeStart, lineStart)
                                val intersectionEnd = minOf(safeEnd, lineEnd)

                                var actualStart = intersectionStart
                                while (actualStart < intersectionEnd && text[actualStart].isWhitespace()) {
                                    actualStart++
                                }

                                var actualEnd = intersectionEnd
                                while (actualEnd > actualStart && text[actualEnd - 1].isWhitespace()) {
                                    actualEnd--
                                }

                                if (actualStart < actualEnd) {
                                    var minX = Float.POSITIVE_INFINITY
                                    var maxX = Float.NEGATIVE_INFINITY
                                    for (i in actualStart until actualEnd) {
                                        try {
                                            val box = layout.getBoundingBox(i)
                                            minX = minOf(minX, box.left, box.right)
                                            maxX = maxOf(maxX, box.left, box.right)
                                        } catch (e: Exception) {
                                            Timber.tag("DecorationsDiag").e(e, "Underline box out of bounds")
                                        }
                                    }

                                    if (minX < maxX && !minX.isInfinite() && !maxX.isInfinite()) {
                                        val baseline = layout.getLineBaseline(line)
                                        val defaultOffset = layout.layoutInput.style.fontSize.toPx() * 0.1f
                                        val requestedOffset = parts.getOrNull(2)?.toFloatOrNull()?.dp?.toPx()
                                        val y = baseline + (requestedOffset ?: defaultOffset)

                                        var underlinePath: Path? = null
                                        var effect: PathEffect? = null

                                        when (decoStyle) {
                                            "wavy" -> {
                                                underlinePath = Path()
                                                underlinePath.moveTo(minX, y)
                                                val waveLength = 4.dp.toPx()
                                                val amplitude = 1.dp.toPx()
                                                var currentX = minX
                                                var isUp = true

                                                while (currentX < maxX) {
                                                    val nextX = minOf(currentX + waveLength / 2f, maxX)
                                                    val midX = currentX + (nextX - currentX) / 2f
                                                    val cpY = if (isUp) y - amplitude else y + amplitude
                                                    underlinePath.quadraticTo(midX, cpY, nextX, y)
                                                    currentX = nextX
                                                    isUp = !isUp
                                                }
                                            }
                                            "dashed" -> {
                                                effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                                            }
                                            "dotted" -> {
                                                effect = PathEffect.dashPathEffect(floatArrayOf(1f, 4.dp.toPx()))
                                            }
                                        }

                                        lines.add(UnderlineDrawInfo(underlinePath, effect, minX, maxX, y, decoStyle, decoColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated custom underlines for block ${block.blockIndex} in ${duration}ms")
        }
        lines
    }

    val customDrawer = Modifier.drawBehind {
        val drawStartTime = System.currentTimeMillis()

        textLayoutResult?.let { layoutResult ->
            if (activeSelection != null) {
                val currentBlockAbs = getTextBlockCharOffset(block)
                val isSelectedOnPage = isBlockSelectedOnPage(block, pageIndex, activeSelection)
                val isStart =
                    pageIndex == activeSelection.startPageIndex &&
                        block.blockIndex == activeSelection.startBlockIndex &&
                        currentBlockAbs == activeSelection.startBlockCharOffset
                val isEnd =
                    pageIndex == activeSelection.endPageIndex &&
                        block.blockIndex == activeSelection.endBlockIndex &&
                        currentBlockAbs == activeSelection.endBlockCharOffset

                if (isSelectedOnPage) {
                    val sOffset = if (isStart) activeSelection.startOffset else 0
                    val eOffset = if (isEnd) activeSelection.endOffset else layoutResult.layoutInput.text.length

                    if (sOffset < eOffset) {
                        try {
                            val path = layoutResult.getPathForRange(sOffset, eOffset)
                            drawPath(path, Color(0xFF1976D2).copy(alpha = 0.3f))
                        } catch (e: Exception) {
                            Timber.tag("DecorationsDiag").e(e, "Highlight path out of bounds")
                        }
                    }
                }
            }

            cachedHighlights.forEach { (path, color) ->
                drawPath(path, color, blendMode = BlendMode.SrcOver)
            }

            cachedEmphasisMarks.forEach { mark ->
                drawCircle(mark.color, mark.radius, mark.center, style = Stroke(1f))
            }

            cachedUnderlines.forEach { line ->
                when (line.decoStyle) {
                    "wavy" -> {
                        line.path?.let { p ->
                            drawPath(p, color = line.decoColor, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    "dashed", "dotted" -> {
                        drawLine(
                            color = line.decoColor,
                            start = Offset(line.minX, line.y),
                            end = Offset(line.maxX, line.y),
                            strokeWidth = if (line.decoStyle == "dotted") 2.dp.toPx() else 1.dp.toPx(),
                            cap = if (line.decoStyle == "dotted") StrokeCap.Round else StrokeCap.Butt,
                            pathEffect = line.effect
                        )
                    }
                    else -> { // Solid or Double
                        drawLine(
                            color = line.decoColor,
                            start = Offset(line.minX, line.y),
                            end = Offset(line.maxX, line.y),
                            strokeWidth = 1.dp.toPx()
                        )
                        if (line.decoStyle == "double") {
                            drawLine(
                                color = line.decoColor,
                                start = Offset(line.minX, line.y + 2.dp.toPx()),
                                end = Offset(line.maxX, line.y + 2.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
        val drawDuration = System.currentTimeMillis() - drawStartTime
        if (drawDuration > 5) {
            Timber.tag("DecorationsDiag").w("Modifier.drawBehind took ${drawDuration}ms for block ${block.blockIndex}")
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
            detectTapGestures(
                onLongPress = { offset ->
                    textLayoutResult?.let { layout ->
                        val charOffset = layout.getOffsetForPosition(offset)
                        val wordBoundary = layout.getWordBoundary(charOffset)

                        var start = wordBoundary.start
                        var end = wordBoundary.end

                        val textStr = text.text
                        while (start < end && start < textStr.length && !textStr[start].isLetterOrDigit()) start++
                        while (end > start && end <= textStr.length && !textStr[end - 1].isLetterOrDigit()) end--

                        if (start < end && block.cfi != null) {
                            layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val maxIdx = maxOf(0, textStr.length - 1)
                                    val startBox = layout.getBoundingBox(start.coerceIn(0, maxIdx))
                                    val endBox = layout.getBoundingBox((end - 1).coerceIn(0, maxIdx))

                                    val topLeftWin = coords.localToWindow(startBox.topLeft)
                                    val bottomRightWin = coords.localToWindow(endBox.bottomRight)

                                    val selText = textStr.substring(start, end)

                                    val startBlockAbs = when (block) {
                                        is ParagraphBlock -> block.startCharOffsetInSource
                                        is HeaderBlock -> block.startCharOffsetInSource
                                        is QuoteBlock -> block.startCharOffsetInSource
                                        is ListItemBlock -> block.startCharOffsetInSource
                                    }

                                    onSelectionChange(
                                        PaginatedSelection(
                                            startBlockIndex = block.blockIndex,
                                            endBlockIndex = block.blockIndex,
                                            startBaseCfi = block.cfi!!,
                                            endBaseCfi = block.cfi!!,
                                            startOffset = start,
                                            endOffset = end,
                                            text = selText,
                                            rect = Rect(topLeftWin, bottomRightWin),
                                            startPageIndex = pageIndex,
                                            endPageIndex = pageIndex,
                                            startBlockCharOffset = startBlockAbs,
                                            endBlockCharOffset = startBlockAbs,
                                            textPerBlock = mapOf(
                                                buildSelectionBlockKey(
                                                    pageIndex = pageIndex,
                                                    blockIndex = block.blockIndex,
                                                    blockCharOffset = startBlockAbs
                                                ) to selText
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                onTap = { offset ->
                    textLayoutResult?.let { layout ->
                        val hit = getHighlightAt(offset, layout)
                        if (hit != null) {
                            val (highlight, localRect) = hit
                            val globalRect = layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val topLeft = coords.localToWindow(localRect.topLeft)
                                    val bottomRight = coords.localToWindow(localRect.bottomRight)
                                    Rect(topLeft, bottomRight)
                                } else null
                            } ?: localRect
                            onHighlightClick(highlight, globalRect)
                            return@detectTapGestures
                        }

                        val charOffset = layout.getOffsetForPosition(offset)
                        val urlAnnotation = text.getStringAnnotations("URL", charOffset, charOffset).firstOrNull()
                        if (urlAnnotation != null) onLinkClick(urlAnnotation.item)
                        else onGeneralTap(offset)
                    }
                }
            )
        }, onTextLayout = {
        textLayoutResult = it
        if (layoutCoordinates != null && block.cfi != null) {
            onRegisterLayout?.invoke(it, layoutCoordinates!!)
        }
    })
}

@SuppressLint("BinaryOperationInTimber")
private fun checkLayoutMismatch(
    blockIndex: Int,
    blockType: String,
    expectedHeight: Int,
    actualHeight: Int,
    textSnippet: String,
    diagnostics: String = "",
    @Suppress("SameParameterValue") tolerance: Int = 2
) {
    if (expectedHeight == 0) {
        Timber.tag("PAGINATION_MISMATCH").w(
            "Block #$blockIndex ($blockType) has expectedHeight=0. Skipping check. Text: '$textSnippet'" +
                    if (diagnostics.isNotBlank()) "\n -> Diagnostics: $diagnostics" else ""
        )
        return
    }

    if (actualHeight > expectedHeight + tolerance) {
        val diff = actualHeight - expectedHeight
        Timber.tag("PAGINATION_MISMATCH").e(
            "OVERFLOW DETECTED! Block #$blockIndex ($blockType)\n" +
                    " -> Expected: ${expectedHeight}px\n" +
                    " -> Actual:   ${actualHeight}px\n" +
                    " -> Diff:     +${diff}px\n" +
                    " -> Content:  '$textSnippet'" +
                    if (diagnostics.isNotBlank()) "\n -> Diagnostics: $diagnostics" else ""
        )
    }
}

@Suppress("unused")
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun PaginatedReaderContent(
    uiState: PaginatedReaderUiState,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    effectiveBg: Color,
    effectiveText: Color,
    searchQuery: String,
    ttsHighlightInfo: TtsHighlightInfo?,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
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
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onGetChapterInfo: (Int) -> Pair<String, Int?>?,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<HighlightColor>,
    onUpdatePalette: (Int, HighlightColor) -> Unit,
    isDarkTheme: Boolean,
    pageTextureModifier: Modifier = Modifier,
    pageTextureBitmap: ImageBitmap? = null,
    pageTextureAlpha: Float = 0f
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val textMeasurer = rememberTextMeasurer()
    var activeSelection by remember { mutableStateOf<PaginatedSelection?>(null) }

    if (showExternalLinkDialog != null) {
        val urlToShow = showExternalLinkDialog!!
        AlertDialog(
            onDismissRequest = { showExternalLinkDialog = null },
            title = { Text(stringResource(R.string.dialog_external_link_title)) },
            text = {
                Text(
                    stringResource(R.string.dialog_external_link_desc, urlToShow)
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_link), urlToShow)
                            clipboard.setPrimaryClip(clip)
                            showExternalLinkDialog = null
                        }) { Text(stringResource(R.string.action_copy)) }
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
                                    context, context.getString(R.string.error_no_browser), Toast.LENGTH_LONG
                                ).show()
                            }
                            showExternalLinkDialog = null
                        }) { Text(stringResource(R.string.action_open)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalLinkDialog = null }) { Text(stringResource(R.string.action_cancel)) }
            })
    }

    var pageTurnTouchY by remember { mutableStateOf<Float?>(null) }
    var lastKnownSelectionRect by remember { mutableStateOf<Pair<Rect, Int>?>(null) }

    LaunchedEffect(activeSelection) {
        if (activeSelection != null && activeSelection!!.rect != Rect.Zero) {
            lastKnownSelectionRect = activeSelection!!.rect to pagerState.currentPage
        }
    }

    val blockLayoutMap = remember {
        ReactiveBlockMap()
    }
    var showColorPickerDialog by remember { mutableStateOf<Int?>(null) }

    var showPaletteManager by remember { mutableStateOf(false) }
    var pagerWindowBounds by remember { mutableStateOf(Rect.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    var isDraggingHandle by remember { mutableStateOf(false) }
    var pendingCrossPageSelection by remember { mutableStateOf<PendingCrossPageSelection?>(null) }
    var crossPageTriggerInfo by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { newPage ->
            if (newPage == previousPage + 1) {
                if (crossPageTriggerInfo != null && crossPageTriggerInfo!!.first == previousPage) {
                    pendingCrossPageSelection = PendingCrossPageSelection(fromPageIndex = previousPage)
                    Timber.d("CrossPageSelection: Strict bottom trigger activated, queued for page $newPage")
                }
            } else if (newPage != previousPage) {
                pendingCrossPageSelection = null
            }
            crossPageTriggerInfo = null
            previousPage = newPage
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Timber.tag("ReflowPaginationDiag").d("PaginatedReaderContent: isLoading=false, totalPageCount=${uiState.totalPageCount}")
        if (uiState.totalPageCount > 0) {
            uiState.generation

            var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }

            val magnifierModifier = if (magnifierCenter.isSpecified) {
                Modifier.magnifier(
                    sourceCenter = { magnifierCenter },
                    zoom = 1.5f,
                    size = DpSize(140.dp, 48.dp),
                    cornerRadius = 24.dp,
                    elevation = 4.dp
                )
            } else Modifier

            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }.then(magnifierModifier)) {
                run {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().onGloballyPositioned { coords ->
                            pagerWindowBounds =
                                Rect(coords.positionInWindow(), coords.size.toSize())
                        }.pointerInput(Unit) {
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
                        val pageOffset =
                            (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        val zIndex = -pageOffset

                        val pageModifier = if (isPageTurnAnimationEnabled) {
                            Modifier.zIndex(zIndex).realisticBookPage(
                                pagerState,
                                pageIndex,
                                effectiveBg,
                                isDarkTheme,
                                pageTurnTouchY,
                                pageTextureBitmap,
                                pageTextureAlpha
                            )
                        } else Modifier

                        var pageContent by remember { mutableStateOf<Page?>(null) }
                        var currentChapterPath by remember { mutableStateOf<String?>(null) }

                        LaunchedEffect(pageIndex, uiState.generation) {
                            val fetchStartTime = System.currentTimeMillis()
                            Timber.tag("PageTurnDiag").d("Page $pageIndex: Starting content fetch")

                            pageContent = onGetPage(pageIndex)

                            val fetchDuration = System.currentTimeMillis() - fetchStartTime
                            Timber.tag("PageTurnDiag").d("Page $pageIndex: Content fetched in ${fetchDuration}ms")

                            onGetChapterPath(pageIndex)?.let { currentChapterPath = it }
                        }

                        SideEffect {
                            Timber.tag("PageTurnDiag").v("Page $pageIndex: Re-composing content area")
                        }

                        val textBlocksOnPage =
                            pageContent?.content?.extractTextBlocks()
                                ?.filter { it.cfi != null } ?: emptyList()
                        val lastTextBlock = textBlocksOnPage.lastOrNull()
                        val lastBlockAbs = lastTextBlock?.let {
                            when (it) {
                                is ParagraphBlock -> it.startCharOffsetInSource
                                is HeaderBlock -> it.startCharOffsetInSource
                                is QuoteBlock -> it.startCharOffsetInSource
                                is ListItemBlock -> it.startCharOffsetInSource
                            }
                        }

                        LaunchedEffect(activeSelection, lastTextBlock, isDraggingHandle) {
                            if (isDraggingHandle && activeSelection != null && lastTextBlock != null &&
                                activeSelection!!.endPageIndex == pageIndex &&
                                activeSelection!!.endBlockIndex == lastTextBlock.blockIndex &&
                                activeSelection!!.endBlockCharOffset == lastBlockAbs) {
                                if (activeSelection!!.endOffset >= lastTextBlock.content.text.length - 3) {
                                    if (crossPageTriggerInfo?.first != pageIndex) {
                                        Timber.tag("TextSelectionDiag")
                                            .d("Cross-page trigger ACTIVATED. Selection at bottom-right of page $pageIndex.")
                                        crossPageTriggerInfo = pageIndex to lastTextBlock.cfi!!
                                    }
                                    return@LaunchedEffect
                                }
                            }
                            if (!isDraggingHandle && activeSelection == null && crossPageTriggerInfo?.first == pageIndex) {
                                Timber.tag("TextSelectionDiag")
                                    .d("Cross-page trigger CLEARED on page $pageIndex (Custom).")
                                crossPageTriggerInfo = null
                            }
                        }

                        // Smart Cross-page selection logic
                        LaunchedEffect(pendingCrossPageSelection, pageContent) {
                            val pending = pendingCrossPageSelection ?: return@LaunchedEffect
                            if (pageIndex != pending.fromPageIndex + 1) return@LaunchedEffect
                            val content = pageContent ?: return@LaunchedEffect

                            val firstTextBlock =
                                content.content.extractTextBlocks()
                                    .firstOrNull { it.cfi != null } ?: run {
                                    pendingCrossPageSelection = null
                                    return@LaunchedEffect
                                }

                            var layoutInfo: Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? =
                                null
                            for (i in 0 until 20) {
                                layoutInfo = blockLayoutMap["${firstTextBlock.cfi}_$pageIndex"]
                                if (layoutInfo != null && layoutInfo.second.isAttached) break
                                delay(50)
                            }

                            if (layoutInfo == null || !layoutInfo.second.isAttached) {
                                pendingCrossPageSelection = null
                                return@LaunchedEffect
                            }

                            val text = firstTextBlock.content.text
                            if (text.isEmpty()) {
                                pendingCrossPageSelection = null
                                return@LaunchedEffect
                            }

                            // Smart boundary logic (>10 chars & ends at a word)
                            var endIndex = minOf(text.length, 10)
                            if (text.length > 10) {
                                for (i in 10 until text.length) {
                                    if (text[i].isWhitespace() || !text[i].isLetterOrDigit()) {
                                        endIndex = i
                                        break
                                    }
                                }
                            }

                            try {
                                val path = layoutInfo.first.getPathForRange(0, endIndex)
                                val localRect = path.getBounds()
                                val windowTopLeft =
                                    layoutInfo.second.localToWindow(localRect.topLeft)
                                val windowBottomRight =
                                    layoutInfo.second.localToWindow(localRect.bottomRight)

                                val previousSel = activeSelection

                                val firstTextBlockAbs = when (firstTextBlock) {
                                    is ParagraphBlock -> firstTextBlock.startCharOffsetInSource
                                    is HeaderBlock -> firstTextBlock.startCharOffsetInSource
                                    is QuoteBlock -> firstTextBlock.startCharOffsetInSource
                                    is ListItemBlock -> firstTextBlock.startCharOffsetInSource
                                }

                                val newTextPerBlock = (previousSel?.textPerBlock ?: emptyMap()).toMutableMap()
                                newTextPerBlock[
                                    buildSelectionBlockKey(
                                        pageIndex = pageIndex,
                                        blockIndex = firstTextBlock.blockIndex,
                                        blockCharOffset = firstTextBlockAbs
                                    )
                                ] = text.substring(0, endIndex)

                                val newText = newTextPerBlock.entries
                                    .sortedWith { first, second ->
                                        compareSelectionBlockKeys(first.key, second.key)
                                    }
                                    .joinToString(" ") { it.value }

                                activeSelection = PaginatedSelection(
                                    startBlockIndex = previousSel?.startBlockIndex ?: firstTextBlock.blockIndex,
                                    endBlockIndex = firstTextBlock.blockIndex,
                                    startBaseCfi = previousSel?.startBaseCfi ?: firstTextBlock.cfi!!,
                                    endBaseCfi = firstTextBlock.cfi!!,
                                    startOffset = previousSel?.startOffset ?: 0,
                                    endOffset = endIndex,
                                    text = newText,
                                    rect = Rect(windowTopLeft, windowBottomRight),
                                    startPageIndex = previousSel?.startPageIndex ?: pending.fromPageIndex,
                                    endPageIndex = pageIndex,
                                    startBlockCharOffset = previousSel?.startBlockCharOffset ?: firstTextBlockAbs,
                                    endBlockCharOffset = firstTextBlockAbs,
                                    textPerBlock = newTextPerBlock
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "CrossPageSelection: Failed to create selection")
                            }

                            pendingCrossPageSelection = null
                        }

                        Box(modifier = Modifier.fillMaxSize().background(effectiveBg).then(pageTextureModifier).then(pageModifier)) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            Timber.d("Tap detected on empty page area.")
                                            activeSelection = null
                                            onTap(offset)
                                        })
                                }.padding(
                                    horizontal = horizontalPadding,
                                    vertical = verticalPadding
                                ), contentAlignment = Alignment.TopStart) {
                                    if (pageContent != null) {
                                        val onGeneralTapCallback: (Offset) -> Unit = { offset ->
                                            activeSelection = null
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
                                                    bottom = block.style.margin.bottom.coerceAtLeast(
                                                        0.dp
                                                    )
                                                )

                                                val alignModifier =
                                                    if (block.style.horizontalAlign == "center") {
                                                        Modifier.align(Alignment.CenterHorizontally)
                                                    } else {
                                                        Modifier.padding(
                                                            start = block.style.margin.left.coerceAtLeast(
                                                                0.dp
                                                            ),
                                                            end = block.style.margin.right.coerceAtLeast(
                                                                0.dp
                                                            )
                                                        )
                                                    }

                                                val widthModifier =
                                                    if (block.style.width != Dp.Unspecified) {
                                                        Modifier.width(block.style.width)
                                                    } else {
                                                        Modifier.fillMaxWidth()
                                                    }

                                                val styleModifier =
                                                    alignModifier.then(if (block.style.horizontalAlign == "center") widthModifier else Modifier)
                                                        .drawCssBorders(
                                                            blockStyle = block.style,
                                                            density = density
                                                        )

                                                val diagnosticModifier =
                                                    Modifier.onGloballyPositioned { coordinates ->
                                                        val actualHeight =
                                                            coordinates.size.height
                                                        if (block.expectedHeight > 0) {
                                                            val snippet = when (block) {
                                                                is ParagraphBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is HeaderBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is QuoteBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is ListItemBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is TextContentBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                else -> "Non-text content"
                                                            }

                                                            checkLayoutMismatch(
                                                                blockIndex = block.blockIndex,
                                                                blockType = block::class.simpleName
                                                                    ?: "Block",
                                                                expectedHeight = block.expectedHeight,
                                                                actualHeight = actualHeight,
                                                                textSnippet = snippet,
                                                                diagnostics = buildString {
                                                                    append("page=")
                                                                    append(pageIndex)
                                                                    append(", width=")
                                                                    append(coordinates.size.width)
                                                                    append("px, styleWidth=")
                                                                    append(block.style.width)
                                                                    append(", maxWidth=")
                                                                    append(block.style.maxWidth)
                                                                    append(", margin=")
                                                                    append(block.style.margin)
                                                                    append(", padding=")
                                                                    append(block.style.padding)
                                                                    append(", borders=(")
                                                                    append(block.style.borderLeft?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderTop?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderRight?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderBottom?.width ?: 0.dp)
                                                                    append(")")
                                                                    when (block) {
                                                                        is ParagraphBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is HeaderBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is QuoteBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is ListItemBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                        }

                                                                        is TextContentBlock -> {
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                        }

                                                                        else -> Unit
                                                                    }
                                                                },
                                                                tolerance = 2
                                                            )
                                                        }
                                                    }.then(marginModifier).then(styleModifier)

                                                Box(modifier = diagnosticModifier) {
                                                    val paddingModifier = Modifier.padding(
                                                        start = block.style.padding.left.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderLeft?.width ?: 0.dp),
                                                        top = block.style.padding.top.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderTop?.width ?: 0.dp),
                                                        end = block.style.padding.right.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderRight?.width
                                                            ?: 0.dp),
                                                        bottom = block.style.padding.bottom.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderBottom?.width
                                                            ?: 0.dp)
                                                    ).then(
                                                        if (block.style.horizontalAlign != "center") widthModifier else Modifier.fillMaxWidth()
                                                    )

                                                    @Suppress("DEPRECATION") when (block) {
                                                        is ParagraphBlock -> {
                                                            val paragraphStyle = textStyle.copy(
                                                                textAlign = block.textAlign
                                                                    ?: textStyle.textAlign
                                                            )
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
                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
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

                                                            @Suppress(
                                                                "UnusedVariable",
                                                                "Unused"
                                                            ) val diagnosticModifier =
                                                                if (block.textAlign == TextAlign.Justify) {
                                                                    Modifier.onGloballyPositioned { coordinates ->
                                                                        val width =
                                                                            coordinates.size.width
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
                                                                style = paragraphStyle,
                                                                modifier = paddingModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = userHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(highlight.cfi)
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is HeaderBlock -> {
                                                            val style = createHeaderTextStyle(
                                                                baseStyle = textStyle,
                                                                level = block.level,
                                                                textAlign = block.textAlign
                                                            )
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
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
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
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = userHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(
                                                                        highlight.cfi
                                                                    )
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is QuoteBlock -> {
                                                            val quoteStyle = textStyle.copy(
                                                                textAlign = block.textAlign
                                                                    ?: textStyle.textAlign
                                                            )
                                                            val quoteModifier =
                                                                paddingModifier.padding(start = 16.dp)
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
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
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
                                                                style = quoteStyle,
                                                                modifier = quoteModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = userHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(highlight.cfi)
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is ListItemBlock -> {
                                                            Row(
                                                                modifier = paddingModifier,
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                val markerAreaModifier =
                                                                    Modifier.width(32.dp)
                                                                        .padding(end = 8.dp)
                                                                val itemMarkerImage = block.itemMarkerImage
                                                                val itemMarker = block.itemMarker

                                                                if (itemMarkerImage != null) {
                                                                    val imageRequest =
                                                                        Builder(LocalContext.current).data(
                                                                            File(
                                                                                itemMarkerImage
                                                                            )
                                                                        ).crossfade(true).build()
                                                                    val imageSize = with(density) {
                                                                        (textStyle.fontSize.value * 0.8f).sp.toDp()
                                                                    }

                                                                    AsyncImage(
                                                                        model = imageRequest,
                                                                        contentDescription = stringResource(R.string.content_desc_list_item_marker),
                                                                        modifier = markerAreaModifier.height(
                                                                            imageSize
                                                                        ),
                                                                        alignment = Alignment.CenterEnd,
                                                                        contentScale = ContentScale.FillHeight
                                                                    )
                                                                } else if (itemMarker != null) {
                                                                    Text(
                                                                        text = itemMarker,
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
                                                                            val intersectionEndAbs =
                                                                                minOf(
                                                                                    blockEndAbs,
                                                                                    highlightEndAbs
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
                                                                    pageIndex = pageIndex,
                                                                    textMeasurer = textMeasurer,
                                                                    onLinkClick = onLinkClickCallback,
                                                                    onGeneralTap = onGeneralTapCallback,
                                                                    block = block,
                                                                    userHighlights = userHighlights,
                                                                    activeSelection = activeSelection,
                                                                    onSelectionChange = { sel ->
                                                                        activeSelection = sel
                                                                    },
                                                                    onHighlightClick = { highlight, _ ->
                                                                        onNoteRequested(highlight.cfi)
                                                                        activeSelection = null
                                                                    },
                                                                    isDarkTheme = isDarkTheme,
                                                                    onRegisterLayout = { layout, coords ->
                                                                        if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                            Triple(
                                                                                layout,
                                                                                coords,
                                                                                block
                                                                            )
                                                                    })
                                                            }
                                                        }

                                                        is WrappingContentBlock -> {
                                                            WrappingContentLayout(
                                                                block = block,
                                                                textStyle = textStyle,
                                                                imageSizeMultiplier = imageSizeMultiplier,
                                                                modifier = paddingModifier,
                                                                searchQuery = searchQuery,
                                                                ttsHighlightInfo = ttsHighlightInfo,
                                                                searchHighlightColor = searchHighlightColor,
                                                                ttsHighlightColor = ttsHighlightColor
                                                            )
                                                        }

                                                        is FlexContainerBlock -> {

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
                                                                    modifier = paddingModifier.fillMaxWidth(),
                                                                    horizontalArrangement = horizontalArrangement,
                                                                    verticalAlignment = verticalAlignment
                                                                ) {
                                                                    block.children.forEach { childBlock ->
                                                                        RenderFlexChildBlock(
                                                                            childBlock = childBlock,
                                                                            textStyle = textStyle,
                                                                            imageSizeMultiplier = imageSizeMultiplier,
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
                                                                                activeSelection =
                                                                                    sel
                                                                            },
                                                                            onHighlightClick = { highlight, _ ->
                                                                                onNoteRequested(
                                                                                    highlight.cfi
                                                                                )
                                                                                activeSelection =
                                                                                    null
                                                                            },
                                                                            isDarkTheme = isDarkTheme,
                                                                            blockLayoutMap = blockLayoutMap,
                                                                            density = density,
                                                                            imageLoader = imageLoader,
                                                                            pageIndex = pageIndex
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
                                                                    modifier = paddingModifier.fillMaxWidth(),
                                                                    verticalArrangement = verticalArrangement,
                                                                    horizontalAlignment = horizontalAlignment
                                                                ) {
                                                                    block.children.forEach { childBlock ->
                                                                        RenderFlexChildBlock(
                                                                            childBlock = childBlock,
                                                                            textStyle = textStyle,
                                                                            imageSizeMultiplier = imageSizeMultiplier,
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
                                                                                activeSelection =
                                                                                    sel
                                                                            },
                                                                            onHighlightClick = { highlight, _ ->
                                                                                onNoteRequested(
                                                                                    highlight.cfi
                                                                                )
                                                                                activeSelection =
                                                                                    null
                                                                            },
                                                                            isDarkTheme = isDarkTheme,
                                                                            blockLayoutMap = blockLayoutMap,
                                                                            density = density,
                                                                            imageLoader = imageLoader,
                                                                            pageIndex = pageIndex
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        is MathBlock -> {
                                                            val svgContent = block.svgContent?.takeIf { it.isNotBlank() }
                                                            Timber.d(
                                                                "PaginatedReader: Rendering MathBlock. Alt: '${block.altText}', Has SVG: ${svgContent != null}"
                                                            )
                                                            if (svgContent != null) {
                                                                val nonBlankSvgContent = svgContent
                                                                BoxWithConstraints(
                                                                    modifier = paddingModifier
                                                                ) {
                                                                    val localDensity =
                                                                        LocalDensity.current
                                                                    val fontSizePx =
                                                                        with(localDensity) {
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
                                                                    val heightPx =
                                                                        parseSvgDimension(
                                                                            block.svgHeight,
                                                                            fontSizePx,
                                                                            containerWidthPx,
                                                                            localDensity
                                                                        )

                                                                    var imageModifier: Modifier =
                                                                        Modifier
                                                                    if (widthPx != null) {
                                                                        val finalWidthDp =
                                                                            with(localDensity) { widthPx.toDp() }
                                                                        Timber.d("Applying calculated width to MathBlock image: $finalWidthDp")
                                                                        imageModifier =
                                                                            imageModifier.width(
                                                                                finalWidthDp
                                                                            )
                                                                    } else {
                                                                        Timber.w("Could not calculate a specific width for MathBlock. It will fill available space.")
                                                                        imageModifier =
                                                                            imageModifier.fillMaxWidth()
                                                                    }

                                                                    if (heightPx != null) {
                                                                        val finalHeightDp =
                                                                            with(localDensity) { heightPx.toDp() }
                                                                        Timber.d("Applying calculated height to MathBlock image: $finalHeightDp")
                                                                        imageModifier =
                                                                            imageModifier.height(
                                                                                finalHeightDp
                                                                            )
                                                                    } else {
                                                                        val viewBoxParts =
                                                                            block.svgViewBox?.split(
                                                                                ' ',
                                                                                ','
                                                                            )
                                                                                ?.mapNotNull { it.toFloatOrNull() }
                                                                        if (viewBoxParts != null && viewBoxParts.size == 4 && viewBoxParts[2] > 0) {
                                                                            val aspectRatio =
                                                                                viewBoxParts[3] / viewBoxParts[2]
                                                                            val effectiveWidth =
                                                                                widthPx
                                                                                    ?: containerWidthPx.toFloat()
                                                                            val finalHeightDp =
                                                                                with(localDensity) { (effectiveWidth * aspectRatio).toDp() }
                                                                            imageModifier =
                                                                                imageModifier.height(
                                                                                    finalHeightDp
                                                                                )
                                                                        } else {
                                                                            val fallbackHeightDp =
                                                                                with(localDensity) { (textStyle.fontSize.value * 3).sp.toDp() }
                                                                            imageModifier =
                                                                                imageModifier.height(
                                                                                    fallbackHeightDp
                                                                                )
                                                                        }
                                                                    }

                                                                    val imageRequest =
                                                                        Builder(LocalContext.current).data(
                                                                            SvgData(
                                                                                nonBlankSvgContent
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

                                                            BoxWithConstraints(
                                                                modifier = paddingModifier,
                                                                contentAlignment = imageBlockContentAlignment(style)
                                                            ) {
                                                                val scaledSize = computeImageRenderSizeDp(
                                                                    block = block,
                                                                    density = density,
                                                                    maxWidthDp = maxWidth,
                                                                    imageSizeMultiplier = imageSizeMultiplier
                                                                )
                                                                val finalImageModifier = Modifier
                                                                    .then(
                                                                        if (scaledSize != null) {
                                                                            Modifier.width(scaledSize.first).height(scaledSize.second)
                                                                        } else if (style.width.isSpecified && style.width > 0.dp) {
                                                                            Modifier.width(style.width)
                                                                        } else {
                                                                            Modifier.fillMaxWidth()
                                                                        }
                                                                    )
                                                                    .then(
                                                                        if (scaledSize == null && style.maxWidth.isSpecified && style.maxWidth > 0.dp) {
                                                                            Modifier.widthIn(max = style.maxWidth)
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .then(
                                                                        if (scaledSize == null) {
                                                                            if (block.expectedHeight > 0) {
                                                                                Modifier.height(with(density) { (block.expectedHeight * imageSizeMultiplier).toDp() })
                                                                            } else {
                                                                                Modifier.height(250.dp)
                                                                            }
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .onGloballyPositioned { coords ->
                                                                        Timber.tag("IMAGE_DIAG").v("Actual Rendered Height for [#${block.blockIndex}]: ${coords.size.height}px")
                                                                    }

                                                                AsyncImage(
                                                                    model = imageRequest,
                                                                    contentDescription = block.altText
                                                                        ?: "Image from EPUB",
                                                                    modifier = finalImageModifier,
                                                                    contentScale = ContentScale.Fit,
                                                                    colorFilter = colorFilter
                                                                )
                                                            }
                                                        }

                                                        is SpacerBlock -> {
                                                            Box(
                                                                modifier = Modifier.fillMaxWidth()
                                                                    .height(block.height)
                                                                    .drawCssBorders(
                                                                        block.style,
                                                                        density
                                                                    )
                                                            )
                                                        }

                                                        is TableBlock -> {
                                                            Column(modifier = paddingModifier) {
                                                                block.rows.forEach { tableRow ->
                                                                    Row(
                                                                        Modifier.fillMaxWidth()
                                                                            .height(
                                                                                IntrinsicSize.Min
                                                                            )
                                                                    ) {
                                                                        val hasFixedWidths =
                                                                            tableRow.any {
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
                                                                                cellContainerModifier.fillMaxHeight()
                                                                                    .then(
                                                                                        if (cellStyle.backgroundColor.isSpecified) {
                                                                                            Modifier.background(
                                                                                                cellStyle.backgroundColor
                                                                                            )
                                                                                        } else {
                                                                                            Modifier
                                                                                        }
                                                                                    )
                                                                                    .drawCssBorders(
                                                                                        cellStyle,
                                                                                        density
                                                                                    ).padding(
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
                                                                                                val itemMarker = blockInCell.itemMarker
                                                                                                if (itemMarker != null) {
                                                                                                    Text(
                                                                                                        text = itemMarker,
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
                                                                                            Spacer(
                                                                                                modifier = Modifier.fillMaxWidth()
                                                                                                    .height(
                                                                                                        blockInCell.height
                                                                                                    )
                                                                                                    .drawCssBorders(
                                                                                                        blockInCell.style,
                                                                                                        density
                                                                                                    )
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
                                                                                                )
                                                                                                    .build(),
                                                                                                contentDescription = blockInCell.altText,
                                                                                                contentScale = ContentScale.Fit,
                                                                                                modifier = tableCellImageModifier(
                                                                                                    block = blockInCell,
                                                                                                    density = density,
                                                                                                    imageSizeMultiplier = imageSizeMultiplier
                                                                                                )
                                                                                            )
                                                                                        }

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

                if (activeSelection != null) {
                    val sel = activeSelection!!
                    val currentPageSuffix = "_${pagerState.currentPage}"

                    val currentPageBlocks =
                        blockLayoutMap.filterKeys { it.endsWith(currentPageSuffix) }.values.filter { it.second.isAttached }
                    val visibleSelectedBlocks =
                        currentPageBlocks.filter { isBlockSelectedOnPage(it.third, pagerState.currentPage, sel) }

                    if (!isDraggingHandle && visibleSelectedBlocks.isNotEmpty()) {
                        val menuAnchorRect = run {
                            var minLeft = Float.MAX_VALUE
                            var minTop = Float.MAX_VALUE
                            var maxRight = Float.MIN_VALUE
                            var maxBottom = Float.MIN_VALUE

                            visibleSelectedBlocks.forEach { triple ->
                                val (textLayout, coords, block) = triple

                                val currentBlockAbs = getTextBlockCharOffset(block)
                                val isStartBlockPart =
                                    pagerState.currentPage == sel.startPageIndex &&
                                        block.blockIndex == sel.startBlockIndex &&
                                        currentBlockAbs == sel.startBlockCharOffset
                                val isEndBlockPart =
                                    pagerState.currentPage == sel.endPageIndex &&
                                        block.blockIndex == sel.endBlockIndex &&
                                        currentBlockAbs == sel.endBlockCharOffset

                                val blockStartOffset = if (isStartBlockPart) sel.startOffset else 0
                                val blockEndOffset = if (isEndBlockPart) sel.endOffset else textLayout.layoutInput.text.length

                                val textLen = textLayout.layoutInput.text.length
                                val maxIdx = maxOf(0, textLen - 1)

                                val safeStart = blockStartOffset.coerceIn(0, textLen)
                                val safeEnd = blockEndOffset.coerceIn(safeStart, textLen)

                                if (safeStart < safeEnd) {
                                    try {
                                        val startBox = textLayout.getBoundingBox(safeStart.coerceIn(0, maxIdx))
                                        val endBox = textLayout.getBoundingBox((safeEnd - 1).coerceIn(0, maxIdx))

                                        val topWin =
                                            coords.localToWindow(Offset(0f, startBox.top)).y
                                        val bottomWin =
                                            coords.localToWindow(Offset(0f, endBox.bottom)).y
                                        val leftWin1 =
                                            coords.localToWindow(Offset(startBox.left, 0f)).x
                                        val rightWin1 =
                                            coords.localToWindow(Offset(startBox.right, 0f)).x
                                        val leftWin2 =
                                            coords.localToWindow(Offset(endBox.left, 0f)).x
                                        val rightWin2 =
                                            coords.localToWindow(Offset(endBox.right, 0f)).x

                                        minTop = minOf(minTop, topWin)
                                        maxBottom = maxOf(maxBottom, bottomWin)
                                        minLeft = minOf(minLeft, leftWin1, leftWin2)
                                        maxRight = maxOf(maxRight, rightWin1, rightWin2)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error calculating exact selection bounds")
                                    }
                                }
                            }

                            val handleSizePx = with(density) { 36.dp.toPx() }
                            if (minTop != Float.MAX_VALUE && maxBottom != Float.MIN_VALUE) {
                                Rect(minLeft, minTop, maxRight, maxBottom + handleSizePx)
                            } else {
                                Rect(
                                    sel.rect.left,
                                    sel.rect.top,
                                    sel.rect.right,
                                    sel.rect.bottom + handleSizePx
                                )
                            }
                        }

                        Popup(
                            popupPositionProvider = remember(
                                menuAnchorRect,
                                density
                            ) { SmartPopupPositionProvider(menuAnchorRect, density) },
                            onDismissRequest = { activeSelection = null },
                            properties = PopupProperties(
                                dismissOnClickOutside = false
                            )
                        ) {
                            PaginatedTextSelectionMenu(
                                onCopy = {
                                    val clipboardManager =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(context.getString(R.string.clip_label_copied_text), sel.text)
                                    clipboardManager.setPrimaryClip(clip)
                                    activeSelection = null
                                },
                                onSelectAll = null,
                                onDictionary = {
                                    if (isProUser || countWords(sel.text) <= 1) {
                                        onWordSelectedForAiDefinition(sel.text)
                                    } else {
                                        onShowDictionaryUpsellDialog()
                                    }
                                    activeSelection = null
                                },
                                onTranslate = {
                                    onTranslate(sel.text)
                                    activeSelection = null
                                },
                                onSearch = {
                                    onSearch(sel.text)
                                    activeSelection = null
                                },
                                onHighlight = { color ->
                                    val finalCfi =
                                        "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                    onHighlightCreated(finalCfi, sel.text, color.id)
                                    activeSelection = null
                                },
                                onNote = {
                                    onNoteRequested(null)
                                    val finalCfi =
                                        "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                    onHighlightCreated(finalCfi, sel.text, HighlightColor.YELLOW.id)
                                    activeSelection = null
                                },
                                onTts = {
                                    val startAbs = sel.startOffset + sel.startBlockCharOffset
                                    onStartTtsFromSelection(sel.startBaseCfi, startAbs)
                                    activeSelection = null
                                },
                                onDelete = null,
                                isProUser = isProUser,
                                isOss = isOss,
                                activeHighlightPalette = activeHighlightPalette,
                                onOpenPaletteManager = { showPaletteManager = true })
                        }
                    }

                    val updateSelection: (Offset, SelectionHandle) -> SelectionHandle =
                        { windowPos, currentDragHandle ->
                            var activeDragHandle = currentDragHandle

                            val attachedBlocks =
                                blockLayoutMap.filterKeys { it.endsWith(currentPageSuffix) }.values.filter { it.second.isAttached }
                                    .sortedBy { it.second.positionInWindow().y }

                            if (attachedBlocks.isNotEmpty()) {
                                val targetTriple = attachedBlocks.minByOrNull {
                                    val coords = it.second
                                    val rect = Rect(coords.positionInWindow(), coords.size.toSize())
                                    val dx =
                                        maxOf(rect.left - windowPos.x, 0f, windowPos.x - rect.right)
                                    val dy =
                                        maxOf(rect.top - windowPos.y, 0f, windowPos.y - rect.bottom)
                                    dx * dx + dy * dy
                                } ?: attachedBlocks.last()

                                val (textLayout, coords, block) = targetTriple
                                val localPos = coords.windowToLocal(windowPos)
                                val offset = textLayout.getOffsetForPosition(localPos)
                                    .coerceIn(0, textLayout.layoutInput.text.length)

                                val isStartHandle = activeDragHandle == SelectionHandle.START
                                var newStartIdx =
                                    if (isStartHandle) block.blockIndex else sel.startBlockIndex
                                var newEndIdx =
                                    if (isStartHandle) sel.endBlockIndex else block.blockIndex
                                var newStartOffset = if (isStartHandle) offset else sel.startOffset
                                var newEndOffset = if (isStartHandle) sel.endOffset else offset
                                var newStartCfi = if (isStartHandle) block.cfi!! else sel.startBaseCfi
                                var newEndCfi = if (isStartHandle) sel.endBaseCfi else block.cfi!!
                                var newStartPageIdx = if (isStartHandle) pagerState.currentPage else sel.startPageIndex
                                var newEndPageIdx = if (isStartHandle) sel.endPageIndex else pagerState.currentPage

                                val currentBlockAbs = getTextBlockCharOffset(block)
                                var newStartBlockAbs = if (isStartHandle) currentBlockAbs else sel.startBlockCharOffset
                                var newEndBlockAbs = if (!isStartHandle) currentBlockAbs else sel.endBlockCharOffset

                                val isReversed = when {
                                    newStartPageIdx != newEndPageIdx -> newStartPageIdx > newEndPageIdx
                                    else -> {
                                        val blockCompare = compareBlockPositionsOnPage(
                                            newStartIdx,
                                            newStartBlockAbs,
                                            newEndIdx,
                                            newEndBlockAbs
                                        )
                                        if (blockCompare != 0) blockCompare > 0 else newStartOffset > newEndOffset
                                    }
                                }

                                if (isReversed) {
                                    newStartPageIdx = newEndPageIdx.also { newEndPageIdx = newStartPageIdx }
                                    newStartIdx = newEndIdx.also { newEndIdx = newStartIdx }
                                    newStartOffset = newEndOffset.also { newEndOffset = newStartOffset }
                                    newStartCfi = newEndCfi.also { newEndCfi = newStartCfi }
                                    newStartBlockAbs = newEndBlockAbs.also { newEndBlockAbs = newStartBlockAbs }
                                    activeDragHandle = if (activeDragHandle == SelectionHandle.START) SelectionHandle.END else SelectionHandle.START
                                }

                                if (
                                    newStartPageIdx != sel.startPageIndex ||
                                    newEndPageIdx != sel.endPageIndex ||
                                    newStartIdx != sel.startBlockIndex ||
                                    newEndIdx != sel.endBlockIndex ||
                                    newStartOffset != sel.startOffset ||
                                    newEndOffset != sel.endOffset
                                ) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    val relevantBlocks = attachedBlocks
                                        .filter {
                                            isBlockSelectedOnPage(
                                                block = it.third,
                                                pageIndex = pagerState.currentPage,
                                                selection = PaginatedSelection(
                                                    startBlockIndex = newStartIdx,
                                                    endBlockIndex = newEndIdx,
                                                    startBaseCfi = newStartCfi,
                                                    endBaseCfi = newEndCfi,
                                                    startOffset = newStartOffset,
                                                    endOffset = newEndOffset,
                                                    text = sel.text,
                                                    rect = sel.rect,
                                                    startPageIndex = newStartPageIdx,
                                                    endPageIndex = newEndPageIdx,
                                                    startBlockCharOffset = newStartBlockAbs,
                                                    endBlockCharOffset = newEndBlockAbs,
                                                    textPerBlock = sel.textPerBlock
                                                )
                                            )
                                        }
                                        .sortedWith(compareBy({ it.third.blockIndex }, { getTextBlockCharOffset(it.third) }))

                                    val newTextPerBlock = sel.textPerBlock.toMutableMap()
                                    newTextPerBlock.keys.removeAll { keyStr ->
                                        parseSelectionBlockKey(keyStr)?.pageIndex == pagerState.currentPage
                                    }

                                    for (b in relevantBlocks) {
                                        val txt = b.third.content.text
                                        val bAbs = getTextBlockCharOffset(b.third)
                                        val isStartBlockPart = b.third.blockIndex == newStartIdx && bAbs == newStartBlockAbs
                                        val isEndBlockPart = b.third.blockIndex == newEndIdx && bAbs == newEndBlockAbs

                                        val s = if (isStartBlockPart) newStartOffset else 0
                                        val e = if (isEndBlockPart) newEndOffset else txt.length

                                        val safeS = s.coerceIn(0, txt.length)
                                        val safeE = e.coerceIn(safeS, txt.length)

                                        if (safeS < safeE) {
                                            newTextPerBlock[
                                                buildSelectionBlockKey(
                                                    pageIndex = pagerState.currentPage,
                                                    blockIndex = b.third.blockIndex,
                                                    blockCharOffset = bAbs
                                                )
                                            ] = txt.substring(safeS, safeE)
                                        } else {
                                            newTextPerBlock.remove(
                                                buildSelectionBlockKey(
                                                    pageIndex = pagerState.currentPage,
                                                    blockIndex = b.third.blockIndex,
                                                    blockCharOffset = bAbs
                                                )
                                            )
                                        }
                                    }

                                    val newText = newTextPerBlock.entries
                                        .sortedWith { first, second ->
                                            compareSelectionBlockKeys(first.key, second.key)
                                        }
                                        .joinToString(" ") { it.value }

                                    val sLayout = blockLayoutMap["${newStartCfi}_$newStartPageIdx"]?.takeIf {
                                        val abs = getTextBlockCharOffset(it.third)
                                        abs == newStartBlockAbs
                                    }

                                    val eLayout = blockLayoutMap["${newEndCfi}_$newEndPageIdx"]
                                    var newRect = sel.rect

                                    if (sLayout != null && eLayout != null && sLayout.second.isAttached && eLayout.second.isAttached) {
                                        val sMaxIdx = maxOf(0, sLayout.first.layoutInput.text.length - 1)
                                        val eMaxIdx = maxOf(0, eLayout.first.layoutInput.text.length - 1)

                                        val sRectLocal = sLayout.first.getBoundingBox(
                                            newStartOffset.coerceIn(0, sMaxIdx)
                                        )
                                        val sRectWin = Rect(
                                            sLayout.second.localToWindow(sRectLocal.topLeft),
                                            sLayout.second.localToWindow(sRectLocal.bottomRight)
                                        )
                                        val eRectLocal = eLayout.first.getBoundingBox(
                                            (newEndOffset - 1).coerceIn(0, eMaxIdx)
                                        )
                                        val eRectWin = Rect(
                                            eLayout.second.localToWindow(eRectLocal.topLeft),
                                            eLayout.second.localToWindow(eRectLocal.bottomRight)
                                        )
                                        newRect = Rect(
                                            minOf(sRectWin.left, eRectWin.left),
                                            sRectWin.top,
                                            maxOf(sRectWin.right, eRectWin.right),
                                            eRectWin.bottom
                                        )
                                    } else {
                                        var minLeft = Float.MAX_VALUE
                                        var minTop = Float.MAX_VALUE
                                        var maxRight = Float.MIN_VALUE
                                        var maxBottom = Float.MIN_VALUE
                                        relevantBlocks.forEach { b ->
                                            if (b.second.isAttached) {
                                                val r = Rect(
                                                    b.second.positionInWindow(),
                                                    b.second.size.toSize()
                                                )
                                                minLeft = minOf(minLeft, r.left)
                                                minTop = minOf(minTop, r.top)
                                                maxRight = maxOf(maxRight, r.right)
                                                maxBottom = maxOf(maxBottom, r.bottom)
                                            }
                                        }
                                        if (minLeft != Float.MAX_VALUE) {
                                            newRect = Rect(minLeft, minTop, maxRight, maxBottom)
                                        }
                                    }

                                    activeSelection = PaginatedSelection(
                                        startBlockIndex = newStartIdx,
                                        endBlockIndex = newEndIdx,
                                        startBaseCfi = newStartCfi,
                                        endBaseCfi = newEndCfi,
                                        startOffset = newStartOffset,
                                        endOffset = newEndOffset,
                                        text = newText,
                                        rect = newRect,
                                        startPageIndex = newStartPageIdx,
                                        endPageIndex = newEndPageIdx,
                                        startBlockCharOffset = newStartBlockAbs,
                                        endBlockCharOffset = newEndBlockAbs,
                                        textPerBlock = newTextPerBlock
                                    )
                                }
                            }
                            activeDragHandle
                        }

                    val latestUpdateSelection by rememberUpdatedState(updateSelection)

                    listOf(SelectionHandle.START, SelectionHandle.END).forEach { handleType ->
                        val isStart = handleType == SelectionHandle.START
                        var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    @Suppress("UNUSED_VARIABLE") val animOffset = pagerState.currentPageOffsetFraction
                                    @Suppress("UNUSED_VARIABLE") val currPage = pagerState.currentPage
                                    @Suppress("UNUSED_VARIABLE") val isScrolling = pagerState.isScrollInProgress
                                    @Suppress("UNUSED_VARIABLE") val tick = blockLayoutMap.tick

                                    val handlePageIndex = if (isStart) sel.startPageIndex else sel.endPageIndex
                                    val pos = if (handlePageIndex == pagerState.currentPage) {
                                        val selCfi = if (isStart) sel.startBaseCfi else sel.endBaseCfi
                                        val selOffset = if (isStart) sel.startOffset else sel.endOffset
                                        val targetBlockAbs = if (isStart) sel.startBlockCharOffset else sel.endBlockCharOffset
                                        val layoutInfo = blockLayoutMap["${selCfi}_$handlePageIndex"]?.takeIf {
                                            val blockAbs = getTextBlockCharOffset(it.third)
                                            blockAbs == targetBlockAbs
                                        }

                                        if (layoutInfo != null && layoutInfo.second.isAttached && rootCoords != null && rootCoords!!.isAttached) {
                                            val textLayout = layoutInfo.first
                                            val coords = layoutInfo.second
                                            val maxIdx = maxOf(0, textLayout.layoutInput.text.length - 1)
                                            val safeOffset = selOffset.coerceIn(0, textLayout.layoutInput.text.length)
                                            val safeOffsetForLine = safeOffset.coerceIn(0, maxIdx)

                                            val line = textLayout.getLineForOffset(safeOffsetForLine)
                                            val x = textLayout.getHorizontalPosition(safeOffset, usePrimaryDirection = true)
                                            val y = textLayout.getLineBottom(line)

                                            try {
                                                val windowPos = coords.localToWindow(Offset(x, y))
                                                rootCoords!!.windowToLocal(windowPos)
                                            } catch (e: Exception) {
                                                Offset.Unspecified
                                            }
                                        } else {
                                            Offset.Unspecified
                                        }
                                    } else {
                                        Offset.Unspecified
                                    }

                                    if (pos.isSpecified) {
                                        translationX = pos.x - 18.dp.toPx()
                                        translationY = pos.y
                                        alpha = 1f
                                    } else {
                                        alpha = 0f
                                    }
                                }
                                .size(36.dp)
                                .onGloballyPositioned { handleCoords = it }
                                .pointerInput(handleType) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        isDraggingHandle = true
                                        var currentDragHandle = handleType

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()

                                            if (handleCoords != null && rootCoords != null && handleCoords!!.isAttached && rootCoords!!.isAttached) {
                                                try {
                                                    val pointerWindow = handleCoords!!.localToWindow(change.position)
                                                    val pointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                                    val targetRootY = pointerRoot.y - 36.dp.toPx()
                                                    val targetRootPos = Offset(pointerRoot.x, targetRootY)

                                                    magnifierCenter = targetRootPos

                                                    val targetWindowPos = rootCoords!!.localToWindow(targetRootPos)
                                                    currentDragHandle = latestUpdateSelection(targetWindowPos, currentDragHandle)
                                                } catch (e: Exception) {
                                                    // Ignore detachment crashes during fast scrolls
                                                }
                                            }
                                        }
                                        isDraggingHandle = false
                                        magnifierCenter = Offset.Unspecified
                                    }
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.teardrop),
                                contentDescription = if (isStart) "Start handle" else "End handle",
                                modifier = Modifier.size(36.dp).graphicsLayer {
                                    rotationZ = if (isStart) 30f else -30f
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                },
                                tint = Color(0xFF1976D2)
                            )
                        }
                    }
                }

                if (showColorPickerDialog != null) {
                    AlertDialog(
                        onDismissRequest = { showColorPickerDialog = null },
                        title = { Text(stringResource(R.string.dialog_select_color)) },
                        text = {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(HighlightColor.entries) { colorOption ->
                                    Box(
                                        modifier = Modifier.size(48.dp)
                                            .background(colorOption.color, CircleShape).border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            ).clickable {
                                                onUpdatePalette(
                                                    showColorPickerDialog!!,
                                                    colorOption
                                                )
                                                showColorPickerDialog = null
                                            })
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showColorPickerDialog = null
                            }) { Text(stringResource(R.string.action_close)) }
                        })
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
                        })
                }
            }
        } else {
            Timber.w("Book has no pages to display.")
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.msg_book_no_content))
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
private fun RenderFlexChildBlock(
    childBlock: ContentBlock,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
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
    blockLayoutMap: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    density: Density,
    imageLoader: ImageLoader,
    pageIndex: Int
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
            createHeaderTextStyle(
                baseStyle = textStyle,
                level = block.level,
                textAlign = block.textAlign
            )
        } else {
            textStyle
        }

        TextWithEmphasis(
            text = finalContent,
            style = finalStyle,
            modifier = Modifier,
            pageIndex = pageIndex,
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
                    blockLayoutMap["${cfi}_$pageIndex"] = Triple(layout, coords, block)
                }
            })
    }

    when (childBlock) {
        is ListItemBlock -> {
            Row(modifier = Modifier, verticalAlignment = Alignment.Top) {
                val markerAreaModifier = Modifier
                    .width(32.dp)
                    .padding(end = 8.dp)
                val itemMarkerImage = childBlock.itemMarkerImage
                val itemMarker = childBlock.itemMarker

                if (itemMarkerImage != null) {
                    val imageRequest =
                        Builder(LocalContext.current).data(File(itemMarkerImage))
                            .crossfade(true).build()
                    val imageSize = with(density) { (textStyle.fontSize.value * 0.8f).sp.toDp() }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.content_desc_list_item_marker),
                        modifier = markerAreaModifier.height(imageSize),
                        alignment = Alignment.CenterEnd,
                        contentScale = ContentScale.FillHeight
                    )
                } else if (itemMarker != null) {
                    Text(
                        text = itemMarker,
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

            BoxWithConstraints(contentAlignment = imageBlockContentAlignment(style)) {
                val scaledSize = computeImageRenderSizeDp(
                    block = childBlock,
                    density = density,
                    maxWidthDp = maxWidth,
                    imageSizeMultiplier = imageSizeMultiplier
                )
                val imageModifier = Modifier
                    .then(
                        if (scaledSize != null) {
                            Modifier.width(scaledSize.first).height(scaledSize.second)
                        } else if (style.width != Dp.Unspecified && style.width > 0.dp) {
                            Modifier.width(style.width)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (scaledSize == null && style.maxWidth != Dp.Unspecified && style.maxWidth > 0.dp) {
                            Modifier.widthIn(max = style.maxWidth)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (scaledSize == null) {
                            if (childBlock.expectedHeight > 0) {
                                Modifier.height(with(density) { (childBlock.expectedHeight * imageSizeMultiplier).toDp() })
                            } else {
                                Modifier.height(250.dp)
                            }
                        } else {
                            Modifier
                        }
                    )

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
        }

        is SpacerBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(childBlock.height)
                    .drawCssBorders(childBlock.style, density)
            )
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
                                .drawCssBorders(cellStyle, density)
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
                                            modifier = tableCellImageModifier(
                                                block = blockInCell,
                                                density = density,
                                                imageSizeMultiplier = imageSizeMultiplier
                                            )
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
    paperColor: Color,
    isDarkTheme: Boolean,
    touchY: Float?,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f
): Modifier = composed {

    val frontPath = remember { Path() }
    val backPath = remember { Path() }
    val reflectedScreenPath = remember { Path() }

    this
        .graphicsLayer {
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction

            if (abs(pageOffset) > 0.001f && abs(pageOffset) < 0.999f) {
                Timber.tag("PageTurnFixDiag").d("graphicsLayer: Page $pageIndex, Offset: $pageOffset")
            }

            if (pageOffset <= 1f && pageOffset > -1f) {
                translationX = -pageOffset * size.width
            }

            if (pageOffset != 0f) {
                shadowElevation = 10f
                shape = RectangleShape
                clip = false
            }
        }
        .drawWithContent {
            val drawStart = System.nanoTime()
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
            fun drawPaperBackground() {
                drawRect(color = paperColor)
                if (textureBitmap != null && textureAlpha > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                        blendMode = BlendMode.SrcOver,
                        alpha = textureAlpha
                    )
                }
            }

            if (abs(pageOffset) < 0.001f) {
                drawPaperBackground()
                drawContent()
            }
            else if (pageOffset < 0f && pageOffset > -1f) {
                val progress = -pageOffset
                val w = size.width
                val h = size.height

                val startY = touchY ?: h
                val rawCenterDist = ((startY - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)

                val flattenFactor = if (progress > 0.75f) {
                    ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val centerDist = rawCenterDist * (1f - flattenFactor)

                val cornerY = if (centerDist >= 0) h else 0f

                val dragX = w - w * 2.2f * progress
                val dragY = cornerY - h * 0.5f * progress * centerDist

                val midX = (w + dragX) / 2f
                val midY = (cornerY + dragY) / 2f

                val dx = w - dragX
                val dy = cornerY - dragY
                val nLen = sqrt(dx * dx + dy * dy)

                // CRITICAL GEOMETRY LOG
                if (progress > 0.8f) { // Focus logs on the "end" of the turn where the stall happens
                    Timber.tag("PageTurnFixDiag").i(
                        "Geometry Page $pageIndex: progress=$progress, nLen=$nLen, cornerY=$cornerY, dragX=$dragX, midX=$midX"
                    )
                }

                if (nLen > 0f) {
                    val nx = dx / nLen
                    val ny = dy / nLen

                    if (nx.isNaN() || ny.isNaN()) {
                        Timber.tag("PageTurnFixDiag").e("NAN DETECTED in Normal Vectors: nx=$nx, ny=$ny")
                    }

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
                        drawPaperBackground()
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
                            drawPath(reflectedScreenPath, color = paperColor)
                            if (textureBitmap != null && textureAlpha > 0f) {
                                clipPath(reflectedScreenPath) {
                                    drawRect(
                                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                                        blendMode = BlendMode.SrcOver,
                                        alpha = textureAlpha
                                    )
                                }
                            }
                            val flapTint = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            drawPath(reflectedScreenPath, color = flapTint)

                            val innerShadowWidth = shadowWidth * 0.7f
                            val innerShadowBrush = Brush.linearGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.05f), Color.Transparent),
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
                    drawPaperBackground()
                    drawContent()
                }
            }
            else {
                drawPaperBackground()
                drawContent()
            }

            val drawDuration = (System.nanoTime() - drawStart) / 1_000_000.0
            if (drawDuration > 12.0) { // Log slow frames (anything near the 16ms frame budget)
                Timber.tag("PageTurnFixDiag").w("Slow Draw on Page $pageIndex: ${drawDuration}ms")
            }
        }
}

@Suppress("KotlinConstantConditions")
@Composable
fun Modifier.drawCssBorders(
    blockStyle: BlockStyle,
    @Suppress("unused") density: Density
): Modifier = this.drawBehind {
    val borderTop = blockStyle.borderTop
    val borderRight = blockStyle.borderRight
    val borderBottom = blockStyle.borderBottom
    val borderLeft = blockStyle.borderLeft
    val topWidth = borderTop?.width?.toPx() ?: 0f
    val rightWidth = borderRight?.width?.toPx() ?: 0f
    val bottomWidth = borderBottom?.width?.toPx() ?: 0f
    val leftWidth = borderLeft?.width?.toPx() ?: 0f

    val tlRadius = blockStyle.borderTopLeftRadius.toPx()
    val trRadius = blockStyle.borderTopRightRadius.toPx()
    val brRadius = blockStyle.borderBottomRightRadius.toPx()
    val blRadius = blockStyle.borderBottomLeftRadius.toPx()

    if (blockStyle.backgroundColor.isSpecified && blockStyle.backgroundColor != Color.Transparent) {
        val bgPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(tlRadius, tlRadius),
                    topRight = CornerRadius(trRadius, trRadius),
                    bottomRight = CornerRadius(brRadius, brRadius),
                    bottomLeft = CornerRadius(blRadius, blRadius)
                )
            )
        }
        drawPath(bgPath, color = blockStyle.backgroundColor, style = Fill)
    }

    // 2. Helper for PathEffects
    fun getPathEffect(style: String?, width: Float): PathEffect? {
        return when (style) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 2f), 0f)
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(width, width), 0f)
            else -> null
        }
    }

    // TOP
    if (topWidth > 0f && borderTop != null) {
        val color = borderTop.color
        val effect = getPathEffect(borderTop.style, topWidth)
        val offset = topWidth / 2f

        val startX = if (tlRadius > 0) tlRadius else 0f
        val endX = if (trRadius > 0) size.width - trRadius else size.width

        drawLine(
            color = color,
            start = Offset(startX, offset),
            end = Offset(endX, offset),
            strokeWidth = topWidth,
            pathEffect = effect
        )
    }

    // BOTTOM
    if (bottomWidth > 0f && borderBottom != null) {
        val color = borderBottom.color
        val effect = getPathEffect(borderBottom.style, bottomWidth)
        val offset = size.height - (bottomWidth / 2f)

        val startX = if (blRadius > 0) blRadius else 0f
        val endX = if (brRadius > 0) size.width - brRadius else size.width

        drawLine(
            color = color,
            start = Offset(startX, offset),
            end = Offset(endX, offset),
            strokeWidth = bottomWidth,
            pathEffect = effect
        )
    }

    // LEFT
    if (leftWidth > 0f && borderLeft != null) {
        val color = borderLeft.color
        val effect = getPathEffect(borderLeft.style, leftWidth)
        val offset = leftWidth / 2f

        val startY = if (tlRadius > 0) tlRadius else 0f
        val endY = if (blRadius > 0) size.height - blRadius else size.height

        drawLine(
            color = color,
            start = Offset(offset, startY),
            end = Offset(offset, endY),
            strokeWidth = leftWidth,
            pathEffect = effect
        )
    }

    // RIGHT
    if (rightWidth > 0f && borderRight != null) {
        val color = borderRight.color
        val effect = getPathEffect(borderRight.style, rightWidth)
        val offset = size.width - (rightWidth / 2f)

        val startY = if (trRadius > 0) trRadius else 0f
        val endY = if (brRadius > 0) size.height - brRadius else size.height

        drawLine(
            color = color,
            start = Offset(offset, startY),
            end = Offset(offset, endY),
            strokeWidth = rightWidth,
            pathEffect = effect
        )
    }

    if (tlRadius > 0f && topWidth > 0f && leftWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 180f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth/2f, topWidth/2f),
            size = Size(tlRadius * 2 - leftWidth, tlRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (trRadius > 0f && topWidth > 0f && rightWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 270f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (trRadius * 2) + (rightWidth/2f), topWidth/2f),
            size = Size(trRadius * 2 - rightWidth, trRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (brRadius > 0f && bottomWidth > 0f && rightWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 0f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (brRadius * 2) + (rightWidth/2f), size.height - (brRadius * 2) + (bottomWidth/2f)),
            size = Size(brRadius * 2 - rightWidth, brRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }

    if (blRadius > 0f && bottomWidth > 0f && leftWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 90f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth/2f, size.height - (blRadius * 2) + (bottomWidth/2f)),
            size = Size(blRadius * 2 - leftWidth, blRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }
}
