package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedTtsChunkMatchingTest {
    private fun ref(text: String, cfi: String, offset: Int) =
        SharedTtsChunkRef(sourceCfi = cfi, startOffsetInSource = offset, text = text)

    @Test
    fun chunkStartMatchingToleratesChildCfiPathAndWhitespaceTextDifferences() {
        val chunks = listOf(
            ref("The first paragraph begins here.", "/4/22/2", 0),
            ref("The second paragraph begins here.", "/4/24/2", 0)
        )
        val extracted = ref("The   second paragraph begins here.", "/4/24", 0)

        assertEquals(1, findTtsChunkStartIndex(chunks, extracted))
    }

    @Test
    fun resumeMatchingFallsBackToCurrentChunkIndexBeforeLeavingChapter() {
        val chunks = listOf(
            ref("One", "/4/2", 0),
            ref("Two", "/4/4", 0),
            ref("Three", "/4/6", 0)
        )

        assertEquals(
            1,
            findTtsChunkResumeIndex(
                chunks = chunks,
                sourceCfi = "/mismatched",
                startOffsetInSource = 0,
                currentText = "unknown",
                currentChunkIndexFallback = 1
            )
        )
    }

    @Test
    fun chunkStartMatchingAcceptsTargetOffsetInsideMatchingSourceBlock() {
        val chunks = listOf(
            ref("Alpha beta gamma", "/4/8/2", 10),
            ref("Delta epsilon", "/4/10/2", 0)
        )

        assertEquals(0, findTtsChunkStartIndex(chunks, ref("", "/4/8", 16)))
    }

    @Test
    fun verticalContinuationFallsBackToLoadedChunkBoundaryWhenResumeMatchIsUnavailable() {
        val chunks = listOf(
            ref("Loaded one", "/4/2", 0),
            ref("Loaded two", "/4/4", 0),
            ref("Remaining three", "/4/6", 0),
            ref("Remaining four", "/4/8", 0)
        )

        assertEquals(
            2,
            resolveTtsContinuationStartIndex(
                chunks = chunks,
                loadedChunkCount = 2,
                sourceCfi = "/does/not/match",
                startOffsetInSource = 0,
                currentText = "not present"
            )
        )
    }

    @Test
    fun verticalContinuationStartsAfterMatchedSpokenChunk() {
        val chunks = listOf(
            ref("Loaded one", "/4/2", 0),
            ref("Loaded two", "/4/4", 0),
            ref("Remaining three", "/4/6", 0)
        )

        assertEquals(
            2,
            resolveTtsContinuationStartIndex(
                chunks = chunks,
                loadedChunkCount = 1,
                sourceCfi = "/4/4",
                startOffsetInSource = 0,
                currentText = "Loaded two"
            )
        )
    }

    @Test
    fun verticalContinuationAdvancesChapterWhenFinalChunkWasSpoken() {
        val chunks = listOf(
            ref("First", "/4/2", 0),
            ref("Final", "/4/4", 0)
        )

        assertNull(
            resolveTtsContinuationStartIndex(
                chunks = chunks,
                loadedChunkCount = 0,
                sourceCfi = "/4/4",
                startOffsetInSource = 0,
                currentText = "Final"
            )
        )
    }

    @Test
    fun verticalContinuationNeverRestartsAtZeroAfterAnUnmatchedFinishedChunk() {
        assertNull(
            resolveTtsContinuationStartIndex(
                chunks = listOf(ref("Only", "/4/2", 0)),
                loadedChunkCount = 0,
                sourceCfi = "/previous/chapter",
                startOffsetInSource = 0,
                currentText = "different text"
            )
        )
    }
}
