package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner

data class PdfTtsPage(
    val pageIndex: Int,
    val sourceText: String,
    val chunks: List<ReaderTtsChunk>,
    /**
     * Raw Pdfium -> clean mapping when this page was planned from raw
     * Pdfium text via [PdfTtsSessionPlanner.pageFromRawPdfium]. Null when
     * planned directly from already-clean text (tests / callers that own
     * normalization). Highlight must map chunk (clean) ranges back through
     * this before calling `rectsForRangeNormalized`, mirroring Android
     * `PdfViewerScreen` `processedText.indexMap` behavior.
     */
    val processed: PdfProcessedText? = null
)

object PdfTtsSessionPlanner {
    /**
     * Android benchmark parity (PdfViewerScreen.startTts):
     * `startCharIndex` is a RAW Pdfium char index (selection / most-visible
     * anchor). The engine speaks CLEAN text (`preprocessForTts`), so the raw
     * start is mapped via `indexMap.indexOfFirst { it >= rawStart }` before
     * chunk slicing. Planning directly on raw text (old behavior) spoke
     * hyphenated/newline artifacts and started in the wrong chunk.
     */
    fun pageFromRawPdfium(
        pageIndex: Int,
        rawText: String,
        rawStartCharIndex: Int = 0
    ): PdfTtsPage {
        val processed = PdfTextProcessing.preprocessForTts(rawText)
        if (processed.cleanText.isBlank()) {
            return PdfTtsPage(pageIndex, "", emptyList(), processed)
        }
        val cleanStart = if (rawStartCharIndex <= 0) {
            0
        } else {
            val mapped = processed.indexMap.indexOfFirst { it >= rawStartCharIndex }
            if (mapped >= 0) mapped else processed.cleanText.length
        }
        val planned = page(pageIndex, processed.cleanText, cleanStart)
        return planned.copy(processed = processed)
    }

    /** Maps a clean-text TTS chunk back to the raw Pdfium range for highlight. */
    fun rawHighlightRange(
        processed: PdfProcessedText?,
        chunk: ReaderTtsChunk?
    ): PdfTextSelectionRange? {
        if (chunk == null) return null
        if (processed == null || processed.indexMap.isEmpty()) {
            return highlightRange(chunk, chunk.endOffset.coerceAtLeast(chunk.startOffset))
        }
        val raw = processed.rawRange(chunk.startOffset, chunk.endOffset) ?: return null
        return PdfTextSelectionRange(raw.first, raw.second).takeUnless { it.isEmpty }
    }

    fun page(pageIndex: Int, sourceText: String, startCharIndex: Int = 0): PdfTtsPage {
        val safeStart = startCharIndex.coerceIn(0, sourceText.length)
        val chunks = ReaderTtsPlanner.chunksForText(
            text = sourceText,
            pageIndex = pageIndex,
            chapterIndex = pageIndex,
            chapterTitle = "Page ${pageIndex + 1}"
        ).mapNotNull { chunk ->
            when {
                chunk.endOffset <= safeStart -> null
                chunk.startOffset < safeStart -> chunk.copy(
                    text = sourceText.substring(safeStart, chunk.endOffset),
                    spokenText = sourceText.substring(safeStart, chunk.endOffset),
                    startOffset = safeStart
                )
                else -> chunk
            }
        }.filter { it.text.isNotBlank() }.mapIndexed { index, chunk -> chunk.copy(index = index) }
        return PdfTtsPage(pageIndex, sourceText, chunks)
    }

    fun nextPage(currentPageIndex: Int, pageCount: Int): Int? =
        (currentPageIndex + 1).takeIf { it in 0 until pageCount }

    fun highlightRange(chunk: ReaderTtsChunk?, pageCharCount: Int): PdfTextSelectionRange? {
        if (chunk == null || pageCharCount <= 0) return null
        val start = chunk.startOffset.coerceIn(0, pageCharCount)
        val end = chunk.endOffset.coerceIn(start, pageCharCount)
        return PdfTextSelectionRange(start, end).takeUnless { it.isEmpty }
    }
}

fun shouldStopPdfTtsForManualPageTurn(
    isPaginationMode: Boolean,
    isUserInitiated: Boolean,
    isTtsPlayingOrLoading: Boolean,
): Boolean = isPaginationMode && isUserInitiated && isTtsPlayingOrLoading

fun shouldStopPdfTtsForNavigation(
    isPaginationMode: Boolean,
    reason: PdfNavigationReason,
    pageWillChange: Boolean,
    isTtsPlayingOrLoading: Boolean,
): Boolean = isPaginationMode &&
    reason != PdfNavigationReason.TTS &&
    pageWillChange &&
    isTtsPlayingOrLoading

fun pdfAutoScrollPixelsPerSecond(speedMultiplier: Float): Float =
    80f * (speedMultiplier.coerceIn(0.1f, 10f) * 0.5f)

data class PdfAutoScrollProfile(
    val speed: Float = 3f,
    val minSpeed: Float = 0.1f,
    val maxSpeed: Float = 10f,
) {
    fun sanitized(): PdfAutoScrollProfile {
        val min = minSpeed.coerceIn(0.1f, 10f)
        val max = maxSpeed.coerceIn(min, 10f)
        return copy(
            speed = speed.coerceIn(min, max),
            minSpeed = min,
            maxSpeed = max,
        )
    }

    fun withMinSpeed(value: Float): PdfAutoScrollProfile {
        val min = value.coerceIn(0.1f, 10f)
        val max = maxSpeed.coerceAtLeast(min)
        return copy(speed = speed.coerceIn(min, max), minSpeed = min, maxSpeed = max).sanitized()
    }

    fun withMaxSpeed(value: Float): PdfAutoScrollProfile {
        val max = value.coerceIn(0.1f, 10f)
        val min = minSpeed.coerceAtMost(max)
        return copy(speed = speed.coerceIn(min, max), minSpeed = min, maxSpeed = max).sanitized()
    }
}

const val PdfMusicianHoldDurationMillis = 1_000L
const val PdfMusicianTapPauseMillis = 600L
const val PdfMusicianHoldPauseMillis = 1_000L
const val PdfMusicianViewportJumpFraction = 0.75f

enum class PdfMusicianNavigationTarget { RELATIVE, START, END }

data class PdfMusicianGesturePlan(
    val target: PdfMusicianNavigationTarget,
    val relativeViewportDelta: Float,
    val pauseMillis: Long,
)

fun planPdfMusicianGesture(isRightRegion: Boolean, isLongPress: Boolean): PdfMusicianGesturePlan {
    if (isLongPress) {
        return PdfMusicianGesturePlan(
            target = if (isRightRegion) PdfMusicianNavigationTarget.END else PdfMusicianNavigationTarget.START,
            relativeViewportDelta = 0f,
            pauseMillis = PdfMusicianHoldPauseMillis,
        )
    }
    return PdfMusicianGesturePlan(
        target = PdfMusicianNavigationTarget.RELATIVE,
        relativeViewportDelta = if (isRightRegion) PdfMusicianViewportJumpFraction else -PdfMusicianViewportJumpFraction,
        pauseMillis = PdfMusicianTapPauseMillis,
    )
}
