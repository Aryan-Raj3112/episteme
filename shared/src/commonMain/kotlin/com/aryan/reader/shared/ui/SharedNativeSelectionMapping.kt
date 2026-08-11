package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubCutoffDiagnosticsTag
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun List<UserHighlight>.visibleInPage(page: ReaderPage): List<UserHighlight> {
    return filter { highlight ->
        val locator = highlight.locator.withFallbacks(
            chapterIndex = highlight.chapterIndex,
            cfi = highlight.cfi,
            textQuote = highlight.text
        )
        (locator.chapterIndex ?: highlight.chapterIndex) == page.chapterIndex &&
            page.containsNativeHighlightLocator(locator, highlight.cfi)
    }
}

internal fun ReaderPage.containsNativeHighlightLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
    if (containsNativeBlockLocator(locator)) return true
    if (containsNativeSourceCfiLocator(locator, fallbackCfi)) return true
    if (locator.hasTextRange) {
        if (locator.hasSharedNativeStructuralScope(fallbackCfi)) return false
        val start = locator.startOffset ?: return false
        val end = locator.endOffset ?: start
        return if (start == end) {
            containsNativeCollapsedOffset(start)
        } else {
            start < endOffset && end > startOffset
        }
    }
    locator.pageIndex?.let { return it == pageIndex }
    val prefix = "desktop:${chapterIndex}:"
    val desktopPageIndex = fallbackCfi
        .takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.substringBefore(':')
        ?.toIntOrNull()
    return desktopPageIndex != null && desktopPageIndex >= 0 && desktopPageIndex == pageIndex
}

internal fun ReaderPage.containsNativeCollapsedOffset(offset: Int): Boolean {
    return if (startOffset == endOffset) {
        offset == startOffset
    } else {
        offset >= startOffset && offset < endOffset
    }
}

internal fun ReaderPage.containsNativeBlockLocator(locator: ReaderLocator): Boolean {
    val blockIndex = locator.blockIndex ?: return false
    val blocks = semanticBlocks.flattenNativeSemanticBlocks()
    if (blocks.isEmpty()) return false
    val matchingBlocks = blocks.filter { it.blockIndex == blockIndex }
    if (matchingBlocks.isEmpty()) return false
    val charOffset = locator.charOffset ?: return true
    if (!containsNativeCollapsedOffset(charOffset)) return false
    return matchingBlocks.filterIsInstance<SemanticTextBlock>().any { block ->
        val start = block.startCharOffsetInSource
        val end = start + block.text.length
        charOffset in start until end || (block.text.isEmpty() && charOffset == start)
    }
}

internal fun ReaderPage.containsNativeSourceCfiLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
    val cfi = (locator.cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi)
        .takeIf { it.startsWith("/") || it.contains("|/") }
        ?: return false
    val blocks = semanticBlocks.flattenNativeSemanticBlocks().filterIsInstance<SemanticTextBlock>()
    if (blocks.isEmpty()) return false
    val parts = cfi.split('|').mapNotNull { it.sharedNativeCfiPointOrNull(allowMissingOffset = true) }
    val startPoint = parts.firstOrNull() ?: return false
    val endPoint = parts.lastOrNull() ?: startPoint
    val quoteLength = locator.textQuote?.length ?: 0
    return blocks.any { block ->
        val blockPath = block.cfi?.substringBefore(':')?.takeIf { it.startsWith("/") } ?: return@any false
        val startMatches = sharedNativeCfiPathsEquivalent(startPoint.path, blockPath)
        val endMatches = sharedNativeCfiPathsEquivalent(endPoint.path, blockPath)
        val isIntermediate = parts.size > 1 &&
            !startMatches &&
            !endMatches &&
            sharedNativeCfiPathStrictlyBetween(blockPath, startPoint.path, endPoint.path)
        if (!startMatches && !endMatches && !isIntermediate) return@any false
        val blockStart = block.startCharOffsetInSource
        val blockEnd = blockStart + block.text.length
        val rangeStart = when {
            startMatches -> sharedNativeCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length)
            isIntermediate || endMatches -> blockStart
            else -> blockStart
        }
        val rangeEnd = when {
            endMatches && parts.size > 1 -> sharedNativeCfiOffsetToAbsolute(endPoint.offset, blockStart, block.text.length)
            startMatches && parts.size == 1 && quoteLength > 0 ->
                sharedNativeCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length) + quoteLength
            startMatches && parts.size == 1 -> sharedNativeCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length)
            isIntermediate -> blockEnd
            else -> blockEnd
        }
        if (rangeStart == rangeEnd) {
            containsNativeCollapsedOffset(rangeStart)
        } else {
            minOf(rangeStart, rangeEnd) < endOffset && maxOf(rangeStart, rangeEnd) > startOffset
        }
    }
}

internal fun sharedNativeCfiOffsetToAbsolute(offset: Int, blockStart: Int, textLength: Int): Int {
    val blockEnd = blockStart + textLength
    return when {
        offset in 0..textLength -> blockStart + offset
        offset in blockStart..blockEnd -> offset
        else -> blockStart + offset.coerceIn(0, textLength)
    }
}

internal fun AnnotatedString.Builder.applyHighlightToTextRange(
    highlight: UserHighlight,
    chapterIndex: Int? = null,
    pageIndex: Int? = null,
    blockCfi: String? = null,
    blockIndex: Int? = null,
    blockCharOffset: Int? = null,
    textStartOffset: Int,
    textLength: Int,
    text: String? = null
) {
    fun applyRange(range: SharedNativeReaderTextRange) {
        addStyle(
            style = highlight.nativeSpanStyle(),
            start = range.start,
            end = range.end
        )
        addStringAnnotation(ReaderNativeAnnotationHighlight, highlight.id, range.start, range.end)
    }

    fun logResult(reason: String, range: SharedNativeReaderTextRange?) {
        logNativeHighlightMapResult(
            reason = reason,
            highlight = highlight,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            blockIndex = blockIndex,
            blockCharOffset = blockCharOffset,
            blockCfi = blockCfi,
            textStartOffset = textStartOffset,
            textLength = textLength,
            range = range,
            text = text
        )
    }

    val blockLocatorRange = sharedNativeBlockLocatorHighlightRangeInBlock(
        highlight = highlight,
        blockIndex = blockIndex,
        blockCharOffset = blockCharOffset,
        textLength = textLength,
        text = text
    )
    if (blockLocatorRange != null) {
        logResult("block_locator", blockLocatorRange)
        applyRange(blockLocatorRange)
        return
    }

    val locatorRange = sharedNativeLocatorHighlightRangeInBlock(
        highlight = highlight,
        blockCfi = blockCfi,
        blockIndex = blockIndex,
        textStartOffset = textStartOffset,
        textLength = textLength,
        text = text
    )
    if (locatorRange != null) {
        logResult("locator_offsets", locatorRange)
        applyRange(locatorRange)
        return
    }

    val cfiRange = sharedNativeHighlightRangeInBlock(
        highlight = highlight,
        blockCfi = blockCfi,
        textStartOffset = textStartOffset,
        textLength = textLength,
        text = text
    )
    if (cfiRange != null) {
        logResult("cfi_or_text", cfiRange)
        applyRange(cfiRange)
        return
    }
    if (highlight.locator.hasTextRange) {
        logResult("locator_offsets_miss", null)
        return
    }
    logResult("no_match", null)
}

internal fun UserHighlight.nativeSpanStyle(): SpanStyle {
    return when (style) {
        HighlightStyle.BACKGROUND -> SpanStyle(background = renderColor(legacyAlpha = 0.38f))
        HighlightStyle.UNDERLINE, HighlightStyle.WAVY_UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        HighlightStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    }
}

internal fun logNativeHighlightMapResult(
    reason: String,
    highlight: UserHighlight,
    chapterIndex: Int?,
    pageIndex: Int?,
    blockIndex: Int?,
    blockCharOffset: Int?,
    blockCfi: String?,
    textStartOffset: Int,
    textLength: Int,
    range: SharedNativeReaderTextRange?,
    text: String?
) {
    val locator = highlight.locator.withFallbacks(
        chapterIndex = highlight.chapterIndex,
        cfi = highlight.cfi,
        textQuote = highlight.text
    )
    logSharedReaderDiagnostic(DesktopHighlightMapLogTag) {
        val renderPage = pageIndex?.let { it + 1 }?.toString() ?: "null"
        val locatorPage = locator.pageIndex?.let { it + 1 }?.toString() ?: "null"
        val textEndOffset = textStartOffset + textLength
        val localRange = range?.let { "${it.start}..${it.end}" } ?: "none"
        val absoluteRange = range
            ?.let { "${textStartOffset + it.start}..${textStartOffset + it.end}" }
            ?: "none"
        val blockText = text
        val matchedText = if (
            range != null &&
            blockText != null &&
            range.start >= 0 &&
            range.end <= blockText.length &&
            range.start < range.end
        ) {
            blockText.substring(range.start, range.end).sharedNativeLogPreview(120)
        } else {
            ""
        }
        "native_highlight_match reason=$reason id=\"${highlight.id.sharedNativeLogPreview(64)}\" " +
            "color=${highlight.color.name} renderChapter=${chapterIndex ?: "null"} renderPage=$renderPage " +
            "block=${blockIndex ?: "null"} blockChar=${blockCharOffset ?: "null"} " +
            "textRange=$textStartOffset..$textEndOffset textLen=$textLength local=$localRange absolute=$absoluteRange " +
            "locatorChapter=${locator.chapterIndex ?: "null"} legacyChapter=${highlight.chapterIndex} " +
            "locatorPage=$locatorPage locatorOffsets=${locator.startOffset ?: "null"}..${locator.endOffset ?: "null"} " +
            "locatorBlock=${locator.blockIndex ?: "null"} locatorChar=${locator.charOffset ?: "null"} " +
            "locatorCfi=\"${locator.cfi.orEmpty().sharedNativeLogPreview(120)}\" " +
            "blockCfi=\"${blockCfi.orEmpty().sharedNativeLogPreview(120)}\" " +
            "quote=\"${(locator.textQuote ?: highlight.text).sharedNativeLogPreview(120)}\" " +
            "matched=\"${matchedText}\" blockText=\"${text.orEmpty().sharedNativeLogPreview(120)}\""
    }
}

internal fun sharedNativeLocatorHighlightRangeInBlock(
    highlight: UserHighlight,
    blockCfi: String?,
    blockIndex: Int?,
    textStartOffset: Int,
    textLength: Int,
    text: String?
): SharedNativeReaderTextRange? {
    if (highlight.hasSharedNativeMultipartCfiRange()) return null
    val locatorBlockIndex = highlight.locator.blockIndex
    val blockMatchesLocator = locatorBlockIndex != null && locatorBlockIndex == blockIndex
    val cfiMatchesBlock = highlight.sharedNativeCfiTouchesBlock(blockCfi)
    val hasStructuralScope = locatorBlockIndex != null || highlight.sharedNativeSourceCfi().startsWith("/")
    if (hasStructuralScope && !blockMatchesLocator && !cfiMatchesBlock) return null
    val start = highlight.locator.startOffset ?: return null
    val end = highlight.locator.endOffset ?: return null
    val rangeStart = minOf(start, end)
    val rangeEnd = maxOf(start, end)
    val textEndOffset = textStartOffset + textLength
    val locatorRange = if (hasStructuralScope) {
        val localStart = sharedNativeScopedOffsetToLocalOrNull(rangeStart, textStartOffset, textLength)
        val localEnd = sharedNativeScopedOffsetToLocalOrNull(rangeEnd, textStartOffset, textLength)
        if (localStart != null && localEnd != null && localStart < localEnd) {
            SharedNativeReaderTextRange(localStart, localEnd)
        } else {
            null
        }
    } else {
        if (rangeEnd <= textStartOffset || rangeStart >= textEndOffset) {
            null
        } else {
            val localStart = (rangeStart - textStartOffset).coerceIn(0, textLength)
            val localEnd = (rangeEnd - textStartOffset).coerceIn(localStart, textLength)
            if (localStart < localEnd) SharedNativeReaderTextRange(localStart, localEnd) else null
        }
    }
    if (locatorRange == null && highlight.sharedNativeSourceCfi().startsWith("/")) return null
    val quoteRange = text
        ?.let { blockText ->
            sharedNativeHighlightTextRangeInBlock(
                blockText = blockText,
                highlightText = highlight.text,
                preferredStart = locatorRange?.start
            )
        }
    if (
        locatorRange != null &&
        quoteRange != null &&
        text != null &&
        !locatorRange.matchesSharedNativeHighlightText(text, highlight.text)
    ) {
        return quoteRange
    }
    return locatorRange ?: quoteRange
}

internal fun sharedNativeScopedOffsetToLocalOrNull(
    offset: Int,
    textStartOffset: Int,
    textLength: Int
): Int? {
    val textEndOffset = textStartOffset + textLength
    return when {
        offset in 0..textLength -> offset
        offset in textStartOffset..textEndOffset -> offset - textStartOffset
        else -> null
    }
}

internal fun sharedNativeBlockLocatorHighlightRangeInBlock(
    highlight: UserHighlight,
    blockIndex: Int?,
    blockCharOffset: Int?,
    textLength: Int,
    text: String?
): SharedNativeReaderTextRange? {
    val locatorBlockIndex = highlight.locator.blockIndex ?: return null
    if (blockIndex == null || locatorBlockIndex != blockIndex) return null
    val locatorCharOffset = highlight.locator.charOffset ?: return null
    val textStartOffset = blockCharOffset ?: 0
    val textEndOffset = textStartOffset + textLength
    val containsOffset = if (textLength == 0) {
        locatorCharOffset == textStartOffset
    } else {
        locatorCharOffset >= textStartOffset && locatorCharOffset < textEndOffset
    }
    if (!containsOffset) return null
    val localStart = (locatorCharOffset - textStartOffset).coerceIn(0, textLength)
    val quoteRange = text
        ?.let { blockText ->
            sharedNativeHighlightTextRangeInBlock(
                blockText = blockText,
                highlightText = highlight.locator.textQuote ?: highlight.text,
                preferredStart = localStart
            )
        }
    if (quoteRange != null) return quoteRange
    val fallbackLength = (highlight.locator.textQuote ?: highlight.text)
        .takeIf { it.isNotBlank() }
        ?.length
        ?: return null
    val localEnd = (localStart + fallbackLength).coerceIn(localStart, textLength)
    return if (localStart < localEnd) SharedNativeReaderTextRange(localStart, localEnd) else null
}

internal fun ReaderLocator.hasSharedNativeStructuralScope(fallbackCfi: String): Boolean {
    val sourceCfi = cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi
    return blockIndex != null || sourceCfi.startsWith("/")
}

internal fun UserHighlight.sharedNativeSourceCfi(): String {
    return locator.cfi?.takeIf { it.isNotBlank() } ?: cfi
}

internal fun UserHighlight.hasSharedNativeMultipartCfiRange(): Boolean {
    val parts = sharedNativeSourceCfi()
        .split('|')
        .mapNotNull { it.sharedNativeCfiPointOrNull(allowMissingOffset = true) }
    if (parts.size < 2) return false
    val start = parts.first().path
    return parts.drop(1).any { !sharedNativeCfiPathsEquivalent(start, it.path) }
}

internal fun UserHighlight.sharedNativeCfiTouchesBlock(blockCfi: String?): Boolean {
    val blockPath = blockCfi?.takeIf { it.startsWith("/") } ?: return false
    return sharedNativeSourceCfi()
        .split('|')
        .mapNotNull { it.sharedNativeCfiPointOrNull(allowMissingOffset = true) }
        .any { sharedNativeCfiPathsEquivalent(it.path, blockPath) }
}

internal fun SharedNativeReaderTextRange.matchesSharedNativeHighlightText(
    blockText: String,
    highlightText: String
): Boolean {
    if (start !in 0..end || end > blockText.length || highlightText.isBlank()) return true
    val actual = blockText.substring(start, end).sharedNativeComparableText()
    val expected = highlightText.sharedNativeComparableText()
    if (actual.isBlank() || expected.isBlank()) return true
    return actual == expected || expected.contains(actual) || actual.contains(expected)
}

internal fun sharedNativeHighlightTextRangeInBlock(
    blockText: String,
    highlightText: String,
    preferredStart: Int? = null
): SharedNativeReaderTextRange? {
    val quote = highlightText.trim().takeIf { it.isNotBlank() } ?: return null
    if (blockText.isEmpty()) return null
    if (quote.contains(blockText, ignoreCase = false) || quote.contains(blockText, ignoreCase = true)) {
        return SharedNativeReaderTextRange(0, blockText.length)
    }
    val exact = blockText.nearestIndexOf(quote, preferredStart, ignoreCase = false)
    if (exact >= 0) {
        return SharedNativeReaderTextRange(exact, (exact + quote.length).coerceAtMost(blockText.length))
    }
    val relaxed = blockText.nearestIndexOf(quote, preferredStart, ignoreCase = true)
    if (relaxed >= 0) {
        return SharedNativeReaderTextRange(relaxed, (relaxed + quote.length).coerceAtMost(blockText.length))
    }
    return sharedNativeFuzzyTextRange(blockText, quote)
}

internal fun String.nearestIndexOf(
    needle: String,
    preferredStart: Int?,
    ignoreCase: Boolean
): Int {
    if (needle.isEmpty()) return -1
    if (preferredStart == null) return indexOf(needle, ignoreCase = ignoreCase)
    var best = -1
    var bestDistance = Int.MAX_VALUE
    var searchStart = 0
    while (searchStart <= length) {
        val index = indexOf(needle, startIndex = searchStart, ignoreCase = ignoreCase)
        if (index < 0) break
        val distance = kotlin.math.abs(index - preferredStart)
        if (distance < bestDistance) {
            best = index
            bestDistance = distance
        }
        searchStart = index + 1
    }
    return best
}

internal fun sharedNativeFuzzyTextRange(
    source: String,
    target: String,
    ignoreCase: Boolean = true
): SharedNativeReaderTextRange? {
    if (target.isBlank()) return null
    val targetWords = target.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (targetWords.isEmpty()) return null
    var searchStart = 0
    while (searchStart < source.length) {
        val firstIndex = source.indexOf(targetWords[0], searchStart, ignoreCase = ignoreCase)
        if (firstIndex < 0) return null
        var currentIndex = firstIndex + targetWords[0].length
        var allMatch = true
        for (index in 1 until targetWords.size) {
            while (currentIndex < source.length && source[currentIndex].isWhitespace()) {
                currentIndex++
            }
            if (currentIndex >= source.length) {
                allMatch = false
                break
            }
            val word = targetWords[index]
            if (source.regionMatches(currentIndex, word, 0, word.length, ignoreCase = ignoreCase)) {
                currentIndex += word.length
            } else {
                allMatch = false
                break
            }
        }
        if (allMatch) return SharedNativeReaderTextRange(firstIndex, currentIndex)
        searchStart = firstIndex + 1
    }
    return null
}

internal fun String.sharedNativeComparableText(): String {
    return replace(Regex("\\s+"), " ").trim()
}

internal fun sharedNativeHighlightRangeForBlock(
    highlight: UserHighlight,
    blockCfi: String?,
    textStartOffset: Int,
    textLength: Int,
    text: String?,
    blockIndex: Int? = null,
    blockCharOffset: Int? = null
): SharedNativeReaderTextRange? {
    sharedNativeBlockLocatorHighlightRangeInBlock(
        highlight = highlight,
        blockIndex = blockIndex,
        blockCharOffset = blockCharOffset,
        textLength = textLength,
        text = text
    )?.let { return it }
    sharedNativeLocatorHighlightRangeInBlock(
        highlight = highlight,
        blockCfi = blockCfi,
        blockIndex = blockIndex,
        textStartOffset = textStartOffset,
        textLength = textLength,
        text = text
    )?.let { return it }
    sharedNativeHighlightRangeInBlock(
        highlight = highlight,
        blockCfi = blockCfi,
        textStartOffset = textStartOffset,
        textLength = textLength,
        text = text
    )?.let { return it }
    if (highlight.locator.hasTextRange) return null
    return null
}

internal fun sharedNativeVisibleHighlightsForPage(
    highlights: List<UserHighlight>,
    page: ReaderPage
): List<UserHighlight> {
    return highlights.visibleInPage(page)
}

internal fun AnnotatedString.Builder.applySelectionToTextRange(
    selection: SharedNativeReaderTextSelection?,
    pageIndex: Int? = null,
    blockIndex: Int? = null,
    blockCharOffset: Int? = null,
    textStartOffset: Int,
    textLength: Int,
    color: Color
) {
    if (selection == null) return
    val blockLocalRange = if (pageIndex != null && blockIndex != null && blockCharOffset != null) {
        sharedNativeSelectionRangeInBlock(
            selection = selection,
            pageIndex = pageIndex,
            blockIndex = blockIndex,
            blockCharOffset = blockCharOffset,
            textLength = textLength
        )
    } else {
        null
    }
    val localStart: Int
    val localEnd: Int
    if (blockLocalRange != null) {
        localStart = blockLocalRange.start
        localEnd = blockLocalRange.end
    } else {
        if (selection.startBlockIndex >= 0 || selection.endBlockIndex >= 0) return
        localStart = (selection.startOffset - textStartOffset).coerceIn(0, textLength)
        localEnd = (selection.endOffset - textStartOffset).coerceIn(localStart, textLength)
    }
    if (localStart < localEnd) {
        addStyle(
            style = SpanStyle(background = color),
            start = localStart,
            end = localEnd
        )
    }
}

internal fun AnnotatedString.stringAnnotationAt(tag: String, offset: Int): String? {
    if (isEmpty()) return null
    val start = offset.coerceIn(0, (length - 1).coerceAtLeast(0))
    val end = (start + 1).coerceAtMost(length)
    return getStringAnnotations(tag, start, end).firstOrNull()?.item
}

internal fun sharedNativeReaderSelectionGestureKey(
    textBlockKey: String,
    text: AnnotatedString
): String = "$textBlockKey:${text.text}"

internal data class SharedNativeSelectedTextRange(
    val info: SharedNativeTextLayoutInfo,
    val start: Int,
    val end: Int
)

internal data class SharedNativeSelectionEndpoint(
    val info: SharedNativeTextLayoutInfo,
    val localOffset: Int
)

internal fun sharedNativeSelectionMenuOffset(
    selection: SharedNativeReaderTextSelection,
    readerCoordinates: LayoutCoordinates?,
    density: Density,
    highlightPaletteSize: Int,
    actionCount: Int
): IntOffset {
    val coordinates = readerCoordinates?.takeIf { it.isAttached } ?: return IntOffset(16, 16)
    if (selection.rect == Rect.Zero) return IntOffset(16, 16)
    val leftTopLocal = coordinates.windowToLocal(Offset(selection.rect.left, selection.rect.top))
    val rightBottomLocal = coordinates.windowToLocal(Offset(selection.rect.right, selection.rect.bottom))
    val paddingPx = with(density) { 16.dp.toPx() }
    val estimatedWidthPx = with(density) { 280.dp.toPx() }
    val estimatedHeightPx = sharedNativeSelectionMenuEstimatedHeightPx(
        density = density,
        highlightPaletteSize = highlightPaletteSize,
        actionCount = actionCount
    )
    val selectionRect = SharedSelectionMenuRect(
        left = leftTopLocal.x,
        top = leftTopLocal.y,
        right = rightBottomLocal.x,
        bottom = rightBottomLocal.y
    )
    val placement = sharedSelectionMenuPlacement(
        viewport = SharedSelectionMenuViewport(coordinates.size.width, coordinates.size.height),
        popup = SharedSelectionMenuSize(
            width = estimatedWidthPx.roundToInt(),
            height = estimatedHeightPx.roundToInt()
        ),
        selection = selectionRect,
        marginPx = paddingPx,
        gapPx = paddingPx
    )
    return IntOffset(placement.x, placement.y)
}

internal fun sharedNativeSelectionMenuEstimatedHeightPx(
    density: Density,
    highlightPaletteSize: Int,
    actionCount: Int
): Float {
    val actionRows = ((actionCount.coerceAtLeast(1) + 2) / 3).coerceAtLeast(1)
    return with(density) {
        val paletteHeight = if (highlightPaletteSize > 0) 45.dp.toPx() else 0f
        val actionsHeight = 7.dp.toPx() +
            (actionRows * 56).dp.toPx() +
            ((actionRows - 1).coerceAtLeast(0) * 3).dp.toPx()
        paletteHeight + actionsHeight
    }
}

internal fun sharedNativeSelectionHandleOffset(
    selection: SharedNativeReaderTextSelection,
    handle: SharedNativeSelectionHandle,
    layouts: Collection<SharedNativeTextLayoutInfo>,
    readerCoordinates: LayoutCoordinates?,
    density: Density
): IntOffset? {
    val reader = readerCoordinates?.takeIf { it.isAttached } ?: return null
    val endpoint = sharedNativeSelectionEndpoint(selection, handle, layouts) ?: return null
    val textLength = endpoint.info.descriptor.text.length
    if (textLength <= 0) return null
    val safeOffset = endpoint.localOffset.coerceIn(0, textLength)
    val probeStart = when (handle) {
        SharedNativeSelectionHandle.START -> safeOffset.coerceIn(0, textLength - 1)
        SharedNativeSelectionHandle.END -> (safeOffset - 1).coerceIn(0, textLength - 1)
    }
    val probeEnd = (probeStart + 1).coerceAtMost(textLength)
    val localRect = runCatching {
        endpoint.info.layout.getPathForRange(probeStart, probeEnd).getBounds()
    }.getOrNull() ?: return null
    val localX = when (handle) {
        SharedNativeSelectionHandle.START -> if (safeOffset >= textLength) localRect.right else localRect.left
        SharedNativeSelectionHandle.END -> if (safeOffset <= probeStart) localRect.left else localRect.right
    }
    val windowPosition = endpoint.info.coordinates.localToWindow(Offset(localX, localRect.bottom))
    val readerPosition = reader.windowToLocal(windowPosition)
    val halfHandlePx = with(density) { 14.dp.toPx() }
    return IntOffset(
        x = (readerPosition.x - halfHandlePx).roundToInt(),
        y = readerPosition.y.roundToInt()
    )
}

internal fun sharedNativeSelectionWithHandleMoved(
    selection: SharedNativeReaderTextSelection,
    handle: SharedNativeSelectionHandle,
    windowPosition: Offset,
    layouts: Collection<SharedNativeTextLayoutInfo>
): SharedNativeReaderTextSelection? {
    val moved = sharedNativeReaderTextPositionAtWindow(windowPosition, layouts) ?: return null
    val opposite = sharedNativeSelectionEndpointPosition(
        selection = selection,
        handle = if (handle == SharedNativeSelectionHandle.START) SharedNativeSelectionHandle.END else SharedNativeSelectionHandle.START,
        layouts = layouts
    ) ?: return null
    return if (handle == SharedNativeSelectionHandle.START) {
        sharedNativeReaderSelectionBetween(moved, opposite, layouts)
    } else {
        sharedNativeReaderSelectionBetween(opposite, moved, layouts)
    }
}

internal fun sharedNativeSelectionEndpointPosition(
    selection: SharedNativeReaderTextSelection,
    handle: SharedNativeSelectionHandle,
    layouts: Collection<SharedNativeTextLayoutInfo>
): SharedNativeTextPosition? {
    val endpoint = sharedNativeSelectionEndpoint(selection, handle, layouts) ?: return null
    return SharedNativeTextPosition(
        descriptor = endpoint.info.descriptor,
        localOffset = endpoint.localOffset.coerceIn(0, endpoint.info.descriptor.text.length)
    )
}

internal fun sharedNativeSelectionEndpoint(
    selection: SharedNativeReaderTextSelection,
    handle: SharedNativeSelectionHandle,
    layouts: Collection<SharedNativeTextLayoutInfo>
): SharedNativeSelectionEndpoint? {
    val pageIndex = if (handle == SharedNativeSelectionHandle.START) {
        selection.startPageIndex
    } else {
        selection.endPageIndex
    }
    val blockIndex = if (handle == SharedNativeSelectionHandle.START) {
        selection.startBlockIndex
    } else {
        selection.endBlockIndex
    }
    val blockCharOffset = if (handle == SharedNativeSelectionHandle.START) {
        selection.startBlockCharOffset
    } else {
        selection.endBlockCharOffset
    }
    val localOffset = if (handle == SharedNativeSelectionHandle.START) {
        selection.startLocalOffset
    } else {
        selection.endLocalOffset
    }
    val key = SharedNativeSelectionBlockKey(pageIndex, blockIndex, blockCharOffset)
    val info = layouts.firstOrNull { it.coordinates.isAttached && it.descriptor.key == key } ?: return null
    return SharedNativeSelectionEndpoint(info, localOffset)
}

internal fun sharedNativeReaderTextPositionAtWindow(
    windowPosition: Offset,
    layouts: Collection<SharedNativeTextLayoutInfo>
): SharedNativeTextPosition? {
    val target = layouts
        .asSequence()
        .filter { it.coordinates.isAttached && it.descriptor.text.isNotEmpty() }
        .minByOrNull { info ->
            val rect = info.coordinates.boundsInWindow()
            val dx = maxOf(rect.left - windowPosition.x, 0f, windowPosition.x - rect.right)
            val dy = maxOf(rect.top - windowPosition.y, 0f, windowPosition.y - rect.bottom)
            dx * dx + dy * dy
        } ?: return null
    val localPosition = target.coordinates.windowToLocal(windowPosition)
    return SharedNativeTextPosition(
        descriptor = target.descriptor,
        localOffset = target.layout.getOffsetForPosition(localPosition)
            .coerceIn(0, target.descriptor.text.length)
    )
}

internal fun sharedNativeReaderSelectionBetween(
    start: SharedNativeTextPosition,
    end: SharedNativeTextPosition,
    layouts: Collection<SharedNativeTextLayoutInfo>
): SharedNativeReaderTextSelection? {
    if (start.descriptor.chapterIndex != end.descriptor.chapterIndex) return null
    val (orderedStart, orderedEnd) = if (sharedNativeCompareTextPositions(start, end) <= 0) {
        start to end
    } else {
        end to start
    }
    val selectedRanges = layouts
        .asSequence()
        .filter { it.coordinates.isAttached }
        .filter { info ->
            sharedNativeSelectionRangeInBlock(
                start = orderedStart,
                end = orderedEnd,
                block = info.descriptor,
                textLength = info.descriptor.text.length
            ) != null
        }
        .sortedWith(
            compareBy<SharedNativeTextLayoutInfo> { it.descriptor.pageIndex }
                .thenBy { it.descriptor.blockIndex }
                .thenBy { it.descriptor.blockCharOffset }
        )
        .mapNotNull { info ->
            val range = sharedNativeSelectionRangeInBlock(
                start = orderedStart,
                end = orderedEnd,
                block = info.descriptor,
                textLength = info.descriptor.text.length
            ) ?: return@mapNotNull null
            SharedNativeSelectedTextRange(info, range.start, range.end)
        }
        .toMutableList()
    sharedNativeTrimSelectedRanges(selectedRanges)
    if (selectedRanges.isEmpty()) return null
    val selectedText = selectedRanges.joinToString(" ") { range ->
        range.info.descriptor.text.substring(range.start, range.end)
    }.trim()
    if (selectedText.isBlank()) return null
    val first = selectedRanges.first()
    val last = selectedRanges.last()
    val startAbsoluteOffset = first.info.descriptor.blockCharOffset + first.start
    val endAbsoluteOffset = last.info.descriptor.blockCharOffset + last.end
    return SharedNativeReaderTextSelection(
        chapterIndex = first.info.descriptor.chapterIndex,
        pageIndex = first.info.descriptor.pageIndex,
        startOffset = startAbsoluteOffset,
        endOffset = endAbsoluteOffset,
        text = selectedText,
        startPageIndex = first.info.descriptor.pageIndex,
        endPageIndex = last.info.descriptor.pageIndex,
        startBlockIndex = first.info.descriptor.blockIndex,
        endBlockIndex = last.info.descriptor.blockIndex,
        startBlockCharOffset = first.info.descriptor.blockCharOffset,
        endBlockCharOffset = last.info.descriptor.blockCharOffset,
        startLocalOffset = first.start,
        endLocalOffset = last.end,
        startBaseCfi = first.info.descriptor.baseCfi,
        endBaseCfi = last.info.descriptor.baseCfi,
        rect = sharedNativeSelectionRect(selectedRanges),
        textPerBlock = selectedRanges.associate { range ->
            range.info.descriptor.key.stableKey to range.info.descriptor.text.substring(range.start, range.end)
        }
    )
}

internal fun sharedNativeSelectionRangeInBlock(
    start: SharedNativeTextPosition,
    end: SharedNativeTextPosition,
    block: SharedNativeTextBlockDescriptor,
    textLength: Int
): SharedNativeReaderTextRange? {
    if (sharedNativeCompareBlockToPosition(block, start) < 0) return null
    if (sharedNativeCompareBlockToPosition(block, end) > 0) return null
    val isStart = block.key == start.descriptor.key
    val isEnd = block.key == end.descriptor.key
    val localStart = if (isStart) start.localOffset else 0
    val localEnd = if (isEnd) end.localOffset else textLength
    val safeStart = localStart.coerceIn(0, textLength)
    val safeEnd = localEnd.coerceIn(safeStart, textLength)
    return if (safeStart < safeEnd) SharedNativeReaderTextRange(safeStart, safeEnd) else null
}

internal fun sharedNativeSelectionRangeInBlock(
    selection: SharedNativeReaderTextSelection,
    pageIndex: Int,
    blockIndex: Int,
    blockCharOffset: Int,
    textLength: Int
): SharedNativeReaderTextRange? {
    if (selection.startBlockIndex < 0 || selection.endBlockIndex < 0) return null
    val blockPosition = SharedNativeSelectionBlockKey(pageIndex, blockIndex, blockCharOffset)
    val startPosition = SharedNativeSelectionBlockKey(
        selection.startPageIndex,
        selection.startBlockIndex,
        selection.startBlockCharOffset
    )
    val endPosition = SharedNativeSelectionBlockKey(
        selection.endPageIndex,
        selection.endBlockIndex,
        selection.endBlockCharOffset
    )
    if (sharedNativeCompareBlockKeys(blockPosition, startPosition) < 0) return null
    if (sharedNativeCompareBlockKeys(blockPosition, endPosition) > 0) return null
    val isStart = blockPosition == startPosition
    val isEnd = blockPosition == endPosition
    val localStart = if (isStart) selection.startLocalOffset else 0
    val localEnd = if (isEnd) selection.endLocalOffset else textLength
    val safeStart = localStart.coerceIn(0, textLength)
    val safeEnd = localEnd.coerceIn(safeStart, textLength)
    return if (safeStart < safeEnd) SharedNativeReaderTextRange(safeStart, safeEnd) else null
}

internal fun sharedNativeCompareTextPositions(
    first: SharedNativeTextPosition,
    second: SharedNativeTextPosition
): Int {
    val blockCompare = sharedNativeCompareBlockKeys(first.descriptor.key, second.descriptor.key)
    return if (blockCompare != 0) blockCompare else first.localOffset.compareTo(second.localOffset)
}

internal fun sharedNativeCompareBlockToPosition(
    block: SharedNativeTextBlockDescriptor,
    position: SharedNativeTextPosition
): Int = sharedNativeCompareBlockKeys(block.key, position.descriptor.key)

internal fun sharedNativeCompareBlockKeys(
    first: SharedNativeSelectionBlockKey,
    second: SharedNativeSelectionBlockKey
): Int {
    if (first.pageIndex != second.pageIndex) return first.pageIndex.compareTo(second.pageIndex)
    if (first.blockIndex != second.blockIndex) return first.blockIndex.compareTo(second.blockIndex)
    return first.blockCharOffset.compareTo(second.blockCharOffset)
}

internal fun sharedNativeTrimSelectedRanges(ranges: MutableList<SharedNativeSelectedTextRange>) {
    while (ranges.isNotEmpty()) {
        val first = ranges.first()
        val text = first.info.descriptor.text
        var start = first.start
        while (start < first.end && text[start].isWhitespace()) start++
        if (start < first.end) {
            if (start != first.start) ranges[0] = first.copy(start = start)
            break
        }
        ranges.removeAt(0)
    }
    while (ranges.isNotEmpty()) {
        val lastIndex = ranges.lastIndex
        val last = ranges[lastIndex]
        val text = last.info.descriptor.text
        var end = last.end
        while (end > last.start && text[end - 1].isWhitespace()) end--
        if (end > last.start) {
            if (end != last.end) ranges[lastIndex] = last.copy(end = end)
            break
        }
        ranges.removeAt(lastIndex)
    }
}

internal fun sharedNativeSelectionRect(ranges: List<SharedNativeSelectedTextRange>): Rect {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    ranges.forEach { range ->
        val coordinates = range.info.coordinates
        val windowRect = runCatching {
            val localRect = range.info.layout.getPathForRange(range.start, range.end).getBounds()
            Rect(
                coordinates.localToWindow(localRect.topLeft),
                coordinates.localToWindow(localRect.bottomRight)
            )
        }.getOrElse {
            coordinates.boundsInWindow()
        }
        left = minOf(left, windowRect.left, windowRect.right)
        top = minOf(top, windowRect.top, windowRect.bottom)
        right = maxOf(right, windowRect.left, windowRect.right)
        bottom = maxOf(bottom, windowRect.top, windowRect.bottom)
    }
    return if (left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
        Rect(left, top, right, bottom)
    } else {
        Rect.Zero
    }
}

internal data class SharedNativeReaderTextRange(
    val start: Int,
    val end: Int
)

internal fun sharedNativeReaderTrimmedWordRange(
    text: String,
    start: Int,
    end: Int
): SharedNativeReaderTextRange? {
    var normalizedStart = start.coerceIn(0, text.length)
    var normalizedEnd = end.coerceIn(normalizedStart, text.length)
    while (normalizedStart < normalizedEnd && !text[normalizedStart].isLetterOrDigit()) {
        normalizedStart++
    }
    while (normalizedEnd > normalizedStart && !text[normalizedEnd - 1].isLetterOrDigit()) {
        normalizedEnd--
    }
    return if (normalizedStart < normalizedEnd) {
        SharedNativeReaderTextRange(normalizedStart, normalizedEnd)
    } else {
        null
    }
}

internal data class SharedNativeCfiPoint(
    val path: String,
    val offset: Int
)

internal fun sharedNativeHighlightRangeInBlock(
    highlight: UserHighlight,
    blockCfi: String?,
    textStartOffset: Int,
    textLength: Int,
    text: String?
): SharedNativeReaderTextRange? {
    val cfi = highlight.sharedNativeSourceCfi().takeIf { it.contains('|') || it.startsWith("/") } ?: return null
    val blockPath = blockCfi?.takeIf { it.startsWith("/") } ?: return null
    val parts = cfi.split('|')
    val start = parts.firstOrNull()?.sharedNativeCfiPointOrNull() ?: return null
    val end = parts.lastOrNull()?.sharedNativeCfiPointOrNull() ?: start
    val startMatches = sharedNativeCfiPathsEquivalent(start.path, blockPath)
    val endMatches = sharedNativeCfiPathsEquivalent(end.path, blockPath)
    val isIntermediate = !startMatches && !endMatches &&
        parts.size > 1 &&
        sharedNativeCfiPathStrictlyBetween(blockPath, start.path, end.path)
    if (!startMatches && !endMatches && !isIntermediate) return null

    var localStart = if (startMatches) {
        sharedNativeCfiOffsetToLocal(start.offset, textStartOffset, textLength)
    } else {
        0
    }
    var localEnd = if (endMatches) {
        sharedNativeCfiOffsetToLocal(end.offset, textStartOffset, textLength)
    } else {
        textLength
    }
    if (startMatches && endMatches && localEnd < localStart) {
        localStart = localEnd.also { localEnd = localStart }
    }
    localStart = localStart.coerceIn(0, textLength)
    localEnd = localEnd.coerceIn(localStart, textLength)
    val cfiRange = if (localStart < localEnd) {
        SharedNativeReaderTextRange(localStart, localEnd)
    } else {
        null
    }
    val quoteRange = text
        ?.let { blockText ->
            sharedNativeHighlightTextRangeInBlock(
                blockText = blockText,
                highlightText = highlight.text,
                preferredStart = cfiRange?.start ?: localStart
            )
        }
    if (
        cfiRange != null &&
        quoteRange != null &&
        text != null &&
        !cfiRange.matchesSharedNativeHighlightText(text, highlight.text)
    ) {
        return quoteRange
    }
    return cfiRange ?: quoteRange
}

internal fun sharedNativeCfiOffsetToLocal(offset: Int, textStartOffset: Int, textLength: Int): Int {
    return when {
        offset in 0..textLength -> offset
        offset in textStartOffset..(textStartOffset + textLength) -> offset - textStartOffset
        else -> offset
    }
}

internal fun String.sharedNativeCfiPointOrNull(allowMissingOffset: Boolean = false): SharedNativeCfiPoint? {
    val separator = lastIndexOf(':')
    if (separator <= 0 || separator == lastIndex) {
        if (!allowMissingOffset) return null
        return SharedNativeCfiPoint(takeIf { it.startsWith("/") } ?: return null, 0)
    }
    val path = substring(0, separator).takeIf { it.startsWith("/") } ?: return null
    val offset = substring(separator + 1).toIntOrNull() ?: return null
    return SharedNativeCfiPoint(path, offset)
}

internal fun sharedNativeCfiPathsEquivalent(first: String, second: String): Boolean {
    if (first == second || first.startsWith("$second/") || second.startsWith("$first/")) return true
    val firstParts = first.split('/').filter { it.isNotEmpty() }
    val secondParts = second.split('/').filter { it.isNotEmpty() }
    if (firstParts == secondParts) return true
    return firstParts.size == secondParts.size &&
        firstParts.isNotEmpty() &&
        firstParts.drop(1) == secondParts.drop(1)
}

internal fun sharedNativeCfiPathStrictlyBetween(candidate: String, start: String, end: String): Boolean {
    val candidateParts = candidate.sharedNativeCfiNumericPathParts() ?: return false
    val startParts = start.sharedNativeCfiNumericPathParts() ?: return false
    val endParts = end.sharedNativeCfiNumericPathParts() ?: return false
    return sharedNativeCompareCfiPathParts(candidateParts, startParts) > 0 &&
        sharedNativeCompareCfiPathParts(candidateParts, endParts) < 0
}

internal fun String.sharedNativeCfiNumericPathParts(): List<Int>? {
    val parts = split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

internal fun sharedNativeCompareCfiPathParts(first: List<Int>, second: List<Int>): Int {
    val length = minOf(first.size, second.size)
    for (index in 0 until length) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
}

internal fun sharedNativeReaderHighlightForSelection(
    selection: SharedNativeReaderTextSelection,
    color: HighlightColor,
    style: HighlightStyle = HighlightStyle.BACKGROUND
): UserHighlight {
    val locator = selection.toReaderLocator()
    return UserHighlight(
        id = "native-${selection.chapterIndex}-${selection.startPageIndex}-${selection.startBlockIndex}-${selection.startLocalOffset}-${selection.endPageIndex}-${selection.endBlockIndex}-${selection.endLocalOffset}-${color.id}",
        cfi = selection.cfi,
        text = selection.text,
        color = color,
        chapterIndex = selection.chapterIndex,
        style = style,
        locator = locator
    )
}

internal fun SharedNativeReaderTextSelection.toReaderLocator(): ReaderLocator {
    val blockIndex = startBlockIndex.takeIf { it >= 0 }
    return ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        blockIndex = blockIndex,
        charOffset = blockIndex?.let { startOffset },
        textQuote = text,
        cfi = cfi
    )
}

internal fun headerScale(level: Int): Float {
    return when (level) {
        1 -> 1.5f
        2 -> 1.35f
        3 -> 1.2f
        4 -> 1.1f
        else -> 1f
    }
}

internal fun sharedNativeListMarker(index: Int, isOrdered: Boolean, listStyleType: String?): String {
    return com.aryan.reader.paginatedreader.readerListMarker(listStyleType, index + 1, isOrdered)
        ?.trimEnd()
        .orEmpty()
}

internal fun Dp.safeDp(): Dp = if (isSpecified && this > 0.dp) this else 0.dp

internal fun Dp.isPositiveSpecified(): Boolean = isSpecified && this > 0.dp

internal fun Dp.takeIfPositiveSpecified(): Dp? = takeIf { it.isPositiveSpecified() }

@Composable
internal fun SemanticBlock.collapsedTopMarginDp(
    previous: SemanticBlock?,
    settings: ReaderSettings
): Dp {
    val top = style.blockStyle.margin.top.safeDp()
    return previous?.let { maxOf(it.effectiveBottomMarginDp(settings), top) } ?: top
}

@Composable
internal fun SemanticBlock.effectiveBottomMarginDp(settings: ReaderSettings): Dp {
    val raw = style.blockStyle.margin.bottom
    if (raw.isSpecified && raw < 0.dp) return 0.dp
    val explicit = raw.safeDp()
    if (explicit != 0.dp) return explicit
    return renderedDefaultBottomSpacingDp(settings)
}

@Composable
internal fun SemanticBlock.renderedDefaultBottomSpacingDp(settings: ReaderSettings): Dp {
    return when (this) {
        is SemanticParagraph,
        is SemanticHeader,
        is SemanticList,
        is SemanticTable,
        is SemanticImage -> settings.renderedDefaultBlockSpacingDp()
        is SemanticMath -> if (svgContent == null) settings.renderedDefaultBlockSpacingDp() else 0.dp
        else -> 0.dp
    }
}

@Composable
internal fun ReaderSettings.renderedDefaultBlockSpacingDp(): Dp {
    val density = LocalDensity.current
    return with(density) { (fontSize * paragraphSpacing).sp.toDp() }
}

internal fun SharedReaderTextAlign.toComposeTextAlign(): TextAlign {
    return when (this) {
        SharedReaderTextAlign.START -> TextAlign.Start
        SharedReaderTextAlign.LEFT -> TextAlign.Left
        SharedReaderTextAlign.RIGHT -> TextAlign.Right
        SharedReaderTextAlign.JUSTIFY -> TextAlign.Justify
        SharedReaderTextAlign.CENTER -> TextAlign.Center
    }
}

internal const val ReaderNativeAnnotationUrl = "URL"
internal const val ReaderNativeAnnotationHighlight = "HIGHLIGHT"
internal const val DesktopHighlightMapLogTag = "EpistemeDesktopHighlightMap"
internal const val EpubPageFitLogTag = "EpistemeEpubPageFit"
internal const val EpubCutoffLogTag = SharedEpubCutoffDiagnosticsTag
internal const val EpubPageFitTailBlockCount = 4
internal const val SharedNativeListItemMarkerAreaWidthDp = 32
internal const val SharedNativeListItemMarkerEndPaddingDp = 8
internal val SharedNativeCssUrlRegex = Regex("""url\(\s*(['"]?)(.*?)\1\s*\)""", RegexOption.IGNORE_CASE)
