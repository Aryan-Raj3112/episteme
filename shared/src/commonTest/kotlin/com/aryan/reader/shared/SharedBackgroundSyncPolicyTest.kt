package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedBackgroundSyncPolicyTest {

    @Test
    fun `outbox retry delays mirror Android CloudFolderSyncWorker`() {
        // CloudFolderSyncWorker.retryDelayMs: (1<<attempts)*1000, clamped 0..8.
        assertEquals(1_000L, sharedCloudFolderOutboxRetryDelayMs(0))
        assertEquals(2_000L, sharedCloudFolderOutboxRetryDelayMs(1))
        assertEquals(4_000L, sharedCloudFolderOutboxRetryDelayMs(2))
        assertEquals(8_000L, sharedCloudFolderOutboxRetryDelayMs(3))
        assertEquals(256_000L, sharedCloudFolderOutboxRetryDelayMs(8))
        // Clamp: negative and oversized attempts behave like 0 and 8.
        assertEquals(1_000L, sharedCloudFolderOutboxRetryDelayMs(-4))
        assertEquals(256_000L, sharedCloudFolderOutboxRetryDelayMs(99))
    }

    @Test
    fun `outbox retries stop at max attempts and quarantine`() {
        assertEquals(8, SHARED_BACKGROUND_MAX_OUTBOX_ATTEMPTS)
        assertTrue(shouldRetrySharedCloudFolderOutbox(0))
        assertTrue(shouldRetrySharedCloudFolderOutbox(7))
        assertFalse(shouldRetrySharedCloudFolderOutbox(8))
        assertFalse(shouldRetrySharedCloudFolderOutbox(12))
    }

    @Test
    fun `worker backoff constants mirror Android WorkManager`() {
        assertEquals(30_000L, SHARED_BACKGROUND_CLOUD_FOLDER_BACKOFF_MS)
        assertEquals(10_000L, SHARED_BACKGROUND_CLOUD_BOOK_DELETE_BACKOFF_MS)
        assertEquals(10_000L, SHARED_BACKGROUND_ANNOTATION_EXPORT_BACKOFF_MS)
        assertEquals(30_000L, SHARED_BACKGROUND_METADATA_EXTRACTION_BACKOFF_MS)
        assertEquals(500, SHARED_BACKGROUND_OUTBOX_BATCH_LIMIT)
        assertEquals(500, SHARED_BACKGROUND_METADATA_OUTBOX_BATCH_LIMIT)
        assertEquals(500L, SHARED_BACKGROUND_HEAD_DEBOUNCE_MS)
    }

    @Test
    fun `metadata extraction retries at most three times on failures`() {
        assertTrue(shouldRetrySharedMetadataExtraction(failedCount = 2, runAttempt = 0))
        assertTrue(shouldRetrySharedMetadataExtraction(failedCount = 1, runAttempt = 2))
        assertFalse(shouldRetrySharedMetadataExtraction(failedCount = 1, runAttempt = 3))
        assertFalse(shouldRetrySharedMetadataExtraction(failedCount = 0, runAttempt = 0))
    }

    @Test
    fun `inventory refresh windows mirror MainViewModel`() {
        assertEquals(5L * 60L * 1_000L, SHARED_BACKGROUND_INVENTORY_REFRESH_MS)
        assertEquals(30L * 1_000L, SHARED_BACKGROUND_INVENTORY_RETRY_MS)
        assertEquals(60L * 1_000L, SHARED_BACKGROUND_INVENTORY_STALE_MS)

        // No inventory always refreshes; stale SCANNING / FAILED / old READY too.
        assertTrue(shouldRefreshSharedFolderInventory(false, null, 1_000L, 0L, 0L))
        assertTrue(
            shouldRefreshSharedFolderInventory(
                true, "SCANNING", 61_000L, 0L, 0L,
            ),
        )
        assertFalse(
            shouldRefreshSharedFolderInventory(
                true, "SCANNING", 30_000L, 0L, 0L,
            ),
        )
        assertTrue(
            shouldRefreshSharedFolderInventory(
                true, "FAILED", 31_000L, 0L, 0L,
            ),
        )
        assertTrue(
            shouldRefreshSharedFolderInventory(
                true, "READY", 301_000L, 0L, 0L,
            ),
        )
        assertFalse(
            shouldRefreshSharedFolderInventory(
                true, "READY", 100_000L, 50_000L, 50_000L,
            ),
        )
    }

    @Test
    fun `background requests are one-shot and never periodic`() {
        // No account / sync off / nothing pending -> no request (Android gates
        // every enqueue on account + eligibility first).
        assertNull(
            sharedBackgroundRefreshRequest(
                hasAccount = false,
                isSyncEnabled = true,
                hasPendingSnapshot = true,
                hasPendingOutbox = true,
                hasSelectedCloudRoots = true,
            ),
        )
        assertNull(
            sharedBackgroundRefreshRequest(
                hasAccount = true,
                isSyncEnabled = false,
                hasPendingSnapshot = true,
                hasPendingOutbox = true,
                hasSelectedCloudRoots = true,
            ),
        )
        assertNull(
            sharedBackgroundRefreshRequest(
                hasAccount = true,
                isSyncEnabled = true,
                hasPendingSnapshot = false,
                hasPendingOutbox = false,
                hasSelectedCloudRoots = false,
            ),
        )
        val refresh = sharedBackgroundRefreshRequest(
            hasAccount = true,
            isSyncEnabled = true,
            hasPendingSnapshot = false,
            hasPendingOutbox = true,
            hasSelectedCloudRoots = false,
        )
        assertNotNull(refresh)
        assertEquals(SharedBackgroundTaskKind.APP_REFRESH, refresh.kind)
        assertTrue(refresh.requiresNetwork)

        assertNull(
            sharedBackgroundProcessingRequest(
                hasAccount = true,
                isSyncEnabled = true,
                isProUser = false,
                hasEnabledFolders = true,
            ),
        )
        val processing = sharedBackgroundProcessingRequest(
            hasAccount = true,
            isSyncEnabled = true,
            isProUser = true,
            hasEnabledFolders = true,
        )
        assertNotNull(processing)
        assertEquals(SharedBackgroundTaskKind.PROCESSING, processing.kind)
        assertEquals(SHARED_BACKGROUND_CLOUD_FOLDER_BACKOFF_MS, processing.earliestBeginMsFromNow)
    }

    @Test
    fun `head debounce stays foreground-only`() {
        // Documents the Android invariant: the head listener debounces 500ms
        // in the foreground and detaches in the background. iOS must not
        // invent periodic polling; BG tasks are one-shot retries only.
        assertEquals(500L, SHARED_BACKGROUND_HEAD_DEBOUNCE_MS)
        assertNotNull(SharedBackgroundTaskKind.APP_REFRESH)
        assertNotNull(SharedBackgroundTaskKind.PROCESSING)
    }
}
