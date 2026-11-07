package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The folder-sidecar merge must never let an empty payload ("this device has
 * nothing to share") erase a non-empty local bookmarks/highlights payload.
 */
class FolderSyncAnnotationMergeTest {

    private val local = """[{"pageIndex":1}]"""
    private val remote = """[{"pageIndex":2}]"""
    private val mapped = """[{"pageIndex":3}]"""

    @Test
    fun `no applied metadata keeps the local payload`() {
        assertEquals(
            local,
            resolveFolderAnnotationJson(
                hasAppliedMetadata = false,
                appliedFieldIsExplicit = false,
                preferMappedOverExplicit = false,
                appliedJson = null,
                existingJson = local,
                mappedJson = null,
            ),
        )
    }

    @Test
    fun `empty applied payload never erases a non-empty local payload`() {
        for (empty in listOf(null, "", "[]", "  ")) {
            assertEquals(
                local,
                resolveFolderAnnotationJson(
                    hasAppliedMetadata = true,
                    appliedFieldIsExplicit = true,
                    preferMappedOverExplicit = false,
                    appliedJson = empty,
                    existingJson = local,
                    mappedJson = null,
                ),
            )
        }
    }

    @Test
    fun `non-empty applied payload wins over the local payload`() {
        assertEquals(
            remote,
            resolveFolderAnnotationJson(
                hasAppliedMetadata = true,
                appliedFieldIsExplicit = true,
                preferMappedOverExplicit = false,
                appliedJson = remote,
                existingJson = local,
                mappedJson = null,
            ),
        )
    }

    @Test
    fun `bookmarks prefer explicit metadata over a non-empty mapped set`() {
        assertEquals(
            remote,
            resolveFolderAnnotationJson(
                hasAppliedMetadata = true,
                appliedFieldIsExplicit = true,
                preferMappedOverExplicit = false,
                appliedJson = remote,
                existingJson = local,
                mappedJson = mapped,
            ),
        )
    }

    @Test
    fun `highlights prefer a non-empty mapped set over explicit metadata`() {
        assertEquals(
            mapped,
            resolveFolderAnnotationJson(
                hasAppliedMetadata = true,
                appliedFieldIsExplicit = true,
                preferMappedOverExplicit = true,
                appliedJson = remote,
                existingJson = local,
                mappedJson = mapped,
            ),
        )
        // Explicit but empty highlights still lose to the non-empty mapped set.
        assertEquals(
            mapped,
            resolveFolderAnnotationJson(
                hasAppliedMetadata = true,
                appliedFieldIsExplicit = true,
                preferMappedOverExplicit = true,
                appliedJson = "[]",
                existingJson = local,
                mappedJson = mapped,
            ),
        )
    }

    @Test
    fun `mapped payload is used when metadata is not explicit`() {
        assertEquals(
            mapped,
            resolveFolderAnnotationJson(
                hasAppliedMetadata = true,
                appliedFieldIsExplicit = false,
                preferMappedOverExplicit = false,
                appliedJson = remote,
                existingJson = local,
                mappedJson = mapped,
            ),
        )
    }

    @Test
    fun `everything absent stays absent`() {
        assertNull(
            resolveFolderAnnotationJson(
                hasAppliedMetadata = false,
                appliedFieldIsExplicit = false,
                preferMappedOverExplicit = false,
                appliedJson = null,
                existingJson = null,
                mappedJson = null,
            ),
        )
    }
}
