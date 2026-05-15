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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.compose.ui.unit.IntOffset
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
    val text: String,
    val startPageIndex: Int = pageIndex,
    val endPageIndex: Int = pageIndex,
    val startBlockIndex: Int = -1,
    val endBlockIndex: Int = -1,
    val startBlockCharOffset: Int = startOffset,
    val endBlockCharOffset: Int = endOffset,
    val startLocalOffset: Int = 0,
    val endLocalOffset: Int = endOffset - startOffset,
    val startBaseCfi: String? = null,
    val endBaseCfi: String? = null,
    val rect: Rect = Rect.Zero,
    val textPerBlock: Map<String, String> = emptyMap()
) {
    val cfi: String
        get() = if (!startBaseCfi.isNullOrBlank() && !endBaseCfi.isNullOrBlank()) {
            "${startBaseCfi}:${startLocalOffset}|${endBaseCfi}:${endLocalOffset}"
        } else {
            "desktop:$chapterIndex:$startOffset:$endOffset"
        }
}

private data class SharedNativeSelectionBlockKey(
    val pageIndex: Int,
    val blockIndex: Int,
    val blockCharOffset: Int
) {
    val stableKey: String get() = "$pageIndex:$blockIndex:$blockCharOffset"
}

private data class SharedNativeTextBlockDescriptor(
    val chapterIndex: Int,
    val pageIndex: Int,
    val blockIndex: Int,
    val blockCharOffset: Int,
    val baseCfi: String?,
    val textStartOffset: Int,
    val text: String
) {
    val key: SharedNativeSelectionBlockKey
        get() = SharedNativeSelectionBlockKey(pageIndex, blockIndex, blockCharOffset)
}

private data class SharedNativeTextLayoutInfo(
    val descriptor: SharedNativeTextBlockDescriptor,
    val layout: TextLayoutResult,
    val coordinates: LayoutCoordinates
)

private data class SharedNativeTextPosition(
    val descriptor: SharedNativeTextBlockDescriptor,
    val localOffset: Int
)

private enum class SharedNativeSelectionHandle {
    START,
    END
}

private object SharedNativeSelectionVectorIcons {
    val Copy: ImageVector = vector(
        name = "SharedNativeSelectionCopy",
        pathData = "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640L720,160L360,160L360,640ZM200,880Q167,880 143.5,856.5Q120,833 120,800L120,240L200,240L200,800L640,800L640,880L200,880Z"
    )
    val Define: ImageVector = vector(
        name = "SharedNativeSelectionDefine",
        pathData = "M480,800Q432,762 376,741Q320,720 260,720Q218,720 177.5,731Q137,742 100,762Q79,773 59.5,761Q40,749 40,726L40,244Q40,233 45.5,223Q51,213 62,208Q108,184 158,172Q208,160 260,160Q318,160 373.5,175Q429,190 480,220Q531,190 586.5,175Q642,160 700,160Q752,160 802,172Q852,184 898,208Q909,213 914.5,223Q920,233 920,244L920,726Q920,749 900.5,761Q881,773 860,762Q823,742 782.5,731Q742,720 700,720Q640,720 584,741Q528,762 480,800ZM520,682Q564,661 608.5,650.5Q653,640 700,640Q736,640 770.5,646Q805,652 840,664L840,268Q807,254 771.5,247Q736,240 700,240Q653,240 607,252Q561,264 520,288L520,682ZM440,682L440,288Q399,264 353,252Q307,240 260,240Q224,240 188.5,247Q153,254 120,268L120,664Q155,652 189.5,646Q224,640 260,640Q307,640 351.5,650.5Q396,661 440,682Z"
    )
    val Speak: ImageVector = vector(
        name = "SharedNativeSelectionSpeak",
        pathData = "M560,828L560,746Q653,719 706.5,642Q760,565 760,466Q760,367 706.5,290Q653,213 560,186L560,104Q687,133 763.5,234Q840,335 840,466Q840,597 763.5,698Q687,799 560,828ZM120,600L120,360L280,360L480,160L480,800L280,600L120,600ZM560,640L560,292Q612,317 646,364.5Q680,412 680,466Q680,520 646,567.5Q612,615 560,640Z"
    )
    val Search: ImageVector = vector(
        name = "SharedNativeSelectionSearch",
        pathData = "M784,840L532,588Q502,612 463,626Q424,640 380,640Q271,640 195.5,564.5Q120,489 120,380Q120,271 195.5,195.5Q271,120 380,120Q489,120 564.5,195.5Q640,271 640,380Q640,424 626,463Q612,502 588,532L840,784L784,840ZM380,560Q455,560 507.5,507.5Q560,455 560,380Q560,305 507.5,252.5Q455,200 380,200Q305,200 252.5,252.5Q200,305 200,380Q200,455 252.5,507.5Q305,560 380,560Z"
    )
    val Clear: ImageVector = vector(
        name = "SharedNativeSelectionClear",
        pathData = "M256,760L200,704L424,480L200,256L256,200L480,424L704,200L760,256L536,480L760,704L704,760L480,536L256,760Z"
    )
    val Teardrop: ImageVector = vector(
        name = "SharedNativeSelectionTeardrop",
        pathData = "M480,860Q347,860 253.5,768Q160,676 160,544Q160,481 184.5,423.5Q209,366 254,322L480,100L706,322Q751,366 775.5,423.5Q800,481 800,544Q800,676 706.5,768Q613,860 480,860Z"
    )

    private fun vector(name: String, pathData: String): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }
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
    var selectionGestureActive by remember(renderPlan.navigationTarget.requestId) {
        mutableStateOf(false)
    }
    var selectionHandleDragging by remember(renderPlan.navigationTarget.requestId) {
        mutableStateOf(false)
    }
    fun updateActiveSelection(selection: SharedNativeReaderTextSelection?) {
        activeSelection = selection
        if (selection == null) {
            selectionGestureActive = false
            selectionHandleDragging = false
        }
    }
    val visiblePageIndices = remember(visiblePages) { visiblePages.map { it.pageIndex } }
    val selectionLayouts = remember(renderPlan.navigationTarget.requestId, visiblePageIndices) {
        mutableStateMapOf<String, SharedNativeTextLayoutInfo>()
    }
    var readerCoordinates by remember(renderPlan.navigationTarget.requestId) {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    val density = LocalDensity.current
    LaunchedEffect(visiblePageIndices) {
        val selection = activeSelection
        if (selection != null && selection.pageIndex !in visiblePageIndices) {
            updateActiveSelection(null)
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
    Box(
        modifier = modifier.onGloballyPositioned { readerCoordinates = it }
    ) {
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
                        onSelectionChange = ::updateActiveSelection,
                        onSelectionGestureActiveChange = { selectionGestureActive = it },
                        onHighlightSelected = onHighlightSelected,
                        onLinkClicked = onLinkClicked,
                        selectionLayouts = selectionLayouts,
                        imageContent = imageContent,
                        modifier = Modifier
                            .width(pageOuterWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }
        activeSelection?.let { selection ->
            arrayOf(SharedNativeSelectionHandle.START, SharedNativeSelectionHandle.END).forEach { handle ->
                SharedNativeSelectionHandleView(
                    selection = selection,
                    handle = handle,
                    selectionLayouts = selectionLayouts.values,
                    readerCoordinates = readerCoordinates,
                    onDragActiveChange = { selectionHandleDragging = it },
                    onDrag = { windowPosition ->
                        val currentSelection = activeSelection
                        if (currentSelection != null) {
                            sharedNativeSelectionWithHandleMoved(
                                selection = currentSelection,
                                handle = handle,
                                windowPosition = windowPosition,
                                layouts = selectionLayouts.values
                            )?.let(::updateActiveSelection)
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            if (!selectionGestureActive && !selectionHandleDragging) {
                SharedNativeSelectionMenu(
                    selection = selection,
                    highlightPalette = renderPlan.highlightPalette.sanitized().colors,
                    enabledSelectionActions = enabledSelectionActions,
                    background = renderPlan.background,
                    foreground = renderPlan.foreground,
                    onCopy = {
                        onCopyText(selection.text)
                        updateActiveSelection(null)
                    },
                    onSelectionAction = { action ->
                        onSelectionAction(action, selection.text)
                        updateActiveSelection(null)
                    },
                    onHighlight = { color ->
                        onHighlightCreated(sharedNativeReaderHighlightForSelection(selection, color))
                        updateActiveSelection(null)
                    },
                    onDismiss = { updateActiveSelection(null) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            sharedNativeSelectionMenuOffset(
                                selection = selection,
                                readerCoordinates = readerCoordinates,
                                density = density
                            )
                        }
                )
            }
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
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
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
                        fontFamily = readerFontFamily
                    ).withAndroidPaginationTextMetrics(),
                    onSelectionChange = onSelectionChange,
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
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
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
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
    @Suppress("UNUSED_PARAMETER")
    selection: SharedNativeReaderTextSelection,
    highlightPalette: List<HighlightColor>,
    enabledSelectionActions: Set<SharedNativeReaderSelectionAction>,
    background: Color,
    foreground: Color,
    onCopy: () -> Unit,
    onSelectionAction: (SharedNativeReaderSelectionAction) -> Unit,
    onHighlight: (HighlightColor) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuBackground = background.blendWith(foreground, foregroundWeight = 0.08f)
    val borderColor = foreground.copy(alpha = 0.18f)
    val hoverIconBackground = foreground.copy(alpha = 0.09f)
    val iconColor = foreground.copy(alpha = 0.86f)
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
        if (SharedNativeReaderSelectionAction.SEARCH in enabledSelectionActions) {
            add(
                SharedNativeSelectionMenuAction(
                    "Search",
                    SharedNativeSelectionVectorIcons.Search,
                    { onSelectionAction(SharedNativeReaderSelectionAction.SEARCH) }
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
                .widthIn(max = 300.dp)
                .padding(bottom = 6.dp)
        ) {
            if (highlightPalette.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    highlightPalette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color.color)
                                .border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = CircleShape
                                )
                                .clickable { onHighlight(color) }
                        )
                    }
                }
                HorizontalDivider(color = foreground.copy(alpha = 0.12f))
            }
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

private data class SharedNativeSelectionMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun SharedNativeSelectionIconButton(
    action: SharedNativeSelectionMenuAction,
    iconColor: Color,
    iconBackground: Color,
    foreground: Color
) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { action.onClick() }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = action.label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun SharedNativeSelectionHandleView(
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
private fun SharedNativeInteractiveText(
    text: AnnotatedString,
    page: ReaderPage,
    textBlock: SharedNativeTextBlockDescriptor,
    textStartOffset: Int,
    color: Color,
    textAlign: TextAlign,
    style: TextStyle,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    modifier: Modifier = Modifier,
    fitLabel: SharedNativeTextFitLabel? = null
) {
    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(text) { mutableStateOf<LayoutCoordinates?>(null) }
    var lastTextClipLogSignature by remember(text) { mutableStateOf<String?>(null) }
    var dragAnchorOffset by remember(text) { mutableStateOf<Int?>(null) }
    val viewConfiguration = LocalViewConfiguration.current
    val textBlockKey = textBlock.key.stableKey
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
                        val charOffset = layout.getOffsetForPosition(offset)
                            .coerceIn(0, text.text.length)
                        val boundary = layout.getWordBoundary(charOffset)
                        val range = sharedNativeReaderTrimmedWordRange(
                            text = text.text,
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
                        onSelectionGestureActiveChange(true)
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
                            val current = textCoordinates?.let { coordinates ->
                                sharedNativeReaderTextPositionAtWindow(
                                    windowPosition = coordinates.localToWindow(change.position),
                                    layouts = selectionLayouts.values
                                )
                            } ?: SharedNativeTextPosition(
                                descriptor = textBlock,
                                localOffset = layout.getOffsetForPosition(change.position)
                                    .coerceIn(0, text.text.length)
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
            .pointerInput(textBlockKey, text) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val layout = textLayoutResult ?: return@awaitEachGesture
                    val coordinates = textCoordinates ?: return@awaitEachGesture
                    val anchorOffset = layout.getOffsetForPosition(down.position)
                        .coerceIn(0, text.text.length)
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
                                        .coerceIn(0, text.text.length)
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
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
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
            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
            onHighlightSelected = onHighlightSelected,
            onLinkClicked = onLinkClicked,
            selectionLayouts = selectionLayouts,
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
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
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
                onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                onHighlightSelected = onHighlightSelected,
                onLinkClicked = onLinkClicked,
                selectionLayouts = selectionLayouts
            )
        }

        is SemanticParagraph -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts)
        is SemanticListItem -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts)
        is SemanticTextBlock -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts)

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
                            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                            onHighlightSelected = onHighlightSelected,
                            onLinkClicked = onLinkClicked,
                            selectionLayouts = selectionLayouts
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
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
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
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
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
                                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                                    onHighlightSelected = onHighlightSelected,
                                    onLinkClicked = onLinkClicked,
                                    selectionLayouts = selectionLayouts,
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
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>
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
            blockFontSizeSp = textStyle.fontSize.value,
            pageIndex = page.pageIndex,
            blockCfi = block.cfi,
            blockIndex = block.blockIndex,
            blockCharOffset = block.startCharOffsetInSource
        ),
        page = page,
        textBlock = SharedNativeTextBlockDescriptor(
            chapterIndex = page.chapterIndex,
            pageIndex = page.pageIndex,
            blockIndex = block.blockIndex,
            blockCharOffset = block.startCharOffsetInSource,
            baseCfi = block.cfi,
            textStartOffset = block.startCharOffsetInSource,
            text = block.text
        ),
        textStartOffset = block.startCharOffsetInSource,
        color = foreground,
        modifier = modifier,
        textAlign = block.style.paragraphStyle.textAlign.takeUnless { it == TextAlign.Unspecified } ?: fallbackTextAlign,
        style = textStyle,
        onSelectionChange = onSelectionChange,
        onSelectionGestureActiveChange = onSelectionGestureActiveChange,
        onHighlightSelected = onHighlightSelected,
        onLinkClicked = onLinkClicked,
        selectionLayouts = selectionLayouts,
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
    blockFontSizeSp: Float,
    pageIndex: Int,
    blockCfi: String?,
    blockIndex: Int,
    blockCharOffset: Int
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
                blockCfi = blockCfi,
                textStartOffset = startCharOffsetInSource,
                textLength = text.length,
                text = text
            )
        }
        applySelectionToTextRange(
            selection = activeSelection,
            pageIndex = pageIndex,
            blockIndex = blockIndex,
            blockCharOffset = blockCharOffset,
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

private fun Color.blendWith(other: Color, foregroundWeight: Float): Color {
    val weight = foregroundWeight.coerceIn(0f, 1f)
    val baseWeight = 1f - weight
    return Color(
        red * baseWeight + other.red * weight,
        green * baseWeight + other.green * weight,
        blue * baseWeight + other.blue * weight,
        alpha
    )
}

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
        val pageMatch = locator.pageIndex == page.pageIndex
        val offsetMatch = start != null &&
            end != null &&
            start < page.endOffset &&
            end > page.startOffset
        chapterIndex == page.chapterIndex && (pageMatch || offsetMatch)
    }
}

private fun AnnotatedString.Builder.applyHighlightToTextRange(
    highlight: UserHighlight,
    blockCfi: String? = null,
    textStartOffset: Int,
    textLength: Int,
    text: String? = null
) {
    val cfiRange = sharedNativeHighlightRangeInBlock(
        highlight = highlight,
        blockCfi = blockCfi,
        textLength = textLength,
        text = text
    )
    if (cfiRange != null) {
        addStyle(
            style = SpanStyle(background = highlight.color.color.copy(alpha = 0.38f)),
            start = cfiRange.start,
            end = cfiRange.end
        )
        addStringAnnotation(ReaderNativeAnnotationHighlight, highlight.id, cfiRange.start, cfiRange.end)
        return
    }
    if (highlight.cfi.contains('|') || highlight.cfi.startsWith("/")) return
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

private fun AnnotatedString.stringAnnotationAt(tag: String, offset: Int): String? {
    if (isEmpty()) return null
    val start = offset.coerceIn(0, (length - 1).coerceAtLeast(0))
    val end = (start + 1).coerceAtMost(length)
    return getStringAnnotations(tag, start, end).firstOrNull()?.item
}

private data class SharedNativeSelectedTextRange(
    val info: SharedNativeTextLayoutInfo,
    val start: Int,
    val end: Int
)

private data class SharedNativeSelectionEndpoint(
    val info: SharedNativeTextLayoutInfo,
    val localOffset: Int
)

private fun sharedNativeSelectionMenuOffset(
    selection: SharedNativeReaderTextSelection,
    readerCoordinates: LayoutCoordinates?,
    density: Density
): IntOffset {
    val coordinates = readerCoordinates?.takeIf { it.isAttached } ?: return IntOffset(16, 16)
    if (selection.rect == Rect.Zero) return IntOffset(16, 16)
    val centerX = (selection.rect.left + selection.rect.right) / 2f
    val topLocal = coordinates.windowToLocal(Offset(centerX, selection.rect.top))
    val bottomLocal = coordinates.windowToLocal(Offset(centerX, selection.rect.bottom))
    val paddingPx = with(density) { 16.dp.toPx() }
    val estimatedWidthPx = with(density) { 300.dp.toPx() }
    val estimatedHeightPx = with(density) { 154.dp.toPx() }
    val maxX = (coordinates.size.width - estimatedWidthPx - paddingPx).coerceAtLeast(paddingPx)
    val x = (topLocal.x - estimatedWidthPx / 2f).coerceIn(paddingPx, maxX)
    val yAbove = topLocal.y - estimatedHeightPx - paddingPx
    val y = if (yAbove >= paddingPx) {
        yAbove
    } else {
        (bottomLocal.y + paddingPx).coerceAtMost(
            (coordinates.size.height - estimatedHeightPx - paddingPx).coerceAtLeast(paddingPx)
        )
    }
    return IntOffset(x.roundToInt(), y.roundToInt())
}

private fun sharedNativeSelectionHandleOffset(
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

private fun sharedNativeSelectionWithHandleMoved(
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

private fun sharedNativeSelectionEndpointPosition(
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

private fun sharedNativeSelectionEndpoint(
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

private fun sharedNativeReaderTextPositionAtWindow(
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

private fun sharedNativeReaderSelectionBetween(
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

private fun sharedNativeSelectionRangeInBlock(
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

private fun sharedNativeSelectionRangeInBlock(
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

private fun sharedNativeCompareTextPositions(
    first: SharedNativeTextPosition,
    second: SharedNativeTextPosition
): Int {
    val blockCompare = sharedNativeCompareBlockKeys(first.descriptor.key, second.descriptor.key)
    return if (blockCompare != 0) blockCompare else first.localOffset.compareTo(second.localOffset)
}

private fun sharedNativeCompareBlockToPosition(
    block: SharedNativeTextBlockDescriptor,
    position: SharedNativeTextPosition
): Int = sharedNativeCompareBlockKeys(block.key, position.descriptor.key)

private fun sharedNativeCompareBlockKeys(
    first: SharedNativeSelectionBlockKey,
    second: SharedNativeSelectionBlockKey
): Int {
    if (first.pageIndex != second.pageIndex) return first.pageIndex.compareTo(second.pageIndex)
    if (first.blockIndex != second.blockIndex) return first.blockIndex.compareTo(second.blockIndex)
    return first.blockCharOffset.compareTo(second.blockCharOffset)
}

private fun sharedNativeTrimSelectedRanges(ranges: MutableList<SharedNativeSelectedTextRange>) {
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

private fun sharedNativeSelectionRect(ranges: List<SharedNativeSelectedTextRange>): Rect {
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

private data class SharedNativeCfiPoint(
    val path: String,
    val offset: Int
)

private fun sharedNativeHighlightRangeInBlock(
    highlight: UserHighlight,
    blockCfi: String?,
    textLength: Int,
    text: String?
): SharedNativeReaderTextRange? {
    val cfi = highlight.cfi.takeIf { it.contains('|') || it.startsWith("/") } ?: return null
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

    var localStart = if (startMatches) start.offset else 0
    var localEnd = if (endMatches) end.offset else textLength
    if (startMatches && endMatches && localEnd < localStart) {
        localStart = localEnd.also { localEnd = localStart }
    }
    localStart = localStart.coerceIn(0, textLength)
    localEnd = localEnd.coerceIn(localStart, textLength)
    if (localStart < localEnd) {
        return SharedNativeReaderTextRange(localStart, localEnd)
    }

    val quote = highlight.text.takeIf { it.isNotBlank() }
    val blockText = text
    if (quote != null && blockText != null) {
        val exact = blockText.indexOf(quote, ignoreCase = false)
        if (exact >= 0) return SharedNativeReaderTextRange(exact, (exact + quote.length).coerceAtMost(textLength))
        val relaxed = blockText.indexOf(quote, ignoreCase = true)
        if (relaxed >= 0) return SharedNativeReaderTextRange(relaxed, (relaxed + quote.length).coerceAtMost(textLength))
    }
    return null
}

private fun String.sharedNativeCfiPointOrNull(): SharedNativeCfiPoint? {
    val separator = lastIndexOf(':')
    if (separator <= 0 || separator == lastIndex) return null
    val path = substring(0, separator).takeIf { it.startsWith("/") } ?: return null
    val offset = substring(separator + 1).toIntOrNull() ?: return null
    return SharedNativeCfiPoint(path, offset)
}

private fun sharedNativeCfiPathsEquivalent(first: String, second: String): Boolean {
    val firstParts = first.split('/').filter { it.isNotEmpty() }
    val secondParts = second.split('/').filter { it.isNotEmpty() }
    if (firstParts == secondParts) return true
    return firstParts.size == secondParts.size &&
        firstParts.isNotEmpty() &&
        firstParts.drop(1) == secondParts.drop(1)
}

private fun sharedNativeCfiPathStrictlyBetween(candidate: String, start: String, end: String): Boolean {
    val candidateParts = candidate.sharedNativeCfiNumericPathParts() ?: return false
    val startParts = start.sharedNativeCfiNumericPathParts() ?: return false
    val endParts = end.sharedNativeCfiNumericPathParts() ?: return false
    return sharedNativeCompareCfiPathParts(candidateParts, startParts) > 0 &&
        sharedNativeCompareCfiPathParts(candidateParts, endParts) < 0
}

private fun String.sharedNativeCfiNumericPathParts(): List<Int>? {
    val parts = split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

private fun sharedNativeCompareCfiPathParts(first: List<Int>, second: List<Int>): Int {
    val length = minOf(first.size, second.size)
    for (index in 0 until length) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
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
        id = "native-${selection.chapterIndex}-${selection.startPageIndex}-${selection.startBlockIndex}-${selection.startLocalOffset}-${selection.endPageIndex}-${selection.endBlockIndex}-${selection.endLocalOffset}-${color.id}",
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
