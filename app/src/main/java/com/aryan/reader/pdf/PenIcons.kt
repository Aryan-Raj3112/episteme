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

import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.shared.ui.SharedPdfInkPreviewCommand
import com.aryan.reader.shared.ui.SharedPdfPenIconInkHeadroomFraction
import com.aryan.reader.shared.ui.SharedPdfPenIconInkStartXFraction
import com.aryan.reader.shared.ui.applySharedPdfInkPreview
import com.aryan.reader.shared.ui.darker
import com.aryan.reader.shared.ui.drawBrushHead
import com.aryan.reader.shared.ui.drawFountainNib
import com.aryan.reader.shared.ui.drawHighlighterChiselParts
import com.aryan.reader.shared.ui.drawHighlighterRoundParts
import com.aryan.reader.shared.ui.drawMarkerHead
import com.aryan.reader.shared.ui.drawMatteCylinder
import com.aryan.reader.shared.ui.drawPencilHead
import com.aryan.reader.shared.ui.lighter
import com.aryan.reader.shared.ui.sharedPdfInkPreviewCommands
import android.graphics.Paint as NativePaint

private val BODY_COLOR = Color(0xFF454545)
private val SILVER_NIB_COLOR = Color(0xFFCFD8DC)

@Composable
fun PenIcon(
    color: Color,
    modifier: Modifier = Modifier,
    type: PenType = PenType.FOUNTAIN_PEN,
    isSelected: Boolean = false,
    strokeWidth: Float = 0.005f,
    forcedInkType: InkType? = null,
    inkColor: Color? = null,
    isSnappingEnabled: Boolean = false
) {
    val animatedColor by animateColorAsState(targetValue = color, label = "color")

    val targetInkColor = inkColor ?: color
    val animatedInkColor by animateColorAsState(targetValue = targetInkColor, label = "ink_color")

    val inkProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "ink_progress"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val penWidth = w * 0.65f
        val startX = (w - penWidth) / 2f

        // Reserve the top of the canvas for the ink flourish so the selection
        // animation is not clipped; the pen itself is drawn in the area below.
        // Mirrors SharedPdfPenIcon's headroom so both platforms match.
        val penTop = h * SharedPdfPenIconInkHeadroomFraction
        val penHeight = h - penTop
        val tipHeight = penHeight * 0.45f
        val collarHeight = penHeight * 0.15f
        val bodyHeight = penHeight * 0.35f

        val tipRect = Rect(offset = Offset(startX, penTop), size = Size(penWidth, tipHeight))
        val collarRect = Rect(offset = Offset(startX, penTop + tipHeight), size = Size(penWidth, collarHeight))
        val bodyRect = Rect(offset = Offset(startX, penTop + tipHeight + collarHeight), size = Size(penWidth, bodyHeight))

        drawMatteCylinder(BODY_COLOR, bodyRect)

        when (type) {
            PenType.FOUNTAIN_PEN -> {
                drawMatteCylinder(animatedColor, collarRect)
                drawFountainNib(SILVER_NIB_COLOR, animatedColor, tipRect)
            }
            PenType.PENCIL -> {
                drawMatteCylinder(animatedColor, collarRect)
                drawPencilHead(animatedColor, tipRect)
            }
            PenType.MARKER -> {
                drawMatteCylinder(animatedColor, collarRect)
                drawMarkerHead(animatedColor, tipRect)
            }
            PenType.BRUSH -> {
                drawMatteCylinder(animatedColor, collarRect)
                drawBrushHead(
                    animatedColor,
                    Rect(offset = tipRect.topLeft, size = Size(tipRect.width, tipHeight + collarHeight))
                )
            }
            PenType.HIGHLIGHTER -> {
                drawHighlighterChiselParts(animatedColor, collarRect, tipRect)
            }
            PenType.HIGHLIGHTER_ROUND -> {
                drawHighlighterRoundParts(animatedColor, collarRect, tipRect)
            }
        }

        if (inkProgress > 0.01f) {
            val tipX = size.width * SharedPdfPenIconInkStartXFraction
            val tipY = when (type) {
                PenType.HIGHLIGHTER -> penTop
                PenType.HIGHLIGHTER_ROUND -> penTop + tipHeight * 0.15f
                else -> penTop
            }

            drawInkSquiggle(
                type = type,
                forcedInkType = forcedInkType,
                color = animatedInkColor,
                progress = inkProgress,
                startPoint = Offset(tipX, tipY),
                baseStrokeWidth = strokeWidth,
                isStraight = isSnappingEnabled
            )
        }
    }
}

// Helpers
/**
 * Ink-preview flourish commands for the Android tool-settings icon.
 * Highlighters reuse the shared metrics; pens draw the same full-size swirl
 * as shared (Android's signature sweep, expressed in canvas fractions) so the
 * animation stays inside the canvas at any size and both platforms match.
 */
fun pdfInkPreviewCommands(isHighlighter: Boolean, straight: Boolean): List<SharedPdfInkPreviewCommand> {
    if (isHighlighter) {
        return sharedPdfInkPreviewCommands(isHighlighter = true, straight = straight)
    }
    return listOf(
        SharedPdfInkPreviewCommand.MoveTo(0f, 0f),
        SharedPdfInkPreviewCommand.CubicTo(0.225f, -0.133f, -0.225f, -0.29f, -0.097f, -0.15f),
        SharedPdfInkPreviewCommand.CubicTo(-0.032f, -0.033f, 0.29f, -0.083f, 0.40f, -0.183f),
    )
}

private fun DrawScope.drawInkSquiggle(
    type: PenType,
    forcedInkType: InkType?,
    color: Color,
    progress: Float,
    startPoint: Offset,
    baseStrokeWidth: Float,
    isStraight: Boolean = false
) {
    val x = startPoint.x
    val y = startPoint.y - 2f
    val canvasSize = size
    val isHighlighter = type == PenType.HIGHLIGHTER || type == PenType.HIGHLIGHTER_ROUND
    val path = Path().apply {
        moveTo(x, y)
        // Skip the initial MoveTo: the path already starts at the ink start point.
        applySharedPdfInkPreview(
            commands = pdfInkPreviewCommands(isHighlighter = isHighlighter, straight = isStraight).drop(1),
            start = Offset(x, y),
            size = canvasSize,
        )
    }

    val inkType = forcedInkType ?: when(type) {
        PenType.FOUNTAIN_PEN -> InkType.FOUNTAIN_PEN
        PenType.PENCIL -> InkType.PENCIL
        PenType.MARKER -> InkType.PEN
        PenType.HIGHLIGHTER, PenType.HIGHLIGHTER_ROUND -> InkType.HIGHLIGHTER
        else -> InkType.PEN
    }

    val pathMeasure = PathMeasure()
    pathMeasure.setPath(path, false)
    val length = pathMeasure.length
    val targetLength = length * progress
    val pointCount = (targetLength / 2f).toInt().coerceAtLeast(2)
    val points = ArrayList<PdfPoint>(pointCount)
    var currentTime = 0L

    for (i in 0 until pointCount) {
        val distance = (i.toFloat() / pointCount) * targetLength
        val timeDelta = 15L
        currentTime += timeDelta

        pathMeasure.getPosition(distance).let { offset ->
            points.add(PdfPoint(offset.x, offset.y, timestamp = currentTime))
        }
    }

    if (points.isEmpty()) return

    val simulationScale = 1000f
    val strokeMultiplier = if (type == PenType.HIGHLIGHTER || type == PenType.HIGHLIGHTER_ROUND) 1.0f else 1f
    val scaledStrokeWidth = baseStrokeWidth * simulationScale * strokeMultiplier

    val annotation = PdfAnnotation(
        type = AnnotationType.INK,
        inkType = inkType,
        pageIndex = 0,
        points = points,
        color = color,
        strokeWidth = scaledStrokeWidth
    )

    val renderData = PdfAnnotationRenderHelper.createRenderData(
        annot = annotation,
        widthPx = 1,
        heightPx = 1
    )

    if (renderData != null) {
        when (renderData) {
            is AnnotationRenderData.Standard -> {
                val effectiveBlendMode = if (type == PenType.HIGHLIGHTER || type == PenType.HIGHLIGHTER_ROUND) {
                    BlendMode.SrcOver
                } else if (renderData.blendMode == BlendMode.Darken) {
                    BlendMode.SrcOver
                } else {
                    renderData.blendMode
                }

                // Handle caps for specific highlighters
                val strokeCap = when (type) {
                    PenType.HIGHLIGHTER -> StrokeCap.Square
                    PenType.HIGHLIGHTER_ROUND -> StrokeCap.Round
                    else -> renderData.cap
                }

                drawPath(
                    path = renderData.path,
                    color = renderData.color,
                    style = ComposeStroke(
                        width = renderData.strokeWidth,
                        cap = strokeCap,
                        join = StrokeJoin.Round
                    ),
                    blendMode = effectiveBlendMode
                )
            }
            is AnnotationRenderData.Fountain -> {
                drawPath(
                    path = renderData.path,
                    color = renderData.color,
                    style = androidx.compose.ui.graphics.drawscope.Fill
                )
            }
            is AnnotationRenderData.Pencil -> {
                val texture = PdfTextureGenerator.getNoiseTexture()
                drawIntoCanvas { canvas ->
                    val paint = NativePaint().apply {
                        isAntiAlias = true
                        style = NativePaint.Style.STROKE
                        strokeCap = NativePaint.Cap.ROUND
                        strokeJoin = NativePaint.Join.ROUND
                        strokeWidth = renderData.strokeWidth
                        shader = BitmapShader(
                            texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
                        )
                        colorFilter = PorterDuffColorFilter(
                            renderData.color.toArgb(), PorterDuff.Mode.SRC_IN
                        )
                        alpha = (renderData.color.alpha * renderData.velocityAlpha * 255).toInt()
                    }
                    canvas.nativeCanvas.drawPath(renderData.path, paint)
                }
            }
        }
    }
}

enum class PenType {
    FOUNTAIN_PEN, PENCIL, MARKER, BRUSH, HIGHLIGHTER, HIGHLIGHTER_ROUND
}
