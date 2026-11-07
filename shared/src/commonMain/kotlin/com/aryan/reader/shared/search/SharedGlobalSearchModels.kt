package com.aryan.reader.shared.search

import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderLocator
import kotlinx.serialization.Serializable

/**
 * One addressable stretch of native text inside a book. Platforms extract
 * these; shared indexes them. No OCR text ever enters here (see doc).
 *
 * EPUB fills [chapterIndex]/[blockIndex]/[charOffset]/[cfi]; PDF fills
 * [pageIndex]; text-likes fill chapter/block with pageIndex null.
 */
@Serializable
data class SharedSearchChunk(
    val chunkIndex: Int,
    val chapterIndex: Int? = null,
    val blockIndex: Int? = null,
    val charOffset: Int? = null,
    val pageIndex: Int? = null,
    val cfi: String? = null,
    val anchorId: String? = null,
    val text: String,
) {
    fun toLocator(textQuote: String? = null): ReaderLocator = ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        blockIndex = blockIndex,
        charOffset = charOffset,
        startOffset = charOffset,
        endOffset = charOffset?.let { start -> textQuote?.let { start + it.length } },
        textQuote = textQuote,
        cfi = cfi,
    )
}

/** Query as the UI means it. Produced by [parseSharedSearchQuery]. */
data class SharedSearchQuery(
    val raw: String,
    /** Lowercased, folded tokens in order. */
    val terms: List<String>,
    /** Exact-phrase spans (already tokenized per span, original casing dropped). */
    val phrases: List<String> = emptyList(),
    /** Last token is a prefix when the raw query has no trailing space. */
    val prefixTerm: String? = null,
) {
    val isEmpty: Boolean get() = terms.isEmpty() && phrases.isEmpty()
}

/** One chunk-level hit. Snippet + ranges keep UI highlight HTML-free. */
data class SharedGlobalSearchHit(
    val bookId: String,
    val chunkIndex: Int,
    val locator: ReaderLocator,
    val snippet: String,
    /** Match ranges inside [snippet]. */
    val matchRanges: List<IntRange> = emptyList(),
    val score: Double,
    val chapterIndex: Int? = null,
    val pageIndex: Int? = null,
)

/** Book-level grouping for library rows.
 * [matchingChunkCount] is the total uncapped chunk matches in the book;
 * [hits] is the capped preview (best-first, at most MAX_HITS_PER_BOOK).
 */
data class SharedGlobalSearchBookResult(
    val bookId: String,
    val score: Double,
    val matchingChunkCount: Int,
    val hits: List<SharedGlobalSearchHit>,
)

/** Capped preview plus uncapped total for one book's index search. */
data class SharedGlobalSearchIndexResult(
    val hits: List<SharedGlobalSearchHit>,
    val totalMatchingChunks: Int,
)

/** Why a book has no content hits. */
enum class SharedSearchIndexStatus {
    INDEXED,
    NOT_INDEXED,
    NO_NATIVE_TEXT,
    UNSUPPORTED_TYPE,
    OVER_LIMIT,
    FAILED,
}

@Serializable
data class SharedGlobalSearchPersistedPosting(
    val chunkIndex: Int,
    val freq: Int,
)

@Serializable
data class SharedGlobalSearchPersistedBookIndex(
    val bookId: String,
    val contentHash: String,
    val chunkCount: Int,
    val totalTerms: Int,
    /** term -> postings. */
    val postings: Map<String, List<SharedGlobalSearchPersistedPosting>>,
    /** Chunk texts, index-aligned. Needed for snippets. */
    val chunkTexts: List<String>,
    val chapterIndices: List<Int?> = emptyList(),
    val blockIndices: List<Int?> = emptyList(),
    val charOffsets: List<Int?> = emptyList(),
    val pageIndices: List<Int?> = emptyList(),
    val cfis: List<String?> = emptyList(),
    val anchorIds: List<String?> = emptyList(),
    val status: String = SharedSearchIndexStatus.INDEXED.name,
) {
    /** Old payloads predate location arrays; same-hash but label-less rows re-index. */
    fun hasCompleteLocations(): Boolean {
        if (chunkCount == 0) return true
        if (chunkTexts.size != chunkCount) return false
        if (chapterIndices.size != chunkCount) return false
        if (pageIndices.size != chunkCount) return false
        if (blockIndices.size != chunkCount) return false
        if (charOffsets.size != chunkCount) return false
        if (cfis.size != chunkCount) return false
        if (anchorIds.size != chunkCount) return false
        return true
    }
}

/** Storage seam platforms implement (files/Room). Shared owns the bytes. */
interface SharedGlobalSearchStorage {
    fun saveBook(index: SharedGlobalSearchPersistedBookIndex)
    fun loadBook(bookId: String): SharedGlobalSearchPersistedBookIndex?
    fun deleteBook(bookId: String)
    /** Ids with a persisted payload. Defaults to none; file stores override. */
    fun storedBookIds(): Set<String> = emptySet()
}

class InMemorySharedGlobalSearchStorage : SharedGlobalSearchStorage {
    private val books = mutableMapOf<String, SharedGlobalSearchPersistedBookIndex>()
    override fun saveBook(index: SharedGlobalSearchPersistedBookIndex) {
        books[index.bookId] = index
    }
    override fun loadBook(bookId: String): SharedGlobalSearchPersistedBookIndex? = books[bookId]
    override fun deleteBook(bookId: String) {
        books.remove(bookId)
    }
    override fun storedBookIds(): Set<String> = books.keys.toSet()
}

object SharedGlobalSearchPolicy {
    const val MAX_QUERY_TERMS = 8
    const val MAX_CHUNKS_PER_BOOK = 2000
    const val MAX_CHUNK_CHARS = 2000
    /** Total native chars indexed per book; the rest is dropped (head-kept). */
    const val MAX_INDEXED_CHARS_PER_BOOK = 2_000_000
    const val MAX_HITS_PER_BOOK = 20
    const val MAX_BOOKS_RETURNED = 50
    const val SNIPPET_RADIUS = 60
    const val MIN_TOKEN_LENGTH = 2

    /** Toggle default: library search stays metadata-only until the user opts in. */
    const val SEARCH_INSIDE_FILES_DEFAULT = false

    fun isContentSearchable(type: FileType): Boolean = when (type) {
        FileType.EPUB, FileType.PDF, FileType.MOBI, FileType.MD, FileType.TXT,
        FileType.HTML, FileType.FB2, FileType.DOCX, FileType.DOC,
        FileType.ODT, FileType.FODT, FileType.PPTX -> true
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT,
        FileType.AUDIOBOOK, FileType.UNKNOWN -> false
    }

    /**
     * Splits oversized extractor output into indexable chunks without
     * breaking a surrogate pair. Head-kept: the first
     * [MAX_INDEXED_CHARS_PER_BOOK] chars win so small books are exact and
     * huge books degrade predictably.
     */
    fun chunkTextForIndex(
        chapterIndex: Int? = null,
        blockIndex: Int? = null,
        charOffset: Int? = null,
        pageIndex: Int? = null,
        cfi: String? = null,
        anchorId: String? = null,
        text: String,
        baseChunkIndex: Int = 0,
    ): List<SharedSearchChunk> {
        if (text.isBlank()) return emptyList()
        val bounded = if (text.length > MAX_CHUNK_CHARS * 4) {
            text.take(MAX_CHUNK_CHARS * 4)
        } else {
            text
        }
        val out = mutableListOf<SharedSearchChunk>()
        var start = 0
        var index = baseChunkIndex
        while (start < bounded.length && out.size < 8) {
            var end = (start + MAX_CHUNK_CHARS).coerceAtMost(bounded.length)
            if (end < bounded.length) {
                val boundary = bounded.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                if (boundary > start + MAX_CHUNK_CHARS / 2) end = boundary
            }
            val slice = bounded.substring(start, end).trim()
            if (slice.isNotBlank()) {
                out += SharedSearchChunk(
                    chunkIndex = index++,
                    chapterIndex = chapterIndex,
                    blockIndex = blockIndex,
                    charOffset = charOffset?.plus(start),
                    pageIndex = pageIndex,
                    cfi = cfi,
                    anchorId = anchorId,
                    text = slice,
                )
            }
            if (end >= bounded.length) break
            start = end
        }
        return out
    }
}
