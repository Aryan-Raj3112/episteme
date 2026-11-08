package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTtsChapterChainTest {
    @Test
    fun `plan with content starts the planned chapter`() {
        assertEquals(
            ReaderTtsChainDecision.Start(4),
            decideReaderTtsChain(completedChapterIndex = 2, chapterCount = 10, plannedChapterIndex = 4)
        )
    }

    @Test
    fun `empty plan before the last chapter is retried and not treated as the end`() {
        assertEquals(
            ReaderTtsChainDecision.Retry(READER_TTS_CHAIN_RETRY_DELAY_MS),
            decideReaderTtsChain(completedChapterIndex = 2, chapterCount = 10, plannedChapterIndex = null)
        )
    }

    @Test
    fun `retry budget is bounded`() {
        assertEquals(
            ReaderTtsChainDecision.Retry(READER_TTS_CHAIN_RETRY_DELAY_MS),
            decideReaderTtsChain(
                completedChapterIndex = 2,
                chapterCount = 10,
                plannedChapterIndex = null,
                attempt = READER_TTS_CHAIN_MAX_ATTEMPTS - 1
            )
        )
        assertEquals(
            ReaderTtsChainDecision.EndOfBook,
            decideReaderTtsChain(
                completedChapterIndex = 2,
                chapterCount = 10,
                plannedChapterIndex = null,
                attempt = READER_TTS_CHAIN_MAX_ATTEMPTS
            )
        )
    }

    @Test
    fun `last chapter ends the session`() {
        assertEquals(
            ReaderTtsChainDecision.EndOfBook,
            decideReaderTtsChain(completedChapterIndex = 9, chapterCount = 10, plannedChapterIndex = null)
        )
    }

    @Test
    fun `out of range plan is not started`() {
        assertEquals(
            ReaderTtsChainDecision.EndOfBook,
            decideReaderTtsChain(completedChapterIndex = 9, chapterCount = 10, plannedChapterIndex = 10)
        )
    }

    @Test
    fun `unknown completed chapter ends the session`() {
        assertEquals(
            ReaderTtsChainDecision.EndOfBook,
            decideReaderTtsChain(completedChapterIndex = null, chapterCount = 10, plannedChapterIndex = 3)
        )
    }
}
