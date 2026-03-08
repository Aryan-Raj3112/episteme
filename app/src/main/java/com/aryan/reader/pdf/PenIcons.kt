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

        val tipHeight = h * 0.45f
        val collarHeight = h * 0.15f
        val bodyHeight = h * 0.35f
        val topPadding = h * 0.05f

        val tipRect = Rect(offset = Offset(startX, topPadding), size = Size(penWidth, tipHeight))
        val collarRect = Rect(offset = Offset(startX, topPadding + tipHeight), size = Size(penWidth, collarHeight))
        val bodyRect = Rect(offset = Offset(startX, topPadding + tipHeight + collarHeight), size = Size(penWidth, bodyHeight))

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
            val tipX = size.width / 2f
            val tipY = when (type) {
                PenType.HIGHLIGHTER -> topPadding
                PenType.HIGHLIGHTER_ROUND -> topPadding + tipHeight * 0.15f
                else -> topPadding
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
private fun DrawScope.drawMatteCylinder(color: Color, rect: Rect) {
    val gradient = Brush.horizontalGradient(
        0.0f to color.darker(0.6f),
        0.3f to color.lighter(0.1f),
        0.5f to color,
        0.85f to color.darker(0.5f),
        1.0f to color.darker(0.7f),
        startX = rect.left,
        endX = rect.right
    )
    drawRect(brush = gradient, topLeft = rect.topLeft, size = rect.size)
}

private fun DrawScope.drawFountainNib(metalColor: Color, inkColor: Color, rect: Rect) {
    val cx = rect.left + rect.width / 2
    val path = Path().apply {
        moveTo(rect.left + rect.width * 0.15f, rect.bottom)
        lineTo(rect.right - rect.width * 0.15f, rect.bottom)
        cubicTo(
            rect.right - rect.width * 0.1f, rect.bottom - rect.height * 0.6f,
            rect.right, rect.top + rect.height * 0.2f,
            cx, rect.top
        )
        cubicTo(
            rect.left, rect.top + rect.height * 0.2f,
            rect.left + rect.width * 0.1f, rect.bottom - rect.height * 0.6f,
            rect.left + rect.width * 0.15f, rect.bottom
        )
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

    drawCircle(
        color = Color.Black.copy(alpha=0.7f),
        radius = rect.width * 0.06f,
        center = Offset(cx, rect.bottom - rect.height * 0.5f)
    )

    drawLine(
        color = Color.Black.copy(alpha=0.6f),
        start = Offset(cx, rect.top),
        end = Offset(cx, rect.bottom - rect.height * 0.5f),
        strokeWidth = 2f
    )

    drawCircle(
        color = inkColor.copy(alpha = 0.5f),
        radius = rect.width * 0.04f,
        center = Offset(cx, rect.bottom - rect.height * 0.5f)
    )
}

private fun DrawScope.drawMarkerHead(inkColor: Color, rect: Rect) {
    val cx = rect.left + rect.width / 2
    val coneHeight = rect.height * 0.8f
    val conePath = Path().apply {
        moveTo(rect.left, rect.bottom)
        lineTo(rect.right, rect.bottom)
        lineTo(cx + rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        lineTo(cx - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        close()
    }
    val plasticColor = Color(0xFF616161)
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
        moveTo(cx - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        lineTo(cx + rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        quadraticTo(cx, rect.top, cx, rect.top) // Round tip
        lineTo(cx - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
    }
    drawPath(path = tipPath, color = inkColor)
}

private fun DrawScope.drawPencilHead(inkColor: Color, rect: Rect) {
    val cx = rect.left + rect.width / 2
    val woodColor = Color(0xFFFFCC80)
    val woodPath = Path().apply {
        moveTo(rect.left, rect.bottom)
        val scallops = 3
        val step = rect.width / scallops
        for (i in 0 until scallops) {
            quadraticTo(
                rect.left + (i * step) + (step / 2), rect.bottom - (rect.width * 0.1f),
                rect.left + ((i + 1) * step), rect.bottom
            )
        }
        lineTo(cx + rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(cx - rect.width * 0.12f, rect.top + rect.height * 0.25f)
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
        moveTo(cx - rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(cx + rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(cx, rect.top)
        close()
    }
    drawPath(path = leadPath, color = inkColor)
}

private fun DrawScope.drawBrushHead(inkColor: Color, rect: Rect) {
    val cx = rect.left + rect.width / 2
    val brushPath = Path().apply {
        moveTo(rect.left + rect.width * 0.15f, rect.bottom)
        lineTo(rect.right - rect.width * 0.15f, rect.bottom)
        quadraticTo(rect.right, rect.bottom - rect.height * 0.4f, cx, rect.top)
        quadraticTo(rect.left, rect.bottom - rect.height * 0.4f, rect.left + rect.width * 0.15f, rect.bottom)
        close()
    }
    val gradient = Brush.radialGradient(
        colors = listOf(inkColor.lighter(0.4f), inkColor.darker(0.6f)),
        center = Offset(cx, rect.top + rect.height * 0.3f),
        radius = rect.height
    )
    drawPath(path = brushPath, brush = gradient)
}

private fun DrawScope.drawHighlighterChiselParts(color: Color, collarRect: Rect, tipRect: Rect) {
    drawMatteCylinder(color, collarRect)
    val neckHeight = tipRect.height * 0.65f
    val inkTipHeight = tipRect.height - neckHeight

    val neckBottomY = tipRect.bottom
    val neckTopY = tipRect.bottom - neckHeight

    val cx = tipRect.center.x
    val neckTopHalfWidth = tipRect.width * 0.25f

    val neckPath = Path().apply {
        moveTo(tipRect.left, neckBottomY)
        lineTo(tipRect.right, neckBottomY)
        lineTo(cx + neckTopHalfWidth, neckTopY)
        lineTo(cx - neckTopHalfWidth, neckTopY)
        close()
    }

    val neckGradient = Brush.horizontalGradient(
        0.0f to BODY_COLOR.darker(0.6f),
        0.3f to BODY_COLOR.lighter(0.1f),
        0.5f to BODY_COLOR,
        0.85f to BODY_COLOR.darker(0.5f),
        1.0f to BODY_COLOR.darker(0.7f),
        startX = tipRect.left,
        endX = tipRect.right
    )
    drawPath(path = neckPath, brush = neckGradient)

    val tipBottomY = neckTopY
    val tipTopY = tipRect.top
    val slantDrop = inkTipHeight * 0.4f

    val tipPath = Path().apply {
        moveTo(cx - neckTopHalfWidth, tipBottomY)
        lineTo(cx + neckTopHalfWidth, tipBottomY)
        lineTo(cx + neckTopHalfWidth, tipTopY + slantDrop)
        lineTo(cx - neckTopHalfWidth, tipTopY)
        close()
    }

    drawPath(
        path = tipPath,
        brush = Brush.horizontalGradient(
            0.0f to color.darker(0.8f),
            0.5f to color,
            1.0f to color.darker(0.8f),
            startX = cx - neckTopHalfWidth,
            endX = cx + neckTopHalfWidth
        )
    )

    val facePath = Path().apply {
        moveTo(cx - neckTopHalfWidth, tipTopY)
        lineTo(cx + neckTopHalfWidth, tipTopY + slantDrop)
        quadraticTo(cx, tipTopY + slantDrop * 0.5f, cx - neckTopHalfWidth, tipTopY)
        close()
    }
    drawPath(path = facePath, color = color.lighter(0.2f))
}

private fun DrawScope.drawHighlighterRoundParts(color: Color, collarRect: Rect, tipRect: Rect) {
    drawMatteCylinder(color, collarRect)

    val neckHeight = tipRect.height * 0.65f
    val neckBottomY = tipRect.bottom
    val neckTopY = tipRect.bottom - neckHeight
    val cx = tipRect.center.x
    val neckTopHalfWidth = tipRect.width * 0.25f
    val neckPath = Path().apply {
        moveTo(tipRect.left, neckBottomY)
        lineTo(tipRect.right, neckBottomY)
        lineTo(cx + neckTopHalfWidth, neckTopY)
        lineTo(cx - neckTopHalfWidth, neckTopY)
        close()
    }

    val neckGradient = Brush.horizontalGradient(
        0.0f to BODY_COLOR.darker(0.6f),
        0.3f to BODY_COLOR.lighter(0.1f),
        0.5f to BODY_COLOR,
        0.85f to BODY_COLOR.darker(0.5f),
        1.0f to BODY_COLOR.darker(0.7f),
        startX = tipRect.left,
        endX = tipRect.right
    )
    drawPath(path = neckPath, brush = neckGradient)

    neckTopHalfWidth * 2
    val tipHeight = tipRect.height - neckHeight

    val domeRect = Rect(
        left = cx - neckTopHalfWidth,
        top = neckTopY - tipHeight,
        right = cx + neckTopHalfWidth,
        bottom = neckTopY
    )

    val domePath = Path().apply {
        moveTo(domeRect.left, domeRect.bottom)
        lineTo(domeRect.right, domeRect.bottom)
        arcTo(
            rect = domeRect,
            startAngleDegrees = 0f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false
        )
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
    val path = Path().apply {
        moveTo(x, y)

        if (type == PenType.HIGHLIGHTER || type == PenType.HIGHLIGHTER_ROUND) {
            val waveWidth = 70f

            if (isStraight) {
                lineTo(x + waveWidth, y)
            } else {
                val amplitude = 20f
                cubicTo(
                    x + waveWidth * 0.35f, y - amplitude,
                    x + waveWidth * 0.65f, y + amplitude,
                    x + waveWidth, y
                )
            }
        } else {
            cubicTo(
                x + 35f, y - 40f,
                x - 35f, y - 90f,
                x - 15f, y - 45f
            )
            cubicTo(
                x - 5f, y - 10f,
                x + 50f, y - 25f,
                x + 70f, y - 55f
            )
        }
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

fun Color.darker(factor: Float = 0.7f): Color {
    return Color(
        red = this.red * factor,
        green = this.green * factor,
        blue = this.blue * factor,
        alpha = this.alpha
    )
}

fun Color.lighter(factor: Float = 0.3f): Color {
    val r = this.red + (1 - this.red) * factor
    val g = this.green + (1 - this.green) * factor
    val b = this.blue + (1 - this.blue) * factor
    return Color(r, g, b, this.alpha)
}