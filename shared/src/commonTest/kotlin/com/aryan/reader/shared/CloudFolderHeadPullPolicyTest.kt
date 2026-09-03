package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudFolderHeadPullPolicyTest {

    @Test
    fun `newer remote revision schedules pull for included bindings`() {
        assertTrue(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 5L,
                knownRevision = 3L,
                hasBinding = true,
                isIncluded = true,
            ),
        )
    }

    @Test
    fun `known revision suppresses echoes and stale heads`() {
        assertFalse(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 3L,
                knownRevision = 3L,
                hasBinding = true,
                isIncluded = true,
            ),
        )
        assertFalse(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 2L,
                knownRevision = 3L,
                hasBinding = true,
                isIncluded = true,
            ),
        )
    }

    @Test
    fun `excluded bindings never schedule but unbound discoveries do`() {
        assertFalse(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 9L,
                knownRevision = -1L,
                hasBinding = true,
                isIncluded = false,
            ),
        )
        assertTrue(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 0L,
                knownRevision = -1L,
                hasBinding = false,
                isIncluded = false,
            ),
        )
    }

    @Test
    fun `negative remote revisions never schedule`() {
        assertFalse(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = -1L,
                knownRevision = -1L,
                hasBinding = false,
                isIncluded = true,
            ),
        )
    }
}
