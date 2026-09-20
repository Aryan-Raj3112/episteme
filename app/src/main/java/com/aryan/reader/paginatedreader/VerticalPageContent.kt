package com.aryan.reader.paginatedreader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import java.io.File
import timber.log.Timber

/**
 * Native vertical-rl (`tategaki`) page renderer for paginated EPUB chapters.
 *
 * Pagination ([paginateVerticalContentBlocks]) measures vertical text with
 * [layoutVerticalParagraph] and stacks columns right-to-left; this composable
 * renders the same blocks with the same engine ([VerticalParagraphText]) and
 * the same inputs, so on-screen pages agree with measured breaks exactly.
 * Text blocks size themselves from the engine layout; horizontal padding /
 * collapsed margins are applied around them exactly like the paginator
 * accounts for them ([verticalFlowWidth]/`verticalSpaceBefore`).
 *
 * v1 scope: search/TTS/highlight overlays, link taps and highlight taps.
 * Drag text selection is not supported in vertical pages yet (selection
 * handles need a horizontal [androidx.compose.ui.text.TextLayoutResult]).
 */
@Composable
internal fun VerticalPageContent(
    blocks: List<ContentBlock>,
    textStyle: TextStyle,
    pageHeightPx: Int,
    contentWidthPx: Float,
    density: Density = LocalDensity.current,
    imageSizeMultiplier: Float,
    hideImages: Boolean,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    pageUserHighlights: List<UserHighlight>,
    fallbackTextColor: Color,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit,
    onHighlightClick: (UserHighlight) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            var previousBlock: ContentBlock? = null
            for (block in blocks) {
                val spaceBeforePx = with(density) {
                    if (previousBlock == null) {
                        0f
                    } else {
                        maxOf(
                            previousBlock.style.margin.right.toPx(),
                            block.style.margin.left.toPx()
                        )
                    }
                }
                when (block) {
                    is TextContentBlock -> {
                        if (block.content.isNotBlank()) {
                            VerticalPageTextBlock(
                                block = block,
                                textStyle = textStyle,
                                pageHeightPx = pageHeightPx,
                                density = density,
                                spaceBeforePx = spaceBeforePx,
                                searchQuery = searchQuery,
                                searchHighlightColor = searchHighlightColor,
                                ttsHighlightInfo = ttsHighlightInfo,
                                ttsHighlightColor = ttsHighlightColor,
                                pageUserHighlights = pageUserHighlights,
                                fallbackTextColor = fallbackTextColor,
                                onLinkClick = onLinkClick,
                                onGeneralTap = onGeneralTap,
                                onHighlightClick = onHighlightClick
                            )
                        }
                    }

                    is ImageBlock -> {
                        if (!hideImages) {
                            VerticalPageImageBlock(
                                block = block,
                                pageHeightPx = pageHeightPx,
                                contentWidthPx = contentWidthPx,
                                density = density,
                                imageSizeMultiplier = imageSizeMultiplier,
                                spaceBeforePx = spaceBeforePx
                            )
                        }
                    }

                    is SpacerBlock -> {
                        // Zero flow width in vertical pagination; nothing to draw.
                    }

                    else -> {
                        Timber.w(
                            "VerticalPageContent: skipping unsupported block " +
                                "${block::class.simpleName} index=${block.blockIndex}"
                        )
                    }
                }
                previousBlock = block
            }
        }
    }
}

@Composable
private fun VerticalPageTextBlock(
    block: TextContentBlock,
    textStyle: TextStyle,
    pageHeightPx: Int,
    density: Density,
    spaceBeforePx: Float,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    pageUserHighlights: List<UserHighlight>,
    fallbackTextColor: Color,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit,
    onHighlightClick: (UserHighlight) -> Unit
) {
    val params = remember(block, textStyle, pageHeightPx) {
        block.verticalContentParams(
            textStyle,
            pageHeightPx,
            density,
            defaultVerticalLineSpacing(textStyle)
        )
    }
    val columnHeightDp = with(density) { params.columnHeightPx.toDp() }
    val fontSize = with(density) { params.fontSizePx.toSp() }
    // Physical left/right padding and borders widen the block exactly like
    // the paginator accounts for them; the collapsed margin gap faces the
    // previous (righter) block, i.e. RTL start.
    val padStartPx = spaceBeforePx +
        with(density) {
            block.style.padding.right.coerceAtLeast(0.dp).toPx() +
                (block.style.borderRight?.width?.toPx() ?: 0f)
        }
    val padEndPx = with(density) {
        block.style.padding.left.coerceAtLeast(0.dp).toPx() +
            (block.style.borderLeft?.width?.toPx() ?: 0f)
    }
    // Physical top decorations shift text down inside the fixed column
    // height; the engine budget already excludes them, so nothing overflows.
    val topDecorDp = with(density) {
        (
            block.style.margin.top.toPx() +
                block.style.padding.top.coerceAtLeast(0.dp).toPx() +
                (block.style.borderTop?.width?.toPx() ?: 0f)
            ).toDp()
    }
    val textColor = textStyle.color.takeIf { it != Color.Unspecified } ?: fallbackTextColor
    var layout by remember(block) { mutableStateOf<VerticalParagraphLayout?>(null) }

    Box(
        modifier = Modifier
            .requiredHeight(columnHeightDp)
            .padding(
                start = with(density) { padStartPx.toDp() },
                end = with(density) { padEndPx.toDp() }
            )
            .drawCssBorders(blockStyle = block.style, density = density),
        contentAlignment = Alignment.TopStart
    ) {
        Box(modifier = Modifier.offset(y = topDecorDp)) {
            VerticalParagraphText(
                text = block.content.text,
                rubies = block.rubies,
                tcyRanges = params.tcyRanges,
                fontSize = fontSize,
                lineHeightEm = params.lineHeightEm,
                textAlign = params.textAlign,
                color = textColor,
                fontFamily = textStyle.fontFamily,
                fontWeight = textStyle.fontWeight,
                fontFeatureSettings = textStyle.fontFeatureSettings,
                columnHeightPx = params.columnHeightPx,
                onLayoutResolved = { resolved -> layout = resolved }
            )
            val resolved = layout
            if (resolved != null) {
                val overlayRects = remember(
                    resolved, block, searchQuery, ttsHighlightInfo, pageUserHighlights
                ) {
                    verticalOverlayRects(
                        layout = resolved,
                        block = block,
                        searchQuery = searchQuery,
                        searchHighlightColor = searchHighlightColor,
                        ttsHighlightInfo = ttsHighlightInfo,
                        ttsHighlightColor = ttsHighlightColor,
                        pageUserHighlights = pageUserHighlights
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            for ((rect, color) in overlayRects) {
                                drawRect(color = color, topLeft = rect.topLeft, size = rect.size)
                            }
                        }
                        .pointerInput(resolved, block, pageUserHighlights) {
                            detectTapGestures(
                                onTap = { position ->
                                    val tappedOffset = resolved.offsetAt(position)
                                    val href = tappedOffset?.let {
                                        block.content.readerUrlAnnotationAtOffset(it)
                                    }
                                    if (href != null) {
                                        onLinkClick(href)
                                        return@detectTapGestures
                                    }
                                    val highlightHit = pageUserHighlights.firstOrNull { highlight ->
                                        getHighlightOffsetsInBlock(block, highlight)?.let { range ->
                                            resolved.rectsForRange(range.first, range.last + 1)
                                                .any { rect -> rect.contains(position) }
                                        } == true
                                    }
                                    if (highlightHit != null) {
                                        onHighlightClick(highlightHit)
                                    } else {
                                        onGeneralTap(position)
                                    }
                                }
                            )
                        }
                )
            }
        }
    }
}

/** Overlay rectangles (search, TTS, user highlights) in paragraph space. */
private fun verticalOverlayRects(
    layout: VerticalParagraphLayout,
    block: TextContentBlock,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    pageUserHighlights: List<UserHighlight>
): List<Pair<Rect, Color>> {
    val out = mutableListOf<Pair<Rect, Color>>()
    if (searchQuery.length >= 3) {
        for (range in findVerticalQueryRanges(block.content.text, searchQuery)) {
            for (rect in layout.rectsForRange(range.first, range.last + 1)) {
                out.add(rect to searchHighlightColor)
            }
        }
    }
    if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
        val blockStartAbs = block.startCharOffsetInSource
        val blockEndAbs = blockStartAbs + block.content.length
        val highlightStartAbs = ttsHighlightInfo.offset
        val highlightEndAbs = ttsHighlightInfo.offset + ttsHighlightInfo.text.length
        val startAbs = maxOf(blockStartAbs, highlightStartAbs)
        val endAbs = minOf(blockEndAbs, highlightEndAbs)
        if (startAbs < endAbs) {
            for (rect in layout.rectsForRange(startAbs - blockStartAbs, endAbs - blockStartAbs)) {
                out.add(rect to ttsHighlightColor)
            }
        }
    }
    for (highlight in pageUserHighlights) {
        val range = getHighlightOffsetsInBlock(block, highlight) ?: continue
        for (rect in layout.rectsForRange(range.first, range.last + 1)) {
            out.add(rect to highlight.renderColor(legacyAlpha = 0.4f))
        }
    }
    return out
}

private fun findVerticalQueryRanges(text: String, query: String): List<IntRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var startIndex = 0
    while (startIndex < text.length) {
        val index = text.indexOf(query, startIndex, ignoreCase = true)
        if (index == -1) break
        ranges.add(index..(index + query.length - 1))
        startIndex = index + query.length
    }
    return ranges
}

@Composable
private fun VerticalPageImageBlock(
    block: ImageBlock,
    pageHeightPx: Int,
    contentWidthPx: Float,
    density: Density,
    imageSizeMultiplier: Float,
    spaceBeforePx: Float
) {
    // Same budget the paginator measures with: page height minus physical
    // top/bottom padding and borders.
    val columnHeightPx = (pageHeightPx - with(density) {
        block.style.padding.top.coerceAtLeast(0.dp).toPx() +
            block.style.padding.bottom.coerceAtLeast(0.dp).toPx() +
            (block.style.borderTop?.width?.toPx() ?: 0f) +
            (block.style.borderBottom?.width?.toPx() ?: 0f)
    }).coerceAtLeast(48f)
    // Mirror measureVerticalContentImage exactly (it returns only the
    // contain-fit width): re-derive the clamped height from the aspect.
    val (widthPx, heightPx) = remember(
        block, contentWidthPx, columnHeightPx, imageSizeMultiplier
    ) {
        var width = measureVerticalContentImage(
            block,
            contentWidthPx.toInt().coerceAtLeast(1),
            columnHeightPx,
            imageSizeMultiplier,
            hideImages = false
        ).toFloat()
        val aspect = verticalImageAspect(block)
        var height = width * aspect
        if (aspect > 0f && height > columnHeightPx) {
            height = columnHeightPx
            width = (height / aspect).coerceAtLeast(1f)
        }
        width to height
    }
    if (widthPx <= 0f || heightPx <= 0f) return
    val topDecorDp = with(density) {
        (
            block.style.padding.top.coerceAtLeast(0.dp).toPx() +
                (block.style.borderTop?.width?.toPx() ?: 0f)
            ).toDp()
    }
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .requiredHeight(with(density) { columnHeightPx.toDp() })
            .padding(start = with(density) { spaceBeforePx.toDp() }),
        contentAlignment = Alignment.TopStart
    ) {
        val imageRequest = remember(block.path) {
            Builder(context).data(File(block.path)).crossfade(true).build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = block.altText ?: "Image from EPUB",
            modifier = Modifier
                .offset(y = topDecorDp)
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { heightPx.toDp() }),
            contentScale = imageContentScale(block.style)
        )
    }
}

private fun verticalImageAspect(block: ImageBlock): Float {
    val w = block.intrinsicWidth?.takeIf { it > 0f }
    val h = block.intrinsicHeight?.takeIf { it > 0f }
    if (w == null || h == null) return 1f
    return h / w
}
