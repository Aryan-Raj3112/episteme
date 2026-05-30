package com.aryan.reader.epubreader

import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.RenderMode
import com.aryan.reader.SearchResult
import com.aryan.reader.SearchState
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.paginatedreader.Page
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EpubReaderSearchTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `search scans existing chapter files case-insensitively and skips missing chapters`() = runTest {
        val root = temp.newFolder("book")
        writeChapter(root, "chapter1.xhtml", "<html><body><p>Alpha needle.</p></body></html>")
        writeChapter(root, "chapter2.xhtml", "<html><body><p>Needle one.</p><p>needle two.</p></body></html>")
        val book = epubBook(
            root = root,
            chapters = listOf(
                chapter("ch1", "One", "chapter1.xhtml"),
                chapter("missing", "Missing", "missing.xhtml"),
                chapter("ch2", "Two", "chapter2.xhtml")
            )
        )

        val results = createEpubSearcher(book)("NEEDLE")

        assertEquals(listOf("One", "Two", "Two"), results.map { it.locationTitle })
        assertEquals(listOf(0, 2, 2), results.map { it.locationInSource })
        assertEquals(listOf(0, 0, 1), results.map { it.occurrenceIndexInLocation })
        assertTrue(results.all { it.query == "NEEDLE" })
    }

    @Test
    fun `search records chunk index after body children are chunked in groups of twenty`() = runTest {
        val root = temp.newFolder("chunked")
        val paragraphs = (1..25).joinToString("") { index ->
            if (index == 22) "<p>late target appears here</p>" else "<p>filler $index</p>"
        }
        writeChapter(root, "chapter.xhtml", "<html><body>$paragraphs</body></html>")
        val book = epubBook(root, listOf(chapter("ch1", "Chunky", "chapter.xhtml")))

        val result = createEpubSearcher(book)("target").single()

        assertEquals(1, result.chunkIndex)
        assertEquals(0, result.occurrenceIndexInLocation)
        assertEquals("Chunky", result.locationTitle)
    }

    @Test
    fun `search scans oversized text nodes in bounded windows`() = runTest {
        val root = temp.newFolder("bounded-window")
        val filler = "alpha ".repeat(7_000)
        writeChapter(
            root,
            "chapter.xhtml",
            "<html><body><p>${filler}Needle ${filler}pineedle ${filler}Needle</p></body></html>"
        )
        val book = epubBook(root, listOf(chapter("ch1", "Large", "chapter.xhtml")))

        val results = createEpubSearcher(book)("needle")

        assertEquals(2, results.size)
        assertEquals(listOf(0, 1), results.map { it.occurrenceIndexInLocation })
        assertTrue(results.all { it.snippet.text.contains("Needle", ignoreCase = true) })
    }

    @Test
    fun `search currently requires only a word start and highlights the matched substring`() = runTest {
        val root = temp.newFolder("word-start")
        writeChapter(root, "chapter.xhtml", "<html><body><p>cart art artist</p></body></html>")
        val book = epubBook(root, listOf(chapter("ch1", "Words", "chapter.xhtml")))

        val results = createEpubSearcher(book)("art")

        assertEquals(2, results.size)
        assertEquals(listOf("art", "art"), results.map { result ->
            val style = result.snippet.spanStyles.single()
            result.snippet.substring(style.start, style.end)
        })
    }

    @Test
    fun `vertical navigation changes chapter when needed and scrolls in place when chunk is loaded`() {
        val result = searchResult(chapter = 1, chunk = 2, occurrence = 3)
        val searchState = SearchState(CoroutineScope(UnconfinedTestDispatcher())) { emptyList() }.apply {
            searchResults = listOf(result)
        }
        val webView = mockk<android.webkit.WebView>(relaxed = true)
        val chapterChanges = mutableListOf<Triple<Int, Int, SearchResult>>()
        val inPlaceScrolls = mutableListOf<SearchResult>()

        performSearchResultNavigation(
            index = 0,
            searchState = searchState,
            renderMode = RenderMode.VERTICAL_SCROLL,
            currentChapterIndex = 0,
            loadedChunkCount = 10,
            webView = webView,
            paginator = null,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
            onVerticalChapterChange = { chapterIndex, chunkIndex, navResult ->
                chapterChanges.add(Triple(chapterIndex, chunkIndex, navResult))
            },
            onVerticalScrollToResult = { inPlaceScrolls.add(it) },
            onPaginatedScrollToPage = {}
        )

        assertEquals(listOf(Triple(1, 2, result)), chapterChanges)
        assertTrue(inPlaceScrolls.isEmpty())
        assertEquals(0, searchState.currentSearchResultIndex)

        performSearchResultNavigation(
            index = 0,
            searchState = searchState,
            renderMode = RenderMode.VERTICAL_SCROLL,
            currentChapterIndex = 1,
            loadedChunkCount = 3,
            webView = webView,
            paginator = null,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
            onVerticalChapterChange = { chapterIndex, chunkIndex, navResult ->
                chapterChanges.add(Triple(chapterIndex, chunkIndex, navResult))
            },
            onVerticalScrollToResult = { inPlaceScrolls.add(it) },
            onPaginatedScrollToPage = {}
        )

        assertEquals(listOf(result), inPlaceScrolls)
        verify { webView.evaluateJavascript("javascript:window.scrollToOccurrence(3);", null) }
    }

    @Test
    fun `vertical navigation reloads same chapter when target chunk has not been loaded`() {
        val result = searchResult(chapter = 0, chunk = 5, occurrence = 0)
        val searchState = SearchState(CoroutineScope(UnconfinedTestDispatcher())) { emptyList() }.apply {
            searchResults = listOf(result)
        }
        val chapterChanges = mutableListOf<Pair<Int, Int>>()

        performSearchResultNavigation(
            index = 0,
            searchState = searchState,
            renderMode = RenderMode.VERTICAL_SCROLL,
            currentChapterIndex = 0,
            loadedChunkCount = 5,
            webView = null,
            paginator = null,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
            onVerticalChapterChange = { chapterIndex, chunkIndex, _ -> chapterChanges.add(chapterIndex to chunkIndex) },
            onVerticalScrollToResult = {},
            onPaginatedScrollToPage = {}
        )

        assertEquals(listOf(0 to 5), chapterChanges)
    }

    @Test
    fun `paginated navigation asks paginator for target page and invokes suspend scroll callback`() = runTest {
        val result = searchResult(chapter = 0, chunk = 0, occurrence = 0)
        val searchState = SearchState(this) { emptyList() }.apply { searchResults = listOf(result) }
        val paginator = FakePaginator(pageForResult = 42)
        val pages = mutableListOf<Int>()

        performSearchResultNavigation(
            index = 0,
            searchState = searchState,
            renderMode = RenderMode.PAGINATED,
            currentChapterIndex = 0,
            loadedChunkCount = 0,
            webView = null,
            paginator = paginator,
            coroutineScope = this,
            onVerticalChapterChange = { _, _, _ -> error("Unexpected vertical navigation") },
            onVerticalScrollToResult = { error("Unexpected vertical scroll") },
            onPaginatedScrollToPage = { pages.add(it) }
        )
        advanceUntilIdle()

        assertEquals(result, paginator.lastSearchResult)
        assertEquals(listOf(42), pages)
    }

    @Test
    fun `navigation ignores out of bounds search result index`() {
        val searchState = SearchState(CoroutineScope(UnconfinedTestDispatcher())) { emptyList() }
        var called = false

        performSearchResultNavigation(
            index = 0,
            searchState = searchState,
            renderMode = RenderMode.VERTICAL_SCROLL,
            currentChapterIndex = 0,
            loadedChunkCount = 0,
            webView = null,
            paginator = null,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
            onVerticalChapterChange = { _, _, _ -> called = true },
            onVerticalScrollToResult = { called = true },
            onPaginatedScrollToPage = { called = true }
        )

        assertTrue(!called)
        assertEquals(-1, searchState.currentSearchResultIndex)
    }

    private fun writeChapter(root: java.io.File, relativePath: String, html: String) {
        val file = java.io.File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(html)
    }

    private fun epubBook(root: java.io.File, chapters: List<EpubChapter>): EpubBook =
        EpubBook(
            fileName = "test.epub",
            title = "Test",
            author = "Author",
            language = "en",
            coverImage = null,
            chapters = chapters,
            extractionBasePath = root.absolutePath
        )

    private fun chapter(id: String, title: String, path: String): EpubChapter =
        EpubChapter(
            chapterId = id,
            absPath = path,
            title = title,
            htmlFilePath = path,
            plainTextContent = "",
            htmlContent = ""
        )

    private fun searchResult(chapter: Int, chunk: Int, occurrence: Int): SearchResult =
        SearchResult(
            locationInSource = chapter,
            locationTitle = "Chapter $chapter",
            snippet = buildAnnotatedString { append("snippet") },
            query = "needle",
            occurrenceIndexInLocation = occurrence,
            chunkIndex = chunk
        )

    private class FakePaginator(private val pageForResult: Int) : IPaginator {
        var lastSearchResult: SearchResult? = null
        override val totalPageCount: Int = 0
        override val isLoading: Boolean = false
        override val generation: Int = 0
        override val pageShiftRequest: Flow<Int> = emptyFlow()
        override fun getPageContent(pageIndex: Int): Page? = null
        override fun getChapterPathForPage(pageIndex: Int): String? = null
        override fun getPlainTextForChapter(chapterIndex: Int): String? = null
        override fun navigateToHref(currentChapterAbsPath: String, href: String, onNavigationComplete: (pageIndex: Int) -> Unit) = Unit
        override fun findPageForSearchResult(result: SearchResult, onResult: (pageIndex: Int) -> Unit) {
            lastSearchResult = result
            onResult(pageForResult)
        }
        override fun findPageForAnchor(chapterIndex: Int, anchor: String?, onResult: (pageIndex: Int) -> Unit) = Unit
        override fun findPageForCfi(chapterIndex: Int, cfi: String, onResult: (pageIndex: Int) -> Unit) = Unit
        override fun findPageForCfiAndOffset(chapterIndex: Int, cfi: String, charOffset: Int): Int? = null
        override fun findChapterIndexForPage(pageIndex: Int): Int? = null
        override fun getCfiForPage(pageIndex: Int): String? = null
        override fun onUserScrolledTo(pageIndex: Int) = Unit
        override fun getActiveAnchorForPage(pageIndex: Int, tocAnchors: List<String>): String? = null
    }
}
