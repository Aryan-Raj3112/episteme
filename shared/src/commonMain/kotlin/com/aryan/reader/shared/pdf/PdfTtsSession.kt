package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsPlanner

data class PdfTtsPage(
    val pageIndex: Int,
    val sourceText: String,
    val chunks: List<ReaderTtsChunk>
)

object PdfTtsSessionPlanner {
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
