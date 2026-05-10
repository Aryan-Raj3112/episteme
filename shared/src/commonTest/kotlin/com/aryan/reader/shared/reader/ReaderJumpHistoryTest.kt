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
