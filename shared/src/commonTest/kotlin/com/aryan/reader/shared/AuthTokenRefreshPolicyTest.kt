package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTokenRefreshPolicyTest {

    @Test
    fun `fresh token refreshes at TTL minus lead`() {
        assertEquals(
            3_000_000L,
            sharedAuthTokenRefreshDelayMs(nowMs = 1_000L, fetchedAtMs = 1_000L),
        )
    }

    @Test
    fun `aged token refreshes immediately once past the refresh point`() {
        assertEquals(
            0L,
            sharedAuthTokenRefreshDelayMs(
                nowMs = 10_000_000L,
                fetchedAtMs = 1_000L,
            ),
        )
    }

    @Test
    fun `partially aged token waits out the remainder`() {
        assertEquals(
            1_000_000L,
            sharedAuthTokenRefreshDelayMs(
                nowMs = 2_001_000L,
                fetchedAtMs = 1_000L,
            ),
        )
    }
}
