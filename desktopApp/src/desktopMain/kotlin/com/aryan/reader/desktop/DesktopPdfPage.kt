package com.aryan.reader.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfSearchEngine
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.ui.SharedPdfAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfEmbeddedAnnotationOverlay
import com.aryan.reader.shared.ui.SharedPdfInlineTextEditorOverlay
import com.aryan.reader.shared.ui.SharedPdfPageNumberOverlay
import com.aryan.reader.shared.ui.SharedPdfRichTextLayer
import com.aryan.reader.shared.ui.SharedPdfTextBoxEditorOverlay
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.sharedPdfEmbeddedHitTest
import com.aryan.reader.shared.ui.sharedPdfHitTest
import com.aryan.reader.shared.ui.toSharedPdfPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val DesktopVerticalPdfPageTurnAnimationMillis = 140

@Composable
internal fun DesktopVerticalPdfPage(
    document: DesktopPdfDocument,
    pageIndex: Int,
    scale: Float,
    zoomSpec: PdfZoomSpec,
    annotations: List<SharedPdfAnnotation>,
    searchResults: List<SharedPdfSearchResult>,
    activeSearchIndex: Int,
    searchHighlightMode: SearchHighlightMode,
    activeTtsChunk: ReaderTtsChunk?,
    searchQuery: String,
    isTextSelectionMode: Boolean,
    selectedAnnotationId: String?,
    selectedEmbeddedAnnotationId: String?,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    highlighterPalette: List<Int>,
    strokeWidth: Float,
    isHighlighterSnapEnabled: Boolean,
    activeTextDraft: SharedPdfTextDraft?,
    richTextController: SharedPdfRichTextController,
    isRichTextMode: Boolean,
    readerAiFeaturesAvailable: Boolean,
    cloudTtsAvailable: Boolean,
    externalLookupAvailable: Boolean,
    themeStyle: DesktopPdfThemeStyle,
    shouldRender: Boolean,
    zoomPreview: DesktopPdfZoomPreview?,
    zoomPreviewAnchorPageRootOffset: Offset? = null,
    zoomPreviewScrollBounds: DesktopPdfZoomScrollBounds? = null,
    zoomViewportRootOffset: Offset,
    showPageNumberOverlay: Boolean = true,
    onSelectPage: (Int) -> Unit,
    onCopySelection: (DesktopPdfTextSelection) -> Unit,
    onHighlightSelection: (Int, DesktopPdfTextSelection, IntSize, Int) -> Unit,
    onExternalSearchSelection: (DesktopPdfTextSelection) -> Unit,
    onHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit,
    onDefineSelection: (DesktopPdfTextSelection) -> Unit,
    onSpeakSelection: (DesktopPdfTextSelection) -> Unit,
    onEmbeddedAnnotationSelected: (SharedPdfEmbeddedAnnotation) -> Unit,
    onAnnotationSelected: (SharedPdfAnnotation?) -> Unit,
    onLinkActivated: (DesktopPdfLinkTarget) -> Unit,
    onAnnotationAdded: (SharedPdfAnnotation) -> Unit,
    onAnnotationUpdated: (SharedPdfAnnotation) -> Unit,
    onAnnotationsChanged: (List<SharedPdfAnnotation>) -> Unit,
    onTextAnnotationSelected: (SharedPdfAnnotation) -> Unit,
    onTextDraftStarted: (Int, Offset, IntSize) -> Unit,
    onTextDraftChanged: (String, IntSize) -> Unit,
    onTextDraftBoundsChanged: (PdfPageBounds) -> Unit,
    onPan: (Offset) -> Unit,
    onPageSizeChanged: (Int, IntSize) -> Unit = { _, _ -> },
    onPagePositioned: (Int, Offset) -> Unit
) {
    val documentHandleId = document.handleId
    val density = LocalDensity.current
    var renderedPage by remember(documentHandleId) { mutableStateOf<DesktopPdfPageRender?>(null) }
    var renderedPageIndex by remember(documentHandleId) { mutableStateOf<Int?>(null) }
    var renderedPageScale by remember(documentHandleId) { mutableStateOf<Float?>(null) }
    var renderError by remember(documentHandleId) { mutableStateOf<String?>(null) }
    var isRendering by remember(documentHandleId) { mutableStateOf(true) }
    var pageCanvasSize by remember(documentHandleId, pageIndex) { mutableStateOf(IntSize.Zero) }
    var pageRootOffset by remember(documentHandleId, pageIndex) { mutableStateOf(Offset.Zero) }
    var selectionStartIndex by remember(documentHandleId, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionEndIndex by remember(documentHandleId, pageIndex) { mutableStateOf<Int?>(null) }
    var selectionStartHit by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var selectionEndHit by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfCharHit?>(null) }
    var textSelection by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfTextSelection?>(null) }
    var selectionMenuOffset by remember(documentHandleId, pageIndex) { mutableStateOf<Offset?>(null) }
    var activeSelectionHandle by remember(documentHandleId, pageIndex) { mutableStateOf<DesktopPdfSelectionHandle?>(null) }
    var activeStroke by remember(documentHandleId, pageIndex, selectedTool) { mutableStateOf<List<PdfPagePoint>>(emptyList()) }
    var eraserPosition by remember(documentHandleId, pageIndex, selectedTool) { mutableStateOf<Offset?>(null) }
    val currentTextSelection by rememberUpdatedState(textSelection)
    val currentAnnotations by rememberUpdatedState(annotations)

    fun clearSelection() {
        selectionStartIndex = null
        selectionEndIndex = null
        selectionStartHit = null
        selectionEndHit = null
        textSelection = null
        selectionMenuOffset = null
        activeSelectionHandle = null
    }

    fun clearInteractionState() {
        clearSelection()
        activeStroke = emptyList()
        eraserPosition = null
    }
    val failedRenderMessage = readerString("desktop_failed_render_page", "Failed to render page.")

    LaunchedEffect(documentHandleId, pageIndex, scale, shouldRender) {
        if (!shouldRender) {
            logPdfZoomSettle {
                "item_render_skip page=${pageIndex + 1} reason=outside_window scale=${scale.formatLogFloat()}"
            }
            renderedPage = null
            renderedPageIndex = null
            renderedPageScale = null
            renderError = null
            isRendering = false
            clearInteractionState()
            return@LaunchedEffect
        }
        val hasPageRender = renderedPage != null && desktopPdfRenderBelongsToPage(renderedPageIndex, pageIndex)
        logPdfZoomSettle {
            "item_render_effect page=${pageIndex + 1} scale=${scale.formatLogFloat()} shouldRender=$shouldRender " +
                "hasRender=$hasPageRender renderedPage=${renderedPageIndex?.plus(1) ?: "none"} " +
                "renderScale=${renderedPageScale?.formatLogFloat() ?: "none"}"
        }
        if (!hasPageRender) {
            renderedPage = null
            renderedPageIndex = null
            renderedPageScale = null
            isRendering = true
        }
        renderError = null
        val pageSize = document.pageSizes.getOrNull(pageIndex)
        if (pageSize == null) {
            renderedPage = null
            renderedPageIndex = null
            renderedPageScale = null
            renderError = failedRenderMessage
            isRendering = false
            return@LaunchedEffect
        }
        val safeScale = zoomSpec.safeRenderScale(pageSize.width, pageSize.height, scale)
        if (hasPageRender && !desktopPdfRenderScaleNeedsUpgrade(renderedPageScale, safeScale)) {
            logPdfZoomSettle {
                "item_render_skip page=${pageIndex + 1} reason=no_scale_upgrade " +
                    "safeScale=${safeScale.formatLogFloat()} existingScale=${renderedPageScale?.formatLogFloat() ?: "none"}"
            }
            isRendering = false
            return@LaunchedEffect
        }
        logPdfZoomSettle {
            "item_render_scheduled page=${pageIndex + 1} safeScale=${safeScale.formatLogFloat()} " +
                "delayMs=${if (hasPageRender) DesktopPdfZoomRenderDebounceMillis else 45L} hasRender=$hasPageRender"
        }
        delay(if (hasPageRender) DesktopPdfZoomRenderDebounceMillis else 45L)
        isRendering = true
        val renderStartedAt = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            runCatching { DesktopPdfium.renderPage(document, pageIndex, safeScale) }
        }
        val renderElapsedMs = System.currentTimeMillis() - renderStartedAt
        result.getOrNull()?.let {
            renderedPage = it
            renderedPageIndex = pageIndex
            renderedPageScale = safeScale
        }
        val renderedCurrentPage = renderedPage != null && desktopPdfRenderBelongsToPage(renderedPageIndex, pageIndex)
        renderError = result.exceptionOrNull()?.message
            ?: if (!renderedCurrentPage && renderedPage == null) failedRenderMessage else null
        isRendering = false
        logPdfZoomSettle {
            "item_render_end page=${pageIndex + 1} safeScale=${safeScale.formatLogFloat()} " +
                "elapsedMs=$renderElapsedMs success=${result.isSuccess} bitmap=${renderedPage?.width ?: 0}x${renderedPage?.height ?: 0} " +
                "canvas=${pageCanvasSize.formatLogSize()} root=${pageRootOffset.formatLogOffset()}"
        }
    }

    LaunchedEffect(isTextSelectionMode) {
        if (!isTextSelectionMode) {
            clearSelection()
        } else {
            activeStroke = emptyList()
            eraserPosition = null
        }
    }

    LaunchedEffect(selectedTool) {
        activeStroke = emptyList()
        eraserPosition = null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pageSize = document.pageSizes.getOrNull(pageIndex)
        val displayPageIndex = renderedPageIndex ?: pageIndex
        val displayPageIsCurrent = displayPageIndex == pageIndex
        val placeholderScale = zoomSpec.clamp(scale)
        val placeholderWidthDp = with(density) { ((pageSize?.width ?: 612f) * placeholderScale).toDp() }
        val placeholderHeightDp = with(density) { ((pageSize?.height ?: 792f) * placeholderScale).toDp() }
        val renderedPageWidth = renderedPage?.width ?: 0
        val renderedPageHeight = renderedPage?.height ?: 0
        val pageRenderScale = if (pageSize != null && pageSize.width > 0f && renderedPageWidth > 0) {
            renderedPageWidth / pageSize.width
        } else {
            placeholderScale
        }
        val pageEmbeddedAnnotations = remember(document.embeddedAnnotations, pageIndex) {
            document.embeddedAnnotations.filter { it.pageIndex == pageIndex }
        }

        Box(
            modifier = Modifier
                .size(placeholderWidthDp, placeholderHeightDp)
                .onGloballyPositioned { coordinates ->
                    val rootOffset = coordinates.positionInRoot()
                    if (rootOffset != pageRootOffset) {
                        logPdfZoomSettle {
                            "item_layout page=${pageIndex + 1} prevRoot=${pageRootOffset.formatLogOffset()} " +
                                "nextRoot=${rootOffset.formatLogOffset()} scale=${scale.formatLogFloat()} " +
                                "preview=${zoomPreview != null} canvas=${pageCanvasSize.formatLogSize()}"
                        }
                    }
                    pageRootOffset = rootOffset
                    onPagePositioned(pageIndex, rootOffset)
                }
                .onSizeChanged { size ->
                    if (pageCanvasSize != size) {
                        logPdfZoomSettle {
                            "item_size page=${pageIndex + 1} prev=${pageCanvasSize.formatLogSize()} " +
                                "next=${size.formatLogSize()} scale=${scale.formatLogFloat()} " +
                                "preview=${zoomPreview != null} renderScale=${pageRenderScale.formatLogFloat()}"
                        }
                    }
                    pageCanvasSize = size
                    onPageSizeChanged(pageIndex, size)
                }
                .desktopPdfDocumentZoomPreviewLayer(
                    preview = zoomPreview,
                    currentZoom = scale,
                    viewportRootOffset = zoomViewportRootOffset,
                    pageRootOffset = pageRootOffset,
                    anchorPageRootOffset = zoomPreviewAnchorPageRootOffset,
                    scrollBounds = zoomPreviewScrollBounds
                )
                .background(themeStyle.pageBackgroundColor, RoundedCornerShape(2.dp))
                .pointerInput(
                    pageIndex,
                    displayPageIsCurrent,
                    pageCanvasSize,
                    isTextSelectionMode,
                    selectedTool,
                    isRichTextMode
                ) {
                    if (!displayPageIsCurrent || isRichTextMode) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val point = event.changes.firstOrNull()?.position ?: continue
                            if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                if (isTextSelectionMode) {
                                    logPdfChromeTap {
                                        "page_press source=vertical_page page=${pageIndex + 1} " +
                                            "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                            "consumedBefore=${event.changes.any { it.isConsumed }} " +
                                            "selectionActive=${currentTextSelection != null} " +
                                            "selectionMenuOpen=${selectionMenuOffset != null} " +
                                            "selectedTool=$selectedTool richText=$isRichTextMode"
                                    }
                                }
                                val highlightHit = if (selectedTool != PdfInkTool.TEXT && selectedTool != PdfInkTool.ERASER) {
                                    currentAnnotations.asReversed().firstOrNull {
                                        it.isDesktopTextSelectionHighlight &&
                                            it.pageIndex == pageIndex &&
                                            it.sharedPdfHitTest(point, pageCanvasSize)
                                    }
                                } else {
                                    null
                                }
                                if (highlightHit != null) {
                                    logPdfChromeTap {
                                        "page_press_consume source=vertical_page page=${pageIndex + 1} " +
                                            "reason=text_selection_highlight annotation=${highlightHit.id}"
                                    }
                                    onSelectPage(pageIndex)
                                    onAnnotationSelected(highlightHit)
                                    clearInteractionState()
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                if (selectedTool != PdfInkTool.TEXT) {
                                    val linkTarget = document.linkAt(pageIndex, point, pageCanvasSize)
                                    if (linkTarget != null) {
                                        logPdfChromeTap {
                                            "page_press_consume source=vertical_page page=${pageIndex + 1} " +
                                                "reason=link target=${linkTarget.formatLogTarget()}"
                                        }
                                        logPdfLink(
                                            "tap_hit mode=vertical page=${pageIndex + 1} " +
                                                "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                                "textSelection=$isTextSelectionMode target=${linkTarget.formatLogTarget()}"
                                        )
                                        onSelectPage(pageIndex)
                                        onLinkActivated(linkTarget)
                                        clearInteractionState()
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }
                                }
                                val embeddedHit = pageEmbeddedAnnotations.findLast {
                                    it.sharedPdfEmbeddedHitTest(point, pageCanvasSize)
                                }
                                if (embeddedHit != null) {
                                    logPdfChromeTap {
                                        "page_press_consume source=vertical_page page=${pageIndex + 1} " +
                                            "reason=embedded_annotation annotation=${embeddedHit.id}"
                                    }
                                    onSelectPage(pageIndex)
                                    onEmbeddedAnnotationSelected(embeddedHit)
                                    clearInteractionState()
                                    event.changes.forEach { it.consume() }
                                } else if (
                                    currentTextSelection != null &&
                                    selectionMenuOffset == null
                                ) {
                                    logPdfChromeTap {
                                        "page_press_passthrough source=vertical_page page=${pageIndex + 1} " +
                                            "action=clear_selection consumed=false"
                                    }
                                    clearSelection()
                                } else if (isTextSelectionMode) {
                                    logPdfChromeTap {
                                        "page_press_passthrough source=vertical_page page=${pageIndex + 1} " +
                                            "action=none consumed=false"
                                    }
                                }
                            } else if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val selection = currentTextSelection
                                if (selection != null) {
                                    onSelectPage(pageIndex)
                                    selectionMenuOffset = point
                                    logPdfSelection(
                                        "menu_open page=${pageIndex + 1} " +
                                            "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                            "range=${selection.startIndex}..${selection.endIndex} " +
                                            "chars=${selection.text.length}"
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
                .pointerInput(pageIndex, displayPageIsCurrent, pageCanvasSize, isTextSelectionMode, isRichTextMode) {
                    if (!displayPageIsCurrent || isRichTextMode || !isTextSelectionMode) return@pointerInput
                    detectDesktopPdfTextSelectionLongPress(
                        source = "vertical_page",
                        pageIndex = pageIndex
                    ) { point ->
                        val selection = document.wordSelectionAt(pageIndex, point, pageCanvasSize)
                        logPdfChromeTap {
                            "long_press_selection source=vertical_page page=${pageIndex + 1} " +
                                "selectionFound=${selection != null} " +
                                "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()}"
                        }
                        if (selection != null) {
                            onSelectPage(pageIndex)
                            selectionStartIndex = null
                            selectionEndIndex = null
                            selectionStartHit = null
                            selectionEndHit = null
                            activeSelectionHandle = null
                            textSelection = selection
                            selectionMenuOffset = selection.menuAnchor(pageCanvasSize, point)
                            logPdfSelection(
                                "long_press page=${pageIndex + 1} " +
                                    "x=${point.x.formatLogFloat()} y=${point.y.formatLogFloat()} " +
                                    "range=${selection.startIndex}..${selection.endIndex} " +
                                    "chars=${selection.text.length} " +
                                    "text=\"${selection.text.logPreview()}\""
                            )
                        }
                    }
                }
                .pointerInput(pageIndex, displayPageIsCurrent, selectedTool, isTextSelectionMode, isRichTextMode) {
                    if (!displayPageIsCurrent || isRichTextMode || isTextSelectionMode || selectedTool != PdfInkTool.NONE) {
                        return@pointerInput
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                        val pointerId = down.id
                        var dragStarted = false
                        var dragDistance = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: return@awaitEachGesture
                            if (change.changedToUp()) {
                                return@awaitEachGesture
                            }
                            if (!change.positionChanged()) continue
                            val delta = change.positionChange()
                            if (!dragStarted) {
                                dragDistance += delta.getDistance()
                                if (dragDistance <= viewConfiguration.touchSlop) {
                                    continue
                                }
                                dragStarted = true
                                change.consume()
                                continue
                            }
                            onPan(delta)
                            change.consume()
                        }
                    }
                }
                .pointerInput(
                    pageIndex,
                    isTextSelectionMode,
                    selectedTool,
                    selectedColor,
                    strokeWidth,
                    isHighlighterSnapEnabled,
                    activeTextDraft?.id,
                    isRichTextMode,
                    pageCanvasSize,
                    renderedPageWidth,
                    renderedPageHeight,
                    displayPageIsCurrent
                ) {
                    if (displayPageIsCurrent && renderedPageWidth > 0 && renderedPageHeight > 0) {
                        if (isRichTextMode) return@pointerInput
                        if (isTextSelectionMode) {
                            var latestSelectionDragPoint: Offset? = null
                            var lastSelectionPreviewAt = 0L
                            detectDragGestures(
                                onDragStart = { start ->
                                    latestSelectionDragPoint = start
                                    lastSelectionPreviewAt = 0L
                                    onSelectPage(pageIndex)
                                    activeStroke = emptyList()
                                    selectionMenuOffset = null
                                    val existingSelection = textSelection
                                    val handle = existingSelection?.handleAt(start, pageCanvasSize)
                                    activeSelectionHandle = handle
                                    val hit = document.charHitAt(pageIndex, start, pageCanvasSize)
                                    if (handle != null && existingSelection != null) {
                                        selectionStartHit = null
                                        selectionStartIndex = when (handle) {
                                            DesktopPdfSelectionHandle.START -> existingSelection.endIndex
                                            DesktopPdfSelectionHandle.END -> existingSelection.startIndex
                                        }
                                        selectionEndHit = hit
                                        selectionEndIndex = hit?.index ?: when (handle) {
                                            DesktopPdfSelectionHandle.START -> existingSelection.startIndex
                                            DesktopPdfSelectionHandle.END -> existingSelection.endIndex
                                        }
                                    } else {
                                        selectionStartHit = hit
                                        selectionStartIndex = hit?.index
                                        selectionEndHit = null
                                        selectionEndIndex = null
                                        textSelection = null
                                    }
                                    logPdfSelection(
                                        "drag_start page=${pageIndex + 1} " +
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
                                            "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                            "handle=${handle?.name ?: "none"} " +
                                            hit.formatLogHit("start")
                                    )
                                },
                                onDrag = { change, _ ->
                                    latestSelectionDragPoint = change.position
                                    val now = System.currentTimeMillis()
                                    if (lastSelectionPreviewAt == 0L ||
                                        now - lastSelectionPreviewAt >= DesktopPdfSelectionPreviewThrottleMillis
                                    ) {
                                        lastSelectionPreviewAt = now
                                        val startIndex = selectionStartIndex
                                        val hit = document.charHitAt(pageIndex, change.position, pageCanvasSize)
                                        selectionEndHit = hit
                                        val endIndex = hit?.index
                                        val previousEndIndex = selectionEndIndex
                                        selectionEndIndex = endIndex
                                        if (endIndex != previousEndIndex || textSelection == null) {
                                            textSelection = if (startIndex != null && endIndex != null) {
                                                document.selectionPreviewBetweenIndexes(
                                                    pageIndex = pageIndex,
                                                    startIndex = startIndex,
                                                    endIndex = endIndex,
                                                    canvasSize = pageCanvasSize
                                                )
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    val finalHit = latestSelectionDragPoint
                                        ?.let { document.charHitAt(pageIndex, it, pageCanvasSize) }
                                        ?: selectionEndHit
                                    if (finalHit != null) {
                                        selectionEndHit = finalHit
                                        selectionEndIndex = finalHit.index
                                    }
                                    val startIndex = selectionStartIndex
                                    val endIndex = selectionEndIndex
                                    val selection = if (startIndex != null && endIndex != null) {
                                        document.selectionBetweenIndexes(
                                            pageIndex = pageIndex,
                                            startIndex = startIndex,
                                            endIndex = endIndex,
                                            canvasSize = pageCanvasSize,
                                            useNativeBounds = true
                                        )
                                    } else {
                                        textSelection?.takeIf { it.text.isNotBlank() }
                                    }
                                    textSelection = selection
                                    selectionMenuOffset = selection?.menuAnchor(
                                        pageCanvasSize,
                                        finalHit?.point ?: selectionEndHit?.point ?: selectionStartHit?.point
                                    )
                                    logPdfSelection(
                                        "drag_end page=${pageIndex + 1} " +
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
                                            "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                            selectionStartHit.formatLogHit("start") + " " +
                                            selectionEndHit.formatLogHit("end") + " " +
                                            "range=${selection?.startIndex}..${selection?.endIndex} " +
                                            "chars=${selection?.text?.length ?: 0} " +
                                            "lines=${selection?.lineBounds?.size ?: 0} " +
                                            "text=\"${selection?.text.orEmpty().logPreview()}\""
                                    )
                                    selectionStartIndex = null
                                    selectionEndIndex = null
                                    selectionStartHit = null
                                    selectionEndHit = null
                                    activeSelectionHandle = null
                                    latestSelectionDragPoint = null
                                    lastSelectionPreviewAt = 0L
                                },
                                onDragCancel = {
                                    logPdfSelection(
                                        "drag_cancel page=${pageIndex + 1} " +
                                            "canvas=${pageCanvasSize.formatLogSize()} bitmap=${renderedPageWidth}x$renderedPageHeight " +
                                            "requestedScale=${scale.formatLogFloat()} renderScale=${pageRenderScale.formatLogFloat()} " +
                                            selectionStartHit.formatLogHit("start") + " " +
                                            selectionEndHit.formatLogHit("end")
                                    )
                                    selectionStartIndex = null
                                    selectionEndIndex = null
                                    selectionStartHit = null
                                    selectionEndHit = null
                                    activeSelectionHandle = null
                                    latestSelectionDragPoint = null
                                    lastSelectionPreviewAt = 0L
                                }
                            )
                        } else if (selectedTool == PdfInkTool.TEXT) {
                            detectTapGestures(
                                onTap = { start ->
                                    onSelectPage(pageIndex)
                                    when {
                                        activeTextDraft?.containsOffset(pageIndex, start, pageCanvasSize) == true -> Unit
                                        else -> {
                                            val textHit = currentAnnotations.textAnnotationHitAt(
                                                pageIndex = pageIndex,
                                                point = start,
                                                canvasSize = pageCanvasSize
                                            )
                                            clearInteractionState()
                                            if (textHit != null) {
                                                onTextAnnotationSelected(textHit)
                                            } else {
                                                onTextDraftStarted(pageIndex, start, pageCanvasSize)
                                            }
                                        }
                                    }
                                }
                            )
                        } else if (selectedTool != PdfInkTool.NONE) {
                            var eraserPreviousPoint: Offset? = null
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                                val start = down.position
                                onSelectPage(pageIndex)
                                clearInteractionState()
                                if (selectedTool == PdfInkTool.ERASER) {
                                    eraserPosition = start
                                    val annotationSnapshot = currentAnnotations
                                    val updatedAnnotations = annotationSnapshot.filterNot {
                                        it.pageIndex == pageIndex && it.sharedPdfHitTest(
                                            point = start,
                                            size = pageCanvasSize,
                                            eraserStrokeWidth = strokeWidth
                                        )
                                    }
                                    if (updatedAnnotations.size != annotationSnapshot.size) {
                                        onAnnotationsChanged(updatedAnnotations)
                                    }
                                    eraserPreviousPoint = start
                                } else {
                                    activeStroke = listOf(
                                        start.toSharedPdfPoint(pageCanvasSize, System.currentTimeMillis())
                                    )
                                }

                                val pointerId = down.id
                                var dragStarted = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) {
                                        eraserPreviousPoint = null
                                        eraserPosition = null
                                        activeStroke = emptyList()
                                        return@awaitEachGesture
                                    }
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: run {
                                            eraserPreviousPoint = null
                                            eraserPosition = null
                                            activeStroke = emptyList()
                                            return@awaitEachGesture
                                        }
                                    if (change.changedToUp()) {
                                        change.consume()
                                        if (selectedTool != PdfInkTool.ERASER && activeStroke.isNotEmpty()) {
                                            onAnnotationAdded(
                                                SharedPdfAnnotation(
                                                    id = "ink_${System.currentTimeMillis()}",
                                                    pageIndex = pageIndex,
                                                    kind = PdfAnnotationKind.INK,
                                                    tool = selectedTool,
                                                    points = activeStroke,
                                                    colorArgb = selectedColor,
                                                    strokeWidth = strokeWidth,
                                                    createdAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        eraserPreviousPoint = null
                                        eraserPosition = null
                                        activeStroke = emptyList()
                                        return@awaitEachGesture
                                    }
                                    if (!change.positionChanged()) continue
                                    val distance = (change.position - start).getDistance()
                                    if (selectedTool != PdfInkTool.ERASER && !dragStarted && distance <= viewConfiguration.touchSlop) continue
                                    dragStarted = true
                                    if (selectedTool == PdfInkTool.ERASER) {
                                        val point = change.position
                                        eraserPosition = point
                                        val previousPoint = eraserPreviousPoint
                                        val annotationSnapshot = currentAnnotations
                                        val updatedAnnotations = annotationSnapshot.filterNot {
                                            it.pageIndex == pageIndex && it.sharedPdfHitTest(
                                                point = point,
                                                size = pageCanvasSize,
                                                lastPoint = previousPoint,
                                                eraserStrokeWidth = strokeWidth
                                            )
                                        }
                                        if (updatedAnnotations.size != annotationSnapshot.size) {
                                            onAnnotationsChanged(updatedAnnotations)
                                        }
                                        eraserPreviousPoint = point
                                    } else {
                                        activeStroke = activeStroke.withDesktopPdfDragPoint(
                                            point = change.position,
                                            canvasSize = pageCanvasSize,
                                            tool = selectedTool,
                                            snapHighlighter = isHighlighterSnapEnabled,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                !shouldRender -> {
                    Text(
                        readerString("pdf_page_short", "Page %1\$d", pageIndex + 1),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                renderError != null && renderedPageIndex != pageIndex -> Text(
                    renderError ?: readerString("desktop_failed_render_page", "Failed to render page."),
                    color = MaterialTheme.colorScheme.error
                )
                renderedPage != null && desktopPdfRenderBelongsToPage(renderedPageIndex, pageIndex) -> {
                    val currentRenderedPageIndex = renderedPageIndex!!
                    Crossfade(
                        targetState = currentRenderedPageIndex,
                        animationSpec = tween(DesktopVerticalPdfPageTurnAnimationMillis),
                        label = "DesktopVerticalPdfPage"
                    ) { pageIndex ->
                    val pageRender = renderedPage!!
                    val pageEmbeddedAnnotations = remember(document.embeddedAnnotations, pageIndex) {
                        document.embeddedAnnotations.filter { it.pageIndex == pageIndex }
                    }
                    val pageAnnotations = remember(annotations, pageIndex, pageCanvasSize) {
                        annotations
                            .filter { it.pageIndex == pageIndex }
                            .flatMap { annotation ->
                                annotation.toRenderablePdfAnnotations(document, pageIndex, pageCanvasSize)
                            }
                    }
                    val selectedTextAnnotationForPage = remember(annotations, selectedAnnotationId, selectedTool, isTextSelectionMode, pageIndex) {
                        annotations.firstOrNull {
                            selectedTool == PdfInkTool.TEXT &&
                                !isTextSelectionMode &&
                                it.id == selectedAnnotationId &&
                                it.kind == PdfAnnotationKind.TEXT &&
                                it.pageIndex == pageIndex
                        }
                    }
                    val visiblePageAnnotations = remember(pageAnnotations, selectedTextAnnotationForPage?.id) {
                        pageAnnotations.filterNot {
                            it.kind == PdfAnnotationKind.TEXT && it.id == selectedTextAnnotationForPage?.id
                        }
                    }
                    val searchHighlightBounds: List<PdfPageBounds> = remember(
                        document.path,
                        searchResults,
                        pageIndex,
                        activeSearchIndex,
                        searchHighlightMode,
                        pageCanvasSize,
                        searchQuery
                    ) {
                        val queryLength = searchQuery.trim().length
                        if (queryLength <= 0 || pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) {
                            emptyList()
                        } else {
                            SharedPdfSearchEngine.highlightsForPage(
                                results = searchResults,
                                pageIndex = pageIndex,
                                activeResultIndex = activeSearchIndex,
                                mode = searchHighlightMode
                            ).flatMap { result ->
                                val matchLength = result.matchLength.takeIf { it > 0 } ?: queryLength
                                DesktopPdfium.textRectsForRange(
                                    document = document,
                                    pageIndex = pageIndex,
                                    startIndex = result.matchIndex,
                                    endIndex = result.matchIndex + matchLength - 1,
                                    viewportWidth = pageCanvasSize.width,
                                    viewportHeight = pageCanvasSize.height
                                ).map { it.toPdfPageBounds() }
                                    .filter { it.right > it.left && it.bottom > it.top }
                                    .mergePdfBoundsByLine()
                            }
                        }
                    }
                    val ttsHighlightBounds: List<PdfPageBounds> = remember(
                        document.path,
                        activeTtsChunk,
                        pageIndex,
                        pageCanvasSize
                    ) {
                        val chunk = activeTtsChunk?.takeIf { it.pageIndex == pageIndex }
                        if (chunk == null || pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0 || chunk.endOffset <= chunk.startOffset) {
                            emptyList()
                        } else {
                            DesktopPdfium.textRectsForRange(
                                document = document,
                                pageIndex = pageIndex,
                                startIndex = chunk.startOffset,
                                endIndex = chunk.endOffset - 1,
                                viewportWidth = pageCanvasSize.width,
                                viewportHeight = pageCanvasSize.height
                            ).map { it.toPdfPageBounds() }
                                .filter { it.right > it.left && it.bottom > it.top }
                                .mergePdfBoundsByLine()
                        }
                    }

                    DesktopPdfThemedPageImage(
                        bitmap = pageRender.image,
                        contentDescription = readerString("desktop_pdf_page_content_desc", "PDF page %1\$d", pageIndex + 1),
                        themeStyle = themeStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedPdfRichTextLayer(
                        pageIndex = pageIndex,
                        controller = richTextController,
                        pageWidth = pageCanvasSize.width.toFloat(),
                        pageHeight = pageCanvasSize.height.toFloat(),
                        isTextEditingEnabled = isRichTextMode,
                        onPageTapped = {}
                    )
                    PdfSearchHighlightOverlay(
                        bounds = searchHighlightBounds,
                        canvasSize = pageCanvasSize,
                        color = when (searchHighlightMode) {
                            SearchHighlightMode.ALL -> Color(0x55FDD835)
                            SearchHighlightMode.FOCUSED -> Color(0x88FF9800)
                        }
                    )
                    PdfSearchHighlightOverlay(
                        bounds = ttsHighlightBounds,
                        canvasSize = pageCanvasSize,
                        color = Color(0x887DD3FC)
                    )
                    PdfTextSelectionOverlay(
                        selection = textSelection,
                        canvasSize = pageCanvasSize
                    )
                    SharedPdfAnnotationOverlay(
                        annotations = visiblePageAnnotations,
                        activeStroke = activeStroke,
                        canvasSize = pageCanvasSize,
                        activeTool = selectedTool,
                        activeStrokeColorArgb = selectedColor,
                        activeStrokeWidth = strokeWidth,
                        selectedAnnotationId = selectedAnnotationId,
                        eraserPosition = eraserPosition,
                        showEraserIndicator = selectedTool == PdfInkTool.ERASER,
                        eraserStrokeWidth = strokeWidth
                    )
                    PdfTextSelectionHandles(
                        selection = textSelection,
                        canvasSize = pageCanvasSize,
                        activeHandle = activeSelectionHandle
                    )
                    SharedPdfInlineTextEditorOverlay(
                        draft = activeTextDraft?.takeIf { it.pageIndex == pageIndex },
                        canvasSize = pageCanvasSize,
                        onTextChange = { onTextDraftChanged(it, pageCanvasSize) },
                        onBoundsChange = { onTextDraftBoundsChanged(it) }
                    )
                    selectedTextAnnotationForPage?.let { annotation ->
                        val bounds = annotation.bounds
                        if (bounds != null && activeTextDraft == null) {
                            SharedPdfTextBoxEditorOverlay(
                                id = annotation.id,
                                text = annotation.text,
                                style = annotation.sharedPdfTextStyle(),
                                bounds = bounds,
                                canvasSize = pageCanvasSize,
                                onTextChange = { text ->
                                    onAnnotationUpdated(annotation.copy(text = text))
                                },
                                onBoundsChange = { nextBounds ->
                                    onAnnotationUpdated(annotation.copy(bounds = nextBounds))
                                }
                            )
                        }
                    }
                    SharedPdfEmbeddedAnnotationOverlay(
                        annotations = pageEmbeddedAnnotations,
                        canvasSize = pageCanvasSize,
                        selectedAnnotationId = selectedEmbeddedAnnotationId
                    )
                    if (showPageNumberOverlay) {
                        SharedPdfPageNumberOverlay(
                            pageIndex = pageIndex,
                            pageCount = document.pageCount
                        )
                    }
                    if (textSelection != null && selectionMenuOffset != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(pageIndex, selectionMenuOffset) {
                                    detectTapGestures {
                                        logPdfChromeTap {
                                            "selection_menu_scrim_tap source=vertical_page page=${pageIndex + 1} " +
                                                "consumedByScrim=true"
                                        }
                                        clearSelection()
                                    }
                                }
                        )
                    }
                    PdfSelectionMenu(
                        selection = textSelection,
                        menuOffset = selectionMenuOffset,
                        canvasSize = pageCanvasSize,
                        highlighterPalette = highlighterPalette,
                        onHighlighterPaletteChange = onHighlighterPaletteChange,
                        onCopy = {
                            textSelection?.let(onCopySelection)
                            clearSelection()
                        },
                        onHighlight = { colorArgb ->
                            textSelection?.let { onHighlightSelection(pageIndex, it, pageCanvasSize, colorArgb) }
                            clearSelection()
                        },
                        onSearch = {
                            textSelection?.let(onExternalSearchSelection)
                            clearSelection()
                        },
                        onDefine = {
                            textSelection?.let(onDefineSelection)
                            clearSelection()
                        },
                        onSpeak = {
                            textSelection?.let(onSpeakSelection)
                            clearSelection()
                        },
                        showDefine = readerAiFeaturesAvailable,
                        showSpeak = cloudTtsAvailable,
                        showSearch = externalLookupAvailable,
                        onClear = ::clearSelection
                    )
                    }
                }
                isRendering -> CircularProgressIndicator()
                renderError != null -> Text(
                    renderError ?: readerString("desktop_failed_render_page", "Failed to render page."),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
