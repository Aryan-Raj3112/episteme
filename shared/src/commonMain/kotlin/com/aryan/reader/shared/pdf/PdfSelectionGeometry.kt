package com.aryan.reader.shared.pdf

import kotlin.math.abs

data class PdfNormalizedPoint(
    val x: Float,
    val y: Float
)

data class PdfTextCharBounds(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val hasBounds: Boolean
        get() = right > left && bottom > top
}

object PdfSelectionGeometry {
    private const val DefaultMergedLineTolerance = 0.006f
    private const val DefaultCharLineTolerance = 0.012f
    private const val MinLineTolerance = 0.002f

    fun normalizedPoint(
        pointX: Float,
        pointY: Float,
        viewportWidth: Int,
        viewportHeight: Int
    ): PdfNormalizedPoint? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        return PdfNormalizedPoint(
            x = (pointX / viewportWidth).coerceIn(0f, 1f),
            y = (pointY / viewportHeight).coerceIn(0f, 1f)
        )
    }

    fun mergeBoundsByLine(
        bounds: List<PdfPageBounds>,
        lineTolerance: Float = DefaultMergedLineTolerance
    ): List<PdfPageBounds> {
        if (bounds.isEmpty()) return emptyList()
        val lines = mutableListOf<MutableList<PdfPageBounds>>()
        bounds.sortedWith(compareBy<PdfPageBounds> { it.top }.thenBy { it.left }).forEach { boundsForChar ->
            val line = lines.firstOrNull { existing ->
                existing.any { it.isSameVisualLineAs(boundsForChar, lineTolerance) }
            }
            if (line == null) {
                lines += mutableListOf(boundsForChar)
            } else {
                line += boundsForChar
            }
        }
        return lines.map { it.toMergedBounds() }
    }

    fun lineBoundsForChars(
        chars: List<PdfTextCharBounds>,
        lineTolerance: Float = DefaultCharLineTolerance
    ): List<PdfPageBounds> {
        return mergeBoundsByLine(
            bounds = chars.groupByLine(lineTolerance).map { it.toCharLineBounds() }
        )
    }

    fun nearestCharOnLine(
        chars: List<PdfTextCharBounds>,
        point: PdfNormalizedPoint,
        lineTolerance: Float = DefaultCharLineTolerance
    ): PdfTextCharBounds? {
        val lines = chars.groupByLine(lineTolerance)
        val matchingLines = lines.filter { line ->
            val top = line.minOf { it.top }
            val bottom = line.maxOf { it.bottom }
            val averageHeight = line.map { it.bottom - it.top }.average().toFloat()
            val verticalPadding = maxOf(averageHeight * 0.45f, MinLineTolerance)
            point.y in (top - verticalPadding)..(bottom + verticalPadding)
        }
        val line = matchingLines.minWithOrNull(
            compareBy<List<PdfTextCharBounds>>(
                { lineVerticalDistance(point.y, it) },
                { lineHorizontalDistance(point.x, it) }
            )
        ) ?: return null

        return line.minByOrNull { char ->
            horizontalDistance(point.x, char)
        }
    }

    fun nearestBoundsIndex(
        bounds: List<PdfPageBounds?>,
        pointX: Float,
        pointY: Float
    ): Int {
        if (bounds.isEmpty()) return -1
        val containmentX = pointX.toInt().toFloat()
        val containmentY = pointY.toInt().toFloat()
        val containingIndex = bounds.indexOfFirst { box ->
            box != null && containmentX >= box.left && containmentX < box.right &&
                containmentY >= box.top && containmentY < box.bottom
        }
        if (containingIndex != -1) return containingIndex

        var closestIndex = -1
        var minimumDistanceSquared = Float.MAX_VALUE
        bounds.forEachIndexed { index, box ->
            if (box != null) {
                val deltaX = pointX - (box.left + box.right) / 2f
                val deltaY = pointY - (box.top + box.bottom) / 2f
                val distanceSquared = deltaX * deltaX + deltaY * deltaY
                if (distanceSquared < minimumDistanceSquared) {
                    minimumDistanceSquared = distanceSquared
                    closestIndex = index
                }
            }
        }
        return closestIndex
    }

    private fun List<PdfTextCharBounds>.groupByLine(lineTolerance: Float): List<List<PdfTextCharBounds>> {
        val lines = mutableListOf<MutableList<PdfTextCharBounds>>()
        filter { it.hasBounds }
            .sortedWith(compareBy<PdfTextCharBounds> { it.top }.thenBy { it.left })
            .forEach { char ->
                val line = lines.firstOrNull { existing ->
                    val averageHeight = existing.map { it.bottom - it.top }.average().toFloat()
                    val charHeight = char.bottom - char.top
                    val dynamicTolerance = maxOf(minOf(averageHeight, charHeight) * 0.55f, MinLineTolerance)
                    abs(existing.averageVerticalMidpoint() - char.verticalMidpoint()) <= minOf(lineTolerance, dynamicTolerance)
                }
                if (line == null) {
                    lines += mutableListOf(char)
                } else {
                    line += char
                }
            }
        return lines
    }

    private fun List<PdfTextCharBounds>.toCharLineBounds(): PdfPageBounds {
        return PdfPageBounds(
            left = minOf { it.left }.coerceIn(0f, 1f),
            top = minOf { it.top }.coerceIn(0f, 1f),
            right = maxOf { it.right }.coerceIn(0f, 1f),
            bottom = maxOf { it.bottom }.coerceIn(0f, 1f)
        )
    }

    private fun List<PdfPageBounds>.toMergedBounds(): PdfPageBounds {
        return PdfPageBounds(
            left = minOf { it.left }.coerceIn(0f, 1f),
            top = minOf { it.top }.coerceIn(0f, 1f),
            right = maxOf { it.right }.coerceIn(0f, 1f),
            bottom = maxOf { it.bottom }.coerceIn(0f, 1f)
        )
    }

    private fun PdfTextCharBounds.verticalMidpoint(): Float = (top + bottom) / 2f

    private fun PdfPageBounds.isSameVisualLineAs(other: PdfPageBounds, lineTolerance: Float): Boolean {
        val overlap = minOf(bottom, other.bottom) - maxOf(top, other.top)
        val minHeight = minOf(bottom - top, other.bottom - other.top)
        if (overlap > 0f && overlap >= minHeight * 0.45f) return true

        val dynamicTolerance = maxOf(minHeight * 0.35f, MinLineTolerance)
        return abs(verticalMidpoint() - other.verticalMidpoint()) <= minOf(lineTolerance, dynamicTolerance)
    }

    private fun PdfPageBounds.verticalMidpoint(): Float = (top + bottom) / 2f

    private fun List<PdfTextCharBounds>.averageVerticalMidpoint(): Float {
        return map { it.verticalMidpoint() }.average().toFloat()
    }

    private fun lineVerticalDistance(pointY: Float, line: List<PdfTextCharBounds>): Float {
        val top = line.minOf { it.top }
        val bottom = line.maxOf { it.bottom }
        return when {
            pointY < top -> top - pointY
            pointY > bottom -> pointY - bottom
            else -> 0f
        }
    }

    private fun lineHorizontalDistance(pointX: Float, line: List<PdfTextCharBounds>): Float {
        val left = line.minOf { it.left }
        val right = line.maxOf { it.right }
        return when {
            pointX < left -> left - pointX
            pointX > right -> pointX - right
            else -> 0f
        }
    }

    private fun horizontalDistance(pointX: Float, char: PdfTextCharBounds): Float {
        return when {
            pointX < char.left -> char.left - pointX
            pointX > char.right -> pointX - char.right
            else -> 0f
        }
    }
}
