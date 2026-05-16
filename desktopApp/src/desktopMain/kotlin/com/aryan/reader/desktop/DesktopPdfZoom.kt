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

internal data class DesktopPdfZoomPreview(
    val baseZoom: Float,
    val zoom: Float,
    val anchor: Offset?,
    val displayMode: PdfDisplayMode,
    val pageIndex: Int?
)

internal data class DesktopPdfCachedPageRender(
    val render: DesktopPdfPageRender,
    val scale: Float
)

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
    pageCanvasSize: IntSize
): Modifier {
    val activePreview = preview ?: return this
    if (pageCanvasSize.width <= 0 || pageCanvasSize.height <= 0) return this
    if (!currentZoom.isFinite() || currentZoom <= 0f) return this
    if (!activePreview.zoom.isFinite() || activePreview.zoom <= 0f) return this
    val previewScale = activePreview.zoom / currentZoom
    if (!previewScale.isFinite() || abs(previewScale - 1f) < 0.0001f) return this
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
        this.transformOrigin = transformOrigin
    }
}

internal fun Modifier.desktopPdfDocumentZoomPreviewLayer(
    preview: DesktopPdfZoomPreview?,
    currentZoom: Float,
    viewportRootOffset: Offset,
    pageRootOffset: Offset
): Modifier {
    val activePreview = preview ?: return this
    if (!currentZoom.isFinite() || currentZoom <= 0f) return this
    if (!activePreview.zoom.isFinite() || activePreview.zoom <= 0f) return this
    val previewScale = activePreview.zoom / currentZoom
    if (!previewScale.isFinite() || abs(previewScale - 1f) < 0.0001f) return this
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
        translationX = translation.x
        translationY = translation.y
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
