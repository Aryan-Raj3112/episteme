@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfSelectionHandle
import com.aryan.reader.shared.pdf.PdfTextPageSession
import com.aryan.reader.shared.pdf.PdfTextSelectionEngine
import com.aryan.reader.shared.pdf.PdfTextSelectionRange
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.teardrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

private const val LongPressCharTolerance = 5.0
private const val DragCharTolerance = 10.0
private const val DragWideYToleranceMultiplier = 1.5

private data class SharedMobilePdfTextSelectionState(
    val range: PdfTextSelectionRange? = null,
    val selectionRects: List<Rect> = emptyList(),
    val selectedText: String? = null,
    val menuAnchor: Rect? = null
)

@Composable
internal fun SharedMobilePdfTextSelectionOverlay(
    book: BookItem,
    pageIndex: Int,
    canvasSize: IntSize,
    selectedTool: PdfInkTool,
    modifier: Modifier = Modifier
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val session = rememberPdfTextPageSession(book, pageIndex)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val teardropWidthDp = 24.dp
    val teardropHeightDp = 24.dp
    val teardropWidthPx = with(density) { teardropWidthDp.toPx() }
    val teardropHeightPx = with(density) { teardropHeightDp.toPx() }

    androidx.compose.runtime.LaunchedEffect(book.path, pageIndex, canvasSize) {
        selLog { "overlay mount page=$pageIndex canvas=${canvasSize.width}x${canvasSize.height} tool=$selectedTool session=${session != null} pageChars=${session?.pageCharCount ?: -1}" }
    }

    var state by remember(book.path, pageIndex) {
        mutableStateOf(SharedMobilePdfTextSelectionState())
    }

    fun boundsToCanvas(bounds: PdfPageBounds): Rect {
        return Rect(
            left = bounds.left * canvasSize.width,
            top = bounds.top * canvasSize.height,
            right = bounds.right * canvasSize.width,
            bottom = bounds.bottom * canvasSize.height
        )
    }

    fun applyRangeUpdate(
        range: PdfTextSelectionRange?,
        rects: List<Rect>,
        text: String?
    ) {
        if (range == null) {
            selLog { "applyRangeUpdate: clear" }
            state = SharedMobilePdfTextSelectionState()
            return
        }
        val anchor = if (rects.isNotEmpty()) {
            val first = rects.first()
            var left = first.left
            var top = first.top
            var right = first.right
            var bottom = first.bottom
            rects.drop(1).forEach { rect ->
                left = minOf(left, rect.left)
                top = minOf(top, rect.top)
                right = maxOf(right, rect.right)
                bottom = maxOf(bottom, rect.bottom)
            }
            Rect(left, top, right, bottom)
        } else null
        selLog { "applyRangeUpdate: range=${range.start}..${range.end} rects=${rects.size} textLen=${text?.length ?: 0}" }
        state = state.copy(
            range = range,
            selectionRects = rects,
            selectedText = text,
            menuAnchor = anchor
        )
    }

    suspend fun computeAndApply(range: PdfTextSelectionRange) {
        val s = session ?: run {
            selLog { "computeAndApply: no session" }; return
        }
        val coerced = range.coerced(s.pageCharCount)
        selLog { "computeAndApply: coerced=${coerced.start}..${coerced.end} pageChars=${s.pageCharCount}" }
        val rects = withContext(Dispatchers.Default) {
            s.rectsForRangeNormalized(coerced.start, coerced.length)
                .map(::boundsToCanvas)
                .filter { it.width > 0f && it.height > 0f }
        }
        val text = withContext(Dispatchers.Default) {
            s.textForRange(coerced.start, coerced.length)?.takeIf { it.isNotBlank() }
        }
        selLog { "computeAndApply: rects=${rects.size} firstRect=${rects.firstOrNull()} textLen=${text?.length ?: 0}" }
        applyRangeUpdate(coerced, rects, text)
    }

    suspend fun startNewSelectionAt(touchOffset: Offset): Boolean {
        val s = session ?: run {
            selLog { "startNewSelectionAt: no session" }; return false
        }
        val normX = (touchOffset.x / canvasSize.width).coerceIn(0f, 1f)
        val normY = (touchOffset.y / canvasSize.height).coerceIn(0f, 1f)
        selLog { "startNewSelectionAt: touch=(${touchOffset.x},${touchOffset.y}) norm=($normX,$normY) pageChars=${s.pageCharCount}" }
        val charIndex = withContext(Dispatchers.Default) {
            s.charIndexAtNormalized(
                normX = normX,
                normY = normY,
                xTolerance = LongPressCharTolerance,
                yTolerance = LongPressCharTolerance
            )
        }
        selLog { "startNewSelectionAt: charIndex=$charIndex" }
        if (charIndex < 0) return false
        val word = PdfTextSelectionEngine.wordBoundaries(s, charIndex) ?: run {
            selLog { "startNewSelectionAt: wordBoundaries=null" }; return false
        }
        selLog { "startNewSelectionAt: word=${word.start}..${word.end}" }
        computeAndApply(word)
        return true
    }

    // Decide tap handling: only when an active selection or menu exists do we
    // consume a single tap (to dismiss it). When there is nothing selected, a
    // tap must fall through to the parent (which toggles chrome / turns pages),
    // matching Android's behavior where onSingleTap fires for plain taps.
    val hasActiveSelection = state.range != null || state.menuAnchor != null
    val tapDetector: Modifier = if (selectedTool != PdfInkTool.NONE) {
        Modifier
    } else if (hasActiveSelection) {
        // Selection/menu active: tap dismisses; long-press starts a new word.
        Modifier.pointerInput(book.path, pageIndex, canvasSize) {
            detectTapGestures(
                onLongPress = { offset ->
                    selLog { "longPress at canvas=(${offset.x},${offset.y})" }
                    scope.launch { startNewSelectionAt(offset) }
                },
                onTap = {
                    selLog { "tap -> clear (selection/menu was active)" }
                    applyRangeUpdate(null, emptyList(), null)
                }
            )
        }
    } else {
        // No selection present: only long-press matters; taps fall through to
        // the parent (chrome toggle / page turn). `detectLongPressOnly` is a
        // custom detector that does NOT consume quick taps, ensuring the
        // parent's `detectTapGestures` / `Modifier.clickable` still fires.
        Modifier.pointerInput(book.path, pageIndex, canvasSize, teardropWidthPx, teardropHeightPx) {
            detectLongPressOnly { offset ->
                selLog { "longPress at canvas=(${offset.x},${offset.y})" }
                scope.launch { startNewSelectionAt(offset) }
            }
        }
    }

    // Live handle hit-rects (canvas-space). Captured into a snapshot so the
    // eager drag pointerInput always sees the latest anchors when its handler
    // re-runs on every recomposition (keyed only on book+page+canvas, not on
    // the selection state — so an in-flight gesture is never cancelled when
    // the anchors float during the drag).
    var dragPointerId: Any? by remember { mutableStateOf<Any?>(null) }
    var dragHandle: PdfSelectionHandle? by remember { mutableStateOf<PdfSelectionHandle?>(null) }

    val touchExpansionDp = 8.dp
    val touchExpansionPx = with(density) { touchExpansionDp.toPx() }
    val touchW = teardropWidthPx + touchExpansionPx
    val touchH = teardropHeightPx + touchExpansionPx

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(tapDetector)
            .pointerInput(book.path, pageIndex, canvasSize, teardropWidthPx, teardropHeightPx, touchExpansionPx) {
                // Eager drag routed entirely in canvas coords. This matches
                // Android's PdfPageComposable pattern: hit-test handle rects at
                // down time; consume immediately in the Initial pass so the
                // parent LazyColumn / HorizontalPager can't intercept scrolls.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val pos = down.position // canvas-space (this Box == canvas)
                    val currentRects = state.selectionRects
                    if (currentRects.isEmpty()) {
                        // not our gesture; let parent deal with it
                        return@awaitEachGesture
                    }
                    val startAnchor = Offset(currentRects.first().left, currentRects.first().bottom)
                    val endAnchor = Offset(currentRects.last().right, currentRects.last().bottom)
                    val startHit = Rect(
                        left = startAnchor.x - touchW / 2f,
                        top = startAnchor.y,
                        right = startAnchor.x + touchW / 2f,
                        bottom = startAnchor.y + touchH
                    )
                    val endHit = Rect(
                        left = endAnchor.x - touchW / 2f,
                        top = endAnchor.y,
                        right = endAnchor.x + touchW / 2f,
                        bottom = endAnchor.y + touchH
                    )
                    selLog { "drag.down canvas=(${pos.x},${pos.y}) startHit=$startHit endHit=$endHit" }
                    val handle: PdfSelectionHandle? = when {
                        startHit.contains(pos) -> PdfSelectionHandle.START
                        endHit.contains(pos) -> PdfSelectionHandle.END
                        else -> null
                    }
                    if (handle == null) {
                        selLog { "drag.down outside handles -> not consumed" }
                        return@awaitEachGesture
                    }
                    selLog { "drag.down hit=$handle -> consume (Initial pass)" }
                    down.consume()
                    dragPointerId = down.id
                    dragHandle = handle
                    val pointerId = down.id
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null) {
                                selLog { "drag[$handle] change=null -> end" }
                                break
                            }
                            if (change.changedToUp()) {
                                selLog { "drag[$handle] up canvas=(${change.position.x},${change.position.y})" }
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                val fingerCanvas = change.position
                                selLog { "drag[$handle] move canvas=(${fingerCanvas.x},${fingerCanvas.y})" }
                                val s = session
                                val currentRange = state.range
                                if (s == null || currentRange == null) {
                                    selLog { "drag[$handle] missing session/range in loop" }
                                    continue
                                }
                                val normX = (fingerCanvas.x / canvasSize.width).coerceIn(0f, 1f)
                                val normY = (fingerCanvas.y / canvasSize.height).coerceIn(0f, 1f)
                                scope.launch {
                                    val charIndex = withContext(Dispatchers.Default) {
                                        s.charIndexAtNormalized(
                                            normX = normX, normY = normY,
                                            xTolerance = DragCharTolerance,
                                            yTolerance = DragCharTolerance * DragWideYToleranceMultiplier
                                        )
                                    }
                                    selLog { "drag[$handle] charIndex=$charIndex" }
                                    if (charIndex < 0) return@launch
                                    val update = PdfTextSelectionEngine.extendRange(
                                        backend = s,
                                        current = currentRange.coerced(s.pageCharCount),
                                        activeHandle = handle,
                                        newCharIndex = charIndex
                                    )
                                    selLog { "drag[$handle] update=${update.range.start}..${update.range.end} handle=${update.activeHandle}" }
                                    computeAndApply(update.range)
                                }
                            }
                        }
                    } finally {
                        dragPointerId = null
                        dragHandle = null
                        selLog { "drag[$handle] gesture ended" }
                    }
                }
            }
    ) {
        if (state.selectionRects.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                state.selectionRects.forEach { rect ->
                    drawRect(
                        color = Color(0x663399FF),
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                }
            }
        }

        if (state.range != null && state.selectionRects.isNotEmpty()) {
            val first = state.selectionRects.first()
            val last = state.selectionRects.last()
            val startPos = Offset(first.left, first.bottom)
            val endPos = Offset(last.right, last.bottom)
            val handleColor = Color.Blue
            val colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(handleColor)
            val teardropPainter = painterResource(Res.drawable.teardrop)
            Canvas(Modifier.fillMaxSize()) {
                drawHandle(teardropPainter, startPos, teardropWidthPx, teardropHeightPx, tiltDeg = 30f, colorFilter)
                drawHandle(teardropPainter, endPos, teardropWidthPx, teardropHeightPx, tiltDeg = -30f, colorFilter)
            }
        }
    }

    val anchor = state.menuAnchor
    val selectedText = state.selectedText
    if (anchor != null && selectedText != null && selectedText.isNotBlank()) {
        Popup(
            popupPositionProvider = SharedMobilePdfSelectionMenuPositionProvider(anchor),
            onDismissRequest = { selLog { "popup onDismissRequest (ignored — not clearing selection automatically)" } },
            properties = PopupProperties(focusable = false)
        ) {
            SharedMobilePdfSelectionMenu(
                selectedText = selectedText,
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    applyRangeUpdate(null, emptyList(), null)
                },
                onTranslate = { text ->
                    openSharedMobileExternalUrl("https://translate.google.com/?text=${encodeQuery(text)}")
                    applyRangeUpdate(null, emptyList(), null)
                },
                onSearch = { text ->
                    openSharedMobileExternalUrl("https://www.google.com/search?q=${encodeQuery(text)}")
                    applyRangeUpdate(null, emptyList(), null)
                }
            )
        }
    }
}

private fun encodeQuery(text: String): String {
    val trimmed = text.trim()
    val builder = StringBuilder(trimmed.length)
    for (ch in trimmed) {
        when {
            ch == ' ' -> builder.append('+')
            ch == '\n' -> builder.append('+')
            ch.isLetterOrDigit() -> builder.append(ch)
            else -> {
                val code = ch.code
                if (code < 128) {
                    builder.append('%')
                    builder.append(code.toString(16).uppercase().padStart(2, '0'))
                } else {
                    builder.append(ch)
                }
            }
        }
    }
    return builder.toString()
}

/**
 * Draw a teardrop handle whose tip sits on [anchor]. Mirrors Android's
 * `PdfPageComposable.kt:5363-5392` layout: `translate(position.x - w/2,
 * position.y)` then `rotate(±30°, pivot = (w/2, 0))`. Tinted blue.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    painter: androidx.compose.ui.graphics.painter.Painter,
    anchor: Offset,
    teardropWidthPx: Float,
    teardropHeightPx: Float,
    tiltDeg: Float,
    colorFilter: androidx.compose.ui.graphics.ColorFilter?
) {
    translate(left = anchor.x - teardropWidthPx / 2f, top = anchor.y) {
        rotate(degrees = tiltDeg, pivot = Offset(teardropWidthPx / 2f, 0f)) {
            with(painter) {
                draw(
                    size = Size(teardropWidthPx, teardropHeightPx),
                    colorFilter = colorFilter
                )
            }
        }
    }
}

private const val SEL_LOG_TAG = "PdfTextSelection"
internal fun selLog(message: () -> String) {
    println("[$SEL_LOG_TAG] ${message()}")
}

/**
 * Detector that fires `onLongPress` *only* after the finger stays stationary
 * for `ViewConfiguration.longPressTimeoutMillis`. Doesn't consume on quick tap
 * (so parent `Modifier.clickable` / `detectTapGestures.onTap` still fires to
 * toggle chrome), and doesn't consume on early move/scroll (lets the parent's
 * scrollable / pager receive the drag). Only on long-press does it consume the
 * event so the parent won't interpret the gesture as a tap afterward.
 *
 * Mirrors Android docs for `View.setOnLongClickListener`: presses shorter than
 * the long-press timeout are reported as taps/scrolls to the parent.
 */
private suspend fun PointerInputScope.detectLongPressOnly(
    onLongPress: (Offset) -> Unit
) {
    val viewConfig = viewConfiguration
    val longPressDelay = viewConfig.longPressTimeoutMillis
    val touchSlop = viewConfig.touchSlop
    selLog { "detectLongPressOnly installed (longPress=${longPressDelay}ms slop=${touchSlop})" }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
        selLog { "longPress.down at canvas=(${down.position.x},${down.position.y})" }
        try {
            withTimeout(longPressDelay) {
                waitForUpOrCancellation()
            }
        } catch (_: androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException) {
            down.consume()
            selLog { "longPress.fired after ${longPressDelay}ms at canvas=(${down.position.x},${down.position.y}) -> consume" }
            onLongPress(down.position)
        }
    }
}

private class SharedMobilePdfSelectionMenuPositionProvider(
    private val anchor: Rect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val centerX = (anchor.left + anchor.right) / 2f
        val x = (centerX - popupContentSize.width / 2f)
            .coerceIn(0f, (windowSize.width - popupContentSize.width).toFloat())
        val gap = 8f
        val yAbove = (anchor.top - gap - popupContentSize.height).coerceAtLeast(0f)
        return IntOffset(x.roundToInt(), yAbove.roundToInt())
    }
}

@Composable
private fun SharedMobilePdfSelectionMenu(
    selectedText: String,
    onCopy: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SharedMobilePdfSelectionMenuAction(icon = Icons.Default.ContentCopy, label = "Copy") {
                onCopy(selectedText)
            }
            SharedMobilePdfSelectionMenuAction(icon = Icons.Default.Translate, label = "Translate") {
                onTranslate(selectedText)
            }
            SharedMobilePdfSelectionMenuAction(icon = Icons.Default.Search, label = "Search") {
                onSearch(selectedText)
            }
        }
    }
}

@Composable
private fun SharedMobilePdfSelectionMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}