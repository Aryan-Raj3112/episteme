package com.aryan.reader.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDrivePaginationTest {
    @Test
    fun `collects every page and passes continuation token`() {
        val requestedTokens = mutableListOf<String?>()
        val pages = mapOf(
            null to GoogleDrivePage(listOf("one", "two"), "page-2"),
            "page-2" to GoogleDrivePage(listOf("three"), "page-3"),
            "page-3" to GoogleDrivePage(listOf("four"), null),
        )

        val files = collectGoogleDrivePages { token ->
            requestedTokens += token
            pages.getValue(token)
        }

        assertEquals(listOf("one", "two", "three", "four"), files)
        assertEquals(listOf(null, "page-2", "page-3"), requestedTokens)
    }

    @Test
    fun `repeated continuation token is rejected`() {
        var calls = 0
        val error = runCatching {
            collectGoogleDrivePages {
                calls++
                GoogleDrivePage(listOf(calls), "same-token")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(2, calls)
    }

    @Test
    fun `bulk deletion helper returns every per-file failure`() = runTest {
        val deleted = mutableListOf<String>()
        val failures = deleteDriveItemsCollectingFailures(
            items = listOf("ok", "bad-1", "bad-2"),
            id = { it },
            delete = { id ->
                if (id.startsWith("bad")) error("failed $id")
                deleted += id
            },
        )

        assertEquals(listOf("ok"), deleted)
        assertEquals(listOf("bad-1", "bad-2"), failures.map { it.first })
    }
}
