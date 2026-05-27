package com.aryan.reader.shared.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.sharedPdfRichTextSelectionBounds
import com.aryan.reader.shared.pdf.withoutTrailingSharedPdfPageBreak
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SharedPdfRichTextHiddenInput(
    controller: SharedPdfRichTextController,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(enabled, controller.activePageIndex) {
        SharedPdfRichTextLog.d(
            "ui.hiddenInput enabled=$enabled activePage=${controller.activePageIndex} " +
                "editingLen=${controller.editingValue.text.length} selection=${controller.editingValue.selection}"
        )
        if (enabled && controller.activePageIndex != -1) {
            controller.requestEditingFocus()
            delay(16)
            controller.requestEditingFocus()
        }
    }

    if (!enabled) return

    BasicTextField(
        value = controller.editingValue,
        onValueChange = controller::onValueChanged,
        textStyle = TextStyle(
            color = controller.currentStyle.color,
            fontSize = controller.currentStyle.fontSize,
            fontWeight = controller.currentStyle.fontWeight,
            fontStyle = controller.currentStyle.fontStyle,
            textDecoration = controller.currentStyle.textDecoration
        ),
        modifier = modifier
            .size(1.dp)
            .alpha(0f)
            .clearAndSetSemantics { }
            .focusRequester(controller.focusRequester)
            .onKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.Backspace &&
                    controller.handleBackspaceAtStart()
            }
    )
}

@Composable
fun SharedPdfRichTextLayer(
    pageIndex: Int,
    controller: SharedPdfRichTextController,
    pageWidth: Float,
    pageHeight: Float,
    isTextEditingEnabled: Boolean,
    centeringOffsetX: Float = 0f,
    centeringOffsetY: Float = 0f,
    isDarkMode: Boolean = false,
    isScrolling: Boolean = false,
    onPageTapped: (Int) -> Unit = {}
) {
    LaunchedEffect(pageIndex, pageWidth, pageHeight, isTextEditingEnabled) {
        if (pageWidth <= 0f || pageHeight <= 0f) {
            SharedPdfRichTextLog.d(
                "ui.layer invalidSize page=$pageIndex size=${pageWidth.richTextUiFloat()}x${pageHeight.richTextUiFloat()} " +
                    "editing=$isTextEditingEnabled"
            )
        }
    }

    if (pageWidth <= 0f || pageHeight <= 0f) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(pageWidth, pageHeight, density, textMeasurer) {
        controller.updateLayoutConfig(pageWidth, pageHeight, density, textMeasurer)
    }

    val pageLayout = remember(controller.pageLayouts, pageIndex) {
        controller.pageLayouts.find { it.pageIndex == pageIndex }
    }

    LaunchedEffect(
        pageIndex,
        pageWidth,
        pageHeight,
        isTextEditingEnabled,
        controller.activePageIndex,
        pageLayout?.globalStartIndex,
        pageLayout?.globalEndIndex
    ) {
        SharedPdfRichTextLog.d(
            "ui.layer page=$pageIndex size=${pageWidth.richTextUiFloat()}x${pageHeight.richTextUiFloat()} " +
                "editing=$isTextEditingEnabled activePage=${controller.activePageIndex} " +
                "layout=${pageLayout?.globalStartIndex}-${pageLayout?.globalEndIndex} " +
                "visibleLen=${pageLayout?.visibleText?.length ?: 0}"
        )
    }

    val marginX = pageWidth * 0.1f
    val marginY = pageHeight * 0.08f
    val editorWidth = (pageWidth - (marginX * 2f)).coerceAtLeast(10f)
    val editorHeight = (pageHeight - (marginY * 2f)).coerceAtLeast(10f)
    val editorWidthDp = with(density) { editorWidth.toDp() }
    val editorHeightDp = with(density) { editorHeight.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centeringOffsetX + marginX).roundToInt(),
                    (centeringOffsetY + marginY).roundToInt()
                )
            }
            .size(editorWidthDp, editorHeightDp)
            .graphicsLayer()
            .clipToBounds()
            .then(
                if (isTextEditingEnabled) {
                    Modifier.pointerInput(
                        pageIndex,
                        editorWidth,
                        editorHeight,
                        controller.activePageIndex,
                        pageLayout?.globalStartIndex,
                        pageLayout?.globalEndIndex
                    ) {
                        detectTapGestures { tapOffset ->
                            SharedPdfRichTextLog.d(
                                "ui.layer.tap page=$pageIndex offset=${tapOffset.richTextUiOffsetSummary()} " +
                                    "editor=${editorWidth.richTextUiFloat()}x${editorHeight.richTextUiFloat()} " +
                                    "activePage=${controller.activePageIndex} hasLayout=${pageLayout != null}"
                            )
                            onPageTapped(pageIndex)
                            controller.handleTapOnPage(pageIndex, tapOffset)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val textToRender = if (controller.activePageIndex == pageIndex) {
            controller.localTextFieldValue.annotatedString
        } else {
            pageLayout?.visibleText?.withoutTrailingSharedPdfPageBreak()
        } ?: return@Box

        val measureResult = remember(textToRender, editorWidth, density) {
            textMeasurer.measure(
                text = textToRender,
                style = TextStyle(fontSize = 16.sp),
                constraints = Constraints(maxWidth = editorWidth.toInt()),
                density = density
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            measureResult.multiParagraph.paint(drawContext.canvas)
        }

        if (isTextEditingEnabled && controller.activePageIndex == pageIndex) {
            val selection = controller.editingValue.selection

            sharedPdfRichTextSelectionBounds(
                selectionStart = selection.start,
                selectionEnd = selection.end,
                textLength = textToRender.length
            )?.let { (localStart, localEnd) ->
                val selectionPath = measureResult.getPathForRange(localStart, localEnd)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPath(selectionPath, Color(0xFFB3D7FF).copy(alpha = 0.5f))
                }
            }

            if (selection.collapsed && controller.isCursorVisible) {
                val localStart = selection.start.coerceIn(0, textToRender.length)
                val alpha = if (isScrolling) {
                    1f
                } else {
                    val infiniteTransition = rememberInfiniteTransition(label = "pdfRichCursor")
                    infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                        label = "pdfRichCursorAlpha"
                    ).value
                }
                val cursorRect = measureResult.getCursorRect(localStart)
                val styleFontSize = controller.currentStyle.fontSize
                val cursorHeight = if (styleFontSize.isSpecified) {
                    with(density) { styleFontSize.toPx() } * 1.2f
                } else {
                    cursorRect.height
                }
                val centerY = cursorRect.center.y
                val cursorColor = if (isDarkMode) Color.White else Color.Black

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = cursorColor.copy(alpha = alpha),
                        start = Offset(cursorRect.left, centerY - cursorHeight / 2f),
                        end = Offset(cursorRect.left, centerY + cursorHeight / 2f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}

private fun Float.richTextUiFloat(): String {
    return if (isFinite()) {
        val rounded = kotlin.math.round(this * 10f) / 10f
        rounded.toString()
    } else {
        toString()
    }
}

private fun Offset.richTextUiOffsetSummary(): String {
    return "(${x.richTextUiFloat()},${y.richTextUiFloat()})"
}
