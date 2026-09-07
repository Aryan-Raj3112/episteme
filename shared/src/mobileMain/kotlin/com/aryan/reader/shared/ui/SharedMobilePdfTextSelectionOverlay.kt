@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.readerExternalLookupActionsAvailable
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfLinkTarget
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfSelectionHandle
import com.aryan.reader.shared.pdf.PdfTextPageSession
import com.aryan.reader.shared.pdf.PdfTextSelectionEngine
import com.aryan.reader.shared.pdf.PdfTextProcessing
import com.aryan.reader.shared.pdf.PdfTextSelectionRange
import com.aryan.reader.shared.pdf.pdfLinkLog
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.copy
import com.aryan.reader.shared.generated.resources.font_background
import com.aryan.reader.shared.generated.resources.format_underlined
import com.aryan.reader.shared.generated.resources.format_underlined_squiggle
import com.aryan.reader.shared.generated.resources.select_all
import com.aryan.reader.shared.generated.resources.strikethrough
import com.aryan.reader.shared.generated.resources.teardrop
import com.aryan.reader.shared.generated.resources.translate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
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
    password: String? = null,
    textSession: com.aryan.reader.shared.pdf.PdfTextPageSession?,
    canvasSize: IntSize,
    selectedTool: PdfInkTool,
    pageRender: SharedMobilePdfPageRender?,
    zoomTiles: List<SharedMobilePdfTileRender>,
    zoomScale: Float,
    magnifierColorFilter: androidx.compose.ui.graphics.ColorFilter?,
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    existingHighlights: List<SharedPdfAnnotation>,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int) -> Unit,
    onAiDefine: ((String) -> Unit)? = null,
    onClipboardError: ((String) -> Unit)? = null,
    onSelectionDragActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val session = textSession
    val linkBounds = remember(session) { session?.linkBoundsNormalized().orEmpty() }
    val scope = rememberCoroutineScope()
    val copiedTextLabel = readerString("clip_label_copied_text", "Copied Text")
    val clipboardErrorMessage = readerString("error_copy_to_clipboard", "Could not copy to clipboard")
    val density = LocalDensity.current
    // Android parity (PdfPageComposable teardropWidthPxState): handles stay a
    // constant 24dp on screen at any zoom by dividing by the visual scale.
    // The overlay lives inside the zoom viewport's scaled layer, so without
    // this both the teardrops and their hit rects grow with the zoom.
    val zoomForHandles = zoomScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val teardropWidthDp = 24.dp
    val teardropHeightDp = 24.dp
    var teardropWidthPx = with(density) { teardropWidthDp.toPx() } / zoomForHandles
    val teardropHeightPx = with(density) { teardropHeightDp.toPx() } / zoomForHandles

    androidx.compose.runtime.LaunchedEffect(book.path, pageIndex, password, canvasSize) {
        selLog { "overlay mount page=$pageIndex canvas=${canvasSize.width}x${canvasSize.height} tool=$selectedTool session=${session != null} pageChars=${session?.pageCharCount ?: -1}" }
    }

    var state by remember(book.path, pageIndex, password) {
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

    // Android parity (PdfPageComposable mergedSelectionRects): glyph rects are
    // merged into line rects before display so the highlight is a continuous
    // band per line instead of per-glyph boxes with seams.
    fun mergeCanvasRectsIntoLines(rects: List<Rect>): List<Rect> {
        if (rects.isEmpty()) return rects
        return PdfTextProcessing.mergeScreenBoundsIntoLines(
            rects.map { PdfPageBounds(it.left, it.top, it.right, it.bottom) }
        ).map { Rect(it.left, it.top, it.right, it.bottom) }
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
        val displayRects = mergeCanvasRectsIntoLines(rects)
        val anchor = if (displayRects.isNotEmpty()) {
            val first = displayRects.first()
            var left = first.left
            var top = first.top
            var right = first.right
            var bottom = first.bottom
            displayRects.drop(1).forEach { rect ->
                left = minOf(left, rect.left)
                top = minOf(top, rect.top)
                right = maxOf(right, rect.right)
                bottom = maxOf(bottom, rect.bottom)
            }
            Rect(left, top, right, bottom)
        } else null
        selLog { "applyRangeUpdate: range=${range.start}..${range.end} rects=${rects.size}->${displayRects.size} textLen=${text?.length ?: 0}" }
        state = state.copy(
            range = range,
            selectionRects = displayRects,
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
        val rects = s.rectsForRangeNormalized(coerced.start, coerced.length)
            .map(::boundsToCanvas)
            .filter { it.width > 0f && it.height > 0f }
        val text = s.textForRange(coerced.start, coerced.length)?.takeIf { it.isNotBlank() }
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
        val charIndex = s.charIndexAtNormalized(
            normX = normX,
            normY = normY,
            xTolerance = LongPressCharTolerance,
            yTolerance = LongPressCharTolerance
        )
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
        // No selection present: long-press starts a new selection; quick tap
        // resolves a PDF link at the finger (if any). If no link, the tap is
        // NOT consumed so the parent's chrome toggle / page-turn still fires.
        Modifier.pointerInput(book.path, pageIndex, canvasSize, session, existingHighlights, teardropWidthPx, teardropHeightPx) {
            var pendingLinkTarget: PdfLinkTarget? = null
            var pendingHighlight: SharedPdfAnnotation? = null
            detectTapOrLongPress(
                onLongPress = { offset ->
                    selLog { "longPress at canvas=(${offset.x},${offset.y})" }
                    scope.launch { startNewSelectionAt(offset) }
                },
                shouldReserveTap = { offset ->
                    val normX = (offset.x / canvasSize.width).coerceIn(0f, 1f)
                    val normY = (offset.y / canvasSize.height).coerceIn(0f, 1f)
                    pendingHighlight = existingHighlights.lastOrNull { annotation ->
                        annotation.boundsList.ifEmpty { listOfNotNull(annotation.bounds) }.any { bounds ->
                            normX in bounds.left..bounds.right && normY in bounds.top..bounds.bottom
                        }
                    }
                    if (pendingHighlight != null) {
                        pendingLinkTarget = null
                        true
                    } else {
                    val s = session
                    if (s == null) {
                        pdfLinkLog { "tap page=$pageIndex ignored reason=session-not-ready" }
                        selLog { "tap.noSession -> not consumed" }
                        pendingLinkTarget = null
                        false
                    } else {
                        pdfLinkLog { "hit-test page=$pageIndex canvas=${offset.x},${offset.y} normalized=$normX,$normY" }
                        selLog { "tap.link.lookup canvas=(${offset.x},${offset.y}) norm=($normX,$normY)" }
                        pendingLinkTarget = s.linkAtNormalized(normX, normY)
                        pdfLinkLog { "hit-test-result page=$pageIndex target=$pendingLinkTarget" }
                        selLog { "tap.link.target=$pendingLinkTarget" }
                        pendingLinkTarget != null
                    }
                    }
                },
                onReservedTap = {
                    val highlight = pendingHighlight
                    if (highlight != null) {
                        onExistingHighlightTap(highlight)
                    } else when (val target = pendingLinkTarget) {
                        is PdfLinkTarget.ExternalUrl -> {
                            pdfLinkLog { "navigate-external page=$pageIndex url=${target.url}" }
                            onExternalLink(target.url)
                        }
                        is PdfLinkTarget.InternalPage -> {
                            pdfLinkLog { "navigate-internal from=$pageIndex to=${target.pageIndex}" }
                            onInternalLink(target.pageIndex)
                        }
                        null -> Unit
                    }
                    pendingHighlight = null
                    pendingLinkTarget = null
                }
            )
        }
    }

    if (linkBounds.isNotEmpty()) {
        val linkColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        Canvas(Modifier.fillMaxSize()) {
            linkBounds.forEach { bounds ->
                drawRect(
                    color = linkColor,
                    topLeft = Offset(bounds.left * size.width, bounds.top * size.height),
                    size = Size(
                        (bounds.right - bounds.left) * size.width,
                        (bounds.bottom - bounds.top) * size.height
                    )
                )
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
    // Window rect of the drag Box, used only for drag-coordinate diagnostics:
    // it lets us tell which space move positions arrive in.
    var overlayWindowRect by remember { mutableStateOf<Rect?>(null) }
    // Live layout coordinates of the drag Box. Unlike the snapshot window
    // rect, localToWindow() on these accounts for the zoom viewport's
    // graphicsLayer scale + pan + Center pivot at call time, which is what
    // the selection-menu anchor mapping needs at any zoom (Android parity:
    // contentToScreen + LayoutCoordinates.localToWindow).
    var overlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Generation guard so a previous gesture's drain/cleanup cannot clobber a
    // newer in-flight drag (cleanup runs async after the worker drains).
    var dragGeneration by remember { mutableStateOf(0) }

    var showMagnifier by remember { mutableStateOf(false) }
    var magnifierActiveHandle by remember { mutableStateOf<PdfSelectionHandle?>(null) }
    val latestOnSelectionDragActiveChange by rememberUpdatedState(onSelectionDragActiveChange)

    // Android-exact magnifier metrics: 120x60dp lens, 24dp above the handle,
    // centered on the handle x, sampling 2x of the on-screen content.
    val magnifierWidthDp = 120.dp
    val magnifierHeightDp = 60.dp
    val magnifierOffsetAboveHandleDp = 24.dp

    val touchExpansionDp = 8.dp
    val touchExpansionPx = with(density) { touchExpansionDp.toPx() } / zoomForHandles
    val touchW = teardropWidthPx + touchExpansionPx
    val touchH = teardropHeightPx + touchExpansionPx

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayWindowRect = it.boundsInWindow()
                overlayCoordinates = it
            }
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
                        pdfTextDragLog {
                            "down page=$pageIndex gen=${dragGeneration + 1} result=miss " +
                                "pos=(${pos.x},${pos.y}) canvas=${canvasSize.width}x${canvasSize.height} " +
                                "zoom=$zoomScale startHit=$startHit endHit=$endHit " +
                                "range=${state.range} rects=${currentRects.size}"
                        }
                        selLog { "drag.down outside handles -> not consumed" }
                        return@awaitEachGesture
                    }
                    selLog { "drag.down hit=$handle -> consume (Initial pass)" }
                    down.consume()
                    dragPointerId = down.id
                    dragHandle = handle
                    showMagnifier = true
                    magnifierActiveHandle = handle
                    val myGeneration = dragGeneration + 1
                    dragGeneration = myGeneration
                    pdfTextDragLog {
                        "down page=$pageIndex gen=$myGeneration result=hit handle=$handle " +
                            "pos=(${pos.x},${pos.y}) canvas=${canvasSize.width}x${canvasSize.height} " +
                            "zoom=$zoomScale range=${state.range} rects=${currentRects.size} " +
                            "touchW=$touchW touchH=$touchH sessionChars=${session?.pageCharCount ?: -1} " +
                            "winRect=$overlayWindowRect"
                    }
                    // Android parity (PdfPageComposable activeDraggingHandle):
                    // zoom/pan is disabled while a handle drag is in flight.
                    latestOnSelectionDragActiveChange(true)
                    val pointerId = down.id
                    // Android parity (PdfPageComposable dragWorker): a single
                    // conflated worker processes moves sequentially off the
                    // main thread. Per-move launches raced (out-of-order
                    // commits snapped the selection back, so drags stuck).
                    val dragChannel = Channel<PdfTextDragMove>(Channel.CONFLATED)
                    var sendSeq = 0L
                    var probeMoves = 0
                    // Self-calibration for the iOS move-coordinate jump: the
                    // first move's previousPosition is always the down
                    // position, so a huge jump between them is physically
                    // impossible finger motion and must be a coordinate-space
                    // offset. Subtract it for the rest of this gesture only.
                    var gestureSpaceOffset: Offset? = null
                    val downTimeMs = down.uptimeMillis
                    var appliedSeq = -1L
                    var appliedRange: PdfTextSelectionRange? = null
                    val gestureStartMs = currentTimestamp()
                    val dragWorker = scope.launch(Dispatchers.Default) {
                        pdfTextDragLog { "worker-start page=$pageIndex gen=$myGeneration" }
                        for (move in dragChannel) {
                            val recvMs = currentTimestamp()
                            val s = session
                            if (s == null) {
                                pdfTextDragLog { "worker-recv page=$pageIndex gen=$myGeneration seq=${move.seq} result=no-session" }
                                continue
                            }
                            val base = state.range
                            if (base == null) {
                                pdfTextDragLog { "worker-recv page=$pageIndex gen=$myGeneration seq=${move.seq} result=no-range" }
                                continue
                            }
                            val fingerCanvas = move.offset
                            val normX = (fingerCanvas.x / canvasSize.width).coerceIn(0f, 1f)
                            val normY = (fingerCanvas.y / canvasSize.height).coerceIn(0f, 1f)
                            val lookupStartMs = currentTimestamp()
                            var charIndex = s.charIndexAtNormalized(
                                normX = normX, normY = normY,
                                xTolerance = DragCharTolerance,
                                yTolerance = DragCharTolerance * DragWideYToleranceMultiplier
                            )
                            var fallbackUsed = false
                            var anchorIndexForLog: Int? = null
                            if (charIndex < 0) {
                                // Android parity (wide-search fallback): when
                                // the finger is in a gap, retry across the full
                                // page width at the anchor line so the drag
                                // keeps moving forward instead of sticking.
                                val anchorIndex = if ((dragHandle ?: handle) == PdfSelectionHandle.START) {
                                    base.coerced(s.pageCharCount).start
                                } else {
                                    (base.coerced(s.pageCharCount).end - 1).coerceAtLeast(0)
                                }
                                anchorIndexForLog = anchorIndex
                                val anchorBox = s.charBoxNormalized(anchorIndex)
                                if (anchorBox != null) {
                                    fallbackUsed = true
                                    charIndex = s.charIndexAtNormalized(
                                        normX = normX,
                                        normY = ((anchorBox.top + anchorBox.bottom) / 2f).coerceIn(0f, 1f),
                                        xTolerance = 1000.0,
                                        yTolerance = DragCharTolerance * DragWideYToleranceMultiplier
                                    )
                                }
                                if (charIndex < 0) {
                                    pdfTextDragLog {
                                        "worker-recv page=$pageIndex gen=$myGeneration seq=${move.seq} " +
                                            "result=gap finger=(${fingerCanvas.x},${fingerCanvas.y}) " +
                                            "norm=($normX,$normY) fallback=$fallbackUsed " +
                                            "anchor=$anchorIndexForLog base=$base"
                                    }
                                    continue
                                }
                            }
                            // Android parity (extendRange activeHandle):
                            // crossing over swaps the active handle so
                            // the other handle continues the drag.
                            val activeForUpdate = dragHandle ?: handle
                            val update = PdfTextSelectionEngine.extendRange(
                                backend = s,
                                current = base.coerced(s.pageCharCount),
                                activeHandle = activeForUpdate,
                                newCharIndex = charIndex
                            )
                            val coerced = update.range.coerced(s.pageCharCount)
                            val rectStartMs = currentTimestamp()
                            val rects = s.rectsForRangeNormalized(coerced.start, coerced.length)
                                .map(::boundsToCanvas)
                                .filter { it.width > 0f && it.height > 0f }
                            val text = s.textForRange(coerced.start, coerced.length)
                                ?.takeIf { it.isNotBlank() }
                            val lookupMs = rectStartMs - lookupStartMs
                            val rectMs = currentTimestamp() - rectStartMs
                            val queuedFor = recvMs - gestureStartMs
                            withContext(Dispatchers.Main) {
                                // Drop results from a superseded gesture.
                                if (dragGeneration != myGeneration) {
                                    pdfTextDragLog {
                                        "worker-drop page=$pageIndex gen=$myGeneration seq=${move.seq} " +
                                            "reason=superseded currentGen=$dragGeneration"
                                    }
                                    return@withContext
                                }
                                selLog { "drag update=${coerced.start}..${coerced.end} handle=${update.activeHandle}" }
                                pdfTextDragLog {
                                    "worker-apply page=$pageIndex gen=$myGeneration seq=${move.seq} " +
                                        "char=$charIndex fallback=$fallbackUsed anchor=$anchorIndexForLog " +
                                        "base=$base update=${coerced.start}..${coerced.end} " +
                                        "handle=$activeForUpdate->${update.activeHandle} " +
                                        "rects=${rects.size} textLen=${text?.length ?: 0} " +
                                        "lookupMs=$lookupMs rectMs=$rectMs queuedForMs=$queuedFor"
                                }
                                dragHandle = update.activeHandle
                                magnifierActiveHandle = update.activeHandle
                                appliedSeq = move.seq
                                appliedRange = coerced
                                applyRangeUpdate(coerced, rects, text)
                            }
                        }
                        pdfTextDragLog { "worker-end page=$pageIndex gen=$myGeneration appliedSeq=$appliedSeq appliedRange=$appliedRange" }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null) {
                                pdfTextDragLog { "event page=$pageIndex gen=$myGeneration type=no-change sent=$sendSeq applied=$appliedSeq" }
                                selLog { "drag[$handle] change=null -> end" }
                                break
                            }
                            if (change.changedToUp()) {
                                selLog { "drag[$handle] up canvas=(${change.position.x},${change.position.y})" }
                                pdfTextDragLog {
                                    "event page=$pageIndex gen=$myGeneration type=up " +
                                        "pos=(${change.position.x},${change.position.y}) " +
                                        "sent=$sendSeq applied=$appliedSeq range=${state.range}"
                                }
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                sendSeq += 1
                                if (sendSeq == 1L) {
                                    val delta0 = change.position - pos
                                    val dtMs = (change.uptimeMillis - downTimeMs).coerceAtLeast(1L)
                                    val velocityPxS = delta0.getDistance() / dtMs.toFloat() * 1000f
                                    if (delta0.getDistance() > 250f && velocityPxS > 12000f) {
                                        gestureSpaceOffset = delta0
                                    }
                                    pdfTextDragLog {
                                        "calibrate page=$pageIndex gen=$myGeneration " +
                                            "delta0=(${delta0.x},${delta0.y}) dtMs=$dtMs " +
                                            "velocityPxS=$velocityPxS offset=$gestureSpaceOffset"
                                    }
                                }
                                val adjusted = gestureSpaceOffset?.let { change.position - it } ?: change.position
                                selLog { "drag[$handle] move canvas=(${adjusted.x},${adjusted.y})" }
                                val result = dragChannel.trySend(PdfTextDragMove(sendSeq, adjusted))
                                pdfTextDragLog {
                                    "event page=$pageIndex gen=$myGeneration type=move seq=$sendSeq " +
                                        "pos=(${change.position.x},${change.position.y}) " +
                                        "adjusted=(${adjusted.x},${adjusted.y}) " +
                                        "sendOk=${result.isSuccess} sendClosed=${result.isClosed}"
                                }
                                // Probe the coordinate space of the first few
                                // moves: log previous position plus the
                                // calibrated position actually used.
                                if (probeMoves < 3) {
                                    probeMoves += 1
                                    val rect = overlayWindowRect
                                    val prev = change.previousPosition
                                    pdfTextDragLog {
                                        "probe page=$pageIndex gen=$myGeneration seq=$sendSeq " +
                                            "pos=(${change.position.x},${change.position.y}) " +
                                            "adjusted=(${adjusted.x},${adjusted.y}) " +
                                            "prev=(${prev.x},${prev.y}) down=(${pos.x},${pos.y}) " +
                                            "changes=${event.changes.size} " +
                                            "winRect=$rect offset=$gestureSpaceOffset"
                                    }
                                }
                            }
                        }
                    } finally {
                        dragChannel.close()
                        // Drain the latest move before clearing drag state so
                        // the final position is not lost. join() is a member
                        // suspend fun and cannot run in this restricted
                        // pointer-input scope, so defer the drain + cleanup.
                        val worker = dragWorker
                        val sent = sendSeq
                        pdfTextDragLog {
                            "cleanup page=$pageIndex gen=$myGeneration closing sent=$sent applied=$appliedSeq"
                        }
                        scope.launch(Dispatchers.Main) {
                            runCatching { worker.join() }
                            if (dragGeneration != myGeneration) {
                                pdfTextDragLog {
                                    "cleanup page=$pageIndex gen=$myGeneration result=superseded " +
                                        "currentGen=$dragGeneration"
                                }
                                return@launch
                            }
                            dragPointerId = null
                            dragHandle = null
                            showMagnifier = false
                            magnifierActiveHandle = null
                            latestOnSelectionDragActiveChange(false)
                            pdfTextDragLog {
                                "cleanup page=$pageIndex gen=$myGeneration result=done " +
                                    "sent=$sent applied=$appliedSeq range=${state.range}"
                            }
                            selLog { "drag[$handle] gesture ended" }
                        }
                    }
                }
            }
    ) {
        if (state.selectionRects.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                state.selectionRects.forEach { rect ->
                    drawRect(
                        // Android parity (selectionHighlightColor).
                        color = Color(0x6633B5E5),
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
    // Hide the menu while a handle drag is in flight, matching Android's
    // shouldShowPdfSelectionMenu (menu only when no active handle drag).
    val isHandleDragging = dragHandle != null
    if (anchor != null && selectedText != null && selectedText.isNotBlank() && !isHandleDragging) {
        // Snapshot the coordinates for the menu provider: localToWindow() on
        // these maps canvas points through the live zoom transform.
        val menuCoordinates = overlayCoordinates
        Popup(
            popupPositionProvider = SharedMobilePdfSelectionMenuPositionProvider(
                anchor = anchor,
                canvasSize = canvasSize,
                marginPx = with(density) { 16.dp.toPx() },
                mapToWindow = { offset -> menuCoordinates?.localToWindow(offset) }
            ),
            onDismissRequest = { selLog { "popup onDismissRequest (ignored — not clearing selection automatically)" } },
            properties = PopupProperties(focusable = false)
        ) {
            SharedMobilePdfSelectionMenu(
                selectedText = selectedText,
                onHighlight = { colorArgb, style, addNote ->
                    val range = state.range ?: return@SharedMobilePdfSelectionMenu
                    val bounds = state.selectionRects.map { rect ->
                        PdfPageBounds(
                            left = (rect.left / canvasSize.width).coerceIn(0f, 1f),
                            top = (rect.top / canvasSize.height).coerceIn(0f, 1f),
                            right = (rect.right / canvasSize.width).coerceIn(0f, 1f),
                            bottom = (rect.bottom / canvasSize.height).coerceIn(0f, 1f)
                        )
                    }
                    onHighlight(range, selectedText, bounds, colorArgb, style, addNote)
                    applyRangeUpdate(null, emptyList(), null)
                },
                onCopy = { text ->
                    val result = writeSharedClipboard(copiedTextLabel, text)
                    if (!result.success) onClipboardError?.invoke(clipboardErrorMessage)
                    applyRangeUpdate(null, emptyList(), null)
                },
                onDefine = { text ->
                    openSharedMobileEpubLookup(ReaderExternalLookupAction.DICTIONARY, text)
                    applyRangeUpdate(null, emptyList(), null)
                },
                onTranslate = { text ->
                    openSharedMobileEpubLookup(ReaderExternalLookupAction.TRANSLATE, text)
                    applyRangeUpdate(null, emptyList(), null)
                },
                onSearch = { text ->
                    openSharedMobileEpubLookup(ReaderExternalLookupAction.SEARCH, text)
                    applyRangeUpdate(null, emptyList(), null)
                },
                onReadAloud = {
                    state.range?.let { onReadAloud(it.start) }
                    applyRangeUpdate(null, emptyList(), null)
                },
                onAiDefine = onAiDefine,
                onSelectAll = {
                    val s = session ?: return@SharedMobilePdfSelectionMenu
                    scope.launch { computeAndApply(PdfTextSelectionRange(0, s.pageCharCount)) }
                }
            )
        }
    }

    val magnifierHandle = magnifierActiveHandle
    val magnifierBitmap = pageRender?.bitmap
    val visibleSelectionRects = state.selectionRects
    if (showMagnifier && magnifierHandle != null && magnifierBitmap != null && visibleSelectionRects.isNotEmpty()) {
        val handle = magnifierHandle
        val handleRect = when (handle) {
            PdfSelectionHandle.START -> visibleSelectionRects.first()
            PdfSelectionHandle.END -> visibleSelectionRects.last()
        }
        val handleX = when (handle) {
            PdfSelectionHandle.START -> handleRect.left
            PdfSelectionHandle.END -> handleRect.right
        }
        val handleY = handleRect.bottom
        // The lens is drawn inside the (graphicsLayer-scaled) overlay, so its
        // canvas-local size is divided by the zoom and the inverse scale is
        // applied afterwards — the lens renders at a constant 120x60dp on
        // screen and samples 2x of the on-screen content at any zoom level.
        val zoom = zoomScale.takeIf { it.isFinite() && it > 1f } ?: 1f
        val lensVisualWidthPx = with(density) { magnifierWidthDp.toPx() }
        val lensVisualHeightPx = with(density) { magnifierHeightDp.toPx() }
        val gapVisualPx = with(density) { magnifierOffsetAboveHandleDp.toPx() }
        val lensCanvasWidthPx = lensVisualWidthPx / zoom
        val lensCanvasHeightPx = lensVisualHeightPx / zoom
        val gapCanvasPx = gapVisualPx / zoom
        val lensLeft = handleX - lensCanvasWidthPx / 2f
        val lensTop = handleY - lensCanvasHeightPx - gapCanvasPx
        // Content space is the canvas (page fit) size at scale 1 — Android's
        // "targetWidth" space — so the sampling center is the canvas handle
        // position directly: (handle x, selection rect center y).
        val magnifierCenter = Offset(handleX, handleRect.center.y)
        Box(
            Modifier
                .offset { IntOffset(lensLeft.roundToInt(), lensTop.roundToInt()) }
                .width(with(density) { lensCanvasWidthPx.toDp() })
                .height(with(density) { lensCanvasHeightPx.toDp() })
                .graphicsLayer {
                    scaleX = 1f / zoom
                    scaleY = 1f / zoom
                }
        ) {
            SharedPdfMagnifier(
                sourceBitmap = magnifierBitmap,
                tiles = zoomTiles,
                currentScale = zoomScale,
                magnifierCenterOnBitmap = magnifierCenter,
                contentWidthPx = canvasSize.width,
                contentHeightPx = canvasSize.height,
                selectionRectsInContentCoords = visibleSelectionRects,
                highlightColor = Color(0x6633B5E5),
                colorFilter = magnifierColorFilter
            )
        }
    }
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
 * Dedicated drag-diagnostics log with a single greppable tag.
 * Filter logcat / Xcode output for `PdfTextDrag` and attach it when
 * reporting that a handle drag sticks or jumps.
 */
private const val PDF_TEXT_DRAG_LOG_TAG = "PdfTextDrag"
internal fun pdfTextDragLog(message: () -> String) {
    println("[$PDF_TEXT_DRAG_LOG_TAG] ${message()}")
}

private data class PdfTextDragMove(val seq: Long, val offset: Offset)

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
private suspend fun PointerInputScope.detectTapOrLongPress(
    onLongPress: (Offset) -> Unit,
    shouldReserveTap: (Offset) -> Boolean,
    onReservedTap: () -> Unit
) {
    val longPressDelay = viewConfiguration.longPressTimeoutMillis
    selLog { "detectTapOrLongPress installed (longPress=${longPressDelay}ms)" }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        selLog { "tapLong.down at canvas=(${down.position.x},${down.position.y})" }
        val reservedForLink = shouldReserveTap(down.position)
        if (reservedForLink) {
            down.consume()
            pdfLinkLog { "gesture-reserved pointer=${down.id} phase=down" }
        }
        val up = try {
            withTimeout(longPressDelay) {
                // Link taps reserve their down event during Initial. Observe the release in the
                // same pass so the parent reader's tap/page-turn detector cannot consume it first
                // and turn a valid PDF link into a cancellation.
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
        } catch (_: androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException) {
            // Long-press triggered: finger stayed down past the threshold.
            down.consume()
            selLog { "tapLong.longPress.fired at canvas=(${down.position.x},${down.position.y}) -> consume" }
            onLongPress(down.position)
            return@awaitEachGesture
        }
        // Finger lifted before long-press: this is a tap. Ask caller whether to
        // consume (e.g. link click should consume, plain empty tap should not
        if (up == null) {
            pdfLinkLog { "gesture-cancelled reserved=$reservedForLink" }
            selLog { "tapLong.cancelled" }
            return@awaitEachGesture
        }
        selLog { "tapLong.tap.reserved=$reservedForLink at canvas=(${up.position.x},${up.position.y})" }
        if (reservedForLink) {
            up.consume()
            pdfLinkLog { "gesture-reserved pointer=${up.id} phase=up dispatch=true" }
            onReservedTap()
        }
    }
}

private class SharedMobilePdfSelectionMenuPositionProvider(
    private val anchor: Rect,
    private val canvasSize: IntSize,
    private val marginPx: Float,
    private val mapToWindow: (Offset) -> Offset? = { null }
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // Prefer the framework's live mapping (accounts for the zoom
        // viewport's graphicsLayer scale + pan + Center pivot at call time).
        // Fall back to the fractional overlay-bounds mapping when coordinates
        // are not yet available. Either way the same shared
        // above > below > side > fallback policy runs on the result.
        val mappedTopLeft = mapToWindow(anchor.topLeft)
        val mappedBottomRight = mapToWindow(anchor.bottomRight)
        val selection = if (mappedTopLeft != null && mappedBottomRight != null) {
            SharedSelectionMenuRect(
                left = minOf(mappedTopLeft.x, mappedBottomRight.x),
                top = minOf(mappedTopLeft.y, mappedBottomRight.y),
                right = maxOf(mappedTopLeft.x, mappedBottomRight.x),
                bottom = maxOf(mappedTopLeft.y, mappedBottomRight.y)
            )
        } else {
            sharedPdfSelectionWindowRect(
                anchor = SharedSelectionMenuRect(anchor.left, anchor.top, anchor.right, anchor.bottom),
                canvasWidth = canvasSize.width,
                canvasHeight = canvasSize.height,
                overlayLeft = anchorBounds.left,
                overlayTop = anchorBounds.top,
                overlayWidth = anchorBounds.right - anchorBounds.left,
                overlayHeight = anchorBounds.bottom - anchorBounds.top,
            )
        }
        val placement = sharedSelectionMenuPlacement(
            viewport = SharedSelectionMenuViewport(windowSize.width, windowSize.height),
            popup = SharedSelectionMenuSize(popupContentSize.width, popupContentSize.height),
            selection = selection,
            marginPx = marginPx,
            gapPx = marginPx
        )
        println(
            "[PdfTextDrag] menu anchorCanvas=$anchor mapped=$selection " +
                "viaLiveMap=${mappedTopLeft != null} viewport=${windowSize.width}x${windowSize.height} " +
                "popup=${popupContentSize.width}x${popupContentSize.height} " +
                "placement=${placement.placement} at=(${placement.x},${placement.y})"
        )
        return IntOffset(placement.x, placement.y)
    }
}

@Composable
private fun SharedMobilePdfSelectionMenu(
    selectedText: String,
    onHighlight: (Int, HighlightStyle, Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onDefine: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onReadAloud: () -> Unit,
    onAiDefine: ((String) -> Unit)?,
    onSelectAll: () -> Unit
) {
    var selectedStyle by remember { mutableStateOf(HighlightStyle.BACKGROUND) }
    val colors = SharedPdfAndroidHighlightColors.palette.take(4)
    // Android parity (PdfSelectionMenuPopup): cap height to window - 32dp
    // (min 160dp) with scrolling, and wrap width to content via
    // IntrinsicSize.Max so the menu matches Android's size. BoxWithConstraints
    // inside the Popup reports the window size on all mobile targets.
    BoxWithConstraints {
        val selectionMenuMaxHeight = (maxHeight - 32.dp).coerceAtLeast(160.dp)
        val menuScrollState = rememberScrollState()
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .heightIn(max = selectionMenuMaxHeight)
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .heightIn(max = selectionMenuMaxHeight)
                    .verticalScroll(menuScrollState)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 10.dp, end = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightStyle.entries.forEach { style ->
                    val painter = painterResource(
                        when (style) {
                            HighlightStyle.BACKGROUND -> Res.drawable.font_background
                            HighlightStyle.UNDERLINE -> Res.drawable.format_underlined
                            HighlightStyle.WAVY_UNDERLINE -> Res.drawable.format_underlined_squiggle
                            HighlightStyle.STRIKETHROUGH -> Res.drawable.strikethrough
                        }
                    )
                    Box(
                        modifier = Modifier.padding(horizontal = 3.dp).size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedStyle == style) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(1.dp, if (selectedStyle == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { selectedStyle = style },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painter,
                            contentDescription = style.id,
                            tint = if (selectedStyle == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { colorArgb ->
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp).size(28.dp).clip(CircleShape)
                            .background(Color(colorArgb))
                            .clickable { onHighlight(colorArgb, selectedStyle, false) }
                    )
                }
            }
            HorizontalDivider()
            val actions = buildList {
                add(SharedMobilePdfMenuAction(Res.drawable.copy, "Copy") { onCopy(selectedText) })
                add(SharedMobilePdfMenuAction(imageVector = Icons.AutoMirrored.Filled.VolumeUp, label = "Read aloud") { onReadAloud() })
                if (readerExternalLookupActionsAvailable(selectedText.length)) {
                    onAiDefine?.let { define ->
                        add(SharedMobilePdfMenuAction(imageVector = Icons.Default.Ai, label = "AI define") { define(selectedText) })
                    }
                    add(SharedMobilePdfMenuAction(imageVector = Icons.Default.Book, label = "Define") { onDefine(selectedText) })
                    add(SharedMobilePdfMenuAction(Res.drawable.translate, "Translate") { onTranslate(selectedText) })
                    add(SharedMobilePdfMenuAction(imageVector = Icons.Default.Search, label = "Search") { onSearch(selectedText) })
                }
                add(SharedMobilePdfMenuAction(imageVector = Icons.Default.Edit, label = "Note") { onHighlight(colors.first(), selectedStyle, true) })
                add(SharedMobilePdfMenuAction(Res.drawable.select_all, "Select all") { onSelectAll() })
            }
            Column(Modifier.padding(bottom = 4.dp)) {
                actions.chunked(3).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                    ) {
                        rowActions.forEach { SharedMobilePdfSelectionMenuAction(it) }
                        repeat(3 - rowActions.size) { androidx.compose.foundation.layout.Spacer(Modifier.width(56.dp)) }
                    }
                }
            }
            }
        }
    }
}

private data class SharedMobilePdfMenuAction(
    val iconResource: org.jetbrains.compose.resources.DrawableResource? = null,
    val label: String,
    val imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit
)

@Composable
private fun SharedMobilePdfSelectionMenuAction(
    action: SharedMobilePdfMenuAction
) {
    Column(
        modifier = Modifier.width(56.dp).clickable(onClick = action.onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            action.iconResource != null -> Icon(painterResource(action.iconResource), action.label, Modifier.size(22.dp))
            action.imageVector != null -> Icon(action.imageVector, action.label, Modifier.size(22.dp))
        }
        // Android parity: 2dp gap between icon and label.
        Spacer(modifier = Modifier.height(2.dp))
        Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
