package com.aryan.reader.shared.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedOpdsStreamRequestTest {
    @Test
    fun `page URL expands page and width and rewrites persisted catalog authority`() {
        val reference = OpdsStreamReference(
            id = "book",
            count = 12,
            urlTemplate = "https://cdn.example/page/{pageNumber}?width={maxWidth}",
            catalogId = "catalog",
        )

        assertEquals(
            "https://catalog.example/page/7?width=1600",
            SharedOpdsStreamRequest.buildPageUrl(
                reference = reference,
                pageIndex = 7,
                catalogUrl = "https://catalog.example/opds",
            ),
        )
    }

    @Test
    fun `resource URI round trips encoded stream request`() {
        val reference = OpdsStreamReference(
            id = "book 1",
            count = 12,
            urlTemplate = "https://example.org/page/{pageNumber}?width={maxWidth}&token=a&b",
            catalogId = "catalog 1",
        )

        val parsed = SharedOpdsStreamRequest.parseResourceUri(
            SharedOpdsStreamRequest.buildResourceUri(reference, pageIndex = 3, maxWidth = 900),
        )

        assertEquals(SharedOpdsStreamPageRequest(reference, 3, 900), parsed)
    }

    @Test
    fun `malformed or out of range resource URIs are rejected`() {
        assertNull(SharedOpdsStreamRequest.parseResourceUri("reader-opds-page://stream?page=0"))
        assertNull(
            SharedOpdsStreamRequest.parseResourceUri(
                "reader-opds-page://stream?id=book&count=2&page=2&width=1600&url=https%3A%2F%2Fexample.org%2F{pageNumber}",
            ),
        )
    }
}
