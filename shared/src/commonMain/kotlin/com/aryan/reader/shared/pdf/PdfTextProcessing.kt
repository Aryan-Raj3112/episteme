package com.aryan.reader.shared.pdf

data class PdfProcessedText(
    val cleanText: String,
    val indexMap: List<Int>
) {
    /**
     * Maps a [start, endExclusive) range in [cleanText] back to raw source offsets
     * (end exclusive). Returns null when no clean character falls inside the range.
     */
    fun rawRange(start: Int, endExclusive: Int): Pair<Int, Int>? {
        if (indexMap.isEmpty()) return null
        val first = start.coerceIn(0, indexMap.lastIndex)
        val last = (endExclusive - 1).coerceIn(first, indexMap.lastIndex)
        return indexMap[first] to indexMap[last] + 1
    }
}

data class PdfOcrWord(
    val text: String,
    val bounds: PdfPageBounds?
)

object PdfTextProcessing {
    fun preprocessForTts(rawText: String): PdfProcessedText {
        if (rawText.isBlank()) return PdfProcessedText("", emptyList())

        val cleanText = StringBuilder(rawText.length)
        val indexMap = mutableListOf<Int>()
        rawText.forEachIndexed { index, char ->
            when (char) {
                '\n' -> {
                    if (isNewlineInsideHyphenatedWord(rawText, index)) {
                        removeTrailingHyphenFragment(cleanText, indexMap)
                    } else {
                        val lastChar = cleanText.trimEnd().lastOrNull()
                        if (lastChar != null && lastChar !in ".?!" &&
                            cleanText.isNotEmpty() && !cleanText.last().isWhitespace()
                        ) {
                            cleanText.append(' ')
                            indexMap += index
                        }
                    }
                }
                '\r' -> Unit
                else -> {
                    cleanText.append(char)
                    indexMap += index
                }
            }
        }
        return PdfProcessedText(cleanText.toString().trim(), indexMap)
    }

    /**
     * Removes hyphens that typographically split a word across a line break
     * ("understand-\ning" -> "understanding") while leaving every other character,
     * including regular line breaks, untouched. Heuristic: the break hyphen must sit
     * between two letters, and the word continuation must start right after the line
     * break. Hyphenated compounds broken at an existing hyphen ("mother-in-\nlaw")
     * are indistinguishable from soft wraps without a dictionary and get joined too.
     */
    fun joinHyphenatedLineBreaks(text: String): String = joinHyphenatedLineBreaksMapped(text).cleanText

    /** [joinHyphenatedLineBreaks] with a clean-to-source index map for offset remapping. */
    fun joinHyphenatedLineBreaksMapped(text: String): PdfProcessedText {
        val result = StringBuilder(text.length)
        val indexMap = mutableListOf<Int>()
        text.forEachIndexed { index, char ->
            if (char == '\n' && isNewlineInsideHyphenatedWord(text, index)) {
                removeTrailingHyphenFragment(result, indexMap)
            } else {
                result.append(char)
                indexMap += index
            }
        }
        return PdfProcessedText(result.toString(), indexMap)
    }

    private fun isNewlineInsideHyphenatedWord(text: String, newlineIndex: Int): Boolean {
        val after = text.getOrNull(newlineIndex + 1) ?: return false
        if (!after.isLetter()) return false

        var cursor = newlineIndex - 1
        while (cursor >= 0 && text[cursor] != '\n' && text[cursor].isWhitespace()) cursor--
        if (cursor < 0 || !isBreakHyphen(text[cursor])) return false

        val before = text.getOrNull(cursor - 1) ?: return false
        return before.isLetter()
    }

    private fun removeTrailingHyphenFragment(cleanText: StringBuilder, indexMap: MutableList<Int>) {
        var removed = 0
        while (cleanText.isNotEmpty() &&
            (cleanText.last().isWhitespace() || isBreakHyphen(cleanText.last()))
        ) {
            cleanText.deleteAt(cleanText.length - 1)
            removed++
        }
        repeat(removed) {
            if (indexMap.isNotEmpty()) indexMap.removeAt(indexMap.size - 1)
        }
    }

    private fun isBreakHyphen(char: Char): Boolean =
        char == '-' || char == '\u2010' || char == '\u00AD'

    fun mergeScreenBoundsIntoLines(bounds: List<PdfPageBounds>): List<PdfPageBounds> {
        if (bounds.isEmpty()) return emptyList()
        val merged = mutableListOf<PdfPageBounds>()
        var current: PdfPageBounds? = null
        bounds.sortedWith(compareBy<PdfPageBounds> { it.top }.thenBy { it.left }).forEach { next ->
            val line = current
            if (line == null) {
                current = next
            } else if (maxOf(line.top, next.top) < minOf(line.bottom, next.bottom)) {
                current = PdfPageBounds(
                    left = minOf(line.left, next.left),
                    top = minOf(line.top, next.top),
                    right = maxOf(line.right, next.right),
                    bottom = maxOf(line.bottom, next.bottom)
                )
            } else {
                merged += line
                current = next
            }
        }
        current?.let(merged::add)
        return merged
    }

    fun mergePdfBoundsIntoLines(bounds: List<PdfPageBounds>): List<PdfPageBounds> {
        if (bounds.isEmpty()) return emptyList()
        val normalized = bounds.map { boundsForRect ->
            PdfPageBounds(
                left = minOf(boundsForRect.left, boundsForRect.right),
                top = minOf(boundsForRect.top, boundsForRect.bottom),
                right = maxOf(boundsForRect.left, boundsForRect.right),
                bottom = maxOf(boundsForRect.top, boundsForRect.bottom)
            )
        }
        val merged = mutableListOf<PdfPageBounds>()
        var current: PdfPageBounds? = null
        normalized.sortedWith(compareBy<PdfPageBounds> { -it.bottom }.thenBy { it.left }).forEach { next ->
            val line = current
            if (line == null) {
                current = next
            } else {
                val overlapHeight = minOf(line.bottom, next.bottom) - maxOf(line.top, next.top)
                val minHeight = minOf(line.bottom - line.top, next.bottom - next.top)
                if (overlapHeight > 0f && overlapHeight >= minHeight * 0.1f) {
                    current = PdfPageBounds(
                        left = minOf(line.left, next.left),
                        top = minOf(line.top, next.top),
                        right = maxOf(line.right, next.right),
                        bottom = maxOf(line.bottom, next.bottom)
                    )
                } else {
                    merged += line.toPdfCoordinateBounds()
                    current = next
                }
            }
        }
        current?.let { merged += it.toPdfCoordinateBounds() }
        return merged
    }

    fun findOcrWordSequence(words: List<PdfOcrWord>, textChunk: String): List<PdfPageBounds> {
        if (textChunk.isBlank() || words.isEmpty()) return emptyList()
        val targetWords = textChunk.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (targetWords.isEmpty() || targetWords.size > words.size) return emptyList()

        for (start in 0..words.size - targetWords.size) {
            val matchedBounds = mutableListOf<PdfPageBounds>()
            var matches = true
            for (offset in targetWords.indices) {
                val word = words[start + offset]
                if (!wordsMatch(word.text, targetWords[offset])) {
                    matches = false
                    break
                }
                word.bounds?.let(matchedBounds::add)
            }
            if (matches) return matchedBounds
        }
        return emptyList()
    }

    private fun wordsMatch(ocrWord: String, targetWord: String): Boolean {
        return ocrWord.equals(targetWord, ignoreCase = true) ||
            ocrWord.replace(TrailingOcrPunctuation, "").equals(targetWord, ignoreCase = true) ||
            targetWord.replace(TrailingTargetPunctuation, "").equals(ocrWord, ignoreCase = true)
    }

    private fun PdfPageBounds.toPdfCoordinateBounds(): PdfPageBounds =
        PdfPageBounds(left = left, top = bottom, right = right, bottom = top)

    private val TrailingOcrPunctuation = Regex("[.,;:!?\"')$]")
    private val TrailingTargetPunctuation = Regex("[.,;:!?\"'(]$")
}
