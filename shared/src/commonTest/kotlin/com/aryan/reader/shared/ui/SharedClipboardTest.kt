package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedClipboardTest {

    @Test
    fun `clipboard result exposes success and failure consistently`() {
        val success = SharedClipboardResult.success()
        assertTrue(success.success)
        assertTrue(success.succeeded)
        assertNull(success.failureReason)

        val failure = SharedClipboardResult.failure(SharedClipboardFailureReason.SECURITY_POLICY)
        assertFalse(failure.success)
        assertFalse(failure.succeeded)
        assertEquals(SharedClipboardFailureReason.SECURITY_POLICY, failure.failureReason)
    }
}
