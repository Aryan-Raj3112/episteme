package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedJvmLruMemoryCacheTest {
    @Test
    fun `cache evicts least recently used entry`() {
        val cache = SharedJvmLruMemoryCache<String, Int>(maxEntries = 2)

        cache["one"] = 1
        cache["two"] = 2
        assertEquals(1, cache["one"])

        cache["three"] = 3

        assertEquals(1, cache["one"])
        assertNull(cache["two"])
        assertEquals(3, cache["three"])
    }
}
