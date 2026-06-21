package com.aryan.reader

import android.content.Intent
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
    @Test
    fun `internal forward strips uri grant flags`() {
        val sourceFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
            Intent.FLAG_ACTIVITY_NEW_TASK

        val forwardedFlags = ExternalFileOpenRouteDecider.flagsForInternalForward(sourceFlags)

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, forwardedFlags)
    }
}
