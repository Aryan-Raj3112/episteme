package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveFailureDiagnosticsTest {
    @Test
    fun `structured rate limit reason is retained while 403 status stays compatible`() {
        val result = classifyGoogleDriveFailure(
            httpStatusCode = 403,
            responseBody = null,
            structuredErrors = listOf(
                GoogleDriveStructuredError("usageLimits", "userRateLimitExceeded"),
            ),
        )

        assertEquals("forbidden", result.statusCategory)
        assertEquals("unknown", result.bodyCategory)
        assertEquals("userratelimitexceeded", result.driveReason)
        assertEquals("usagelimits", result.driveDomain)
    }

    @Test
    fun `response body still supplies legacy category without exposing message`() {
        val result = classifyGoogleDriveFailure(
            httpStatusCode = 403,
            responseBody = "{\"error\":{\"message\":\"Rate Limit Exceeded\"}}",
        )

        assertEquals("quota", result.statusCategory)
        assertEquals("quota", result.bodyCategory)
        assertEquals("unknown", result.driveReason)
    }

    @Test
    fun `retry after accepts delta seconds and HTTP date without retaining header`() {
        assertEquals(RetryAfterDiagnostic("seconds", 17L), parseRetryAfterDiagnostic("17"))
        assertEquals(
            RetryAfterDiagnostic("http_date", 60L),
            parseRetryAfterDiagnostic("Thu, 01 Jan 1970 00:01:00 GMT", nowMillis = 0L),
        )
    }
}
