/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aryan.reader.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.shared.pdf.RichParagraphUiState
import com.aryan.reader.shared.pdf.SharedPdfRichListType
import com.aryan.reader.shared.pdf.SharedPdfRichParagraph
import com.aryan.reader.shared.pdf.SharedPdfRichTextAlign
import com.aryan.reader.shared.pdf.sharedPdfTextBoxAnnotatedString
import com.aryan.reader.shared.pdf.sharedPdfTextBoxDockState
import com.aryan.reader.shared.pdf.sharedPdfTextBoxKeystroke
import com.aryan.reader.shared.pdf.sharedPdfTextBoxParagraphCount
import com.aryan.reader.shared.pdf.toComposeTextAlign
import com.aryan.reader.shared.pdf.trimmedRichParagraphs
import timber.log.Timber
import kotlin.math.roundToInt

private enum class ResizeHandle {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    RIGHT_CENTER,
    BOTTOM_RIGHT, BOTTOM_CENTER, BOTTOM_LEFT,
    LEFT_CENTER,
    NONE
}

enum class HandlePosition {
    TOP, BOTTOM, AUTO
}

private const val TEXT_BOX_DRAG_PILL_VISUAL_WIDTH_DP = 48f
private const val TEXT_BOX_DRAG_PILL_VISUAL_HEIGHT_DP = 24f
private const val TEXT_BOX_DRAG_PILL_TOUCH_WIDTH_DP = 72f
private const val TEXT_BOX_DRAG_PILL_TOUCH_HEIGHT_DP = 48f
private const val TEXT_BOX_DRAG_PILL_GAP_DP = 8f

/** Vertical gap between a duplicated text box and its original (page-relative). */
const val PDF_TEXT_BOX_DUPLICATE_GAP_REL = 0.04f

/** Stable tag for tracing text-box selection, focus, and IME value delivery. */
internal const val PDF_TEXT_BOX_INPUT_TRACE_TAG = "PdfTextBoxInputTrace"

/**
 * Dedicated debug tag for the text-box feature (cursor, alignment, list,
 * typing echo). Filter logcat with `TextBoxTrace` to follow one session.
 */
internal const val TEXT_BOX_TRACE_TAG = "TextBoxTrace"

/** Escaped + truncated text for trace logs (flags a leaked ZWSP anchor). */
internal fun pdfTextBoxTraceText(text: String, maxLen: Int = 160): String {
    val escaped = text.replace("\n", "\\n").replace("\u200B", "<ZWSP>")
    return if (escaped.length <= maxLen) {
        "\"$escaped\""
    } else {
        "\"${escaped.take(maxLen)}…\"(len=${text.length})"
    }
}

/** Compact paragraph summary for trace logs: [index:Align/List …]. */
internal fun pdfTextBoxTraceParagraphs(paragraphs: List<SharedPdfRichParagraph>): String =
    if (paragraphs.isEmpty()) {
        "[]"
    } else {
        paragraphs.mapIndexed { index, paragraph ->
            "$index:${paragraph.alignment.name.first()}/${paragraph.listType.name.first()}"
        }.joinToString(prefix = "[", postfix = "]")
    }

/** Compact dock-state summary for trace logs. */
internal fun pdfTextBoxTraceDockState(state: RichParagraphUiState): String =
    "${state.alignment} b=${state.isBulleted} n=${state.isNumbered}"

/** Compact per-box action menu entries shown above a selected text box. */
enum class PdfTextBoxMenuAction { DELETE, DUPLICATE, LOCK }

/**
 * One-shot post-toggle cursor for a text box (Android only).
 *
 * The dock list toggle inserts markers around the field, so the correct
 * cursor (shifted past "• "/"1. ") is computed parent-side. A plain
 * [TextRange] mirror cannot be used: it goes stale within a frame (the
 * mirror only learns the field's own reports) and re-adopting it yanked
 * the cursor to 0 on every keystroke. The [token] is bumped per toggle
 * and consumed once, so later syncs never re-adopt a stale cursor.
 */
data class TextBoxPendingSelection(
    val range: TextRange,
    val token: Long,
)

/** A selected legacy text box owns the IME instead of the page rich-text editor. */
internal fun isPdfRichTextInputEnabled(
    isEditMode: Boolean,
    selectedTool: InkType,
    selectedTextBoxId: String?,
): Boolean {
    // Currently retired: page rich text is hidden on Android, text boxes only
    // (docs/android-page-rich-text-retirement.md). The page editor never owns
    // the IME; a selected box owns its own field. Kept (not deleted) so
    // re-enabling is a one-line change via ENABLE_PAGE_RICH_TEXT.
    return false
}

// Eagerly consumes pointer events so parent scaled pan/zoom gestures don't intercept it
suspend fun PointerInputScope.detectEagerDragGestures(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) {
    awaitEachGesture {
        var dragStarted = false
        try {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            down.consume() // Consume immediately
            onDragStart(down.position)
            dragStarted = true
            val pointerId = down.id
            var canceled = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null) {
                    canceled = true
                    break
                }
                if (change.changedToUp()) {
                    change.consume()
                    break
                }
                if (change.positionChanged()) {
                    val dragAmount = change.position - change.previousPosition
                    change.consume()
                    onDrag(change, dragAmount)
                }
            }
            if (canceled) onDragCancel() else onDragEnd()
            dragStarted = false
        } finally {
            if (dragStarted) {
                onDragCancel()
            }
        }
    }
}

@Composable
fun ResizableTextBox(
    box: PdfTextBox,
    isSelected: Boolean,
    isEditMode: Boolean,
    isDarkMode: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
    scale: Float = 1f,
    onBoundsChanged: (Rect) -> Unit,
    onTextChanged: (String, List<SharedPdfRichParagraph>) -> Unit,
    onSelect: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset, Rect) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onDragCancel: () -> Unit = {},
    handlePosition: HandlePosition = HandlePosition.AUTO,
    onParagraphUiStateChanged: (RichParagraphUiState, TextRange) -> Unit = { _, _ -> },
    // One-shot post-toggle cursor from the parent (e.g. list markers
    // inserted around the field): adopted once on the next external sync
    // so the caret lands AFTER "• "/"1. " instead of staying at its stale
    // pre-toggle offset. Never the live mirror — see
    // TextBoxPendingSelection.
    pendingSelection: TextBoxPendingSelection? = null,
    // Compact box menu (delete / duplicate / lock). Null hides the menu
    // (e.g. the pagination drag preview where actions make no sense).
    onTextBoxMenuAction: ((PdfTextBoxMenuAction) -> Unit)? = null,
) {
    if (pageWidthPx <= 0 || pageHeightPx <= 0) return

    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val currentOnBoundsChanged by rememberUpdatedState(onBoundsChanged)
    val currentOnTextChanged by rememberUpdatedState(onTextChanged)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    // Counter-scale fixed sizes so they render proportionally regardless of the zoom level
    val handleSize = (10f / scale).dp
    val handleTouchSize = (40f / scale).dp
    val handleSizePx = with(density) { handleSize.toPx() }
    val halfHandlePx = handleSizePx / 2f
    val handleTouchSizePx = with(density) { handleTouchSize.toPx() }

    val borderColor = if (isDarkMode) Color.White else Color.Black
    val handleColor = if (isDarkMode) Color.White else Color.Black

    var isDraggingOrResizing by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    val isTextInputEnabled = isEditMode && isSelected

    val fontFamily = remember(box.fontPath, box.fontName) {
        Timber.tag("PdfFontDebug").d("Rendering Box ${box.id}: FontPath=${box.fontPath}, FontName=${box.fontName}")
        if (box.fontPath != null) {
            PdfFontCache.getFontFamily(box.fontPath)
        } else {
            when (box.fontName) {
                "Serif" -> FontFamily.Serif
                "Sans" -> FontFamily.SansSerif
                "Monospace" -> FontFamily.Monospace
                "Cursive" -> FontFamily.Cursive
                else -> FontFamily.Default
            }
        }
    }
    val currentOnParagraphUiStateChanged by rememberUpdatedState(onParagraphUiStateChanged)
    // Box-level style as the AnnotatedString base; per-paragraph alignment
    // and list tags come from shared helpers (same model as rich text).
    val boxSpanStyle = SpanStyle(
        color = box.color,
        background = box.backgroundColor,
        fontFamily = fontFamily,
        fontWeight = if (box.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (box.isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = run {
            val decs = mutableListOf<TextDecoration>()
            if (box.isUnderline) decs.add(TextDecoration.Underline)
            if (box.isStrikeThrough) decs.add(TextDecoration.LineThrough)
            if (decs.isEmpty()) TextDecoration.None else TextDecoration.combine(decs)
        }
    )
    val externalAnnotated = remember(box.text, box.paragraphs, boxSpanStyle) {
        sharedPdfTextBoxAnnotatedString(box.text, box.paragraphs, boxSpanStyle)
    }
    var fieldValue by remember(box.id) {
        mutableStateOf(TextFieldValue(externalAnnotated)).also {
            Timber.tag(TEXT_BOX_TRACE_TAG).d(
                "field_init id=${box.id} text=${pdfTextBoxTraceText(box.text)} " +
                    "paras=${pdfTextBoxTraceParagraphs(box.paragraphs)}"
            )
        }
    }
    // Paragraphs backing the field text. Kept in lockstep with fieldValue so
    // rapid keystrokes always reconcile against their true predecessor even
    // before the parent recomposes with the updated box.
    var fieldParagraphs by remember(box.id) { mutableStateOf(box.paragraphs) }
    // Sync external changes (dock alignment/list toggles, undo, reload)
    // into the field. Three rules keep live typing intact:
    // 1. A fresh toggle token adopts the post-toggle text + cursor once,
    //    then is consumed (never re-adopted).
    // 2. While the IME session is active, parent text is NEVER adopted:
    //    the echo of our own keystrokes always lags a frame, and adopting
    //    it reverted fresh input and pinned the cursor. The field wins;
    //    paragraphs are still adopted when the paragraph count matches
    //    (dock alignment tapped mid-lag), which is index-safe.
    // 3. Idle (unfocused) fields adopt parent text: background merges apply
    //    when nobody is typing, and the blur race self-heals because the
    //    parent always holds our synchronous echoes.
    // Span-only mismatches (IME autocorrect underlines) rebuild the
    // annotated string but keep the current cursor and composition.
    var consumedSelectionToken by remember(box.id) { mutableStateOf(-1L) }
    LaunchedEffect(box.id, externalAnnotated, pendingSelection, box.paragraphs, isTextFieldFocused) {
        val current = fieldValue
        val token = pendingSelection?.takeIf { it.token != consumedSelectionToken }
        if (token != null) {
            val wanted = TextRange(
                token.range.min.coerceIn(0, externalAnnotated.length),
                token.range.max.coerceIn(0, externalAnnotated.length)
            )
            Timber.tag(TEXT_BOX_TRACE_TAG).d(
                "sync_token id=${box.id} token=${token.token} sel=$wanted " +
                    "boxText=${pdfTextBoxTraceText(externalAnnotated.text)} " +
                    "boxParas=${pdfTextBoxTraceParagraphs(box.paragraphs)}"
            )
            fieldValue = TextFieldValue(externalAnnotated, wanted, null)
            fieldParagraphs = box.paragraphs.trimmedRichParagraphs()
            consumedSelectionToken = token.token
            return@LaunchedEffect
        }
        val textChanged = current.text != externalAnnotated.text
        if (textChanged && isTextFieldFocused) {
            // Paragraphs-only external changes (dock alignment tapped
            // mid-lag) are still safe to adopt when the paragraph count
            // matches — indices line up, and the echo case is a no-op
            // because the parent echoes our paragraphs verbatim.
            if (sharedPdfTextBoxParagraphCount(current.text) ==
                sharedPdfTextBoxParagraphCount(externalAnnotated.text)
            ) {
                fieldParagraphs = box.paragraphs.trimmedRichParagraphs()
            }
            Timber.tag(TEXT_BOX_TRACE_TAG).d(
                "sync_skip_echo_guard id=${box.id} " +
                    "fieldText=${pdfTextBoxTraceText(current.text)} " +
                    "boxText=${pdfTextBoxTraceText(externalAnnotated.text)} " +
                    "currentSel=${current.selection} " +
                    "fieldParas=${pdfTextBoxTraceParagraphs(fieldParagraphs)}"
            )
            return@LaunchedEffect
        }
        val parasChanged = fieldParagraphs.trimmedRichParagraphs() !=
            box.paragraphs.trimmedRichParagraphs()
        val spansChanged = current.annotatedString != externalAnnotated
        if (!textChanged && !parasChanged && !spansChanged) {
            Timber.tag(TEXT_BOX_TRACE_TAG).d(
                "sync id=${box.id} decision=SKIP currentSel=${current.selection}"
            )
            return@LaunchedEffect
        }
        val wantedSelection = TextRange(
            current.selection.min.coerceIn(0, externalAnnotated.length),
            current.selection.max.coerceIn(0, externalAnnotated.length)
        )
        val composition = if (!textChanged) current.composition else null
        Timber.tag(TEXT_BOX_TRACE_TAG).d(
            "sync id=${box.id} textChanged=$textChanged parasChanged=$parasChanged " +
                "spansChanged=$spansChanged focused=$isTextFieldFocused " +
                "currentSel=${current.selection} wantedSel=$wantedSelection " +
                "fieldText=${pdfTextBoxTraceText(current.text)} " +
                "boxText=${pdfTextBoxTraceText(externalAnnotated.text)} " +
                "fieldParas=${pdfTextBoxTraceParagraphs(fieldParagraphs)} " +
                "boxParas=${pdfTextBoxTraceParagraphs(box.paragraphs)} decision=RESET"
        )
        fieldValue = TextFieldValue(externalAnnotated, wantedSelection, composition)
        fieldParagraphs = box.paragraphs.trimmedRichParagraphs()
        Timber.tag(TEXT_BOX_TRACE_TAG).d(
            "synced id=${box.id} sel=$wantedSelection compositionKept=${composition != null}"
        )
    }
    // Report dock state for the selected box (alignment + list actives).
    // Stored-aware: empty paragraphs report their stored alignment, which
    // the anchor-free buffer alone cannot represent.
    LaunchedEffect(fieldValue.text, fieldParagraphs, fieldValue.selection, isSelected) {
        if (isSelected) {
            val dockState = sharedPdfTextBoxDockState(fieldValue.text, fieldParagraphs, fieldValue.selection)
            Timber.tag(TEXT_BOX_TRACE_TAG).d(
                "dock_report id=${box.id} state=${pdfTextBoxTraceDockState(dockState)} " +
                    "sel=${fieldValue.selection} textLen=${fieldValue.text.length} " +
                    "paras=${pdfTextBoxTraceParagraphs(fieldParagraphs)} " +
                    "emptyFallback=${if (fieldValue.text.isEmpty()) {
                        fieldParagraphs.getOrElse(0) { SharedPdfRichParagraph() }.alignment
                    } else {
                        "n/a"
                    }}"
            )
            currentOnParagraphUiStateChanged(
                dockState,
                fieldValue.selection
            )
        }
    }
    androidx.compose.runtime.SideEffect {
        Timber.tag("PdfTextBoxDebug").v("ResizableTextBox Recompose [ID: ${box.id}] | isSelected=$isSelected | scale=$scale | pagePx=${pageWidthPx}x${pageHeightPx} | bounds=${box.relativeBounds}")
    }
    LaunchedEffect(
        box.id,
        box.pageIndex,
        isSelected,
        isEditMode,
        isTextInputEnabled,
        isTextFieldFocused,
        box.text.length
    ) {
        Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
            "event=compose id=${box.id} page=${box.pageIndex} selected=$isSelected " +
                "editMode=$isEditMode enabled=$isTextInputEnabled focused=$isTextFieldFocused " +
                "textLength=${box.text.length}"
        )
    }
    var currentRectPx by remember {
        mutableStateOf(
            Rect(
                left = box.relativeBounds.left * pageWidthPx,
                top = box.relativeBounds.top * pageHeightPx,
                right = box.relativeBounds.right * pageWidthPx,
                bottom = box.relativeBounds.bottom * pageHeightPx
            )
        )
    }

    LaunchedEffect(
        isSelected,
        isEditMode,
        box.color,
        box.backgroundColor,
        box.fontSize,
        box.isBold,
        box.isItalic,
        box.isUnderline,
        box.isStrikeThrough,
        box.fontPath,
        box.fontName,
    ) {
        Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
            "event=selection_or_style_state id=${box.id} page=${box.pageIndex} selected=$isSelected " +
                "editMode=$isEditMode enabled=$isTextInputEnabled focused=$isTextFieldFocused " +
                "textLength=${box.text.length}"
        )
        if (isSelected && isEditMode) {
            val requestResult = runCatching { focusRequester.requestFocus() }
            Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                "event=request_focus id=${box.id} page=${box.pageIndex} reason=selection_or_style " +
                    "selected=$isSelected " +
                    "editMode=$isEditMode enabled=$isTextInputEnabled result=${requestResult.getOrNull()} " +
                    "error=${requestResult.exceptionOrNull()?.javaClass?.simpleName ?: "none"}"
            )
            requestResult.getOrThrow()
        }
    }

    LaunchedEffect(box.relativeBounds, pageWidthPx, pageHeightPx) {
        if (!isDraggingOrResizing) {
            val newPx = Rect(
                left = box.relativeBounds.left * pageWidthPx,
                top = box.relativeBounds.top * pageHeightPx,
                right = box.relativeBounds.right * pageWidthPx,
                bottom = box.relativeBounds.bottom * pageHeightPx
            )
            Timber.tag("PdfTextBoxDebug").d("LaunchedEffect bounds recalculation [ID: ${box.id}] | currentRectPx=$newPx")

            if (kotlin.math.abs(newPx.left - currentRectPx.left) > 1f ||
                kotlin.math.abs(newPx.top - currentRectPx.top) > 1f ||
                kotlin.math.abs(newPx.width - currentRectPx.width) > 1f ||
                kotlin.math.abs(newPx.height - currentRectPx.height) > 1f
            ) {
                currentRectPx = newPx
            }
        }
    }

    val requiredBottomSpacePx = with(density) { 60.dp.toPx() } / scale

    // Freeze handle position while dragging to prevent UI jumping
    var isHandleAtTop by remember { mutableStateOf(false) }

    LaunchedEffect(currentRectPx, pageHeightPx, handlePosition, requiredBottomSpacePx, isDraggingOrResizing) {
        if (!isDraggingOrResizing) {
            isHandleAtTop = when (handlePosition) {
                HandlePosition.TOP -> true
                HandlePosition.BOTTOM -> false
                HandlePosition.AUTO -> {
                    if (pageHeightPx <= 0f) false
                    else (pageHeightPx - currentRectPx.bottom) < requiredBottomSpacePx
                }
            }
        }
    }

    val dragPillTouchWidth = (TEXT_BOX_DRAG_PILL_TOUCH_WIDTH_DP / scale).dp
    val dragPillTouchHeight = (TEXT_BOX_DRAG_PILL_TOUCH_HEIGHT_DP / scale).dp
    val dragPillWidthPx = with(density) { dragPillTouchWidth.toPx() }
    val dragPillHeightPx = with(density) { dragPillTouchHeight.toPx() }
    val dragPillGapPx = with(density) { (TEXT_BOX_DRAG_PILL_GAP_DP / scale).dp.toPx() }
    // Compact action menu: constant on-screen size, opposite end from the pill.
    val showActionMenu = isSelected && onTextBoxMenuAction != null
    val actionMenuWidthDp = 100.dp
    val actionMenuHeightDp = 24.dp
    val actionMenuWidthPx = with(density) { actionMenuWidthDp.toPx() / scale }
    val actionMenuHeightPx = with(density) { actionMenuHeightDp.toPx() / scale }
    val chromeLayout = calculateTextBoxChromeLayout(
        textBoundsPx = currentRectPx,
        isSelected = isSelected,
        isHandleAtTop = isHandleAtTop,
        handleSizePx = handleSizePx,
        dragPillWidthPx = dragPillWidthPx,
        dragPillHeightPx = dragPillHeightPx,
        dragPillGapPx = dragPillGapPx,
        hasActionMenu = showActionMenu,
        actionMenuWidthPx = actionMenuWidthPx,
        actionMenuHeightPx = actionMenuHeightPx,
    )

    Box(
        modifier = modifier
            .zIndex(if (isSelected) 10f else 0f)
            .graphicsLayer {
                translationX = chromeLayout.outerTranslationX
                translationY = chromeLayout.outerTranslationY
            }
            .size(
                width = with(density) { chromeLayout.containerWidthPx.toDp() },
                height = with(density) { chromeLayout.containerHeightPx.toDp() }
            )
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        chromeLayout.contentOffsetX.roundToInt(),
                        chromeLayout.contentOffsetY.roundToInt()
                    )
                }
                .size(
                    width = with(density) { chromeLayout.contentWidthPx.toDp() },
                    height = with(density) { chromeLayout.contentHeightPx.toDp() }
                )
                .zIndex(1f)
        ) {
            // --- 1. Content Body ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(handleSize / 2)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            Timber.tag("PdfTextBoxDebug").d("TextBox Tapped[ID: ${box.id}]")
                            Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                                "event=select_tap id=${box.id} page=${box.pageIndex} " +
                                    "selectedBefore=$isSelected editMode=$isEditMode enabled=$isTextInputEnabled"
                            )
                            currentOnSelect()
                        }
                    }
                    .then(
                        if (isSelected) Modifier.border((1.5f / scale).dp, borderColor) else Modifier
                    )
            ) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                            "event=value_change id=${box.id} page=${box.pageIndex} " +
                                "oldLength=${fieldValue.text.length} newLength=${newValue.text.length} " +
                                "selected=$isSelected editMode=$isEditMode enabled=$isTextInputEnabled " +
                                "focused=$isTextFieldFocused"
                        )
                        // Route every keystroke through shared list/alignment
                        // maintenance (same Samsung-Notes rules as rich text:
                        // marker relocation, Enter inheritance, backspace exit,
                        // numbered renumber) plus stored-alignment preservation
                        // for empty paragraphs. Plain typing inside unchanged
                        // structure flows through UNTOUCHED: rebuilding the
                        // AnnotatedString on every keystroke (and dropping
                        // TextFieldValue.composition) interrupts the IME
                        // pipeline — predictive text, autocorrect and
                        // space-commit silently swallow characters.
                        // Paragraph base: when the field text matches the box,
                        // the box paragraphs are freshest (they include our
                        // echoes plus any dock change that landed after our
                        // last keystroke — using stale field paragraphs here
                        // is what wiped empty-line alignment on the next
                        // keystroke). When they differ the parent echo is
                        // lagging, so the field paragraphs are newer.
                        val oldFieldText = fieldValue.text
                        val useBoxParagraphs = oldFieldText == box.text
                        val baseParagraphs = if (useBoxParagraphs) box.paragraphs else fieldParagraphs
                        val result = sharedPdfTextBoxKeystroke(
                            oldText = oldFieldText,
                            oldParagraphs = baseParagraphs,
                            newText = newValue.text,
                            newSelection = newValue.selection,
                            markerFallbackStyle = boxSpanStyle
                        )
                        val newParagraphs = result.paragraphs.trimmedRichParagraphs()
                        val textSame = result.text == newValue.text
                        val selSame = result.selection == newValue.selection
                        val parasSame = newParagraphs == fieldParagraphs
                        val structural = result.shifts.isNotEmpty() ||
                            !textSame ||
                            !selSame ||
                            !parasSame
                        // Span authority: IME edits fragment or drop our
                        // ParagraphStyle runs (typing at a run end falls
                        // outside the style) and list tags. Until the next
                        // sync the field then renders LEFT (flicker) and the
                        // fragmented boundaries split MultiParagraph into
                        // phantom lines. So boxes with any non-default
                        // paragraph always rebuild from the normalized
                        // result, restoring clean merged runs. Text and
                        // selection are identical here, so the composition
                        // offsets stay valid and the IME session survives.
                        // Plain boxes have no paragraph spans to lose and
                        // keep the untouched passthrough.
                        val hasNonDefault = newParagraphs.any {
                            it.alignment != SharedPdfRichTextAlign.LEFT ||
                                it.listType != SharedPdfRichListType.NONE
                        }
                        val needsRebuild = structural || hasNonDefault
                        Timber.tag(TEXT_BOX_TRACE_TAG).d(
                            "value_change id=${box.id} newText=${pdfTextBoxTraceText(newValue.text)} " +
                                "newSel=${newValue.selection} composition=${newValue.composition} " +
                                "baseSrc=${if (useBoxParagraphs) "box" else "field"} " +
                                "resultText=${pdfTextBoxTraceText(result.text)} resultSel=${result.selection} " +
                                "shifts=${result.shifts.size} paras=${pdfTextBoxTraceParagraphs(newParagraphs)} " +
                                "textSame=$textSame selSame=$selSame parasSame=$parasSame " +
                                "inPStyles=${newValue.annotatedString.paragraphStyles.size} " +
                                "structural=$structural spanFixup=${hasNonDefault && !structural} " +
                                "rebuild=$needsRebuild"
                        )
                        fieldValue = if (needsRebuild) {
                            // Rebuild annotated from the normalized result;
                            // box style covers marker spans. Composition is
                            // only valid when the text itself is unchanged
                            // (offsets still line up).
                            val normalized = sharedPdfTextBoxAnnotatedString(
                                result.text,
                                result.paragraphs,
                                boxSpanStyle
                            )
                            val composition =
                                if (result.text == newValue.text) newValue.composition else null
                            Timber.tag(TEXT_BOX_TRACE_TAG).d(
                                "rebuild id=${box.id} sel=${result.selection} " +
                                    "compositionKept=${composition != null}"
                            )
                            TextFieldValue(normalized, result.selection, composition)
                        } else {
                            newValue
                        }
                        fieldParagraphs = newParagraphs
                        currentOnTextChanged(result.text, fieldParagraphs)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isTextFieldFocused = focusState.isFocused
                            Timber.tag(PDF_TEXT_BOX_INPUT_TRACE_TAG).d(
                                "event=focus_changed id=${box.id} page=${box.pageIndex} " +
                                    "focused=${focusState.isFocused} captured=${focusState.isCaptured} " +
                                    "selected=$isSelected editMode=$isEditMode enabled=$isTextInputEnabled " +
                                    "textLength=${box.text.length}"
                            )
                        },
                    textStyle = TextStyle(
                        color = box.color,
                        background = box.backgroundColor,
                        fontFamily = fontFamily,
                        // Empty text carries no paragraph style range, so the
                        // caret would always sit left: fall back to the stored
                        // alignment so it renders center/right as selected.
                        // Non-empty text is covered by explicit paragraph
                        // spans, which take precedence over this.
                        textAlign = if (fieldValue.text.isEmpty()) {
                            fieldParagraphs.getOrElse(0) {
                                SharedPdfRichParagraph()
                            }.alignment.toComposeTextAlign()
                        } else {
                            TextAlign.Unspecified
                        },
                        fontSize = with(LocalDensity.current) {
                            (box.fontSize * pageHeightPx).toSp()
                        },
                        fontWeight = if (box.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (box.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = run {
                            val decs = mutableListOf<TextDecoration>()
                            if (box.isUnderline) decs.add(TextDecoration.Underline)
                            if (box.isStrikeThrough) decs.add(TextDecoration.LineThrough)
                            if (decs.isEmpty()) TextDecoration.None else TextDecoration.combine(decs)
                        }
                    ),
                    cursorBrush = SolidColor(if (isDarkMode) Color.White else MaterialTheme.colorScheme.primary),
                    enabled = isEditMode && isSelected,
                    readOnly = !isEditMode
                )
            }

            if (isSelected && !box.isLocked) {
                val handles = ResizeHandle.entries.filter { it != ResizeHandle.NONE }

                fun getHandleCenter(handle: ResizeHandle, w: Float, h: Float): Offset {
                    return when (handle) {
                        ResizeHandle.TOP_LEFT -> Offset(halfHandlePx, halfHandlePx)
                        ResizeHandle.TOP_CENTER -> Offset(halfHandlePx + w / 2, halfHandlePx)
                        ResizeHandle.TOP_RIGHT -> Offset(halfHandlePx + w, halfHandlePx)
                        ResizeHandle.RIGHT_CENTER -> Offset(halfHandlePx + w, halfHandlePx + h / 2)
                        ResizeHandle.BOTTOM_RIGHT -> Offset(halfHandlePx + w, halfHandlePx + h)
                        ResizeHandle.BOTTOM_CENTER -> Offset(halfHandlePx + w / 2, halfHandlePx + h)
                        ResizeHandle.BOTTOM_LEFT -> Offset(halfHandlePx, halfHandlePx + h)
                        ResizeHandle.LEFT_CENTER -> Offset(halfHandlePx, halfHandlePx + h / 2)
                        else -> Offset.Zero
                    }
                }

                handles.forEach { handle ->
                    val center = getHandleCenter(handle, currentRectPx.width, currentRectPx.height)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (center.x - handleTouchSizePx / 2).roundToInt(),
                                    (center.y - handleTouchSizePx / 2).roundToInt()
                                )
                            }
                            .size(handleTouchSize)
                            .pointerInput(box.id, handle, pageWidthPx, pageHeightPx) {
                                detectEagerDragGestures(
                                    onDragStart = {
                                        Timber.tag("PdfTextBoxDebug").d("ResizeHandle DragStart[ID: ${box.id}] Handle=$handle")
                                        isDraggingOrResizing = true
                                    },
                                    onDragEnd = {
                                        isDraggingOrResizing = false
                                        val normalized = Rect(
                                            left = currentRectPx.left / pageWidthPx,
                                            top = currentRectPx.top / pageHeightPx,
                                            right = currentRectPx.right / pageWidthPx,
                                            bottom = currentRectPx.bottom / pageHeightPx
                                        )
                                        Timber.tag("PdfTextBoxDebug").d("ResizeHandle DragEnd [ID: ${box.id}] finalNormalized=$normalized")
                                        currentOnBoundsChanged(normalized)
                                    },
                                    onDragCancel = { isDraggingOrResizing = false }
                                ) { change, dragAmount ->
                                    Timber.tag("PdfTextBoxDebug").v("ResizeHandle Drag [ID: ${box.id}] Handle=$handle | dragAmount=$dragAmount")

                                    var l = currentRectPx.left
                                    var t = currentRectPx.top
                                    var r = currentRectPx.right
                                    var b = currentRectPx.bottom
                                    val dx = dragAmount.x
                                    val dy = dragAmount.y
                                    val minSize = 50f / scale

                                    when (handle) {
                                        ResizeHandle.TOP_LEFT -> {
                                            l = (l + dx).coerceIn(0f, maxOf(0f, r - minSize))
                                            t = (t + dy).coerceIn(0f, maxOf(0f, b - minSize))
                                        }
                                        ResizeHandle.TOP_CENTER -> t = (t + dy).coerceIn(0f, maxOf(0f, b - minSize))
                                        ResizeHandle.TOP_RIGHT -> {
                                            r = (r + dx).coerceIn(l + minSize, maxOf(l + minSize, pageWidthPx))
                                            t = (t + dy).coerceIn(0f, maxOf(0f, b - minSize))
                                        }
                                        ResizeHandle.RIGHT_CENTER -> r = (r + dx).coerceIn(l + minSize, maxOf(l + minSize, pageWidthPx))
                                        ResizeHandle.BOTTOM_RIGHT -> {
                                            r = (r + dx).coerceIn(l + minSize, maxOf(l + minSize, pageWidthPx))
                                            b = (b + dy).coerceIn(t + minSize, maxOf(t + minSize, pageHeightPx))
                                        }
                                        ResizeHandle.BOTTOM_CENTER -> b = (b + dy).coerceIn(t + minSize, maxOf(t + minSize, pageHeightPx))
                                        ResizeHandle.BOTTOM_LEFT -> {
                                            l = (l + dx).coerceIn(0f, maxOf(0f, r - minSize))
                                            b = (b + dy).coerceIn(t + minSize, maxOf(t + minSize, pageHeightPx))
                                        }
                                        ResizeHandle.LEFT_CENTER -> l = (l + dx).coerceIn(0f, maxOf(0f, r - minSize))
                                        else -> {}
                                    }
                                    currentRectPx = Rect(l, t, r, b)
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(handleSize)
                                .background(handleColor, CircleShape)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        if (isSelected && !box.isLocked) {
            DragPill(
                isDarkMode = isDarkMode,
                scale = scale,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            chromeLayout.dragPillLeftPx.roundToInt(),
                            chromeLayout.dragPillTopPx.roundToInt()
                        )
                    }
                    .size(width = dragPillTouchWidth, height = dragPillTouchHeight)
                    .zIndex(20f)
                    .pointerInput(box.id, pageWidthPx, pageHeightPx) {
                        detectEagerDragGestures(
                            onDragStart = { offset ->
                                Timber.tag("PdfTextBoxDebug").d("DragPill DragStart [ID: ${box.id}] at offset=$offset")
                                isDraggingOrResizing = true
                                currentOnDragStart(offset)
                            },
                            onDragEnd = {
                                isDraggingOrResizing = false
                                val normalized = Rect(
                                    left = currentRectPx.left / pageWidthPx,
                                    top = currentRectPx.top / pageHeightPx,
                                    right = currentRectPx.right / pageWidthPx,
                                    bottom = currentRectPx.bottom / pageHeightPx
                                )
                                Timber.tag("PdfTextBoxDebug").d("DragPill DragEnd[ID: ${box.id}] finalNormalized=$normalized")
                                currentOnBoundsChanged(normalized)
                                currentOnDragEnd()
                            },
                            onDragCancel = {
                                isDraggingOrResizing = false
                                currentOnDragCancel()
                            }
                        ) { change, dragAmount ->
                            val w = currentRectPx.width
                            val h = currentRectPx.height
                            val rawLeft = currentRectPx.left + dragAmount.x
                            val rawTop = currentRectPx.top + dragAmount.y
                            val newLeft = rawLeft.coerceIn(0f, maxOf(0f, pageWidthPx - w))
                            val newTop = rawTop.coerceIn(0f, maxOf(0f, pageHeightPx - h))
                            val newRect = Rect(newLeft, newTop, newLeft + w, newTop + h)
                            currentRectPx = newRect
                            currentOnDrag(dragAmount, newRect)
                        }
                    }
            )
        }

        if (showActionMenu) {
            TextBoxActionMenu(
                isLocked = box.isLocked,
                isDarkMode = isDarkMode,
                scale = scale,
                onAction = onTextBoxMenuAction,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            chromeLayout.actionMenuLeftPx.roundToInt(),
                            chromeLayout.actionMenuTopPx.roundToInt()
                        )
                    }
                    .zIndex(20f)
            )
        }
    }
}

/**
 * Compact action menu floating at the end of a selected text box opposite the
 * drag pill: delete, duplicate, and lock/unlock. Constant on-screen size
 * (like the handles), destructive action tinted with the theme error color.
 */
@Composable
private fun TextBoxActionMenu(
    isLocked: Boolean,
    isDarkMode: Boolean,
    scale: Float,
    onAction: (PdfTextBoxMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val menuWidth = (100f / safeScale).dp
    val menuHeight = (24f / safeScale).dp
    Row(
        modifier = modifier.width(menuWidth).height(menuHeight),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape((12f / safeScale).dp),
            color = if (isDarkMode) Color(0xFF2A2A2A) else Color.White,
            contentColor = if (isDarkMode) Color.White else Color.Black,
            tonalElevation = (3f / safeScale).dp,
            shadowElevation = (4f / safeScale).dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdfTextBoxMenuAction.entries.forEachIndexed { index, action ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height((16f / safeScale).dp)
                                .background(
                                    (if (isDarkMode) Color.White else Color.Black).copy(alpha = 0.15f)
                                )
                        )
                    }
                    IconButton(
                        onClick = { onAction(action) },
                        modifier = Modifier.size((24f / safeScale).dp)
                    ) {
                        Icon(
                            imageVector = when (action) {
                                PdfTextBoxMenuAction.DELETE -> Icons.Default.Delete
                                PdfTextBoxMenuAction.DUPLICATE -> Icons.Default.ContentCopy
                                PdfTextBoxMenuAction.LOCK ->
                                    if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock
                            },
                            contentDescription = stringResource(
                                when (action) {
                                    PdfTextBoxMenuAction.DELETE -> R.string.textbox_menu_delete
                                    PdfTextBoxMenuAction.DUPLICATE -> R.string.textbox_menu_duplicate
                                    PdfTextBoxMenuAction.LOCK ->
                                        if (isLocked) R.string.textbox_menu_unlock else R.string.textbox_menu_lock
                                }
                            ),
                            tint = if (action == PdfTextBoxMenuAction.DELETE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            },
                            modifier = Modifier.size((13f / safeScale).dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DragPill(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    scale: Float = 1f
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(
                    width = (TEXT_BOX_DRAG_PILL_VISUAL_WIDTH_DP / scale).dp,
                    height = (TEXT_BOX_DRAG_PILL_VISUAL_HEIGHT_DP / scale).dp
                ),
            shape = CircleShape,
            color = if (isDarkMode) Color.White else Color.Black,
            contentColor = if (isDarkMode) Color.Black else Color.White,
            shadowElevation = (4f / scale).dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.drag_handle),
                    contentDescription = stringResource(R.string.content_desc_drag_text_box),
                    modifier = Modifier.size((20f / scale).dp)
                )
            }
        }
    }
}
