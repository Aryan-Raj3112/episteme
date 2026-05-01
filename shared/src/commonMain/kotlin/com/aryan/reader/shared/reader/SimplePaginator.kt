package com.aryan.reader.shared.reader

class SimplePaginator {
    fun paginate(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewportWidth: Int = 980,
        viewportHeight: Int = 720
    ): List<ReaderPage> {
        val charsPerPage = estimateCharsPerPage(settings, viewportWidth, viewportHeight)
        return book.chapters.flatMapIndexed { chapterIndex, chapter ->
            paginateChapter(
                chapter = chapter,
                chapterIndex = chapterIndex,
                firstPageIndex = 0,
                charsPerPage = charsPerPage
            )
        }.mapIndexed { pageIndex, page -> page.copy(pageIndex = pageIndex) }
    }

    fun repaginate(
        state: PaginatedReaderState,
        settings: ReaderSettings,
        viewportWidth: Int = 980,
        viewportHeight: Int = 720
    ): PaginatedReaderState {
        val current = state.currentPage
        val pages = paginate(state.book, settings, viewportWidth, viewportHeight)
        val newIndex = if (current == null) {
            0
        } else {
            pages.indexOfFirst {
                it.chapterIndex == current.chapterIndex && it.startOffset <= current.startOffset && it.endOffset >= current.startOffset
            }.takeIf { it >= 0 } ?: 0
        }
        return state.copy(pages = pages, currentPageIndex = newIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0)), settings = settings)
    }

    private fun paginateChapter(
        chapter: SharedEpubChapter,
        chapterIndex: Int,
        firstPageIndex: Int,
        charsPerPage: Int
    ): List<ReaderPage> {
        val normalized = chapter.plainText
            .replace("\r\n", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        if (normalized.isBlank()) {
            return listOf(
                ReaderPage(
                    pageIndex = firstPageIndex,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title,
                    text = "",
                    startOffset = 0,
                    endOffset = 0
                )
            )
        }

        val pages = mutableListOf<ReaderPage>()
        var start = 0
        while (start < normalized.length) {
            val rawEnd = (start + charsPerPage).coerceAtMost(normalized.length)
            val end = findPageBreak(normalized, start, rawEnd)
            pages.add(
                ReaderPage(
                    pageIndex = firstPageIndex + pages.size,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title,
                    text = normalized.substring(start, end).trim(),
                    startOffset = start,
                    endOffset = end
                )
            )
            start = end
            while (start < normalized.length && normalized[start].isWhitespace()) {
                start++
            }
        }
        return pages
    }

    private fun findPageBreak(text: String, start: Int, rawEnd: Int): Int {
        if (rawEnd >= text.length) return text.length
        val paragraphBreak = text.lastIndexOf("\n\n", rawEnd).takeIf { it > start + 300 }
        if (paragraphBreak != null) return paragraphBreak
        val sentenceBreak = text.lastIndexOfAny(charArrayOf('.', '!', '?'), rawEnd - 1).takeIf { it > start + 300 }
        if (sentenceBreak != null) return sentenceBreak + 1
        val wordBreak = text.lastIndexOf(' ', rawEnd - 1).takeIf { it > start + 120 }
        return wordBreak ?: rawEnd
    }

    private fun estimateCharsPerPage(
        settings: ReaderSettings,
        viewportWidth: Int,
        viewportHeight: Int
    ): Int {
        val usableWidth = (viewportWidth - settings.margin * 2).coerceAtLeast(360)
        val usableHeight = (viewportHeight - settings.margin * 2).coerceAtLeast(360)
        val averageCharWidth = settings.fontSize * 0.55f
        val lineHeight = settings.fontSize * settings.lineSpacing
        val charsPerLine = (usableWidth / averageCharWidth).toInt().coerceAtLeast(35)
        val linesPerPage = (usableHeight / lineHeight).toInt().coerceAtLeast(12)
        return (charsPerLine * linesPerPage).coerceIn(900, 4_500)
    }
}
