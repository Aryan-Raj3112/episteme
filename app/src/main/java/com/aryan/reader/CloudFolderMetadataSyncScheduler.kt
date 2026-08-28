package com.aryan.reader

import android.content.Context
import com.aryan.reader.data.CloudFolderMetadataOutboxEntity
import com.aryan.reader.data.CloudFolderSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges the existing folder-sidecar writers to the durable cloud-folder
 * protocol.  A sidecar is the source of truth; this class only records a
 * coalesced wake-up after the writer has validated the committed file.
 */
internal object CloudFolderMetadataSyncScheduler {
    suspend fun onSidecarCommitted(
        context: Context,
        sourceFolderUri: String,
        bookId: String,
        kind: String,
        /** The exact canonical payload installed by the sidecar writer. */
        payload: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (!BuildConfig.IS_PRO) return@withContext
        val payloadInfo = cloudFolderSidecarPayloadInfo(payload)
        val accountId = AuthRepository(context.applicationContext).getSignedInUser()?.uid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: run {
                cloudFolderLogD(
                    "event=metadata_sidecar_commit_skip reason=no_account " +
                        "book=${cloudFolderSafeId(bookId)} kind=$kind ${payloadInfo.toLogFields()}",
                )
                return@withContext
            }
        val normalizedUri = sourceFolderUri.trim().takeIf { it.isNotBlank() }
            ?: run {
                cloudFolderLogD(
                    "event=metadata_sidecar_commit_skip reason=no_folder " +
                        "account=${cloudFolderSafeId(accountId)} book=${cloudFolderSafeId(bookId)} " +
                        "kind=$kind ${payloadInfo.toLogFields()}",
                )
                return@withContext
            }
        val enqueueCorrelation = cloudFolderSyncCorrelationId(
            "metadata-sidecar",
            accountId,
            normalizedUri,
            bookId,
            kind,
        )
        val repository = CloudFolderSyncRepository(context.applicationContext, accountId)
        val rootId = CloudFolderAppStoragePrefs.rootIdForUri(
            context = context.applicationContext,
            accountId = accountId,
            uriString = normalizedUri,
        ) ?: repository.findBindingForLocalUri(normalizedUri)?.rootId
        if (rootId.isNullOrBlank()) {
            cloudFolderLogD(
                "event=metadata_sidecar_commit_skip reason=unbound_folder " +
                    "correlation=$enqueueCorrelation account=${cloudFolderSafeId(accountId)} " +
                    "book=${cloudFolderSafeId(bookId)} kind=$kind ${payloadInfo.toLogFields()}",
            )
            return@withContext
        }
        val binding = repository.getBinding(rootId)
        if (binding == null || binding.materializationMode == com.aryan.reader.shared.CloudFolderMaterializationMode.CLOUD_ONLY) {
            cloudFolderLogD(
                "event=metadata_sidecar_commit_skip reason=no_local_materialization " +
                    "correlation=$enqueueCorrelation account=${cloudFolderSafeId(accountId)} " +
                    "root=${cloudFolderSafeId(rootId)} book=${cloudFolderSafeId(bookId)} " +
                    "kind=$kind ${payloadInfo.toLogFields()}",
            )
            return@withContext
        }
        val pending = repository.markMetadataOutboxPending(
            rootId = rootId,
            bookId = bookId,
            dirtyKinds = kind,
        ) ?: return@withContext
        val operation = cloudFolderOperationId(
            "metadata-sidecar",
            accountId,
            rootId,
            bookId,
            pending.generation,
        )
        val correlation = cloudFolderSyncCorrelationId(
            "metadata-sidecar",
            accountId,
            rootId,
            bookId,
            pending.generation,
        )
        cloudFolderLogD(
            "event=metadata_sidecar_commit root=${cloudFolderSafeId(rootId)} " +
                "book=${cloudFolderSafeId(bookId)} operation=$operation correlation=$correlation " +
                "generation=${pending.generation} kinds=${pending.dirtyKinds} " +
                "${payloadInfo.toLogFields()} state=${pending.state}",
        )
        if (isCloudFolderSyncEnabled(context.applicationContext)) {
            cloudFolderLogD(
                "event=metadata_worker_enqueue root=${cloudFolderSafeId(rootId)} " +
                    "book=${cloudFolderSafeId(bookId)} operation=$operation correlation=$correlation " +
                    "generation=${pending.generation} reason=sidecar_commit",
            )
            try {
                CloudFolderSyncWorker.enqueue(
                    context = context.applicationContext,
                    accountId = accountId,
                    rootId = rootId,
                    direction = com.aryan.reader.shared.CloudFolderSyncDirection.NONE,
                    replace = false,
                    metadataOnly = true,
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                // The Room row remains pending and will be retried by the
                // next startup/manual pass. Keep the failure attributable to
                // the scheduling boundary without exposing WorkManager or
                // provider details in logcat.
                cloudFolderLogError(
                    event = "metadata_worker_enqueue",
                    error = error,
                    details = "root=${cloudFolderSafeId(rootId)} book=${cloudFolderSafeId(bookId)} " +
                        "operation=$operation correlation=$correlation generation=${pending.generation} " +
                        "stage=enqueue category=metadata_work_enqueue result=failure",
                )
            }
        } else {
            cloudFolderLogD(
                "event=metadata_worker_enqueue_skip root=${cloudFolderSafeId(rootId)} " +
                    "book=${cloudFolderSafeId(bookId)} operation=$operation correlation=$correlation " +
                    "generation=${pending.generation} reason=sync_disabled",
            )
        }
    }

    const val METADATA_KIND: String = CloudFolderMetadataOutboxEntity.KIND_METADATA
    const val ANNOTATIONS_KIND: String = CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS
}
