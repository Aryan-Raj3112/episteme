package com.aryan.reader.shared.pdf

/**
 * PDF text-page compatible adapter for Vision OCR. It intentionally exposes the same normalized
 * geometry contract as [IosPdfTextPage], so the shared selection overlay does not need an
 * Android/iOS branch when a scanned page has no embedded text layer.
 */
internal class IosPdfOcrTextPageSession private constructor(
    private val textPage: IosPdfOcrTextPage,
) : PdfTextPageSession {
    override val pageCharCount: Int
        get() = textPage.text.length

    override fun charAt(index: Int): Char = textPage.text.getOrNull(index) ?: 0.toChar()

    override fun charIndexAtNormalized(
        normX: Float,
        normY: Float,
        xTolerance: Double,
        yTolerance: Double,
    ): Int {
        if (textPage.characterBounds.isEmpty()) return -1
        val x = normX.coerceIn(0f, 1f)
        val y = normY.coerceIn(0f, 1f)
        val toleranceX = (xTolerance / 1000.0).toFloat().coerceAtLeast(0.01f)
        val toleranceY = (yTolerance / 1000.0).toFloat().coerceAtLeast(0.01f)
        return textPage.characterBounds
            .mapIndexed { index, bounds ->
                val dx = when {
                    x < bounds.left -> bounds.left - x
                    x > bounds.right -> x - bounds.right
                    else -> 0f
                }
                val dy = when {
                    y < bounds.top -> bounds.top - y
                    y > bounds.bottom -> y - bounds.bottom
                    else -> 0f
                }
                index to (dx / toleranceX + dy / toleranceY)
            }
            .minByOrNull { it.second }
            ?.takeIf { (index, distance) ->
                distance <= 2f || textPage.characterBounds[index].contains(x, y)
            }
            ?.first
            ?: -1
    }

    override fun linkAtNormalized(normX: Float, normY: Float): PdfLinkTarget? = null

    override fun charBoxNormalized(index: Int): PdfPageBounds? =
        textPage.characterBounds.getOrNull(index)

    override fun rectsForRangeNormalized(startIndex: Int, length: Int): List<PdfPageBounds> {
        if (length <= 0) return emptyList()
        val start = startIndex.coerceIn(0, pageCharCount)
        val end = (start + length).coerceIn(start, pageCharCount)
        if (start >= end) return emptyList()
        return textPage.characterBounds
            .subList(start, end)
            .filterIndexed { index, _ -> textPage.text.getOrNull(start + index)?.isWhitespace() != true }
            .mergeOcrCharacterBounds()
    }

    override fun textForRange(startIndex: Int, length: Int): String? {
        if (length <= 0) return null
        val start = startIndex.coerceIn(0, pageCharCount)
        val end = (start + length).coerceIn(start, pageCharCount)
        return textPage.text.substring(start, end).takeIf(String::isNotBlank)
    }

    override fun close() = Unit

    private fun List<PdfPageBounds>.mergeOcrCharacterBounds(): List<PdfPageBounds> {
        if (isEmpty()) return emptyList()
        val sorted = sortedWith(compareBy<PdfPageBounds> { it.top }.thenBy { it.left })
        val merged = mutableListOf<PdfPageBounds>()
        var current: PdfPageBounds? = null
        sorted.forEach { next ->
            val line = current
            if (line == null) {
                current = next
            } else if (maxOf(line.top, next.top) < minOf(line.bottom, next.bottom)) {
                current = PdfPageBounds(
                    left = minOf(line.left, next.left),
                    top = minOf(line.top, next.top),
                    right = maxOf(line.right, next.right),
                    bottom = maxOf(line.bottom, next.bottom),
                )
            } else {
                merged += line
                current = next
            }
        }
        current?.let(merged::add)
        return merged
    }

    private fun PdfPageBounds.contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    companion object {
        suspend fun open(
            path: String?,
            pageIndex: Int,
            password: String?,
            languages: List<String> = IosPdfOcrLanguagePreferences.languages,
        ): IosPdfOcrTextPageSession? {
            val words = IosPdfOcrPageCache.getOrRecognize(path, pageIndex, password, languages)
            return IosPdfOcrTextPageSession(buildIosPdfOcrTextPage(words))
                .takeIf { it.pageCharCount > 0 }
        }
    }
}
