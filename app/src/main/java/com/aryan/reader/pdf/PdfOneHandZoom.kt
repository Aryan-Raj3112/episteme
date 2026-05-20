package com.aryan.reader.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.ViewConfiguration
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

internal const val PDF_ONE_HAND_ZOOM_TRACE_TAG = "PdfOneHandZoomTrace"
internal const val PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS = 90L
internal const val PDF_ONE_HAND_ZOOM_DRAG_DISTANCE_FOR_DOUBLE_DP = 240f

internal enum class PdfSecondTapZoomAction {
    QUICK_DOUBLE_TAP,
    ONE_HAND_ZOOM,
    HELD_NO_MOVEMENT
}

internal fun classifyPdfSecondTapZoomAction(
    pressDurationMillis: Long,
    totalDragY: Float,
    movementSlopPx: Float,
    holdTimeoutMillis: Long = PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS
): PdfSecondTapZoomAction {
    return when {
        pressDurationMillis < holdTimeoutMillis -> PdfSecondTapZoomAction.QUICK_DOUBLE_TAP
        abs(totalDragY) >= movementSlopPx -> PdfSecondTapZoomAction.ONE_HAND_ZOOM
        else -> PdfSecondTapZoomAction.HELD_NO_MOVEMENT
    }
}

internal fun pdfOneHandZoomScale(
    startScale: Float,
    totalDragY: Float,
    dragDistanceForDoublePx: Float,
    minScale: Float,
    maxScale: Float
): Float {
    val safeStart = startScale.takeIf { it.isFinite() && it > 0f } ?: minScale
    val safeDistance = dragDistanceForDoublePx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val scaleMultiplier = 2f.pow(totalDragY / safeDistance)
    return (safeStart * scaleMultiplier).coerceIn(minScale, maxScale)
}

internal fun clampCenteredPdfCameraOffset(
    scale: Float,
    offset: Offset,
    viewportSize: Size,
    contentSize: Size
): Offset {
    if (viewportSize.width <= 0f || viewportSize.height <= 0f || scale <= 1f) {
        return Offset.Zero
    }
    val maxOffsetX = ((contentSize.width * scale) - viewportSize.width).coerceAtLeast(0f) / 2f
    val maxOffsetY = ((contentSize.height * scale) - viewportSize.height).coerceAtLeast(0f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

internal fun centeredPdfCameraOffsetForScaleChange(
    previousScale: Float,
    nextScale: Float,
    previousOffset: Offset,
    pivot: Offset,
    viewportSize: Size,
    contentSize: Size
): Offset {
    val safePreviousScale = previousScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val ratio = nextScale / safePreviousScale
    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val targetOffset = previousOffset * ratio + (pivot - viewportCenter) * (1f - ratio)
    return clampCenteredPdfCameraOffset(
        scale = nextScale,
        offset = targetOffset,
        viewportSize = viewportSize,
        contentSize = contentSize
    )
}

internal fun topLeftPdfPanForScaleChange(
    previousScale: Float,
    nextScale: Float,
    previousPan: Offset,
    pivot: Offset
): Offset {
    val safePreviousScale = previousScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val contentPivot = (pivot - previousPan) / safePreviousScale
    return pivot - (contentPivot * nextScale)
}

private fun Offset.traceString(): String = "(${x.toInt()},${y.toInt()})"

private fun PointerInputChange.traceString(): String {
    return "id=$id pos=${position.traceString()} prev=${previousPosition.traceString()} pressed=$pressed consumed=$isConsumed"
}

private fun traceOneHandZoom(message: String) {
    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).d(message)
}

internal suspend fun PointerInputScope.detectPdfTapAndOneHandZoomGestures(
    viewConfiguration: ViewConfiguration,
    canStartOneHandZoom: () -> Boolean,
    canHandleQuickDoubleTap: () -> Boolean = { true },
    consumeSingleTap: Boolean = true,
    onTap: (Offset) -> Unit,
    onQuickDoubleTap: (Offset) -> Unit,
    onOneHandZoomHoldStart: (Offset) -> Unit,
    onOneHandZoom: (pivot: Offset, totalDragY: Float) -> Unit,
    onOneHandZoomEnd: (started: Boolean) -> Unit
) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        traceOneHandZoom(
            "detector.firstDown ${firstDown.traceString()} consumeSingleTap=$consumeSingleTap " +
                "doubleTapTimeout=${viewConfiguration.doubleTapTimeoutMillis} holdTimeout=$PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS"
        )
        val firstUp = waitForUpOrCancellation()
        if (firstUp == null) {
            traceOneHandZoom("detector.firstUpCanceled first=${firstDown.traceString()}")
            return@awaitEachGesture
        }
        traceOneHandZoom("detector.firstUp ${firstUp.traceString()}")

        val secondDown = awaitPdfSecondDown(
            firstPointerId = firstDown.id,
            timeoutMillis = viewConfiguration.doubleTapTimeoutMillis
        )

        if (secondDown == null) {
            traceOneHandZoom(
                "detector.singleTap noSecondDown consume=$consumeSingleTap firstUpConsumedBefore=${firstUp.isConsumed} " +
                    "tap=${firstDown.position.traceString()}"
            )
            if (consumeSingleTap) firstUp.consume()
            onTap(firstDown.position)
            return@awaitEachGesture
        }

        val pivot = secondDown.position
        var latestPosition = pivot
        var quickDoubleTapUp: PointerInputChange? = null
        var canceled = false
        val oneHandAllowed = canStartOneHandZoom()
        val movementSlopPx = max(2f, viewConfiguration.touchSlop * 0.35f)
        var shouldStartOneHandZoom = false
        traceOneHandZoom(
            "detector.secondDown ${secondDown.traceString()} oneHandAllowed=$oneHandAllowed " +
                "quickAllowed=${canHandleQuickDoubleTap()} movementSlop=$movementSlopPx touchSlop=${viewConfiguration.touchSlop}"
        )

        try {
            withTimeout(PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == secondDown.id }
                    if (change == null) {
                        canceled = true
                        traceOneHandZoom(
                            "detector.preHoldCancel missingSecondPointer changes=${event.changes.joinToString { it.traceString() }}"
                        )
                        return@withTimeout
                    }

                    latestPosition = change.position

                    if (change.changedToUp()) {
                        quickDoubleTapUp = change
                        traceOneHandZoom("detector.preHoldQuickUp ${change.traceString()}")
                        return@withTimeout
                    }

                    if (change.isConsumed) {
                        canceled = true
                        traceOneHandZoom(
                            "detector.preHoldCancel consumedByOther ${change.traceString()} " +
                                "allChanges=${event.changes.joinToString { it.traceString() }}"
                        )
                        return@withTimeout
                    }

                    if (oneHandAllowed) {
                        val delta = change.position - pivot
                        val isVerticalZoomDrag = abs(delta.y) >= movementSlopPx &&
                            abs(delta.y) >= abs(delta.x) * 1.1f
                        if (isVerticalZoomDrag) {
                            shouldStartOneHandZoom = true
                            traceOneHandZoom(
                                "detector.preHoldStart verticalDrag delta=${delta.traceString()} " +
                                    "slop=$movementSlopPx change=${change.traceString()}"
                            )
                            change.consume()
                            return@withTimeout
                        } else if (delta.getDistance() >= viewConfiguration.touchSlop) {
                            canceled = true
                            traceOneHandZoom(
                                "detector.preHoldCancel nonZoomMove delta=${delta.traceString()} " +
                                    "distance=${delta.getDistance()} touchSlop=${viewConfiguration.touchSlop}"
                            )
                            return@withTimeout
                        }
                    }
                }
            }
        } catch (_: PointerEventTimeoutCancellationException) {
            // The second tap is being held, so the quick double-tap action is suppressed.
            shouldStartOneHandZoom = true
            traceOneHandZoom(
                "detector.holdTimeout pivot=${pivot.traceString()} latest=${latestPosition.traceString()} " +
                    "oneHandAllowed=$oneHandAllowed"
            )
        }

        if (canceled) {
            traceOneHandZoom("detector.end canceledBeforeAction latest=${latestPosition.traceString()}")
            return@awaitEachGesture
        }

        if (quickDoubleTapUp != null) {
            val quickAllowed = canHandleQuickDoubleTap()
            traceOneHandZoom(
                "detector.quickDoubleTap fire quickAllowed=$quickAllowed upConsumedBefore=${quickDoubleTapUp?.isConsumed} " +
                    "pivot=${pivot.traceString()}"
            )
            if (quickAllowed) {
                quickDoubleTapUp?.consume()
            }
            onQuickDoubleTap(pivot)
            return@awaitEachGesture
        }

        if (!oneHandAllowed) {
            traceOneHandZoom("detector.oneHandBlocked waitingForUp pivot=${pivot.traceString()}")
            waitForUpOrCancellation()
            return@awaitEachGesture
        }

        if (!shouldStartOneHandZoom) {
            traceOneHandZoom("detector.end noAction shouldStart=false latest=${latestPosition.traceString()}")
            return@awaitEachGesture
        }

        var zoomStarted = false

        fun updateZoom(position: Offset) {
            val totalDragY = position.y - pivot.y
            val action = classifyPdfSecondTapZoomAction(
                pressDurationMillis = PDF_ONE_HAND_ZOOM_HOLD_TIMEOUT_MS,
                totalDragY = totalDragY,
                movementSlopPx = movementSlopPx
            )
            if (action == PdfSecondTapZoomAction.ONE_HAND_ZOOM) {
                if (!zoomStarted) {
                    traceOneHandZoom(
                        "detector.oneHandZoomStart dragY=$totalDragY pivot=${pivot.traceString()} " +
                            "position=${position.traceString()} slop=$movementSlopPx"
                    )
                }
                zoomStarted = true
            }
            if (zoomStarted) {
                Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                    "detector.oneHandZoomUpdate dragY=$totalDragY pivot=${pivot.traceString()} position=${position.traceString()}"
                )
                onOneHandZoom(pivot, totalDragY)
            }
        }

        traceOneHandZoom(
            "detector.oneHandHoldStart pivot=${pivot.traceString()} latest=${latestPosition.traceString()}"
        )
        onOneHandZoomHoldStart(pivot)
        try {
            updateZoom(latestPosition)

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == secondDown.id }
                if (change == null) {
                    traceOneHandZoom(
                        "detector.postHoldEnd missingSecondPointer changes=${event.changes.joinToString { it.traceString() }}"
                    )
                    break
                }

                latestPosition = change.position
                if (change.isConsumed) {
                    traceOneHandZoom(
                        "detector.postHoldEnd consumedByOther ${change.traceString()} zoomStarted=$zoomStarted"
                    )
                    break
                }

                val isPositionChanged = change.positionChanged()
                val isUp = change.changedToUp()
                updateZoom(latestPosition)

                if (isPositionChanged || zoomStarted || isUp) {
                    Timber.tag(PDF_ONE_HAND_ZOOM_TRACE_TAG).v(
                        "detector.consumePostHold changed=$isPositionChanged zoomStarted=$zoomStarted up=$isUp " +
                            change.traceString()
                    )
                    change.consume()
                }

                if (isUp) {
                    traceOneHandZoom(
                        "detector.oneHandPointerUp zoomStarted=$zoomStarted latest=${latestPosition.traceString()}"
                    )
                    break
                }
            }
        } finally {
            traceOneHandZoom(
                "detector.oneHandEnd zoomStarted=$zoomStarted latest=${latestPosition.traceString()}"
            )
            onOneHandZoomEnd(zoomStarted)
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPdfSecondDown(
    firstPointerId: PointerId,
    timeoutMillis: Long
): PointerInputChange? {
    return try {
        withTimeout(timeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val secondDown = event.changes.firstOrNull {
                    it.id != firstPointerId && it.changedToDown()
                } ?: event.changes.firstOrNull {
                    it.id == firstPointerId && it.changedToDown()
                }
                if (secondDown != null) return@withTimeout secondDown
            }
            null
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        null
    }
}
