package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSessionOrchestratorTest {
    @Test
    fun `open command carries a platform-neutral source token and drives lifecycle`() {
        val command = ReaderOpenCommand("book", FileType.EPUB, "content://book", restore = true)
        val opening = ReaderSessionOrchestrator.start(AppReaderSessionState(), command)
        val ready = ReaderSessionOrchestrator.ready(opening, "book")

        assertEquals("content://book", command.sourceToken)
        assertEquals(true, command.restore)
        assertEquals(AppReaderSessionPhase.OPENING, opening.phase)
        assertEquals(AppReaderSessionPhase.READY, ready.phase)
    }

    @Test
    fun `stale completion is rejected and close clears the session`() {
        val opening = ReaderSessionOrchestrator.start(
            AppReaderSessionState(),
            ReaderOpenCommand("book", FileType.PDF, "file:///book.pdf"),
        )

        assertEquals(opening, ReaderSessionOrchestrator.ready(opening, "other"))
        assertEquals(AppReaderSessionState(), ReaderSessionOrchestrator.close(opening))
    }
}
