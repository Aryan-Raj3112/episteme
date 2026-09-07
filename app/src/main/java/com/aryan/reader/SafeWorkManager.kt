package com.aryan.reader

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.UUID

/**
 * Fault-tolerant entry point for all WorkManager scheduling in the app.
 *
 * WorkManager initializes its JobScheduler-backed scheduler when the
 * WorkManager instance is first created. On a small number of devices with
 * broken firmware (framework reports API 34+ but
 * `JobScheduler.forNamespace(String)` is missing), that initialization throws
 * `NoSuchMethodError` and used to kill the app during startup, before any app
 * code ran (via `InitializationProvider`). WorkManager is therefore
 * initialized on demand (auto-init is disabled in the manifest) and every
 * access goes through here, so affected devices degrade to "no background
 * work" instead of crashing.
 *
 * On healthy devices this is a pure pass-through: the same WorkManager
 * instance and the same scheduling semantics, plus a log line only when
 * something actually fails.
 *
 * Note: this stays in the Android app module (not `shared`) because
 * WorkManager is an Android-platform API with no common equivalent.
 */
object SafeWorkManager {

    @Volatile
    private var jobSchedulerBroken = false

    /** True once this process has proven its JobScheduler cannot back WorkManager. */
    fun isJobSchedulerBroken(): Boolean = jobSchedulerBroken

    @VisibleForTesting
    fun resetForTests() {
        jobSchedulerBroken = false
    }

    /**
     * Returns the WorkManager instance, or null when it cannot be created on
     * this device. A [LinkageError] (e.g. the missing `forNamespace` method)
     * is permanent for the device, so it latches and later calls short-circuit
     * without rethrowing. Other failures stay retryable.
     */
    fun getOrNull(context: Context): WorkManager? {
        if (jobSchedulerBroken) return null
        return try {
            WorkManager.getInstance(context.applicationContext)
        } catch (t: Throwable) {
            if (t is LinkageError) jobSchedulerBroken = true
            Timber.e(t, "WorkManager unavailable; background work will be skipped")
            null
        }
    }

    /** See [WorkManager.enqueueUniqueWork]. False when the work could not be scheduled. */
    fun enqueueUniqueWork(
        context: Context,
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): Boolean {
        val workManager = getOrNull(context) ?: return false
        return try {
            workManager.enqueueUniqueWork(uniqueWorkName, existingWorkPolicy, request)
            true
        } catch (t: Throwable) {
            Timber.e(t, "Failed to enqueue unique work $uniqueWorkName; skipping")
            false
        }
    }

    /** See [WorkManager.enqueue]. False when the work could not be scheduled. */
    fun enqueue(context: Context, request: WorkRequest): Boolean {
        val workManager = getOrNull(context) ?: return false
        return try {
            workManager.enqueue(request)
            true
        } catch (t: Throwable) {
            Timber.e(t, "Failed to enqueue work; skipping")
            false
        }
    }

    fun cancelUniqueWork(context: Context, uniqueWorkName: String): Boolean {
        val workManager = getOrNull(context) ?: return false
        return try {
            workManager.cancelUniqueWork(uniqueWorkName)
            true
        } catch (t: Throwable) {
            Timber.e(t, "Failed to cancel unique work $uniqueWorkName; skipping")
            false
        }
    }

    fun cancelAllWorkByTag(context: Context, tag: String): Boolean {
        val workManager = getOrNull(context) ?: return false
        return try {
            workManager.cancelAllWorkByTag(tag)
            true
        } catch (t: Throwable) {
            Timber.e(t, "Failed to cancel work by tag $tag; skipping")
            false
        }
    }

    fun pruneWork(context: Context): Boolean {
        val workManager = getOrNull(context) ?: return false
        return try {
            workManager.pruneWork()
            true
        } catch (t: Throwable) {
            Timber.e(t, "Failed to prune work; skipping")
            false
        }
    }

    /** Null when WorkManager is unavailable; callers must skip observation in that case. */
    fun getWorkInfoByIdFlow(context: Context, id: UUID): Flow<WorkInfo?>? {
        val workManager = getOrNull(context) ?: return null
        return try {
            workManager.getWorkInfoByIdFlow(id)
        } catch (t: Throwable) {
            Timber.e(t, "Failed to observe work $id; skipping")
            null
        }
    }
}
