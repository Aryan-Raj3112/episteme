package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity item B1. Android's `CfiUtils` and shared's `sharedNativeCfiPointOrNull` /
 * `sharedNativeCfiPathStrictlyBetween` were two implementations of one question and they
 * disagreed about malformed offsets. The bodies now live here, and both platforms delegate.
 *
 * `CfiUtilsTest` on the Android side covers the same ground through the delegates and is the proof
 * the move preserved behaviour; this file owns the contract, including the cases Android's own
 * test never pinned.
 */
class ReaderCfiPathsTest {

    @Test
    fun `path is everything before the first colon`() {
        assertEquals("/4/2/6", readerCfiPath("/4/2/6:13"))
        assertEquals("/4/2/6", readerCfiPath("/4/2/6"))
        assertEquals("", readerCfiPath(":13"))
        assertEquals("", readerCfiPath(""))
    }

    @Test
    fun `a malformed offset resolves to zero rather than failing`() {
        assertEquals(13, readerCfiOffset("/4/2/6:13"))
        assertEquals(0, readerCfiOffset("/4/2/6:0"))
        assertEquals(0, readerCfiOffset("/4/2/6"))
        assertEquals(0, readerCfiOffset("/4/2/6:"))
        assertEquals(0, readerCfiOffset("/4/2/6:bad"))
    }

    @Test
    fun `the nullable offset variant still distinguishes malformed from valid`() {
        assertEquals(13, readerCfiOffsetOrNull("/4/2/6:13"))
        assertEquals(0, readerCfiOffsetOrNull("/4/2/6:0"))
        assertNull(readerCfiOffsetOrNull("/4/2/6"))
        assertNull(readerCfiOffsetOrNull("/4/2/6:"))
        assertNull(readerCfiOffsetOrNull("/4/2/6:bad"))
    }

    @Test
    fun `a malformed offset is still a usable point at zero`() {
        assertEquals(ReaderCfiPoint("/4/2/6", 0), readerCfiPointOrNull("/4/2/6:bad"))
        assertEquals(ReaderCfiPoint("/4/2/6", 0), readerCfiPointOrNull("/4/2/6"))
        assertEquals(ReaderCfiPoint("/4/2/6", 13), readerCfiPointOrNull("/4/2/6:13"))
        assertNull(readerCfiPointOrNull("4/2/6:13"))
        assertNull(readerCfiPointOrNull("headless/0/1:0"))
    }

    @Test
    fun `path parts are the numeric step indices`() {
        assertEquals(listOf(4, 2, 6), readerCfiPathParts("/4/2/6:13"))
        assertEquals(listOf(4, 2, 6), readerCfiPathParts("/4/2/6"))
        // A non-numeric segment is not a comparable position, and the caller must be able to tell.
        assertNull(readerCfiPathParts("/4/nav"))
        assertNull(readerCfiPathParts("/"))
        assertNull(readerCfiPathParts(""))
    }

    @Test
    fun `path part comparison is numeric and not lexicographic`() {
        assertTrue(readerCompareCfiPathParts(listOf(4, 2), listOf(4, 10)) < 0)
        assertEquals(0, readerCompareCfiPathParts(listOf(4, 2, 6), listOf(4, 2, 6)))
        assertTrue(readerCompareCfiPathParts(listOf(4, 2, 6), listOf(4, 2)) > 0)
    }

    @Test
    fun `strictly between bounds the intermediate blocks of a multipart highlight`() {
        assertTrue(readerCfiPathStrictlyBetween("/4/4", "/4/2:1", "/4/6:1"))
        assertFalse(readerCfiPathStrictlyBetween("/4/2", "/4/2:1", "/4/6:1"))
        assertFalse(readerCfiPathStrictlyBetween("/4/8", "/4/2:1", "/4/6:1"))
        // An uncomparable operand makes the whole question false rather than a guess.
        assertFalse(readerCfiPathStrictlyBetween("/4/nav", "/4/2:1", "/4/6:1"))
        assertFalse(readerCfiPathStrictlyBetween("/4/4", "/4/nav:1", "/4/6:1"))
    }

    @Test
    fun `full comparison falls through to the offset only when the paths are equal`() {
        assertTrue(readerCompareCfi("/4/2", "/4/10") < 0)
        assertTrue(readerCompareCfi("/4/2/6", "/4/2/6/2") < 0)
        assertTrue(readerCompareCfi("/4/2/6:7", "/4/2/6:18") < 0)
        assertEquals(0, readerCompareCfi("/4/2/6:bad", "/4/2/6"))
    }
}