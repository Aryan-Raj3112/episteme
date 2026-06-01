package com.aryan.reader.desktop

import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.pdf.PdfZoomSpec
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val DesktopPdfZoomGestureFrameMillis = 16L
private const val DesktopPdfZoomPreviewTolerance = 0.0001f
internal const val DesktopPdfPaginationFastFirstRenderMaxScale = 2.0f

internal fun desktopPdfScrollZoomFactor(scrollDelta: Float): Float {
    if (!scrollDelta.isFinite() || abs(scrollDelta) < 0.01f) return 1f
    val normalizedDelta = scrollDelta.coerceIn(-8f, 8f)
    return exp((-normalizedDelta * 0.12f).toDouble()).toFloat()
}

internal fun desktopPdfZoomTarget(
    currentZoom: Float,
    zoomSpec: PdfZoomSpec,
    factor: Float
): Float {
    val baseZoom = currentZoom.takeIf { it.isFinite() } ?: zoomSpec.default
    val safeFactor = factor.takeIf { it.isFinite() && it > 0f } ?: 1f
    return zoomSpec.clamp(baseZoom * safeFactor)
}

internal fun desktopPdfAnchoredScrollTarget(
    currentScroll: Int,
    anchor: Float,
    oldZoom: Float,
    newZoom: Float
): Int {
    if (
        !anchor.isFinite() ||
        !oldZoom.isFinite() ||
        !newZoom.isFinite() ||
        oldZoom <= 0f ||
        newZoom <= 0f
    ) {
        return currentScroll.coerceAtLeast(0)
    }
    val zoomRatio = newZoom / oldZoom
    return (((currentScroll + anchor) * zoomRatio) - anchor).roundToInt().coerceAtLeast(0)
}

internal fun desktopPdfAnchoredLazyItemScrollOffset(
    itemOffset: Int,
    anchor: Float,
    oldZoom: Float,
    newZoom: Float
): Int {
    if (
        !anchor.isFinite() ||
        !oldZoom.isFinite() ||
        !newZoom.isFinite() ||
        oldZoom <= 0f ||
        newZoom <= 0f
    ) {
        return (-itemOffset).coerceAtLeast(0)
    }
    val zoomRatio = newZoom / oldZoom
    val offsetWithinItem = anchor - itemOffset
    return ((offsetWithinItem * zoomRatio) - anchor).roundToInt().coerceAtLeast(0)
}

internal fun desktopPdfAnchoredPageScrollDelta(
    viewportRootOffset: Offset,
    oldPageRootOffset: Offset,
    currentPageRootOffset: Offset,
    anchor: Offset,
    oldZoom: Float,
    newZoom: Float
): IntOffset? {
    if (
        !anchor.x.isFinite() ||
        !anchor.y.isFinite() ||
        !oldZoom.isFinite() ||
        !newZoom.isFinite() ||
        oldZoom <= 0f ||
        newZoom <= 0f
    ) {
        return null
    }
    val rootAnchor = viewportRootOffset + anchor
    val oldPageLocal = rootAnchor - oldPageRootOffset
    val zoomRatio = newZoom / oldZoom
    val newPageLocal = Offset(oldPageLocal.x * zoomRatio, oldPageLocal.y * zoomRatio)
    val desiredPageRoot = rootAnchor - newPageLocal
    val delta = currentPageRootOffset - desiredPageRoot
    return IntOffset(delta.x.roundToInt(), delta.y.roundToInt())
}

internal fun desktopPdfPaginationFirstRenderScale(
    requestedScale: Float,
    hasPageRender: Boolean,
    isOpeningRender: Boolean = false
): Float {
    if (hasPageRender || isOpeningRender || !requestedScale.isFinite() || requestedScale <= 0f) {
        return requestedScale
    }
    return requestedScale.coerceAtMost(DesktopPdfPaginationFastFirstRenderMaxScale)
}

internal fun desktopPdfRenderBelongsToPage(
    renderedPageIndex: Int?,
    requestedPageIndex: Int
): Boolean {
    return renderedPageIndex == requestedPageIndex
}

internal fun desktopPdfRenderScaleNeedsUpgrade(
    renderedScale: Float?,
    requestedScale: Float
): Boolean {
    if (renderedScale == null) return true
    if (!requestedScale.isFinite() || requestedScale <= 0f) return false
    if (!renderedScale.isFinite() || renderedScale <= 0f) return true
    return requestedScale - renderedScale > DesktopPdfRenderScaleTolerance
}

internal fun desktopPdfSpreadZoomAnchorPageIndex(
    viewportRootOffset: Offset,
    anchor: Offset?,
    visiblePageIndices: List<Int>,
    pageRootOffsets: Map<Int, Offset>,
    pageSizes: Map<Int, IntSize>,
    fallbackPageIndex: Int
): Int {
    if (anchor == null || visiblePageIndices.isEmpty()) return fallbackPageIndex
    if (!anchor.x.isFinite() || !anchor.y.isFinite()) return fallbackPageIndex
    val rootAnchor = viewportRootOffset + anchor
    if (!rootAnchor.x.isFinite() || !rootAnchor.y.isFinite()) return fallbackPageIndex
    val candidates = visiblePageIndices.mapNotNull { pageIndex ->
        pageRootOffsets[pageIndex]?.let { root ->
            pageIndex to root
        }
    }
    if (candidates.isEmpty()) return fallbackPageIndex
    candidates.firstOrNull { (pageIndex, root) ->
        val size = pageSizes[pageIndex] ?: return@firstOrNull false
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        rootAnchor.x >= root.x &&
            rootAnchor.x <= root.x + width &&
            rootAnchor.y >= root.y &&
            rootAnchor.y <= root.y + height
    }?.let { return it.first }
    return candidates.minByOrNull { (pageIndex, root) ->
        val size = pageSizes[pageIndex]
        val dx: Float
        val dy: Float
        if (size == null) {
            dx = rootAnchor.x - root.x
            dy = rootAnchor.y - root.y
        } else {
            val right = root.x + size.width.toFloat()
            val bottom = root.y + size.height.toFloat()
            dx = when {
                rootAnchor.x < root.x -> root.x - rootAnchor.x
                rootAnchor.x > right -> rootAnchor.x - right
                else -> 0f
            }
            dy = when {
                rootAnchor.y < root.y -> root.y - rootAnchor.y
                rootAnchor.y > bottom -> rootAnchor.y - bottom
                else -> 0f
            }
        }
        dx * dx + dy * dy
    }?.first ?: fallbackPageIndex
}

internal data class DesktopPdfZoomPreview(
    val baseZoom: Float,
    val zoom: Float,
    val anchor: Offset?,
    val displayMode: PdfDisplayMode,
    val pageIndex: Int?,
    val viewportRootOffset: Offset = Offset.Zero,
    val pageRootOffset: Offset? = null,
    val commitTargetHorizontalScroll: Int? = null,
    val commitTargetVerticalScroll: Int? = null,
    val diagnosticSequence: Int = 0
)

internal data class DesktopPdfZoomScrollBounds(
    val currentHorizontalScroll: Int? = null,
    val maxHorizontalScroll: Int? = null,
    val currentVerticalScroll: Int? = null,
    val maxVerticalScroll: Int? = null
)

internal interface DesktopPdfLayoutScrollPrediction {
    val maxHorizontalScroll: Int
    val maxVerticalScroll: Int
}

internal data class DesktopPdfSinglePageLayoutPrediction(
    val rootOffset: Offset,
    override val maxHorizontalScroll: Int,
    override val maxVerticalScroll: Int
) : DesktopPdfLayoutScrollPrediction

internal data class DesktopPdfSpreadLayoutPrediction(
    val pageRootOffsets: Map<Int, Offset>,
    override val maxHorizontalScroll: Int,
    override val maxVerticalScroll: Int
) : DesktopPdfLayoutScrollPrediction

internal data class DesktopPdfCachedPageRender(
    val render: DesktopPdfPageRender,
    val scale: Float
)

internal data class DesktopPdfNavigationZoomSnapshot(
    val zoom: Float,
    val horizontalScroll: Int,
    val verticalScroll: Int
)

internal fun desktopPdfNavigationZoomSnapshot(
    preview: DesktopPdfZoomPreview?,
    currentHorizontalScroll: Int,
    currentVerticalScroll: Int
): DesktopPdfNavigationZoomSnapshot? {
    val activePreview = preview ?: return null
    val baseZoom = activePreview.baseZoom.takeIf { it.isFinite() && it > 0f } ?: return null
    val targetZoom = activePreview.zoom.takeIf { it.isFinite() && it > 0f } ?: return null
    val anchor = activePreview.anchor
    return DesktopPdfNavigationZoomSnapshot(
        zoom = targetZoom,
        horizontalScroll = anchor?.let {
            desktopPdfAnchoredScrollTarget(currentHorizontalScroll, it.x, baseZoom, targetZoom)
        } ?: currentHorizontalScroll.coerceAtLeast(0),
        verticalScroll = anchor?.let {
            desktopPdfAnchoredScrollTarget(currentVerticalScroll, it.y, baseZoom, targetZoom)
        } ?: currentVerticalScroll.coerceAtLeast(0)
    )
}

internal fun desktopPdfZoomPreviewMatchesScale(
    preview: DesktopPdfZoomPreview,
    scale: Float
): Boolean {
    return abs(preview.baseZoom - scale) <= DesktopPdfZoomPreviewTolerance ||
        abs(preview.zoom - scale) <= DesktopPdfZoomPreviewTolerance
}

internal fun desktopPdfReachableScrollDelta(
    currentScroll: Int?,
    maxScroll: Int?,
    requestedDelta: Int
): Int {
    if (currentScroll == null || maxScroll == null) return requestedDelta
    val safeMax = maxScroll.coerceAtLeast(0)
    val safeCurrent = currentScroll.coerceIn(0, safeMax)
    val targetScroll = (safeCurrent + requestedDelta).coerceIn(0, safeMax)
    return targetScroll - safeCurrent
}

internal fun desktopPdfReachableScrollDelta(
    requestedDelta: IntOffset,
    scrollBounds: DesktopPdfZoomScrollBounds?
): IntOffset {
    if (scrollBounds == null) return requestedDelta
    return IntOffset(
        x = desktopPdfReachableScrollDelta(
            currentScroll = scrollBounds.currentHorizontalScroll,
            maxScroll = scrollBounds.maxHorizontalScroll,
            requestedDelta = requestedDelta.x
        ),
        y = desktopPdfReachableScrollDelta(
            currentScroll = scrollBounds.currentVerticalScroll,
            maxScroll = scrollBounds.maxVerticalScroll,
            requestedDelta = requestedDelta.y
        )
    )
}

internal fun desktopPdfZoomScrollBoundsWithCommitTargets(
    preview: DesktopPdfZoomPreview?,
    currentHorizontalScroll: Int,
    maxHorizontalScroll: Int,
    currentVerticalScroll: Int? = null,
    maxVerticalScroll: Int? = null
): DesktopPdfZoomScrollBounds {
    return DesktopPdfZoomScrollBounds(
        currentHorizontalScroll = currentHorizontalScroll,
        maxHorizontalScroll = maxOf(maxHorizontalScroll, preview?.commitTargetHorizontalScroll ?: 0),
        currentVerticalScroll = currentVerticalScroll,
        maxVerticalScroll = maxVerticalScroll?.let {
            maxOf(it, preview?.commitTargetVerticalScroll ?: 0)
        }
    )
}

internal fun desktopPdfSinglePageLayoutPrediction(
    viewportRootOffset: Offset,
    viewportSize: IntSize,
    pageCanvasSize: IntSize,
    horizontalScroll: Int,
    verticalScroll: Int,
    paddingPx: Float
): DesktopPdfSinglePageLayoutPrediction? {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return null
    if (pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) return null
    if (!viewportRootOffset.x.isFinite() || !viewportRootOffset.y.isFinite()) return null
    if (!paddingPx.isFinite() || paddingPx < 0f) return null
    val viewportWidth = viewportSize.width.toFloat()
    val viewportHeight = viewportSize.height.toFloat()
    val pageWidth = pageCanvasSize.width.toFloat()
    val contentWidth = (viewportWidth - paddingPx * 2f).coerceAtLeast(0f)
    val maxHorizontalScroll = (pageWidth + paddingPx * 2f - viewportWidth)
        .roundToInt()
        .coerceAtLeast(0)
    val maxVerticalScroll = (pageCanvasSize.height.toFloat() + paddingPx * 2f - viewportHeight)
        .roundToInt()
        .coerceAtLeast(0)
    val safeHorizontalScroll = horizontalScroll.coerceIn(0, maxHorizontalScroll)
    val safeVerticalScroll = verticalScroll.coerceIn(0, maxVerticalScroll)
    val pageX = if (pageWidth <= contentWidth) {
        ((viewportWidth - pageWidth) / 2f) - safeHorizontalScroll.toFloat()
    } else {
        paddingPx - safeHorizontalScroll.toFloat()
    }
    val pageY = paddingPx - safeVerticalScroll.toFloat()
    return DesktopPdfSinglePageLayoutPrediction(
        rootOffset = Offset(
            x = viewportRootOffset.x + pageX,
            y = viewportRootOffset.y + pageY
        ),
        maxHorizontalScroll = maxHorizontalScroll,
        maxVerticalScroll = maxVerticalScroll
    )
}

internal fun desktopPdfSpreadLayoutPrediction(
    viewportRootOffset: Offset,
    viewportSize: IntSize,
    visiblePageIndices: List<Int>,
    pageCanvasSizes: Map<Int, IntSize>,
    horizontalScroll: Int,
    verticalScroll: Int,
    paddingPx: Float,
    pageGapPx: Float
): DesktopPdfSpreadLayoutPrediction? {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return null
    if (visiblePageIndices.isEmpty()) return null
    if (!viewportRootOffset.x.isFinite() || !viewportRootOffset.y.isFinite()) return null
    if (!paddingPx.isFinite() || paddingPx < 0f) return null
    if (!pageGapPx.isFinite() || pageGapPx < 0f) return null
    val pageSizes = visiblePageIndices.map { pageIndex ->
        val pageSize = pageCanvasSizes[pageIndex] ?: return null
        if (pageSize.width <= 0 || pageSize.height <= 0) return null
        pageSize
    }
    val viewportWidth = viewportSize.width.toFloat()
    val viewportHeight = viewportSize.height.toFloat()
    val rowWidth = pageSizes.sumOf { it.width }.toFloat() +
        (pageGapPx * (pageSizes.size - 1).coerceAtLeast(0))
    val rowHeight = pageSizes.maxOf { it.height }.toFloat()
    val contentWidth = (viewportWidth - paddingPx * 2f).coerceAtLeast(0f)
    val maxHorizontalScroll = (rowWidth + paddingPx * 2f - viewportWidth)
        .roundToInt()
        .coerceAtLeast(0)
    val maxVerticalScroll = (rowHeight + paddingPx * 2f - viewportHeight)
        .roundToInt()
        .coerceAtLeast(0)
    val safeHorizontalScroll = horizontalScroll.coerceIn(0, maxHorizontalScroll)
    val safeVerticalScroll = verticalScroll.coerceIn(0, maxVerticalScroll)
    val rowX = if (rowWidth <= contentWidth) {
        ((viewportWidth - rowWidth) / 2f) - safeHorizontalScroll.toFloat()
    } else {
        paddingPx - safeHorizontalScroll.toFloat()
    }
    val rowY = paddingPx - safeVerticalScroll.toFloat()
    var pageX = viewportRootOffset.x + rowX
    val pageY = viewportRootOffset.y + rowY
    val roots = visiblePageIndices.mapIndexed { index, pageIndex ->
        val root = Offset(pageX, pageY)
        pageX += pageSizes[index].width.toFloat() + pageGapPx
        pageIndex to root
    }.toMap()
    return DesktopPdfSpreadLayoutPrediction(
        pageRootOffsets = roots,
        maxHorizontalScroll = maxHorizontalScroll,
        maxVerticalScroll = maxVerticalScroll
    )
}

internal fun desktopPdfZoomCommitPreviewTranslation(
    viewportRootOffset: Offset,
    oldPageRootOffset: Offset?,
    currentAnchorPageRootOffset: Offset,
    anchor: Offset?,
    oldZoom: Float,
    newZoom: Float,
    currentZoom: Float,
    scrollBounds: DesktopPdfZoomScrollBounds? = null
): Offset? {
    if (oldPageRootOffset == null || anchor == null) return null
    if (abs(currentZoom - newZoom) > DesktopPdfZoomPreviewTolerance) return null
    val pageDelta = desktopPdfAnchoredPageScrollDelta(
        viewportRootOffset = viewportRootOffset,
        oldPageRootOffset = oldPageRootOffset,
        currentPageRootOffset = currentAnchorPageRootOffset,
        anchor = anchor,
        oldZoom = oldZoom,
        newZoom = newZoom
    ) ?: return null
    val reachableDelta = desktopPdfReachableScrollDelta(pageDelta, scrollBounds)
    return Offset(
        x = if (reachableDelta.x == 0) 0f else -reachableDelta.x.toFloat(),
        y = if (reachableDelta.y == 0) 0f else -reachableDelta.y.toFloat()
    )
}

internal fun desktopPdfZoomPreviewPivotFraction(
    viewportRootOffset: Offset,
    pageRootOffset: Offset,
    anchor: Offset,
    pageCanvasSize: IntSize
): Offset? {
    if (pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) return null
    if (!anchor.x.isFinite() || !anchor.y.isFinite()) return null
    val pageAnchor = viewportRootOffset + anchor - pageRootOffset
    if (!pageAnchor.x.isFinite() || !pageAnchor.y.isFinite()) return null
    return Offset(
        x = (pageAnchor.x / pageCanvasSize.width).coerceIn(0f, 1f),
        y = (pageAnchor.y / pageCanvasSize.height).coerceIn(0f, 1f)
    )
}

internal fun desktopPdfDocumentZoomPreviewTranslation(
    viewportRootOffset: Offset,
    pageRootOffset: Offset,
    anchor: Offset,
    previewScale: Float
): Offset? {
    if (!anchor.x.isFinite() || !anchor.y.isFinite()) return null
    if (!previewScale.isFinite() || previewScale <= 0f) return null
    val rootAnchor = viewportRootOffset + anchor
    if (!rootAnchor.x.isFinite() || !rootAnchor.y.isFinite()) return null
    return Offset(
        x = (pageRootOffset.x - rootAnchor.x) * (previewScale - 1f),
        y = (pageRootOffset.y - rootAnchor.y) * (previewScale - 1f)
    )
}

internal fun Modifier.desktopPdfZoomPreviewLayer(
    preview: DesktopPdfZoomPreview?,
    currentZoom: Float,
    viewportRootOffset: Offset,
    pageRootOffset: Offset,
    pageCanvasSize: IntSize,
    commitPageRootOffset: Offset? = null,
    scrollBounds: DesktopPdfZoomScrollBounds? = null
): Modifier {
    val activePreview = preview ?: return this
    if (pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) return this
    if (!currentZoom.isFinite() || currentZoom <= 0f) return this
    if (!activePreview.zoom.isFinite() || activePreview.zoom <= 0f) return this
    val previewScale = activePreview.zoom / currentZoom
    val commitTranslation = desktopPdfZoomCommitPreviewTranslation(
        viewportRootOffset = activePreview.viewportRootOffset,
        oldPageRootOffset = activePreview.pageRootOffset,
        currentAnchorPageRootOffset = commitPageRootOffset ?: pageRootOffset,
        anchor = activePreview.anchor,
        oldZoom = activePreview.baseZoom,
        newZoom = activePreview.zoom,
        currentZoom = currentZoom,
        scrollBounds = scrollBounds
    )
    if (
        !previewScale.isFinite() ||
        (abs(previewScale - 1f) < DesktopPdfZoomPreviewTolerance && commitTranslation == null)
    ) {
        return this
    }
    logPdfZoomSettle {
        "preview_layer seq=${activePreview.diagnosticSequence} kind=page currentZoom=${currentZoom.formatLogFloat()} " +
            "previewZoom=${activePreview.zoom.formatLogFloat()} scale=${previewScale.formatLogFloat()} " +
            "pageRoot=${pageRootOffset.formatLogOffset()} commitRoot=${commitPageRootOffset.formatLogOffset()} " +
            "commit=${commitTranslation.formatLogOffset()} " +
            "h=${scrollBounds?.currentHorizontalScroll ?: "none"}/${scrollBounds?.maxHorizontalScroll ?: "none"} " +
            "v=${scrollBounds?.currentVerticalScroll ?: "none"}/${scrollBounds?.maxVerticalScroll ?: "none"}"
    }
    val transformOrigin = activePreview.anchor?.let { anchor ->
        desktopPdfZoomPreviewPivotFraction(
            viewportRootOffset = viewportRootOffset,
            pageRootOffset = pageRootOffset,
            anchor = anchor,
            pageCanvasSize = pageCanvasSize
        )?.let { pivot ->
            TransformOrigin(pivotFractionX = pivot.x, pivotFractionY = pivot.y)
        } ?: TransformOrigin.Center
    } ?: TransformOrigin.Center
    return graphicsLayer {
        scaleX = previewScale
        scaleY = previewScale
        translationX = commitTranslation?.x ?: 0f
        translationY = commitTranslation?.y ?: 0f
        this.transformOrigin = transformOrigin
    }
}

internal fun Modifier.desktopPdfDocumentZoomPreviewLayer(
    preview: DesktopPdfZoomPreview?,
    currentZoom: Float,
    viewportRootOffset: Offset,
    pageRootOffset: Offset,
    anchorPageRootOffset: Offset? = null,
    scrollBounds: DesktopPdfZoomScrollBounds? = null
): Modifier {
    val activePreview = preview ?: return this
    if (!currentZoom.isFinite() || currentZoom <= 0f) return this
    if (!activePreview.zoom.isFinite() || activePreview.zoom <= 0f) return this
    val previewScale = activePreview.zoom / currentZoom
    val commitTranslation = desktopPdfZoomCommitPreviewTranslation(
        viewportRootOffset = activePreview.viewportRootOffset,
        oldPageRootOffset = activePreview.pageRootOffset,
        currentAnchorPageRootOffset = anchorPageRootOffset ?: pageRootOffset,
        anchor = activePreview.anchor,
        oldZoom = activePreview.baseZoom,
        newZoom = activePreview.zoom,
        currentZoom = currentZoom,
        scrollBounds = scrollBounds
    )
    if (
        !previewScale.isFinite() ||
        (abs(previewScale - 1f) < DesktopPdfZoomPreviewTolerance && commitTranslation == null)
    ) {
        return this
    }
    logPdfZoomSettle {
        "preview_layer seq=${activePreview.diagnosticSequence} kind=document currentZoom=${currentZoom.formatLogFloat()} " +
            "previewZoom=${activePreview.zoom.formatLogFloat()} scale=${previewScale.formatLogFloat()} " +
            "pageRoot=${pageRootOffset.formatLogOffset()} anchorRoot=${(anchorPageRootOffset ?: pageRootOffset).formatLogOffset()} " +
            "commit=${commitTranslation.formatLogOffset()} h=${scrollBounds?.currentHorizontalScroll ?: "none"}/" +
            "${scrollBounds?.maxHorizontalScroll ?: "none"} v=${scrollBounds?.currentVerticalScroll ?: "none"}/" +
            "${scrollBounds?.maxVerticalScroll ?: "none"}"
    }
    val translation = activePreview.anchor?.let { anchor ->
        desktopPdfDocumentZoomPreviewTranslation(
            viewportRootOffset = viewportRootOffset,
            pageRootOffset = pageRootOffset,
            anchor = anchor,
            previewScale = previewScale
        )
    } ?: Offset.Zero
    return graphicsLayer {
        scaleX = previewScale
        scaleY = previewScale
        translationX = translation.x + (commitTranslation?.x ?: 0f)
        translationY = translation.y + (commitTranslation?.y ?: 0f)
        transformOrigin = TransformOrigin(0f, 0f)
    }
}

@Composable
internal fun Modifier.desktopPdfZoomGestures(
    currentZoom: Float,
    zoomSpec: PdfZoomSpec,
    onZoomChanged: (oldZoom: Float, newZoom: Float, anchor: Offset?) -> Unit
): Modifier {
    val latestZoom by rememberUpdatedState(currentZoom)
    val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)
    return this.pointerInput(zoomSpec) {
        var gestureZoom = latestZoom
        var appliedGestureZoom = latestZoom
        var lastZoomEventAt = 0L
        var lastAppliedZoomAt = 0L
        fun applyZoomFactor(factor: Float, eventTime: Long, anchor: Offset?) {
            if (lastZoomEventAt == 0L || eventTime - lastZoomEventAt > 180L) {
                gestureZoom = latestZoom
                appliedGestureZoom = latestZoom
                lastAppliedZoomAt = 0L
            }
            val newZoom = desktopPdfZoomTarget(gestureZoom, zoomSpec, factor)
            gestureZoom = newZoom
            lastZoomEventAt = eventTime
            val shouldApplyNow = lastAppliedZoomAt == 0L ||
                eventTime - lastAppliedZoomAt >= DesktopPdfZoomGestureFrameMillis ||
                newZoom == zoomSpec.min ||
                newZoom == zoomSpec.max
            if (shouldApplyNow && newZoom != appliedGestureZoom) {
                latestOnZoomChanged(appliedGestureZoom, newZoom, anchor)
                appliedGestureZoom = newZoom
                lastAppliedZoomAt = eventTime
            }
        }

        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val eventTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L
                if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isPointerCtrlPressed) {
                    val scrollDelta = event.changes.fold(Offset.Zero) { total, change ->
                        total + change.scrollDelta
                    }
                    val zoomDelta = if (abs(scrollDelta.y) >= abs(scrollDelta.x)) scrollDelta.y else scrollDelta.x
                    val factor = desktopPdfScrollZoomFactor(zoomDelta)
                    if (abs(factor - 1f) > 0.0001f) {
                        applyZoomFactor(factor, eventTime, event.changes.firstOrNull()?.position)
                        event.changes.forEach { it.consume() }
                    }
                    continue
                }

                val pressedPointers = event.changes.count { it.pressed }
                if (pressedPointers > 1) {
                    val zoomChange = event.calculateZoom()
                    if (zoomChange.isFinite() && abs(zoomChange - 1f) > 0.005f) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val anchor = if (centroid == Offset.Unspecified) {
                            event.changes.firstOrNull { it.pressed }?.position
                        } else {
                            centroid
                        }
                        applyZoomFactor(zoomChange, eventTime, anchor)
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
