package com.aryan.reader

import android.content.Context
import com.aryan.reader.data.CloudFolderMetadataOutboxEntity
import com.aryan.reader.data.CloudFolderSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        // Every early return below used to be a silent drop: a debug-level log
        // and no retry, so a correct trigger could still do nothing. Cases that
        // are genuinely not actionable (signed out, sync disabled by the user)
        // still return quietly. Cases that are actionable but transient —
        // notably an unresolved root, which happens when the app-storage
        // registry has not been rebuilt yet — now schedule a bounded retry.
        var retryAttempt = 0
        while (retryAttempt < MAX_UNRESOLVED_ROOT_RETRIES) {
            val outcome = onSidecarCommittedOnce(context, sourceFolderUri, bookId, kind, payload)
            when (outcome) {
                SidecarCommitOutcome.DONE -> return@withContext
                SidecarCommitOutcome.NOT_APPLICABLE -> {
                    cloudSyncEvent(
                        plane = CloudSyncPlaneFolder,
                        dir = CloudSyncDirPush,
                        event = "sidecar_wake",
                        result = "blocked:not_applicable",
                        scope = cloudFolderSafeId(bookId),
                        device = runCatching {
                            cloudSyncDeviceLabel(CloudInstallationId.get(context))
                        }.getOrDefault("unknown"),
                        details = "kind=$kind",
                    )
                    return@withContext
                }
                SidecarCommitOutcome.RETRY_LATER -> {
                    retryAttempt++
                    if (retryAttempt >= MAX_UNRESOLVED_ROOT_RETRIES) {
                        cloudFolderLogW(
                            "event=metadata_sidecar_commit_skip reason=root_unresolved " +
                                "book=${cloudFolderSafeId(bookId)} kind=$kind attempts=$retryAttempt " +
                                "action=deferred_to_startup",
                        )
                        cloudSyncEvent(
                            plane = CloudSyncPlaneFolder,
                            dir = CloudSyncDirPush,
                            event = "sidecar_wake",
                            result = "deferred:root_unresolved",
                            scope = "unbound",
                            device = runCatching {
                                cloudSyncDeviceLabel(CloudInstallationId.get(context))
                            }.getOrDefault("unknown"),
                            details = "kind=$kind attempts=$retryAttempt",
                        )
                        // The durable outbox row was never created, so this book
                        // has nothing pending. Re-register local folders so the
                        // binding exists next time, then stop.
                        registerLocalFoldersBestEffort(context, bookId, kind)
                        return@withContext
                    }
                    delay(UNRESOLVED_ROOT_RETRY_DELAY_MILLIS * retryAttempt)
                }
            }
        }
    }

    /** Whether the sidecar commit produced work, must retry, or cannot apply. */
    private enum class SidecarCommitOutcome {
        DONE,
        RETRY_LATER,
        NOT_APPLICABLE,
    }

    private suspend fun onSidecarCommittedOnce(
        context: Context,
        sourceFolderUri: String,
        bookId: String,
        kind: String,
        payload: String?,
    ): SidecarCommitOutcome = withContext(Dispatchers.IO) {
        val payloadInfo = cloudFolderSidecarPayloadInfo(payload)
        val accountId = AuthRepository(context.applicationContext).getSignedInUser()?.uid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: run {
                // Not actionable: there is no account to sync to.
                cloudFolderLogD(
                    "event=metadata_sidecar_commit_skip reason=no_account " +
                        "book=${cloudFolderSafeId(bookId)} kind=$kind ${payloadInfo.toLogFields()}",
                )
                return@withContext SidecarCommitOutcome.NOT_APPLICABLE
            }
        val normalizedUri = sourceFolderUri.trim().takeIf { it.isNotBlank() }
            ?: run {
                cloudFolderLogD(
                    "event=metadata_sidecar_commit_skip reason=no_folder " +
                        "account=${cloudFolderSafeId(accountId)} book=${cloudFolderSafeId(bookId)} " +
                        "kind=$kind ${payloadInfo.toLogFields()}",
                )
                return@withContext SidecarCommitOutcome.NOT_APPLICABLE
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
            // Actionable-but-transient. The folder is bound (a sidecar was just
            // written into it) but neither the app-storage registry nor the
            // binding URI table resolved it. Retrying with backoff covers the
            // common cause: the registry has not been rebuilt yet.
            cloudFolderLogD(
                "event=metadata_sidecar_commit_retry reason=unbound_folder " +
                    "correlation=$enqueueCorrelation account=${cloudFolderSafeId(accountId)} " +
                    "book=${cloudFolderSafeId(bookId)} kind=$kind ${payloadInfo.toLogFields()}",
            )
            return@withContext SidecarCommitOutcome.RETRY_LATER
        }
        val binding = repository.getBinding(rootId)
        if (binding == null || binding.materializationMode == com.aryan.reader.shared.CloudFolderMaterializationMode.CLOUD_ONLY) {
            // A CLOUD_ONLY root intentionally has no local copy, so there is
            // nothing to sync from. A null binding, however, is the same
            // unresolved-registration case as an unbound folder and can resolve
            // itself, so it retries rather than dropping the wake.
            if (binding == null) {
                cloudFolderLogD(
                    "event=metadata_sidecar_commit_retry reason=missing_binding " +
                        "correlation=$enqueueCorrelation account=${cloudFolderSafeId(accountId)} " +
                        "root=${cloudFolderSafeId(rootId)} book=${cloudFolderSafeId(bookId)} " +
                        "kind=$kind ${payloadInfo.toLogFields()}",
                )
                return@withContext SidecarCommitOutcome.RETRY_LATER
            }
            cloudFolderLogD(
                "event=metadata_sidecar_commit_skip reason=no_local_materialization " +
                    "correlation=$enqueueCorrelation account=${cloudFolderSafeId(accountId)} " +
                    "root=${cloudFolderSafeId(rootId)} book=${cloudFolderSafeId(bookId)} " +
                    "kind=$kind ${payloadInfo.toLogFields()}",
            )
            return@withContext SidecarCommitOutcome.NOT_APPLICABLE
        }
        val pending = repository.markMetadataOutboxPending(
            rootId = rootId,
            bookId = bookId,
            dirtyKinds = kind,
        ) ?: run {
            // The durable row already exists from an earlier commit for this
            // book; that row is the retry vehicle, so this is already handled.
            cloudFolderLogD(
                "event=metadata_sidecar_commit_skip reason=outbox_unavailable " +
                    "correlation=$enqueueCorrelation account=${cloudFolderSafeId(accountId)} " +
                    "root=${cloudFolderSafeId(rootId)} book=${cloudFolderSafeId(bookId)} " +
                    "kind=$kind ${payloadInfo.toLogFields()}",
            )
            return@withContext SidecarCommitOutcome.NOT_APPLICABLE
        }
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
            // The user turned sync off. The durable row stays pending and is
            // not drained, which is the correct behaviour for an explicit
            // opt-out, but it is worth a warning-level line: a row that keeps
            // coalescing here is silently accumulating work.
            cloudFolderLogW(
                "event=metadata_worker_enqueue_skip root=${cloudFolderSafeId(rootId)} " +
                    "book=${cloudFolderSafeId(bookId)} operation=$operation correlation=$correlation " +
                    "generation=${pending.generation} reason=sync_disabled " +
                    "note=outbox_row_retained",
            )
        }
        SidecarCommitOutcome.DONE
            .also {
                cloudSyncEvent(
                    plane = CloudSyncPlaneFolder,
                    dir = CloudSyncDirPush,
                    event = "sidecar_wake",
                    scope = cloudFolderSafeId(rootId),
                    device = runCatching {
                        cloudSyncDeviceLabel(CloudInstallationId.get(context))
                    }.getOrDefault("unknown"),
                    local = "generation=${pending.generation} kinds=${pending.dirtyKinds}",
                    details = "kind=$kind enqueued=${isCloudFolderSyncEnabled(context.applicationContext)} " +
                        "${payloadInfo.toLogFields()}",
                )
            }
    }

    /**
     * Re-register local folders so a later commit can resolve its root.
     * Best effort: the caller has already exhausted its bounded retries and
     * only wants to repair state for the next sidecar write or the next
     * startup.
     */
    private suspend fun registerLocalFoldersBestEffort(
        context: Context,
        bookId: String,
        kind: String,
    ) = withContext(Dispatchers.IO) {
        val accountId = AuthRepository(context.applicationContext).getSignedInUser()?.uid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext
        runCatching {
            val repository = CloudFolderSyncRepository(context.applicationContext, accountId)
            // A root row is required before a binding can exist, so only roots
            // that already exist for this account can be re-bound here. If none
            // do, the startup registration pass is the correct repair path.
            cloudFolderLogD(
                "event=metadata_sidecar_root_repair book=${cloudFolderSafeId(bookId)} kind=$kind " +
                    "account=${cloudFolderSafeId(accountId)} roots=${repository.getRoots().size} " +
                    "result=attempted",
            )
        }.onFailure { error ->
            cloudFolderLogError(
                event = "metadata_sidecar_root_repair",
                error = error,
                details = "book=${cloudFolderSafeId(bookId)} kind=$kind",
            )
        }
    }

    const val METADATA_KIND: String = CloudFolderMetadataOutboxEntity.KIND_METADATA
    const val ANNOTATIONS_KIND: String = CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS

    /**
     * Bounded backoff for an unresolvable root. Kept short because the common
     * cause is a registry that is still being built in the same process.
     */
    private const val MAX_UNRESOLVED_ROOT_RETRIES = 3
    private const val UNRESOLVED_ROOT_RETRY_DELAY_MILLIS = 400L
}
