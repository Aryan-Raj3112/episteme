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
@file:Suppress("SameParameterValue")

package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Path
import android.util.Xml
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.PathParser
import com.aryan.reader.pdf.data.PdfAnnotation
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.util.Stack
import kotlin.math.hypot
import androidx.core.graphics.toColorInt

object SvgToAnnotationConverter {

    private data class SvgStyle(
        val strokeColor: Color? = null,
        val strokeWidth: Float? = null,
        val fill: Color? = null,
        val opacity: Float = 1.0f
    )

    fun importSvgFromAssets(
        context: Context,
        fileName: String,
        pageIndex: Int,
    ): List<PdfAnnotation> {
        val annotations = mutableListOf<PdfAnnotation>()
        var currentTime = System.currentTimeMillis()

        try {
            context.assets.open(fileName).use { inputStream ->
                val parser = Xml.newPullParser()
                parser.setInput(inputStream, null)

                var eventType = parser.eventType

                var viewBoxWidth = 800f
                @Suppress("VariableNeverRead") var viewBoxHeight = 300f

                val styleStack = Stack<SvgStyle>()
                styleStack.push(SvgStyle(strokeColor = Color.Black, strokeWidth = 2f))

                val targetWidthPercent = 0.8f
                val startX = (1f - targetWidthPercent) / 2f
                val startY = 0.3f

                var scale = 1f

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tagName = parser.name

                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tagName.equals("svg", ignoreCase = true)) {
                                val viewBox = parser.getAttributeValue(null, "viewBox")
                                if (viewBox != null) {
                                    val parts = viewBox.split(" ").mapNotNull { it.toFloatOrNull() }
                                    if (parts.size == 4) {
                                        viewBoxWidth = parts[2]
                                        viewBoxHeight = parts[3]
                                    }
                                }
                                scale = targetWidthPercent / viewBoxWidth
                            }

                            val rawStroke = parser.getAttributeValue(null, "stroke")
                            val rawStrokeWidth = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull()
                            val rawFill = parser.getAttributeValue(null, "fill")
                            val rawOpacity = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1.0f

                            val strokeColor = parseSvgColor(rawStroke)
                            val fillColor = parseSvgColor(rawFill)

                            val parentStyle = styleStack.peek()
                            val currentStyle = SvgStyle(
                                strokeColor = strokeColor ?: parentStyle.strokeColor,
                                strokeWidth = rawStrokeWidth ?: parentStyle.strokeWidth,
                                fill = fillColor ?: parentStyle.fill,
                                opacity = rawOpacity * parentStyle.opacity
                            )

                            if (tagName.equals("g", ignoreCase = true)) {
                                styleStack.push(currentStyle)
                            }

                            if (tagName.equals("circle", ignoreCase = true)) {
                                val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
                                val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
                                val r = parser.getAttributeValue(null, "r")?.toFloatOrNull() ?: 0f

                                if (r > 0) {
                                    val finalColor = (currentStyle.fill ?: currentStyle.strokeColor ?: Color.Black)
                                        .copy(alpha = currentStyle.opacity)

                                    val pdfDiameter = (2 * r) * scale

                                    val pdfCx = startX + (cx * scale)
                                    val pdfCy = startY + (cy * scale)

                                    val points = listOf(
                                        PdfPoint(pdfCx, pdfCy, currentTime),
                                        PdfPoint(pdfCx + 0.00001f, pdfCy, currentTime + 1)
                                    )

                                    annotations.add(
                                        PdfAnnotation(
                                            type = AnnotationType.INK,
                                            inkType = InkType.PEN,
                                            pageIndex = pageIndex,
                                            points = points,
                                            color = finalColor,
                                            strokeWidth = pdfDiameter
                                        )
                                    )
                                    currentTime += 5
                                }
                            }

                            // --- PATH HANDLING ---
                            if (tagName.equals("path", ignoreCase = true)) {
                                val d = parser.getAttributeValue(null, "d")
                                if (!d.isNullOrBlank()) {
                                    val subPathDataStrings = d.split(Regex("(?=[Mm])")).filter { it.isNotBlank() }

                                    subPathDataStrings.forEach { subPathData ->
                                        try {
                                            val finalColor = (currentStyle.strokeColor ?: currentStyle.fill ?: Color.Black)
                                                .copy(alpha = currentStyle.opacity)

                                            val svgStrokeWidth = currentStyle.strokeWidth ?: 1f
                                            val pdfStrokeWidth = svgStrokeWidth * scale

                                            val path = PathParser.createPathFromPathData(subPathData)

                                            val points = flattenPathToPdfPoints(
                                                path = path,
                                                scale = scale,
                                                offsetX = startX,
                                                offsetY = startY,
                                                baseTime = currentTime
                                            )

                                            if (points.isNotEmpty()) {
                                                annotations.add(
                                                    PdfAnnotation(
                                                        type = AnnotationType.INK,
                                                        inkType = InkType.PEN,
                                                        pageIndex = pageIndex,
                                                        points = points,
                                                        color = finalColor,
                                                        strokeWidth = pdfStrokeWidth
                                                    )
                                                )
                                                currentTime += points.size
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to parse sub-path data")
                                        }
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            if (tagName.equals("g", ignoreCase = true)) {
                                if (styleStack.size > 1) {
                                    styleStack.pop()
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error importing SVG")
        }

        return annotations
    }

    private fun parseSvgColor(hexOrName: String?): Color? {
        if (hexOrName.isNullOrBlank() || hexOrName.equals("none", ignoreCase = true)) return null
        return try {
            Color(hexOrName.toColorInt())
        } catch (_: Exception) {
            null
        }
    }

    private fun flattenPathToPdfPoints(
        path: Path,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        baseTime: Long
    ): List<PdfPoint> {
        val coords = path.approximate(0.5f)
        val rawPoints = mutableListOf<PdfPoint>()
        var timeOffset = 0L

        var i = 0
        while (i < coords.size) {
            val x = coords[i + 1]
            val y = coords[i + 2]

            val pdfX = offsetX + (x * scale)
            val pdfY = offsetY + (y * scale)

            rawPoints.add(PdfPoint(pdfX, pdfY, baseTime + timeOffset))
            timeOffset++
            i += 3
        }

        return densifyPoints(rawPoints, threshold = 0.001f)
    }

    private fun densifyPoints(points: List<PdfPoint>, threshold: Float): List<PdfPoint> {
        if (points.size < 2) return points

        val result = mutableListOf<PdfPoint>()
        result.add(points[0])

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val dist = hypot(p2.x - p1.x, p2.y - p1.y)

            if (dist > threshold) {
                val steps = (dist / threshold).toInt()
                for (j in 1..steps) {
                    val fraction = j.toFloat() / (steps + 1)
                    val newX = p1.x + (p2.x - p1.x) * fraction
                    val newY = p1.y + (p2.y - p1.y) * fraction
                    val newTime = p1.timestamp + ((p2.timestamp - p1.timestamp) * fraction).toLong()

                    result.add(PdfPoint(newX, newY, newTime))
                }
            }
            result.add(p2)
        }
        return result
    }
}