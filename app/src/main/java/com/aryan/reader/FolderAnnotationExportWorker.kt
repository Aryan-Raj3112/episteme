package com.aryan.reader

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.PendingFolderAnnotationExportEntity
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.shared.pdf.folderAnnotationExportDelayMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Durable, per-book folder-sidecar outbox. A row is deleted only when the exact revision that was
 * exported is still current, so edits arriving during an export remain pending.
 */
class FolderAnnotationExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return@withContext Result.failure()
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.pendingFolderAnnotationExportDao()
        val pending = dao.get(bookId) ?: return@withContext Result.success()
        dao.recordAttempt(bookId, pending.revision, System.currentTimeMillis())

        val saved = RecentFilesRepository(applicationContext).syncLocalAnnotationsToFolder(bookId)
        if (!saved) return@withContext Result.retry()

        dao.deleteRevision(bookId, pending.revision)
        val newer = dao.get(bookId)
        if (newer != null) {
            schedule(applicationContext, newer, replace = false, append = true)
        }
        Result.success()
    }

    companion object {
        private const val KEY_BOOK_ID = "book_id"
        suspend fun markPending(
            context: Context,
            bookId: String,
            reason: String,
            immediate: Boolean = false,
            now: Long = System.currentTimeMillis(),
        ): PendingFolderAnnotationExportEntity {
            val database = AppDatabase.getDatabase(context)
            val dao = database.pendingFolderAnnotationExportDao()
            val pending = database.withTransaction {
                val previous = dao.get(bookId)
                PendingFolderAnnotationExportEntity(
                    bookId = bookId,
                    revision = (previous?.revision ?: 0L) + 1L,
                    dirtySince = previous?.dirtySince ?: now,
                    updatedAt = now,
                    lastAttemptAt = previous?.lastAttemptAt ?: 0L,
                    attemptCount = previous?.attemptCount ?: 0,
                    reason = reason,
                ).also { dao.put(it) }
            }
            schedule(context, pending, replace = true, immediate = immediate)
            return pending
        }

        suspend fun scheduleAllPending(context: Context) {
            AppDatabase.getDatabase(context).pendingFolderAnnotationExportDao().getAll().forEach {
                schedule(context, it, replace = false)
            }
        }

        suspend fun schedulePendingNow(context: Context, bookId: String) {
            AppDatabase.getDatabase(context).pendingFolderAnnotationExportDao().get(bookId)?.let {
                schedule(context, it, replace = true, immediate = true)
            }
        }

        private fun schedule(
            context: Context,
            pending: PendingFolderAnnotationExportEntity,
            replace: Boolean,
            immediate: Boolean = false,
            append: Boolean = false,
        ) {
            val delay = folderAnnotationExportDelayMillis(
                dirtySinceMillis = pending.dirtySince,
                nowMillis = System.currentTimeMillis(),
                immediate = immediate,
            )
            val request = OneTimeWorkRequestBuilder<FolderAnnotationExportWorker>()
                .setInputData(Data.Builder().putString(KEY_BOOK_ID, pending.bookId).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(pending.bookId),
                when {
                    append || (replace && !immediate && delay == 0L) ->
                        ExistingWorkPolicy.APPEND_OR_REPLACE
                    replace -> ExistingWorkPolicy.REPLACE
                    else -> ExistingWorkPolicy.KEEP
                },
                request,
            )
        }

        internal fun uniqueWorkName(bookId: String): String =
            "FolderAnnotationExport_${bookId.hashCode().toUInt().toString(16)}"
    }
}
