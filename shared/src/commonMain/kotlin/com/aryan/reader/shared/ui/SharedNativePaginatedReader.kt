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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
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
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit = {}
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
                .background(renderPlan.background)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val pageGap = 16.dp
            val maxConfiguredPageWidth = renderPlan.settings.pageWidth.dp
            val pageWidth = if (visiblePages.size > 1) {
                val availableSpreadWidth = if (maxWidth > pageGap) maxWidth - pageGap else maxWidth
                minOf(availableSpreadWidth / 2f, maxConfiguredPageWidth)
            } else {
                minOf(maxWidth, maxConfiguredPageWidth)
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
                        modifier = Modifier
                            .width(pageWidth)
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
    modifier: Modifier = Modifier
) {
    val settings = renderPlan.settings
    val fallbackTextAlign = settings.textAlign.toComposeTextAlign()
    val visibleHighlights = renderPlan.highlights.visibleInPage(page)
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
                ),
            verticalArrangement = Arrangement.spacedBy((settings.fontSize * settings.paragraphSpacing * 0.45f).dp)
        ) {
            val blocks = page.semanticBlocks
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
                    ),
                    onSelectionChange = onSelectionChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked
                )
            } else {
                blocks.forEach { block ->
                    SharedSemanticBlockView(
                        block = block,
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
                        onSelectionChange = onSelectionChange,
                        onHighlightSelected = onHighlightSelected,
                        onLinkClicked = onLinkClicked
                    )
                }
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
    modifier: Modifier = Modifier
) {
    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var dragAnchorOffset by remember(text) { mutableStateOf<Int?>(null) }
    Text(
        text = text,
        color = color,
        modifier = modifier
            .fillMaxWidth()
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
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(
            start = block.style.blockStyle.margin.left.safeDp(),
            top = block.style.blockStyle.margin.top.safeDp(),
            end = block.style.blockStyle.margin.right.safeDp(),
            bottom = block.style.blockStyle.margin.bottom.safeDp()
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

    when (block) {
        is SemanticHeader -> {
            SharedSemanticTextView(
                block = block,
                page = page,
                modifier = modifier,
                foreground = foreground,
                searchQuery = searchQuery,
                searchHighlight = searchHighlight,
                highlights = highlights,
                activeSelection = activeSelection,
                selectionHighlight = selectionHighlight,
                fallbackTextAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
                fallbackFontFamily = fallbackFontFamily,
                settings = settings.copy(fontSize = (settings.fontSize * headerScale(block.level)).toInt()),
                fontWeight = FontWeight.Bold,
                onSelectionChange = onSelectionChange,
                onHighlightSelected = onHighlightSelected,
                onLinkClicked = onLinkClicked
            )
        }

        is SemanticParagraph -> SharedSemanticTextView(block, page, modifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked)
        is SemanticListItem -> SharedSemanticTextView(block, page, modifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked)
        is SemanticTextBlock -> SharedSemanticTextView(block, page, modifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked)

        is SemanticList -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (block.isOrdered) "${index + 1}." else "\u2022", color = foreground)
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
                }
            }
        }

        is SemanticFlexContainer -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.children.forEach {
                    SharedSemanticBlockView(it, page, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange, onHighlightSelected, onLinkClicked)
                }
            }
        }

        is SemanticWrappingBlock -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SharedSemanticBlockView(block.floatedImage, page, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange, onHighlightSelected, onLinkClicked)
                block.paragraphsToWrap.forEach {
                    SharedSemanticBlockView(it, page, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange, onHighlightSelected, onLinkClicked)
                }
            }
        }

        is SemanticTable -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cell ->
                            Column(modifier = Modifier.weight(cell.colspan.toFloat().coerceAtLeast(1f))) {
                                cell.content.forEach {
                                    SharedSemanticBlockView(it, page, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange, onHighlightSelected, onLinkClicked)
                                }
                            }
                        }
                    }
                }
            }
        }

        is SemanticImage -> {
            Text(
                text = block.altText?.takeIf { it.isNotBlank() } ?: block.path.substringAfterLast('/').substringAfterLast('\\'),
                color = foreground.copy(alpha = 0.7f),
                modifier = modifier,
                style = MaterialTheme.typography.bodySmall
            )
        }

        is SemanticMath -> {
            Text(
                text = block.altText ?: "Equation",
                color = foreground,
                modifier = modifier,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is SemanticSpacer -> Spacer(modifier.height(if (block.isExplicitLineBreak) 8.dp else 16.dp))
    }
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
    SharedNativeInteractiveText(
        text = block.toAnnotatedString(
            query = searchQuery,
            highlightColor = searchHighlight,
            highlights = highlights,
            activeSelection = activeSelection,
            selectionHighlight = selectionHighlight
        ),
        page = page,
        textStartOffset = block.startCharOffsetInSource,
        color = foreground,
        modifier = modifier,
        textAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineSpacing).sp,
            fontFamily = fallbackFontFamily,
            fontWeight = fontWeight
        ),
        onSelectionChange = onSelectionChange,
        onHighlightSelected = onHighlightSelected,
        onLinkClicked = onLinkClicked
    )
}

private fun SemanticTextBlock.toAnnotatedString(
    query: String,
    highlightColor: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color
): AnnotatedString {
    val normalized = query.trim()
    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start < end) {
                addStyle(span.style.spanStyle, start, end)
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

private fun SharedReaderTextAlign.toComposeTextAlign(): TextAlign {
    return when (this) {
        SharedReaderTextAlign.START -> TextAlign.Start
        SharedReaderTextAlign.JUSTIFY -> TextAlign.Justify
        SharedReaderTextAlign.CENTER -> TextAlign.Center
    }
}

private const val ReaderNativeAnnotationUrl = "URL"
private const val ReaderNativeAnnotationHighlight = "HIGHLIGHT"
