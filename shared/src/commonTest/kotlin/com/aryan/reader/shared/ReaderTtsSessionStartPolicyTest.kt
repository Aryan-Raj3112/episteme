package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderTtsSessionStartPolicyTest {
    private fun chunk(
        index: Int,
        text: String,
        startOffset: Int,
        endOffset: Int = startOffset + text.length,
        sourceCfi: String? = "epub/chapter.xhtml#p1",
    ) = ReaderTtsChunk(
        index = index,
        pageIndex = 3,
        chapterIndex = 0,
        chapterTitle = "One",
        text = text,
        startOffset = startOffset,
        endOffset = endOffset,
        sourceCfi = sourceCfi,
    )

    @Test
    fun tinyHeadIsFoldedIntoTheFollowingChunk() {
        val head = chunk(0, "T.", 100, 102)
        val next = chunk(1, "The quick brown fox jumps.", 102, 126)
        val third = chunk(2, "Another part.", 126, 140)

        val merged = listOf(head, next, third).mergeTinyLeadingChunk()

        assertEquals(2, merged.size)
        assertEquals("T. The quick brown fox jumps.", merged[0].text)
        assertEquals(100, merged[0].startOffset)
        assertEquals(126, merged[0].endOffset)
        assertEquals("Another part.", merged[1].text)
    }

    @Test
    fun mergedSpokenTextIsJoinedTheSameWay() {
        val head = chunk(0, "T.", 100, 102)
        val next = chunk(1, "Hello there.", 102, 114).copy(spokenText = "Hello there.")

        val merged = listOf(head, next).mergeTinyLeadingChunk()

        assertEquals("T. Hello there.", merged.single().spokenText)
    }

    @Test
    fun aNormalSizedHeadIsLeftAlone() {
        val head = chunk(0, "a".repeat(READER_TTS_TINY_HEAD_CHUNK_MAX_CHARS + 1), 0)
        val next = chunk(1, "Second chunk text.", 200)
        val original = listOf(head, next)

        assertSame(original, original.mergeTinyLeadingChunk())
    }

    @Test
    fun aHeadThatEndsItsBlockIsNotMergedAcrossCfiBoundaries() {
        // Merging across blocks would break offset/CFI-based progress
        // highlighting, so the tiny head keeps its own (wasted but safe) chunk.
        val head = chunk(0, "T.", 100, 102, sourceCfi = "epub/chapter.xhtml#p1")
        val next = chunk(1, "Next block text.", 102, 118, sourceCfi = "epub/chapter.xhtml#p2")

        val merged = listOf(head, next).mergeTinyLeadingChunk()

        assertEquals(2, merged.size)
        assertEquals("T.", merged[0].text)
    }

    @Test
    fun nonAdjacentChunksAreNotMerged() {
        val head = chunk(0, "T.", 100, 102)
        val next = chunk(1, "Not adjacent.", 500, 512)

        val merged = listOf(head, next).mergeTinyLeadingChunk()

        assertEquals(2, merged.size)
    }

    @Test
    fun aLoneTinyChunkSurvives() {
        val head = chunk(0, "T.", 100, 102)

        val merged = listOf(head).mergeTinyLeadingChunk()

        assertEquals(1, merged.size)
        assertEquals("T.", merged.single().text)
    }

    @Test
    fun emptyInputIsHandled() {
        assertTrue(emptyList<ReaderTtsChunk>().mergeTinyLeadingChunk().isEmpty())
    }
}
