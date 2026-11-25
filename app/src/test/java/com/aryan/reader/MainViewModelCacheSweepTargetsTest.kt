package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache sweeper builds two O(library size) indexes before it deletes anything: a directory
 * name and a `hashCode()` string per book. A Crashlytics OOM report caught one of those
 * allocations inside `sweepOrphanedCache` on a 256 MB heap with 557 KB free, before the sweep had
 * freed a single file, so the indexes are now built only for entries that are tested against them.
 */
class MainViewModelCacheSweepTargetsTest {

    private fun targets(vararg names: String) =
        MainViewModel.CacheSweepTargets.of(names.asSequence())

    @Test
    fun `cache without library entries needs no indexes`() {
        val sweepTargets = targets("temp_import_1", "sync_bundle_2", "covers", "extracted_epubs")

        assertFalse(sweepTargets.needsActiveBookDirs)
        assertFalse(sweepTargets.needsStreamHashes)
        assertFalse(sweepTargets.needsLibraryIds)
    }

    @Test
    fun `active book dir needs the library ids`() {
        val sweepTargets = targets("temp_import_1", "imported_file_book-1_deadbeef")

        assertTrue(sweepTargets.needsActiveBookDirs)
        assertFalse(sweepTargets.needsStreamHashes)
        assertTrue(sweepTargets.needsLibraryIds)
    }

    @Test
    fun `opds stream dir needs the library ids`() {
        val sweepTargets = targets("opds_stream_-12345")

        assertFalse(sweepTargets.needsActiveBookDirs)
        assertTrue(sweepTargets.needsStreamHashes)
        assertTrue(sweepTargets.needsLibraryIds)
    }

    @Test
    fun `temporary book dir is not an active book dir`() {
        val sweepTargets = targets("imported_file_tmp_import_1")

        assertFalse(sweepTargets.needsActiveBookDirs)
        assertFalse(sweepTargets.needsStreamHashes)
    }

    @Test
    fun `both kinds are detected in one pass`() {
        val sweepTargets = targets("imported_file_book-1_deadbeef", "opds_stream_-12345")

        assertEquals(MainViewModel.CacheSweepTargets(true, true), sweepTargets)
        assertTrue(sweepTargets.needsLibraryIds)
    }

    @Test
    fun `empty cache needs no indexes`() {
        assertEquals(MainViewModel.CacheSweepTargets(false, false), targets())
    }
}
