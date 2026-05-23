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
            val cache = SharedJvmBookLoadCache(root)
            val key = SharedJvmBookLoadCacheKey(
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
                css = mapOf("style.css" to "p { margin: 0; }"),
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
                        baseHref = "one.xhtml"
                    )
                )
            )

            cache.save(key, book)
            val loaded = cache.load(key)

            assertNotNull(loaded)
            assertEquals(book.title, loaded.title)
            assertEquals(book.css, loaded.css)
            assertEquals("Hello cache.", loaded.chapters.single().plainText)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `book load cache rejects styled reader books without semantic blocks`() {
        val root = Files.createTempDirectory("reader-book-load-cache").toFile()
        try {
            val cache = SharedJvmBookLoadCache(root)
            val key = SharedJvmBookLoadCacheKey(
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
            val cache = SharedJvmBookLoadCache(root)
            val key = SharedJvmBookLoadCacheKey(
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
