package com.aryan.reader.paginatedreader

import android.content.Context
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.data.AnchorIndexEntry
import com.aryan.reader.paginatedreader.data.BookCacheDao
import com.aryan.reader.paginatedreader.data.ConfigurationCache
import com.aryan.reader.paginatedreader.data.PageCacheChunk
import com.aryan.reader.paginatedreader.data.PageCacheMetadata
import com.aryan.reader.paginatedreader.data.PageIndexEntry
import com.aryan.reader.paginatedreader.data.ProcessedBook
import com.aryan.reader.paginatedreader.data.ProcessedChapter
import com.aryan.reader.paginatedreader.data.ProcessedChapterChunk
import com.aryan.reader.paginatedreader.data.ProcessedChapterMetadata
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class LocatorConverterTest {

    private val proto = ProtoBuf {
        serializersModule = semanticBlockModule
    }

    @Test
    fun `getLocatorFromCfi resolves best cached semantic block and preserves character offset`() = runTest {
        val blocks = semanticBlocks()
        val converter = converterFor(blocks)
        val book = book()

        val locator = converter.getLocatorFromCfi(book, chapterIndex = 0, cfi = "/4/2/6:13")

        assertEquals(Locator(chapterIndex = 0, blockIndex = 2, charOffset = 13), locator)
    }

    @Test
    fun `zero estimate semantic cache remains usable`() = runTest {
        val converter = converterFor(semanticBlocks(), estimatedPageCount = 0)

        val locator = converter.getLocatorFromCfi(book(), chapterIndex = 0, cfi = "/4/2/6:7")

        assertEquals(Locator(chapterIndex = 0, blockIndex = 2, charOffset = 7), locator)
    }

    @Test
    fun `stable book id is used for locator cache lookups`() = runTest {
        val chapter = ProcessedChapter(
            bookId = "stable-book-id",
            chapterIndex = 0,
            contentBlocksProto = proto.encodeToByteArray(semanticBlocks()),
            estimatedPageCount = 1
        )
        val dao = FakeBookCacheDao(chapter)
        val converter = LocatorConverter(dao, proto, mockk<Context>(relaxed = true), stableBookId = "stable-book-id")

        converter.getLocatorFromCfi(book(), chapterIndex = 0, cfi = "/4/2")

        assertEquals("stable-book-id", dao.requestedBookIds.single())
    }

    @Test
    fun `cfi locator cfi round trip preserves exact block and offset across reader modes`() = runTest {
        val converter = converterFor(
            listOf(
                paragraph("Outer text", blockIndex = 10, cfi = "/4/2"),
                paragraph("Nested candidate", blockIndex = 11, cfi = "/4/2/6"),
                paragraph("Deep exact candidate", blockIndex = 12, cfi = "/4/2/6/10")
            )
        )
        val book = book()

        val locator = converter.getLocatorFromCfi(book, chapterIndex = 0, cfi = "/4/2/6/10:19")
        val cfi = locator?.let { converter.getCfiFromLocator(book, it) }

        assertEquals(Locator(chapterIndex = 0, blockIndex = 12, charOffset = 19), locator)
        assertEquals("/4/2/6/10:19", cfi)
    }

    @Test
    fun `zero offset cfi round trip canonicalizes to base path without losing locator`() = runTest {
        val converter = converterFor(semanticBlocks())
        val book = book()

        val locator = converter.getLocatorFromCfi(book, chapterIndex = 0, cfi = "/4/2/6:0")
        val cfi = locator?.let { converter.getCfiFromLocator(book, it) }

        assertEquals(Locator(chapterIndex = 0, blockIndex = 2, charOffset = 0), locator)
        assertEquals("/4/2/6", cfi)
    }

    @Test
    fun `malformed cfi offset is treated as block start and remains restorable`() = runTest {
        val converter = converterFor(semanticBlocks())
        val book = book()

        val locator = converter.getLocatorFromCfi(book, chapterIndex = 0, cfi = "/4/2:not-a-number")
        val cfi = locator?.let { converter.getCfiFromLocator(book, it) }

        assertEquals(Locator(chapterIndex = 0, blockIndex = 1, charOffset = 0), locator)
        assertEquals("/4/2", cfi)
    }

    @Test
    fun `getCfiFromLocator finds nested block and appends positive offset`() = runTest {
        val converter = converterFor(semanticBlocks())

        assertEquals(
            "/6/4:8",
            converter.getCfiFromLocator(book(), Locator(chapterIndex = 0, blockIndex = 3, charOffset = 8))
        )
        assertEquals(
            "/4/2",
            converter.getCfiFromLocator(book(), Locator(chapterIndex = 0, blockIndex = 1, charOffset = 0))
        )
        assertNull(converter.getCfiFromLocator(book(), Locator(chapterIndex = 0, blockIndex = 404, charOffset = 0)))
    }

    @Test
    fun `getTextOffset sums preceding text blocks including nested containers`() = runTest {
        val converter = converterFor(semanticBlocks())

        val offset = converter.getTextOffset(book(), Locator(chapterIndex = 0, blockIndex = 3, charOffset = 4))

        assertEquals("First paragraph".length + 1 + "Second paragraph".length + 1 + 4, offset)
    }

    @Test
    fun `getTtsChunksForChapter traverses cached semantic text blocks with source cfi`() = runTest {
        val converter = converterFor(
            listOf(
                paragraph("First sentence. Second sentence.", blockIndex = 1, cfi = "/4/2", offset = 5),
                SemanticFlexContainer(
                    children = listOf(paragraph("Nested text.", blockIndex = 2, cfi = "/6/2", offset = 40)),
                    style = CssStyle(),
                    elementId = null,
                    cfi = null,
                    blockIndex = 10
                )
            )
        )

        val chunks = converter.getTtsChunksForChapter(book(), chapterIndex = 0)!!

        assertTrue(chunks.isNotEmpty())
        assertEquals("/4/2", chunks.first().sourceCfi)
        assertEquals(5, chunks.first().startOffsetInSource)
        assertTrue(chunks.any { it.text.contains("Nested text") && it.sourceCfi == "/6/2" })
    }

    @Test
    fun `invalid cached proto returns null instead of processing when chapter has no html`() = runTest {
        val dao = FakeBookCacheDao(
            ProcessedChapter(
                bookId = "Book",
                chapterIndex = 0,
                contentBlocksProto = byteArrayOf(1, 2, 3),
                estimatedPageCount = 1
            )
        )
        val converter = LocatorConverter(dao, proto, mockk<Context>(relaxed = true))

        assertNull(converter.getLocatorFromCfi(book(), chapterIndex = 0, cfi = "/4/2"))
    }

    private fun converterFor(blocks: List<SemanticBlock>, estimatedPageCount: Int = 1): LocatorConverter {
        val chapter = ProcessedChapter(
            bookId = "Book",
            chapterIndex = 0,
            contentBlocksProto = proto.encodeToByteArray(blocks),
            estimatedPageCount = estimatedPageCount
        )
        return LocatorConverter(FakeBookCacheDao(chapter), proto, mockk<Context>(relaxed = true))
    }

    private fun semanticBlocks(): List<SemanticBlock> {
        return listOf(
            paragraph("First paragraph", blockIndex = 1, cfi = "/4/2"),
            paragraph("Second paragraph", blockIndex = 2, cfi = "/4/2/6"),
            SemanticFlexContainer(
                children = listOf(paragraph("Nested paragraph", blockIndex = 3, cfi = "/6/4")),
                style = CssStyle(),
                elementId = null,
                cfi = null,
                blockIndex = 20
            )
        )
    }

    private fun paragraph(
        text: String,
        blockIndex: Int,
        cfi: String,
        offset: Int = 0
    ): SemanticParagraph {
        return SemanticParagraph(
            text = text,
            spans = emptyList(),
            style = CssStyle(),
            elementId = null,
            cfi = cfi,
            startCharOffsetInSource = offset,
            blockIndex = blockIndex
        )
    }

    private fun book(): EpubBook {
        return EpubBook(
            fileName = "book.epub",
            title = "Book",
            author = "Author",
            language = "en",
            coverImage = null,
            chapters = listOf(
                EpubChapter(
                    chapterId = "c1",
                    absPath = "c1.xhtml",
                    title = "Chapter",
                    htmlFilePath = "c1.xhtml",
                    plainTextContent = "",
                    htmlContent = ""
                )
            ),
            extractionBasePath = ""
        )
    }

    private class FakeBookCacheDao(
        private val chapter: ProcessedChapter?
    ) : BookCacheDao() {
        val requestedBookIds = mutableListOf<String>()

        override suspend fun getProcessedChapter(bookId: String, chapterIndex: Int): ProcessedChapter? {
            requestedBookIds += bookId
            return chapter
        }
        override suspend fun insertProcessedChapters(chapters: List<ProcessedChapter>) = Unit

        override suspend fun getProcessedBook(bookId: String): ProcessedBook? = null
        override suspend fun insertProcessedBook(book: ProcessedBook) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun clearProcessedBooks() = Unit
        override suspend fun insertAnchorIndices(anchors: List<AnchorIndexEntry>) = Unit
        override suspend fun getAnchorIndex(bookId: String, anchorId: String): AnchorIndexEntry? = null
        override suspend fun deleteAnchorsForBook(bookId: String) = Unit
        override suspend fun deleteConfigurationCacheForBook(bookId: String) = Unit
        override suspend fun clearAnchors() = Unit
        override suspend fun clearConfigurationCache() = Unit
        override suspend fun getConfigurationCache(bookId: String, configHash: Int): ConfigurationCache? = null
        override suspend fun insertConfigurationCache(cache: ConfigurationCache) = Unit
        override suspend fun cleanupOldConfigurations(bookId: String) = Unit
        override suspend fun insertPageIndexEntries(entries: List<PageIndexEntry>) = Unit
        override suspend fun getPageIndexEntries(bookId: String, configHash: Int, chapterIndex: Int): List<PageIndexEntry> = emptyList()
        override suspend fun cleanupOldPageCaches(bookId: String) = Unit

        protected override suspend fun getChapterMetadata(bookId: String, chapterIndex: Int): ProcessedChapterMetadata? = null
        protected override suspend fun getChapterChunks(bookId: String, chapterIndex: Int): List<ByteArray> = emptyList()
        protected override suspend fun insertChapterMetadata(metadata: ProcessedChapterMetadata) = Unit
        protected override suspend fun insertChapterChunks(chunks: List<ProcessedChapterChunk>) = Unit
        protected override suspend fun deleteChapterMetadataForBook(bookId: String) = Unit
        protected override suspend fun deleteAllChapterMetadata() = Unit
        protected override suspend fun deletePageCacheMetadataForBook(bookId: String) = Unit
        protected override suspend fun deletePageCacheMetadataForChapter(bookId: String, configHash: Int, chapterIndex: Int) = Unit
        protected override suspend fun clearPageCacheMetadata() = Unit
        protected override suspend fun getPageCacheMetadata(bookId: String, configHash: Int, chapterIndex: Int): PageCacheMetadata? = null
        protected override suspend fun getPageCacheChunks(bookId: String, configHash: Int, chapterIndex: Int): List<ByteArray> = emptyList()
        protected override suspend fun insertPageCacheMetadata(metadata: PageCacheMetadata) = Unit
        protected override suspend fun insertPageCacheChunks(chunks: List<PageCacheChunk>) = Unit
    }
}
