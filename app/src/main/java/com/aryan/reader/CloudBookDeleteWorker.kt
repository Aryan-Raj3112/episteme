package com.aryan.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.data.CloudBookDeleteIntentEntity
import com.aryan.reader.data.CloudBookDeletePersistence
import com.aryan.reader.data.DriveFile
import com.aryan.reader.data.FirestoreRepository
import com.aryan.reader.data.GoogleDriveRepository
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.data.claimedLocalGeneration
import com.aryan.reader.data.matchesCloudBookLocalGeneration
import com.aryan.reader.shared.CloudBookTombstone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Completes cloud-book deletion after the initiating device has returned to
 * the library. Room is the source of truth: a worker may be killed at any
 * point and a later run repeats each idempotent operation safely.
 *
 * Firestore tombstones are published before Drive payload cleanup. That makes
 * the delete authoritative for other devices even when Drive credentials are
 * unavailable; the versioned Room row keeps physical cleanup retryable and
 * prevents an older worker completion from deleting a newer intent.
 */
class CloudBookDeleteWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val driveRepository by lazy { GoogleDriveRepository() }
    private val firestoreRepository by lazy { FirestoreRepository() }
    private val authRepository by lazy { AuthRepository(applicationContext) }
    private val recentFilesRepository by lazy { RecentFilesRepository(applicationContext) }
    private val persistence by lazy { CloudBookDeletePersistence(applicationContext) }
    private val preferences: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val accountId = inputData.getString(KEY_ACCOUNT_ID)?.trim().orEmpty()
        if (accountId.isBlank()) {
            Timber.w("Cloud-book delete worker started without an account")
            return@withContext Result.failure()
        }

        val signedInAccount = authRepository.getSignedInUser()?.uid?.trim().orEmpty()
        if (signedInAccount != accountId) {
            // Never process a previous account's queue after an account
            // switch. The account-scoped rows remain for a later sign-in.
            Timber.i(
                "Cloud-book delete worker skipped account mismatch requested=$accountId " +
                    "current=${signedInAccount.ifBlank { "none" }}",
            )
            return@withContext Result.success()
        }

        // OSS has no remote account/token. Keeping this worker in the shared
        // Android source set simplifies variant wiring, while an OSS queue is
        // never acted on as a fake successful cloud deletion.
        if (!BuildConfig.IS_PRO) return@withContext Result.success()

        try {
            CloudBookSyncBarrier.withAccountLock(accountId) {
                processAccount(accountId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Cloud-book deletion worker failed account=$accountId")
            Result.retry()
        }
    }

    private suspend fun processAccount(accountId: String): Result {
        while (true) {
            currentCoroutineContext().ensureActive()
            val pending = persistence.workPending(accountId)
            if (pending.isEmpty()) return Result.success()

            // Atomically claim the local generation before publishing. If a
            // newer row is already present, quarantine without touching
            // Firestore so an old delete cannot remove a re-import.
            val unclaimed = pending.filter {
                it.state == CloudBookDeleteIntentEntity.STATE_PENDING
            }
            unclaimed.forEach { persistence.claimLocalGeneration(it) }

            val claimed = persistence.workPending(accountId)
            claimed
                .filter { it.state == CloudBookDeleteIntentEntity.STATE_LOCAL_CLAIMED }
                .forEach { persistence.revalidateLocalGeneration(it) }

            val publishable = persistence.workPending(accountId).filter {
                it.state == CloudBookDeleteIntentEntity.STATE_LOCAL_CLAIMED
            }
            val remoteBooks = if (publishable.isNotEmpty()) {
                firestoreRepository.getAllBooks(accountId).associateBy { it.bookId }
            } else {
                emptyMap()
            }
            if (publishable.isNotEmpty()) {
                val metadata = publishable.map { intent ->
                    cloudBookDeletionMetadata(
                        tombstone = intent.toTombstone(),
                        remote = remoteBooks[intent.bookId],
                        nowMillis = System.currentTimeMillis(),
                    )
                }
                ensureAccountStillActive(accountId)
                firestoreRepository.syncBookMetadataDeletions(
                    userId = accountId,
                    books = metadata,
                    originDeviceId = installationId(),
                )
                // Exact versions make this safe if a new delete was enqueued
                // while the batch was in flight.
                publishable.forEach { persistence.markTombstonePublished(it) }
            }

            val published = persistence.workPending(accountId)
            if (published.isEmpty()) continue

            // Finalize any local row left behind by a process death before
            // asking Drive for credentials. A newer local incarnation is a
            // real conflict; quarantine it instead of deleting it.
            val physicallyDeletable = mutableListOf<CloudBookDeleteIntentEntity>()
            var localCleanupNeedsRetry = false
            published.forEach { intent ->
                when (finalizeLocal(intent)) {
                    LocalFinalizeResult.READY -> physicallyDeletable += intent
                    LocalFinalizeResult.QUARANTINED -> Unit
                    LocalFinalizeResult.RETRY -> localCleanupNeedsRetry = true
                }
            }
            if (localCleanupNeedsRetry) return Result.retry()
            if (physicallyDeletable.isEmpty()) continue

            val accessToken = driveRepository.getAccessToken(applicationContext)
                ?: return Result.retry()
            val remoteFiles = driveRepository.getFilesOrThrow(accessToken).files
            val remoteFilesByName = remoteFiles.groupBy(DriveFile::name)
            val remoteBooksForPayloads = if (physicallyDeletable.any {
                    cloudBookDeletionType(it.toTombstone(), null) == null
                }
            ) {
                firestoreRepository.getAllBooks(accountId).associateBy { it.bookId }
            } else {
                emptyMap()
            }

            val unknownType = physicallyDeletable.filter {
                cloudBookDeletionType(it.toTombstone(), remoteBooksForPayloads[it.bookId]) == null
            }
            unknownType.forEach { intent ->
                persistence.quarantine(intent, "unknown cloud book type; Drive payload was not removed")
            }
            val knownType = physicallyDeletable.filterNot { it in unknownType }
            if (knownType.isEmpty()) continue

            val failedBookIds = deleteDrivePayloads(
                accessToken = accessToken,
                fileIdsByBook = knownType.associate { intent ->
                    intent.bookId to cloudBookDeletionPayloadIds(
                        tombstone = intent.toTombstone(),
                        remote = remoteBooksForPayloads[intent.bookId],
                        remoteFilesByName = remoteFilesByName,
                    )
                },
                accountId = accountId,
            )

            knownType
                .filterNot { it.bookId in failedBookIds }
                .forEach { persistence.removeIfVersion(it) }

            if (failedBookIds.isNotEmpty()) {
                Timber.w(
                    "Cloud-book deletion will retry failed books account=$accountId " +
                        "failed=${failedBookIds.size}",
                )
                return Result.retry()
            }
            // Re-read the queue so an intent appended during this batch is
            // consumed by this unique worker where possible.
        }
    }

    private suspend fun finalizeLocal(intent: CloudBookDeleteIntentEntity): LocalFinalizeResult {
        val expected = intent.claimedLocalGeneration()
        if (expected == null) {
            // A missing local row is safe to treat as finalized (the
            // initiating device may already have removed it). If a row is
            // still present, an unclaimed recovery intent must not guess at
            // its generation.
            if (recentFilesRepository.getFileByBookId(intent.bookId) == null) {
                return LocalFinalizeResult.READY
            }
            persistence.quarantine(
                intent,
                "local generation was not durably claimed before finalization",
            )
            Timber.w("Skipped local cleanup for unclaimed generation book=${intent.bookId}")
            return LocalFinalizeResult.QUARANTINED
        }
        return try {
            // Recovery must be database-only. A separate generation check
            // followed by deleting a URI can race a re-import; the repository
            // performs the hide/delete comparison in one Room transaction and
            // leaves the source/artifacts for an orphan sweeper or manual
            // cleanup once no newer incarnation uses them.
            if (recentFilesRepository.removeCloudDeleteGenerationFromDatabase(intent.bookId, expected)) {
                LocalFinalizeResult.READY
            } else {
                val latest = recentFilesRepository.getFileByBookId(intent.bookId)
                if (latest != null && !latest.matchesCloudBookLocalGeneration(expected)) {
                    persistence.quarantine(intent, "local generation changed during finalization")
                    LocalFinalizeResult.QUARANTINED
                } else {
                    Timber.w("Local delete did not remove book row book=${intent.bookId}")
                    LocalFinalizeResult.RETRY
                }
            }
        } catch (error: Exception) {
            Timber.e(error, "Unable to finalize local cloud-book delete book=${intent.bookId}")
            LocalFinalizeResult.RETRY
        }
    }

    private enum class LocalFinalizeResult {
        READY,
        QUARANTINED,
        RETRY,
    }

    private suspend fun deleteDrivePayloads(
        accessToken: String,
        fileIdsByBook: Map<String, List<String>>,
        accountId: String,
    ): Set<String> = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLEL_BOOK_DELETIONS)
        fileIdsByBook.map { (bookId, fileIds) ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    ensureAccountStillActive(accountId)
                    try {
                        fileIds.forEach { fileId ->
                            ensureActive()
                            // The repository treats 404 as idempotent success.
                            driveRepository.deleteDriveFileOrThrow(accessToken, fileId)
                        }
                        null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.e(error, "Drive payload cleanup failed book=$bookId account=$accountId")
                        bookId
                    }
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    private suspend fun ensureAccountStillActive(accountId: String) {
        val activeAccount = authRepository.getSignedInUser()?.uid?.trim().orEmpty()
        if (activeAccount != accountId) {
            throw CancellationException("Cloud-book deletion account changed")
        }
    }

    private fun installationId(): String {
        var value = preferences.getString(KEY_INSTALLATION_ID, null)
        if (value.isNullOrBlank()) {
            value = java.util.UUID.randomUUID().toString()
            preferences.edit().putString(KEY_INSTALLATION_ID, value).apply()
        }
        return value
    }

    private fun CloudBookDeleteIntentEntity.toTombstone() = CloudBookTombstone(
        bookId = bookId,
        type = type,
        deletedAt = requestedAt,
    )

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val PREFERENCES_NAME = "reader_user_prefs"
        private const val WORK_NAME_PREFIX = "CloudBookDeleteWorker_"
        private const val MAX_PARALLEL_BOOK_DELETIONS = 4

        fun enqueue(context: Context, accountId: String) {
            val normalizedAccountId = accountId.trim()
            require(normalizedAccountId.isNotBlank()) {
                "Cloud-book deletion work requires an account ID"
            }
            val request = OneTimeWorkRequestBuilder<CloudBookDeleteWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_ACCOUNT_ID, normalizedAccountId)
                        .build(),
                )
                .addTag(accountTag(normalizedAccountId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10L,
                    TimeUnit.SECONDS,
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(normalizedAccountId),
                // APPEND_OR_REPLACE captures a request added while a worker
                // is running without cancelling its current batch.
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun cancelForAccount(context: Context, accountId: String) {
            val normalizedAccountId = accountId.trim()
            if (normalizedAccountId.isBlank()) return
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(workName(normalizedAccountId))
        }

        private fun workName(accountId: String): String = WORK_NAME_PREFIX + accountId

        private fun accountTag(accountId: String): String = "cloud_book_delete_account_$accountId"
    }
}
