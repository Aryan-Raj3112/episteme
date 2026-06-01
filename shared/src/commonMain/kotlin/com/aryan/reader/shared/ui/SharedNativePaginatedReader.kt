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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
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
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubCutoffDiagnosticsTag
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
    onSelectionAction: (SharedNativeReaderSelectionAction, String, ReaderLocator?) -> Unit = { _, _, _ -> },
    onOpenHighlightPaletteManager: () -> Unit = {},
    onHighlightCreated: (UserHighlight) -> Unit = {},
    onHighlightSelected: (String) -> Unit = {},
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit = {},
    onReaderTap: () -> Unit = {},
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
    val density = LocalDensity.current
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
            val pageRenderGeometry = with(density) {
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
                    onHighlight = { color ->
                        val highlight = sharedNativeReaderHighlightForSelection(selection, color)
                        logSharedReaderDiagnostic(DesktopHighlightMapLogTag) {
                            "native_highlight_create_click id=\"${highlight.id.sharedNativeLogPreview(64)}\" " +
                                "color=${color.id} chapter=${highlight.chapterIndex} page=${highlight.locator.pageIndex} " +
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

private fun ReaderSettings.usesNativePaginatedSpreadPageSlot(): Boolean {
    return readingMode == ReaderReadingMode.PAGINATED
}

@Composable
private fun SharedNativePaginatedPage(
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
                        fontFamily = readerFontFamily
                    ).withAndroidPaginationTextMetrics(),
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
    onOpenHighlightPaletteManager: () -> Unit,
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
                .widthIn(max = 280.dp)
                .padding(bottom = 6.dp)
        ) {
            if (highlightPalette.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                .clickable { onHighlight(color) }
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

private data class SharedNativeSelectionMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun SharedNativeSelectionPaletteButton(
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
private fun SharedNativeSelectionIconButton(
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
    Text(
        text = text,
        color = color,
        modifier = modifier
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
            },
        textAlign = textAlign,
        style = style,
        onTextLayout = { textLayoutResult = it }
    )
}

private fun ReaderPage.toNativeReaderLocator(): ReaderLocator {
    val blockPosition = firstNativeLocatorBlockPosition()
    return ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        blockIndex = blockPosition?.blockIndex,
        charOffset = blockPosition?.charOffset,
        textQuote = text.replace(Regex("\\s+"), " ").trim().take(160),
        cfi = blockPosition?.androidStyleCfi() ?: "desktop:$chapterIndex:$startOffset:$endOffset"
    )
}

private data class SharedNativeLocatorBlockPosition(
    val blockIndex: Int,
    val charOffset: Int,
    val cfi: String? = null,
    val localCharOffset: Int = 0
) {
    fun androidStyleCfi(): String? {
        val base = cfi
            ?.takeIf { it.startsWith("/") }
            ?.substringBefore(':')
            ?: return null
        return "$base:${localCharOffset.coerceAtLeast(0)}"
    }
}

private fun ReaderPage.firstNativeLocatorBlockPosition(): SharedNativeLocatorBlockPosition? {
    val blocks = semanticBlocks.flattenNativeSemanticBlocks()
    val textBlock = blocks
        .filterIsInstance<SemanticTextBlock>()
        .firstOrNull { it.text.isNotBlank() }
        ?: blocks.filterIsInstance<SemanticTextBlock>().firstOrNull()
    if (textBlock != null) {
        return SharedNativeLocatorBlockPosition(
            blockIndex = textBlock.blockIndex,
            charOffset = textBlock.startCharOffsetInSource,
            cfi = textBlock.cfi,
            localCharOffset = 0
        )
    }
    val firstBlock = blocks.firstOrNull() ?: return null
    return SharedNativeLocatorBlockPosition(firstBlock.blockIndex, 0, firstBlock.cfi, 0)
}

private fun List<SemanticBlock>.flattenNativeSemanticBlocks(): List<SemanticBlock> {
    return flatMap { it.flattenNativeSemanticBlock() }
}

private fun SemanticBlock.flattenNativeSemanticBlock(): List<SemanticBlock> {
    return when (this) {
        is SemanticList -> listOf(this) + items
        is SemanticTable -> listOf(this) + rows.flatMap { row -> row.flatMap { cell -> cell.content.flattenNativeSemanticBlocks() } }
        is SemanticFlexContainer -> listOf(this) + children.flattenNativeSemanticBlocks()
        is SemanticWrappingBlock -> listOf(this, floatedImage) + paragraphsToWrap
        is SemanticImage,
        is SemanticMath,
        is SemanticSpacer,
        is SemanticTextBlock -> listOf(this)
    }
}

private fun String.toReaderAnnotatedString(
    searchQuery: String,
    searchHighlight: Color,
    chapterIndex: Int,
    pageIndex: Int,
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
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
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
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null,
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
            onReaderTap = onReaderTap,
            onSelectionChange = onSelectionChange,
            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
            onHighlightSelected = onHighlightSelected,
            onLinkClicked = onLinkClicked,
            selectionLayouts = selectionLayouts,
            imageContent = imageContent,
            layoutIndex = index,
            onTextLaidOut = onTextLaidOut,
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
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    imageContent: (@Composable (SemanticImage, Modifier) -> Unit)?,
    layoutIndex: Int? = null,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null,
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
                onReaderTap = onReaderTap,
                onSelectionChange = onSelectionChange,
                onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                onHighlightSelected = onHighlightSelected,
                onLinkClicked = onLinkClicked,
                selectionLayouts = selectionLayouts,
                onTextLaidOut = onTextLaidOut
            )
        }

        is SemanticParagraph -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onReaderTap = onReaderTap, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts, onTextLaidOut = onTextLaidOut)
        is SemanticListItem -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onReaderTap = onReaderTap, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts, onTextLaidOut = onTextLaidOut)
        is SemanticTextBlock -> SharedSemanticTextView(block, page, measuredModifier, foreground, searchQuery, searchHighlight, highlights, activeSelection, selectionHighlight, fallbackTextAlign, fallbackFontFamily, settings, onReaderTap = onReaderTap, onSelectionChange = onSelectionChange, onSelectionGestureActiveChange = onSelectionGestureActiveChange, onHighlightSelected = onHighlightSelected, onLinkClicked = onLinkClicked, selectionLayouts = selectionLayouts, onTextLaidOut = onTextLaidOut)

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
                            onReaderTap = onReaderTap,
                            onSelectionChange = onSelectionChange,
                            onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                            onHighlightSelected = onHighlightSelected,
                            onLinkClicked = onLinkClicked,
                            selectionLayouts = selectionLayouts,
                            onTextLaidOut = onTextLaidOut
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
                    onReaderTap = onReaderTap,
                    onSelectionChange = onSelectionChange,
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
                    imageContent = imageContent,
                    onTextLaidOut = onTextLaidOut
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
                    onReaderTap = onReaderTap,
                    onSelectionChange = onSelectionChange,
                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                    onHighlightSelected = onHighlightSelected,
                    onLinkClicked = onLinkClicked,
                    selectionLayouts = selectionLayouts,
                    imageContent = imageContent,
                    onTextLaidOut = onTextLaidOut
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
                                    onReaderTap = onReaderTap,
                                    onSelectionChange = onSelectionChange,
                                    onSelectionGestureActiveChange = onSelectionGestureActiveChange,
                                    onHighlightSelected = onHighlightSelected,
                                    onLinkClicked = onLinkClicked,
                                    selectionLayouts = selectionLayouts,
                                    imageContent = imageContent,
                                    onTextLaidOut = onTextLaidOut
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

private data class SharedNativePageRenderGeometry(
    val readerWidthPx: Int,
    val readerHeightPx: Int,
    val pageOuterWidthPx: Int,
    val pageContentWidthPx: Int,
    val pageContentHeightPx: Int,
    val pageGapPx: Int,
    val horizontalMarginPx: Int,
    val verticalMarginPx: Int,
    val configuredPageWidthPx: Int,
    val visiblePageCount: Int,
    val spreadMode: String
)

private data class SharedNativeTextFitLabel(
    val page: ReaderPage,
    val blockIndex: Int,
    val kind: String,
    val sourceRange: String,
    val textChars: Int
)

private data class SharedNativeTextFit(
    val pageIndex: Int,
    val chapterIndex: Int,
    val blockIndex: Int,
    val kind: String,
    val sourceRange: String,
    val textChars: Int,
    val rootTopPx: Int,
    val boxWidthPx: Int,
    val boxHeightPx: Int,
    val layoutWidthPx: Int,
    val layoutHeightPx: Int,
    val lineCount: Int,
    val lastLineIndex: Int,
    val lastLineTopPx: Int,
    val lastLineBottomPx: Int,
    val lastLineStartOffset: Int,
    val lastLineEndOffset: Int
) {
    val key: String
        get() = "$pageIndex:$blockIndex:$sourceRange:$textChars"

    val layoutRootBottomPx: Int
        get() = rootTopPx + layoutHeightPx

    val lastLineRootBottomPx: Int
        get() = rootTopPx + lastLineBottomPx

    val overflowRootBottomPx: Int
        get() = maxOf(layoutRootBottomPx, lastLineRootBottomPx)

    fun lastLineOverflowPx(contentBottomRootPx: Int): Int {
        return overflowRootBottomPx - contentBottomRootPx
    }

    fun format(contentTopPx: Int, contentBottomRootPx: Int): String {
        val textBoxTopPx = rootTopPx - contentTopPx
        val textBoxBottomPx = textBoxTopPx + boxHeightPx
        val lineTopPx = rootTopPx + lastLineTopPx - contentTopPx
        val lineBottomPx = lastLineRootBottomPx - contentTopPx
        val layoutBottomPx = layoutRootBottomPx - contentTopPx
        val overflowBottomPx = overflowRootBottomPx - contentTopPx
        return "block=$blockIndex kind=$kind textBox=${boxWidthPx}x$boxHeightPx@top=$textBoxTopPx " +
            "textBoxBottom=$textBoxBottomPx layout=${layoutWidthPx}x$layoutHeightPx " +
            "layoutBottom=$layoutBottomPx lines=$lineCount lastLine=$lastLineIndex " +
            "lineTop=$lineTopPx lineBottom=$lineBottomPx overflowBottom=$overflowBottomPx " +
            "lineOverflowPx=${lastLineOverflowPx(contentBottomRootPx)} " +
            "lineOffsets=$lastLineStartOffset..$lastLineEndOffset range=$sourceRange textChars=$textChars"
    }
}

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

private fun SharedNativeTextFitLabel.toSharedNativeTextFit(
    coordinates: LayoutCoordinates,
    layout: TextLayoutResult
): SharedNativeTextFit {
    val lastLine = layout.lineCount - 1
    return SharedNativeTextFit(
        pageIndex = page.pageIndex,
        chapterIndex = page.chapterIndex,
        blockIndex = blockIndex,
        kind = kind,
        sourceRange = sourceRange,
        textChars = textChars,
        rootTopPx = coordinates.positionInRoot().y.roundToInt(),
        boxWidthPx = coordinates.size.width,
        boxHeightPx = coordinates.size.height,
        layoutWidthPx = layout.size.width,
        layoutHeightPx = layout.size.height,
        lineCount = layout.lineCount,
        lastLineIndex = lastLine,
        lastLineTopPx = if (lastLine >= 0) layout.getLineTop(lastLine).roundToInt() else 0,
        lastLineBottomPx = if (lastLine >= 0) layout.getLineBottom(lastLine).roundToInt() else layout.size.height,
        lastLineStartOffset = if (lastLine >= 0) layout.getLineStart(lastLine) else 0,
        lastLineEndOffset = if (lastLine >= 0) layout.getLineEnd(lastLine, visibleEnd = true) else 0
    )
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

private fun SemanticBlock.sharedNativeTextFitCount(): Int {
    return when (this) {
        is SemanticTextBlock -> 1
        is SemanticList -> items.sumOf { it.sharedNativeTextFitCount() }
        is SemanticTable -> rows.sumOf { row -> row.sumOf { cell -> cell.content.sumOf { it.sharedNativeTextFitCount() } } }
        is SemanticFlexContainer -> children.sumOf { it.sharedNativeTextFitCount() }
        is SemanticWrappingBlock -> paragraphsToWrap.sumOf { it.sharedNativeTextFitCount() }
        is SemanticImage,
        is SemanticMath,
        is SemanticSpacer -> 0
    }
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

private fun UserHighlight.nativeHighlightLogKey(): String {
    val normalizedLocator = this.locator.withFallbacks(
        chapterIndex = chapterIndex,
        cfi = cfi,
        textQuote = text
    )
    val page = normalizedLocator.pageIndex?.let { it + 1 }?.toString() ?: "null"
    return "id=\"${id.sharedNativeLogPreview(48)}\"" +
        ":chapter=${normalizedLocator.chapterIndex ?: "null"}" +
        ":page=$page" +
        ":offsets=${normalizedLocator.startOffset ?: "null"}..${normalizedLocator.endOffset ?: "null"}" +
        ":block=${normalizedLocator.blockIndex ?: "null"}" +
        ":char=${normalizedLocator.charOffset ?: "null"}" +
        ":text=\"${(normalizedLocator.textQuote ?: text).sharedNativeLogPreview(64)}\""
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
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null
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
            chapterIndex = page.chapterIndex,
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
        activeSelection = activeSelection,
        onReaderTap = onReaderTap,
        onSelectionChange = onSelectionChange,
        onSelectionGestureActiveChange = onSelectionGestureActiveChange,
        onHighlightSelected = onHighlightSelected,
        onLinkClicked = onLinkClicked,
        selectionLayouts = selectionLayouts,
        onTextLaidOut = onTextLaidOut,
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
    chapterIndex: Int,
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
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                blockCfi = blockCfi,
                blockIndex = blockIndex,
                blockCharOffset = blockCharOffset,
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
        val locator = highlight.locator.withFallbacks(
            chapterIndex = highlight.chapterIndex,
            cfi = highlight.cfi,
            textQuote = highlight.text
        )
        (locator.chapterIndex ?: highlight.chapterIndex) == page.chapterIndex &&
            page.containsNativeHighlightLocator(locator, highlight.cfi)
    }
}

private fun ReaderPage.containsNativeHighlightLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
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

private fun ReaderPage.containsNativeCollapsedOffset(offset: Int): Boolean {
    return if (startOffset == endOffset) {
        offset == startOffset
    } else {
        offset >= startOffset && offset < endOffset
    }
}

private fun ReaderPage.containsNativeBlockLocator(locator: ReaderLocator): Boolean {
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

private fun ReaderPage.containsNativeSourceCfiLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
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

private fun sharedNativeCfiOffsetToAbsolute(offset: Int, blockStart: Int, textLength: Int): Int {
    val blockEnd = blockStart + textLength
    return when {
        offset in 0..textLength -> blockStart + offset
        offset in blockStart..blockEnd -> offset
        else -> blockStart + offset.coerceIn(0, textLength)
    }
}

private fun AnnotatedString.Builder.applyHighlightToTextRange(
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
            style = SpanStyle(background = highlight.color.color.copy(alpha = 0.38f)),
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

private fun logNativeHighlightMapResult(
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

private fun sharedNativeLocatorHighlightRangeInBlock(
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

private fun sharedNativeScopedOffsetToLocalOrNull(
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

private fun sharedNativeBlockLocatorHighlightRangeInBlock(
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

private fun ReaderLocator.hasSharedNativeStructuralScope(fallbackCfi: String): Boolean {
    val sourceCfi = cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi
    return blockIndex != null || sourceCfi.startsWith("/")
}

private fun UserHighlight.sharedNativeSourceCfi(): String {
    return locator.cfi?.takeIf { it.isNotBlank() } ?: cfi
}

private fun UserHighlight.hasSharedNativeMultipartCfiRange(): Boolean {
    val parts = sharedNativeSourceCfi()
        .split('|')
        .mapNotNull { it.sharedNativeCfiPointOrNull(allowMissingOffset = true) }
    if (parts.size < 2) return false
    val start = parts.first().path
    return parts.drop(1).any { !sharedNativeCfiPathsEquivalent(start, it.path) }
}

private fun UserHighlight.sharedNativeCfiTouchesBlock(blockCfi: String?): Boolean {
    val blockPath = blockCfi?.takeIf { it.startsWith("/") } ?: return false
    return sharedNativeSourceCfi()
        .split('|')
        .mapNotNull { it.sharedNativeCfiPointOrNull(allowMissingOffset = true) }
        .any { sharedNativeCfiPathsEquivalent(it.path, blockPath) }
}

private fun SharedNativeReaderTextRange.matchesSharedNativeHighlightText(
    blockText: String,
    highlightText: String
): Boolean {
    if (start !in 0..end || end > blockText.length || highlightText.isBlank()) return true
    val actual = blockText.substring(start, end).sharedNativeComparableText()
    val expected = highlightText.sharedNativeComparableText()
    if (actual.isBlank() || expected.isBlank()) return true
    return actual == expected || expected.contains(actual) || actual.contains(expected)
}

private fun sharedNativeHighlightTextRangeInBlock(
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

private fun String.nearestIndexOf(
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

private fun sharedNativeFuzzyTextRange(
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

private fun String.sharedNativeComparableText(): String {
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

internal fun sharedNativeReaderSelectionGestureKey(
    textBlockKey: String,
    text: AnnotatedString
): String = "$textBlockKey:${text.text}"

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

private fun sharedNativeSelectionMenuEstimatedHeightPx(
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

private fun sharedNativeCfiOffsetToLocal(offset: Int, textStartOffset: Int, textLength: Int): Int {
    return when {
        offset in 0..textLength -> offset
        offset in textStartOffset..(textStartOffset + textLength) -> offset - textStartOffset
        else -> offset
    }
}

private fun String.sharedNativeCfiPointOrNull(allowMissingOffset: Boolean = false): SharedNativeCfiPoint? {
    val separator = lastIndexOf(':')
    if (separator <= 0 || separator == lastIndex) {
        if (!allowMissingOffset) return null
        return SharedNativeCfiPoint(takeIf { it.startsWith("/") } ?: return null, 0)
    }
    val path = substring(0, separator).takeIf { it.startsWith("/") } ?: return null
    val offset = substring(separator + 1).toIntOrNull() ?: return null
    return SharedNativeCfiPoint(path, offset)
}

private fun sharedNativeCfiPathsEquivalent(first: String, second: String): Boolean {
    if (first == second || first.startsWith("$second/") || second.startsWith("$first/")) return true
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
    val locator = selection.toReaderLocator()
    return UserHighlight(
        id = "native-${selection.chapterIndex}-${selection.startPageIndex}-${selection.startBlockIndex}-${selection.startLocalOffset}-${selection.endPageIndex}-${selection.endBlockIndex}-${selection.endLocalOffset}-${color.id}",
        cfi = selection.cfi,
        text = selection.text,
        color = color,
        chapterIndex = selection.chapterIndex,
        locator = locator
    )
}

private fun SharedNativeReaderTextSelection.toReaderLocator(): ReaderLocator {
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
        SharedReaderTextAlign.RIGHT -> TextAlign.Right
        SharedReaderTextAlign.JUSTIFY -> TextAlign.Justify
        SharedReaderTextAlign.CENTER -> TextAlign.Center
    }
}

private const val ReaderNativeAnnotationUrl = "URL"
private const val ReaderNativeAnnotationHighlight = "HIGHLIGHT"
private const val DesktopHighlightMapLogTag = "EpistemeDesktopHighlightMap"
private const val EpubPageFitLogTag = "EpistemeEpubPageFit"
private const val EpubCutoffLogTag = SharedEpubCutoffDiagnosticsTag
private const val EpubPageFitTailBlockCount = 4
private const val SharedNativeListItemMarkerAreaWidthDp = 32
private const val SharedNativeListItemMarkerEndPaddingDp = 8
