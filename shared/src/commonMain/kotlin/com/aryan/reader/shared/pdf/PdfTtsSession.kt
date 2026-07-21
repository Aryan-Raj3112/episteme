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
