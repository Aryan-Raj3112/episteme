package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.paintOnlyColorOverlayText
import com.aryan.reader.shared.reader.withoutForegroundColorSpans
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlin.math.roundToInt

@Composable
internal fun SharedNativePaginatedPage(
    page: ReaderPage,
    renderPlan: ReaderContentRenderPlan.NativePaginatedPages,
    readerFontFamily: FontFamily,
    searchHighlight: Color,
    selectionHighlight: Color,
    activeSelection: SharedNativeReaderTextSelection?,
    renderGeometry: SharedNativePageRenderGeometry,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    onReaderTap: () -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val settings = renderPlan.settings
    val fallbackTextAlign = settings.textAlign.toComposeTextAlign()
    val visibleHighlights = renderPlan.highlights.visibleInPage(page)
    val blocks = page.semanticBlocks
    val visibleHighlightSignature = remember(visibleHighlights) {
        visibleHighlights.joinToString(separator = "|") { highlight -> highlight.id }
    }
    var contentFit by remember(page.pageIndex, blocks) { mutableStateOf<SharedNativeContentFit?>(null) }
    val blockLayouts = remember(page.pageIndex, blocks) { mutableStateMapOf<Int, SharedNativeBlockFit>() }
    val textLayouts = remember(page.pageIndex, blocks) { mutableStateMapOf<String, SharedNativeTextFit>() }
    val expectedTextLayoutCount = remember(page.pageIndex, blocks, page.text) {
        if (blocks.isEmpty() && page.text.isNotBlank()) {
            1
        } else {
            blocks.sumOf { it.sharedNativeTextFitCount() }
        }
    }
    var layoutVersion by remember(page.pageIndex, blocks) { mutableStateOf(0) }
    var lastPageFitLogSignature by remember(page.pageIndex, blocks) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        page.pageIndex,
        page.chapterIndex,
        page.startOffset,
        page.endOffset,
        renderPlan.highlights.size,
        visibleHighlightSignature
    ) {
        logSharedReaderDiagnostic(DesktopHighlightMapLogTag) {
            "native_page_scope page=${page.pageIndex + 1} chapter=${page.chapterIndex} " +
                "range=${page.startOffset}..${page.endOffset} pageText=${page.text.length} blocks=${blocks.size} " +
                "inputHighlights=${renderPlan.highlights.size} visibleHighlights=${visibleHighlights.size} " +
                "visible=\"${visibleHighlights.take(16).joinToString(";") { highlight -> highlight.nativeHighlightLogKey() }}\""
        }
    }

    LaunchedEffect(
        contentFit,
        layoutVersion,
        blocks.size,
        textLayouts.size,
        expectedTextLayoutCount,
        page.pageIndex,
        page.chapterIndex,
        settings.fontSize,
        settings.lineSpacing,
        settings.paragraphSpacing,
        renderGeometry
    ) {
        val content = contentFit ?: return@LaunchedEffect
        if (blocks.isEmpty() || blockLayouts.size < blocks.size) return@LaunchedEffect
        if (expectedTextLayoutCount > 0 && textLayouts.isEmpty()) return@LaunchedEffect
        val contentTopPx = content.rootTopPx
        val contentHeightPx = content.heightPx
        val contentBottomRootPx = contentTopPx + contentHeightPx
        val orderedFits = blocks.indices.mapNotNull { index -> blockLayouts[index] }
        if (orderedFits.size < blocks.size) return@LaunchedEffect

        val usedPx = orderedFits.maxOfOrNull { fit ->
            fit.relativeBottomPx(contentTopPx)
        } ?: return@LaunchedEffect
        val remainingPx = contentHeightPx - usedPx
        if (remainingPx >= 0) return@LaunchedEffect
        val firstOverflowingBlock = orderedFits.firstOrNull { fit ->
            fit.relativeBottomPx(contentTopPx) > contentHeightPx
        }
        val worstTextOverflow = textLayouts.values
            .maxByOrNull { fit -> fit.lastLineOverflowPx(contentBottomRootPx) }
            ?.takeIf { fit -> fit.lastLineOverflowPx(contentBottomRootPx) > 1 }

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
            worstTextOverflow?.let { fit ->
                append(":text,")
                append(fit.key)
                append(',')
                append(fit.overflowRootBottomPx)
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
            logSharedReaderDiagnostic(EpubCutoffLogTag) {
                "cutoff_probe layer=rendered_overflow page=${page.pageIndex + 1} chapter=${page.chapterIndex} " +
                    "usedPx=$usedPx contentPx=${renderGeometry.pageContentWidthPx}x$contentHeightPx " +
                    "expectedContentPx=${renderGeometry.pageContentWidthPx}x${renderGeometry.pageContentHeightPx} " +
                    "remainingPx=$remainingPx overflowPx=${(-remainingPx).coerceAtLeast(0)} blocks=${blocks.size} " +
                    "readerPx=${renderGeometry.readerWidthPx}x${renderGeometry.readerHeightPx} " +
                    "pageOuterPx=${renderGeometry.pageOuterWidthPx} visiblePages=${renderGeometry.visiblePageCount} " +
                    "spread=${renderGeometry.spreadMode} pageGapPx=${renderGeometry.pageGapPx} " +
                    "marginsPx=${renderGeometry.horizontalMarginPx}x${renderGeometry.verticalMarginPx} " +
                    "configuredPageWidthPx=${renderGeometry.configuredPageWidthPx} " +
                    "firstOverflowBlock=\"${firstOverflowingBlock?.format(contentTopPx) ?: "none"}\" " +
                    "overflowText=\"${worstTextOverflow?.format(contentTopPx, contentBottomRootPx) ?: "none"}\" " +
                    "range=${page.startOffset}..${page.endOffset} textChars=${page.text.length} " +
                    "tail=\"${orderedFits.renderedPageFitTail(contentTopPx)}\""
            }
        }
    }

    val showsPageChrome = renderGeometry.showsPageChrome
    Surface(
        modifier = modifier,
        shape = if (showsPageChrome) RoundedCornerShape(4.dp) else RectangleShape,
        color = renderPlan.background,
        contentColor = renderPlan.foreground,
        tonalElevation = 0.dp,
        shadowElevation = if (showsPageChrome) 1.dp else 0.dp,
        border = if (showsPageChrome) BorderStroke(1.dp, renderPlan.foreground.copy(alpha = 0.14f)) else null
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
                        chapterIndex = page.chapterIndex,
                        pageIndex = page.pageIndex,
                        absoluteStartOffset = page.startOffset,
                        highlights = visibleHighlights,
                        activeSelection = activeSelection,
                        selectionHighlight = selectionHighlight
                    ),
                    page = page,
                    textBlock = SharedNativeTextBlockDescriptor(
                        chapterIndex = page.chapterIndex,
                        pageIndex = page.pageIndex,
                        blockIndex = -1,
                        blockCharOffset = page.startOffset,
                        baseCfi = null,
                        textStartOffset = page.startOffset,
                        text = page.text
                    ),
                    textStartOffset = page.startOffset,
                    color = renderPlan.foreground,
                    textAlign = fallbackTextAlign,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = settings.fontSize.sp,
                        lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                        fontFamily = readerFontFamily,
                        fontWeight = settings.fontWeight.takeIf { it > 0 }?.let(::FontWeight),
                        letterSpacing = settings.letterSpacing.em
                    ).withAndroidPaginationTextMetrics(settings.letterSpacing),
                    activeSelection = activeSelection,
                    onReaderTap = onReaderTap,
                    onSelectionChange = onSelectionChange,
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
                    onTextLaidOut = { fit ->
                        if (textLayouts[fit.key] != fit) {
                            textLayouts[fit.key] = fit
                            layoutVersion += 1
                        }
                    },
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
                    background = renderPlan.background,
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
                    onReaderTap = onReaderTap,
                    onSelectionChange = onSelectionChange,
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
                    imageContent = imageContent,
                    onTextLaidOut = { fit ->
                        if (textLayouts[fit.key] != fit) {
                            textLayouts[fit.key] = fit
                            layoutVersion += 1
                        }
                    },
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
internal fun SharedNativeSelectionMenu(
    @Suppress("UNUSED_PARAMETER")
    selection: SharedNativeReaderTextSelection,
    highlightPalette: List<HighlightColor>,
    enabledSelectionActions: Set<SharedNativeReaderSelectionAction>,
    background: Color,
    foreground: Color,
    onCopy: () -> Unit,
    onSelectionAction: (SharedNativeReaderSelectionAction) -> Unit,
    onHighlight: (HighlightColor, HighlightStyle) -> Unit,
    onOpenHighlightPaletteManager: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuBackground = background.blendWith(foreground, foregroundWeight = 0.08f)
    val borderColor = foreground.copy(alpha = 0.18f)
    val hoverIconBackground = foreground.copy(alpha = 0.09f)
    val iconColor = foreground.copy(alpha = 0.86f)
    var selectedStyle by remember { mutableStateOf(HighlightStyle.BACKGROUND) }
    val actions = buildList {
        add(SharedNativeSelectionMenuAction("Copy", SharedNativeSelectionVectorIcons.Copy, onCopy))
        if (SharedNativeReaderSelectionAction.DEFINE in enabledSelectionActions) {
            add(
                SharedNativeSelectionMenuAction(
                    "Define",
                    SharedNativeSelectionVectorIcons.Define,
                    { onSelectionAction(SharedNativeReaderSelectionAction.DEFINE) }
                )
            )
        }
        if (SharedNativeReaderSelectionAction.SPEAK in enabledSelectionActions) {
            add(
                SharedNativeSelectionMenuAction(
                    "Speak",
                    SharedNativeSelectionVectorIcons.Speak,
                    { onSelectionAction(SharedNativeReaderSelectionAction.SPEAK) }
                )
            )
        }
        if (SharedNativeReaderSelectionAction.TRANSLATE in enabledSelectionActions) {
            add(
                SharedNativeSelectionMenuAction(
                    "Translate",
                    SharedNativeSelectionVectorIcons.Define,
                    { onSelectionAction(SharedNativeReaderSelectionAction.TRANSLATE) }
                )
            )
        }
        if (SharedNativeReaderSelectionAction.SEARCH in enabledSelectionActions) {
            add(
                SharedNativeSelectionMenuAction(
                    "Search",
                    SharedNativeSelectionVectorIcons.Search,
                    { onSelectionAction(SharedNativeReaderSelectionAction.SEARCH) }
                )
            )
        }
        if (SharedNativeReaderSelectionAction.NOTE in enabledSelectionActions) {
            add(
                SharedNativeSelectionMenuAction(
                    "Note",
                    SharedNativeSelectionVectorIcons.Copy,
                    { onSelectionAction(SharedNativeReaderSelectionAction.NOTE) }
                )
            )
        }
        add(SharedNativeSelectionMenuAction("Clear", SharedNativeSelectionVectorIcons.Clear, onDismiss))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = menuBackground,
        contentColor = foreground,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(max = 280.dp)
                .padding(bottom = 6.dp)
        ) {
            if (highlightPalette.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightStyle.entries.forEach { style ->
                        SharedNativeHighlightStyleButton(
                            style = style,
                            selected = selectedStyle == style,
                            foreground = foreground,
                            borderColor = borderColor,
                            onClick = { selectedStyle = style }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    highlightPalette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color.color)
                                .border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = CircleShape
                                )
                                .clickable { onHighlight(color, selectedStyle) }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    SharedNativeSelectionPaletteButton(
                        onClick = onOpenHighlightPaletteManager,
                        modifier = Modifier.size(28.dp)
                    )
                }
                HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            }
            Column(
                modifier = Modifier
                    .padding(start = 6.dp, top = 5.dp, end = 6.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowActions.forEach { action ->
                            SharedNativeSelectionIconButton(
                                action = action,
                                iconColor = iconColor,
                                iconBackground = hoverIconBackground,
                                foreground = foreground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedNativeHighlightStyleButton(
    style: HighlightStyle,
    selected: Boolean,
    foreground: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    val label = when (style) {
        HighlightStyle.BACKGROUND -> "B"
        HighlightStyle.UNDERLINE -> "U"
        HighlightStyle.WAVY_UNDERLINE -> "~"
        HighlightStyle.STRIKETHROUGH -> "S"
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(foreground.copy(alpha = if (selected) 0.18f else 0.05f))
            .border(1.dp, if (selected) foreground.copy(alpha = 0.55f) else borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = foreground, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

internal data class SharedNativeSelectionMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
internal fun SharedNativeSelectionPaletteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red,
        Color(0xFFFF7F00),
        Color.Yellow,
        Color.Green,
        Color.Blue,
        Color(0xFF4B0082),
        Color(0xFF8B00FF)
    )
    Box(
        modifier = modifier
            .background(
                brush = Brush.sweepGradient(rainbowColors),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
internal fun SharedNativeSelectionIconButton(
    action: SharedNativeSelectionMenuAction,
    iconColor: Color,
    iconBackground: Color,
    foreground: Color
) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { action.onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = action.label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
internal fun SharedNativeSelectionHandleView(
    selection: SharedNativeReaderTextSelection,
    handle: SharedNativeSelectionHandle,
    selectionLayouts: Collection<SharedNativeTextLayoutInfo>,
    readerCoordinates: LayoutCoordinates?,
    onDragActiveChange: (Boolean) -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleOffset = sharedNativeSelectionHandleOffset(
        selection = selection,
        handle = handle,
        layouts = selectionLayouts,
        readerCoordinates = readerCoordinates,
        density = density
    ) ?: return
    val handleColor = MaterialTheme.colorScheme.primary
    var handleCoordinates by remember(handle) { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = modifier
            .offset { handleOffset }
            .size(28.dp)
            .onGloballyPositioned { handleCoordinates = it }
            .pointerInput(handle) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onDragActiveChange(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            handleCoordinates
                                ?.takeIf { it.isAttached }
                                ?.let { coordinates -> onDrag(coordinates.localToWindow(change.position)) }
                            change.consume()
                        }
                    } finally {
                        onDragActiveChange(false)
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Icon(
            imageVector = SharedNativeSelectionVectorIcons.Teardrop,
            contentDescription = if (handle == SharedNativeSelectionHandle.START) {
                "Adjust selection start"
            } else {
                "Adjust selection end"
            },
            tint = handleColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    rotationZ = if (handle == SharedNativeSelectionHandle.START) 28f else -28f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
        )
    }
}

@Composable
internal fun SharedNativeInteractiveText(
    text: AnnotatedString,
    page: ReaderPage,
    textBlock: SharedNativeTextBlockDescriptor,
    textStartOffset: Int,
    color: Color,
    textAlign: TextAlign,
    style: TextStyle,
    activeSelection: SharedNativeReaderTextSelection?,
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    modifier: Modifier = Modifier,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null,
    fitLabel: SharedNativeTextFitLabel? = null
) {
    var textLayoutResult by remember(text.text) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(text.text) { mutableStateOf<LayoutCoordinates?>(null) }
    var lastTextClipLogSignature by remember(text.text) { mutableStateOf<String?>(null) }
    var dragAnchorOffset by remember(text.text) { mutableStateOf<Int?>(null) }
    val currentText by rememberUpdatedState(text)
    val currentActiveSelection by rememberUpdatedState(activeSelection)
    val viewConfiguration = LocalViewConfiguration.current
    val textBlockKey = textBlock.key.stableKey
    val selectionGestureKey = sharedNativeReaderSelectionGestureKey(textBlockKey, text)
    val shapingText = remember(text) { text.withoutForegroundColorSpans() }
    val paintOnlyColorOverlayText = remember(text, color) {
        text.paintOnlyColorOverlayText(baseColor = color)
    }
    DisposableEffect(textBlockKey, selectionLayouts) {
        onDispose {
            selectionLayouts.remove(textBlockKey)
        }
    }
    LaunchedEffect(textLayoutResult, textCoordinates, textBlock, textBlockKey) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val coordinates = textCoordinates ?: return@LaunchedEffect
        selectionLayouts[textBlockKey] = SharedNativeTextLayoutInfo(
            descriptor = textBlock,
            layout = layout,
            coordinates = coordinates
        )
    }
    LaunchedEffect(textLayoutResult, textCoordinates, fitLabel) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val coordinates = textCoordinates ?: return@LaunchedEffect
        val label = fitLabel ?: return@LaunchedEffect
        val fit = label.toSharedNativeTextFit(coordinates, layout)
        onTextLaidOut?.invoke(fit)
        val boxWidthPx = coordinates.size.width
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
                "block=${label.blockIndex} kind=${label.kind} boxPx=${boxWidthPx}x$boxHeightPx layoutPx=${layout.size.width}x$layoutHeightPx " +
                "lastLineBottomPx=$lastLineBottomPx clipPx=$clipPx lines=${layout.lineCount} " +
                "range=${label.sourceRange} textChars=${label.textChars}"
        }
        logSharedReaderDiagnostic(EpubCutoffLogTag) {
            "cutoff_probe layer=text_clip page=${label.page.pageIndex + 1} chapter=${label.page.chapterIndex} " +
                "block=${label.blockIndex} kind=${label.kind} boxPx=${boxWidthPx}x$boxHeightPx " +
                "layoutPx=${layout.size.width}x$layoutHeightPx lastLineBottomPx=$lastLineBottomPx " +
                "clipPx=$clipPx lines=${layout.lineCount} range=${label.sourceRange} textChars=${label.textChars}"
        }
    }
    Box(modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { textCoordinates = it }
            .pointerInput(selectionGestureKey) {
                detectTapGestures(
                    onPress = {
                        onSelectionGestureActiveChange(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onSelectionGestureActiveChange(false)
                        }
                    },
                    onLongPress = { offset ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val annotatedText = currentText
                        val plainText = annotatedText.text
                        val charOffset = layout.getOffsetForPosition(offset)
                            .coerceIn(0, plainText.length)
                        val boundary = layout.getWordBoundary(charOffset)
                        val range = sharedNativeReaderTrimmedWordRange(
                            text = plainText,
                            start = boundary.start,
                            end = boundary.end
                        ) ?: return@detectTapGestures
                        onSelectionChange(
                            sharedNativeReaderSelectionBetween(
                                start = SharedNativeTextPosition(textBlock, range.start),
                                end = SharedNativeTextPosition(textBlock, range.end),
                                layouts = selectionLayouts.values
                            )
                        )
                    },
                    onTap = { offset ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val annotatedText = currentText
                        val plainText = annotatedText.text
                        val charOffset = layout.getOffsetForPosition(offset)
                            .coerceIn(0, plainText.length)
                        annotatedText.stringAnnotationAt(ReaderNativeAnnotationUrl, charOffset)?.let { href ->
                            onSelectionChange(null)
                            onLinkClicked(
                                SharedNativeReaderLinkClick(
                                    href = href,
                                    chapterIndex = page.chapterIndex,
                                    text = plainText
                                )
                            )
                            return@detectTapGestures
                        }
                        annotatedText.stringAnnotationAt(ReaderNativeAnnotationHighlight, charOffset)?.let { highlightId ->
                            onSelectionChange(null)
                            onHighlightSelected(highlightId)
                            return@detectTapGestures
                        }
                        if (currentActiveSelection == null) {
                            onReaderTap()
                        }
                        onSelectionChange(null)
                    }
                )
            }
            .pointerInput(selectionGestureKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onSelectionGestureActiveChange(true)
                        val layout = textLayoutResult
                        if (layout != null) {
                            val plainText = currentText.text
                            val charOffset = layout.getOffsetForPosition(offset)
                                .coerceIn(0, plainText.length)
                            val boundary = layout.getWordBoundary(charOffset)
                            val range = sharedNativeReaderTrimmedWordRange(
                                text = plainText,
                                start = boundary.start,
                                end = boundary.end
                            )
                            if (range != null) {
                                dragAnchorOffset = range.start
                                onSelectionChange(
                                    sharedNativeReaderSelectionBetween(
                                        start = SharedNativeTextPosition(textBlock, range.start),
                                        end = SharedNativeTextPosition(textBlock, range.end),
                                        layouts = selectionLayouts.values
                                    )
                                )
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val layout = textLayoutResult
                        val anchor = dragAnchorOffset
                        if (layout != null && anchor != null) {
                            val plainText = currentText.text
                            val current = textCoordinates?.let { coordinates ->
                                sharedNativeReaderTextPositionAtWindow(
                                    windowPosition = coordinates.localToWindow(change.position),
                                    layouts = selectionLayouts.values
                                )
                            } ?: SharedNativeTextPosition(
                                descriptor = textBlock,
                                localOffset = layout.getOffsetForPosition(change.position)
                                    .coerceIn(0, plainText.length)
                            )
                            onSelectionChange(
                                sharedNativeReaderSelectionBetween(
                                    start = SharedNativeTextPosition(textBlock, anchor),
                                    end = current,
                                    layouts = selectionLayouts.values
                                )
                            )
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        dragAnchorOffset = null
                        onSelectionGestureActiveChange(false)
                    },
                    onDragCancel = {
                        dragAnchorOffset = null
                        onSelectionGestureActiveChange(false)
                    }
                )
            }
            .pointerInput(selectionGestureKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val layout = textLayoutResult ?: return@awaitEachGesture
                    val coordinates = textCoordinates ?: return@awaitEachGesture
                    val plainText = currentText.text
                    val anchorOffset = layout.getOffsetForPosition(down.position)
                        .coerceIn(0, plainText.length)
                    val anchor = SharedNativeTextPosition(textBlock, anchorOffset)
                    val touchSlopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop
                    var selecting = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!selecting && dx * dx + dy * dy >= touchSlopSquared) {
                                selecting = true
                                onSelectionGestureActiveChange(true)
                            }
                            if (selecting) {
                                val latestCoordinates = textCoordinates ?: coordinates
                                val windowPosition = latestCoordinates.localToWindow(change.position)
                                val current = sharedNativeReaderTextPositionAtWindow(
                                    windowPosition = windowPosition,
                                    layouts = selectionLayouts.values
                                ) ?: SharedNativeTextPosition(
                                    descriptor = textBlock,
                                    localOffset = layout.getOffsetForPosition(change.position)
                                        .coerceIn(0, plainText.length)
                                )
                                onSelectionChange(
                                    sharedNativeReaderSelectionBetween(
                                        start = anchor,
                                        end = current,
                                        layouts = selectionLayouts.values
                                    )
                                )
                                change.consume()
                            }
                        }
                    } finally {
                        if (selecting) {
                            onSelectionGestureActiveChange(false)
                        }
                    }
                }
            }) {
        Text(
            text = shapingText,
            color = color,
            modifier = Modifier.fillMaxWidth(),
            textAlign = textAlign,
            style = style,
            onTextLayout = { textLayoutResult = it }
        )
        if (paintOnlyColorOverlayText.isNotEmpty()) {
            Text(
                text = paintOnlyColorOverlayText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {},
                textAlign = textAlign,
                style = style.copy(color = Color.Transparent)
            )
        }
    }
}
