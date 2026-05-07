package com.aryan.reader.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfInkRenderData
import com.aryan.reader.shared.pdf.SharedPdfInkRenderer
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextFontPreset
import com.aryan.reader.shared.pdf.SharedPdfTextResizeHandle
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.movedBy
import com.aryan.reader.shared.pdf.resizedBy
import com.aryan.reader.shared.pdf.sharedPdfStrokePercent
import com.aryan.reader.shared.pdf.sharedPdfStrokeWidthRange
import kotlin.math.roundToInt

val SharedPdfAnnotationDefaultTools: List<PdfInkTool> = listOf(
    PdfInkTool.PEN,
    PdfInkTool.FOUNTAIN_PEN,
    PdfInkTool.PENCIL,
    PdfInkTool.HIGHLIGHTER,
    PdfInkTool.HIGHLIGHTER_ROUND,
    PdfInkTool.TEXT,
    PdfInkTool.ERASER
)

@Composable
fun SharedPdfAnnotationToolDock(
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    tools: List<PdfInkTool> = SharedPdfAnnotationDefaultTools,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    isHighlighterSnapEnabled: Boolean = false,
    onHighlighterSnapChange: (Boolean) -> Unit = {}
) {
    val strokeRange = selectedTool.sharedPdfStrokeWidthRange()
    val sliderValue = strokeWidth.coerceIn(strokeRange.start, strokeRange.endInclusive)
    val showColorPalette = selectedTool != PdfInkTool.TEXT && selectedTool != PdfInkTool.ERASER
    val showStrokeSettings = selectedTool != PdfInkTool.TEXT
    val palette = if (selectedTool.isHighlighter) {
        SharedPdfAnnotationDefaults.highlighterPalette
    } else {
        SharedPdfAnnotationDefaults.penPalette
    }

    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tools.distinct().chunked(4).forEach { rowTools ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowTools.forEach { tool ->
                        SharedPdfToolButton(
                            tool = tool,
                            selectedTool = selectedTool,
                            selectedColor = selectedColor,
                            strokeWidth = strokeWidth,
                            onToolSelected = onToolSelected
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DockCircleButton(onClick = onUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo annotation",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DockCircleButton(onClick = onClearPage) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear page annotations",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showColorPalette) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    palette.forEach { argb ->
                        val selected = argb == selectedColor
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb).copy(alpha = 1f))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.22f),
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(argb) }
                        )
                    }
                }
            }

            if (showStrokeSettings) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Thickness ${sliderValue.sharedPdfStrokePercent(strokeRange)}",
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = onStrokeWidthChange,
                        valueRange = strokeRange,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = if (selectedTool == PdfInkTool.ERASER) Color.White else Color(selectedColor).copy(alpha = 1f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        )
                    )
                }
            }

            if (selectedTool.isHighlighter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Straight line",
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Switch(
                        checked = isHighlighterSnapEnabled,
                        onCheckedChange = onHighlighterSnapChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(selectedColor).copy(alpha = 1f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SharedPdfTextAnnotationDock(
    style: SharedPdfTextStyleConfig,
    onStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SharedPdfTextStyleControls(
                style = style,
                onStyleChange = onStyleChange,
                dark = true
            )
        }
    }
}

@Composable
fun SharedPdfInlineTextEditorOverlay(
    draft: SharedPdfTextDraft?,
    canvasSize: IntSize,
    onTextChange: (String) -> Unit,
    onBoundsChange: (PdfPageBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    if (draft == null || canvasSize.width <= 0 || canvasSize.height <= 0) return

    SharedPdfTextBoxEditorOverlay(
        id = draft.id,
        text = draft.text,
        style = draft.style,
        bounds = draft.bounds,
        canvasSize = canvasSize,
        onTextChange = onTextChange,
        onBoundsChange = onBoundsChange,
        modifier = modifier
    )
}

@Composable
fun SharedPdfTextBoxEditorOverlay(
    id: String,
    text: String,
    style: SharedPdfTextStyleConfig,
    bounds: PdfPageBounds,
    canvasSize: IntSize,
    onTextChange: (String) -> Unit,
    onBoundsChange: (PdfPageBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return

    val density = LocalDensity.current
    val focusRequester = remember(id) { FocusRequester() }
    var liveBounds by remember(id) { mutableStateOf(bounds) }
    var isResizing by remember(id) { mutableStateOf(false) }

    LaunchedEffect(bounds) {
        if (!isResizing) {
            liveBounds = bounds
        }
    }

    val leftPx = liveBounds.left * canvasSize.width
    val topPx = liveBounds.top * canvasSize.height
    val widthPx = ((liveBounds.right - liveBounds.left) * canvasSize.width).coerceAtLeast(50f)
    val heightPx = ((liveBounds.bottom - liveBounds.top) * canvasSize.height).coerceAtLeast(50f)
    val textColor = Color(style.colorArgb)
    val backgroundColor = Color(style.backgroundColorArgb)
    val handleSize = 10.dp
    val handleTouchSize = 38.dp
    val handleTouchSizePx = with(density) { handleTouchSize.toPx() }
    val moveHandleWidth = 54.dp
    val moveHandleHeight = 24.dp
    val moveHandleWidthPx = with(density) { moveHandleWidth.toPx() }
    val moveHandleHeightPx = with(density) { moveHandleHeight.toPx() }
    val moveHandleBelow = topPx + heightPx + moveHandleHeightPx + 10f <= canvasSize.height

    LaunchedEffect(id, style) {
        focusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(
                color = textColor,
                fontSize = style.fontSize.sp,
                lineHeight = (style.fontSize * 1.25f).sp,
                fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = sharedPdfFontFamily(style.fontName ?: style.fontPath),
                textDecoration = style.textDecoration
            ),
            cursorBrush = SolidColor(textColor),
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { heightPx.toDp() })
                .background(
                    color = if (style.backgroundColorArgb.isTransparentArgb()) {
                        Color.Transparent
                    } else {
                        backgroundColor
                    },
                    shape = RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF64B5F6),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(focusRequester)
        )

        SharedPdfTextResizeHandle.entries.forEach { handle ->
            val center = handle.centerOffset(
                leftPx = leftPx,
                topPx = topPx,
                widthPx = widthPx,
                heightPx = heightPx
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - handleTouchSizePx / 2f).roundToInt(),
                            (center.y - handleTouchSizePx / 2f).roundToInt()
                        )
                    }
                    .size(handleTouchSize)
                    .pointerInput(id, handle, canvasSize) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                            },
                            onDragEnd = {
                                isResizing = false
                                onBoundsChange(liveBounds)
                            },
                            onDragCancel = {
                                isResizing = false
                                liveBounds = bounds
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                liveBounds = liveBounds.resizedBy(
                                    handle = handle,
                                    deltaXPx = dragAmount.x,
                                    deltaYPx = dragAmount.y,
                                    canvasSize = canvasSize
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .background(Color(0xFF64B5F6), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (leftPx + (widthPx / 2f) - (moveHandleWidthPx / 2f)).roundToInt(),
                        if (moveHandleBelow) {
                            (topPx + heightPx + 8f).roundToInt()
                        } else {
                            (topPx - moveHandleHeightPx - 8f).roundToInt()
                        }
                    )
                }
                .size(width = moveHandleWidth, height = moveHandleHeight)
                .clip(CircleShape)
                .background(Color(0xFF64B5F6))
                .border(1.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                .pointerInput(id, canvasSize) {
                    detectDragGestures(
                        onDragStart = {
                            isResizing = true
                        },
                        onDragEnd = {
                            isResizing = false
                            onBoundsChange(liveBounds)
                        },
                        onDragCancel = {
                            isResizing = false
                            liveBounds = bounds
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            liveBounds = liveBounds.movedBy(
                                deltaXPx = dragAmount.x,
                                deltaYPx = dragAmount.y,
                                canvasSize = canvasSize
                            )
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(width = 24.dp, height = 10.dp)) {
                val lineColor = Color.White.copy(alpha = 0.92f)
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.2f, size.height * 0.25f),
                    end = Offset(size.width * 0.8f, size.height * 0.25f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.2f, size.height * 0.75f),
                    end = Offset(size.width * 0.8f, size.height * 0.75f),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
fun SharedPdfTextStyleControls(
    style: SharedPdfTextStyleConfig,
    onStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false
) {
    val labelColor = if (dark) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant
    val buttonTextColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val selectedBackground = if (dark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val unselectedBackground = if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    var fontMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Font", color = labelColor, style = MaterialTheme.typography.labelMedium)
            Box {
                TextButton(onClick = { fontMenuExpanded = true }) {
                    Text(
                        text = style.displayFontName(),
                        color = buttonTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = fontMenuExpanded,
                    onDismissRequest = { fontMenuExpanded = false }
                ) {
                    SharedPdfTextAnnotationDefaults.fontPresets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name) },
                            onClick = {
                                onStyleChange(style.withFontPreset(preset))
                                fontMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SharedPdfTextAnnotationDefaults.fontSizes.chunked(4).forEach { rowSizes ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    rowSizes.forEach { size ->
                        SharedTextStyleChoiceButton(
                            selected = style.fontSize.toInt() == size.toInt(),
                            selectedBackground = selectedBackground,
                            unselectedBackground = unselectedBackground,
                            onClick = { onStyleChange(style.copy(fontSize = size)) }
                        ) {
                            Text(
                                text = size.toInt().toString(),
                                color = buttonTextColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SharedTextStyleChoiceButton(
                selected = style.isBold,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isBold = !style.isBold)) }
            ) {
                Text("B", color = buttonTextColor, fontWeight = FontWeight.Bold)
            }
            SharedTextStyleChoiceButton(
                selected = style.isItalic,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isItalic = !style.isItalic)) }
            ) {
                Text("I", color = buttonTextColor, fontStyle = FontStyle.Italic)
            }
            SharedTextStyleChoiceButton(
                selected = style.isUnderline,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isUnderline = !style.isUnderline)) }
            ) {
                Text("U", color = buttonTextColor, textDecoration = TextDecoration.Underline)
            }
            SharedTextStyleChoiceButton(
                selected = style.isStrikeThrough,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isStrikeThrough = !style.isStrikeThrough)) }
            ) {
                Text("S", color = buttonTextColor, textDecoration = TextDecoration.LineThrough)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Text", color = labelColor, style = MaterialTheme.typography.labelMedium)
            SharedTextColorSwatches(
                palette = SharedPdfTextAnnotationDefaults.textColorPalette,
                selectedArgb = style.colorArgb,
                allowTransparent = false,
                dark = dark,
                onColorSelected = { onStyleChange(style.copy(colorArgb = it)) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fill", color = labelColor, style = MaterialTheme.typography.labelMedium)
            SharedTextColorSwatches(
                palette = SharedPdfTextAnnotationDefaults.backgroundColorPalette,
                selectedArgb = style.backgroundColorArgb,
                allowTransparent = true,
                dark = dark,
                onColorSelected = { onStyleChange(style.copy(backgroundColorArgb = it)) }
            )
        }
    }
}

@Composable
fun SharedPdfAnnotationOverlay(
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    canvasSize: IntSize,
    activeTool: PdfInkTool = PdfInkTool.PEN,
    activeStrokeColorArgb: Int = 0xFF1976D2.toInt(),
    activeStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).strokeWidth,
    selectedAnnotationId: String? = null
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            annotations.forEach { annotation ->
                val isSelected = annotation.matchesSelectedAnnotation(selectedAnnotationId)
                if (isSelected && annotation.kind == PdfAnnotationKind.INK) {
                    SharedPdfInkRenderer.createRenderData(annotation, canvasSize)?.let { renderData ->
                        drawInkRenderData(renderData, selectedOutline = true)
                    }
                }

                when (annotation.kind) {
                    PdfAnnotationKind.HIGHLIGHT -> {
                        val highlightBounds = annotation.boundsList.ifEmpty { listOfNotNull(annotation.bounds) }
                        highlightBounds.forEach { bounds ->
                            drawRect(
                                color = Color(annotation.colorArgb),
                                topLeft = bounds.topLeft(canvasSize),
                                size = bounds.size(canvasSize),
                                blendMode = BlendMode.Multiply
                            )
                        }
                    }
                    PdfAnnotationKind.INK -> {
                        SharedPdfInkRenderer.createRenderData(annotation, canvasSize)?.let(::drawInkRenderData)
                    }
                    PdfAnnotationKind.TEXT -> {
                        val bounds = annotation.bounds ?: return@forEach
                        if (!annotation.backgroundArgb.isTransparentArgb()) {
                            drawRoundRect(
                                color = Color(annotation.backgroundArgb),
                                topLeft = bounds.topLeft(canvasSize),
                                size = bounds.size(canvasSize),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }

                if (isSelected && annotation.kind != PdfAnnotationKind.INK) {
                    val bounds = annotation.bounds ?: annotation.boundsList.firstOrNull() ?: return@forEach
                    drawRect(
                        color = Color(0xFF64B5F6),
                        topLeft = bounds.topLeft(canvasSize),
                        size = bounds.size(canvasSize),
                        style = Stroke(width = 2f)
                    )
                }
            }

            if (activeStroke.size > 1) {
                val activeAnnotation = SharedPdfAnnotation(
                    id = "active",
                    pageIndex = 0,
                    kind = PdfAnnotationKind.INK,
                    tool = activeTool,
                    points = activeStroke,
                    colorArgb = activeStrokeColorArgb,
                    strokeWidth = activeStrokeWidth
                )
                SharedPdfInkRenderer.createRenderData(activeAnnotation, canvasSize)?.let(::drawInkRenderData)
            }
        }

        annotations
            .filter { it.kind == PdfAnnotationKind.TEXT && it.text.isNotBlank() }
            .forEach { annotation ->
                val bounds = annotation.bounds ?: return@forEach
                val leftPx = bounds.left * canvasSize.width
                val topPx = bounds.top * canvasSize.height
                val widthPx = ((bounds.right - bounds.left) * canvasSize.width).coerceAtLeast(24f)
                val heightPx = ((bounds.bottom - bounds.top) * canvasSize.height).coerceAtLeast(18f)
                Text(
                    text = annotation.text,
                    color = Color(annotation.colorArgb),
                    fontSize = annotation.fontSize.sp,
                    lineHeight = (annotation.fontSize * 1.25f).sp,
                    fontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = annotation.sharedPdfTextFontFamily(),
                    textDecoration = annotation.textDecoration,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = SharedPdfTextAnnotationDefaults.estimateLineCount(annotation.text, annotation.fontSize, widthPx),
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .width(with(density) { widthPx.toDp() })
                        .heightIn(
                            min = with(density) { heightPx.toDp() },
                            max = with(density) { heightPx.toDp() }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
    }
}

@Composable
fun SharedPdfPageNumberOverlay(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    isDarkPage: Boolean = false
) {
    if (pageCount <= 0 || pageIndex !in 0 until pageCount) return
    val textColor = if (isDarkPage) Color.White else Color.Black
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "${pageIndex + 1}/$pageCount",
            color = textColor.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        )
    }
}

@Composable
fun SharedPdfEmbeddedAnnotationOverlay(
    annotations: List<SharedPdfEmbeddedAnnotation>,
    canvasSize: IntSize,
    selectedAnnotationId: String? = null
) {
    if (annotations.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    Canvas(Modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            val bounds = annotation.bounds
            val isSelected = annotation.id == selectedAnnotationId
            val color = if (isSelected) Color(0xFF1976D2) else Color(0xFFFF9800)
            drawRect(
                color = color.copy(alpha = if (isSelected) 0.12f else 0.07f),
                topLeft = bounds.topLeft(canvasSize),
                size = bounds.size(canvasSize)
            )
            drawRect(
                color = color,
                topLeft = bounds.topLeft(canvasSize),
                size = bounds.size(canvasSize),
                style = Stroke(width = if (isSelected) 2.5f else 1.25f)
            )
        }
    }
}

@Composable
private fun SharedPdfToolButton(
    tool: PdfInkTool,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    onToolSelected: (PdfInkTool) -> Unit
) {
    val selected = tool == selectedTool
    val toolColor = if (selected) {
        selectedColor
    } else {
        SharedPdfAnnotationDefaults.configFor(tool).colorArgb
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0f))
            .clickable { onToolSelected(tool) },
        contentAlignment = Alignment.Center
    ) {
        when (tool) {
            PdfInkTool.TEXT -> Icon(
                imageVector = Icons.Default.TextFields,
                contentDescription = "text",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            PdfInkTool.ERASER -> Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "eraser",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            else -> SharedPdfPenIcon(
                tool = tool,
                color = Color(toolColor).copy(alpha = 1f),
                inkColor = Color(toolColor),
                isSelected = selected,
                strokeWidth = strokeWidth,
                modifier = Modifier.size(width = 28.dp, height = 34.dp)
            )
        }
    }
}

@Composable
private fun SharedTextStyleChoiceButton(
    selected: Boolean,
    selectedBackground: Color,
    unselectedBackground: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) selectedBackground else unselectedBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SharedTextColorSwatches(
    palette: List<Int>,
    selectedArgb: Int,
    allowTransparent: Boolean,
    dark: Boolean,
    onColorSelected: (Int) -> Unit
) {
    val borderBase = if (dark) Color.White else Color.Black
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        palette
            .filter { allowTransparent || !it.isTransparentArgb() }
            .forEach { argb ->
                val selected = argb == selectedArgb || (argb.isTransparentArgb() && selectedArgb.isTransparentArgb())
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (argb.isTransparentArgb()) Color.Transparent else Color(argb).copy(alpha = 1f))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) borderBase.copy(alpha = 0.88f) else borderBase.copy(alpha = 0.22f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(argb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (argb.isTransparentArgb()) {
                        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                            drawCircle(color = borderBase.copy(alpha = 0.18f))
                            drawLine(
                                color = borderBase.copy(alpha = 0.68f),
                                start = Offset(size.width * 0.22f, size.height * 0.78f),
                                end = Offset(size.width * 0.78f, size.height * 0.22f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun DockCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SharedPdfPenIcon(
    tool: PdfInkTool,
    color: Color,
    inkColor: Color,
    isSelected: Boolean,
    strokeWidth: Float,
    modifier: Modifier = Modifier
) {
    val animatedBodyColor by animateColorAsState(targetValue = color, label = "shared_pen_color")
    val animatedInkColor by animateColorAsState(targetValue = inkColor, label = "shared_ink_color")
    val inkProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        label = "shared_ink_progress"
    )

    Canvas(modifier = modifier) {
        val penWidth = size.width * 0.65f
        val startX = (size.width - penWidth) / 2f
        val tipHeight = size.height * 0.45f
        val collarHeight = size.height * 0.15f
        val bodyHeight = size.height * 0.35f
        val topPadding = size.height * 0.05f
        val tipRect = Rect(Offset(startX, topPadding), Size(penWidth, tipHeight))
        val collarRect = Rect(Offset(startX, topPadding + tipHeight), Size(penWidth, collarHeight))
        val bodyRect = Rect(Offset(startX, topPadding + tipHeight + collarHeight), Size(penWidth, bodyHeight))

        drawMatteCylinder(Color(0xFF454545), bodyRect)
        when (tool) {
            PdfInkTool.FOUNTAIN_PEN -> {
                drawMatteCylinder(animatedBodyColor, collarRect)
                drawFountainNib(Color(0xFFCFD8DC), animatedBodyColor, tipRect)
            }
            PdfInkTool.PENCIL -> {
                drawMatteCylinder(animatedBodyColor, collarRect)
                drawPencilHead(animatedBodyColor, tipRect)
            }
            PdfInkTool.HIGHLIGHTER -> drawHighlighterChiselParts(animatedBodyColor, collarRect, tipRect)
            PdfInkTool.HIGHLIGHTER_ROUND -> drawHighlighterRoundParts(animatedBodyColor, collarRect, tipRect)
            PdfInkTool.PEN -> {
                drawMatteCylinder(animatedBodyColor, collarRect)
                drawMarkerHead(animatedBodyColor, tipRect)
            }
            PdfInkTool.TEXT,
            PdfInkTool.ERASER -> Unit
        }

        if (inkProgress > 0.01f) {
            drawInkPreview(
                tool = tool,
                color = animatedInkColor,
                progress = inkProgress,
                startPoint = Offset(size.width / 2f, topPadding - 1f),
                strokeWidth = strokeWidth
            )
        }
    }
}

fun Offset.toSharedPdfPoint(size: IntSize, timestamp: Long): PdfPagePoint {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    return PdfPagePoint(
        x = (x / width).coerceIn(0f, 1f),
        y = (y / height).coerceIn(0f, 1f),
        timestamp = timestamp
    )
}

fun pageBoundsFromSharedPdfPoint(point: Offset, size: IntSize): PdfPageBounds {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val left = (point.x / width).coerceIn(0f, 0.92f)
    val top = (point.y / height).coerceIn(0f, 0.95f)
    return PdfPageBounds(
        left = left,
        top = top,
        right = (left + 0.32f).coerceAtMost(1f),
        bottom = (top + 0.08f).coerceAtMost(1f)
    )
}

fun SharedPdfAnnotation.sharedPdfHitTest(
    point: Offset,
    size: IntSize,
    lastPoint: Offset? = null,
    eraserStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth
): Boolean {
    val pageWidthPx = size.width.coerceAtLeast(1).toFloat()
    val pageAspectRatio = size.width.toFloat() / size.height.coerceAtLeast(1).toFloat()
    return SharedPdfInkRenderer.isAnnotationHit(
        annotation = this,
        hitPoint = point.toSharedPdfPoint(size, timestamp = 0L),
        pageWidthPx = pageWidthPx,
        pageAspectRatio = pageAspectRatio,
        eraserStrokeWidth = eraserStrokeWidth,
        lastHitPoint = lastPoint?.toSharedPdfPoint(size, timestamp = 0L)
    )
}

fun SharedPdfEmbeddedAnnotation.sharedPdfEmbeddedHitTest(
    point: Offset,
    size: IntSize,
    tolerancePx: Float = 24f
): Boolean {
    val rect = bounds
    val left = (rect.left * size.width) - tolerancePx
    val top = (rect.top * size.height) - tolerancePx
    val right = (rect.right * size.width) + tolerancePx
    val bottom = (rect.bottom * size.height) + tolerancePx
    return point.x in left..right && point.y in top..bottom
}

private fun DrawScope.drawInkRenderData(
    renderData: SharedPdfInkRenderData,
    selectedOutline: Boolean = false
) {
    when (renderData) {
        is SharedPdfInkRenderData.Standard -> {
            drawPath(
                path = renderData.path,
                color = if (selectedOutline) Color(0xFF64B5F6).copy(alpha = 0.30f) else renderData.color,
                style = Stroke(
                    width = if (selectedOutline) renderData.strokeWidthPx + 7f else renderData.strokeWidthPx,
                    cap = renderData.cap,
                    join = StrokeJoin.Round
                ),
                blendMode = if (selectedOutline) BlendMode.SrcOver else renderData.blendMode
            )
        }
        is SharedPdfInkRenderData.Fountain -> {
            drawPath(
                path = renderData.path,
                color = if (selectedOutline) Color(0xFF64B5F6).copy(alpha = 0.30f) else renderData.color,
                style = Fill
            )
        }
        is SharedPdfInkRenderData.Pencil -> {
            val color = if (selectedOutline) {
                Color(0xFF64B5F6).copy(alpha = 0.28f)
            } else {
                renderData.color.copy(alpha = renderData.color.alpha * renderData.velocityAlpha)
            }
            val width = if (selectedOutline) renderData.strokeWidthPx + 7f else renderData.strokeWidthPx
            drawPath(
                path = renderData.path,
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            if (!selectedOutline) {
                translate(left = 0.7f, top = 0.4f) {
                    drawPath(
                        path = renderData.path,
                        color = renderData.color.copy(alpha = renderData.color.alpha * 0.18f),
                        style = Stroke(width = (width * 0.55f).coerceAtLeast(0.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMatteCylinder(color: Color, rect: Rect) {
    drawRect(
        brush = Brush.horizontalGradient(
            0.0f to color.darker(0.6f),
            0.3f to color.lighter(0.1f),
            0.5f to color,
            0.85f to color.darker(0.5f),
            1.0f to color.darker(0.7f),
            startX = rect.left,
            endX = rect.right
        ),
        topLeft = rect.topLeft,
        size = rect.size
    )
}

private fun DrawScope.drawFountainNib(metalColor: Color, inkColor: Color, rect: Rect) {
    val centerX = rect.left + rect.width / 2f
    val path = Path().apply {
        moveTo(rect.left + rect.width * 0.15f, rect.bottom)
        lineTo(rect.right - rect.width * 0.15f, rect.bottom)
        cubicTo(rect.right - rect.width * 0.1f, rect.bottom - rect.height * 0.6f, rect.right, rect.top + rect.height * 0.2f, centerX, rect.top)
        cubicTo(rect.left, rect.top + rect.height * 0.2f, rect.left + rect.width * 0.1f, rect.bottom - rect.height * 0.6f, rect.left + rect.width * 0.15f, rect.bottom)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            0.0f to metalColor.darker(0.6f),
            0.4f to Color.White,
            0.6f to metalColor,
            1.0f to metalColor.darker(0.6f),
            startX = rect.left,
            endX = rect.right
        )
    )
    drawCircle(Color.Black.copy(alpha = 0.7f), radius = rect.width * 0.06f, center = Offset(centerX, rect.bottom - rect.height * 0.5f))
    drawLine(Color.Black.copy(alpha = 0.6f), start = Offset(centerX, rect.top), end = Offset(centerX, rect.bottom - rect.height * 0.5f), strokeWidth = 1.2f)
    drawCircle(inkColor.copy(alpha = 0.5f), radius = rect.width * 0.04f, center = Offset(centerX, rect.bottom - rect.height * 0.5f))
}

private fun DrawScope.drawMarkerHead(inkColor: Color, rect: Rect) {
    val centerX = rect.left + rect.width / 2f
    val plasticColor = Color(0xFF616161)
    val coneHeight = rect.height * 0.8f
    val conePath = Path().apply {
        moveTo(rect.left, rect.bottom)
        lineTo(rect.right, rect.bottom)
        lineTo(centerX + rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        lineTo(centerX - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        close()
    }
    drawPath(
        path = conePath,
        brush = Brush.horizontalGradient(
            0.0f to plasticColor.darker(0.5f),
            0.5f to plasticColor,
            1.0f to plasticColor.darker(0.5f),
            startX = rect.left,
            endX = rect.right
        )
    )
    val tipPath = Path().apply {
        moveTo(centerX - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        lineTo(centerX + rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        quadraticTo(centerX, rect.top, centerX, rect.top)
        close()
    }
    drawPath(path = tipPath, color = inkColor)
}

private fun DrawScope.drawPencilHead(inkColor: Color, rect: Rect) {
    val centerX = rect.left + rect.width / 2f
    val woodColor = Color(0xFFFFCC80)
    val woodPath = Path().apply {
        moveTo(rect.left, rect.bottom)
        val scallops = 3
        val step = rect.width / scallops
        for (i in 0 until scallops) {
            quadraticTo(
                rect.left + i * step + step / 2f,
                rect.bottom - rect.width * 0.1f,
                rect.left + (i + 1) * step,
                rect.bottom
            )
        }
        lineTo(centerX + rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(centerX - rect.width * 0.12f, rect.top + rect.height * 0.25f)
        close()
    }
    drawPath(
        path = woodPath,
        brush = Brush.horizontalGradient(
            0.0f to woodColor.darker(0.3f),
            0.5f to woodColor.lighter(0.1f),
            1.0f to woodColor.darker(0.3f),
            startX = rect.left,
            endX = rect.right
        )
    )
    val leadPath = Path().apply {
        moveTo(centerX - rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(centerX + rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(centerX, rect.top)
        close()
    }
    drawPath(path = leadPath, color = inkColor)
}

private fun DrawScope.drawHighlighterChiselParts(color: Color, collarRect: Rect, tipRect: Rect) {
    drawMatteCylinder(color, collarRect)
    val bodyColor = Color(0xFF454545)
    val neckHeight = tipRect.height * 0.65f
    val inkTipHeight = tipRect.height - neckHeight
    val neckTopY = tipRect.bottom - neckHeight
    val centerX = tipRect.center.x
    val neckTopHalfWidth = tipRect.width * 0.25f
    val neckPath = Path().apply {
        moveTo(tipRect.left, tipRect.bottom)
        lineTo(tipRect.right, tipRect.bottom)
        lineTo(centerX + neckTopHalfWidth, neckTopY)
        lineTo(centerX - neckTopHalfWidth, neckTopY)
        close()
    }
    drawPath(
        path = neckPath,
        brush = Brush.horizontalGradient(
            0.0f to bodyColor.darker(0.6f),
            0.3f to bodyColor.lighter(0.1f),
            0.5f to bodyColor,
            0.85f to bodyColor.darker(0.5f),
            1.0f to bodyColor.darker(0.7f),
            startX = tipRect.left,
            endX = tipRect.right
        )
    )

    val slantDrop = inkTipHeight * 0.4f
    val tipPath = Path().apply {
        moveTo(centerX - neckTopHalfWidth, neckTopY)
        lineTo(centerX + neckTopHalfWidth, neckTopY)
        lineTo(centerX + neckTopHalfWidth, tipRect.top + slantDrop)
        lineTo(centerX - neckTopHalfWidth, tipRect.top)
        close()
    }
    drawPath(
        path = tipPath,
        brush = Brush.horizontalGradient(
            0.0f to color.darker(0.8f),
            0.5f to color,
            1.0f to color.darker(0.8f),
            startX = centerX - neckTopHalfWidth,
            endX = centerX + neckTopHalfWidth
        )
    )
}

private fun DrawScope.drawHighlighterRoundParts(color: Color, collarRect: Rect, tipRect: Rect) {
    drawMatteCylinder(color, collarRect)
    val bodyColor = Color(0xFF454545)
    val neckHeight = tipRect.height * 0.65f
    val neckTopY = tipRect.bottom - neckHeight
    val centerX = tipRect.center.x
    val neckTopHalfWidth = tipRect.width * 0.25f
    val neckPath = Path().apply {
        moveTo(tipRect.left, tipRect.bottom)
        lineTo(tipRect.right, tipRect.bottom)
        lineTo(centerX + neckTopHalfWidth, neckTopY)
        lineTo(centerX - neckTopHalfWidth, neckTopY)
        close()
    }
    drawPath(
        path = neckPath,
        brush = Brush.horizontalGradient(
            0.0f to bodyColor.darker(0.6f),
            0.3f to bodyColor.lighter(0.1f),
            0.5f to bodyColor,
            0.85f to bodyColor.darker(0.5f),
            1.0f to bodyColor.darker(0.7f),
            startX = tipRect.left,
            endX = tipRect.right
        )
    )
    val tipHeight = tipRect.height - neckHeight
    val domeRect = Rect(
        left = centerX - neckTopHalfWidth,
        top = neckTopY - tipHeight,
        right = centerX + neckTopHalfWidth,
        bottom = neckTopY
    )
    val domePath = Path().apply {
        moveTo(domeRect.left, domeRect.bottom)
        lineTo(domeRect.right, domeRect.bottom)
        arcTo(domeRect, startAngleDegrees = 0f, sweepAngleDegrees = -180f, forceMoveTo = false)
        close()
    }
    drawPath(
        path = domePath,
        brush = Brush.radialGradient(
            colors = listOf(color.lighter(0.3f), color, color.darker(0.6f)),
            center = Offset(domeRect.center.x - domeRect.width * 0.2f, domeRect.top + domeRect.height * 0.4f),
            radius = domeRect.width
        )
    )
}

private fun DrawScope.drawInkPreview(
    tool: PdfInkTool,
    color: Color,
    progress: Float,
    startPoint: Offset,
    strokeWidth: Float
) {
    val path = Path().apply {
        moveTo(startPoint.x, startPoint.y)
        if (tool.isHighlighter) {
            val waveWidth = 46f
            cubicTo(startPoint.x + waveWidth * 0.35f, startPoint.y - 12f, startPoint.x + waveWidth * 0.65f, startPoint.y + 12f, startPoint.x + waveWidth, startPoint.y)
        } else {
            cubicTo(startPoint.x + 22f, startPoint.y - 24f, startPoint.x - 22f, startPoint.y - 52f, startPoint.x - 9f, startPoint.y - 28f)
            cubicTo(startPoint.x - 3f, startPoint.y - 8f, startPoint.x + 32f, startPoint.y - 16f, startPoint.x + 44f, startPoint.y - 34f)
        }
    }
    val width = SharedPdfInkRenderer.effectiveStrokeWidthPx(strokeWidth, pageWidthPx = 700f)
        .coerceIn(if (tool.isHighlighter) 5f else 1.2f, if (tool.isHighlighter) 16f else 5f)
    drawPath(
        path = path,
        color = color.copy(alpha = color.alpha * progress),
        style = Stroke(
            width = width,
            cap = if (tool == PdfInkTool.HIGHLIGHTER) StrokeCap.Butt else StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = if (tool.isHighlighter) BlendMode.SrcOver else BlendMode.SrcOver
    )
}

private fun SharedPdfAnnotation.matchesSelectedAnnotation(selectedAnnotationId: String?): Boolean {
    if (selectedAnnotationId == null) return false
    return id == selectedAnnotationId || id.startsWith("${selectedAnnotationId}_line_")
}

private val PdfInkTool.isHighlighter: Boolean
    get() = this == PdfInkTool.HIGHLIGHTER || this == PdfInkTool.HIGHLIGHTER_ROUND

private val SharedPdfAnnotation.textDecoration: TextDecoration
    get() {
        val decorations = mutableListOf<TextDecoration>()
        if (isUnderline) decorations += TextDecoration.Underline
        if (isStrikeThrough) decorations += TextDecoration.LineThrough
        return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
    }

private val SharedPdfTextStyleConfig.textDecoration: TextDecoration
    get() {
        val decorations = mutableListOf<TextDecoration>()
        if (isUnderline) decorations += TextDecoration.Underline
        if (isStrikeThrough) decorations += TextDecoration.LineThrough
        return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
    }

private fun SharedPdfAnnotation.sharedPdfTextFontFamily(): FontFamily? {
    return sharedPdfFontFamily(fontName ?: fontPath)
}

private fun SharedPdfTextResizeHandle.centerOffset(
    leftPx: Float,
    topPx: Float,
    widthPx: Float,
    heightPx: Float
): Offset {
    return when (this) {
        SharedPdfTextResizeHandle.TOP_LEFT -> Offset(leftPx, topPx)
        SharedPdfTextResizeHandle.TOP_CENTER -> Offset(leftPx + widthPx / 2f, topPx)
        SharedPdfTextResizeHandle.TOP_RIGHT -> Offset(leftPx + widthPx, topPx)
        SharedPdfTextResizeHandle.RIGHT_CENTER -> Offset(leftPx + widthPx, topPx + heightPx / 2f)
        SharedPdfTextResizeHandle.BOTTOM_RIGHT -> Offset(leftPx + widthPx, topPx + heightPx)
        SharedPdfTextResizeHandle.BOTTOM_CENTER -> Offset(leftPx + widthPx / 2f, topPx + heightPx)
        SharedPdfTextResizeHandle.BOTTOM_LEFT -> Offset(leftPx, topPx + heightPx)
        SharedPdfTextResizeHandle.LEFT_CENTER -> Offset(leftPx, topPx + heightPx / 2f)
    }
}

private fun SharedPdfTextStyleConfig.withFontPreset(preset: SharedPdfTextFontPreset): SharedPdfTextStyleConfig {
    return copy(
        fontName = preset.name.takeUnless { it == "Default" },
        fontPath = preset.fontPath
    )
}

private fun SharedPdfTextStyleConfig.displayFontName(): String {
    return fontName
        ?: fontPath?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "Default"
}

private fun sharedPdfFontFamily(nameOrPath: String?): FontFamily? {
    return when (nameOrPath) {
        "Merriweather",
        "Lora",
        "asset:fonts/merriweather.ttf",
        "asset:fonts/lora.ttf" -> FontFamily.Serif
        "Roboto Mono",
        "asset:fonts/roboto_mono.ttf" -> FontFamily.Monospace
        "Lato",
        "Lexend",
        "asset:fonts/lato.ttf",
        "asset:fonts/lexend.ttf" -> FontFamily.SansSerif
        else -> null
    }
}

private fun Int.isTransparentArgb(): Boolean {
    return (this ushr 24) == 0
}

private fun PdfPageBounds.topLeft(canvasSize: IntSize): Offset {
    return Offset(left * canvasSize.width, top * canvasSize.height)
}

private fun PdfPageBounds.size(canvasSize: IntSize): Size {
    return Size((right - left) * canvasSize.width, (bottom - top) * canvasSize.height)
}

private fun Color.darker(factor: Float = 0.7f): Color {
    return Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha
    )
}

private fun Color.lighter(factor: Float = 0.3f): Color {
    return Color(
        red = red + (1 - red) * factor,
        green = green + (1 - green) * factor,
        blue = blue + (1 - blue) * factor,
        alpha = alpha
    )
}
