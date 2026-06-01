package com.aryan.reader.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.unit.IntSize
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfNormalizedPoint
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfSelectionGeometry
import com.aryan.reader.shared.pdf.PdfTextCharBounds
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfInkRenderer
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.ui.sharedPdfHitTest
import com.aryan.reader.shared.ui.toSharedPdfPoint
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal val PdfInkTool.isDesktopHighlighter: Boolean
    get() = this == PdfInkTool.HIGHLIGHTER || this == PdfInkTool.HIGHLIGHTER_ROUND

internal val SharedPdfAnnotation.isDesktopTextSelectionHighlight: Boolean
    get() = kind == PdfAnnotationKind.HIGHLIGHT &&
        text.isNotBlank() &&
        rangeStartIndex != null &&
        rangeEndIndex != null

internal fun SharedPdfReaderState.withDesktopPdfTextSelectionHighlightAdded(
    annotation: SharedPdfAnnotation,
    zoomSpec: PdfZoomSpec = PdfZoomSpec()
): SharedPdfReaderState {
    val next = reduce(SharedPdfReaderAction.AnnotationAdded(annotation), zoomSpec)
    return if (annotation.isDesktopTextSelectionHighlight) {
        next.reduce(SharedPdfReaderAction.AnnotationSelected(null), zoomSpec)
    } else {
        next
    }
}

internal fun SharedPdfReaderState.withDesktopPdfTextHighlightSheetDismissed(
    zoomSpec: PdfZoomSpec = PdfZoomSpec()
): SharedPdfReaderState {
    return reduce(SharedPdfReaderAction.AnnotationSelected(null), zoomSpec)
}

internal fun List<PdfPagePoint>.withDesktopPdfDragPoint(
    point: Offset,
    canvasSize: IntSize,
    tool: PdfInkTool,
    snapHighlighter: Boolean,
    timestamp: Long
): List<PdfPagePoint> {
    val nextPoint = point.toSharedPdfPoint(canvasSize, timestamp)
    if (snapHighlighter && tool.isDesktopHighlighter && isNotEmpty()) {
        val pageAspectRatio = canvasSize.width.toFloat() / canvasSize.height.coerceAtLeast(1).toFloat()
        return listOf(
            first(),
            SharedPdfInkRenderer.calculateSnappedPoint(
                currentPoint = nextPoint,
                startPoint = first(),
                pageAspectRatio = pageAspectRatio
            )
        )
    }
    return this + nextPoint
}

internal suspend fun PointerInputScope.detectDesktopPdfTextSelectionLongPress(
    source: String,
    pageIndex: Int,
    onLongPress: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val secondaryDown = currentEvent.buttons.isSecondaryPressed
        logPdfChromeTap {
            "long_press_down source=$source page=${pageIndex + 1} " +
                "x=${down.position.x.formatLogFloat()} y=${down.position.y.formatLogFloat()} " +
                "downConsumed=${down.isConsumed} secondary=$secondaryDown"
        }
        if (down.isConsumed || secondaryDown) {
            logPdfChromeTap {
                "long_press_skip source=$source page=${pageIndex + 1} " +
                    "reason=${if (down.isConsumed) "down_consumed" else "secondary_button"}"
            }
            return@awaitEachGesture
        }
        val pointerId = down.id
        val start = down.position
        var latestPosition = start
        var canceledBeforeLongPress = false
        var longPressReached = false
        var cancelReason = ""

        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.buttons.isSecondaryPressed) {
                        canceledBeforeLongPress = true
                        cancelReason = "secondary_button"
                        return@withTimeout
                    }
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        canceledBeforeLongPress = true
                        cancelReason = "pointer_lost"
                        return@withTimeout
                    }
                    latestPosition = change.position
                    val distance = (latestPosition - start).getDistance()
                    when {
                        change.isConsumed -> {
                            canceledBeforeLongPress = true
                            cancelReason = "change_consumed"
                            return@withTimeout
                        }
                        change.changedToUp() || !change.pressed -> {
                            canceledBeforeLongPress = true
                            cancelReason = "up_before_long_press"
                            return@withTimeout
                        }
                        distance > viewConfiguration.touchSlop -> {
                            canceledBeforeLongPress = true
                            cancelReason = "moved distance=${distance.formatLogFloat()}"
                            return@withTimeout
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            longPressReached = !canceledBeforeLongPress
        }

        if (!longPressReached) {
            logPdfChromeTap {
                "long_press_cancel source=$source page=${pageIndex + 1} " +
                    "reason=${cancelReason.ifBlank { "unknown" }} " +
                    "x=${latestPosition.x.formatLogFloat()} y=${latestPosition.y.formatLogFloat()}"
            }
            return@awaitEachGesture
        }
        logPdfChromeTap {
            "long_press_reached source=$source page=${pageIndex + 1} " +
                "x=${latestPosition.x.formatLogFloat()} y=${latestPosition.y.formatLogFloat()}"
        }
        onLongPress(latestPosition)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
            change.consume()
            if (change.changedToUp() || !change.pressed) return@awaitEachGesture
        }
    }
}

internal data class DesktopPdfCharHit(
    val index: Int,
    val source: String,
    val point: Offset,
    val normalized: PdfNormalizedPoint
)

internal fun SharedPdfAnnotation.toDesktopPdfTextSelection(): DesktopPdfTextSelection {
    return DesktopPdfTextSelection(
        text = text,
        lineBounds = boundsList.ifEmpty { listOfNotNull(bounds) },
        startIndex = rangeStartIndex ?: 0,
        endIndex = rangeEndIndex ?: text.length
    )
}

internal fun DesktopPdfDocument.linkAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): DesktopPdfLinkTarget? {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
    return DesktopPdfium.linkAt(
        document = this,
        pageIndex = pageIndex,
        normalizedX = point.x / canvasSize.width,
        normalizedY = point.y / canvasSize.height,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    )
}

internal fun DesktopPdfDocument.charHitAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): DesktopPdfCharHit? {
    val normalized = PdfSelectionGeometry.normalizedPoint(
        pointX = point.x,
        pointY = point.y,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    ) ?: return null
    val nativeIndex = DesktopPdfium.charIndexAt(
        document = this,
        pageIndex = pageIndex,
        normalizedX = normalized.x,
        normalizedY = normalized.y,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    )
    if (nativeIndex != null) {
        return DesktopPdfCharHit(
            index = nativeIndex,
            source = "native",
            point = point,
            normalized = normalized
        )
    }
    val fallback = PdfSelectionGeometry.nearestCharOnLine(
        chars = textPageData(pageIndex).chars.visiblePdfTextBounds(),
        point = normalized
    ) ?: return null
    return DesktopPdfCharHit(
        index = fallback.index,
        source = "fallback_line",
        point = point,
        normalized = normalized
    )
}

internal fun DesktopPdfDocument.wordSelectionAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): DesktopPdfTextSelection? {
    val hit = charHitAt(pageIndex, point, canvasSize) ?: return null
    if (hit.source == "fallback_line" && !isPointNearTextChar(pageIndex, hit.index, hit.normalized)) {
        return null
    }
    val pageText = textPageData(pageIndex).text
    if (pageText.isEmpty()) return null
    val hitIndex = hit.index.coerceIn(0, pageText.lastIndex)
    if (!pageText[hitIndex].isDesktopPdfWordPart()) return null
    var startIndex = hitIndex
    while (startIndex > 0 && pageText[startIndex - 1].isDesktopPdfWordPart()) {
        startIndex -= 1
    }
    var endIndex = hitIndex
    while (endIndex < pageText.lastIndex && pageText[endIndex + 1].isDesktopPdfWordPart()) {
        endIndex += 1
    }
    return selectionBetweenIndexes(
        pageIndex = pageIndex,
        startIndex = startIndex,
        endIndex = endIndex,
        canvasSize = canvasSize,
        useNativeBounds = true
    )
}

private fun DesktopPdfDocument.isPointNearTextChar(
    pageIndex: Int,
    charIndex: Int,
    point: PdfNormalizedPoint
): Boolean {
    val charBounds = textPageData(pageIndex).chars
        .visiblePdfTextBounds()
        .firstOrNull { it.index == charIndex }
        ?: return false
    val horizontalPadding = maxOf((charBounds.right - charBounds.left) * 2f, 0.025f)
    val verticalPadding = maxOf((charBounds.bottom - charBounds.top) * 0.65f, 0.006f)
    return point.x in (charBounds.left - horizontalPadding)..(charBounds.right + horizontalPadding) &&
        point.y in (charBounds.top - verticalPadding)..(charBounds.bottom + verticalPadding)
}

private fun Char.isDesktopPdfWordPart(): Boolean {
    return isLetterOrDigit() || this == '\'' || this == '-' || this == '_'
}

internal fun DesktopPdfDocument.selectionPreviewBetweenIndexes(
    pageIndex: Int,
    startIndex: Int,
    endIndex: Int,
    canvasSize: IntSize
): DesktopPdfTextSelection? {
    return selectionBetweenIndexes(
        pageIndex = pageIndex,
        startIndex = startIndex,
        endIndex = endIndex,
        canvasSize = canvasSize,
        useNativeBounds = false,
        includeText = false
    )
}

internal fun DesktopPdfDocument.selectionBetweenIndexes(
    pageIndex: Int,
    startIndex: Int,
    endIndex: Int,
    canvasSize: IntSize,
    useNativeBounds: Boolean = true,
    includeText: Boolean = true
): DesktopPdfTextSelection? {
    val chars = textPageData(pageIndex).chars
    if (chars.isEmpty()) return null
    val firstIndex = minOf(startIndex, endIndex)
    val lastIndex = maxOf(startIndex, endIndex)
    val selectedChars = chars.filter { it.index in firstIndex..lastIndex }
    if (selectedChars.isEmpty()) return null
    val text = if (includeText) {
        selectedChars.joinToString("") { it.char.toString() }
            .replace(DesktopPdfSelectionInlineWhitespaceRegex, " ")
            .replace(DesktopPdfSelectionBlankLinesRegex, "\n\n")
            .trim()
    } else {
        ""
    }
    if (includeText && text.isBlank()) return null
    val fallbackBounds = PdfSelectionGeometry.lineBoundsForChars(selectedChars.visiblePdfTextBounds())
    if (!includeText && fallbackBounds.isEmpty()) return null
    val nativeBounds = if (useNativeBounds) {
        DesktopPdfium.textRectsForRange(
            document = this,
            pageIndex = pageIndex,
            startIndex = firstIndex,
            endIndex = lastIndex,
            viewportWidth = canvasSize.width,
            viewportHeight = canvasSize.height
        ).map { it.toPdfPageBounds() }
            .filter { it.right > it.left && it.bottom > it.top }
            .mergePdfBoundsByLine()
    } else {
        emptyList()
    }
    return DesktopPdfTextSelection(
        text = text,
        lineBounds = nativeBounds.ifEmpty { fallbackBounds },
        startIndex = firstIndex,
        endIndex = lastIndex
    )
}

internal fun DesktopPdfTextRect.toPdfPageBounds(): PdfPageBounds {
    return PdfPageBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

internal fun SharedPdfAnnotation.toRenderablePdfAnnotations(
    document: DesktopPdfDocument,
    pageIndex: Int,
    canvasSize: IntSize
): List<SharedPdfAnnotation> {
    val startIndex = rangeStartIndex
    val endIndex = rangeEndIndex
    if (kind != PdfAnnotationKind.HIGHLIGHT || startIndex == null || endIndex == null) {
        return listOf(this)
    }
    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
        return listOf(this)
    }
    val dynamicBounds = DesktopPdfium.textRectsForRange(
        document = document,
        pageIndex = pageIndex,
        startIndex = startIndex,
        endIndex = endIndex,
        viewportWidth = canvasSize.width,
        viewportHeight = canvasSize.height
    ).map { it.toPdfPageBounds() }
        .filter { it.right > it.left && it.bottom > it.top }
        .mergePdfBoundsByLine()

    return dynamicBounds.ifEmpty { boundsList.ifEmpty { listOfNotNull(bounds) } }
        .mapIndexed { index, dynamicBounds ->
            copy(
                id = "${id}_line_$index",
                bounds = dynamicBounds
            )
        }
}

internal fun SharedPdfTextDraft.containsOffset(
    pageIndex: Int,
    offset: Offset,
    canvasSize: IntSize
): Boolean {
    if (this.pageIndex != pageIndex || canvasSize.width <= 0 || canvasSize.height <= 0) return false
    val left = bounds.left * canvasSize.width
    val right = bounds.right * canvasSize.width
    val top = bounds.top * canvasSize.height
    val bottom = bounds.bottom * canvasSize.height
    return offset.x in left..right && offset.y in top..bottom
}

internal fun List<SharedPdfAnnotation>.textAnnotationHitAt(
    pageIndex: Int,
    point: Offset,
    canvasSize: IntSize
): SharedPdfAnnotation? {
    return asReversed().firstOrNull { annotation ->
        annotation.kind == PdfAnnotationKind.TEXT &&
            annotation.pageIndex == pageIndex &&
            annotation.sharedPdfHitTest(point, canvasSize)
    }
}

internal fun List<PdfPageBounds>.mergePdfBoundsByLine(): List<PdfPageBounds> {
    return PdfSelectionGeometry.mergeBoundsByLine(this)
}

private fun List<DesktopPdfTextChar>.visiblePdfTextBounds(): List<PdfTextCharBounds> {
    return asSequence()
        .filter { it.hasBounds && !it.char.isISOControl() }
        .map { it.toPdfTextCharBounds() }
        .toList()
}

private fun DesktopPdfTextChar.toPdfTextCharBounds(): PdfTextCharBounds {
    return PdfTextCharBounds(
        index = index,
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

internal const val DesktopPdfSelectionPreviewThrottleMillis = 32L
internal const val DesktopPdfZoomCommitDebounceMillis = 260L
internal const val DesktopPdfZoomRenderDebounceMillis = 300L
internal const val DesktopPdfViewportPersistDebounceMillis = 300L
internal const val DesktopPdfPaginationPrefetchDelayMillis = 450L
internal const val DesktopPdfRenderScaleTolerance = 0.01f
internal const val DesktopPdfPaginationRenderCacheRadius = 2
private val DesktopPdfSelectionInlineWhitespaceRegex = Regex("[ \\t\\x0B\\f\\r]+")
private val DesktopPdfSelectionBlankLinesRegex = Regex("\\n{3,}")
