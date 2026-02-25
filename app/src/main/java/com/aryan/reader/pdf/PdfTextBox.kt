/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aryan.reader.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aryan.reader.pdf.data.PdfTextBox
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

@Composable
fun ResizableTextBox(
    box: PdfTextBox,
    isSelected: Boolean,
    isEditMode: Boolean,
    isDarkMode: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
    onBoundsChanged: (Rect) -> Unit,
    onTextChanged: (String) -> Unit,
    onSelect: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset, Rect) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onDragCancel: () -> Unit = {},
    handlePosition: HandlePosition = HandlePosition.AUTO
) {
    if (pageWidthPx <= 0 || pageHeightPx <= 0) return

    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }

    val handleSize = 10.dp
    val handleTouchSize = 40.dp
    val handleSizePx = with(density) { handleSize.toPx() }
    val halfHandlePx = handleSizePx / 2f
    val handleTouchSizePx = with(density) { handleTouchSize.toPx() }

    val borderColor = if (isDarkMode) Color.White else Color.Black
    val handleColor = if (isDarkMode) Color.White else Color.Black

    var isDraggingOrResizing by remember { mutableStateOf(false) }

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

    LaunchedEffect(isSelected) {
        if (isSelected && isEditMode) {
            focusRequester.requestFocus()
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
            if (kotlin.math.abs(newPx.left - currentRectPx.left) > 1f ||
                kotlin.math.abs(newPx.top - currentRectPx.top) > 1f ||
                kotlin.math.abs(newPx.width - currentRectPx.width) > 1f ||
                kotlin.math.abs(newPx.height - currentRectPx.height) > 1f
            ) {
                currentRectPx = newPx
            }
        }
    }

    val requiredBottomSpacePx = with(density) { 60.dp.toPx() }

    val isHandleAtTop by remember(currentRectPx, pageHeightPx, handlePosition) {
        derivedStateOf {
            when (handlePosition) {
                HandlePosition.TOP -> true
                HandlePosition.BOTTOM -> false
                HandlePosition.AUTO -> {
                    if (pageHeightPx <= 0f) {
                        false
                    } else {
                        val spaceBelow = pageHeightPx - currentRectPx.bottom
                        spaceBelow < requiredBottomSpacePx
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .zIndex(if (isSelected) 10f else 0f)
            .offset {
                IntOffset(
                    (currentRectPx.left - halfHandlePx).roundToInt(),
                    (currentRectPx.top - halfHandlePx).roundToInt()
                )
            }
            .size(
                width = with(density) { (currentRectPx.width + handleSizePx).toDp() },
                height = with(density) { (currentRectPx.height + handleSizePx).toDp() }
            )
    ) {
        // --- 1. Content Body ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(handleSize / 2)
                .pointerInput(Unit) {
                    detectTapGestures { onSelect() }
                }
                .then(
                    if (isSelected) Modifier.border(1.5.dp, borderColor) else Modifier
                )
        ) {
            BasicTextField(
                value = box.text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = box.color,
                    background = box.backgroundColor,
                    fontFamily = fontFamily,
                    fontSize = with(LocalDensity.current) {
                        (box.fontSize * pageHeightPx).coerceAtLeast(10f).toSp()
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

        if (isSelected) {
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
                        .pointerInput(onBoundsChanged) {
                            detectDragGestures(
                                onDragStart = { isDraggingOrResizing = true },
                                onDragEnd = {
                                    isDraggingOrResizing = false
                                    val normalized = Rect(
                                        left = currentRectPx.left / pageWidthPx,
                                        top = currentRectPx.top / pageHeightPx,
                                        right = currentRectPx.right / pageWidthPx,
                                        bottom = currentRectPx.bottom / pageHeightPx
                                    )
                                    onBoundsChanged(normalized)
                                },
                                onDragCancel = { isDraggingOrResizing = false }
                            ) { change, dragAmount ->
                                change.consume()
                                var l = currentRectPx.left
                                var t = currentRectPx.top
                                var r = currentRectPx.right
                                var b = currentRectPx.bottom
                                val dx = dragAmount.x
                                val dy = dragAmount.y
                                val minSize = 50f

                                when (handle) {
                                    ResizeHandle.TOP_LEFT -> {
                                        l = (l + dx).coerceIn(0f, r - minSize)
                                        t = (t + dy).coerceIn(0f, b - minSize)
                                    }
                                    ResizeHandle.TOP_CENTER -> t = (t + dy).coerceIn(0f, b - minSize)
                                    ResizeHandle.TOP_RIGHT -> {
                                        r = (r + dx).coerceIn(l + minSize, pageWidthPx)
                                        t = (t + dy).coerceIn(0f, b - minSize)
                                    }
                                    ResizeHandle.RIGHT_CENTER -> r = (r + dx).coerceIn(l + minSize, pageWidthPx)
                                    ResizeHandle.BOTTOM_RIGHT -> {
                                        r = (r + dx).coerceIn(l + minSize, pageWidthPx)
                                        b = (b + dy).coerceIn(t + minSize, pageHeightPx)
                                    }
                                    ResizeHandle.BOTTOM_CENTER -> b = (b + dy).coerceIn(t + minSize, pageHeightPx)
                                    ResizeHandle.BOTTOM_LEFT -> {
                                        l = (l + dx).coerceIn(0f, r - minSize)
                                        b = (b + dy).coerceIn(t + minSize, pageHeightPx)
                                    }
                                    ResizeHandle.LEFT_CENTER -> l = (l + dx).coerceIn(0f, r - minSize)
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

            DragPill(
                isDarkMode = isDarkMode,
                modifier = Modifier
                    .align(if (isHandleAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .offset(y = if (isHandleAtTop) (-32).dp else 32.dp)
                    .zIndex(20f)
                    .pointerInput(pageWidthPx, pageHeightPx, onDragStart, onDragEnd, onDragCancel) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDraggingOrResizing = true
                                onDragStart(offset)
                            },
                            onDragEnd = {
                                isDraggingOrResizing = false
                                val normalized = Rect(
                                    left = currentRectPx.left / pageWidthPx,
                                    top = currentRectPx.top / pageHeightPx,
                                    right = currentRectPx.right / pageWidthPx,
                                    bottom = currentRectPx.bottom / pageHeightPx
                                )
                                onBoundsChanged(normalized)
                                onDragEnd()
                            },
                            onDragCancel = {
                                isDraggingOrResizing = false
                                onDragCancel()
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val w = currentRectPx.width
                            val h = currentRectPx.height
                            val rawLeft = currentRectPx.left + dragAmount.x
                            val rawTop = currentRectPx.top + dragAmount.y
                            val newLeft = rawLeft.coerceIn(0f, pageWidthPx - w)
                            val newTop = rawTop.coerceIn(0f, pageHeightPx - h)
                            val newRect = Rect(newLeft, newTop, newLeft + w, newTop + h)
                            currentRectPx = newRect
                            onDrag(dragAmount, newRect)
                        }
                    }
            )
        }
    }
}

@Composable
private fun DragPill(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean
) {
    Surface(
        modifier = modifier
            .size(width = 48.dp, height = 24.dp),
        shape = CircleShape,
        color = if (isDarkMode) Color.White else Color.Black,
        contentColor = if (isDarkMode) Color.Black else Color.White,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = R.drawable.drag_handle),
                contentDescription = "Drag to move text box",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}