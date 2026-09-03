package com.aryan.reader.shared

/**
 * Shared background-sync retry policy.
 *
 * Android is the absolute benchmark and is NOT changed by iOS parity work.
 * Every number below mirrors an Android WorkManager constant so iOS schedules
 * the same durable retry behavior through BGTaskScheduler instead of
 * WorkManager:
 *
 * - CloudFolderSyncWorker: CONNECTED + EXPONENTIAL 30s, MAX_OUTBOX_ATTEMPTS=8,
 *   retryDelayMs=(1<<attempts)*1000, claim limits 500/500
 *   (app/src/main/java/com/aryan/reader/CloudFolderSyncWorker.kt:1325,1936,1999-2006,3401-3402,3444,3527-3540).
 * - CloudBookDeleteWorker: CONNECTED + EXPONENTIAL 10s, APPEND_OR_REPLACE
 *   (app/src/main/java/com/aryan/reader/CloudBookDeleteWorker.kt:326-344).
 * - FolderAnnotationExportWorker: EXPONENTIAL 10s
 *   (app/src/main/java/com/aryan/reader/FolderAnnotationExportWorker.kt:85-112).
 * - MetadataExtractionWorker: EXPONENTIAL 30s, MAX_RETRY=3, batch 300
 *   (app/src/main/java/com/aryan/reader/MetadataExtractionWorker.kt:35-36,48-52,617).
 * - CloudFolderHeadListener: foreground-only, 500ms debounce, background
 *   detaches (app/src/main/java/com/aryan/reader/CloudFolderHeadListener.kt:94,128-136,181-197).
 * - Purchase reconciliation: foreground-only, no Worker. BillingClientWrapper
 *   re-queries on init + every auth emission; iOS must do the same via
 *   StoreKit on foreground/auth change instead of a background queue.
 * - Local folder inventory: REFRESH 5min, RETRY 30s, STALE 60s
 *   (app/src/main/java/com/aryan/reader/MainViewModel.kt:227-229,3953-3977).
 * - No PeriodicWork anywhere on Android; iOS must not invent periodic polling
 *   either. BG tasks are one-shot retry/resume only.
 *
 * iOS applies this policy through `IosBackgroundSyncScheduler` (iosMain) and
 * the Swift `IosBackgroundSync` BGTaskScheduler shell. Keeping the math here
 * keeps the visible retry behavior identical once either platform has queued
 * the same outbox row.
 */

/** Terminal attempt count for cloud-folder content/metadata outbox rows. */
const val SHARED_BACKGROUND_MAX_OUTBOX_ATTEMPTS = 8

/** WorkManager EXPONENTIAL backoff mirrored for iOS BG task rescheduling. */
const val SHARED_BACKGROUND_CLOUD_FOLDER_BACKOFF_MS = 30_000L
const val SHARED_BACKGROUND_CLOUD_BOOK_DELETE_BACKOFF_MS = 10_000L
const val SHARED_BACKGROUND_ANNOTATION_EXPORT_BACKOFF_MS = 10_000L
const val SHARED_BACKGROUND_METADATA_EXTRACTION_BACKOFF_MS = 30_000L

/** Android claim limits: CloudFolderSyncWorker drains at most 500 rows per pass. */
const val SHARED_BACKGROUND_OUTBOX_BATCH_LIMIT = 500
const val SHARED_BACKGROUND_METADATA_OUTBOX_BATCH_LIMIT = 500

/** Metadata extraction backfill: batch 300, at most 3 attempts. */
const val SHARED_BACKGROUND_METADATA_EXTRACTION_BATCH = 300
const val SHARED_BACKGROUND_METADATA_EXTRACTION_MAX_RETRY = 3

/** Foreground head-listener debounce; background never polls. */
const val SHARED_BACKGROUND_HEAD_DEBOUNCE_MS = 500L

/** Local folder inventory freshness windows from MainViewModel. */
const val SHARED_BACKGROUND_INVENTORY_REFRESH_MS = 5L * 60L * 1_000L
const val SHARED_BACKGROUND_INVENTORY_RETRY_MS = 30L * 1_000L
const val SHARED_BACKGROUND_INVENTORY_STALE_MS = 60L * 1_000L

/** Swift outbox retry window already in LocalAccountController (base 5s, max 15m). */
const val SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS = 5_000L
const val SHARED_BACKGROUND_CLOUD_SYNC_RETRY_MAX_MS = 15L * 60L * 1_000L

/**
 * Android `CloudFolderSyncWorker.retryDelayMs`: `(1<<attempts)*1000`,
 * clamped to attempts 0..8. Attempt 8 is the quarantine boundary, not a delay.
 */
fun sharedCloudFolderOutboxRetryDelayMs(attempts: Int): Long =
    (1L shl attempts.coerceIn(0, SHARED_BACKGROUND_MAX_OUTBOX_ATTEMPTS)) * 1_000L

/** Whether an outbox row with [attempts] failures gets another attempt. */
fun shouldRetrySharedCloudFolderOutbox(attempts: Int): Boolean =
    attempts < SHARED_BACKGROUND_MAX_OUTBOX_ATTEMPTS

/** Whether a metadata-extraction batch with [failedCount] failures retries. */
fun shouldRetrySharedMetadataExtraction(failedCount: Int, runAttempt: Int): Boolean =
    failedCount > 0 && runAttempt < SHARED_BACKGROUND_METADATA_EXTRACTION_MAX_RETRY

/**
 * Whether a local folder inventory row needs a metadata-only refresh.
 * Mirrors MainViewModel.scheduleLocalCloudFolderInventories exactly.
 */
fun shouldRefreshSharedFolderInventory(
    hasInventory: Boolean,
    inventoryState: String?,
    nowMs: Long,
    scannedAtMs: Long,
    updatedAtMs: Long,
): Boolean {
    if (!hasInventory) return true
    return when (inventoryState) {
        "SCANNING" -> nowMs - updatedAtMs > SHARED_BACKGROUND_INVENTORY_STALE_MS
        "FAILED" -> nowMs - updatedAtMs > SHARED_BACKGROUND_INVENTORY_RETRY_MS
        else -> nowMs - scannedAtMs > SHARED_BACKGROUND_INVENTORY_REFRESH_MS
    }
}

enum class SharedBackgroundTaskKind {
    /** Short refresh: outbox retry, head re-check, entitlement re-query. */
    APP_REFRESH,

    /** Longer processing: folder index pass, upload drain, GC, cleanup. */
    PROCESSING,
}

/**
 * One-shot background request. Never periodic: Android has zero
 * PeriodicWorkRequests, so iOS schedules at most one retry per trigger and
 * re-schedules only when work remains after a pass.
 */
data class SharedBackgroundSyncRequest(
    val kind: SharedBackgroundTaskKind,
    /** Earliest run, mirroring WorkManager backoff/initialDelay. */
    val earliestBeginMsFromNow: Long,
    /** Requires network, mirroring WorkManager CONNECTED constraints. */
    val requiresNetwork: Boolean = true,
)

/**
 * One-shot refresh request for pending cloud work. Returns null when there is
 * nothing durable to resume (no signed-in account, sync off, no pending
 * snapshot/outbox/selection), mirroring Android's account + eligibility gates
 * before any enqueue. Shared-first so iOS and tests share the exact gate.
 */
fun sharedBackgroundRefreshRequest(
    hasAccount: Boolean,
    isSyncEnabled: Boolean,
    hasPendingSnapshot: Boolean,
    hasPendingOutbox: Boolean,
    hasSelectedCloudRoots: Boolean,
): SharedBackgroundSyncRequest? {
    if (!hasAccount || !isSyncEnabled) return null
    if (!hasPendingSnapshot && !hasPendingOutbox && !hasSelectedCloudRoots) return null
    return SharedBackgroundSyncRequest(
        kind = SharedBackgroundTaskKind.APP_REFRESH,
        earliestBeginMsFromNow = SHARED_BACKGROUND_CLOUD_SYNC_RETRY_BASE_MS,
        requiresNetwork = true,
    )
}

/**
 * One-shot processing request for a full folder pass / GC / cleanup. Mirrors
 * Android's explicit user/refresh triggers (REPLACE) rather than periodic
 * polling: call only from a real trigger (foreground resume, manual refresh,
 * selection change), never on a timer.
 */
fun sharedBackgroundProcessingRequest(
    hasAccount: Boolean,
    isSyncEnabled: Boolean,
    isProUser: Boolean,
    hasEnabledFolders: Boolean,
): SharedBackgroundSyncRequest? {
    if (!hasAccount || !isSyncEnabled || !isProUser || !hasEnabledFolders) return null
    return SharedBackgroundSyncRequest(
        kind = SharedBackgroundTaskKind.PROCESSING,
        earliestBeginMsFromNow = SHARED_BACKGROUND_CLOUD_FOLDER_BACKOFF_MS,
        requiresNetwork = true,
    )
}
