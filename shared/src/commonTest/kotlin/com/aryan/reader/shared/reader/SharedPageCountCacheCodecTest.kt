package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPageCountCacheCodecTest {
    @Test
    fun `versioned cache round trips only finalized measured chapters`() {
        val encoded = SharedPageCountCacheCodec.encode(
            counts = mapOf(0 to 3, 1 to 8, 2 to 13),
            finalizedChapters = setOf(2, 0),
        )

        assertEquals("v2;final=0,2;counts=0:3,2:13", encoded)
        val decoded = SharedPageCountCacheCodec.decode(encoded)
        assertEquals(mapOf(0 to 3, 2 to 13), decoded.counts)
        assertEquals(setOf(0, 2), decoded.finalizedChapters)
        assertTrue(decoded.isVersioned)
    }

    @Test
    fun `legacy counts remain readable and invalid counts are ignored`() {
        val decoded = SharedPageCountCacheCodec.decode("0:10,1:0,bad,2:4")

        assertEquals(mapOf(0 to 10, 2 to 4), decoded.counts)
        assertEquals(emptySet(), decoded.finalizedChapters)
        assertFalse(decoded.isVersioned)
    }
}
