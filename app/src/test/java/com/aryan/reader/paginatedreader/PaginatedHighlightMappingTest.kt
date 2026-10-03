package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Offset-arithmetic checks for the paginated surfaces.
 *
 * Placement itself moved to `PaginatedHighlightPageScopeTest`, which exercises the resolver the
 * surfaces actually call. What remains here is the CFI-offset conversion that decides whether a
 * stored offset belongs to a block at all.
 */
class PaginatedHighlightMappingTest {

    @Test
    fun `cfi offset outside block resolves to null instead of clamped tail`() {
        assertEquals(10, cfiOffsetToBlockLocal(10, 0, 500, 500))
        assertEquals(20, cfiOffsetToBlockLocal(520, 500, 1000, 500))
        assertNull(cfiOffsetToBlockLocal(800, 0, 500, 500))
        assertNull(cfiOffsetToBlockLocal(5000, 0, 200, 200))
    }
}