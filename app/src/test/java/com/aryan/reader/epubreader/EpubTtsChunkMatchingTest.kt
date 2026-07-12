package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.TtsChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubTtsChunkMatchingTest {
    @Test
    fun `chunk start matching tolerates child cfi path and whitespace text differences`() {
        val chunks = listOf(
            TtsChunk(
                text = "The first paragraph begins here.",
                sourceCfi = "/4/22/2",
                startOffsetInSource = 0
            ),
            TtsChunk(
                text = "The second paragraph begins here.",
                sourceCfi = "/4/24/2",
                startOffsetInSource = 0
            )
        )
        val extracted = TtsChunk(
            text = "The   second paragraph begins here.",
            sourceCfi = "/4/24",
            startOffsetInSource = 0
        )

        assertEquals(1, findTtsChunkStartIndex(chunks, extracted))
    }

    @Test
    fun `resume matching falls back to current chunk index before leaving chapter`() {
        val chunks = listOf(
            TtsChunk("One", "/4/2", 0),
            TtsChunk("Two", "/4/4", 0),
            TtsChunk("Three", "/4/6", 0)
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
    fun `chunk start matching accepts target offset inside matching source block`() {
        val chunks = listOf(
            TtsChunk("Alpha beta gamma", "/4/8/2", 10),
            TtsChunk("Delta epsilon", "/4/10/2", 0)
        )
        val nativeVerticalTarget = TtsChunk(
            text = "",
            sourceCfi = "/4/8",
            startOffsetInSource = 16
        )

        assertEquals(0, findTtsChunkStartIndex(chunks, nativeVerticalTarget))
    }

    @Test
    fun `vertical continuation falls back to loaded chunk boundary when resume match is unavailable`() {
        val chunks = listOf(
            TtsChunk("Loaded one", "/4/2", 0),
            TtsChunk("Loaded two", "/4/4", 0),
            TtsChunk("Remaining three", "/4/6", 0),
            TtsChunk("Remaining four", "/4/8", 0)
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
    fun `vertical continuation starts after matched spoken chunk`() {
        val chunks = listOf(
            TtsChunk("Loaded one", "/4/2", 0),
            TtsChunk("Loaded two", "/4/4", 0),
            TtsChunk("Remaining three", "/4/6", 0)
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
    fun `vertical continuation advances chapter when final chunk was spoken`() {
        val chunks = listOf(
            TtsChunk("First", "/4/2", 0),
            TtsChunk("Final", "/4/4", 0)
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
    fun `vertical continuation never restarts at zero after an unmatched finished chunk`() {
        val chunks = listOf(TtsChunk("Only", "/4/2", 0))

        assertNull(
            resolveTtsContinuationStartIndex(
                chunks = chunks,
                loadedChunkCount = 0,
                sourceCfi = "/previous/chapter",
                startOffsetInSource = 0,
                currentText = "different text"
            )
        )
    }
}
