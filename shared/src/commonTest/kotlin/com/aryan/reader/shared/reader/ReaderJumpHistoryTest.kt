package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderJumpHistoryTest {

    @Test
    fun `records explicit locator jumps and exposes back and forward locators`() {
        val start = locator(chapter = 0, cfi = "start")
        val middle = locator(chapter = 1, cfi = "middle")
        val end = locator(chapter = 2, cfi = "end")

        val recorded = ReaderJumpHistory()
            .record(currentLocator = start, targetLocator = middle, chapterCount = 4)
            .record(currentLocator = middle, targetLocator = end, chapterCount = 4)

        val steppedBack = recorded.stepBack()
        val branched = steppedBack.record(
            currentLocator = middle,
            targetLocator = locator(chapter = 3, cfi = "appendix"),
            chapterCount = 4
        )

        assertEquals(listOf(start, middle, end), recorded.locators)
        assertEquals(middle, recorded.backLocator)
        assertEquals(null, recorded.forwardLocator)
        assertEquals(start, steppedBack.backLocator)
        assertEquals(end, steppedBack.forwardLocator)
        assertEquals(listOf(start, middle, locator(chapter = 3, cfi = "appendix")), branched.locators)
        assertEquals(middle, branched.backLocator)
    }

    @Test
    fun `jump history refreshes current locator before stepping back`() {
        val page1 = locator(chapter = 0, cfi = "page-1").copy(pageIndex = 0)
        val page20 = locator(chapter = 0, cfi = "page-20").copy(pageIndex = 19)
        val page22 = locator(chapter = 0, cfi = "page-22").copy(pageIndex = 21)

        val refreshed = ReaderJumpHistory()
            .record(currentLocator = page1, targetLocator = page20, chapterCount = 1)
            .updateCurrentLocation(currentLocator = page22, chapterCount = 1)
        val steppedBack = refreshed.stepBack()
        val steppedForward = steppedBack.stepForward()

        assertEquals(listOf(page1, page22), refreshed.locators)
        assertEquals(page1, steppedBack.locators[steppedBack.cursor])
        assertEquals(page22, steppedBack.forwardLocator)
        assertEquals(page22, steppedForward.locators[steppedForward.cursor])
    }

    @Test
    fun `jump history ignores invalid current locator refreshes`() {
        val history = ReaderJumpHistory()
            .record(
                currentLocator = locator(chapter = 0, cfi = "page-1"),
                targetLocator = locator(chapter = 0, cfi = "page-20"),
                chapterCount = 1
            )

        assertEquals(
            history,
            history.updateCurrentLocation(
                currentLocator = locator(chapter = 1, cfi = "outside"),
                chapterCount = 1
            )
        )
        assertEquals(history, history.updateCurrentLocation(currentLocator = null, chapterCount = 1))
    }

    @Test
    fun `jump history refresh keeps stable cfi identity while updating metadata`() {
        val start = locator(chapter = 0, cfi = "start")
        val previous = locator(chapter = 0, cfi = "stable").copy(pageIndex = 20)
        val current = previous.copy(pageIndex = 22)

        val refreshed = ReaderJumpHistory()
            .record(currentLocator = start, targetLocator = previous, chapterCount = 1)
            .updateCurrentLocation(currentLocator = current, chapterCount = 1)

        assertEquals(current, refreshed.locators.last())
        assertTrue(previous.hasSameJumpLocation(refreshed.locators.last()))
    }

    @Test
    fun `ignores invalid and duplicate jumps prunes chapters and caps entries`() {
        val unchanged = ReaderJumpHistory()
            .record(currentLocator = locator(chapter = 0, cfi = "same"), targetLocator = locator(chapter = 0, cfi = "same"), chapterCount = 3)
            .record(currentLocator = locator(chapter = 0, cfi = "ok"), targetLocator = locator(chapter = 99, cfi = "bad"), chapterCount = 3)

        val pruned = ReaderJumpHistory(
            locators = listOf(
                locator(chapter = 0, cfi = "start"),
                locator(chapter = 3, cfi = "drop"),
                locator(chapter = 1, cfi = "keep")
            ),
            cursor = 2
        ).pruned(chapterCount = 2)

        val capped = (0 until 40).fold(ReaderJumpHistory(maxEntries = 5)) { history, index ->
            history.record(
                currentLocator = locator(chapter = 0, cfi = "spot-$index"),
                targetLocator = locator(chapter = 0, cfi = "spot-${index + 1}"),
                chapterCount = 1
            )
        }

        assertTrue(unchanged.locators.isEmpty())
        assertTrue(
            locator(chapter = 0, cfi = "stable").copy(pageIndex = 12)
                .hasSameJumpLocation(locator(chapter = 0, cfi = "stable").copy(pageIndex = 48))
        )
        assertEquals(listOf(locator(chapter = 0, cfi = "start"), locator(chapter = 1, cfi = "keep")), pruned.locators)
        assertEquals(1, pruned.cursor)
        assertEquals((36..40).map { locator(chapter = 0, cfi = "spot-$it") }, capped.locators)
        assertEquals(4, capped.cursor)
    }

    private fun locator(chapter: Int, cfi: String): ReaderLocator {
        return ReaderLocator(chapterIndex = chapter, cfi = cfi)
    }
}
