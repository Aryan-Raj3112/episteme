package com.aryan.reader.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FirestoreDeletionChunksTest {
    @Test
    fun `deletion targets are committed in chunks of at most 450`() = runTest {
        val targets = (0 until 1_001).map { FirestoreDeleteTarget("books", "book-$it") }
        val committedSizes = mutableListOf<Int>()

        executeFirestoreDeleteChunks(targets) { committedSizes += it.size }

        assertEquals(listOf(450, 450, 101), committedSizes)
    }

    @Test
    fun `commit failure propagates and later chunks are not attempted`() = runTest {
        val targets = (0 until 500).map { FirestoreDeleteTarget("books", "book-$it") }
        val failure = IllegalStateException("Firestore unavailable")
        val committedSizes = mutableListOf<Int>()

        var thrown: Throwable? = null
        try {
            executeFirestoreDeleteChunks(targets) {
                committedSizes += it.size
                throw failure
            }
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(failure, thrown)
        assertEquals(listOf(450), committedSizes)
    }
}
