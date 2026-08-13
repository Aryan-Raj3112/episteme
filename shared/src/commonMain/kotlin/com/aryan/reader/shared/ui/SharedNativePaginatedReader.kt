package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

internal enum class SharedPaginatedTapAction {
    PREVIOUS_PAGE,
    TOGGLE_CHROME,
    NEXT_PAGE
}

internal fun sharedPaginatedTapAction(
    horizontalFraction: Float,
    tapToNavigateEnabled: Boolean,
    rightToLeftPagination: Boolean
): SharedPaginatedTapAction {
    if (!tapToNavigateEnabled) return SharedPaginatedTapAction.TOGGLE_CHROME
    return when {
        horizontalFraction < 0.25f -> if (rightToLeftPagination) {
            SharedPaginatedTapAction.NEXT_PAGE
        } else {
            SharedPaginatedTapAction.PREVIOUS_PAGE
        }
        horizontalFraction > 0.75f -> if (rightToLeftPagination) {
            SharedPaginatedTapAction.PREVIOUS_PAGE
        } else {
            SharedPaginatedTapAction.NEXT_PAGE
        }
        else -> SharedPaginatedTapAction.TOGGLE_CHROME
    }
}

internal fun sharedPaginatedTransitionDirection(
    initialPageIndex: Int,
    targetPageIndex: Int,
    rightToLeftPagination: Boolean
): Int {
    if (initialPageIndex == targetPageIndex) return 0
    val logicalDirection = if (targetPageIndex > initialPageIndex) 1 else -1
    return if (rightToLeftPagination) -logicalDirection else logicalDirection
}

enum class SharedNativeReaderSelectionAction {
    DEFINE,
    TRANSLATE,
    SEARCH,
    SPEAK,
    NOTE
}

internal fun SharedNativeReaderSelectionAction.externalLookupActionOrNull(): ReaderExternalLookupAction? {
    return when (this) {
        SharedNativeReaderSelectionAction.DEFINE -> ReaderExternalLookupAction.DICTIONARY
        SharedNativeReaderSelectionAction.TRANSLATE -> ReaderExternalLookupAction.TRANSLATE
        SharedNativeReaderSelectionAction.SEARCH -> ReaderExternalLookupAction.SEARCH
        SharedNativeReaderSelectionAction.SPEAK,
        SharedNativeReaderSelectionAction.NOTE -> null
    }
}

data class SharedNativeReaderLinkClick(
    val href: String,
    val chapterIndex: Int?,
    val text: String?
)

internal enum class SharedNativeVerticalFlowItemKind {
    CHAPTER_GAP,
    BLOCK,
    TEXT_PAGE,
    EMPTY_CHAPTER
}

internal data class SharedNativeVerticalFlowItem(
    val key: String,
    val kind: SharedNativeVerticalFlowItemKind,
    val page: ReaderPage,
    val block: SemanticBlock? = null
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

internal data class SharedNativeSelectionBlockKey(
    val pageIndex: Int,
    val blockIndex: Int,
    val blockCharOffset: Int
) {
    val stableKey: String get() = "$pageIndex:$blockIndex:$blockCharOffset"
}

internal data class SharedNativeTextBlockDescriptor(
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

internal data class SharedNativeTextLayoutInfo(
    val descriptor: SharedNativeTextBlockDescriptor,
    val layout: TextLayoutResult,
    val coordinates: LayoutCoordinates
)

internal data class SharedNativeTextPosition(
    val descriptor: SharedNativeTextBlockDescriptor,
    val localOffset: Int
)

internal enum class SharedNativeSelectionHandle {
    START,
    END
}

internal object SharedNativeSelectionVectorIcons {
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
    onSelectionAction: (SharedNativeReaderSelectionAction, String, ReaderLocator?) -> Unit = { _, _, _ -> },
    onOpenHighlightPaletteManager: () -> Unit = {},
    onHighlightCreated: (UserHighlight) -> Unit = {},
    onHighlightSelected: (String) -> Unit = {},
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit = {},
    onReaderTap: () -> Unit = {},
    onReaderHorizontalTap: ((Float) -> Unit)? = null,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)? = null
) {
    val visiblePages = renderPlan.visiblePages
    val logicalFirstPage = remember(visiblePages) {
        visiblePages.minByOrNull { it.pageIndex }
    }
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
    val readerDensity = LocalDensity.current
    LaunchedEffect(visiblePageIndices) {
        val selection = activeSelection
        if (selection != null && selection.pageIndex !in visiblePageIndices) {
            updateActiveSelection(null)
        }
    }
    LaunchedEffect(logicalFirstPage?.pageIndex, renderPlan.navigationTarget.requestId) {
        logicalFirstPage?.let { page ->
            onVisiblePageChanged(
                page.pageIndex,
                renderPlan.navigationTarget.locator ?: page.toNativeReaderLocator()
            )
        }
    }

    if (visiblePages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(readerString("desktop_no_page_content", "No page content"), color = renderPlan.foreground.copy(alpha = 0.68f))
        }
        return
    }

    val selectionHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Box(
        modifier = modifier
            .readerHorizontalTapPointerInput { horizontalFraction ->
                if (activeSelection == null) {
                    onReaderHorizontalTap?.invoke(horizontalFraction) ?: onReaderTap()
                }
            }
            .onGloballyPositioned { readerCoordinates = it }
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
            val pageOuterWidth = if (renderPlan.settings.usesNativePaginatedSpreadPageSlot()) {
                val availablePageOuterWidth = ((maxWidth - pageGap).coerceAtLeast(1.dp)) / 2f
                val availableContentWidth = (availablePageOuterWidth - (horizontalMargin * 2f)).coerceAtLeast(1.dp)
                minOf(availableContentWidth, configuredContentWidth) + (horizontalMargin * 2f)
            } else {
                val availableContentWidth = (maxWidth - (horizontalMargin * 2f)).coerceAtLeast(1.dp)
                minOf(availableContentWidth, configuredContentWidth) + (horizontalMargin * 2f)
            }
            val pageRenderGeometry = with(readerDensity) {
                val contentWidth = (pageOuterWidth - (horizontalMargin * 2f)).coerceAtLeast(1.dp)
                val contentHeight = (maxHeight - (renderPlan.settings.resolvedVerticalMargin.dp * 2f)).coerceAtLeast(1.dp)
                SharedNativePageRenderGeometry(
                    readerWidthPx = maxWidth.toPx().roundToInt(),
                    readerHeightPx = maxHeight.toPx().roundToInt(),
                    pageOuterWidthPx = pageOuterWidth.toPx().roundToInt(),
                    pageContentWidthPx = contentWidth.toPx().roundToInt(),
                    pageContentHeightPx = contentHeight.toPx().roundToInt(),
                    pageGapPx = pageGap.toPx().roundToInt(),
                    horizontalMarginPx = horizontalMargin.toPx().roundToInt(),
                    verticalMarginPx = renderPlan.settings.resolvedVerticalMargin.dp.toPx().roundToInt(),
                    configuredPageWidthPx = configuredContentWidth.toPx().roundToInt(),
                    visiblePageCount = visiblePages.size,
                    spreadMode = renderPlan.settings.pageSpreadMode.name
                )
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
                        renderGeometry = pageRenderGeometry,
                        onSelectionChange = ::updateActiveSelection,
                        onSelectionGestureActiveChange = { selectionGestureActive = it },
                        onHighlightSelected = onHighlightSelected,
                        onLinkClicked = onLinkClicked,
                        onReaderTap = onReaderTap,
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
                val highlightPalette = renderPlan.highlightPalette.sanitized().colors
                SharedNativeSelectionMenu(
                    selection = selection,
                    highlightPalette = highlightPalette,
                    enabledSelectionActions = enabledSelectionActions,
                    background = renderPlan.background,
                    foreground = renderPlan.foreground,
                    onCopy = {
                        onCopyText(selection.text)
                        updateActiveSelection(null)
                    },
                    onSelectionAction = { action ->
                        onSelectionAction(action, selection.text, selection.toReaderLocator())
                        updateActiveSelection(null)
                    },
                    onHighlight = { color, style ->
                        val highlight = sharedNativeReaderHighlightForSelection(selection, color, style)
                        logSharedReaderDiagnostic(DesktopHighlightMapLogTag) {
                            "native_highlight_create_click id=\"${highlight.id.sharedNativeLogPreview(64)}\" " +
                                "color=${color.id} style=${style.id} chapter=${highlight.chapterIndex} page=${highlight.locator.pageIndex} " +
                                "offsets=${highlight.locator.startOffset}..${highlight.locator.endOffset} " +
                                "block=${highlight.locator.blockIndex} char=${highlight.locator.charOffset} " +
                                "cfi=\"${highlight.cfi.sharedNativeLogPreview(160)}\" text=\"${highlight.text.sharedNativeLogPreview(120)}\""
                        }
                        onHighlightCreated(highlight)
                        updateActiveSelection(null)
                    },
                    onOpenHighlightPaletteManager = onOpenHighlightPaletteManager,
                    onDismiss = { updateActiveSelection(null) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            sharedNativeSelectionMenuOffset(
                                selection = selection,
                                readerCoordinates = readerCoordinates,
                                density = readerDensity,
                                highlightPaletteSize = highlightPalette.size,
                                actionCount = enabledSelectionActions.size + 2
                            )
                        }
                )
            }
        }
    }
}

/** Lets the host screen drive the native vertical EPUB list from outside it (auto-scroll, musician gestures). */
class SharedNativeVerticalScrollController {
    private var listState: LazyListState? = null

    internal fun attach(state: LazyListState) {
        listState = state
    }

    internal fun detach() {
        listState = null
    }

    suspend fun scrollByPixels(deltaPx: Float) {
        listState?.scrollBy(deltaPx)
    }

    suspend fun scrollByViewportFraction(fraction: Float) {
        val state = listState ?: return
        val viewportHeightPx = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
        if (viewportHeightPx <= 0) return
        state.scrollBy(viewportHeightPx * fraction)
    }

    suspend fun scrollToStart() {
        listState?.scrollToItem(0)
    }

    suspend fun scrollToEnd() {
        val state = listState ?: return
        state.scrollToItem((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    }

    fun canScrollForward(): Boolean = listState?.canScrollForward ?: false
}

@Composable
fun SharedNativeVerticalReader(
    renderPlan: ReaderContentRenderPlan.NativeVerticalPages,
    readerFontFamily: FontFamily,
    searchHighlight: Color,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    modifier: Modifier = Modifier,
    enabledSelectionActions: Set<SharedNativeReaderSelectionAction> = emptySet(),
    onCopyText: (String) -> Unit = {},
    onSelectionAction: (SharedNativeReaderSelectionAction, String, ReaderLocator?) -> Unit = { _, _, _ -> },
    onOpenHighlightPaletteManager: () -> Unit = {},
    onHighlightCreated: (UserHighlight) -> Unit = {},
    onHighlightSelected: (String) -> Unit = {},
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit = {},
    onReaderTap: () -> Unit = {},
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)? = null,
    verticalScrollController: SharedNativeVerticalScrollController? = null
) {
    val flowItems = remember(renderPlan.book, renderPlan.pages) {
        buildSharedNativeVerticalFlowItems(
            book = renderPlan.book,
            pages = renderPlan.pages
        )
    }
    val listState = rememberLazyListState()
    DisposableEffect(verticalScrollController, listState) {
        verticalScrollController?.attach(listState)
        onDispose { verticalScrollController?.detach() }
    }
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
    val selectionLayouts = remember(renderPlan.navigationTarget.requestId, renderPlan.book.id) {
        mutableStateMapOf<String, SharedNativeTextLayoutInfo>()
    }
    var readerCoordinates by remember(renderPlan.navigationTarget.requestId) {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    var didInitialScroll by remember(renderPlan.book.id) {
        mutableStateOf(false)
    }
    val density = LocalDensity.current
    val selectionHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    LaunchedEffect(flowItems, renderPlan.navigationTarget.requestId) {
        if (flowItems.isEmpty()) return@LaunchedEffect
        val navigationIndex = renderPlan.navigationTarget.locator
            ?.let { locator -> flowItems.sharedNativeVerticalItemIndexForLocator(locator) }
        val initialIndex = if (!didInitialScroll) {
            flowItems.sharedNativeVerticalItemIndexForPage(renderPlan.currentPageIndex)
        } else {
            null
        }
        val targetIndex = navigationIndex ?: initialIndex
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceIn(0, flowItems.lastIndex))
        }
        didInitialScroll = true
    }

    LaunchedEffect(flowItems, listState) {
        if (flowItems.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { itemIndex ->
                val item = flowItems.getOrNull(itemIndex) ?: return@collectLatest
                onVisiblePageChanged(item.page.pageIndex, item.toNativeVerticalLocator())
            }
    }

    if (flowItems.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(readerString("desktop_no_page_content", "No page content"), color = renderPlan.foreground.copy(alpha = 0.68f))
        }
        return
    }

    Box(
        modifier = modifier
            .readerChromeTapTogglePointerInput {
                if (activeSelection == null) {
                    onReaderTap()
                }
            }
            .onGloballyPositioned { readerCoordinates = it }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(renderPlan.background)
        ) {
            val requestedHorizontalPadding = renderPlan.settings.resolvedHorizontalMargin.dp
            val requestedVerticalPadding = renderPlan.settings.resolvedVerticalMargin.dp
            val minReadableWidth = minOf(96.dp, maxWidth)
            val minReadableHeight = minOf(160.dp, maxHeight)
            val horizontalPadding = requestedHorizontalPadding.coerceAtMost(
                ((maxWidth - minReadableWidth) / 2f).coerceAtLeast(0.dp)
            )
            val verticalPadding = requestedVerticalPadding.coerceAtMost(
                ((maxHeight - minReadableHeight) / 2f).coerceAtLeast(0.dp)
            )
            val chapterBoundaryGap = (
                44f * (renderPlan.settings.resolvedVerticalMargin / 48f)
            ).coerceIn(32f, 112f).dp
            val fallbackTextAlign = renderPlan.settings.textAlign.toComposeTextAlign()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding)
            ) {
                itemsIndexed(
                    items = flowItems,
                    key = { _, item -> item.key }
                ) { _, item ->
                    when (item.kind) {
                        SharedNativeVerticalFlowItemKind.CHAPTER_GAP -> {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(chapterBoundaryGap)
                            )
                        }

                        SharedNativeVerticalFlowItemKind.EMPTY_CHAPTER -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .height(72.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = item.page.chapterTitle.ifBlank { readerString("reader_chapter", "Chapter") },
                                    color = renderPlan.foreground.copy(alpha = 0.54f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        SharedNativeVerticalFlowItemKind.TEXT_PAGE -> {
                            val page = item.page
                            val visibleHighlights = renderPlan.highlights.visibleInPage(page)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                            ) {
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
                                        fontSize = renderPlan.settings.fontSize.sp,
                                        lineHeight = (renderPlan.settings.fontSize * renderPlan.settings.lineSpacing).sp,
                                        fontFamily = readerFontFamily,
                                        fontWeight = renderPlan.settings.fontWeight.takeIf { it > 0 }?.let(::FontWeight),
                                        letterSpacing = renderPlan.settings.letterSpacing.em
                                    ).withAndroidPaginationTextMetrics(renderPlan.settings.letterSpacing),
                                    activeSelection = activeSelection,
                                    onReaderTap = onReaderTap,
                                    onSelectionChange = ::updateActiveSelection,
                                    onSelectionGestureActiveChange = { selectionGestureActive = it },
                                    onHighlightSelected = onHighlightSelected,
                                    onLinkClicked = onLinkClicked,
                                    selectionLayouts = selectionLayouts
                                )
                            }
                        }

                        SharedNativeVerticalFlowItemKind.BLOCK -> {
                            val page = item.page
                            val block = item.block
                            if (block != null) {
                                val visibleHighlights = renderPlan.highlights.visibleInPage(page)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = horizontalPadding)
                                        .background(renderPlan.background)
                                ) {
                                    SharedSemanticBlockStack(
                                        blocks = listOf(block),
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
                                        settings = renderPlan.settings,
                                        includeTrailingBottomMargin = true,
                                        onReaderTap = onReaderTap,
                                        onSelectionChange = ::updateActiveSelection,
                                        onSelectionGestureActiveChange = { selectionGestureActive = it },
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
                val highlightPalette = renderPlan.highlightPalette.sanitized().colors
                SharedNativeSelectionMenu(
                    selection = selection,
                    highlightPalette = highlightPalette,
                    enabledSelectionActions = enabledSelectionActions,
                    background = renderPlan.background,
                    foreground = renderPlan.foreground,
                    onCopy = {
                        onCopyText(selection.text)
                        updateActiveSelection(null)
                    },
                    onSelectionAction = { action ->
                        onSelectionAction(action, selection.text, selection.toReaderLocator())
                        updateActiveSelection(null)
                    },
                    onHighlight = { color, style ->
                        onHighlightCreated(sharedNativeReaderHighlightForSelection(selection, color, style))
                        updateActiveSelection(null)
                    },
                    onOpenHighlightPaletteManager = onOpenHighlightPaletteManager,
                    onDismiss = { updateActiveSelection(null) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            sharedNativeSelectionMenuOffset(
                                selection = selection,
                                readerCoordinates = readerCoordinates,
                                density = density,
                                highlightPaletteSize = highlightPalette.size,
                                actionCount = enabledSelectionActions.size + 2
                            )
                        }
                )
            }
        }
    }
}

internal fun ReaderSettings.usesNativePaginatedSpreadPageSlot(): Boolean {
    return readingMode == ReaderReadingMode.PAGINATED
}
