@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsResumableDownload
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithLength
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosOpdsDownloadResumeTest {
    private val payload = ByteArray(4096) { (it % 251).toByte() }
    private val entry = OpdsEntry(
        id = "resume-test-book",
        title = "Resume Test Book",
        summary = null,
        coverUrl = null,
        acquisitions = listOf(
            OpdsAcquisition(
                url = "https://example.com/books/resume-test.epub",
                mimeType = "application/epub+zip",
            )
        ),
        navigationUrl = null,
    )
    private val acquisition = entry.acquisitions.single()

    @Test
    fun resumesAfterNetworkLossWithRangeRequest() = runTest {
        val firstHalf = payload.copyOfRange(0, 1024)
        val secondHalf = payload.copyOfRange(1024, payload.size)
        val delays = mutableListOf<Long>()
        val client = ScriptedDownloadClient(
            listOf(
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = mapOf(
                        "Content-Length" to payload.size.toString(),
                        "ETag" to "\"v1\"",
                    ),
                    chunks = listOf(firstHalf),
                    error = NSError(domain = "NSURLErrorDomain", code = -1005, userInfo = null),
                ),
                ScriptedDownloadClient.Step(
                    status = 206,
                    headers = mapOf(
                        "Content-Range" to "bytes 1024-4095/${payload.size}",
                        "Content-Length" to secondHalf.size.toString(),
                        "ETag" to "\"v1\"",
                    ),
                    chunks = listOf(secondHalf),
                ),
            )
        )
        val repository = IosOpdsRepository(httpClient = client, retrySleep = { delays += it })

        val result = repository.downloadBook(entry, acquisition, null, null)

        assertTrue(result.isSuccess, "resume should succeed, got ${result.exceptionOrNull()}")
        val book = result.getOrThrow().book
        try {
            assertContentEquals(payload, readFile(book.path))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(book.path, error = null)
        }
        assertEquals(2, client.calls.size)
        assertEquals("bytes=1024-", client.calls[1].headers["Range"])
        assertEquals("\"v1\"", client.calls[1].headers["If-Range"])
        assertEquals(1, delays.size)
    }

    @Test
    fun restartsCleanlyWhenServerIgnoresRange() = runTest {
        val partial = payload.copyOfRange(0, 512)
        val client = ScriptedDownloadClient(
            listOf(
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = mapOf("Content-Length" to payload.size.toString()),
                    chunks = listOf(partial),
                    error = NSError(domain = "NSURLErrorDomain", code = -1005, userInfo = null),
                ),
                // Server ignores Range and returns the full representation.
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = mapOf("Content-Length" to payload.size.toString()),
                    chunks = listOf(payload),
                ),
            )
        )
        val repository = IosOpdsRepository(httpClient = client, retrySleep = {})

        val result = repository.downloadBook(entry, acquisition, null, null)

        assertTrue(result.isSuccess, "restart should succeed, got ${result.exceptionOrNull()}")
        val book = result.getOrThrow().book
        try {
            assertContentEquals(payload, readFile(book.path))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(book.path, error = null)
        }
        assertEquals(2, client.calls.size)
        assertEquals("bytes=512-", client.calls[1].headers["Range"])
    }

    @Test
    fun retriesRetryableHttpStatusThenSucceeds() = runTest {
        val client = ScriptedDownloadClient(
            listOf(
                ScriptedDownloadClient.Step(
                    status = 503,
                    headers = mapOf("Retry-After" to "0"),
                ),
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = mapOf("Content-Length" to payload.size.toString()),
                    chunks = listOf(payload),
                ),
            )
        )
        val delays = mutableListOf<Long>()
        val repository = IosOpdsRepository(httpClient = client, retrySleep = { delays += it })

        val result = repository.downloadBook(entry, acquisition, null, null)

        assertTrue(result.isSuccess, "retry should succeed, got ${result.exceptionOrNull()}")
        val book = result.getOrThrow().book
        try {
            assertContentEquals(payload, readFile(book.path))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(book.path, error = null)
        }
        assertEquals(2, client.calls.size)
        assertEquals(listOf(0L), delays)
    }

    @Test
    fun discardsErrorPageBodyOnRetryableStatus() = runTest {
        val errorPage = "<html>Service Unavailable</html>".encodeToByteArray()
        val client = ScriptedDownloadClient(
            listOf(
                ScriptedDownloadClient.Step(
                    status = 503,
                    headers = mapOf("Retry-After" to "0"),
                    chunks = listOf(errorPage),
                ),
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = mapOf("Content-Length" to payload.size.toString()),
                    chunks = listOf(payload),
                ),
            )
        )
        val repository = IosOpdsRepository(httpClient = client, retrySleep = {})

        val result = repository.downloadBook(entry, acquisition, null, null)

        assertTrue(result.isSuccess, "retry should succeed, got ${result.exceptionOrNull()}")
        val book = result.getOrThrow().book
        try {
            assertContentEquals(payload, readFile(book.path))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(book.path, error = null)
        }
        assertEquals(2, client.calls.size)
    }

    @Test
    fun givesUpAfterExhaustingAttempts() = runTest {        val client = ScriptedDownloadClient(
            listOf(
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = mapOf("Content-Length" to payload.size.toString()),
                    chunks = emptyList(),
                    error = NSError(domain = "NSURLErrorDomain", code = -1005, userInfo = null),
                ),
            )
        )
        val repository = IosOpdsRepository(httpClient = client, retrySleep = {})

        val result = repository.downloadBook(entry, acquisition, null, null)

        assertTrue(result.isFailure, "persistent loss should fail")
        assertEquals(SharedOpdsResumableDownload.MaxAttempts, client.calls.size)
    }

    @Test
    fun cancellationIsNotRetried() = runTest {
        val client = ScriptedDownloadClient(
            listOf(
                ScriptedDownloadClient.Step(
                    status = 200,
                    headers = emptyMap(),
                    chunks = emptyList(),
                    error = NSError(domain = "NSURLErrorDomain", code = -999, userInfo = null),
                ),
            )
        )
        val repository = IosOpdsRepository(httpClient = client, retrySleep = {})

        val result = repository.downloadBook(entry, acquisition, null, null)

        assertTrue(result.isFailure, "cancellation should fail fast")
        assertEquals(1, client.calls.size)
    }

    private fun readFile(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: error("Missing file at $path")
        val output = ByteArray(data.length.toInt())
        if (output.isNotEmpty()) {
            output.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return output
    }
}

private class ScriptedDownloadClient(
    private val script: List<Step>,
) : IosOpdsHttpClient {
    data class Step(
        val status: Int = 200,
        val headers: Map<String, String> = emptyMap(),
        val chunks: List<ByteArray> = emptyList(),
        val error: NSError? = null,
    )

    data class Call(
        val url: String,
        val username: String?,
        val password: String?,
        val headers: Map<String, String>,
    )

    val calls = mutableListOf<Call>()

    override suspend fun fetch(
        url: String,
        username: String?,
        password: String?,
        headers: Map<String, String>,
        resourceTimeoutSeconds: Double?,
        onResponse: ((Int, Map<String, String>) -> Unit)?,
        onData: ((NSData, Long, Long?) -> Unit)?,
    ): IosUrlSessionResponse {
        val step = script[minOf(calls.size, script.size - 1)]
        calls += Call(url, username, password, headers)
        onResponse?.invoke(step.status, step.headers)
        var received = 0L
        step.chunks.forEach { chunk ->
            received += chunk.size
            onData?.invoke(chunk.toTestNSData(), received, null)
        }
        return IosUrlSessionResponse(
            data = null,
            response = null,
            error = step.error,
            statusCodeOverride = step.status,
            headersOverride = step.headers,
        )
    }
}

private fun ByteArray.toTestNSData(): NSData {
    val data = NSMutableData.dataWithLength(size.toULong()) ?: NSMutableData()
    if (isNotEmpty()) {
        val pinned = pin()
        try {
            memcpy(data.mutableBytes, pinned.addressOf(0), size.toULong())
        } finally {
            pinned.unpin()
        }
    }
    return data
}
