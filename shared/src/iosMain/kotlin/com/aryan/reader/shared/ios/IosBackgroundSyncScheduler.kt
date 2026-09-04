package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS
import com.aryan.reader.shared.SHARED_BACKGROUND_CLOUD_SYNC_RETRY_MAX_MS
import com.aryan.reader.shared.SharedBackgroundSyncRequest
import com.aryan.reader.shared.sharedBackgroundProcessingRequest
import com.aryan.reader.shared.sharedBackgroundRefreshRequest
import com.aryan.reader.shared.sharedCloudFolderOutboxRetryDelayMs
import com.aryan.reader.shared.shouldRetrySharedCloudFolderOutbox

/**
 * iOS background-sync scheduling decisions.
 *
 * Android is the absolute benchmark and is NOT changed. Android uses
 * one-shot WorkManager requests (never periodic) with CONNECTED constraints
 * and EXPONENTIAL backoff; iOS mirrors that with one-shot BGAppRefreshTask
 * (short retry / head re-check / entitlement re-query) and BGProcessingTask
 * (folder index, upload drain, GC, cleanup). The Swift `IosBackgroundSync`
 * shell performs the actual BGTaskScheduler submit/cancel; this file only
 * computes *when* and *what*, so the math stays shared-first and testable
 * through the common policy.
 */
object IosBackgroundSyncScheduler {
    /** Must match BGTaskSchedulerPermittedIdentifiers in ReaderInfo.plist. */
    const val REFRESH_TASK_ID = "com.aryan.reader.sync.refresh"
    const val PROCESSING_TASK_ID = "com.aryan.reader.sync.processing"

    /**
     * Next one-shot delay for a cloud-folder outbox row. Mirrors
     * CloudFolderSyncWorker.retryDelayMs exactly; callers clamp to the
     * Swift retry ceiling (base 5s .. max 15m) already used by
     * LocalAccountController.
     */
    fun outboxRetryDelayMs(attempts: Int): Long =
        sharedCloudFolderOutboxRetryDelayMs(attempts)
            .coerceIn(SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS, SHARED_BACKGROUND_CLOUD_SYNC_RETRY_MAX_MS)

    fun shouldRetryOutbox(attempts: Int): Boolean =
        shouldRetrySharedCloudFolderOutbox(attempts)

    /**
     * One-shot refresh request for pending cloud work. Delegates to the shared
     * gate so iOS and tests share Android's exact eligibility math.
     */
    fun refreshRequestForPendingWork(
        hasAccount: Boolean,
        isSyncEnabled: Boolean,
        hasPendingSnapshot: Boolean,
        hasPendingOutbox: Boolean,
        hasSelectedCloudRoots: Boolean,
    ): SharedBackgroundSyncRequest? = sharedBackgroundRefreshRequest(
        hasAccount = hasAccount,
        isSyncEnabled = isSyncEnabled,
        hasPendingSnapshot = hasPendingSnapshot,
        hasPendingOutbox = hasPendingOutbox,
        hasSelectedCloudRoots = hasSelectedCloudRoots,
    )

    /**
     * One-shot processing request for a full folder pass / GC / cleanup.
     * Delegates to the shared gate (explicit REPLACE triggers only, never
     * periodic).
     */
    fun processingRequestForFolderPass(
        hasAccount: Boolean,
        isSyncEnabled: Boolean,
        isProUser: Boolean,
        hasEnabledFolders: Boolean,
    ): SharedBackgroundSyncRequest? = sharedBackgroundProcessingRequest(
        hasAccount = hasAccount,
        isSyncEnabled = isSyncEnabled,
        isProUser = isProUser,
        hasEnabledFolders = hasEnabledFolders,
    )
}
