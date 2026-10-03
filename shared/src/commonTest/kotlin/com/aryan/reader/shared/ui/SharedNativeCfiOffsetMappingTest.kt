package com.aryan.reader.shared.ui

import com.aryan.reader.paginatedreader.ReaderCfiPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parity item B1. Shared used to carry two twins of Android's `cfiOffsetToBlockLocal`:
 * `sharedNativeScopedOffsetToLocalOrNull` returned null for an offset that belonged to
 * another block (correct), while `sharedNativeCfiOffsetToLocal` returned the raw offset,
 * which the caller then clamped onto this block's tail — the ghost-highlight bug Android
 * had already fixed. The non-null twin is gone and both call sites now use the nullable one.
 */
class SharedNativeCfiOffsetMappingTest {

    @Test
    fun `offset already inside the block text is used as-is`() {
        assertEquals(0, sharedNativeScopedOffsetToLocalOrNull(0, 100, 40))
        assertEquals(40, sharedNativeScopedOffsetToLocalOrNull(40, 100, 40))
        assertEquals(17, sharedNativeScopedOffsetToLocalOrNull(17, 100, 40))
    }

    @Test
    fun `absolute offset is rebased onto the block`() {
        assertEquals(0, sharedNativeScopedOffsetToLocalOrNull(100, 100, 40))
        assertEquals(40, sharedNativeScopedOffsetToLocalOrNull(140, 100, 40))
        assertEquals(20, sharedNativeScopedOffsetToLocalOrNull(120, 100, 40))
    }

    @Test
    fun `offset belonging to another block is rejected rather than clamped`() {
        // The ghost-highlight case: 250 is past this block's 100..140 span and is not a
        // valid in-block offset either. Android returns null here on purpose.
        assertNull(sharedNativeScopedOffsetToLocalOrNull(250, 100, 40))
        assertNull(sharedNativeScopedOffsetToLocalOrNull(99, 100, 40))
        assertNull(sharedNativeScopedOffsetToLocalOrNull(-1, 100, 40))
        assertNull(sharedNativeScopedOffsetToLocalOrNull(41, 100, 40))
    }

    @Test
    fun `a zero length block only accepts offsets that land on it`() {
        assertEquals(0, sharedNativeScopedOffsetToLocalOrNull(0, 100, 0))
        // offset == textStartOffset is the start of an empty block, which is a real
        // position -- Android's cfiOffsetToBlockLocal rebases it to 0 the same way.
        assertEquals(0, sharedNativeScopedOffsetToLocalOrNull(100, 100, 0))
        assertNull(sharedNativeScopedOffsetToLocalOrNull(1, 100, 0))
        assertNull(sharedNativeScopedOffsetToLocalOrNull(99, 100, 0))
    }

    @Test
    fun `the in-block interpretation wins when both could apply`() {
        // offset 5 satisfies `0..40` first, so it must not be rebased by 100.
        assertEquals(5, sharedNativeScopedOffsetToLocalOrNull(5, 100, 40))
    }

    /**
     * Android's `CfiUtils.getPath` and `getOffset` are independent: the path is everything before
     * the first `:`, and the offset is `substringAfter(':', "0").toIntOrNull() ?: 0`. A point whose
     * suffix is not a number is therefore a usable point at offset 0 on Android. Shared rejected
     * it, which dropped the whole highlight on iOS. The arithmetic now lives in
     * `ReaderCfiPaths.kt` and both platforms delegate to it.
     */
    @Test
    fun `a non-numeric offset suffix resolves to zero instead of rejecting the point`() {
        assertEquals(ReaderCfiPoint("/2/4/6", 0), "/2/4/6:abc".sharedNativeCfiPointOrNull())
        assertEquals(ReaderCfiPoint("/2/4/6", 0), "/2/4/6:".sharedNativeCfiPointOrNull())
        assertEquals(ReaderCfiPoint("/2/4/6", 7), "/2/4/6:7".sharedNativeCfiPointOrNull())
        assertEquals(ReaderCfiPoint("/2/4/6", 0), "/2/4/6".sharedNativeCfiPointOrNull())
    }

    @Test
    fun `a point whose path is not a slash-rooted cfi path is still rejected`() {
        assertNull("2/4/6:abc".sharedNativeCfiPointOrNull())
        assertNull("headless/0/1:abc".sharedNativeCfiPointOrNull())
        assertNull(":12".sharedNativeCfiPointOrNull())
    }
}