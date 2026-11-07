package com.aryan.reader.shared.search

import com.aryan.reader.shared.reader.ReaderLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Row-expansion rules for library content results: one hit per location while
 * collapsed, more when expanded, and a count of what stays hidden.
 */
class SharedContentSearchHitVisibilityTest {

    @Test
    fun `collapsed row shows the best hit only`() {
        val result = result(
            hit(chapter = 0, page = null, score = 5.0, snippet = "best"),
            hit(chapter = 0, page = null, score = 1.0, snippet = "same chapter"),
            hit(chapter = 3, page = null, score = 9.0, snippet = "elsewhere"),
        )

        val collapsed = SharedContentSearchHitVisibility.hitsFor(result, expanded = false)

        assertEquals(1, collapsed.size)
        // The highest-scoring hit wins, even where it lives.
        assertEquals("elsewhere", collapsed.single().snippet)
    }

    @Test
    fun `collapsed row never shows two hits from one location`() {
        val result = result(
            hit(chapter = 0, page = null, score = 9.0, snippet = "a"),
            hit(chapter = 0, page = null, score = 8.0, snippet = "b"),
            hit(chapter = 0, page = null, score = 7.0, snippet = "c"),
        )

        val expanded = SharedContentSearchHitVisibility.hitsFor(result, expanded = true)

        assertEquals(listOf("a"), expanded.map { it.snippet })
    }

    @Test
    fun `expanded row surfaces distinct locations best-first`() {
        val result = result(
            hit(chapter = 0, page = 1, score = 2.0, snippet = "p1"),
            hit(chapter = 0, page = 2, score = 7.0, snippet = "p2"),
            hit(chapter = 1, page = 3, score = 4.0, snippet = "p3"),
            hit(chapter = 0, page = 2, score = 1.0, snippet = "p2 again"),
        )

        val expanded = SharedContentSearchHitVisibility.hitsFor(result, expanded = true)

        assertEquals(listOf("p2", "p3", "p1"), expanded.map { it.snippet })
    }

    @Test
    fun `expanded row stops at the visibility ceiling`() {
        val result = result(
            *(0 until 20).map { index ->
                hit(chapter = index, page = null, score = index.toDouble(), snippet = "c$index")
            }.toTypedArray()
        )

        val expanded = SharedContentSearchHitVisibility.hitsFor(result, expanded = true)
        val hidden = SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded = true)

        assertEquals(SharedContentSearchHitVisibility.EXPANDED_HITS, expanded.size)
        // Best-first, so the strongest hits survive the cut.
        assertEquals(19.0, expanded.first().score)
        assertEquals(20 - SharedContentSearchHitVisibility.EXPANDED_HITS, hidden)
    }

    @Test
    fun `collapsed row reports how many matches expanding would reveal`() {
        val result = result(
            hit(chapter = 0, page = null, score = 9.0, snippet = "a"),
            hit(chapter = 0, page = null, score = 8.0, snippet = "b"),
            hit(chapter = 1, page = null, score = 7.0, snippet = "c"),
        )

        // Two distinct locations, one shown: one more to reveal, and expending
        // row reveals nothing beyond the ceiling.
        assertEquals(1, SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded = false))
        assertEquals(0, SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded = true))
    }

    @Test
    fun `single location book needs no expand affordance`() {
        val result = result(
            hit(chapter = 0, page = null, score = 9.0, snippet = "a"),
            hit(chapter = 0, page = null, score = 8.0, snippet = "b"),
        )

        assertEquals(0, SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded = false))
    }

    @Test
    fun `hidden count shrinks to zero once everything is shown`() {
        val result = result(
            hit(chapter = 0, page = null, score = 3.0, snippet = "a"),
            hit(chapter = 1, page = null, score = 1.0, snippet = "b"),
        )

        assertEquals(1, SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded = false))
        assertEquals(0, SharedContentSearchHitVisibility.hiddenMatchCount(result, expanded = true))
    }

    private fun hit(
        chapter: Int?,
        page: Int?,
        score: Double,
        snippet: String,
    ) = SharedGlobalSearchHit(
        bookId = "b1",
        chunkIndex = 0,
        locator = ReaderLocator(chapterIndex = chapter, pageIndex = page),
        snippet = snippet,
        score = score,
        chapterIndex = chapter,
        pageIndex = page,
    )

    private fun result(vararg hits: SharedGlobalSearchHit) = SharedGlobalSearchBookResult(
        bookId = "b1",
        score = hits.maxOf { it.score },
        matchingChunkCount = hits.size,
        hits = hits.toList(),
    )
}
