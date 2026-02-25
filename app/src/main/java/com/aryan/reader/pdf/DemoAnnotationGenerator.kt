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

import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.PathParser
import com.aryan.reader.pdf.data.PdfAnnotation

object DemoAnnotationGenerator {

    // --- SVG Configuration ---
    private const val SVG_WIDTH = 800f

    // Extracted from your Figma SVG
    private val DECORATIVE_DOTS = listOf(
        DotData(120f, 90f, 5f, Color(0xFFF59E0B), 0.7f),
        DotData(680f, 210f, 6f, Color(0xFFEC4899), 0.7f),
        DotData(700f, 110f, 4f, Color(0xFF8B5CF6), 0.7f),
        DotData(150f, 230f, 5f, Color(0xFF10B981), 0.6f),
        DotData(650f, 80f, 4f, Color(0xFFF59E0B), 0.6f),
        DotData(90f, 180f, 3f, Color(0xFFEC4899), 0.5f),
        DotData(720f, 170f, 5f, Color(0xFF8B5CF6), 0.6f)
    )

    private val TEXT_STROKES_DATA = listOf(
        // T
        "M 80 115 L 140 115 M 110 115 L 110 175 Q 110 185 115 185",
        // r
        "M 150 145 L 150 180 M 150 155 Q 155 145 165 145 Q 172 145 175 150",
        // y (Improvised: Smoother curves and proper descender loop)
        "M 182 148 Q 188 165 195 175 M 208 148 Q 200 165 195 175 L 192 195 Q 188 215 175 212 Q 165 208 172 198",
        // " E" (Space included in coordinates)
        "M 240 110 L 240 180 M 240 110 L 285 110 M 240 145 L 275 145 M 240 180 L 285 180",
        // p
        "M 305 145 L 305 215 M 305 158 Q 305 145 320 145 Q 340 145 345 160 Q 348 170 345 180 Q 340 195 320 195 Q 305 195 305 182",
        // i
        "M 365 145 L 365 180 M 365 130 L 365 132",
        // s
        "M 428 148 Q 418 143 408 145 Q 398 147 395 155 Q 393 162 400 165 Q 410 170 420 168 Q 428 166 430 172 Q 432 180 422 183 Q 412 186 402 182",
        // t
        "M 445 125 L 445 175 Q 445 185 455 185 Q 465 185 470 180 M 435 145 L 460 145",
        // e (Fixed: Standard cursive loop instead of inverted shape)
        "M 495 165 L 522 165 Q 522 145 508 145 Q 488 145 492 170 Q 495 190 525 185",
        // m
        "M 545 145 L 545 180 M 545 155 Q 545 145 555 145 Q 565 145 565 155 L 565 180 M 565 155 Q 565 145 575 145 Q 585 145 585 155 L 585 180",
        // e (Fixed: Shifted +110 relative to previous 'e')
        "M 605 165 L 632 165 Q 632 145 618 145 Q 598 145 602 170 Q 605 190 635 185",
        // !
        "M 660 125 L 660 165 M 660 178 L 660 182"
    )

    private const val UNDERLINE_DATA = "M 180 200 Q 400 220 620 200"

    fun generateDemoAnnotations(pageIndex: Int): List<PdfAnnotation> {
        val annotations = mutableListOf<PdfAnnotation>()

        // --- Layout Calculation ---
        // We want the SVG to occupy 80% of the page width, centered.
        // PDF coordinates are 0..1.
        val targetWidthPercent = 0.8f
        // SVG aspect ratio 300 / 800 = 0.375

        // Calculate scale factor relative to normalized page coordinates
        val scaleX = targetWidthPercent / SVG_WIDTH
        val scaleY = scaleX // Keep uniform scale in abstract space

        // Center offsets (0.5 is middle of page)
        val startX = (1f - targetWidthPercent) / 2f
        val startY = 0.4f // Position slightly above center vertically

        var currentTime = System.currentTimeMillis()

        // Helper to transform SVG points to PDF Page Points
        fun transformPoint(x: Float, y: Float): PdfPoint {
            val pdfX = startX + (x * scaleX)
            val pdfY = startY + (y * scaleY)
            return PdfPoint(pdfX, pdfY, currentTime)
        }

        // 1. Render Decorative Dots
        DECORATIVE_DOTS.forEach { dot ->
            val pdfPoint = transformPoint(dot.cx, dot.cy)

            // To make a "Dot" with the pen, we need at least 2 points very close together
            // or a single point might not render depending on the implementation.
            val points = listOf(
                pdfPoint,
                pdfPoint.copy(x = pdfPoint.x + 0.0001f, timestamp = currentTime + 10)
            )

            // Convert SVG radius to stroke width
            val relativeThickness = (dot.r / SVG_WIDTH) * 2.5f

            annotations.add(
                PdfAnnotation(
                    type = AnnotationType.INK,
                    inkType = InkType.PEN, // Standard pen for dots
                    pageIndex = pageIndex,
                    points = points,
                    color = dot.color.copy(alpha = dot.alpha),
                    strokeWidth = relativeThickness
                )
            )
            currentTime += 50
        }

        // 2. Render Text ("Try Episteme!")
        val textPaths = splitSvgPaths(TEXT_STROKES_DATA)
        textPaths.forEach { pathString ->
            val path = PathParser.createPathFromPathData(pathString)
            val flattenedPoints = flattenPath(path)

            if (flattenedPoints.isNotEmpty()) {
                val pdfPoints = flattenedPoints.mapIndexed { _, p ->
                    // Increment time to simulate drawing speed for Fountain Pen physics
                    currentTime += 8
                    transformPoint(p.x, p.y).copy(timestamp = currentTime)
                }

                annotations.add(
                    PdfAnnotation(
                        type = AnnotationType.INK,
                        inkType = InkType.FOUNTAIN_PEN, // Handwriting looks best with this
                        pageIndex = pageIndex,
                        points = pdfPoints,
                        color = Color(0xFF418377), // Updated Green
                        strokeWidth = 0.004f // Fine tip
                    )
                )
                currentTime += 150 // Pen lift delay
            }
        }

        // 3. Render Underline
        val underlinePath = PathParser.createPathFromPathData(UNDERLINE_DATA)
        val underlinePointsRaw = flattenPath(underlinePath)
        val underlinePdfPoints = underlinePointsRaw.map { p ->
            currentTime += 5
            transformPoint(p.x, p.y).copy(timestamp = currentTime)
        }

        annotations.add(
            PdfAnnotation(
                type = AnnotationType.INK,
                inkType = InkType.PEN, // Consistent width for underline
                pageIndex = pageIndex,
                points = underlinePdfPoints,
                color = Color(0xFFEC4899).copy(alpha = 0.6f), // Pink
                strokeWidth = 0.005f
            )
        )

        return annotations
    }

    // --- Helpers ---

    private data class DotData(val cx: Float, val cy: Float, val r: Float, val color: Color, val alpha: Float)
    private data class PointF(val x: Float, val y: Float)

    /**
     * Android's Path doesn't give us points directly. We use approximate().
     */
    private fun flattenPath(path: Path): List<PointF> {
        // Approximate the path with error tolerance 0.5 (pixels in SVG space)
        val approximation = path.approximate(0.5f)
        val points = mutableListOf<PointF>()

        // approximation array format: [t0, x0, y0, t1, x1, y1, ...]
        var i = 0
        while (i < approximation.size) {
            val x = approximation[i + 1]
            val y = approximation[i + 2]
            points.add(PointF(x, y))
            i += 3
        }
        return points
    }

    /**
     * The SVG string might contain separate letters, but even within a letter
     * (like 'i' or 't') there might be a Move (M) command.
     * We must split by 'M' to ensure we don't draw connecting lines where the pen should lift.
     */
    private fun splitSvgPaths(@Suppress("SameParameterValue") rawPaths: List<String>): List<String> {
        val result = mutableListOf<String>()

        rawPaths.forEach { fullPathString ->
            // Clean up and standardize
            val cleanStr = fullPathString.trim()

            // Split by "M" (Move command).
            val parts = cleanStr.split("M")

            parts.forEach { part ->
                if (part.isNotBlank()) {
                    // Re-prepend M because split removed it
                    result.add("M ${part.trim()}")
                }
            }
        }
        return result
    }
}