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
import com.aryan.reader.shared.pdf.SharedPdfRichLayoutDiag
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.sharedPdfRichTextSelectionBounds
import com.aryan.reader.shared.pdf.withoutTrailingSharedPdfPageBreak
import kotlin.math.roundToInt

@Composable
fun SharedPdfRichTextHiddenInput(
    controller: SharedPdfRichTextController,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
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
    tapHandlingEnabled: Boolean = true,
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

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(pageWidth, pageHeight, density) {
        if (pageWidth > 0f && pageHeight > 0f) {
            controller.updateLayoutConfig(pageWidth, pageHeight, density, textMeasurer)
        }
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
        SharedPdfRichLayoutDiag.d(
            "layer.render page=$pageIndex editing=$isTextEditingEnabled " +
                "active=${controller.activePageIndex} hasLayout=${pageLayout != null} " +
                "global=${pageLayout?.globalStartIndex}-${pageLayout?.globalEndIndex} " +
                "visibleLen=${pageLayout?.visibleText?.length ?: 0} " +
                "pageLayouts=${controller.pageLayouts.size} " +
                "cfg=${pageWidth.richTextUiFloat()}x${pageHeight.richTextUiFloat()}"
        )
    }

    val marginX = pageWidth * 0.1f
    val marginY = pageHeight * 0.08f
    val editorWidth = pageWidth - (marginX * 2f)
    val editorHeight = pageHeight - (marginY * 2f)
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
            .then(
                if (isTextEditingEnabled && tapHandlingEnabled) {
                    Modifier.pointerInput(pageIndex) {
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
            // Fixed-width layout: CENTER/RIGHT align across the whole editor.
            // maxWidth-only would size to content, pinning alignment to a
            // narrow strip (right edge near the text instead of the page).
            textMeasurer.measure(
                text = textToRender,
                style = TextStyle(fontSize = 16.sp),
                constraints = Constraints(
                    minWidth = editorWidth.toInt(),
                    maxWidth = editorWidth.toInt(),
                ),
                density = density
            )
        }

        LaunchedEffect(measureResult, pageIndex) {
            val multi = measureResult.multiParagraph
            val lineCount = multi.lineCount
            var maxLineHeight = 0f
            var firstLineBottom = 0f
            var lastLineBottom = 0f
            var fontMin = Float.MAX_VALUE
            var fontMax = Float.MIN_VALUE
            var fontSamples = 0
            if (lineCount > 0) {
                firstLineBottom = multi.getLineBottom(0)
                lastLineBottom = multi.getLineBottom(lineCount - 1)
                for (i in 0 until lineCount) {
                    maxLineHeight = maxOf(maxLineHeight, multi.getLineBottom(i) - multi.getLineTop(i))
                }
            }
            textToRender.spanStyles.forEach { range ->
                if (range.item.fontSize.isSp) {
                    fontMin = minOf(fontMin, range.item.fontSize.value)
                    fontMax = maxOf(fontMax, range.item.fontSize.value)
                    fontSamples++
                }
            }
            val sampleLines = minOf(lineCount, 10)
            val lineMetrics = buildString {
                for (i in 0 until sampleLines) {
                    val top = multi.getLineTop(i)
                    val bottom = multi.getLineBottom(i)
                    if (i > 0) append(',')
                    append("${top.richTextUiFloat()}-${bottom.richTextUiFloat()}")
                }
                if (lineCount > sampleLines) append(",...")
            }
            // Character span of each visual line: proves whether phantom lines
            // break on '\n', on ParagraphStyle range boundaries, or soft-wrap.
            val lineRanges = buildString {
                for (i in 0 until sampleLines) {
                    val start = multi.getLineStart(i)
                    val end = multi.getLineEnd(i)
                    if (i > 0) append(',')
                    append("$i:$start-$end")
                }
                if (lineCount > sampleLines) append(",...")
            }
            val paraStyles = textToRender.paragraphStyles.joinToString(";") { range ->
                "${range.start}..${range.end}:${range.item.textAlign}"
            }
            val escapedText = buildString {
                val src = textToRender.text
                val limit = src.length.coerceAtMost(160)
                for (i in 0 until limit) {
                    when (val c = src[i]) {
                        '\n' -> append("\\n")
                        '\u200B' -> append("\\u200B")
                        '\u000C' -> append("\\f")
                        else -> append(c)
                    }
                }
                if (src.length > limit) append("...")
            }
            SharedPdfRichLayoutDiag.d(
                "measure page=$pageIndex editing=$isTextEditingEnabled " +
                    "active=${controller.activePageIndex} " +
                    "src=${if (controller.activePageIndex == pageIndex) "local" else "layout"} " +
                    "len=${textToRender.length} newlines=${textToRender.text.count { it == '\n' }} " +
                    "paraStyles=${textToRender.paragraphStyles.size} " +
                    "editorW=${editorWidth.richTextUiFloat()} lines=$lineCount " +
                    "maxLineH=${maxLineHeight.richTextUiFloat()} " +
                    "firstBottom=${firstLineBottom.richTextUiFloat()} " +
                    "lastBottom=${lastLineBottom.richTextUiFloat()} " +
                    "fontSpMin=${if (fontSamples > 0) fontMin.richTextUiFloat() else "-"} " +
                    "fontSpMax=${if (fontSamples > 0) fontMax.richTextUiFloat() else "-"} " +
                    "fontSpans=$fontSamples " +
                    "lineTopsBots=$lineMetrics " +
                    "lineRanges=$lineRanges " +
                    "paraRanges=$paraStyles " +
                    "text=\"$escapedText\""
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
