package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.TtsChunk
import org.junit.Assert.assertEquals
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
}
