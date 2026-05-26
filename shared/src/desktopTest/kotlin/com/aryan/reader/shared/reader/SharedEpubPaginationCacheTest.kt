package com.aryan.reader.shared.reader

import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedEpubPaginationCacheTest {

    @Test
    fun `page cache round trips measured pages`() = runBlocking {
        val root = Files.createTempDirectory("reader-page-cache").toFile()
        try {
            val cache = SharedEpubPaginationCache(root)
            val book = cacheBook()
            val settings = ReaderSettings(fontSize = 19, lineSpacing = 1.5f)
            val viewport = ReaderViewportSpec(widthPx = 960, heightPx = 720)
            val pages = listOf(
                ReaderPage(
                    pageIndex = 12,
                    chapterIndex = 0,
                    chapterTitle = "One",
                    text = "Cached page",
                    startOffset = 4,
                    endOffset = 15
                )
            )

            cache.save(book, settings, viewport, pages)
            val loaded = cache.load(book, settings, viewport)

            assertNotNull(loaded)
            assertEquals(1, loaded.size)
            assertEquals(0, loaded.first().pageIndex)
            assertEquals("Cached page", loaded.first().text)
            assertEquals(4, loaded.first().startOffset)
            assertEquals(15, loaded.first().endOffset)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `page cache misses when viewport or chapter content changes`() = runBlocking {
        val root = Files.createTempDirectory("reader-page-cache").toFile()
        try {
            val cache = SharedEpubPaginationCache(root)
            val book = cacheBook()
            val settings = ReaderSettings()
            val viewport = ReaderViewportSpec(widthPx = 900, heightPx = 700)
            val pages = listOf(
                ReaderPage(
                    pageIndex = 0,
                    chapterIndex = 0,
                    chapterTitle = "One",
                    text = "Cached page",
                    startOffset = 0,
                    endOffset = 11
                )
            )

            cache.save(book, settings, viewport, pages)

            assertNull(cache.load(book, settings, viewport.copy(widthPx = 901)))
            assertNull(
                cache.load(
                    book.copy(
                        chapters = book.chapters.map { chapter ->
                            chapter.copy(plainText = chapter.plainText + " Changed.")
                        }
                    ),
                    settings,
                    viewport
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `chapter page cache round trips one measured chapter`() = runBlocking {
        val root = Files.createTempDirectory("reader-page-cache").toFile()
        try {
            val cache = SharedEpubPaginationCache(root)
            val book = cacheBook().copy(
                chapters = listOf(
                    cacheBook().chapters.first(),
                    cacheBook().chapters.first().copy(id = "chapter-2", title = "Two", plainText = "Second chapter.")
                )
            )
            val settings = ReaderSettings()
            val viewport = ReaderViewportSpec(widthPx = 960, heightPx = 720)
            val pages = listOf(
                ReaderPage(
                    pageIndex = 0,
                    chapterIndex = 0,
                    chapterTitle = "One",
                    text = "First cached page",
                    startOffset = 0,
                    endOffset = 17
                ),
                ReaderPage(
                    pageIndex = 1,
                    chapterIndex = 1,
                    chapterTitle = "Two",
                    text = "Second cached page",
                    startOffset = 0,
                    endOffset = 18
                )
            )

            cache.save(book, settings, viewport, pages)
            val loadedChapter = cache.loadChapter(book, settings, viewport, chapterIndex = 1)

            assertNotNull(loadedChapter)
            assertEquals(1, loadedChapter.size)
            assertEquals(1, loadedChapter.first().pageIndex)
            assertEquals(1, loadedChapter.first().chapterIndex)
            assertEquals("Second cached page", loadedChapter.first().text)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `pagination cache key changes for spread mode`() {
        val root = Files.createTempDirectory("reader-page-cache").toFile()
        try {
            val cache = SharedEpubPaginationCache(root)
            val book = cacheBook()
            val viewport = ReaderViewportSpec(widthPx = 960, heightPx = 720)
            val single = cache.keyFor(book, ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.SINGLE), viewport)
            val spread = cache.keyFor(book, ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE), viewport)

            assertFalse(single.configHash == spread.configHash)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `page cache ignores semanticless pages for semantic books`() = runBlocking {
        val root = Files.createTempDirectory("reader-page-cache").toFile()
        try {
            val cache = SharedEpubPaginationCache(root)
            val book = cacheBook().copy(
                chapters = listOf(
                    cacheBook().chapters.first().copy(
                        semanticBlocks = listOf(
                            SemanticParagraph(
                                text = "Cached page content.",
                                spans = emptyList(),
                                style = CssStyle(fontSize = 22.sp),
                                elementId = null,
                                cfi = null,
                                startCharOffsetInSource = 0,
                                blockIndex = 0
                            )
                        )
                    )
                )
            )
            val settings = ReaderSettings()
            val viewport = ReaderViewportSpec(widthPx = 960, heightPx = 720)
            val pages = listOf(
                ReaderPage(
                    pageIndex = 0,
                    chapterIndex = 0,
                    chapterTitle = "One",
                    text = "Cached page",
                    startOffset = 0,
                    endOffset = 11
                )
            )

            cache.save(book, settings, viewport, pages)

            assertNull(cache.load(book, settings, viewport))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `clear all removes persisted and memory pagination pages`() = runBlocking {
        val root = Files.createTempDirectory("reader-page-cache").toFile()
        try {
            val cache = SharedEpubPaginationCache(root)
            val book = cacheBook()
            val settings = ReaderSettings()
            val viewport = ReaderViewportSpec(widthPx = 960, heightPx = 720)
            val pages = listOf(
                ReaderPage(
                    pageIndex = 0,
                    chapterIndex = 0,
                    chapterTitle = "One",
                    text = "Cached page",
                    startOffset = 0,
                    endOffset = 11
                )
            )

            cache.save(book, settings, viewport, pages)
            assertNotNull(cache.load(book, settings, viewport))

            cache.clearAll()

            assertNull(cache.load(book, settings, viewport))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun cacheBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "book-id",
            fileName = "book.epub",
            title = "Book",
            author = "Author",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter-1",
                    title = "One",
                    plainText = "Cached page content.",
                    htmlContent = "<p>Cached page content.</p>",
                    baseHref = "one.xhtml"
                )
            )
        )
    }
}
