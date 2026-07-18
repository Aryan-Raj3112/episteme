package com.aryan.reader.shared.pdf

/**
 * Pure-Kotlin helpers that drive PDF text selection on platforms whose PDF
 * engine exposes character-level data. They mirror the long-press → word
 * selection → drag-handle extension flow first implemented on Android in
 * `app/src/main/java/com/aryan/reader/pdf/PdfPageComposable.kt` and
 * `app/src/main/java/com/aryan/reader/pdf/PdfHelper.kt`.
 *
 * The helpers are intentionally agnostic of pdfium: they take an abstract
 * [PdfTextSelectionBackend] and produce normalised geometry that the UI layer
 * maps to screen coordinates.
 */

/** Half-open character range `[start, end)` within a page. */
data class PdfTextSelectionRange(
    val start: Int,
    val end: Int
) {
    val isEmpty: Boolean get() = start >= end
    val length: Int get() = (end - start).coerceAtLeast(0)

    fun coerced(pageCharCount: Int): PdfTextSelectionRange {
        if (pageCharCount <= 0) return PdfTextSelectionRange(0, 0)
        val safeStart = start.coerceIn(0, pageCharCount - 1)
        val safeEnd = end.coerceIn(1, pageCharCount)
        return if (safeStart < safeEnd) {
            PdfTextSelectionRange(safeStart, safeEnd)
        } else {
            val clampedStart = minOf(safeStart, safeEnd - 1).coerceIn(0, pageCharCount - 1)
            val clampedEnd = (clampedStart + 1).coerceAtMost(pageCharCount)
            PdfTextSelectionRange(clampedStart, clampedEnd)
        }
    }
}

/**
 * Endpoint that the user is currently dragging. Mirrors the `Handle.START` /
 * `Handle.END` enum used by the Android implementation.
 */
enum class PdfSelectionHandle { START, END }

/**
 * Result of computing the next range after a handle drag, mirroring the
 * swap logic in `PdfPageComposable.kt`.
 */
data class PdfSelectionHandleUpdate(
    val activeHandle: PdfSelectionHandle,
    val range: PdfTextSelectionRange
)

/**
 * Backend that mediates between this pure-Kotlin helper and the platform's PDF
 * engine. All character indices are as defined by pdfium's text page. Positions
 * passed to [charIndexAtNormalized] use normalised page coordinates in
 * `[0, 1]` with origin at the **top-left** of the page (matching the
 * [`PdfPageBounds`] convention used by every Compose overlay in this project).
 */
interface PdfTextSelectionBackend {
    val pageCharCount: Int

    /** Unicode for the character at [index], or `0` if unavailable. */
    fun charAt(index: Int): Char

    /**
     * Index of the character nearest the normalised page position
     * `([normX], [normY])` (top-left origin), or `-1`.
     */
    fun charIndexAtNormalized(
        normX: Float,
        normY: Float,
        xTolerance: Double,
        yTolerance: Double
    ): Int
}

/**
 * A loaded text-page session for a single PDF page, exposing both the
 * selection-relevant character operations ([PdfTextSelectionBackend]) and the
 * rectangle extraction needed to render selection highlights. All coordinates
 * returned from this session are normalised to `[0, 1]` with origin at the
 * top-left of the page, matching the [`PdfPageBounds`] convention used by
 * every Compose overlay in this project.
 *
 * The owner is responsible for closing the session via [close].
 */
interface PdfTextPageSession : PdfTextSelectionBackend, AutoCloseable {
    /** Bounding box of [index] in normalised coordinates, or `null` on failure. */
    fun charBoxNormalized(index: Int): PdfPageBounds?

    /**
     * Rectangles occupied by `[startIndex, startIndex + length)`. Implementations
     * typically merge adjacent glyphs sharing a font on the same line, matching
     * Android's `textPageGetRectsForRanges` output.
     */
    fun rectsForRangeNormalized(startIndex: Int, length: Int): List<PdfPageBounds>

    /** UTF-16 text for `[startIndex, startIndex + length)`, or `null` on failure. */
    fun textForRange(startIndex: Int, length: Int): String?
}

object PdfTextSelectionEngine {

    /**
     * Replicates `findWordBoundaries` in `PdfHelper.kt`: expand forwards and
     * backwards from [initialCharIndex] while the character is a letter or
     * digit. Returns `null` when the initial character is not selectable (e.g.
     * whitespace or punctuation) or when no run is found.
     */
    fun wordBoundaries(
        backend: PdfTextSelectionBackend,
        initialCharIndex: Int
    ): PdfTextSelectionRange? {
        val pageCharCount = backend.pageCharCount
        if (initialCharIndex !in 0 until pageCharCount) return null
        val initialChar = backend.charAt(initialCharIndex)
        if (!initialChar.isLetterOrDigit()) return null

        var wordStartIndex = initialCharIndex
        while (wordStartIndex > 0) {
            val char = backend.charAt(wordStartIndex - 1)
            if (!char.isLetterOrDigit()) break
            wordStartIndex--
        }
        var wordEndIndex = initialCharIndex
        while (wordEndIndex < pageCharCount) {
            val char = backend.charAt(wordEndIndex)
            if (!char.isLetterOrDigit()) break
            wordEndIndex++
        }
        return if (wordStartIndex < wordEndIndex) {
            PdfTextSelectionRange(wordStartIndex, wordEndIndex)
        } else {
            null
        }
    }

    /**
     * Replicates the swap-when-crossed handle drag logic in
     * `PdfPageComposable.kt`. The returned [PdfSelectionHandleUpdate] always
     * points at the handle the user is now effectively dragging, which may
     * differ from [activeHandle] when the user drags one handle across the
     * other.
     */
    fun extendRange(
        backend: PdfTextSelectionBackend,
        current: PdfTextSelectionRange,
        activeHandle: PdfSelectionHandle,
        newCharIndex: Int
    ): PdfSelectionHandleUpdate {
        val pageCharCount = backend.pageCharCount
        if (pageCharCount <= 0) return PdfSelectionHandleUpdate(activeHandle, current)
        val charIndex = newCharIndex.coerceIn(0, pageCharCount - 1)
        return when (activeHandle) {
            PdfSelectionHandle.START -> {
                val newStart = charIndex.coerceIn(0, pageCharCount - 1)
                if (newStart >= current.end - 1) {
                    val tempOldEndCharIndex = (current.end - 1).coerceAtLeast(0)
                    val newEnd = (charIndex + 1).coerceAtMost(pageCharCount)
                    PdfSelectionHandleUpdate(
                        activeHandle = PdfSelectionHandle.END,
                        range = PdfTextSelectionRange(tempOldEndCharIndex, newEnd).coerced(pageCharCount)
                    )
                } else {
                    PdfSelectionHandleUpdate(
                        activeHandle = PdfSelectionHandle.START,
                        range = PdfTextSelectionRange(newStart, current.end)
                    )
                }
            }
            PdfSelectionHandle.END -> {
                val newEnd = (charIndex + 1).coerceIn(1, pageCharCount)
                if (newEnd <= current.start + 1) {
                    val newStart = (newEnd - 1).coerceAtLeast(0)
                    val fixed = (current.start + 1).coerceAtMost(pageCharCount)
                    PdfSelectionHandleUpdate(
                        activeHandle = PdfSelectionHandle.START,
                        range = PdfTextSelectionRange(newStart, fixed).coerced(pageCharCount)
                    )
                } else {
                    PdfSelectionHandleUpdate(
                        activeHandle = PdfSelectionHandle.END,
                        range = PdfTextSelectionRange(current.start, newEnd)
                    )
                }
            }
        }
    }
}
