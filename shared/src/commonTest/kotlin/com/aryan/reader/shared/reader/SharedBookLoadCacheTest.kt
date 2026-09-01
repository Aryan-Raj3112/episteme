package com.aryan.reader.shared.reader

import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.FileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedBookLoadCacheTest {

    private fun cache(): Pair<SharedBookLoadCache, InMemorySharedBookLoadCacheStorage> {
        val storage = InMemorySharedBookLoadCacheStorage()
        return SharedBookLoadCache(storage) to storage
    }

    private fun key(
        type: FileType = FileType.EPUB,
        length: Long = 1234L,
        lastModified: Long = 5678L,
        semanticMode: SharedBookLoadSemanticMode = SharedBookLoadSemanticMode.FULL
    ): SharedBookLoadCacheKey {
        return SharedBookLoadCacheKey(
            canonicalPath = "C:/Books/book.epub",
            type = type,
            length = length,
            lastModified = lastModified,
            semanticMode = semanticMode
        )
    }

    private fun book(title: String = "Cached Book"): SharedEpubBook {
        return SharedEpubBook(
            id = "C:/Books/book.epub",
            fileName = "book.epub",
            title = title,
            author = "Author",
            language = "fr",
            seriesName = "Series",
            seriesIndex = 2.5,
            description = "Description",
            css = mapOf("style.css" to "p { margin: 0; }"),
            pageList = listOf(MobileEpubPageTarget("p1", "1", "One", "one.xhtml#p1")),
            images = listOf(MobileEpubImage("images/cover.png")),
            coverImagePath = "images/cover.png",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = "Hello cache.",
                    htmlContent = "<p>Hello cache.</p>",
                    semanticBlocks = listOf(
                        SemanticParagraph(
                            text = "Hello cache.",
                            spans = emptyList(),
                            style = CssStyle(fontSize = 18.sp),
                            elementId = null,
                            cfi = null,
                            startCharOffsetInSource = 0,
                            blockIndex = 0
                        )
                    ),
                    baseHref = "one.xhtml",
                    fragmentId = "opening",
                    depth = 2,
                    isInToc = false
                )
            )
        )
    }

    @Test
    fun `book load cache round trips parsed shared book`() {
        val (cache, storage) = cache()
        val cacheKey = key()

        cache.save(cacheKey, book())
        val loaded = cache.load(cacheKey)

        assertNotNull(loaded)
        assertEquals(1, storage.entries.size)
        assertEquals("Cached Book", loaded.title)
        assertEquals(mapOf("style.css" to "p { margin: 0; }"), loaded.css)
        assertEquals("Hello cache.", loaded.chapters.single().plainText)
        assertEquals("opening", loaded.chapters.single().fragmentId)
        assertEquals(2, loaded.chapters.single().depth)
        assertEquals(false, loaded.chapters.single().isInToc)
        assertEquals(listOf(MobileEpubPageTarget("p1", "1", "One", "one.xhtml#p1")), loaded.pageList)
        assertEquals("fr", loaded.language)
        assertEquals("Series", loaded.seriesName)
        assertEquals(2.5, loaded.seriesIndex)
        assertEquals("Description", loaded.description)
        assertEquals(listOf(MobileEpubImage("images/cover.png")), loaded.images)
        assertEquals("images/cover.png", loaded.coverImagePath)
    }

    @Test
    fun `book load cache misses when source fingerprint changes`() {
        val (cache, _) = cache()
        val cacheKey = key()

        cache.save(cacheKey, book())

        assertNull(cache.load(cacheKey.copy(length = 1235L)))
        assertNull(cache.load(cacheKey.copy(lastModified = 5679L)))
        assertNull(cache.load(cacheKey.copy(type = FileType.MOBI)))
        assertNull(cache.load(cacheKey.copy(semanticMode = SharedBookLoadSemanticMode.SKIP)))
    }

    @Test
    fun `book load cache rejects styled reader books without semantic blocks`() {
        val (cache, _) = cache()
        val cacheKey = key()
        val styledBook = book().copy(
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = "Hello cache.",
                    htmlContent = "<p>Hello cache.</p>",
                    baseHref = "one.xhtml"
                )
            )
        )

        cache.save(cacheKey, styledBook)

        assertNull(cache.load(cacheKey))
    }

    @Test
    fun `book load cache allows plain text books without semantic blocks`() {
        val (cache, _) = cache()
        val cacheKey = key(type = FileType.TXT)
        val plainBook = book().copy(
            chapters = listOf(
                SharedEpubChapter(id = "one", title = "One", plainText = "Hello cache.")
            )
        )

        cache.save(cacheKey, plainBook)

        assertNotNull(cache.load(cacheKey))
    }

    @Test
    fun `book load cache id is deterministic and fingerprint aware`() {
        val cacheKey = key()

        assertEquals(cacheKey.cacheId, key().cacheId)
        assertNotEquals(cacheKey.cacheId, key(length = 1235L).cacheId)
        assertNotEquals(cacheKey.cacheId, key(lastModified = 5679L).cacheId)
        assertNotEquals(cacheKey.cacheId, key(type = FileType.MOBI).cacheId)
    }
}

internal class InMemorySharedBookLoadCacheStorage : SharedBookLoadCacheStorage {
    val entries = mutableMapOf<String, ByteArray>()

    override fun read(cacheId: String): ByteArray? = entries[cacheId]

    override fun write(cacheId: String, bytes: ByteArray): Boolean {
        entries[cacheId] = bytes
        return true
    }

    override fun cleanupOldEntries() = Unit

    override fun clear() {
        entries.clear()
    }
}