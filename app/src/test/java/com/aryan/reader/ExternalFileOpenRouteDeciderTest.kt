package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalFileOpenRouteDeciderTest {
    @Test
    fun `temporary behavior routes to temporary activity`() {
        assertTrue(ExternalFileOpenRouteDecider.shouldOpenTemporary("TEMPORARY"))
        assertEquals(
            TemporaryExternalFileActivity::class.java,
            ExternalFileOpenRouteDecider.targetActivityClass("TEMPORARY")
        )
    }

    @Test
    fun `existing behaviors route to main activity`() {
        listOf(null, "ASK", "KEEP", "DELETE").forEach { behavior ->
            assertFalse(ExternalFileOpenRouteDecider.shouldOpenTemporary(behavior))
            assertEquals(
                MainActivity::class.java,
                ExternalFileOpenRouteDecider.targetActivityClass(behavior)
            )
        }
    }
}
