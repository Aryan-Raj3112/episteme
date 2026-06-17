package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.reader.ReaderLayoutSignature
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.layoutSignature

internal data class DesktopEpubPaginationRequest(
    val bookId: String,
    val chapterSignature: Int,
    val layoutSignature: ReaderLayoutSignature,
    val viewport: ReaderViewportSpec,
    val density: DesktopEpubPaginationDensity,
    val cacheGeneration: Int,
    val focusChapterIndex: Int
)

internal data class DesktopEpubPaginationDensity(
    val density: Float,
    val fontScale: Float
)

internal fun desktopMeasuredPaginationReady(
    request: DesktopEpubPaginationRequest?,
    completedRequest: DesktopEpubPaginationRequest?,
    currentPages: List<ReaderPage>,
    measuredPages: List<ReaderPage>
): Boolean {
    return request != null &&
        completedRequest == request &&
        measuredPages.isNotEmpty() &&
        currentPages.samePageLayoutAs(measuredPages)
}

internal fun desktopPaginatedLayoutReadyForDisplay(
    readingMode: ReaderReadingMode,
    measuredPagesApplied: Boolean,
    warmPagesApplied: Boolean = false
): Boolean {
    return readingMode != ReaderReadingMode.PAGINATED || measuredPagesApplied || warmPagesApplied
}

internal fun desktopMeasuredPaginationRequestStillCurrent(
    request: DesktopEpubPaginationRequest,
    settings: ReaderSettings
): Boolean {
    return settings.readingMode == ReaderReadingMode.PAGINATED &&
        settings.layoutSignature() == request.layoutSignature
}

internal fun desktopPagesWithMeasuredChapter(
    currentPages: List<ReaderPage>,
    chapterIndex: Int,
    measuredChapterPages: List<ReaderPage>
): List<ReaderPage> {
    if (currentPages.isEmpty() || measuredChapterPages.isEmpty()) return currentPages
    val firstChapterPage = currentPages.indexOfFirst { it.chapterIndex == chapterIndex }
    if (firstChapterPage < 0) return currentPages
    val lastChapterPage = currentPages.indexOfLast { it.chapterIndex == chapterIndex }
    val combined = currentPages.take(firstChapterPage) +
        measuredChapterPages +
        currentPages.drop(lastChapterPage + 1)
    return combined.mapIndexed { index, page -> page.copy(pageIndex = index) }
}

internal fun List<ReaderPage>.firstPageIndexForChapter(chapterIndex: Int): Int? {
    return indexOfFirst { it.chapterIndex == chapterIndex }.takeIf { it >= 0 }
}

internal fun SharedEpubBook.desktopPaginationContentSignature(): Int {
    return chapters.fold(31 * id.hashCode() + css.keys.hashCode() + css.values.sumOf { it.length }) { acc, chapter ->
        31 * acc +
            chapter.id.hashCode() +
            chapter.plainText.length +
            chapter.plainText.hashCode() +
            chapter.semanticBlocks.size +
            chapter.htmlContent.length +
            chapter.baseHref.orEmpty().hashCode()
    }
}

internal fun desktopChapterPaginationPriorityOrder(
    chapterCount: Int,
    currentChapterIndex: Int
): List<Int> {
    if (chapterCount <= 0) return emptyList()
    val current = currentChapterIndex.coerceIn(0, chapterCount - 1)
    val ordered = mutableListOf(current)
    for (offset in 1 until chapterCount) {
        val next = current + offset
        val previous = current - offset
        if (next < chapterCount) ordered += next
        if (previous >= 0) ordered += previous
    }
    return ordered
}

internal fun desktopMeasuredChapterCount(pages: List<ReaderPage>): Int {
    return pages
        .filter { it.semanticBlocks.isNotEmpty() }
        .map { it.chapterIndex }
        .distinct()
        .size
}

@Composable
internal fun DesktopEpubPaginationPreparing(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                if (active) "Preparing pages" else "Measuring reader layout",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
