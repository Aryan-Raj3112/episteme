package com.aryan.reader.shared.reader

import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.FileType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedJvmBookLoadCacheTest {

    @Test
    fun `book load cache round trips parsed shared book`() {
        val root = Files.createTempDirectory("reader-book-load-cache").toFile()
        try {
            val cache = SharedBookLoadCache(JvmSharedBookLoadCacheStorage(root))
            val key = SharedBookLoadCacheKey(
                canonicalPath = "C:/Books/book.epub",
                type = FileType.EPUB,
                length = 1234L,
                lastModified = 5678L
            )
            val book = SharedEpubBook(
                id = "C:/Books/book.epub",
                fileName = "book.epub",
                title = "Cached Book",
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

            cache.save(key, book)
            val loaded = cache.load(key)

            assertNotNull(loaded)
            assertEquals(book.title, loaded.title)
            assertEquals(book.css, loaded.css)
            assertEquals("Hello cache.", loaded.chapters.single().plainText)
            assertEquals("opening", loaded.chapters.single().fragmentId)
            assertEquals(2, loaded.chapters.single().depth)
            assertEquals(false, loaded.chapters.single().isInToc)
            assertEquals(book.pageList, loaded.pageList)
            assertEquals("fr", loaded.language)
            assertEquals("Series", loaded.seriesName)
            assertEquals(2.5, loaded.seriesIndex)
            assertEquals("Description", loaded.description)
            assertEquals(book.images, loaded.images)
            assertEquals("images/cover.png", loaded.coverImagePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `book load cache rejects styled reader books without semantic blocks`() {
        val root = Files.createTempDirectory("reader-book-load-cache").toFile()
        try {
            val cache = SharedBookLoadCache(JvmSharedBookLoadCacheStorage(root))
            val key = SharedBookLoadCacheKey(
                canonicalPath = "C:/Books/book.epub",
                type = FileType.EPUB,
                length = 1234L,
                lastModified = 5678L
            )
            val book = SharedEpubBook(
                id = "C:/Books/book.epub",
                fileName = "book.epub",
                title = "Cached Book",
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

            cache.save(key, book)

            assertNull(cache.load(key))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `book load cache misses when source fingerprint changes`() {
        val root = Files.createTempDirectory("reader-book-load-cache").toFile()
        try {
            val cache = SharedBookLoadCache(JvmSharedBookLoadCacheStorage(root))
            val key = SharedBookLoadCacheKey(
                canonicalPath = "C:/Books/book.epub",
                type = FileType.EPUB,
                length = 1234L,
                lastModified = 5678L
            )
            val book = SharedEpubBook(
                id = "C:/Books/book.epub",
                fileName = "book.epub",
                title = "Cached Book",
                chapters = listOf(SharedEpubChapter("one", "One", "Hello cache."))
            )

            cache.save(key, book)

            assertNull(cache.load(key.copy(lastModified = 5679L)))
            assertNull(cache.load(key.copy(length = 1235L)))
        } finally {
            root.deleteRecursively()
        }
    }
}
