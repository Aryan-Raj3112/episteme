package com.aryan.reader.shared.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedOpdsResumableDownloadTest {
    @Test
    fun `range header uses bytes offset suffix`() {
        assertEquals("bytes=0-", SharedOpdsResumableDownload.rangeHeader(0L))
        assertEquals("bytes=1024-", SharedOpdsResumableDownload.rangeHeader(1024L))
    }

    @Test
    fun `content range total parses sized and unsized responses`() {
        assertEquals(2048L, SharedOpdsResumableDownload.parseContentRangeTotal("bytes 0-1023/2048"))
        assertEquals(2048L, SharedOpdsResumableDownload.parseContentRangeTotal("bytes 1024-2047/2048"))
        assertNull(SharedOpdsResumableDownload.parseContentRangeTotal("bytes 0-1023/*"))
        assertNull(SharedOpdsResumableDownload.parseContentRangeTotal(null))
        assertNull(SharedOpdsResumableDownload.parseContentRangeTotal("garbage"))
    }

    @Test
    fun `content range first byte is parsed for offset validation`() {
        assertEquals(1024L, SharedOpdsResumableDownload.parseContentRangeFirstByte("bytes 1024-2047/2048"))
        assertEquals(0L, SharedOpdsResumableDownload.parseContentRangeFirstByte("bytes 0-1023/2048"))
        assertNull(SharedOpdsResumableDownload.parseContentRangeFirstByte("bytes */2048"))
        assertNull(SharedOpdsResumableDownload.parseContentRangeFirstByte(null))
    }

    @Test
    fun `total resolves from content range then falls back to offset plus length`() {
        assertEquals(
            2048L,
            SharedOpdsResumableDownload.resolveTotal(206, "bytes 1024-2047/2048", 1024L, 1024L)
        )
        assertEquals(
            2048L,
            SharedOpdsResumableDownload.resolveTotal(206, null, 1024L, 1024L)
        )
        assertEquals(
            512L,
            SharedOpdsResumableDownload.resolveTotal(200, null, 512L, 0L)
        )
        assertNull(SharedOpdsResumableDownload.resolveTotal(206, null, null, 1024L))
        assertNull(SharedOpdsResumableDownload.resolveTotal(500, null, 10L, 0L))
    }

    @Test
    fun `retryable statuses cover transient server load`() {
        assertTrue(SharedOpdsResumableDownload.isRetryableHttpStatus(503))
        assertTrue(SharedOpdsResumableDownload.isRetryableHttpStatus(429))
        assertTrue(SharedOpdsResumableDownload.isRetryableHttpStatus(500))
        assertFalse(SharedOpdsResumableDownload.isRetryableHttpStatus(404))
        assertFalse(SharedOpdsResumableDownload.isRetryableHttpStatus(401))
        assertFalse(SharedOpdsResumableDownload.isRetryableHttpStatus(416))
    }

    @Test
    fun `network lost and timeouts are retryable but cancellation is not`() {
        assertTrue(SharedOpdsResumableDownload.isRetryableNetworkError("NSURLErrorDomain", -1005L))
        assertTrue(SharedOpdsResumableDownload.isRetryableNetworkError("NSURLErrorDomain", -1001L))
        assertTrue(SharedOpdsResumableDownload.isRetryableNetworkError("NSURLErrorDomain", -1009L))
        assertTrue(SharedOpdsResumableDownload.isRetryableNetworkError("NSPOSIXErrorDomain", 54L))
        assertFalse(SharedOpdsResumableDownload.isRetryableNetworkError("NSURLErrorDomain", -999L))
        assertFalse(SharedOpdsResumableDownload.isRetryableNetworkError("NSURLErrorDomain", -1202L))
    }

    @Test
    fun `retry after seconds are honored and backoff grows exponentially`() {
        assertEquals(3_000L, SharedOpdsResumableDownload.parseRetryAfterMs("3"))
        assertNull(SharedOpdsResumableDownload.parseRetryAfterMs(null))
        assertNull(SharedOpdsResumableDownload.parseRetryAfterMs("not-a-number"))

        assertEquals(1_000L, SharedOpdsResumableDownload.retryDelayMs(1, null))
        assertEquals(2_000L, SharedOpdsResumableDownload.retryDelayMs(2, null))
        assertEquals(4_000L, SharedOpdsResumableDownload.retryDelayMs(3, null))
        assertEquals(3_000L, SharedOpdsResumableDownload.retryDelayMs(1, 3_000L))
    }
}
