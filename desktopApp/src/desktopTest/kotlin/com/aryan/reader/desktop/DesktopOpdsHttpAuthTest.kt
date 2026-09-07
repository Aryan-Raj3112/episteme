package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopOpdsHttpAuthTest {
    @Test
    fun `preemptive basic header is sent for stream pages with username only`() {
        assertEquals(
            "Basic dXNlcjpwYXNz",
            DesktopOpdsHttp.preemptiveBasicAuthHeader("user", "pass")
        )
        assertEquals(
            "Basic dXNlcjo=",
            DesktopOpdsHttp.preemptiveBasicAuthHeader("user", null)
        )
        assertEquals(
            "Basic dXNlcjo=",
            DesktopOpdsHttp.preemptiveBasicAuthHeader("user", "")
        )
        assertNull(DesktopOpdsHttp.preemptiveBasicAuthHeader(null, "pass"))
        assertNull(DesktopOpdsHttp.preemptiveBasicAuthHeader("   ", "pass"))
    }
}
