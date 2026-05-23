package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings

object PdfSpreadLayout {
    fun isTwoPageSpreadEnabled(settings: ReaderSettings): Boolean {
        return settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE
    }

    fun normalizePageIndex(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): Int {
        if (pageCount <= 0) return 0
        val clamped = pageIndex.coerceIn(0, pageCount - 1)
        if (!isTwoPageSpreadEnabled(settings)) return clamped
        if (!settings.pdfFirstPageStandaloneInSpread) {
            return (clamped - (clamped % 2)).coerceIn(0, pageCount - 1)
        }
        if (clamped == 0) return 0
        val adjusted = clamped - 1
        return (1 + adjusted - (adjusted % 2)).coerceIn(0, pageCount - 1)
    }

    fun visiblePageIndices(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): List<Int> {
        if (pageCount <= 0) return emptyList()
        val start = normalizePageIndex(pageIndex, pageCount, settings)
        if (!isTwoPageSpreadEnabled(settings)) return listOf(start)
        if (settings.pdfFirstPageStandaloneInSpread && start == 0) return listOf(0)
        return listOf(start, start + 1).filter { it in 0 until pageCount }
    }

    fun visiblePageIndicesForDisplay(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): List<Int> {
        val indices = visiblePageIndices(pageIndex, pageCount, settings)
        return if (settings.rightToLeftPagination) indices.asReversed() else indices
    }

    fun spreadStartPageIndices(
        pageCount: Int,
        settings: ReaderSettings
    ): List<Int> {
        if (pageCount <= 0) return emptyList()
        if (!isTwoPageSpreadEnabled(settings)) return (0 until pageCount).toList()
        val starts = mutableListOf<Int>()
        var current = 0
        while (current in 0 until pageCount && current !in starts) {
            starts += current
            val next = nextPageIndex(current, pageCount, settings)
            if (next <= current) break
            current = next
        }
        return starts
    }

    fun canGoPrevious(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): Boolean {
        if (pageCount <= 1) return false
        return previousPageIndex(pageIndex, pageCount, settings) < normalizePageIndex(pageIndex, pageCount, settings)
    }

    fun canGoNext(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): Boolean {
        if (pageCount <= 1) return false
        return nextPageIndex(pageIndex, pageCount, settings) > normalizePageIndex(pageIndex, pageCount, settings)
    }

    fun previousPageIndex(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): Int {
        if (pageCount <= 0) return 0
        val current = normalizePageIndex(pageIndex, pageCount, settings)
        if (!isTwoPageSpreadEnabled(settings)) {
            return (current - 1).coerceIn(0, pageCount - 1)
        }
        val target = if (settings.pdfFirstPageStandaloneInSpread && current <= 1) {
            0
        } else {
            current - 2
        }
        return normalizePageIndex(target, pageCount, settings)
    }

    fun nextPageIndex(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): Int {
        if (pageCount <= 0) return 0
        val current = normalizePageIndex(pageIndex, pageCount, settings)
        if (!isTwoPageSpreadEnabled(settings)) {
            return (current + 1).coerceIn(0, pageCount - 1)
        }
        val target = if (settings.pdfFirstPageStandaloneInSpread && current == 0) {
            1
        } else {
            current + 2
        }
        return normalizePageIndex(target, pageCount, settings)
    }

    fun pageRangeLabel(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): String {
        val pages = visiblePageIndices(pageIndex, pageCount.coerceAtLeast(1), settings).ifEmpty { listOf(0) }
        val first = pages.first() + 1
        val last = pages.last() + 1
        return if (first == last) "$first" else "$first-$last"
    }

    fun progressPercent(
        pageIndex: Int,
        pageCount: Int,
        settings: ReaderSettings
    ): Float {
        if (pageCount <= 0) return 0f
        val visibleEnd = visiblePageIndices(pageIndex, pageCount, settings).lastOrNull()
            ?: normalizePageIndex(pageIndex, pageCount, settings)
        return ((visibleEnd + 1).toFloat() / pageCount.coerceAtLeast(1)) * 100f
    }
}
