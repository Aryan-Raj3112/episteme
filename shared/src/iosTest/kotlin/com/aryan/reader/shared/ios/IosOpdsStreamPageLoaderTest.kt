@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsStreamReference
import com.aryan.reader.shared.opds.SharedOpdsStreamRequest
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSUserDefaults
import platform.posix.memcpy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosOpdsStreamPageLoaderTest {
    @Test
    fun authenticatedPageUsesCatalogHostCredentialsAndCache() = runTest {
        val key = "reader_ios_opds_catalogs_json"
        val defaults = NSUserDefaults.standardUserDefaults
        val previousCatalogs = defaults.stringForKey(key)
        val catalogId = "stream-test-${com.aryan.reader.shared.currentTimestamp()}"
        val catalog = OpdsCatalog(
            id = catalogId,
            title = "Authenticated catalog",
            url = "https://catalog.example/opds/",
            username = "reader",
            password = "secret",
        )
        val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)
        val client = RecordingIosOpdsHttpClient(payload.toNSData())
        val repository = IosOpdsRepository(httpClient = client)
        repository.saveCatalogs(listOf(catalog))
        val loader = IosOpdsStreamPageLoader(repository)
        val reference = OpdsStreamReference(
            id = "book-$catalogId",
            count = 3,
            urlTemplate = "https://cdn.example/pse/{pageNumber}?width={maxWidth}",
            catalogId = catalogId,
        )
        val resourceUrl = SharedOpdsStreamRequest.buildResourceUri(reference, pageIndex = 1)

        try {
            val first = loader.loadPage(resourceUrl)
            val second = loader.loadPage(resourceUrl)

            assertContentEquals(payload, first?.bytes)
            assertEquals("image/jpeg", first?.mimeType)
            assertContentEquals(payload, second?.bytes)
            assertEquals(1, client.calls.size)
            assertEquals(
                "https://catalog.example/pse/1?width=1600",
                client.calls.single().url,
            )
            assertEquals("reader", client.calls.single().username)
            assertEquals("secret", client.calls.single().password)
            // The generated WebView resource URI contains no catalog credentials.
            assertEquals(false, resourceUrl.contains("reader"))
            assertEquals(false, resourceUrl.contains("secret"))
        } finally {
            if (previousCatalogs == null) {
                defaults.removeObjectForKey(key)
            } else {
                defaults.setObject(previousCatalogs, forKey = key)
            }
        }
    }

    @Test
    fun malformedOrOutOfRangePageIsNotFetched() = runTest {
        val client = RecordingIosOpdsHttpClient(byteArrayOf(1, 2, 3).toNSData())
        val loader = IosOpdsStreamPageLoader(IosOpdsRepository(httpClient = client))

        assertNull(loader.loadPage("reader-opds-page://stream?page=99&count=2&width=1600&url=https%3A%2F%2Fexample.com%2F%7BpageNumber%7D"))
        assertEquals(0, client.calls.size)
    }

    @Test
    fun failedAuthenticatedPageIsReturnedAsUnavailable() = runTest {
        val client = RecordingIosOpdsHttpClient(
            responseData = byteArrayOf(1, 2, 3).toNSData(),
            failure = NSError(domain = "opds-test", code = 503, userInfo = null),
        )
        val loader = IosOpdsStreamPageLoader(IosOpdsRepository(httpClient = client))
        val resourceUrl = SharedOpdsStreamRequest.buildResourceUri(
            reference = OpdsStreamReference(
                id = "failed-page",
                count = 1,
                urlTemplate = "https://example.com/page/{pageNumber}",
            ),
            pageIndex = 0,
        )

        assertNull(loader.loadPage(resourceUrl))
        assertEquals(1, client.calls.size)
    }
}

private class RecordingIosOpdsHttpClient(
    private val responseData: NSData,
    private val failure: NSError? = null,
) : IosOpdsHttpClient {
    data class Call(
        val url: String,
        val username: String?,
        val password: String?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun fetch(
        url: String,
        username: String?,
        password: String?,
        headers: Map<String, String>,
        onData: ((NSData, Long, Long?) -> Unit)?,
    ): IosUrlSessionResponse {
        calls += Call(url, username, password)
        return IosUrlSessionResponse(
            data = responseData.takeUnless { failure != null },
            response = null,
            error = failure,
        )
    }
}

private fun ByteArray.toNSData(): NSData {
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
