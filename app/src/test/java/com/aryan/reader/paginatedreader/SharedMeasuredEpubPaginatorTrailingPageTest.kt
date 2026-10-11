package com.aryan.reader.paginatedreader

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.SharedMeasuredEpubPaginator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression for the iOS/desktop paginated reader losing the end of a chapter.
 *
 * `SharedMeasuredEpubPaginator` accumulated a chapter's blocks in `pageBlocks` and returned
 * without committing the last partial page, while the Android benchmark commits it
 * (`paginateReaderBlocks`'s `commitPage("final")`, `paginateVerticalFlow`'s trailing `emit()`).
 * Anything left pending existed on no [com.aryan.reader.shared.reader.ReaderPage] at all, so the
 * reader rendered the chapter's tail as missing text and reported a page count one short —
 * every sonnet in the Sonnets fixture lost its last quarter that way.
 *
 * Image blocks keep the fixture free of text-run-dependent tallies: their heights are pure
 * geometry, so the expected page split is stable across platforms and Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SharedMeasuredEpubPaginatorTrailingPageTest {

    @Test
    fun `chapter whose trailing blocks land mid-page commits the trailing page`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val context = RuntimeEnvironment.getApplication() as Context
                val paginator = SharedMeasuredEpubPaginator(
                    textMeasurer = TextMeasurer(
                        defaultFontFamilyResolver = createFontFamilyResolver(context),
                        defaultDensity = Density(1f),
                        defaultLayoutDirection = LayoutDirection.Ltr
                    ),
                    density = Density(1f)
                )
                val images = (1..8).map { index ->
                    SemanticImage(
                        path = "images/page-$index.png",
                        altText = "Image $index",
                        intrinsicWidth = 400f,
                        intrinsicHeight = 150f,
                        style = CssStyle(),
                        elementId = "image-$index",
                        cfi = null
                    )
                }
                val book = SharedEpubBook(
                    id = "trailing-page",
                    fileName = "trailing-page.epub",
                    title = "Trailing Page",
                    chapters = listOf(
                        SharedEpubChapter(
                            id = "chapter-0",
                            title = "Chapter One",
                            plainText = "",
                            semanticBlocks = images
                        )
                    )
                )

                val pages = paginator.paginate(
                    book = book,
                    settings = ReaderSettings(
                        readingMode = ReaderReadingMode.PAGINATED,
                        margin = 16
                    ),
                    viewport = ReaderViewportSpec(widthPx = 400, heightPx = 600)
                )

                assertTrue(
                    "the fixture must need more than one page, got ${pages.size}",
                    pages.size > 1
                )
                val placed = pages
                    .flatMap { page -> page.semanticBlocks }
                    .filterIsInstance<SemanticImage>()
                    .mapNotNull { it.elementId }
                assertEquals(
                    "no block may be committed to two pages",
                    images.size,
                    placed.size
                )
                assertEquals(
                    "every block of the chapter must land on a page record",
                    images.map { it.elementId }.toSet(),
                    placed.toSet()
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
