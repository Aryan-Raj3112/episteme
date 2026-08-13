package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileReaderSessionRestoreTest {
    private val candidate = MobileReaderSessionRestoreCandidate(
        bookId = "book",
        fileType = FileType.EPUB,
        isAvailable = true,
        hasReadableLocation = true,
    )

    @Test
    fun absentBookIdMeansThereIsNoRestoreRequest() {
        assertEquals(
            MobileReaderSessionRestoreAction.NONE,
            mobileReaderSessionRestoreAction(null, FileType.EPUB.name, candidate = candidate),
        )
    }

    @Test
    fun matchingAvailableCandidateWithReadableLocationRestores() {
        assertEquals(
            MobileReaderSessionRestoreAction.RESTORE,
            mobileReaderSessionRestoreAction("book", FileType.EPUB.name, candidate = candidate),
        )
    }

    @Test
    fun staleOrUnsafePersistedSessionsAreCleared() {
        val invalidCases = listOf(
            mobileReaderSessionRestoreAction("book", null, candidate = candidate),
            mobileReaderSessionRestoreAction("book", "INVALID", candidate = candidate),
            mobileReaderSessionRestoreAction("book", FileType.EPUB.name, setOf("book"), candidate),
            mobileReaderSessionRestoreAction("book", FileType.EPUB.name, candidate = null),
            mobileReaderSessionRestoreAction("other", FileType.EPUB.name, candidate = candidate),
            mobileReaderSessionRestoreAction(
                "book",
                FileType.PDF.name,
                candidate = candidate,
            ),
            mobileReaderSessionRestoreAction(
                "book",
                FileType.EPUB.name,
                candidate = candidate.copy(isAvailable = false),
            ),
            mobileReaderSessionRestoreAction(
                "book",
                FileType.EPUB.name,
                candidate = candidate.copy(hasReadableLocation = false),
            ),
        )

        invalidCases.forEach {
            assertEquals(MobileReaderSessionRestoreAction.CLEAR_PERSISTED_SESSION, it)
        }
    }
}
