package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlin.math.roundToInt

enum class SharedNativeReaderSelectionAction {
    DEFINE,
    SEARCH,
    SPEAK
}

data class SharedNativeReaderLinkClick(
    val href: String,
    val chapterIndex: Int?,
    val text: String?
)

internal data class SharedNativeReaderTextSelection(
    val chapterIndex: Int,
    val pageIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String
) {
    val cfi: String get() = "desktop:$chapterIndex:$startOffset:$endOffset"
}

@Composable
fun SharedNativePaginatedReader(
    renderPlan: ReaderContentRenderPlan.NativePaginatedPages,
    readerFontFamily: FontFamily,
    searchHighlight: Color,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    modifier: Modifier = Modifier,
    enabledSelectionActions: Set<SharedNativeReaderSelectionAction> = emptySet(),
    onCopyText: (String) -> Unit = {},
    onSelectionAction: (SharedNativeReaderSelectionAction, String) -> Unit = { _, _ -> },
    onHighlightCreated: (UserHighlight) -> Unit = {},
    onHighlightSelected: (String) -> Unit = {},
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit = {},
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)? = null
) {
    val visiblePages = renderPlan.visiblePages
    val firstPage = visiblePages.firstOrNull()
    var activeSelection by remember(renderPlan.navigationTarget.requestId) {
        mutableStateOf<SharedNativeReaderTextSelection?>(null)
    }
    val visiblePageIndices = remember(visiblePages) { visiblePages.map { it.pageIndex } }
    LaunchedEffect(visiblePageIndices) {
        val selection = activeSelection
        if (selection != null && selection.pageIndex !in visiblePageIndices) {
            activeSelection = null
        }
    }
    LaunchedEffect(firstPage?.pageIndex, renderPlan.navigationTarget.requestId) {
        firstPage?.let { page ->
            onVisiblePageChanged(
                page.pageIndex,
                renderPlan.navigationTarget.locator ?: page.toNativeReaderLocator()
            )
        }
    }

    if (visiblePages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No page content", color = renderPlan.foreground.copy(alpha = 0.68f))
        }
        return
    }

    val selectionHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Box(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(renderPlan.background),
            contentAlignment = Alignment.Center
        ) {
            val pageGap = 28.dp
            val horizontalMargin = renderPlan.settings.resolvedHorizontalMargin.dp
            val configuredContentWidth = renderPlan.settings.pageWidth.dp
            val pageOuterWidth = if (visiblePages.size > 1) {
                val availablePageOuterWidth = ((maxWidth - pageGap).coerceAtLeast(1.dp)) / 2f
                val availableContentWidth = (availablePageOuterWidth - (horizontalMargin * 2f)).coerceAtLeast(1.dp)
                minOf(availableContentWidth, configuredContentWidth) + (horizontalMargin * 2f)
            } else {
                val availableContentWidth = (maxWidth - (horizontalMargin * 2f)).coerceAtLeast(1.dp)
                minOf(availableContentWidth, configuredContentWidth) + (horizontalMargin * 2f)
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(pageGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visiblePages.forEach { page ->
                    SharedNativePaginatedPage(
                        page = page,
                        renderPlan = renderPlan,
                        readerFontFamily = readerFontFamily,
                        searchHighlight = searchHighlight,
                        selectionHighlight = selectionHighlight,
                        activeSelection = activeSelection,
                        onSelectionChange = { activeSelection = it },
                        onHighlightSelected = onHighlightSelected,
                        onLinkClicked = onLinkClicked,
                        imageContent = imageContent,
                        modifier = Modifier
                            .width(pageOuterWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }
        activeSelection?.let { selection ->
            SharedNativeSelectionMenu(
                selection = selection,
                highlightPalette = renderPlan.highlightPalette.sanitized().colors,
                enabledSelectionActions = enabledSelectionActions,
                onCopy = {
                    onCopyText(selection.text)
                    activeSelection = null
                },
                onSelectionAction = { action ->
                    onSelectionAction(action, selection.text)
                    activeSelection = null
                },
                onHighlight = { color ->
                    onHighlightCreated(sharedNativeReaderHighlightForSelection(selection, color))
                    activeSelection = null
                },
                onDismiss = { activeSelection = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun SharedNativePaginatedPage(
    page: ReaderPage,
    renderPlan: ReaderContentRenderPlan.NativePaginatedPages,
    readerFontFamily: FontFamily,
    searchHighlight: Color,
    selectionHighlight: Color,
    activeSelection: SharedNativeReaderTextSelection?,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val settings = renderPlan.settings
    val fallbackTextAlign = settings.textAlign.toComposeTextAlign()
    val visibleHighlights = renderPlan.highlights.visibleInPage(page)
    val blocks = page.semanticBlocks
    var contentFit by remember(page.pageIndex, blocks) { mutableStateOf<SharedNativeContentFit?>(null) }
    val blockLayouts = remember(page.pageIndex, blocks) { mutableStateMapOf<Int, SharedNativeBlockFit>() }
    var layoutVersion by remember(page.pageIndex, blocks) { mutableStateOf(0) }
    var lastPageFitLogSignature by remember(page.pageIndex, blocks) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        contentFit,
        layoutVersion,
        blocks.size,
        page.pageIndex,
        page.chapterIndex,
        settings.fontSize,
        settings.lineSpacing,
        settings.paragraphSpacing
    ) {
        val content = contentFit ?: return@LaunchedEffect
        if (blocks.isEmpty() || blockLayouts.size < blocks.size) return@LaunchedEffect
        val contentTopPx = content.rootTopPx
        val contentHeightPx = content.heightPx
        val orderedFits = blocks.indices.mapNotNull { index -> blockLayouts[index] }
        if (orderedFits.size < blocks.size) return@LaunchedEffect

        val usedPx = orderedFits.maxOfOrNull { fit ->
            fit.relativeBottomPx(contentTopPx)
        } ?: return@LaunchedEffect
        val remainingPx = contentHeightPx - usedPx
        if (remainingPx >= 0) return@LaunchedEffect

        val signature = buildString {
            append(page.pageIndex)
            append(':')
            append(contentHeightPx)
            append(':')
            append(usedPx)
            orderedFits.forEach { fit ->
                append(':')
                append(fit.index)
                append(',')
                append(fit.relativeTopPx(contentTopPx))
                append(',')
                append(fit.heightPx)
            }
        }
        if (signature != lastPageFitLogSignature) {
            lastPageFitLogSignature = signature
            logSharedReaderDiagnostic(EpubPageFitLogTag) {
                "page_fit layer=rendered_overflow page=${page.pageIndex + 1} chapter=${page.chapterIndex} " +
                    "usedPx=$usedPx contentPx=$contentHeightPx remainingPx=$remainingPx " +
                    "overflowPx=${(-remainingPx).coerceAtLeast(0)} blocks=${blocks.size} " +
                    "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length} " +
                    "tail=\"${orderedFits.renderedPageFitTail(contentTopPx)}\""
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = renderPlan.background,
        contentColor = renderPlan.foreground,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, renderPlan.foreground.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = settings.resolvedHorizontalMargin.dp,
                    vertical = settings.resolvedVerticalMargin.dp
                )
                .onGloballyPositioned { coordinates ->
                    val nextFit = SharedNativeContentFit(
                        rootTopPx = coordinates.positionInRoot().y.roundToInt(),
                        heightPx = coordinates.size.height
                    )
                    if (contentFit != nextFit) {
                        contentFit = nextFit
                    }
                },
            verticalArrangement = Arrangement.Top
        ) {
            if (blocks.isEmpty()) {
                SharedNativeInteractiveText(
                    text = page.text.toReaderAnnotatedString(
                        searchQuery = renderPlan.searchQuery,
                        searchHighlight = searchHighlight,
                        absoluteStartOffset = page.startOffset,
                        highlights = visibleHighlights,
                        activeSelection = activeSelection,
                        selectionHighlight = selectionHighlight
                    ),
                    page = page,
                    textStartOffset = page.startOffset,
                    color = renderPlan.foreground,
                    textAlign = fallbackTextAlign,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = settings.fontSize.sp,
                        lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                        fontFamily = readerFontFamily
                    ).withAndroidPaginationTextMetrics(),
                    onSelectionChange = onSelectionChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    fitLabel = SharedNativeTextFitLabel(
                        page = page,
                        blockIndex = -1,
                        kind = "plain",
                        sourceRange = "${page.startOffset}..${page.endOffset}",
                        textChars = page.text.length
                    )
                )
            } else {
                SharedSemanticBlockStack(
                    blocks = blocks,
                    page = page,
                    foreground = renderPlan.foreground,
                    searchQuery = renderPlan.searchQuery,
                    searchHighlight = searchHighlight,
                    highlights = visibleHighlights,
                    activeSelection = activeSelection,
                    selectionHighlight = selectionHighlight,
                    fallbackTextAlign = fallbackTextAlign,
                    fallbackFontFamily = readerFontFamily,
                    settings = settings,
                    includeTrailingBottomMargin = false,
                    onSelectionChange = onSelectionChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    imageContent = imageContent,
                    onBlockLaidOut = { fit ->
                        if (blockLayouts[fit.index] != fit) {
                            blockLayouts[fit.index] = fit
                            layoutVersion += 1
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedNativeSelectionMenu(
    selection: SharedNativeReaderTextSelection,
    highlightPalette: List<HighlightColor>,
    enabledSelectionActions: Set<SharedNativeReaderSelectionAction>,
    onCopy: () -> Unit,
    onSelectionAction: (SharedNativeReaderSelectionAction) -> Unit,
    onHighlight: (HighlightColor) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCopy) { Text("Copy") }
            if (SharedNativeReaderSelectionAction.DEFINE in enabledSelectionActions) {
                TextButton(onClick = { onSelectionAction(SharedNativeReaderSelectionAction.DEFINE) }) { Text("Define") }
            }
            if (SharedNativeReaderSelectionAction.SEARCH in enabledSelectionActions) {
                TextButton(onClick = { onSelectionAction(SharedNativeReaderSelectionAction.SEARCH) }) { Text("Search") }
            }
            if (SharedNativeReaderSelectionAction.SPEAK in enabledSelectionActions) {
                TextButton(onClick = { onSelectionAction(SharedNativeReaderSelectionAction.SPEAK) }) { Text("Speak") }
            }
            highlightPalette.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color.color, RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable { onHighlight(color) }
                )
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun SharedNativeInteractiveText(
    text: AnnotatedString,
    page: ReaderPage,
    textStartOffset: Int,
    color: Color,
    textAlign: TextAlign,
    style: TextStyle,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    modifier: Modifier = Modifier,
    fitLabel: SharedNativeTextFitLabel? = null
) {
    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(text) { mutableStateOf<LayoutCoordinates?>(null) }
    var lastTextClipLogSignature by remember(text) { mutableStateOf<String?>(null) }
    var dragAnchorOffset by remember(text) { mutableStateOf<Int?>(null) }
    LaunchedEffect(textLayoutResult, textCoordinates, fitLabel) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val coordinates = textCoordinates ?: return@LaunchedEffect
        val label = fitLabel ?: return@LaunchedEffect
        val boxHeightPx = coordinates.size.height
        val layoutHeightPx = layout.size.height
        val lastLineBottomPx = if (layout.lineCount > 0) {
            layout.getLineBottom(layout.lineCount - 1).roundToInt()
        } else {
            layoutHeightPx
        }
        val clipPx = maxOf(layoutHeightPx, lastLineBottomPx) - boxHeightPx
        if (clipPx <= 1) return@LaunchedEffect
        val signature = "${label.page.pageIndex}:${label.blockIndex}:$boxHeightPx:$layoutHeightPx:$lastLineBottomPx"
        if (signature == lastTextClipLogSignature) return@LaunchedEffect
        lastTextClipLogSignature = signature
        logSharedReaderDiagnostic(EpubPageFitLogTag) {
            "page_fit layer=text_clip page=${label.page.pageIndex + 1} chapter=${label.page.chapterIndex} " +
                "block=${label.blockIndex} kind=${label.kind} boxPx=$boxHeightPx layoutPx=$layoutHeightPx " +
                "lastLineBottomPx=$lastLineBottomPx clipPx=$clipPx lines=${layout.lineCount} " +
                "range=${label.sourceRange} textChars=${label.textChars}"
        }
    }
    Text(
        text = text,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { textCoordinates = it }
            .pointerInput(text) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)
                            .coerceIn(0, text.text.length)
                        val boundary = layout.getWordBoundary(charOffset)
                        val range = sharedNativeReaderTrimmedWordRange(
                            text = text.text,
                            start = boundary.start,
                            end = boundary.end
                        ) ?: return@detectTapGestures
                        val selectedText = text.text.substring(range.start, range.end)
                        onSelectionChange(
                            SharedNativeReaderTextSelection(
                                chapterIndex = page.chapterIndex,
                                pageIndex = page.pageIndex,
                                startOffset = textStartOffset + range.start,
                                endOffset = textStartOffset + range.end,
                                text = selectedText
                            )
                        )
                    },
                    onTap = { offset ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)
                            .coerceIn(0, text.text.length)
                        text.stringAnnotationAt(ReaderNativeAnnotationUrl, charOffset)?.let { href ->
                            onSelectionChange(null)
                            onLinkClicked(
                                SharedNativeReaderLinkClick(
                                    href = href,
                                    chapterIndex = page.chapterIndex,
                                    text = text.text
                                )
                            )
                            return@detectTapGestures
                        }
                        text.stringAnnotationAt(ReaderNativeAnnotationHighlight, charOffset)?.let { highlightId ->
                            onSelectionChange(null)
                            onHighlightSelected(highlightId)
                            return@detectTapGestures
                        }
                        onSelectionChange(null)
                    }
                )
            }
            .pointerInput(text) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val layout = textLayoutResult
                        if (layout != null) {
                            val charOffset = layout.getOffsetForPosition(offset)
                                .coerceIn(0, text.text.length)
                            val boundary = layout.getWordBoundary(charOffset)
                            val range = sharedNativeReaderTrimmedWordRange(
                                text = text.text,
                                start = boundary.start,
                                end = boundary.end
                            )
                            if (range != null) {
                                dragAnchorOffset = range.start
                                val selectedText = text.text.substring(range.start, range.end)
                                onSelectionChange(
                                    SharedNativeReaderTextSelection(
                                        chapterIndex = page.chapterIndex,
                                        pageIndex = page.pageIndex,
                                        startOffset = textStartOffset + range.start,
                                        endOffset = textStartOffset + range.end,
                                        text = selectedText
                                    )
                                )
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val layout = textLayoutResult
                        val anchor = dragAnchorOffset
                        if (layout != null && anchor != null) {
                            val current = layout.getOffsetForPosition(change.position)
                                .coerceIn(0, text.text.length)
                            val start = minOf(anchor, current)
                            val end = maxOf(anchor, current)
                            if (start < end) {
                                val selectedText = text.text.substring(start, end)
                                onSelectionChange(
                                    SharedNativeReaderTextSelection(
                                        chapterIndex = page.chapterIndex,
                                        pageIndex = page.pageIndex,
                                        startOffset = textStartOffset + start,
                                        endOffset = textStartOffset + end,
                                        text = selectedText
                                    )
                                )
                            }
                        }
                        change.consume()
                    },
                    onDragEnd = { dragAnchorOffset = null },
                    onDragCancel = { dragAnchorOffset = null }
                )
            },
        textAlign = textAlign,
        style = style,
        onTextLayout = { textLayoutResult = it }
    )
}

private fun ReaderPage.toNativeReaderLocator(): ReaderLocator {
    return ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        textQuote = text.replace(Regex("\\s+"), " ").trim().take(160),
        cfi = "desktop:$chapterIndex:$startOffset:$endOffset"
    )
}

private fun String.toReaderAnnotatedString(
    searchQuery: String,
    searchHighlight: Color,
    absoluteStartOffset: Int,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color
): AnnotatedString {
    val normalized = searchQuery.trim()
    return buildAnnotatedString {
        append(this@toReaderAnnotatedString)
        highlights.forEach { highlight ->
            applyHighlightToTextRange(
                highlight = highlight,
                textStartOffset = absoluteStartOffset,
                textLength = this@toReaderAnnotatedString.length
            )
        }
        applySelectionToTextRange(
            selection = activeSelection,
            textStartOffset = absoluteStartOffset,
            textLength = this@toReaderAnnotatedString.length,
            color = selectionHighlight
        )
        if (normalized.length >= 2) {
            var startIndex = 0
            while (startIndex < this@toReaderAnnotatedString.length) {
                val index = this@toReaderAnnotatedString.indexOf(normalized, startIndex, ignoreCase = true)
                if (index < 0) break
                addStyle(
                    style = SpanStyle(background = searchHighlight),
                    start = index,
                    end = index + normalized.length
                )
                startIndex = index + normalized.length
            }
        }
    }
}

@Composable
private fun SharedSemanticBlockStack(
    blocks: List<SemanticBlock>,
    page: ReaderPage,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    includeTrailingBottomMargin: Boolean,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    onBlockLaidOut: ((SharedNativeBlockFit) -> Unit)? = null
) {
    var previous: SemanticBlock? = null
    blocks.forEachIndexed { index, block ->
        SharedSemanticBlockView(
            block = block,
            page = page,
            foreground = foreground,
            searchQuery = searchQuery,
            searchHighlight = searchHighlight,
            highlights = highlights,
            activeSelection = activeSelection,
            selectionHighlight = selectionHighlight,
            fallbackTextAlign = fallbackTextAlign,
            fallbackFontFamily = fallbackFontFamily,
            settings = settings,
            marginTop = block.collapsedTopMarginDp(previous, settings),
            marginBottom = if (includeTrailingBottomMargin && index == blocks.lastIndex) {
                block.effectiveBottomMarginDp(settings)
            } else {
                0.dp
            },
            onSelectionChange = onSelectionChange,
            onHighlightSelected = onHighlightSelected,
            onLinkClicked = onLinkClicked,
            imageContent = imageContent,
            layoutIndex = index,
            onBlockLaidOut = onBlockLaidOut
        )
        previous = block
    }
}

@Composable
private fun SharedSemanticBlockView(
    block: SemanticBlock,
    page: ReaderPage,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    marginTop: Dp,
    marginBottom: Dp,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    layoutIndex: Int? = null,
    onBlockLaidOut: ((SharedNativeBlockFit) -> Unit)? = null
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(
            start = block.style.blockStyle.margin.left.safeDp(),
            top = marginTop,
            end = block.style.blockStyle.margin.right.safeDp(),
            bottom = marginBottom
        )
        .then(
            if (block.style.blockStyle.backgroundColor.isSpecified) {
                Modifier.background(block.style.blockStyle.backgroundColor, RoundedCornerShape(4.dp))
            } else {
                Modifier
            }
        )
        .padding(
            start = block.style.blockStyle.padding.left.safeDp(),
            top = block.style.blockStyle.padding.top.safeDp(),
            end = block.style.blockStyle.padding.right.safeDp(),
            bottom = block.style.blockStyle.padding.bottom.safeDp()
        )
    val measuredModifier = if (layoutIndex != null && onBlockLaidOut != null) {
        Modifier
            .onGloballyPositioned { coordinates ->
                onBlockLaidOut(block.toSharedNativeBlockFit(layoutIndex, coordinates))
            }
            .then(modifier)
    } else {
        modifier
    }

    when (block) {
        is SemanticHeader -> {
            SharedSemanticTextView(
                block = block,
                page = page,
                modifier = measuredModifier,
                foreground = foreground,
                searchQuery = searchQuery,
                searchHighlight = searchHighlight,
                highlights = highlights,
                activeSelection = activeSelection,
                selectionHighlight = selectionHighlight,
                fallbackTextAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
                fallbackFontFamily = fallbackFontFamily,
                settings = settings,
                fontWeight = FontWeight.Bold,
                onSelectionChange = onSelectionChange,
                onHighlightSelected = onHighlightSelected,
                onLinkClicked = onLinkClicked
            )
        }

        is SemanticParagraph -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked)
        is SemanticListItem -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked)
        is SemanticTextBlock -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked)

        is SemanticList -> {
            Column(modifier = measuredModifier, verticalArrangement = Arrangement.Top) {
                var previous: SemanticBlock? = null
                block.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.padding(
                            top = item.collapsedTopMarginDp(previous, settings),
                            bottom = if (index == block.items.lastIndex) item.effectiveBottomMarginDp(settings) else 0.dp
                        ),
                        verticalAlignment = Alignment.Top
                    ) {
                        val markerModifier = Modifier
                            .width(SharedNativeListItemMarkerAreaWidthDp.dp)
                            .padding(end = SharedNativeListItemMarkerEndPaddingDp.dp)
                        Text(
                            text = if (block.isOrdered) "${index + 1}." else "\u2022",
                            color = foreground,
                            modifier = markerModifier,
                            textAlign = TextAlign.End,
                            style = item.renderedTextStyle(
                                settings = settings,
                                fallbackFontFamily = fallbackFontFamily,
                                fallbackTextAlign = TextAlign.End
                            )
                        )
                        SharedSemanticTextView(
                            block = item,
                            page = page,
                            modifier = Modifier.weight(1f),
                            foreground = foreground,
                            searchQuery = searchQuery,
                            searchHighlight = searchHighlight,
                            highlights = highlights,
                            activeSelection = activeSelection,
                            selectionHighlight = selectionHighlight,
                            fallbackTextAlign = fallbackTextAlign,
                            fallbackFontFamily = fallbackFontFamily,
                            settings = settings,
                            onSelectionChange = onSelectionChange,
                            onHighlightSelected = onHighlightSelected,
                            onLinkClicked = onLinkClicked
                        )
                    }
                    previous = item
                }
            }
        }

        is SemanticFlexContainer -> {
            Column(modifier = measuredModifier, verticalArrangement = Arrangement.Top) {
                SharedSemanticBlockStack(
                    blocks = block.children,
                    page = page,
                    foreground = foreground,
                    searchQuery = searchQuery,
                    searchHighlight = searchHighlight,
                    highlights = highlights,
                    activeSelection = activeSelection,
                    selectionHighlight = selectionHighlight,
                    fallbackTextAlign = fallbackTextAlign,
                    fallbackFontFamily = fallbackFontFamily,
                    settings = settings,
                    includeTrailingBottomMargin = true,
                    onSelectionChange = onSelectionChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    imageContent = imageContent
                )
            }
        }

        is SemanticWrappingBlock -> {
            Column(modifier = measuredModifier, verticalArrangement = Arrangement.Top) {
                SharedSemanticBlockStack(
                    blocks = listOf(block.floatedImage) + block.paragraphsToWrap,
                    page = page,
                    foreground = foreground,
                    searchQuery = searchQuery,
                    searchHighlight = searchHighlight,
                    highlights = highlights,
                    activeSelection = activeSelection,
                    selectionHighlight = selectionHighlight,
                    fallbackTextAlign = fallbackTextAlign,
                    fallbackFontFamily = fallbackFontFamily,
                    settings = settings,
                    includeTrailingBottomMargin = true,
                    onSelectionChange = onSelectionChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    imageContent = imageContent
                )
            }
        }

        is SemanticTable -> {
            Column(modifier = measuredModifier, verticalArrangement = Arrangement.Top) {
                block.rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cell ->
                            Column(modifier = Modifier.weight(cell.colspan.toFloat().coerceAtLeast(1f))) {
                                SharedSemanticBlockStack(
                                    blocks = cell.content,
                                    page = page,
                                    foreground = foreground,
                                    searchQuery = searchQuery,
                                    searchHighlight = searchHighlight,
                                    highlights = highlights,
                                    activeSelection = activeSelection,
                                    selectionHighlight = selectionHighlight,
                                    fallbackTextAlign = fallbackTextAlign,
                                    fallbackFontFamily = fallbackFontFamily,
                                    settings = settings,
                                    includeTrailingBottomMargin = true,
                                    onSelectionChange = onSelectionChange,
                                    onHighlightSelected = onHighlightSelected,
                                    onLinkClicked = onLinkClicked,
                                    imageContent = imageContent
                                )
                            }
                        }
                    }
                }
            }
        }

        is SemanticImage -> {
            SharedNativeImageBlock(
                block = block,
                foreground = foreground,
                settings = settings,
                imageContent = imageContent,
                modifier = measuredModifier
            )
        }

        is SemanticMath -> {
            Text(
                text = block.altText ?: "Equation",
                color = foreground,
                modifier = measuredModifier,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is SemanticSpacer -> Spacer(measuredModifier.height(if (block.isExplicitLineBreak) 8.dp else 16.dp))
    }
}

@Composable
private fun SharedNativeImageBlock(
    block: SemanticImage,
    foreground: Color,
    settings: ReaderSettings,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = block.imageContentAlignment()
    ) {
        val imageModifier = Modifier.sharedNativeImageSize(block, settings, maxWidth)
        if (imageContent != null) {
            imageContent(block, imageModifier)
        } else {
            Text(
                text = block.altText?.takeIf { it.isNotBlank() } ?: block.path.substringAfterLast('/').substringAfterLast('\\'),
                color = foreground.copy(alpha = 0.7f),
                modifier = imageModifier,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun SemanticImage.imageContentAlignment(): Alignment {
    val style = style.blockStyle
    return when {
        style.float == "right" || style.horizontalAlign == "right" || style.horizontalAlign == "end" -> Alignment.CenterEnd
        style.float == "left" || style.horizontalAlign == "left" || style.horizontalAlign == "start" -> Alignment.CenterStart
        else -> Alignment.Center
    }
}

@Composable
private fun Modifier.sharedNativeImageSize(
    block: SemanticImage,
    settings: ReaderSettings,
    maxWidth: Dp
): Modifier {
    val density = LocalDensity.current
    val style = block.style.blockStyle
    val imageScale = settings.imageScale.coerceIn(0.5f, 2f)
    val scaledSize = sharedNativeImageRenderSizeDp(
        block = block,
        density = density,
        maxWidth = maxWidth,
        imageScale = imageScale
    )

    return this
        .then(
            if (scaledSize != null) {
                Modifier
                    .width(scaledSize.first)
                    .height(scaledSize.second)
            } else if (style.width.isPositiveSpecified()) {
                Modifier.width(style.width)
            } else {
                Modifier.fillMaxWidth()
            }
        )
        .then(
            if (scaledSize == null && style.maxWidth.isPositiveSpecified()) {
                Modifier.widthIn(max = style.maxWidth)
            } else {
                Modifier
            }
        )
        .then(
            if (scaledSize == null) {
                val fallbackHeight = style.height.takeIfPositiveSpecified()
                    ?: with(density) { (settings.fontSize * 8f).sp.toDp() }
                Modifier.height(fallbackHeight)
            } else {
                Modifier
            }
        )
}

private fun sharedNativeImageRenderSizeDp(
    block: SemanticImage,
    density: Density,
    maxWidth: Dp,
    imageScale: Float
): Pair<Dp, Dp>? {
    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f || intrinsicHeight <= 0f) {
        return null
    }

    val style = block.style.blockStyle
    val aspectRatio = intrinsicHeight / intrinsicWidth
    val maxWidthPx = with(density) { maxWidth.toPx() }
    val baseWidthPx = with(density) {
        if (style.width.isPositiveSpecified()) style.width.toPx() else maxWidth.toPx()
    }

    var scaledWidthPx = baseWidthPx * imageScale
    if (style.maxWidth.isPositiveSpecified()) {
        scaledWidthPx = scaledWidthPx.coerceAtMost(with(density) { style.maxWidth.toPx() } * imageScale)
    }
    scaledWidthPx = scaledWidthPx.coerceAtMost(maxWidthPx)

    return with(density) {
        scaledWidthPx.toDp() to (scaledWidthPx * aspectRatio).toDp()
    }
}

private data class SharedNativeContentFit(
    val rootTopPx: Int,
    val heightPx: Int
)

private data class SharedNativeTextFitLabel(
    val page: ReaderPage,
    val blockIndex: Int,
    val kind: String,
    val sourceRange: String,
    val textChars: Int
)

private data class SharedNativeBlockFit(
    val index: Int,
    val kind: String,
    val blockIndex: Int,
    val sourceRange: String,
    val rootTopPx: Int,
    val heightPx: Int
) {
    fun relativeTopPx(contentTopPx: Int): Int = rootTopPx - contentTopPx

    fun relativeBottomPx(contentTopPx: Int): Int = relativeTopPx(contentTopPx) + heightPx

    fun format(contentTopPx: Int): String {
        val topPx = relativeTopPx(contentTopPx)
        val bottomPx = topPx + heightPx
        return "#$index:$kind(block=$blockIndex,top=$topPx,height=$heightPx,bottom=$bottomPx,range=$sourceRange)"
    }
}

private fun SemanticBlock.toSharedNativeBlockFit(
    index: Int,
    coordinates: LayoutCoordinates
): SharedNativeBlockFit {
    return SharedNativeBlockFit(
        index = index,
        kind = sharedNativeKindName(),
        blockIndex = blockIndex,
        sourceRange = sharedNativeSourceRangeLabel(),
        rootTopPx = coordinates.positionInRoot().y.roundToInt(),
        heightPx = coordinates.size.height
    )
}

private fun List<SharedNativeBlockFit>.renderedPageFitTail(contentTopPx: Int): String {
    return takeLast(EpubPageFitTailBlockCount).joinToString("|") { it.format(contentTopPx) }
}

private fun SemanticBlock.sharedNativeKindName(): String {
    return when (this) {
        is SemanticTextBlock -> when (this) {
            is SemanticHeader -> "header"
            is SemanticParagraph -> "paragraph"
            is SemanticListItem -> "list_item"
            else -> "text"
        }
        is SemanticList -> "list"
        is SemanticTable -> "table"
        is SemanticFlexContainer -> "flex"
        is SemanticWrappingBlock -> "wrapping"
        is SemanticImage -> "image"
        is SemanticMath -> "math"
        is SemanticSpacer -> "spacer"
    }
}

private fun SemanticBlock.sharedNativeSourceRangeLabel(): String {
    return when (this) {
        is SemanticTextBlock -> {
            val start = startCharOffsetInSource
            "$start..${start + text.length}"
        }
        else -> cfi?.takeIf { it.isNotBlank() }
            ?: elementId?.takeIf { it.isNotBlank() }
            ?: "-"
    }.sharedNativeLogPreview(maxLength = 80)
}

private fun String.sharedNativeLogPreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

@Composable
private fun SharedSemanticTextView(
    block: SemanticTextBlock,
    page: ReaderPage,
    modifier: Modifier,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    fontWeight: FontWeight? = null,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit
) {
    val textStyle = block.renderedTextStyle(
        settings = settings,
        fallbackFontFamily = fallbackFontFamily,
        fallbackTextAlign = fallbackTextAlign,
        fontWeight = fontWeight
    )
    SharedNativeInteractiveText(
        text = block.toAnnotatedString(
            query = searchQuery,
            highlightColor = searchHighlight,
            highlights = highlights,
            activeSelection = activeSelection,
            selectionHighlight = selectionHighlight,
            blockFontSizeSp = textStyle.fontSize.value
        ),
        page = page,
        textStartOffset = block.startCharOffsetInSource,
        color = foreground,
        modifier = modifier,
        textAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
        style = textStyle,
        onSelectionChange = onSelectionChange,
        onHighlightSelected = onHighlightSelected,
        onLinkClicked = onLinkClicked,
        fitLabel = SharedNativeTextFitLabel(
            page = page,
            blockIndex = block.blockIndex,
            kind = block.sharedNativeKindName(),
            sourceRange = block.sharedNativeSourceRangeLabel(),
            textChars = block.text.length
        )
    )
}

@Composable
private fun SemanticTextBlock.renderedTextStyle(
    settings: ReaderSettings,
    fallbackFontFamily: FontFamily,
    fallbackTextAlign: TextAlign,
    fontWeight: FontWeight? = null
): TextStyle {
    val fontSize = (style.fontSize.takeIfSpecified()
        ?: style.spanStyle.fontSize.takeIfSpecified())
        ?.resolveFontSizeSp(settings.fontSize.toFloat())
        ?: when (this) {
            is SemanticHeader -> (settings.fontSize * headerScale(level)).sp
            else -> settings.fontSize.sp
        }
    val lineHeight = style.paragraphStyle.lineHeight.takeIfSpecified()
        ?.resolveLineHeightSp(fontSize.value)
        ?: (fontSize.value * settings.lineSpacing).sp
    return MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = fallbackFontFamily,
        fontWeight = fontWeight ?: if (this is SemanticHeader) FontWeight.Bold else MaterialTheme.typography.bodyLarge.fontWeight,
        textAlign = style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign
    ).withAndroidPaginationTextMetrics()
}

private fun TextStyle.withAndroidPaginationTextMetrics(): TextStyle {
    return copy(
        lineBreak = LineBreak.Paragraph,
        letterSpacing = TextUnit.Unspecified,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None
        )
    )
}

private fun SemanticTextBlock.toAnnotatedString(
    query: String,
    highlightColor: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    blockFontSizeSp: Float
): AnnotatedString {
    val normalized = query.trim()
    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start < end) {
                addStyle(span.style.toRenderedSpanStyle(blockFontSizeSp), start, end)
                span.linkHref?.takeIf { it.isNotBlank() }?.let { href ->
                    addStringAnnotation(ReaderNativeAnnotationUrl, href, start, end)
                }
            }
        }
        highlights.forEach { highlight ->
            applyHighlightToTextRange(
                highlight = highlight,
                textStartOffset = startCharOffsetInSource,
                textLength = text.length
            )
        }
        applySelectionToTextRange(
            selection = activeSelection,
            textStartOffset = startCharOffsetInSource,
            textLength = text.length,
            color = selectionHighlight
        )
        if (normalized.length >= 2) {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = text.indexOf(normalized, startIndex, ignoreCase = true)
                if (index < 0) break
                addStyle(SpanStyle(background = highlightColor), index, index + normalized.length)
                startIndex = index + normalized.length
            }
        }
    }
}

private fun TextUnit.takeIfSpecified(): TextUnit? = if (isSpecified) this else null

private fun TextUnit.resolveFontSizeSp(baseFontSizeSp: Float): TextUnit {
    return when {
        isEm -> (baseFontSizeSp * value).sp
        else -> value.sp
    }
}

private fun TextUnit.resolveLineHeightSp(fontSizeSp: Float): TextUnit {
    return when {
        isEm -> (fontSizeSp * value).sp
        else -> value.sp
    }
}

private fun CssStyle.toRenderedSpanStyle(parentFontSizeSp: Float): SpanStyle {
    val resolvedFontSize = (spanStyle.fontSize.takeIfSpecified() ?: fontSize.takeIfSpecified())
        ?.resolveFontSizeSp(parentFontSizeSp)
    return if (resolvedFontSize == null) {
        spanStyle
    } else {
        spanStyle.copy(fontSize = resolvedFontSize)
    }
}

private fun List<UserHighlight>.visibleInPage(page: ReaderPage): List<UserHighlight> {
    return filter { highlight ->
        val locator = highlight.locator
        val chapterIndex = locator.chapterIndex ?: highlight.chapterIndex
        val start = locator.startOffset
        val end = locator.endOffset
        chapterIndex == page.chapterIndex &&
            start != null &&
            end != null &&
            start < page.endOffset &&
            end > page.startOffset
    }
}

private fun AnnotatedString.Builder.applyHighlightToTextRange(
    highlight: UserHighlight,
    textStartOffset: Int,
    textLength: Int
) {
    val start = highlight.locator.startOffset ?: return
    val end = highlight.locator.endOffset ?: return
    val localStart = (start - textStartOffset).coerceIn(0, textLength)
    val localEnd = (end - textStartOffset).coerceIn(localStart, textLength)
    if (localStart < localEnd) {
        addStyle(
            style = SpanStyle(background = highlight.color.color.copy(alpha = 0.38f)),
            start = localStart,
            end = localEnd
        )
        addStringAnnotation(ReaderNativeAnnotationHighlight, highlight.id, localStart, localEnd)
    }
}

private fun AnnotatedString.Builder.applySelectionToTextRange(
    selection: SharedNativeReaderTextSelection?,
    textStartOffset: Int,
    textLength: Int,
    color: Color
) {
    if (selection == null) return
    val localStart = (selection.startOffset - textStartOffset).coerceIn(0, textLength)
    val localEnd = (selection.endOffset - textStartOffset).coerceIn(localStart, textLength)
    if (localStart < localEnd) {
        addStyle(
            style = SpanStyle(background = color),
            start = localStart,
            end = localEnd
        )
    }
}

private fun AnnotatedString.stringAnnotationAt(tag: String, offset: Int): String? {
    if (isEmpty()) return null
    val start = offset.coerceIn(0, (length - 1).coerceAtLeast(0))
    val end = (start + 1).coerceAtMost(length)
    return getStringAnnotations(tag, start, end).firstOrNull()?.item
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

internal fun sharedNativeReaderHighlightForSelection(
    selection: SharedNativeReaderTextSelection,
    color: HighlightColor
): UserHighlight {
    val locator = ReaderLocator(
        chapterIndex = selection.chapterIndex,
        pageIndex = selection.pageIndex,
        startOffset = selection.startOffset,
        endOffset = selection.endOffset,
        textQuote = selection.text,
        cfi = selection.cfi
    )
    return UserHighlight(
        id = "native-${selection.chapterIndex}-${selection.startOffset}-${selection.endOffset}-${color.id}",
        cfi = selection.cfi,
        text = selection.text,
        color = color,
        chapterIndex = selection.chapterIndex,
        locator = locator
    )
}

private fun headerScale(level: Int): Float {
    return when (level) {
        1 -> 1.5f
        2 -> 1.35f
        3 -> 1.2f
        4 -> 1.1f
        else -> 1f
    }
}

private fun Dp.safeDp(): Dp = if (isSpecified) this else 0.dp

private fun Dp.isPositiveSpecified(): Boolean = isSpecified && this > 0.dp

private fun Dp.takeIfPositiveSpecified(): Dp? = takeIf { it.isPositiveSpecified() }

@Composable
private fun SemanticBlock.collapsedTopMarginDp(
    previous: SemanticBlock?,
    settings: ReaderSettings
): Dp {
    val top = style.blockStyle.margin.top.safeDp()
    return previous?.let { maxOf(it.effectiveBottomMarginDp(settings), top) } ?: top
}

@Composable
private fun SemanticBlock.effectiveBottomMarginDp(settings: ReaderSettings): Dp {
    val explicit = style.blockStyle.margin.bottom.safeDp()
    if (explicit != 0.dp) return explicit
    return renderedDefaultBottomSpacingDp(settings)
}

@Composable
private fun SemanticBlock.renderedDefaultBottomSpacingDp(settings: ReaderSettings): Dp {
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
private fun ReaderSettings.renderedDefaultBlockSpacingDp(): Dp {
    val density = LocalDensity.current
    return with(density) { (fontSize * paragraphSpacing).sp.toDp() }
}

private fun SharedReaderTextAlign.toComposeTextAlign(): TextAlign {
    return when (this) {
        SharedReaderTextAlign.START -> TextAlign.Start
        SharedReaderTextAlign.JUSTIFY -> TextAlign.Justify
        SharedReaderTextAlign.CENTER -> TextAlign.Center
    }
}

private const val ReaderNativeAnnotationUrl = "URL"
private const val ReaderNativeAnnotationHighlight = "HIGHLIGHT"
private const val EpubPageFitLogTag = "EpistemeEpubPageFit"
private const val EpubPageFitTailBlockCount = 4
private const val SharedNativeListItemMarkerAreaWidthDp = 32
private const val SharedNativeListItemMarkerEndPaddingDp = 8
