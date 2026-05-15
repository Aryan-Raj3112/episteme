package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CfiUtilsTest {

    @Test
    fun `getPath strips offsets while preserving full cfi path`() {
        assertEquals("/4/2/6", CfiUtils.getPath("/4/2/6:13"))
        assertEquals("/4/2/6", CfiUtils.getPath("/4/2/6"))
    }

    @Test
    fun `getOffset parses valid offsets and defaults invalid or missing offsets to zero`() {
        assertEquals(13, CfiUtils.getOffset("/4/2/6:13"))
        assertEquals(0, CfiUtils.getOffset("/4/2/6:0"))
        assertEquals(0, CfiUtils.getOffset("/4/2/6"))
        assertEquals(0, CfiUtils.getOffset("/4/2/6:bad"))
    }

    @Test
    fun `getOffsetOrNull only returns explicit numeric offsets`() {
        assertEquals(13, CfiUtils.getOffsetOrNull("/4/2/6:13"))
        assertEquals(0, CfiUtils.getOffsetOrNull("/4/2/6:0"))
        assertNull(CfiUtils.getOffsetOrNull("/4/2/6"))
        assertNull(CfiUtils.getOffsetOrNull("/4/2/6:bad"))
    }

    @Test
    fun `compare sorts numeric cfi paths before character offsets`() {
        assertTrue(CfiUtils.compare("/4/2", "/4/10") < 0)
        assertTrue(CfiUtils.compare("/4/2/6", "/4/2/6/2") < 0)
        assertTrue(CfiUtils.compare("/4/2/6:7", "/4/2/6:18") < 0)
        assertEquals(0, CfiUtils.compare("/4/2/6:bad", "/4/2/6"))
    }

    @Test
    fun `isPathStrictlyBetween requires a bounded numeric cfi path`() {
        assertTrue(CfiUtils.isPathStrictlyBetween("/4/4", "/4/2:1", "/4/6:1"))
        assertFalse(CfiUtils.isPathStrictlyBetween("/4/2", "/4/2:1", "/4/6:1"))
        assertFalse(CfiUtils.isPathStrictlyBetween("/4/8", "/4/2:1", "/4/6:1"))
        assertFalse(CfiUtils.isPathStrictlyBetween("/4/nav", "/4/2:1", "/4/6:1"))
    }
}
