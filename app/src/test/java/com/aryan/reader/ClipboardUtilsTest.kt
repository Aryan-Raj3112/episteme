package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardUtilsTest {
    @Test
    fun `set primary clip reports success`() {
        assertTrue(setPrimaryClipSafely {})
    }

    @Test
    fun `set primary clip handles security rejection`() {
        assertFalse(setPrimaryClipSafely { throw SecurityException("denied") })
    }
}
