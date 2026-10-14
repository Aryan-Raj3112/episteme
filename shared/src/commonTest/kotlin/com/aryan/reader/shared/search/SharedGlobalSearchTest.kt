package com.aryan.reader.shared.search

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.reader.ReaderLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun chunk(text: String, chapter: Int? = 0, page: Int? = null) = SharedSearchChunk(
    chunkIndex = 0,
    chapterIndex = chapter,
    pageIndex = page,
    text = text,
)

private fun contentResult(bookId: String, score: Double = 1.0) = SharedGlobalSearchBookResult(
    bookId = bookId,
    score = score,
    matchingChunkCount = 1,
    hits = listOf(
        SharedGlobalSearchHit(
            bookId = bookId,
            chunkIndex = 0,
            locator = ReaderLocator(chapterIndex = 0),
            snippet = "snippet",
            matchRanges = emptyList(),
            score = score,
            chapterIndex = 0,
            pageIndex = null,
        )
    ),
)

class SharedGlobalSearchTokenizerTest {

    @Test
    fun `latin tokenizes lowercased words`() {
        assertEquals(listOf("hello", "world"), SharedGlobalSearchTokenizer.tokenize("Hello, WORLD!"))
    }

    @Test
    fun `diacritics fold without java Normalizer`() {
        assertEquals(listOf("cafe", "naive"), SharedGlobalSearchTokenizer.tokenize("café naïve"))
    }

    @Test
    fun `cjk emits single characters`() {
        // "hello" + "world" in Chinese: latin words plus per-char CJK tokens.
        val tokens = SharedGlobalSearchTokenizer.tokenize("hello世界")
        assertTrue("hello" in tokens)
        assertTrue("世" in tokens)
        assertTrue("界" in tokens)
    }

    @Test
    fun `mixed latin and cjk`() {
        val tokens = SharedGlobalSearchTokenizer.tokenize("history历史")
        assertTrue("history" in tokens)
        assertTrue("历" in tokens)
    }

    @Test
    fun `query parse splits phrases and prefix`() {
        val q = parseSharedSearchQuery("history \"exact phrase\"")
        assertTrue("history" in q.terms)
        assertEquals(listOf("exact phrase"), q.phrases)
        // Multi-term with phrases: no prefix expansion.
        assertEquals(null, q.prefixTerm)
    }

    @Test
    fun `single partial word becomes prefix`() {
        val q = parseSharedSearchQuery("hist")
        assertEquals(listOf("hist"), q.terms)
        assertEquals("hist", q.prefixTerm)
    }
}

class SharedGlobalSearchIndexTest {

    @Test
    fun `and requires all terms in one chunk`() {
        val index = SharedGlobalSearchIndex.build(
            "b1", "h1",
            listOf(chunk("the quick brown fox"), chunk("quick rabbits")),
        )
        val hits = index.search(parseSharedSearchQuery("quick fox"))
        assertEquals(1, hits.size)
        assertEquals(0, hits.single().chunkIndex)
    }

    @Test
    fun `prefix matches word starts`() {
        val index = SharedGlobalSearchIndex.build("b1", "h1", listOf(chunk("history of rome")))
        val hits = index.search(parseSharedSearchQuery("hist"))
        assertEquals(1, hits.size)
    }

    @Test
    fun `phrase requires exact substring`() {
        val index = SharedGlobalSearchIndex.build(
            "b1", "h1",
            listOf(chunk("the quick brown fox"), chunk("quick fox jumps")),
        )
        val hits = index.search(parseSharedSearchQuery("\"quick brown\""))
        assertEquals(1, hits.size)
        assertEquals(0, hits.single().chunkIndex)
    }

    @Test
    fun `rare term outranks common term`() {
        val index = SharedGlobalSearchIndex.build(
            "b1", "h1",
            listOf(
                chunk("magic magic magic common"),
                chunk("common common common"),
                chunk("common common common"),
            ),
        )
        val hits = index.search(parseSharedSearchQuery("magic common"))
        assertTrue(hits.isNotEmpty())
        assertEquals(0, hits.first().chunkIndex)
    }

    @Test
    fun `snippet carries match range`() {
        val text = "In the beginning there was history and more words after it to fill context."
        val (snippet, ranges) = SharedGlobalSearchSnippet.build(text, parseSharedSearchQuery("history"))
        assertTrue(snippet.contains("history", ignoreCase = true))
        assertEquals(1, ranges.size)
        val range = ranges.single()
        assertEquals("history", snippet.substring(range.first, range.last + 1).lowercase())
    }

    @Test
    fun `snippet highlights every query term and not just the winning one`() {
        val text = "a history of the roman window opening onto the old forum"
        val (snippet, ranges) = SharedGlobalSearchSnippet.build(text, parseSharedSearchQuery("roman history"))
        assertTrue("roman" in snippet.lowercase())
        val covered = ranges.map { snippet.substring(it.first, it.last + 1).lowercase() }
        assertTrue("history" in covered)
        assertTrue("roman" in covered)
        // No overlap: highlights must be disjoint spans for an annotated string.
        ranges.zipWithNext { current, next ->
            assertTrue(current.last < next.first)
        }
    }

    @Test
    fun `snippet extends a typed prefix to the whole word`() {
        val text = "a history of the roman forum and other windows"
        val (snippet, ranges) = SharedGlobalSearchSnippet.build(text, parseSharedSearchQuery("windo"))
        val covered = ranges.map { snippet.substring(it.first, it.last + 1).lowercase() }
        assertTrue("windows" in covered)
    }

    @Test
    fun `snippet merge keeps overlapping highlights valid`() {
        // "history of" and "history" both match the same word start.
        val text = "the history of rome and the history of paris and more text here"
        val (snippet, ranges) = SharedGlobalSearchSnippet.build(
            text,
            parseSharedSearchQuery("\"history of\" history"),
        )
        assertTrue(ranges.isNotEmpty())
        ranges.forEach { range ->
            assertTrue(range.first >= 0 && range.last < snippet.length)
        }
        ranges.zipWithNext { current, next ->
            assertTrue(current.last < next.first)
        }
    }

    @Test
    fun `persisted round trip preserves search`() {
        val index = SharedGlobalSearchIndex.build(
            "b1", "h1",
            listOf(
                SharedSearchChunk(0, chapterIndex = 2, blockIndex = 5, charOffset = 10, text = "hello world"),
            ),
        )
        val restored = SharedGlobalSearchIndex.fromPersisted(index.toPersisted())
        val hits = restored.search(parseSharedSearchQuery("hello"))
        assertEquals(1, hits.size)
        assertEquals(2, hits.single().chapterIndex)
        assertEquals(2, hits.single().locator.chapterIndex)
    }
}

class SharedGlobalSearchPolicyTest {

    @Test
    fun `comics and audiobooks are not searchable`() {
        assertFalse(SharedGlobalSearchPolicy.isContentSearchable(FileType.CBZ))
        assertFalse(SharedGlobalSearchPolicy.isContentSearchable(FileType.AUDIOBOOK))
        assertFalse(SharedGlobalSearchPolicy.isContentSearchable(FileType.UNKNOWN))
        assertTrue(SharedGlobalSearchPolicy.isContentSearchable(FileType.EPUB))
        assertTrue(SharedGlobalSearchPolicy.isContentSearchable(FileType.PDF))
        assertTrue(SharedGlobalSearchPolicy.isContentSearchable(FileType.MD))
    }
}

class SharedGlobalSearchCoordinatorTest {

    @Test
    fun `same hash skips reindex and delete clears`() {
        val coordinator = SharedGlobalSearchCoordinator()
        assertTrue(coordinator.needsIndex("b1", "h1"))
        coordinator.upsertBookChunks("b1", "h1", listOf(chunk("hello world")))
        assertFalse(coordinator.needsIndex("b1", "h1"))
        assertTrue(coordinator.needsIndex("b1", "h2"))
        assertEquals(1, coordinator.search("hello").size)
        coordinator.removeBook("b1")
        assertTrue(coordinator.search("hello").isEmpty())
    }

    @Test
    fun `empty chunks report no native text`() {
        val coordinator = SharedGlobalSearchCoordinator()
        assertFalse(coordinator.upsertBookChunks("scanned", "h1", emptyList()))
        assertTrue(coordinator.search("anything").isEmpty())
    }

    @Test
    fun `empty upsert persists tombstone so needsIndex goes quiet`() {
        val coordinator = SharedGlobalSearchCoordinator()
        assertTrue(coordinator.needsIndex("scanned", "h1"))
        coordinator.upsertBookChunks("scanned", "h1", emptyList())
        assertFalse(coordinator.needsIndex("scanned", "h1"))
        assertTrue(coordinator.needsIndex("scanned", "h2"))
    }
}

class SharedGlobalSearchLibraryTest {

    private fun book(id: String, title: String = id) = BookItem(
        id = id,
        path = "/library/$id.epub",
        type = FileType.EPUB,
        displayName = "$title.epub",
        timestamp = 1L,
        title = title,
    )

    @Test
    fun `toggle off keeps metadata only`() {
        val books = listOf(book("a", "Alpha"), book("b", "Beta"))
        val rows = searchLibraryWithContent(
            books = books,
            query = "alpha",
            searchInsideFiles = false,
            metadataMatches = listOf(books[0]),
            contentResults = listOf(
                SharedGlobalSearchBookResult("b", 9.0, 1, emptyList()),
            ),
        )
        assertEquals(listOf("a"), rows.map { it.book.id })
        assertTrue(rows.single().metadataMatched)
    }

    @Test
    fun `toggle on appends content-only books by score`() {
        val books = listOf(book("a", "Alpha"), book("b", "Beta"), book("c", "Gamma"))
        val rows = searchLibraryWithContent(
            books = books,
            query = "history",
            searchInsideFiles = true,
            metadataMatches = listOf(books[0]),
            contentResults = listOf(
                SharedGlobalSearchBookResult("c", 2.0, 1, emptyList()),
                SharedGlobalSearchBookResult("b", 9.0, 1, emptyList()),
            ),
        )
        assertEquals(listOf("a", "b", "c"), rows.map { it.book.id })
        assertTrue(rows[0].metadataMatched)
        assertFalse(rows[1].metadataMatched)
    }

    @Test
    fun `projector ignores content results when toggle is off`() {
        val books = listOf(book("a", "Alpha"), book("b", "Beta"))
        val base = com.aryan.reader.shared.SharedLibraryProjectionInput(
            state = com.aryan.reader.shared.LibraryFeatureState(searchQuery = "alpha"),
            booksFromStore = books,
            shelfRecords = emptyList(),
            shelfRefs = emptyList(),
            tags = emptyList(),
            contentSearchResults = listOf(contentResult("b")),
        )
        val off = com.aryan.reader.shared.SharedLibraryStateProjector().project(base)
        assertEquals(listOf("a"), off.libraryBooks.map { it.id })
        val on = com.aryan.reader.shared.SharedLibraryStateProjector().project(
            base.copy(state = base.state.copy(searchQuery = "alpha", searchInsideFiles = true)),
        )
        assertEquals(setOf("a", "b"), on.libraryBooks.map { it.id }.toSet())
    }
}
