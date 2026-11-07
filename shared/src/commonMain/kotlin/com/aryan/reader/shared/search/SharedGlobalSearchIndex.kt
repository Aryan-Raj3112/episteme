package com.aryan.reader.shared.search

import kotlin.math.ln

/**
 * Per-book inverted index plus BM25 search. Pure Kotlin, no platform APIs.
 *
 * Documents are chunks; IDF is computed across a book's own chunks for
 * within-book ranking, while [SharedGlobalSearchCoordinator] compares books
 * by their best chunk plus a multi-hit bonus so rare-term books win.
 */
class SharedGlobalSearchIndex private constructor(
    val bookId: String,
    val contentHash: String,
    private val chunks: List<SharedSearchChunk>,
    private val postings: Map<String, Map<Int, Int>>,
    private val chunkLengths: List<Int>,
    private val avgChunkLength: Double,
) {
    val chunkCount: Int get() = chunks.size

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75

        fun build(bookId: String, contentHash: String, rawChunks: List<SharedSearchChunk>): SharedGlobalSearchIndex {
            val boundedChars = SharedGlobalSearchPolicy.MAX_INDEXED_CHARS_PER_BOOK
            val boundedChunks = SharedGlobalSearchPolicy.MAX_CHUNKS_PER_BOOK
            val kept = mutableListOf<SharedSearchChunk>()
            var chars = 0
            for (c in rawChunks) {
                if (kept.size >= boundedChunks) break
                if (c.text.isBlank()) continue
                if (chars + c.text.length > boundedChars) break
                kept += c.copy(chunkIndex = kept.size)
                chars += c.text.length
            }
            val postings = mutableMapOf<String, MutableMap<Int, Int>>()
            val lengths = IntArray(kept.size)
            kept.forEachIndexed { idx, chunk ->
                val tokens = SharedGlobalSearchTokenizer.tokenize(chunk.text)
                lengths[idx] = tokens.size
                val freq = tokens.groupingBy { it }.eachCount()
                for ((term, count) in freq) {
                    postings.getOrPut(term) { mutableMapOf() }[idx] = count
                }
            }
            val avg = if (lengths.isEmpty()) 0.0 else lengths.average()
            return SharedGlobalSearchIndex(
                bookId = bookId,
                contentHash = contentHash,
                chunks = kept.toList(),
                postings = postings,
                chunkLengths = lengths.toList(),
                avgChunkLength = avg,
            )
        }

        fun fromPersisted(persisted: SharedGlobalSearchPersistedBookIndex): SharedGlobalSearchIndex {
            val chunks = persisted.chunkTexts.mapIndexed { idx, text ->
                SharedSearchChunk(
                    chunkIndex = idx,
                    chapterIndex = persisted.chapterIndices.getOrNull(idx),
                    blockIndex = persisted.blockIndices.getOrNull(idx),
                    charOffset = persisted.charOffsets.getOrNull(idx),
                    pageIndex = persisted.pageIndices.getOrNull(idx),
                    cfi = persisted.cfis.getOrNull(idx),
                    anchorId = persisted.anchorIds.getOrNull(idx),
                    text = text,
                )
            }
            val postings = persisted.postings.mapValues { (_, list) ->
                list.associate { it.chunkIndex to it.freq }
            }
            val lengths = chunks.map { SharedGlobalSearchTokenizer.tokenize(it.text).size }
            return SharedGlobalSearchIndex(
                bookId = persisted.bookId,
                contentHash = persisted.contentHash,
                chunks = chunks,
                postings = postings,
                chunkLengths = lengths,
                avgChunkLength = if (lengths.isEmpty()) 0.0 else lengths.average(),
            )
        }
    }

    fun toPersisted(): SharedGlobalSearchPersistedBookIndex = SharedGlobalSearchPersistedBookIndex(
        bookId = bookId,
        contentHash = contentHash,
        chunkCount = chunks.size,
        totalTerms = postings.size,
        postings = postings.mapValues { (_, m) -> m.map { (chunk, freq) -> SharedGlobalSearchPersistedPosting(chunk, freq) } },
        chunkTexts = chunks.map { it.text },
        chapterIndices = chunks.map { it.chapterIndex },
        blockIndices = chunks.map { it.blockIndex },
        charOffsets = chunks.map { it.charOffset },
        pageIndices = chunks.map { it.pageIndex },
        cfis = chunks.map { it.cfi },
        anchorIds = chunks.map { it.anchorId },
    )

    /** Chunk indices containing [term], with prefix expansion when [isPrefix]. */
    private fun candidatesFor(term: String, isPrefix: Boolean): Map<Int, Int> {
        if (!isPrefix) return postings[term] ?: emptyMap()
        val merged = mutableMapOf<Int, Int>()
        for ((indexed, map) in postings) {
            if (indexed.startsWith(term)) {
                for ((chunk, freq) in map) {
                    merged[chunk] = (merged[chunk] ?: 0) + freq
                }
            }
        }
        return merged
    }

    private fun idf(docFreq: Int): Double {
        val n = chunkCount.coerceAtLeast(1).toDouble()
        // BM25 IDF with +1 floor so single-chunk books still score.
        return ln(1.0 + (n - docFreq + 0.5) / (docFreq + 0.5))
    }

    /**
     * AND search over chunks. Returns chunk hits sorted by BM25 desc,
     * capped at [SharedGlobalSearchPolicy.MAX_HITS_PER_BOOK].
     */
    fun search(query: SharedSearchQuery, maxHits: Int = SharedGlobalSearchPolicy.MAX_HITS_PER_BOOK): List<SharedGlobalSearchHit> =
        searchWithTotal(query, maxHits).hits

    /**
     * Same as [search] but also reports the uncapped total so callers can
     * badge the true match count while previewing only the best hits.
     */
    fun searchWithTotal(
        query: SharedSearchQuery,
        maxHits: Int = SharedGlobalSearchPolicy.MAX_HITS_PER_BOOK,
    ): SharedGlobalSearchIndexResult {
        if (query.isEmpty || chunks.isEmpty()) return SharedGlobalSearchIndexResult(emptyList(), 0)
        val termCandidates = query.terms.map { term ->
            term to candidatesFor(term, isPrefix = term == query.prefixTerm)
        }
        // Every term must occur somewhere; otherwise the AND is empty.
        if (termCandidates.any { it.second.isEmpty() }) return SharedGlobalSearchIndexResult(emptyList(), 0)
        var matching: Set<Int>? = null
        for ((_, cands) in termCandidates) {
            matching = if (matching == null) cands.keys.toSet() else matching.intersect(cands.keys)
            if (matching.isEmpty()) return SharedGlobalSearchIndexResult(emptyList(), 0)
        }
        // Phrase-only queries have no term constraints: every chunk is a candidate.
        val matchedChunks = matching ?: chunks.indices.toSet()

        // Phrase filter: the raw phrase must occur as a substring.
        val phraseFiltered = if (query.phrases.isEmpty()) {
            matchedChunks
        } else {
            matchedChunks.filter { idx ->
                val text = chunks[idx].text
                query.phrases.all { phrase -> text.contains(phrase, ignoreCase = true) }
            }.toSet()
        }
        if (phraseFiltered.isEmpty()) return SharedGlobalSearchIndexResult(emptyList(), 0)

        val scored = phraseFiltered.mapNotNull { idx ->
            var score = 0.0
            for ((term, cands) in termCandidates) {
                val freq = cands[idx] ?: continue
                val docFreq = cands.size
                val len = chunkLengths.getOrNull(idx)?.toDouble() ?: 0.0
                val norm = 1.0 - B + B * (len / avgChunkLength.coerceAtLeast(1.0))
                score += idf(docFreq) * (freq * (K1 + 1.0)) / (freq + K1 * norm)
            }
            // Phrase occurrences boost exact matches above bag-of-words ties.
            if (query.phrases.isNotEmpty()) score += query.phrases.size.toDouble()
            val chunk = chunks[idx]
            val (snippet, ranges) = SharedGlobalSearchSnippet.build(chunk.text, query)
            if (snippet.isBlank()) return@mapNotNull null
            val quote = snippet.replace("…", "").trim().take(120).takeIf { it.isNotBlank() }
            SharedGlobalSearchHit(
                bookId = bookId,
                chunkIndex = idx,
                locator = chunk.toLocator(textQuote = quote),
                snippet = snippet,
                matchRanges = ranges,
                score = score,
                chapterIndex = chunk.chapterIndex,
                pageIndex = chunk.pageIndex,
            )
        }.sortedByDescending { it.score }
        val total = scored.size
        return SharedGlobalSearchIndexResult(
            hits = scored.take(maxHits.coerceAtLeast(1)),
            totalMatchingChunks = total,
        )
    }
}

/**
 * Owns all per-book indexes in memory, fans queries out, merges book
 * results. Platforms feed it via [upsertBookChunks] from background work
 * and persist via [SharedGlobalSearchStorage].
 */
class SharedGlobalSearchCoordinator(
    private val storage: SharedGlobalSearchStorage = InMemorySharedGlobalSearchStorage(),
) {
    private val indexes = mutableMapOf<String, SharedGlobalSearchIndex>()
    private val hashes = mutableMapOf<String, String>()

    fun bookCount(): Int = indexes.size

    fun needsIndex(bookId: String, contentHash: String): Boolean {
        if (hashes[bookId] == contentHash) return false
        val persisted = storage.loadBook(bookId) ?: return true
        if (persisted.contentHash != contentHash) return true
        // Same content but an old payload without location arrays would render
        // label-less rows (PDFs showing only the file name with no page).
        return !persisted.hasCompleteLocations()
    }

    /** Returns false when there was nothing indexable ( caller maps to NO_NATIVE_TEXT). */
    fun upsertBookChunks(bookId: String, contentHash: String, chunks: List<SharedSearchChunk>): Boolean {
        if (chunks.isEmpty()) {
            // Persist the tombstone so needsIndex() goes quiet: without it the
            // worker would re-extract a natively empty book on every enqueue.
            val tombstone = SharedGlobalSearchIndex.build(bookId, contentHash, emptyList())
            indexes.remove(bookId)
            hashes[bookId] = contentHash
            storage.saveBook(
                tombstone.toPersisted().copy(status = SharedSearchIndexStatus.NO_NATIVE_TEXT.name),
            )
            return false
        }
        val index = SharedGlobalSearchIndex.build(bookId, contentHash, chunks)
        indexes[bookId] = index
        hashes[bookId] = contentHash
        storage.saveBook(index.toPersisted())
        return true
    }

    fun loadPersistedBook(bookId: String): Boolean {
        val persisted = storage.loadBook(bookId) ?: return false
        if (!persisted.hasCompleteLocations()) return false
        indexes[bookId] = SharedGlobalSearchIndex.fromPersisted(persisted)
        hashes[bookId] = persisted.contentHash
        return true
    }

    fun removeBook(bookId: String) {
        indexes.remove(bookId)
        hashes.remove(bookId)
        storage.deleteBook(bookId)
    }

    fun statusOf(bookId: String): SharedSearchIndexStatus =
        if (indexes.containsKey(bookId)) SharedSearchIndexStatus.INDEXED
        else SharedSearchIndexStatus.NOT_INDEXED

    /** Content hash of the in-memory payload, or null when absent. */
    fun contentHashOf(bookId: String): String? = indexes[bookId]?.contentHash

    fun search(
        rawQuery: String,
        maxBooks: Int = SharedGlobalSearchPolicy.MAX_BOOKS_RETURNED,
    ): List<SharedGlobalSearchBookResult> {
        val query = parseSharedSearchQuery(rawQuery)
        if (query.isEmpty) return emptyList()
        val out = mutableListOf<SharedGlobalSearchBookResult>()
        for ((bookId, index) in indexes) {
            val result = index.searchWithTotal(query)
            val hits = result.hits
            if (hits.isEmpty()) continue
            val best = hits.maxOf { it.score }
            val bonus = 0.5 * ln(1.0 + result.totalMatchingChunks.toDouble())
            out += SharedGlobalSearchBookResult(
                bookId = bookId,
                score = best + bonus,
                matchingChunkCount = result.totalMatchingChunks,
                hits = hits,
            )
        }
        return out.sortedByDescending { it.score }.take(maxBooks.coerceAtLeast(1))
    }
}
