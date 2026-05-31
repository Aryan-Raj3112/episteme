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
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubBook

internal data class DesktopEpubPaginationRequest(
    val bookId: String,
    val chapterSignature: Int,
    val layoutSignature: ReaderLayoutSignature,
    val viewport: ReaderViewportSpec,
    val density: DesktopEpubPaginationDensity,
    val cacheGeneration: Int
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
    measuredPagesApplied: Boolean
): Boolean {
    return readingMode != ReaderReadingMode.PAGINATED || measuredPagesApplied
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
    return chapters.fold(31 * id.hashCode() + css.hashCode()) { acc, chapter ->
        31 * acc +
            chapter.id.hashCode() +
            chapter.plainText.length +
            chapter.plainText.hashCode() +
            chapter.semanticBlocks.hashCode() +
            chapter.htmlContent.length +
            chapter.htmlContent.hashCode() +
            chapter.baseHref.orEmpty().hashCode()
    }
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
