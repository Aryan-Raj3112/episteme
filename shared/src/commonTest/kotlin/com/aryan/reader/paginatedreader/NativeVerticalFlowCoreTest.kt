package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeVerticalFlowCoreTest {
    @Test
    fun `keys match the Android benchmark formats`() {
        assertEquals("chapter-0-gap", nativeVerticalGapKey(0))
        assertEquals("chapter-0-block-0-7", nativeVerticalBlockKey(0, 0, 7))
        assertEquals("chapter-1-unloaded", nativeVerticalUnloadedKey(1))
        assertEquals("chapter-2-empty", nativeVerticalEmptyKey(2))
        assertEquals("chapter-3-page-9", nativeVerticalPageKey(3, 9))
        assertEquals("chapter-4-text", nativeVerticalSyntheticTextKey(4))
    }

    @Test
    fun `gap precedes every chapter after the first`() {
        assertEquals(false, shouldInsertNativeVerticalGap(0))
        assertEquals(true, shouldInsertNativeVerticalGap(1))
        assertEquals(true, shouldInsertNativeVerticalGap(5))
    }

    @Test
    fun `first text unit prefers non-blank then first`() {
        assertEquals("b", pickNativeVerticalFirstTextUnit(listOf("", "b", "")) { it })
        assertEquals("a", pickNativeVerticalFirstTextUnit(listOf("a", "")) { it })
        assertNull(pickNativeVerticalFirstTextUnit(emptyList<String>()) { it })
    }

    @Test
    fun `text range aggregates min start to max end`() {
        assertNull(nativeVerticalTextRangeFromOffsets(emptyList()))
        assertEquals(40 to 53, nativeVerticalTextRangeFromOffsets(listOf(40 to 13)))
        assertEquals(
            10 to 30,
            nativeVerticalTextRangeFromOffsets(listOf(20 to 5, 10 to 20, 25 to 2)),
        )
    }

    @Test
    fun `cascade prefers block over offset over page over chapter`() {
        assertEquals(1, resolveNativeVerticalFlowIndexInOrder(1, 2, 3, 4))
        assertEquals(2, resolveNativeVerticalFlowIndexInOrder(null, 2, 3, 4))
        assertEquals(2, resolveNativeVerticalFlowIndexInOrder(-1, 2, 3, 4))
        assertEquals(3, resolveNativeVerticalFlowIndexInOrder(null, null, 3, 4))
        assertEquals(4, resolveNativeVerticalFlowIndexInOrder(null, null, null, 4))
        assertNull(resolveNativeVerticalFlowIndexInOrder(null, null, null, null))
        assertNull(resolveNativeVerticalFlowIndexInOrder(-1, -1, -1, -1))
    }

    @Test
    fun `pagination target expands to canonical and back`() {
        val target = ReaderNavigationTarget(chapterIndex = 3, blockIndex = 12, charOffset = 86)
        val locator = target.toNativeReaderLocator(
            pageIndex = 9,
            startOffset = 80,
            endOffset = 93,
            textQuote = "nested target",
            cfi = "/4/6:6",
        )
        assertEquals(3, locator.chapterIndex)
        assertEquals(12, locator.blockIndex)
        assertEquals(86, locator.charOffset)
        assertEquals(9, locator.pageIndex)
        assertEquals("/4/6:6", locator.cfi)
        assertEquals(target, locator.toNativeVerticalTarget())
    }

    @Test
    fun `canonical collapse prefers block offset over page range`() {
        val fromBlock = buildNativeReaderLocator(
            chapterIndex = 2,
            blockIndex = 5,
            charOffset = 40,
            startOffset = 10,
        )
        assertEquals(ReaderNavigationTarget(2, 5, 40), fromBlock.toNativeVerticalTarget())

        val fromPage = buildNativeReaderLocator(chapterIndex = 2, startOffset = 10)
        assertEquals(ReaderNavigationTarget(2, 0, 10), fromPage.toNativeVerticalTarget())

        val empty = buildNativeReaderLocator(chapterIndex = 2)
        assertEquals(ReaderNavigationTarget(2, 0, 0), empty.toNativeVerticalTarget())

        assertNull(buildNativeReaderLocator(chapterIndex = null).toNativeVerticalTarget())
    }
}
