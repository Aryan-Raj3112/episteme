package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCountCacheCodecTest {

    @Test
    fun `versioned cache round trips only finalized measured counts`() {
        val encoded = PageCountCacheCodec.encode(
            counts = mapOf(0 to 10, 1 to 20, 2 to 30),
            finalizedChapters = setOf(0, 2)
        )

        val decoded = PageCountCacheCodec.decode(encoded)

        assertTrue(decoded.isVersioned)
        assertEquals(setOf(0, 2), decoded.finalizedChapters)
        assertEquals(mapOf(0 to 10, 2 to 30), decoded.counts)
    }

    @Test
    fun `legacy cache is decoded as ambiguous unfinalized counts`() {
        val decoded = PageCountCacheCodec.decode("0:10,1:20")

        assertFalse(decoded.isVersioned)
        assertEquals(mapOf(0 to 10, 1 to 20), decoded.counts)
        assertTrue(decoded.finalizedChapters.isEmpty())
    }
}
