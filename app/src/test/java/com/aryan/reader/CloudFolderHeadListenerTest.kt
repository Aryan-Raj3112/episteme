package com.aryan.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudFolderHeadListenerTest {
    @Test
    fun `head validation accepts committed and legacy states`() {
        assertThat(
            isValidCloudFolderHeadUpdate(
                CloudFolderHeadUpdate(
                    rootId = "folder_root_123",
                    revision = 4L,
                    state = "COMMITTED",
                    declaredRootId = "folder_root_123",
                    schemaVersion = 1L,
                ),
            ),
        ).isTrue()
        assertThat(
            isValidCloudFolderHeadUpdate(
                CloudFolderHeadUpdate(rootId = "folder_root_legacy", revision = 0L),
            ),
        ).isTrue()
    }

    @Test
    fun `head validation rejects malformed or uncommitted records`() {
        assertThat(
            isValidCloudFolderHeadUpdate(
                CloudFolderHeadUpdate(rootId = "../escape", revision = 1L),
            ),
        ).isFalse()
        assertThat(
            isValidCloudFolderHeadUpdate(
                CloudFolderHeadUpdate(
                    rootId = "folder_root_123",
                    revision = 1L,
                    state = "COMMITTING",
                ),
            ),
        ).isFalse()
        assertThat(
            isValidCloudFolderHeadUpdate(
                CloudFolderHeadUpdate(
                    rootId = "folder_root_123",
                    revision = 1L,
                    declaredRootId = "folder_root_other",
                ),
            ),
        ).isFalse()
    }

    @Test
    fun `transient lease states are recognized separately from malformed heads`() {
        assertThat(isTransientCloudFolderHeadState("COMMITTING")).isTrue()
        assertThat(isTransientCloudFolderHeadState("committing ")).isTrue()
        assertThat(isTransientCloudFolderHeadState(null)).isFalse()
        assertThat(isTransientCloudFolderHeadState("COMMITTED")).isFalse()
        // A COMMITTING head with a malformed identity is not merely
        // transient; it must still be reported as invalid.
        assertThat(
            isValidCloudFolderHeadUpdate(
                CloudFolderHeadUpdate(
                    rootId = "../escape",
                    revision = 1L,
                    state = "COMMITTING",
                ),
            ),
        ).isFalse()
    }

    @Test
    fun `head pull wakes only newer included roots or unbound discovery`() {
        assertThat(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 5L,
                knownRevision = 4L,
                hasBinding = true,
                isIncluded = true,
            ),
        ).isTrue()
        assertThat(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 4L,
                knownRevision = 4L,
                hasBinding = true,
                isIncluded = true,
            ),
        ).isFalse()
        assertThat(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 5L,
                knownRevision = 4L,
                hasBinding = true,
                isIncluded = false,
            ),
        ).isFalse()
        assertThat(
            shouldScheduleCloudFolderHeadPull(
                remoteRevision = 1L,
                knownRevision = -1L,
                hasBinding = false,
                isIncluded = false,
            ),
        ).isTrue()
    }
}
