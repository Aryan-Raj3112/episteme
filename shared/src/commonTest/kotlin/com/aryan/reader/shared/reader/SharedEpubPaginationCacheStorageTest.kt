package com.aryan.reader.shared.reader

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemorySharedEpubPageCacheStorage : SharedEpubPageCacheStorage {
    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf<String>()
    private val modifiedMillis = mutableMapOf<String, Long>()
    private var tick = 0L

    override fun exists(path: String): Boolean = files.containsKey(path) || directories.contains(path)

    override fun isDirectory(path: String): Boolean = directories.contains(path)

    override fun readBytes(path: String): ByteArray? = files[path]

    override fun writeBytesAtomically(path: String, bytes: ByteArray) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) directories += parent
        files[path] = bytes.copyOf()
        modifiedMillis[path] = ++tick
    }

    override fun deleteFile(path: String) {
        files.remove(path)
        modifiedMillis.remove(path)
    }

    override fun deleteDirectory(path: String) {
        files.keys.filter { it == path || it.startsWith("$path/") }.forEach { file ->
            files.remove(file)
            modifiedMillis.remove(file)
        }
        directories.remove(path)
    }

    override fun deleteAll() {
        files.clear()
        directories.clear()
        modifiedMillis.clear()
    }

    override fun listFileNames(path: String): List<String> {
        val prefix = if (path.isBlank()) "" else "$path/"
        return directories.filter { it.startsWith(prefix) && it.removePrefix(prefix).contains('/') == false }
            .map { it.removePrefix(prefix) } +
            files.keys.filter { it.startsWith(prefix) && it.removePrefix(prefix).contains('/') == false }
                .map { it.removePrefix(prefix) }
    }

    override fun lastModifiedMillis(path: String): Long = modifiedMillis[path] ?: 0L

    override fun rootLabel(): String = "in-memory"
}

class SharedEpubPaginationCacheStorageTest {

    @Test
    fun `page cache round trips measured pages through portable storage`() = runBlocking<Unit> {
        val storage = InMemorySharedEpubPageCacheStorage()
        val cache = SharedEpubPaginationCache(storage)
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
    }

    @Test
    fun `page cache misses when viewport or chapter content changes`() = runBlocking<Unit> {
        val storage = InMemorySharedEpubPageCacheStorage()
        val cache = SharedEpubPaginationCache(storage)
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
        assertNotNull(cache.load(book, settings, viewport))
    }

    @Test
    fun `chapter cache round trips with global page indexes`() = runBlocking<Unit> {
        val storage = InMemorySharedEpubPageCacheStorage()
        val cache = SharedEpubPaginationCache(storage)
        val book = cacheBook()
        val settings = ReaderSettings()
        val viewport = ReaderViewportSpec(widthPx = 900, heightPx = 700)
        val pages = listOf(
            ReaderPage(
                pageIndex = 7,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "Chapter page one",
                startOffset = 0,
                endOffset = 10
            ),
            ReaderPage(
                pageIndex = 8,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "Chapter page two",
                startOffset = 11,
                endOffset = 20
            )
        )

        cache.saveChapter(book, settings, viewport, chapterIndex = 0, pages = pages, firstPageIndex = 7)
        val loaded = cache.loadChapter(book, settings, viewport, chapterIndex = 0)

        assertNotNull(loaded)
        assertEquals(listOf(7, 8), loaded.map { it.pageIndex })
        assertEquals("Chapter page one", loaded.first().text)
    }

    @Test
    fun `clearAll wipes disk and memory caches`() = runBlocking<Unit> {
        val storage = InMemorySharedEpubPageCacheStorage()
        val cache = SharedEpubPaginationCache(storage)
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
        assertNotNull(cache.loadMemory(book, settings, viewport))
        cache.clearAll()

        assertNull(cache.load(book, settings, viewport))
        assertNull(cache.loadMemory(book, settings, viewport))
    }

    @Test
    fun `cache keeps newest three configurations per book`() = runBlocking<Unit> {
        val storage = InMemorySharedEpubPageCacheStorage()
        val cache = SharedEpubPaginationCache(storage)
        val book = cacheBook()
        val settings = ReaderSettings()
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
        val viewports = listOf(
            ReaderViewportSpec(widthPx = 900, heightPx = 700),
            ReaderViewportSpec(widthPx = 901, heightPx = 700),
            ReaderViewportSpec(widthPx = 902, heightPx = 700),
            ReaderViewportSpec(widthPx = 903, heightPx = 700)
        )

        viewports.take(3).forEach { viewport ->
            cache.save(book, settings, viewport, pages)
        }
        cache.save(book, settings, viewports.last(), pages)
        val reader = SharedEpubPaginationCache(storage)

        assertNull(reader.load(book, settings, viewports.first()))
        assertNotNull(reader.load(book, settings, viewports.last()))
    }

    @Test
    fun `shared sha256 hex is stable lowercase hex`() {
        val hash = sharedSha256Hex("book-id|book.epub")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(hash, sharedSha256Hex("book-id|book.epub"))
    }

    @Test
    fun `hide images toggle changes the cache configuration hash`() {
        val storage = InMemorySharedEpubPageCacheStorage()
        val cache = SharedEpubPaginationCache(storage)
        val book = cacheBook()
        val settings = ReaderSettings()
        val viewport = ReaderViewportSpec(widthPx = 900, heightPx = 700)

        val visibleKey = cache.keyFor(book, settings, viewport)
        val hiddenKey = cache.keyFor(book, settings.copy(hideImages = true), viewport)

        assertTrue(hiddenKey.configHash != visibleKey.configHash)
        assertTrue(hiddenKey.cacheId != visibleKey.cacheId)
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