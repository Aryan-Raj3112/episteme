package com.aryan.reader.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfInkRenderData
import com.aryan.reader.shared.pdf.SharedPdfInkRenderer
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextFontPreset
import com.aryan.reader.shared.pdf.SharedPdfTextResizeHandle
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.sharedPdfTextFontSizePx
import kotlin.math.roundToInt

@Composable
fun SharedPdfAnnotationOverlay(
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    canvasSize: IntSize,
    activeTool: PdfInkTool = PdfInkTool.PEN,
    activeStrokeColorArgb: Int = 0xFF1976D2.toInt(),
    activeStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).strokeWidth,
    selectedAnnotationId: String? = null,
    eraserPosition: Offset? = null,
    showEraserIndicator: Boolean = false,
    eraserStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            annotations.forEach { annotation ->
                when (annotation.kind) {
                    PdfAnnotationKind.HIGHLIGHT -> {
                        val highlightBounds = annotation.boundsList.ifEmpty { listOfNotNull(annotation.bounds) }
                        val style = sharedPdfHighlightAnnotationOverlayStyle(annotation)
                        highlightBounds.forEach { bounds ->
                            drawSharedPdfHighlightAnnotation(annotation, bounds, canvasSize, style)
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
            }

            if (activeStroke.isNotEmpty()) {
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

            if (showEraserIndicator && eraserPosition != null) {
                val radius = SharedPdfInkRenderer.effectiveStrokeWidthPx(eraserStrokeWidth, canvasSize)
                    .coerceAtLeast(8.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = radius,
                    center = eraserPosition
                )
                drawCircle(
                    color = Color.Black,
                    radius = radius,
                    center = eraserPosition,
                    style = Stroke(width = 1.dp.toPx())
                )
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
                val fontSizePx = annotation.sharedPdfTextFontSizePx(canvasSize)
                Text(
                    text = annotation.text,
                    color = Color(annotation.colorArgb),
                    fontSize = with(density) { fontSizePx.toSp() },
                    lineHeight = with(density) { (fontSizePx * 1.25f).toSp() },
                    fontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = annotation.sharedPdfTextFontFamily(),
                    textDecoration = annotation.textDecoration,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = SharedPdfTextAnnotationDefaults.estimateLineCount(annotation.text, fontSizePx, widthPx),
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

internal fun DrawScope.drawSharedPdfHighlightAnnotation(
    annotation: SharedPdfAnnotation,
    bounds: PdfPageBounds,
    canvasSize: IntSize,
    overlayStyle: SharedPdfHighlightAnnotationOverlayStyle
) {
    val topLeft = bounds.topLeft(canvasSize)
    val size = bounds.size(canvasSize)
    when (annotation.highlightStyle) {
        HighlightStyle.BACKGROUND -> drawRect(
            color = overlayStyle.color,
            topLeft = topLeft,
            size = size,
            blendMode = overlayStyle.blendMode
        )
        HighlightStyle.UNDERLINE -> drawSharedPdfHighlightLine(
            color = overlayStyle.lineColor,
            topLeft = topLeft,
            size = size,
            y = topLeft.y + size.height * 0.86f
        )
        HighlightStyle.WAVY_UNDERLINE -> drawSharedPdfHighlightWave(
            color = overlayStyle.lineColor,
            topLeft = topLeft,
            size = size,
            baselineY = topLeft.y + size.height * 0.86f
        )
        HighlightStyle.STRIKETHROUGH -> drawSharedPdfHighlightLine(
            color = overlayStyle.lineColor,
            topLeft = topLeft,
            size = size,
            y = topLeft.y + size.height * 0.52f
        )
    }
}

internal fun DrawScope.drawSharedPdfHighlightLine(
    color: Color,
    topLeft: Offset,
    size: Size,
    y: Float
) {
    if (size.width <= 0f || size.height <= 0f) return
    drawLine(
        color = color,
        start = Offset(topLeft.x, y),
        end = Offset(topLeft.x + size.width, y),
        strokeWidth = (size.height * 0.08f).coerceIn(1.5f, 4f),
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawSharedPdfHighlightWave(
    color: Color,
    topLeft: Offset,
    size: Size,
    baselineY: Float
) {
    if (size.width <= 0f || size.height <= 0f) return
    val amplitude = (size.height * 0.08f).coerceIn(1.2f, 3.5f)
    val wavelength = (size.height * 0.62f).coerceIn(6f, 14f)
    val path = Path()
    var x = topLeft.x
    val endX = topLeft.x + size.width
    path.moveTo(x, baselineY)
    while (x < endX) {
        val midX = (x + wavelength / 2f).coerceAtMost(endX)
        val nextX = (x + wavelength).coerceAtMost(endX)
        path.quadraticBezierTo(x + wavelength / 4f, baselineY - amplitude, midX, baselineY)
        path.quadraticBezierTo(x + wavelength * 0.75f, baselineY + amplitude, nextX, baselineY)
        x += wavelength
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = (size.height * 0.06f).coerceIn(1.2f, 3f), cap = StrokeCap.Round)
    )
}

internal data class SharedPdfHighlightAnnotationOverlayStyle(
    val color: Color,
    val blendMode: BlendMode,
    val lineColor: Color
)

internal fun sharedPdfHighlightAnnotationOverlayStyle(
    annotation: SharedPdfAnnotation
): SharedPdfHighlightAnnotationOverlayStyle {
    val storedColor = Color(annotation.colorArgb)
    val renderAlpha = storedColor.alpha
        .takeIf { it > 0f }
        ?.coerceAtMost(SharedPdfAndroidHighlightColors.RenderAlpha)
        ?: SharedPdfAndroidHighlightColors.RenderAlpha
    return SharedPdfHighlightAnnotationOverlayStyle(
        color = storedColor.copy(alpha = renderAlpha),
        blendMode = BlendMode.Multiply,
        lineColor = storedColor.copy(alpha = storedColor.alpha.takeIf { it > 0f } ?: 0.92f)
    )
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
    selectedAnnotationId: String? = null,
    onAnnotationTap: (SharedPdfEmbeddedAnnotation) -> Unit = {},
) {
    if (annotations.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    Box(Modifier.fillMaxSize()) {
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
        annotations.forEach { annotation ->
            val bounds = annotation.bounds
            val widthPx = ((bounds.right - bounds.left) * canvasSize.width).coerceAtLeast(24f)
            val heightPx = ((bounds.bottom - bounds.top) * canvasSize.height).coerceAtLeast(24f)
            Box(
                Modifier
                    .offset { IntOffset((bounds.left * canvasSize.width).roundToInt(), (bounds.top * canvasSize.height).roundToInt()) }
                    .size(with(LocalDensity.current) { widthPx.toDp() }, with(LocalDensity.current) { heightPx.toDp() })
                    .semantics {
                        contentDescription = "PDF comment by ${annotation.author.ifBlank { "Unknown author" }}"
                        selected = annotation.id == selectedAnnotationId
                    }
                    .clickable { onAnnotationTap(annotation) }
            )
        }
    }
}

internal const val SharedPdfAndroidPenPath = "M490,433L527,470L744,253L707,216L490,433ZM200,760L237,760L470,527L433,490L200,723L200,760ZM555,555L405,405L572,238L543,209Q543,209 543,209Q543,209 543,209L324,428L268,372L486,153Q510,129 542.5,129Q575,129 599,153L628,182L678,132Q690,120 706.5,120Q723,120 735,132L828,225Q840,237 840,253.5Q840,270 828,282L555,555ZM270,840L120,840L120,690L405,405L555,555L270,840Z"
internal const val SharedPdfAndroidMarkerPath = "M272,856L234,818L192,860Q173,879 145.5,879.5Q118,880 100,860Q81,841 81,814Q81,787 100,768L142,726L104,686L658,132Q670,120 687,120Q704,120 716,132L828,244Q840,256 840,273Q840,290 828,302L272,856ZM444,460L216,686L274,744L500,516L444,460Z"
internal const val SharedPdfAndroidKeyboardPath = "M160,760Q127,760 103.5,736.5Q80,713 80,680L80,280Q80,247 103.5,223.5Q127,200 160,200L800,200Q833,200 856.5,223.5Q880,247 880,280L880,680Q880,713 856.5,736.5Q833,760 800,760L160,760ZM160,680L800,680Q800,680 800,680Q800,680 800,680L800,280Q800,280 800,280Q800,280 800,280L160,280Q160,280 160,280Q160,280 160,280L160,680Q160,680 160,680Q160,680 160,680ZM320,640L640,640L640,560L320,560L320,640ZM200,520L280,520L280,440L200,440L200,520ZM320,520L400,520L400,440L320,440L320,520ZM440,520L520,520L520,440L440,440L440,520ZM560,520L640,520L640,440L560,440L560,520ZM680,520L760,520L760,440L680,440L680,520ZM200,400L280,400L280,320L200,320L200,400ZM320,400L400,400L400,320L320,320L320,400ZM440,400L520,400L520,320L440,320L440,400ZM560,400L640,400L640,320L560,320L560,400ZM680,400L760,400L760,320L680,320L680,400ZM160,680Q160,680 160,680Q160,680 160,680L160,280Q160,280 160,280Q160,280 160,280L160,280Q160,280 160,280Q160,280 160,280L160,680Q160,680 160,680Q160,680 160,680Z"
internal const val SharedPdfAndroidTextSelectStartPath = "M440,840L440,760L520,760L520,840L440,840ZM440,200L440,120L520,120L520,200L440,200ZM600,840L600,760L680,760L680,840L600,840ZM600,200L600,120L680,120L680,200L600,200ZM760,840L760,760L840,760L840,840L760,840ZM760,680L760,600L840,600L840,680L760,680ZM760,520L760,440L840,440L840,520L760,520ZM760,360L760,280L840,280L840,360L760,360ZM760,200L760,120L840,120L840,200L760,200ZM120,840L120,760L200,760L200,200L120,200L120,120L360,120L360,200L280,200L280,760L360,760L360,840L120,840Z"
internal const val SharedPdfAndroidEraserPath = "M690,720L880,720L880,800L610,800L690,720ZM190,800L105,715Q82,692 81.5,658Q81,624 104,600L544,144Q567,120 600.5,120Q634,120 657,143L856,342Q879,365 879,399Q879,433 856,456L520,800L190,800ZM486,720L800,398Q800,398 800,398Q800,398 800,398L602,200Q602,200 602,200Q602,200 602,200L160,656Q160,656 160,656Q160,656 160,656L224,720L486,720ZM480,480L480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480Z"
internal const val SharedPdfAndroidDeletePath = "M280,840Q247,840 223.5,816.5Q200,793 200,760L200,240L160,240L160,160L360,160L360,120L600,120L600,160L800,160L800,240L760,240L760,760Q760,793 736.5,816.5Q713,840 680,840L280,840ZM680,240L280,240L280,760Q280,760 280,760Q280,760 280,760L680,760Q680,760 680,760Q680,760 680,760L680,240ZM360,680L440,680L440,320L360,320L360,680ZM520,680L600,680L600,320L520,320L520,680ZM280,240L280,240L280,760Q280,760 280,760Q280,760 280,760L280,760Q280,760 280,760Q280,760 280,760L280,240Z"
internal const val SharedPdfAndroidTouchAppPath = "M419,880Q391,880 366.5,868Q342,856 325,834L107,557L126,537Q146,516 174,512Q202,508 226,523L300,568L300,240Q300,223 311.5,211.5Q323,200 340,200Q357,200 369,211.5Q381,223 381,240L381,712L284,652L388,785Q394,792 402,796Q410,800 419,800L640,800Q673,800 696.5,776.5Q720,753 720,720L720,560Q720,543 708.5,531.5Q697,520 680,520L461,520L461,440L680,440Q730,440 765,475Q800,510 800,560L800,720Q800,786 753,833Q706,880 640,880L419,880ZM167,340Q154,318 147,292.5Q140,267 140,240Q140,157 198.5,98.5Q257,40 340,40Q423,40 481.5,98.5Q540,157 540,240Q540,267 533,292.5Q526,318 513,340L444,300Q452,286 456,271.5Q460,257 460,240Q460,190 425,155Q390,120 340,120Q290,120 255,155Q220,190 220,240Q220,257 224,271.5Q228,286 236,300L167,340ZM502,620L502,620L502,620L502,620Q502,620 502,620Q502,620 502,620L502,620Q502,620 502,620Q502,620 502,620L502,620Q502,620 502,620Q502,620 502,620L502,620L502,620Z"
internal const val SharedPdfAndroidDoNotTouchPath = "M13 10.17l-2.5-2.5V2.25a1.25 1.25 0 0 1 2.5 0v7.92zm7 2.58v-7.5a1.25 1.25 0 0 0-2.5 0V11h-1V3.25a1.25 1.25 0 0 0-2.5 0v7.92l6 6v-4.42zM9.5 4.25C9.5 3.56 8.94 3 8.25 3c-.67 0-1.2.53-1.24 1.18L9.5 6.67V4.25zm3.5 5.92l-2.5-2.5V2.25a1.25 1.25 0 0 1 2.5 0v7.92zm7 2.58v-7.5a1.25 1.25 0 0 0-2.5 0V11h-1V3.25a1.25 1.25 0 0 0-2.5 0v7.92l6 6v-4.42zM9.5 4.25C9.5 3.56 8.94 3 8.25 3c-.67 0-1.2.53-1.24 1.18L9.5 6.67V4.25zm11.69 16.94L2.81 2.81L1.39 4.22l5.63 5.63L7 9.83v4.3c-1.11-.64-2.58-1.47-2.6-1.48c-.17-.09-.34-.14-.54-.14c-.26 0-.5.09-.7.26c-.04.01-1.16 1.11-1.16 1.11l6.8 7.18c.57.6 1.35.94 2.18.94H17c.62 0 1.18-.19 1.65-.52l-.02-.02l1.15 1.15l1.41-1.42z"
internal const val SharedPdfAndroidUndoPath = "M280,760L280,680L564,680Q627,680 673.5,640Q720,600 720,540Q720,480 673.5,440Q627,400 564,400L312,400L416,504L360,560L160,360L360,160L416,216L312,320L564,320Q661,320 730.5,383Q800,446 800,540Q800,634 730.5,697Q661,760 564,760L280,760Z"
internal const val SharedPdfAndroidRedoPath = "M396,760Q299,760 229.5,697Q160,634 160,540Q160,446 229.5,383Q299,320 396,320L648,320L544,216L600,160L800,360L600,560L544,504L648,400L396,400Q333,400 286.5,440Q240,480 240,540Q240,600 286.5,640Q333,680 396,680L680,680L680,760L396,760Z"

@Composable
internal fun SharedPdfAndroidPathIcon(
    pathData: String,
    tint: Color,
    modifier: Modifier = Modifier,
    viewportWidth: Float = 960f,
    viewportHeight: Float = 960f
) {
    val imageVector = remember(pathData, viewportWidth, viewportHeight) {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.White)
            )
        }.build()
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

@Composable
internal fun SharedPdfModeDockButton(
    tooltip: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val background by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (selected) 0.15f else 0f),
        label = "shared_pdf_mode_background"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.76f),
        label = "shared_pdf_mode_tint"
    )

    ReaderTooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(background)
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
    }
}

@Composable
internal fun SharedPdfInteractionDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.2f))
    )
}

@Composable
internal fun SharedPdfToolButton(
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
    SharedPdfToolButton(
        tool = tool,
        selected = selected,
        color = toolColor,
        strokeWidth = strokeWidth,
        onClick = { onToolSelected(tool) }
    )
}

@Composable
internal fun SharedPdfToolButton(
    tool: PdfInkTool,
    selected: Boolean,
    color: Int?,
    strokeWidth: Float,
    onClick: () -> Unit
) {
    val toolColor = color ?: SharedPdfAnnotationDefaults.configFor(tool).colorArgb
    val tooltip = sharedPdfToolLabel(tool)
    ReaderTooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (selected) 0.16f else 0f))
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center
        ) {
            when (tool) {
                PdfInkTool.TEXT -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidKeyboardPath,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.ERASER -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidEraserPath,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.HIGHLIGHTER,
                PdfInkTool.HIGHLIGHTER_ROUND -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidMarkerPath,
                    tint = Color(toolColor).copy(alpha = 1f),
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.PEN,
                PdfInkTool.FOUNTAIN_PEN,
                PdfInkTool.PENCIL -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidPenPath,
                    tint = Color(toolColor).copy(alpha = 1f),
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.NONE -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidTouchAppPath,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun sharedPdfToolLabel(tool: PdfInkTool): String = when (tool) {
    PdfInkTool.PEN -> readerString("content_desc_pen", "Pen")
    PdfInkTool.FOUNTAIN_PEN -> readerString("desktop_fountain_pen", "Fountain pen")
    PdfInkTool.PENCIL -> readerString("desktop_pencil", "Pencil")
    PdfInkTool.HIGHLIGHTER -> readerString("content_desc_highlighter", "Highlighter")
    PdfInkTool.HIGHLIGHTER_ROUND -> readerString("desktop_round_highlighter", "Round highlighter")
    PdfInkTool.TEXT -> readerString("desktop_text_note", "Text note")
    PdfInkTool.ERASER -> readerString("content_desc_eraser", "Eraser")
    PdfInkTool.NONE -> readerString("label_none", "None")
}

@Composable
internal fun SharedTextStyleChoiceButton(
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
internal fun SharedTextColorSwatches(
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
internal fun DockCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    showBackground: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    ReaderTooltipIconButton(
        tooltip = contentDescription.orEmpty(),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (showBackground && enabled) 0.10f else 0f))
                .then(
                    if (contentDescription == null) {
                        Modifier
                    } else {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
internal fun SharedPdfPenIcon(
    tool: PdfInkTool,
    color: Color,
    inkColor: Color,
    isSelected: Boolean,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
    showHighlighterSnap: Boolean = false
) {
    val animatedBodyColor by animateColorAsState(targetValue = color, label = "shared_pen_color")
    val animatedInkColor by animateColorAsState(targetValue = inkColor, label = "shared_ink_color")
    val inkProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
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
            PdfInkTool.NONE,
            PdfInkTool.TEXT,
            PdfInkTool.ERASER -> Unit
        }

        if (inkProgress > 0.01f) {
            val tipY = when (tool) {
                PdfInkTool.HIGHLIGHTER -> topPadding
                PdfInkTool.HIGHLIGHTER_ROUND -> topPadding + tipHeight * 0.15f
                else -> topPadding
            }
            drawInkPreview(
                tool = tool,
                color = animatedInkColor,
                progress = inkProgress,
                startPoint = Offset(size.width / 2f, tipY),
                strokeWidth = strokeWidth,
                isStraight = showHighlighterSnap
            )
        }
        if (showHighlighterSnap && tool.isDesktopHighlighter) {
            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(size.width * 0.18f, size.height * 0.9f),
                end = Offset(size.width * 0.82f, size.height * 0.9f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
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

internal fun DrawScope.drawInkRenderData(
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

internal fun DrawScope.drawMatteCylinder(color: Color, rect: Rect) {
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

internal fun DrawScope.drawFountainNib(metalColor: Color, inkColor: Color, rect: Rect) {
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

internal fun DrawScope.drawMarkerHead(inkColor: Color, rect: Rect) {
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

internal fun DrawScope.drawPencilHead(inkColor: Color, rect: Rect) {
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

internal fun DrawScope.drawHighlighterChiselParts(color: Color, collarRect: Rect, tipRect: Rect) {
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

internal fun DrawScope.drawHighlighterRoundParts(color: Color, collarRect: Rect, tipRect: Rect) {
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

internal fun DrawScope.drawInkPreview(
    tool: PdfInkTool,
    color: Color,
    progress: Float,
    startPoint: Offset,
    strokeWidth: Float,
    isStraight: Boolean = false
) {
    val x = startPoint.x
    val y = startPoint.y - 2f
    val path = Path().apply {
        moveTo(x, y)
        if (tool.isHighlighter) {
            val waveWidth = 70f
            if (isStraight) {
                lineTo(x + waveWidth, y)
            } else {
                val amplitude = 20f
                cubicTo(
                    x + waveWidth * 0.35f,
                    y - amplitude,
                    x + waveWidth * 0.65f,
                    y + amplitude,
                    x + waveWidth,
                    y
                )
            }
        } else {
            cubicTo(
                x + 16f,
                y - 18f,
                x - 18f,
                y - 34f,
                x - 7f,
                y - 21f
            )
            cubicTo(
                x - 2f,
                y - 5f,
                x + 22f,
                y - 11f,
                x + 31f,
                y - 26f
            )
        }
    }
    val revealProgress = sharedPdfInkPreviewRevealProgress(progress)
    val pathMeasure = PathMeasure()
    pathMeasure.setPath(path, false)
    val revealedPath = Path()
    val targetLength = pathMeasure.length * revealProgress
    if (targetLength <= 0f || !pathMeasure.getSegment(0f, targetLength, revealedPath, true)) return

    val width = SharedPdfInkRenderer.effectiveStrokeWidthPx(strokeWidth, pageWidthPx = 700f)
        .coerceIn(if (tool.isHighlighter) 5f else 1.2f, if (tool.isHighlighter) 16f else 5f)
    drawPath(
        path = revealedPath,
        color = color,
        style = Stroke(
            width = width,
            cap = if (tool == PdfInkTool.HIGHLIGHTER) StrokeCap.Butt else StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = if (tool.isHighlighter) BlendMode.SrcOver else BlendMode.SrcOver
    )
}

internal fun sharedPdfInkPreviewRevealProgress(progress: Float): Float {
    return progress.coerceIn(0f, 1f)
}

internal val PdfInkTool.isDesktopPenTool: Boolean
    get() = this == PdfInkTool.FOUNTAIN_PEN || this == PdfInkTool.PEN || this == PdfInkTool.PENCIL

internal val PdfInkTool.isDesktopHighlighter: Boolean
    get() = this == PdfInkTool.HIGHLIGHTER || this == PdfInkTool.HIGHLIGHTER_ROUND

private val PdfInkTool.isHighlighter: Boolean
    get() = isDesktopHighlighter

internal fun Int.withSharedPdfAnnotationAlpha(alpha: Float): Int {
    return Color(this).copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
}

private val SharedPdfAnnotation.textDecoration: TextDecoration
    get() {
        val decorations = mutableListOf<TextDecoration>()
        if (isUnderline) decorations += TextDecoration.Underline
        if (isStrikeThrough) decorations += TextDecoration.LineThrough
        return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
    }

internal val SharedPdfTextStyleConfig.textDecoration: TextDecoration
    get() {
        val decorations = mutableListOf<TextDecoration>()
        if (isUnderline) decorations += TextDecoration.Underline
        if (isStrikeThrough) decorations += TextDecoration.LineThrough
        return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
    }

internal fun SharedPdfAnnotation.sharedPdfTextFontFamily(): FontFamily? {
    return sharedPdfFontFamily(fontName ?: fontPath)
}

internal fun SharedPdfTextResizeHandle.centerOffset(
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

internal fun SharedPdfTextStyleConfig.withFontPreset(preset: SharedPdfTextFontPreset): SharedPdfTextStyleConfig {
    return copy(
        fontName = preset.name.takeUnless { it == "Default" },
        fontPath = preset.fontPath
    )
}

internal fun SharedPdfTextStyleConfig.displayFontName(): String {
    return fontName
        ?: fontPath?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "Default"
}

internal fun sharedPdfFontFamily(nameOrPath: String?): FontFamily? {
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

internal fun Int.isTransparentArgb(): Boolean {
    return (this ushr 24) == 0
}

internal fun PdfPageBounds.topLeft(canvasSize: IntSize): Offset {
    return Offset(left * canvasSize.width, top * canvasSize.height)
}

internal fun PdfPageBounds.size(canvasSize: IntSize): Size {
    return Size((right - left) * canvasSize.width, (bottom - top) * canvasSize.height)
}

internal fun Color.darker(factor: Float = 0.7f): Color {
    return Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha
    )
}

internal fun Color.lighter(factor: Float = 0.3f): Color {
    return Color(
        red = red + (1 - red) * factor,
        green = green + (1 - green) * factor,
        blue = blue + (1 - blue) * factor,
        alpha = alpha
    )
}
