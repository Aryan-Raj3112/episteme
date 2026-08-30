package com.aryan.reader

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.data.CloudFolderManifestReadResult
import com.aryan.reader.data.CloudFolderManifestLeaseResult
import com.aryan.reader.data.legacyCloudFolderManifestHeadCandidate
import com.aryan.reader.data.CloudFolderMetadataOutboxEntity
import com.aryan.reader.data.CloudFolderOutboxEntity
import com.aryan.reader.data.CloudFolderSafEntry
import com.aryan.reader.data.CloudFolderSafScanResult
import com.aryan.reader.data.CloudFolderSafScanner
import com.aryan.reader.data.CloudFolderSyncRepository
import com.aryan.reader.data.CloudFolderManifestCodec
import com.aryan.reader.data.FirestoreRepository
import com.aryan.reader.data.GoogleDriveRepository
import com.aryan.reader.data.planCloudFolderGarbageCollection
import com.aryan.reader.shared.CloudFolderManifest
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderNode
import com.aryan.reader.shared.CloudFolderNodeKind
import com.aryan.reader.shared.CloudFolderPermissionState
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderRootStats
import com.aryan.reader.shared.CloudFolderSyncDirection
import com.aryan.reader.shared.CloudFolderSyncOperation
import com.aryan.reader.shared.CloudFolderSyncOperationKind
import com.aryan.reader.shared.CloudFolderSyncPhase
import com.aryan.reader.shared.CloudFolderSyncProgress
import com.aryan.reader.shared.CloudFolderConflictResolution
import com.aryan.reader.shared.CloudFolderTombstone
import com.aryan.reader.shared.canonicalCloudFolderContentHash
import com.aryan.reader.shared.defaultResolution
import com.aryan.reader.shared.isCloudFolderSha256
import com.aryan.reader.shared.normalizeCloudFolderRelativePath
import com.aryan.reader.shared.cloudFolderPathKey
import com.aryan.reader.shared.localFolderSyncMetadataFileName
import com.aryan.reader.shared.localFolderSyncAnnotationFileName
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.LOCAL_FOLDER_SIDECAR_HASH_PREFIX
import com.aryan.reader.shared.isCloudFolderMetadataSidecarPath
import com.aryan.reader.shared.planCloudFolderSync
import com.aryan.reader.shared.resolveCloudFolderSync
import com.aryan.reader.shared.stabilizedCloudFolderNodeMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CLOUD_FOLDER_ROOT_WORK_PREFIX = "CloudFolderSyncWorker"
private const val CLOUD_FOLDER_GC_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
private const val METADATA_CONFLICT_RETRY_DELAY_MILLIS = 5L * 60L * 1_000L

/**
 * The small amount of WorkManager state needed to choose a metadata wake-up
 * policy.  Keeping this decision pure makes the retry/revival contract easy
 * to test without depending on WorkManager's database.
 */
internal data class CloudFolderMetadataWorkState(
    val state: WorkInfo.State,
    val runAttemptCount: Int,
)

/**
 * Select a policy for a newly committed metadata sidecar.
 *
 * A RUNNING request may be mid-publish (Drive upload or Firestore lease
 * held); cancelling it abandons the lease and can strand the remote head for
 * the whole lease window.  New wakes therefore always append behind it — the
 * coalesced outbox row means the next attempt picks up the newest
 * generation.  A request waiting in backoff is different: no transfer is in
 * flight, and replacing it revives the newest generation promptly instead of
 * leaving it behind an exponentially growing retry chain.
 */
internal fun cloudFolderMetadataWorkPolicy(
    existing: List<CloudFolderMetadataWorkState>,
): ExistingWorkPolicy {
    val unfinished = existing.filter { state ->
        state.state == WorkInfo.State.RUNNING ||
            state.state == WorkInfo.State.ENQUEUED ||
            state.state == WorkInfo.State.BLOCKED
    }
    return when {
        unfinished.any { it.state == WorkInfo.State.RUNNING } -> ExistingWorkPolicy.APPEND_OR_REPLACE
        unfinished.isNotEmpty() -> ExistingWorkPolicy.REPLACE
        else -> ExistingWorkPolicy.KEEP
    }
}

/**
 * Durable Android executor for the shared cloud-folder protocol. A full SAF
 * scan and every file upload happen before the manifest is published. A
 * partial scan, failed stream, invalid hash, or failed manifest publication
 * therefore leaves the last committed manifest authoritative.
 */
class CloudFolderSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private lateinit var repository: CloudFolderSyncRepository
    // These repositories touch Google/Firebase SDK state during construction.
    // Keep them lazy so a disabled or stale worker can exit before requiring
    // an initialized Firebase app (and before doing any network setup).
    private val driveRepository by lazy { GoogleDriveRepository() }
    private val firestoreRepository by lazy { FirestoreRepository() }
    /** Set once per WorkManager request; included in every detailed event. */
    private var activeOperationId: String = "none"
    private var activeCorrelationId: String = "none"
    /** Last durable pipeline boundary reached by this request. */
    private var activeStage: String = "worker_start"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        GLOBAL_MUTEX.withLock {
            val startedAt = System.currentTimeMillis()
            if (!isCloudFolderSyncEnabled(applicationContext)) {
                cloudFolderLogI("event=worker_gate gate=sync_enabled result=disabled")
                return@withLock Result.success()
            }

            val direction = inputData.getString(KEY_DIRECTION)
                ?.let { runCatching { Direction.valueOf(it) }.getOrNull() }
                ?: Direction.SYNC
            val requestedRootId = inputData.getString(KEY_ROOT_ID)?.trim().orEmpty()
            val metadataOnly = inputData.getBoolean(KEY_METADATA_ONLY, false)
            val requestedAccountId = inputData.getString(KEY_ACCOUNT_ID)?.trim().orEmpty()
            activeOperationId = cloudFolderOperationId(
                "folder-worker",
                id.toString(),
                requestedAccountId,
                requestedRootId,
                direction.name,
                metadataOnly,
            )
            activeCorrelationId = cloudFolderSyncCorrelationId(
                "folder-worker",
                requestedAccountId,
                requestedRootId,
                direction.name,
            )
            activeStage = "account_gate"
            val currentAccountId = AuthRepository(applicationContext).getSignedInUser()?.uid?.trim().orEmpty()
            cloudFolderLogI(
                "event=worker_start direction=${direction.name} " +
                    "operation=$activeOperationId correlation=$activeCorrelationId " +
                    "account=${cloudFolderSafeId(requestedAccountId)} " +
                    "root=${cloudFolderSafeId(requestedRootId)} metadataOnly=$metadataOnly attempt=$runAttemptCount",
            )

            // WorkManager can outlive a Firebase session. Never touch the
            // database or Drive until the request's account matches the
            // currently authenticated Firebase account.
            if (requestedAccountId.isBlank() || currentAccountId.isBlank() || requestedAccountId != currentAccountId) {
                cloudFolderLogW(
                    "event=worker_gate gate=account result=mismatch " +
                        "requested=${cloudFolderSafeId(requestedAccountId)} " +
                        "current=${cloudFolderSafeId(currentAccountId)}",
                )
                return@withLock Result.success()
            }
            repository = CloudFolderSyncRepository(applicationContext, currentAccountId)
            cloudFolderLogD("event=worker_gate gate=account result=accepted account=${cloudFolderSafeId(currentAccountId)}")

            // Reset rows claimed by a process that was killed before it could
            // complete them. WorkManager may recreate this worker later.
            activeStage = "outbox_recovery"
            repository.resetRunningOutbox(now = System.currentTimeMillis())
            repository.resetRunningMetadataOutbox(now = System.currentTimeMillis())
            cloudFolderLogD(
                "event=outbox_recovery ${traceFields(requestedRootId)} result=reset",
            )

            activeStage = "drive_token"
            val accessToken = repositoryAccessToken() ?: run {
                cloudFolderLogW(
                    "event=worker_gate gate=drive_token result=missing " +
                        "account=${cloudFolderSafeId(currentAccountId)}",
                )
                return@withLock if (BuildConfig.IS_PRO) Result.retry() else Result.success()
            }
            cloudFolderLogD("event=worker_gate gate=drive_token result=available")

            try {
                if (direction == Direction.GC) {
                    activeStage = "garbage_collection"
                    runGarbageCollection(accessToken)
                } else if (direction == Direction.DELETE) {
                    activeStage = "delete_root"
                    deleteRootFromCloud(accessToken, requestedRootId)
                } else if (requestedRootId.isNotBlank()) {
                    if (direction == Direction.PULL) {
                        activeStage = "pull_root"
                        pullRoot(accessToken, requestedRootId)
                        if (repository.isIncluded(requestedRootId) &&
                            repository.getBinding(requestedRootId) != null
                        ) {
                            val progress = repository.getProgress(requestedRootId)
                            val stats = repository.getRoot(requestedRootId)?.stats
                            saveRootProgress(
                                rootId = requestedRootId,
                                phase = CloudFolderSyncPhase.SUCCEEDED,
                                completedFiles = progress?.totalFiles ?: stats?.fileCount ?: 0,
                                totalFiles = progress?.totalFiles ?: stats?.fileCount ?: 0,
                                completedBytes = progress?.totalBytes ?: stats?.totalBytes ?: 0L,
                                totalBytes = progress?.totalBytes ?: stats?.totalBytes ?: 0L,
                            )
                        }
                    } else {
                        activeStage = "sync_root"
                        syncRoot(accessToken, requestedRootId, direction, metadataOnly)
                    }
                } else if (direction == Direction.PULL) {
                    // A pull pass has two independent responsibilities:
                    // discover unbound roots for the incoming-folder prompt,
                    // and reconcile roots this device has already selected.
                    // The old discovery-only path did the former and silently
                    // skipped the latter, making manual/startup pulls appear
                    // successful while never importing remote sidecars.
                    activeStage = "pull_discovery"
                    discoverAndPull(accessToken)
                } else {
                    // A normal cloud sync also performs a metadata-only
                    // discovery pass. This makes device-2 roots visible to
                    // settings even when no local folder is indexed, while
                    // keeping byte materialization behind an explicit choice.
                    activeStage = "sync_discovery"
                    val discoveryFailure = discoverIncomingRoots(accessToken)
                    activeStage = "sync_selected_roots"
                    var selectedRootsFailure: Throwable? = null
                    try {
                        syncSelectedRoots(accessToken, direction)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        selectedRootsFailure = error
                    }
                    // Discovery and selected-root reconciliation are
                    // independent account operations. Preserve the first
                    // failure for WorkManager retry after both have had a
                    // chance to make progress.
                    (discoveryFailure ?: selectedRootsFailure)?.let { throw it }
                }
                // Wake an already-running ViewModel so a newly discovered
                // device-2 root is visible immediately, even when Settings
                // is not the current route. Persisted state remains the
                // source of truth across process death.
                CloudFolderSyncEvents.notifyStateChanged()
                cloudFolderLogI(
                    "event=worker_end result=success direction=${direction.name} " +
                        "${traceFields(requestedRootId)} " +
                        "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
                )
                Result.success()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                runCatching {
                    // Do not leave a claimed sidecar generation RUNNING when
                    // a process dies or a transfer fails before the manifest
                    // commit. The next WorkManager attempt can safely claim
                    // it again; exact-generation completion still protects
                    // edits that arrived in the meantime.
                    repository.resetRunningMetadataOutbox(
                        now = System.currentTimeMillis(),
                        error = error.message ?: "Cloud-folder metadata transfer failed",
                    )
                }
                if (requestedRootId.isNotBlank()) {
                    markRootFailure(requestedRootId, error)
                }
                // Stale Drive tokens are transient: re-run with a fresh token
                // instead of failing permanently. Everything else in the
                // deterministic set stops without user action.
                val terminal = cloudFolderFailureIsDeterministic(error) &&
                    !cloudFolderAuthFailureIsTransient(error)
                cloudFolderLogError(
                    event = "worker_end",
                    error = error,
                    details = "${traceFields(requestedRootId)} " +
                        "result=${if (terminal) "failure" else "retry"} " +
                        "direction=${direction.name} metadataOnly=$metadataOnly stage=$activeStage " +
                        "category=${cloudFolderStageCategory(activeStage, metadataOnly)} " +
                        "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
                )
                if (terminal) Result.failure() else Result.retry()
            }
        }
    }

    private fun traceFields(
        rootId: String? = null,
        bookId: String? = null,
        generation: Long? = null,
    ): String {
        val accountId = if (::repository.isInitialized) repository.accountId else "none"
        val correlation = if (!bookId.isNullOrBlank() && generation != null && !rootId.isNullOrBlank()) {
            cloudFolderSyncCorrelationId(
                "metadata-sidecar",
                accountId,
                rootId,
                bookId,
                generation,
            )
        } else {
            cloudFolderSyncCorrelationId(activeCorrelationId, rootId.orEmpty())
        }
        return "operation=$activeOperationId correlation=$correlation " +
            "root=${cloudFolderSafeId(rootId)} " +
            (bookId?.let { "book=${cloudFolderSafeId(it)} " }.orEmpty()) +
            (generation?.let { "generation=$it " }.orEmpty())
    }

    private fun cloudFolderStageCategory(stage: String, metadataOnly: Boolean): String = when (stage) {
        "metadata_outbox_claim" -> "metadata_outbox_claim"
        "metadata_target_resolution" -> "metadata_sidecar_resolution"
        "metadata_local_scan" -> "metadata_local_scan"
        "manifest_read" -> if (metadataOnly) "metadata_manifest_read" else "manifest_read"
        "resume_materialization" -> "materialization_recovery"
        else -> if (metadataOnly) "metadata_pipeline" else "folder_pipeline"
    }

    private fun logMetadataStageFailure(
        rootId: String,
        stage: String,
        category: String,
        error: Throwable,
        details: String = "",
    ) {
        if (error is CancellationException) return
        val safe = cloudFolderTransferFailure(error, stage, category)
        cloudFolderLogError(
            event = "metadata_stage_failure",
            error = safe,
            details = "${traceFields(rootId)} stage=$stage category=$category " +
                "result=failure ${details.trim()}".trim(),
        )
    }

    private suspend fun markRootFailure(rootId: String, error: Throwable) {
        val existingProgress = repository.getProgress(rootId)
        repository.saveProgress(
            (existingProgress ?: CloudFolderSyncProgress(rootId, CloudFolderSyncPhase.FAILED)).copy(
                phase = CloudFolderSyncPhase.FAILED,
                updatedAt = System.currentTimeMillis(),
                errorStatus = cloudFolderPersistedErrorStatus(error),
            ).sanitized()
        )
        try {
            repository.markBindingError(rootId, cloudFolderUserFacingError(error))
        } catch (persistError: Exception) {
            cloudFolderLogError(
                event = "binding_error_persist",
                error = persistError,
                details = "root=${cloudFolderSafeId(rootId)}",
            )
        }
        // Binding state is read by the settings screen independently of
        // WorkManager. Always wake it after a failed attempt so it cannot
        // remain on a stale "Scanning" state until the next refresh.
        CloudFolderSyncEvents.notifyStateChanged()
    }

    private suspend fun saveRootProgress(
        rootId: String,
        phase: CloudFolderSyncPhase,
        completedFiles: Int = 0,
        totalFiles: Int = 0,
        completedBytes: Long = 0L,
        totalBytes: Long = 0L,
        errorStatus: String? = null,
        notify: Boolean = true,
    ) {
        repository.saveProgress(
            CloudFolderSyncProgress(
                rootId = rootId,
                phase = phase,
                completedFiles = completedFiles,
                totalFiles = totalFiles,
                completedBytes = completedBytes,
                totalBytes = totalBytes,
                updatedAt = System.currentTimeMillis(),
                errorStatus = errorStatus,
            ).sanitized()
        )
        if (notify) CloudFolderSyncEvents.notifyStateChanged()
    }

    private suspend fun repositoryAccessToken(): String? =
        driveRepository.getAccessToken(applicationContext)

    private suspend fun syncSelectedRoots(
        accessToken: String,
        direction: Direction,
    ) {
        val roots = repository.getRoots()
            .filter { repository.isIncluded(it.rootId) }
        cloudFolderLogI(
            "event=selection_apply direction=${direction.name} " +
                "account=${cloudFolderSafeId(repository.accountId)} selectedRoots=${roots.size}",
        )
        var failure: Throwable? = null
        for (root in roots) {
            try {
                cloudFolderLogD("event=root_start root=${cloudFolderSafeId(root.rootId)} direction=${direction.name}")
                syncRoot(accessToken, root.rootId, direction)
                cloudFolderLogD("event=root_end root=${cloudFolderSafeId(root.rootId)} result=success")
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = failure ?: error
                markRootFailure(root.rootId, error)
                cloudFolderLogError(
                    event = "root_end",
                    error = error,
                    details = "result=${if (cloudFolderFailureIsDeterministic(error)) "failure" else "retry"} " +
                        "root=${cloudFolderSafeId(root.rootId)}",
                )
            }
        }
        failure?.let { throw it }
    }

    /**
     * Retention-only maintenance for immutable Drive objects. The committed
     * manifests are the reachability roots: an object is eligible only when
     * it is absent from every current root's committed manifest and has aged
     * past the retention window. Each delete re-checks identity in the Drive
     * gateway, so a raced replacement cannot be deleted by an old plan.
     */
    private suspend fun runGarbageCollection(accessToken: String) {
        ensureAccountStillActive()
        val objects = driveRepository.listCloudFolderObjectsForGarbageCollection(accessToken)
        val referenced = linkedSetOf<String>()
        val refs = driveRepository.listCloudFolderManifestRefs(accessToken)
        for (ref in refs) {
            ensureAccountStillActive()
            when (val result = readRemoteManifest(accessToken, ref.rootId)) {
                CloudFolderManifestReadResult.NotFound ->
                    throw IOException("Manifest disappeared during cloud-folder GC: ${ref.rootId}")
                is CloudFolderManifestReadResult.Found -> {
                    referenced += result.driveFileId
                    result.manifest.activeFiles()
                        .mapNotNull { it.contentObjectId?.trim()?.takeIf(String::isNotBlank) }
                        .forEach(referenced::add)
                }
            }
        }
        val candidates = planCloudFolderGarbageCollection(
            objects = objects,
            referencedDriveFileIds = referenced,
            nowMillis = System.currentTimeMillis(),
            retentionMillis = CLOUD_FOLDER_GC_RETENTION_MILLIS,
        )
        for (candidate in candidates) {
            ensureAccountStillActive()
            driveRepository.deleteCloudFolderObject(accessToken, candidate.objectRef)
        }
    }

    /**
     * Remove a synced folder from Drive and from this device. A tombstone
     * revision (root deleted, no nodes) is published first so every other
     * device observes the deletion idempotently; the orphaned content objects
     * become unreferenced and are reclaimed by the normal garbage-collection
     * pass. Local state is then wiped regardless of the remote outcome so the
     * folder is always gone from this device.
     */
    private suspend fun deleteRootFromCloud(accessToken: String, rootId: String) {
        val safeRoot = cloudFolderSafeId(rootId)
        cloudFolderLogI(
            "event=delete_root_start ${traceFields(rootId)} root=$safeRoot " +
                "direction=DELETE",
        )
        val binding = repository.getBinding(rootId)
        val knownRootIds = repository.getRoots().map { it.rootId }
        // A delete must complete even when the remote state is already
        // broken (e.g. the immutable manifest is gone but its Firestore
        // pointer survived, making readRemoteManifest throw). Fall back to
        // deleting the pointer so peers observe a clean "no such folder".
        val remoteResult = runCatching { readRemoteManifest(accessToken, rootId) }.getOrNull()
        if (remoteResult is CloudFolderManifestReadResult.Found) {
            val remote = remoteResult.manifest.normalized()
            val tombstone = remote.copy(
                root = remote.root.copy(isDeleted = true),
                revision = remote.revision + 1L,
                nodes = emptyList(),
                tombstones = emptyList(),
            ).withUpdatedRootStats(System.currentTimeMillis())
            publishManifestWithCas(
                accessToken = accessToken,
                rootId = rootId,
                initialRemote = remoteResult,
                manifest = tombstone,
                persistLocalManifest = false,
            )
            cloudFolderLogD(
                "event=delete_root_tombstone ${traceFields(rootId)} root=$safeRoot " +
                    "revision=${tombstone.revision} result=published",
            )
        } else {
            runCatching {
                firestoreRepository.deleteCloudFolderManifestHead(repository.accountId, rootId)
            }.onSuccess { deleted ->
                cloudFolderLogD(
                    "event=delete_root_head ${traceFields(rootId)} root=$safeRoot " +
                        "result=${if (deleted) "deleted" else "not_found"}",
                )
            }.onFailure { error ->
                cloudFolderLogError(
                    event = "delete_root_head",
                    error = error,
                    details = "${traceFields(rootId)} root=$safeRoot result=failure",
                )
            }
        }
        ensureAccountStillActive()
        if (binding?.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE) {
            removeCloudFolderAppStorageTree(applicationContext.filesDir, rootId)
            CloudFolderAppStoragePrefs.remove(applicationContext, repository.accountId, rootId)
        }
        repository.clearRootState(rootId)
        val selection = CloudFolderSyncPrefs.load(applicationContext, repository.accountId)
            .withoutRoot(rootId, knownRootIds)
        CloudFolderSyncPrefs.save(applicationContext, repository.accountId, selection)
        CloudFolderSyncPrefs.forgetIncomingPrompt(applicationContext, repository.accountId, rootId)
        CloudFolderSyncEvents.notifyStateChanged()
        cloudFolderLogI(
            "event=delete_root_end ${traceFields(rootId)} root=$safeRoot result=success",
        )
    }

    /**
     * Discover incoming roots without materializing them.  Existing bindings
     * are intentionally ignored here; the caller decides separately whether
     * selected bindings should be pulled.
     *
     * Returning the first failure lets a full PULL still process bound roots
     * when an unrelated manifest is temporarily unavailable.
     */
    private suspend fun discoverIncomingRoots(accessToken: String): Throwable? {
        val startedAt = System.currentTimeMillis()
        cloudFolderLogD("event=discovery_start account=${cloudFolderSafeId(repository.accountId)}")
        var failure: Throwable? = null
        val refs = try {
            driveRepository.listCloudFolderManifestRefs(accessToken)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A failed account-wide listing must not starve roots that are
            // already bound. discoverAndPull() will still run their targeted
            // pulls and return this error after that work is attempted.
            failure = error
            cloudFolderLogError(
                event = "discovery_refs",
                error = error,
                details = "result=${if (cloudFolderFailureIsDeterministic(error)) "failure" else "retry"}",
            )
            emptyList()
        }
        cloudFolderLogD("event=discovery_refs count=${refs.size}")
        for (ref in refs) {
            try {
                // Discovery is metadata-only. In particular, do not call the
                // normal pull path here: an already-bound KEEP_OFFLINE or
                // LOCAL_MIRROR root must not receive bytes during a PUSH pass.
                if (repository.getBinding(ref.rootId) != null) continue
                when (val result = readRemoteManifest(accessToken, ref.rootId)) {
                    CloudFolderManifestReadResult.NotFound -> Unit
                    is CloudFolderManifestReadResult.Found -> {
                        cloudFolderLogD(
                            "event=discovery_manifest root=${cloudFolderSafeId(ref.rootId)} " +
                                "revision=${result.manifest.revision}",
                        )
                        val manifest = result.manifest.normalized()
                        repository.saveManifest(manifest)
                        CloudFolderSyncPrefs.markIncomingPromptPending(
                            context = applicationContext,
                            accountId = repository.accountId,
                            rootId = manifest.rootId,
                            revision = manifest.revision,
                        )
                        CloudFolderSyncEvents.notifyStateChanged()
                    }
                }
                cloudFolderLogD("event=discovery_root_end root=${cloudFolderSafeId(ref.rootId)} result=success")
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = failure ?: error
                cloudFolderLogError(
                    event = "discovery_root_end",
                    error = error,
                    details = "result=${if (cloudFolderFailureIsDeterministic(error)) "failure" else "retry"} " +
                        "root=${cloudFolderSafeId(ref.rootId)}",
                )
            }
        }
        cloudFolderLogD(
            "event=discovery_end roots=${refs.size} result=${if (failure == null) "success" else "error"} " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return failure
    }

    /**
     * Pull every root that is both bound on this device and explicitly
     * included in its account-scoped selection.  The binding/mode checks in
     * [pullRoot] remain the final authority, so a selection or sign-in change
     * racing this pass cannot materialize an excluded root.
     */
    private suspend fun pullIncludedRoots(accessToken: String): Throwable? {
        val roots = repository.getRoots()
            .filter { root ->
                shouldPullBoundCloudFolderRoot(
                    isDeleted = root.isDeleted,
                    isIncluded = repository.isIncluded(root.rootId),
                    hasBinding = repository.getBinding(root.rootId) != null,
                )
            }
        cloudFolderLogI(
            "event=bound_pull_start account=${cloudFolderSafeId(repository.accountId)} roots=${roots.size}",
        )
        var failure: Throwable? = null
        for (root in roots) {
            try {
                ensureAccountStillActive()
                cloudFolderLogD(
                    "event=bound_pull_root_start ${traceFields(root.rootId)} " +
                        "root=${cloudFolderSafeId(root.rootId)}",
                )
                pullRoot(accessToken, root.rootId)
                cloudFolderLogD(
                    "event=bound_pull_root_end ${traceFields(root.rootId)} " +
                        "root=${cloudFolderSafeId(root.rootId)} result=success",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = failure ?: error
                markRootFailure(root.rootId, error)
                cloudFolderLogError(
                    event = "bound_pull_root_end",
                    error = error,
                    details = "${traceFields(root.rootId)} " +
                        "root=${cloudFolderSafeId(root.rootId)} " +
                        "result=${if (cloudFolderFailureIsDeterministic(error)) "failure" else "retry"}",
                )
            }
        }
        cloudFolderLogI(
            "event=bound_pull_end account=${cloudFolderSafeId(repository.accountId)} " +
                "roots=${roots.size} result=${if (failure == null) "success" else "error"}",
        )
        return failure
    }

    /**
     * Full PULL combines non-materializing discovery with reconciliation of
     * already-bound roots.  Both passes run even if one root fails so a
     * transient problem cannot starve unrelated roots or incoming prompts.
     */
    private suspend fun discoverAndPull(accessToken: String) {
        val discoveryFailure = discoverIncomingRoots(accessToken)
        val boundPullFailure = pullIncludedRoots(accessToken)
        (discoveryFailure ?: boundPullFailure)?.let { throw it }
    }

    /** Drive objects are authenticated by the gateway; Pro also authenticates
     * the selected immutable object against the Firestore commit pointer. */
    private suspend fun readRemoteManifest(
        accessToken: String,
        rootId: String,
    ): CloudFolderManifestReadResult {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        cloudFolderLogD("event=manifest_read_start root=$safeRoot")
        ensureAccountStillActive()
        // Read the committed head before touching Drive so the download can
        // pin the exact committed object. Without this, an orphan manifest
        // uploaded by an interrupted worker (a higher revision never committed
        // to Firestore) would win the candidate sort on every read and no
        // device could ever observe the authoritative state again.
        var head = if (BuildConfig.IS_PRO) {
            firestoreRepository.getCloudFolderManifestHead(repository.accountId, rootId)
        } else {
            null
        }
        val result = driveRepository.downloadCloudFolderManifest(
            accessToken = accessToken,
            rootId = rootId,
            operationId = activeOperationId,
            correlationId = activeCorrelationId,
            preferredDriveFileId = head?.manifestDriveFileId,
        )
        if (!BuildConfig.IS_PRO) {
            cloudFolderLogD(
                "event=manifest_read_end root=$safeRoot source=drive " +
                    "result=${if (result is CloudFolderManifestReadResult.Found) "found" else "not_found"} " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
            return result
        }
        val bootstrapCandidate = if (head == null && result is CloudFolderManifestReadResult.Found) {
            legacyCloudFolderManifestHeadCandidate(
                remote = result,
                existingHead = null,
                manifestHash = sha256CloudFolderManifest(result.manifest),
            )
        } else {
            null
        }
        if (bootstrapCandidate != null) {
            // Older cloud-folder manifests were written to Drive before the
            // Firestore commit pointer existed. Bootstrap that pointer with
            // a create-if-absent transaction so the first CAS publish can use
            // the legacy revision without accepting an uncommitted object.
            head = firestoreRepository.bootstrapCloudFolderManifestHead(
                userId = repository.accountId,
                rootId = rootId,
                manifestDriveFileId = bootstrapCandidate.manifestDriveFileId,
                revision = bootstrapCandidate.revision,
                manifestHash = bootstrapCandidate.manifestHash,
            ) ?: firestoreRepository.getCloudFolderManifestHead(repository.accountId, rootId)
        }
        if (head == null) {
            // A missing Firestore head is valid only when Drive also has no
            // manifest. If a legacy manifest was present but the
            // create-if-absent bootstrap raced with a deletion or otherwise
            // failed to produce a head, do not accept an unauthenticated
            // Drive object as authoritative.
            if (result is CloudFolderManifestReadResult.Found) {
                throw IOException("Unable to bootstrap cloud-folder manifest head: $rootId")
            }
            cloudFolderLogD(
                "event=manifest_read_end root=$safeRoot source=drive_firestore result=not_found " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
            return result
        }
        if (result !is CloudFolderManifestReadResult.Found ||
            result.driveFileId != head.manifestDriveFileId ||
            result.manifest.revision != head.revision ||
            sha256CloudFolderManifest(result.manifest) != head.manifestHash
        ) {
            throw IOException("Cloud-folder Drive manifest is not the committed Firestore head: $rootId")
        }
        cloudFolderLogD(
            "event=manifest_read_end root=$safeRoot source=drive_firestore result=found " +
                "revision=${result.manifest.revision} durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return result
    }

    /**
     * Finish a target that was published/materialized only partially before a
     * worker interruption. The committed repository manifest is intentionally
     * updated last, so a successful return is the only point at which this
     * method advances the local base.
     */
    private suspend fun resumePendingMaterialization(
        accessToken: String,
        rootId: String,
        binding: com.aryan.reader.shared.CloudFolderDeviceBinding,
        remoteResult: CloudFolderManifestReadResult,
    ): Boolean {
        val pending = repository.getPendingMaterialization(rootId) ?: return false
        cloudFolderLogD(
            "event=resume_materialization_start ${traceFields(rootId)} " +
                "root=${cloudFolderSafeId(rootId)} mode=${binding.materializationMode.name} " +
                "pendingRevision=${pending.revision} " +
                "missingObjectIds=${pending.filesMissingContentObjectIds().size}",
        )
        when (binding.materializationMode) {
            CloudFolderMaterializationMode.CLOUD_ONLY -> {
                // The user explicitly removed local materialization while a
                // previous transfer was pending. No local bytes are required
                // in this mode, so discard only the pending local target.
                repository.clearPendingMaterialization(rootId)
                cloudFolderLogD(
                    "event=resume_materialization_end ${traceFields(rootId)} " +
                        "root=${cloudFolderSafeId(rootId)} result=cleared mode=CLOUD_ONLY",
                )
                return false
            }
            else -> {
                val remote = (remoteResult as? CloudFolderManifestReadResult.Found)
                    ?.manifest
                    ?.normalized()
                val canResume = remote != null &&
                    remote.revision >= pending.revision &&
                    (remote.revision != pending.revision ||
                        sha256CloudFolderManifest(remote) == sha256CloudFolderManifest(pending))
                if (!canResume) {
                    cloudFolderLogD(
                        "event=resume_materialization_end ${traceFields(rootId)} " +
                            "root=${cloudFolderSafeId(rootId)} result=skipped " +
                            "remoteRevision=${remote?.revision ?: "none"} " +
                            "pendingRevision=${pending.revision}",
                    )
                    return false
                }
            }
        }
        when (binding.materializationMode) {
            CloudFolderMaterializationMode.CLOUD_ONLY -> return false
            CloudFolderMaterializationMode.KEEP_OFFLINE ->
                materializeManifestToAppStorage(accessToken, pending)
            CloudFolderMaterializationMode.LOCAL_MIRROR -> {
                val localUri = binding.localUri?.takeIf { it.isNotBlank() } ?: return false
                val contentFilesChanged = materializeManifest(
                    accessToken = accessToken,
                    manifest = pending,
                    localRootUri = Uri.parse(localUri),
                    expectedBase = repository.getManifest(rootId),
                    allowAlreadyMaterialized = true,
                )
                // SAF materialization writes bytes outside the app-private
                // index. Reconcile the completed tree immediately so remote
                // sidecar changes import as metadata-only while downloaded
                // content receives the normal full indexing path.
                FolderSyncWorker.enqueueCloudFolderIndex(
                    context = applicationContext,
                    accountId = repository.accountId,
                    rootId = rootId,
                    metadataOnly = !contentFilesChanged,
                    localUri = localUri,
                )
            }
        }
        ensureAccountStillActive()
        repository.saveManifest(pending)
        repository.clearPendingMaterialization(rootId)
        repository.saveBinding(
            binding.copy(
                permissionState = CloudFolderPermissionState.GRANTED,
                lastAcknowledgedRevision = pending.revision,
                lastError = null,
            )
        )
        CloudFolderSyncEvents.notifyStateChanged()
        cloudFolderLogI(
            "event=resume_materialization_end ${traceFields(rootId)} " +
                "root=${cloudFolderSafeId(rootId)} result=success " +
                "mode=${binding.materializationMode.name} revision=${pending.revision}",
        )
        return true
    }

    private suspend fun pullRoot(accessToken: String, rootId: String) {
        val existingBinding = repository.getBinding(rootId)
        if (existingBinding != null) {
            if (!repository.isIncluded(rootId)) return
        }
        val remoteResult = readRemoteManifest(accessToken, rootId)
        if (remoteResult is CloudFolderManifestReadResult.Found) {
            val remoteManifest = remoteResult.manifest.normalized()
            logPullManifestPlan(
                rootId = rootId,
                remote = remoteManifest,
                local = repository.getManifest(rootId),
                binding = existingBinding,
                hasPendingMaterialization = repository.getPendingMaterialization(rootId) != null,
            )
            logManifestObjectIntegrity(rootId, remoteManifest, source = "pull")
        }
        if (existingBinding != null &&
            resumePendingMaterialization(accessToken, rootId, existingBinding, remoteResult)
        ) return
        when (remoteResult) {
            CloudFolderManifestReadResult.NotFound -> return
            is CloudFolderManifestReadResult.Found -> {
                val manifest = remoteResult.manifest.normalized()
                val binding = existingBinding
                if (binding == null) {
                    // Discovery remains metadata-only until the user makes an
                    // explicit incoming-folder choice.
                    repository.saveManifest(manifest)
                    CloudFolderSyncPrefs.markIncomingPromptPending(
                        context = applicationContext,
                        accountId = repository.accountId,
                        rootId = manifest.rootId,
                        revision = manifest.revision,
                    )
                    CloudFolderSyncEvents.notifyStateChanged()
                    return
                }
                // Incoming roots are metadata-only until a persisted choice
                // opts them in. This is the guard that keeps the default
                // EXCLUDED policy from downloading data merely because a
                // manifest was discovered on Drive.
                if (!repository.isIncluded(rootId)) {
                    repository.saveManifest(manifest)
                    return
                }
                when (binding.materializationMode) {
                    CloudFolderMaterializationMode.CLOUD_ONLY -> {
                        repository.saveManifest(manifest)
                        return
                    }
                    CloudFolderMaterializationMode.KEEP_OFFLINE -> {
                        // The acknowledged revision is advanced only after
                        // every file has been atomically written to the
                        // app-private offline tree.
                        if (binding.lastAcknowledgedRevision == manifest.revision &&
                            repository.getManifest(rootId)?.revision == manifest.revision
                        ) {
                            return
                        }
                        try {
                            verifyAppStorageIsPullSafe(
                                rootId = rootId,
                                remote = manifest,
                                localRoot = cloudFolderAppRootDirectory(applicationContext.filesDir, rootId),
                            )
                        } catch (error: CloudFolderPullUnsafeException) {
                            handoffToLocalChangeSync(rootId, error)
                            return
                        }
                        repository.savePendingMaterialization(manifest)
                        materializeManifestToAppStorage(accessToken, manifest)
                    }
                    CloudFolderMaterializationMode.LOCAL_MIRROR -> {
                        val localUri = binding.localUri?.takeIf { it.isNotBlank() } ?: return
                        try {
                            verifyLocalMirrorIsPullSafe(rootId, manifest, Uri.parse(localUri))
                        } catch (error: CloudFolderPullUnsafeException) {
                            handoffToLocalChangeSync(rootId, error)
                            return
                        }
                        repository.savePendingMaterialization(manifest)
                        val contentFilesChanged = materializeManifest(
                            accessToken = accessToken,
                            manifest = manifest,
                            localRootUri = Uri.parse(localUri),
                            expectedBase = repository.getManifest(rootId),
                            allowAlreadyMaterialized = true,
                        )
                        FolderSyncWorker.enqueueCloudFolderIndex(
                            context = applicationContext,
                            accountId = repository.accountId,
                            rootId = rootId,
                            metadataOnly = !contentFilesChanged,
                            localUri = localUri,
                        )
                    }
                }
                ensureAccountStillActive()
                repository.saveManifest(manifest)
                repository.clearPendingMaterialization(rootId)
                repository.saveBinding(
                    binding.copy(
                        permissionState = CloudFolderPermissionState.GRANTED,
                        lastAcknowledgedRevision = manifest.revision,
                        lastError = null,
                    )
                )
                CloudFolderSyncEvents.notifyStateChanged()
            }
        }
    }

    /**
     * A PULL that observes uncommitted local changes cannot proceed without
     * overwriting them, and retrying the PULL can never make progress. Hand
     * the root to the three-way SYNC planner instead: it merges both sides,
     * auto-resolves decidable conflicts (sidecars keep the local device's
     * view by default), publishes the local change, and materializes the
     * remote one. The SYNC unique-work identity is separate from PULL's, so
     * the hand-off can neither cancel nor recurse into this request.
     */
    private suspend fun handoffToLocalChangeSync(
        rootId: String,
        error: CloudFolderPullUnsafeException,
    ) {
        val message = cloudFolderSafeErrorReason(error)
        val stillSelected = repository.isIncluded(rootId)
        val stillSignedIn = runCatching {
            ensureAccountStillActive()
            true
        }.getOrDefault(false)
        val canQueueSync = stillSelected && stillSignedIn &&
            isCloudFolderSyncEnabled(applicationContext)
        if (canQueueSync) {
            enqueue(
                context = applicationContext,
                accountId = repository.accountId,
                rootId = rootId,
                direction = CloudFolderSyncDirection.NONE,
                replace = false,
            )
            cloudFolderLogI(
                "event=pull_local_change_handoff ${traceFields(rootId)} " +
                    "root=${cloudFolderSafeId(rootId)} result=queued reason=$message",
            )
            repository.markBindingError(rootId, message)
        } else {
            cloudFolderLogW(
                "event=pull_local_change_handoff ${traceFields(rootId)} " +
                    "root=${cloudFolderSafeId(rootId)} result=not_queued " +
                    "selected=$stillSelected signedIn=$stillSignedIn " +
                    "syncEnabled=${isCloudFolderSyncEnabled(applicationContext)} reason=$message",
            )
        }
        CloudFolderSyncEvents.notifyStateChanged()
    }

    /**
     * Emit one privacy-safe summary before a pull is compared or resumed.
     * This deliberately compares manifest state rather than the local file
     * tree: the latter may be a SAF provider or app-private path and is not
     * needed to diagnose whether a remote revision is sidecar-only.
     */
    private fun logPullManifestPlan(
        rootId: String,
        remote: CloudFolderManifest,
        local: CloudFolderManifest?,
        binding: com.aryan.reader.shared.CloudFolderDeviceBinding?,
        hasPendingMaterialization: Boolean,
    ) {
        val delta = classifyCloudFolderPullDelta(local, remote)
        val selectedPath = selectedCloudFolderPullPath(
            binding = binding,
            classification = delta.classification,
            hasPendingMaterialization = hasPendingMaterialization,
        )
        cloudFolderLogI(
            "event=pull_manifest_plan ${traceFields(rootId)} " +
                "root=${cloudFolderSafeId(rootId)} " +
                "localManifestPresent=${local != null} " +
                "localRevision=${local?.revision ?: 0L} " +
                "remoteRevision=${remote.revision} " +
                "metadataAdded=${delta.metadataAdded} " +
                "metadataChanged=${delta.metadataChanged} " +
                "metadataDeleted=${delta.metadataDeleted} " +
                "contentAdded=${delta.contentAdded} " +
                "contentChanged=${delta.contentChanged} " +
                "contentDeleted=${delta.contentDeleted} " +
                "classification=${delta.classification} " +
                "materializationMode=${binding?.materializationMode?.name ?: "DISCOVERY"} " +
                "pendingMaterialization=$hasPendingMaterialization " +
                "selectedPath=$selectedPath",
        )
    }

    /**
     * Surface a poisoned manifest before anything depends on it. A published
     * manifest should never reference active files without an immutable
     * object pointer; when one is seen, log the affected nodes immediately
     * so the publishing revision can be identified from any device's logs.
     */
    private fun logManifestObjectIntegrity(
        rootId: String,
        manifest: CloudFolderManifest,
        source: String,
    ) {
        val missing = manifest.filesMissingContentObjectIds()
        if (missing.isEmpty()) return
        cloudFolderLogW(
            "event=manifest_object_integrity ${traceFields(rootId)} " +
                "root=${cloudFolderSafeId(rootId)} source=$source revision=${manifest.revision} " +
                "files=${manifest.activeFiles().size} missingObjectIds=${missing.size} " +
                "nodes=[${missing.take(5).joinToString(",") { cloudFolderSafeId(it.nodeId) }}]",
        )
    }

    /**
     * A direct PULL must not overwrite edits made after the last committed
     * local snapshot. The normal SYNC planner performs this check as part of
     * its merge; this guard gives explicit PULL the same protection. Instead
     * of failing the request, the caller converts this into a hand-off to
     * the three-way SYNC planner: only SYNC can reconcile and publish the
     * local change, so a plain retry would loop forever.
     */
    private suspend fun verifyLocalMirrorIsPullSafe(
        rootId: String,
        remote: CloudFolderManifest,
        localRootUri: Uri,
    ) {
        val scan = CloudFolderSafScanner.scan(
            context = applicationContext,
            rootUri = localRootUri,
            rootId = rootId,
            deviceId = repository.deviceId,
        )
        if (!scan.complete) {
            throw IOException(scan.errorMessage ?: "SAF scan was incomplete")
        }
        val base = repository.getManifest(rootId)
        if (base == null) {
            if (scan.nodes.isNotEmpty()) {
                throw IOException("Cannot pull into a non-empty uninitialized local folder")
            }
            return
        }
        val local = buildLocalManifest(base, scan, scan.scannedAt, repository.deviceId)
        val plan = planCloudFolderSync(
            base = base,
            local = local,
            remote = remote,
            nowMillis = scan.scannedAt,
            deviceId = repository.deviceId,
        )
        if (plan.conflicts.isNotEmpty()) {
            throw CloudFolderPullUnsafeException(
                "Local and remote folder changes conflict; sync queued",
            )
        }
        if (plan.operations.any { it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD }) {
            throw CloudFolderPullUnsafeException(
                "Local folder changed; sync before pulling remote changes",
            )
        }
    }

    /** The same no-overwrite guard as SAF mirrors for app-private folders. */
    private suspend fun verifyAppStorageIsPullSafe(
        rootId: String,
        remote: CloudFolderManifest,
        localRoot: File,
    ) {
        if (!localRoot.exists()) return
        if (!localRoot.isDirectory) throw IOException("Offline folder is not a directory")
        val base = repository.getManifest(rootId)
        val scan = scanAppStorageForSync(
            root = localRoot,
            rootId = rootId,
            deviceId = repository.deviceId,
            base = base,
            metadataOnly = false,
            requiredKindsByBook = emptyMap(),
        )
        if (!scan.complete) throw IOException(scan.errorMessage ?: "Offline folder scan was incomplete")
        if (base == null) {
            if (scan.entries.isNotEmpty()) {
                throw IOException("Cannot pull into a non-empty uninitialized offline folder")
            }
            return
        }
        val local = buildLocalManifest(base, scan, scan.scannedAt, repository.deviceId)
        val plan = planCloudFolderSync(
            base = base,
            local = local,
            remote = remote,
            nowMillis = scan.scannedAt,
            deviceId = repository.deviceId,
        )
        if (plan.conflicts.isNotEmpty()) {
            throw CloudFolderPullUnsafeException(
                "Local and remote folder changes conflict; sync queued",
            )
        }
        if (plan.operations.any { it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD }) {
            throw CloudFolderPullUnsafeException(
                "Local folder changed; sync before pulling remote changes",
            )
        }
    }

    private suspend fun syncRoot(
        accessToken: String,
        rootId: String,
        direction: Direction,
        metadataOnly: Boolean = false,
    ) {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        activeStage = "root_selection"
        if (!repository.isIncluded(rootId)) {
            cloudFolderLogD("event=root_gate root=$safeRoot gate=selection result=excluded")
            return
        }
        val binding = repository.getBinding(rootId) ?: run {
            cloudFolderLogW("event=root_gate root=$safeRoot gate=binding result=missing")
            return
        }
        cloudFolderLogD(
            "event=sync_root_start ${traceFields(rootId)} direction=${direction.name} " +
                "mode=${binding.materializationMode.name} metadataOnly=$metadataOnly",
        )
        if (metadataOnly) {
            cloudFolderLogI(
                "event=metadata_worker_start ${traceFields(rootId)} direction=${direction.name} " +
                    "mode=${binding.materializationMode.name}",
            )
        }
        activeStage = "progress"
        saveRootProgress(rootId, CloudFolderSyncPhase.SCANNING)
        val appStorageRoot = if (binding.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE) {
            runCatching {
                activeStage = "app_storage_root"
                cloudFolderAppRootDirectory(applicationContext.filesDir, rootId)
            }
                .getOrElse { error ->
                    if (metadataOnly) {
                        logMetadataStageFailure(
                            rootId = rootId,
                            stage = "app_storage_root",
                            category = "metadata_app_storage_root",
                            error = error,
                        )
                    }
                    throw IOException("Offline folder is unavailable: ${error.message}")
                }
        } else {
            null
        }
        if (appStorageRoot != null && direction == Direction.PULL) {
            // KEEP_OFFLINE pulls are handled by the materializer. PUSH/SYNC
            // below use the same three-way planner and CAS as SAF mirrors.
            activeStage = "pull_app_storage"
            pullRoot(accessToken, rootId)
            saveRootProgress(
                rootId = rootId,
                phase = CloudFolderSyncPhase.SUCCEEDED,
                errorStatus = null,
            )
            cloudFolderLogD("event=sync_root_end root=$safeRoot result=offline_pull")
            return
        }
        activeStage = "manifest_read"
        val remoteResult = try {
            readRemoteManifest(accessToken, rootId)
        } catch (error: Exception) {
            if (metadataOnly) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "manifest_read",
                    category = "metadata_manifest_read",
                    error = error,
                )
            }
            throw error
        }
        if (remoteResult is CloudFolderManifestReadResult.Found) {
            logManifestObjectIntegrity(rootId, remoteResult.manifest, source = "sync")
        }
        activeStage = "resume_materialization"
        val resumedPendingMaterialization = try {
            resumePendingMaterialization(accessToken, rootId, binding, remoteResult)
        } catch (error: Exception) {
            if (metadataOnly) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "resume_materialization",
                    category = "metadata_materialization_recovery",
                    error = error,
                )
                // A failed content materialization must not block the
                // sidecar pipeline. Reading positions and annotations ride
                // on the committed manifest base, so defer the interrupted
                // content target to the next full sync instead of failing
                // this metadata-only pass.
                cloudFolderLogW(
                    "event=metadata_resume_deferred ${traceFields(rootId)} " +
                        "root=$safeRoot result=deferred reason=${cloudFolderSafeErrorReason(error)}",
                )
                false
            } else {
                throw error
            }
        }
        if (resumedPendingMaterialization && !metadataOnly) {
            val progress = repository.getProgress(rootId)
            saveRootProgress(
                rootId = rootId,
                phase = CloudFolderSyncPhase.SUCCEEDED,
                completedFiles = progress?.totalFiles ?: 0,
                totalFiles = progress?.totalFiles ?: 0,
                completedBytes = progress?.totalBytes ?: 0L,
                totalBytes = progress?.totalBytes ?: 0L,
            )
            return
        }
        if (binding.materializationMode == CloudFolderMaterializationMode.CLOUD_ONLY) {
            activeStage = "cloud_only"
            val stats = repository.getRoot(rootId)?.stats
            saveRootProgress(
                rootId = rootId,
                phase = CloudFolderSyncPhase.SUCCEEDED,
                completedFiles = stats?.fileCount ?: 0,
                totalFiles = stats?.fileCount ?: 0,
                completedBytes = stats?.totalBytes ?: 0L,
                totalBytes = stats?.totalBytes ?: 0L,
            )
            return
        }
        val localUri = binding.localUri?.takeIf { it.isNotBlank() }
        val rootUri = try {
            localUri?.let(Uri::parse)
        } catch (error: Exception) {
            if (metadataOnly) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "local_uri_parse",
                    category = "metadata_local_uri_resolution",
                    error = error,
                )
            }
            throw error
        }
        activeStage = "metadata_outbox_claim"
        val pendingMetadata = try {
            repository.claimDueMetadataOutbox(rootId, limit = 500)
        } catch (error: Exception) {
            if (metadataOnly) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "metadata_outbox_claim",
                    category = "metadata_outbox_claim",
                    error = error,
                )
            }
            throw error
        }
        val pendingBooks = pendingMetadata.map { it.bookId }.toSet()
        // Per-book dirty kinds: a METADATA-only wake must not demand the
        // annotation sidecar (a PDF-only artifact), or every EPUB position
        // update degrades into a full-tree re-hash.
        val requiredKindsByBook = requiredMetadataKindsByBook(pendingMetadata)
        if (metadataOnly) {
            cloudFolderLogD(
                "event=metadata_batch_claimed ${traceFields(rootId)} rows=${pendingMetadata.size} " +
                    "books=${pendingBooks.size} kinds=${pendingMetadata.flatMap { it.dirtyKinds.split(',') }.distinct().sorted().joinToString(",")}",
            )
        }
        val committedBase = repository.getManifest(rootId)
        activeStage = "metadata_target_resolution"
        val targetedMetadataSync = if (metadataOnly && committedBase != null) {
            try {
                pendingBooks.isEmpty() || when {
                    appStorageRoot != null -> appStorageMetadataTargetsAvailable(
                        root = appStorageRoot,
                        requiredKindsByBook = requiredKindsByBook,
                    )
                    rootUri != null -> safMetadataTargetsAvailable(
                        rootUri = rootUri,
                        rootId = rootId,
                        requiredKindsByBook = requiredKindsByBook,
                    )
                    else -> false
                }
            } catch (error: Exception) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "metadata_target_resolution",
                    category = "metadata_sidecar_resolution",
                    error = error,
                    details = "mode=${if (appStorageRoot != null) "app_storage" else "saf"} books=${pendingBooks.size}",
                )
                false
            }
        } else {
            false
        }
        if (metadataOnly && pendingBooks.isNotEmpty() && !targetedMetadataSync) {
            // A sidecar may have been removed externally since the durable
            // wake-up was recorded. Fall back to a complete inventory so the
            // deletion becomes a real manifest tombstone instead of being
            // hidden by the targeted scan's intentional preservation rule.
            cloudFolderLogW(
                "event=metadata_scan_fallback root=$safeRoot reason=sidecar_missing " +
                    "books=${pendingBooks.size}",
            )
        }
        activeStage = "metadata_local_scan"
        val scan = if (appStorageRoot != null && targetedMetadataSync) {
            cloudFolderLogD(
                "event=metadata_local_scan_start ${traceFields(rootId)} mode=app_storage " +
                    "targeted=true books=${pendingBooks.size}",
            )
            scanAppStorageForSync(
                root = appStorageRoot,
                rootId = rootId,
                deviceId = repository.deviceId,
                base = committedBase,
                metadataOnly = metadataOnly,
                requiredKindsByBook = requiredKindsByBook,
            )
        } else if (appStorageRoot == null && targetedMetadataSync) {
            cloudFolderLogD(
                "event=metadata_local_scan_start ${traceFields(rootId)} mode=saf " +
                    "targeted=true books=${pendingBooks.size}",
            )
            scanSafStorageForMetadata(
                rootUri = requireNotNull(rootUri),
                rootId = rootId,
                deviceId = repository.deviceId,
                base = committedBase,
                requiredKindsByBook = requiredKindsByBook,
            )
        } else {
            if (appStorageRoot != null) {
                scanAppStorageForSync(
                    root = appStorageRoot,
                    rootId = rootId,
                    deviceId = repository.deviceId,
                    base = committedBase,
                    metadataOnly = false,
                    requiredKindsByBook = emptyMap(),
                )
            } else {
                val uri = rootUri ?: throw IOException("Local folder binding is missing")
                CloudFolderSafScanner.scan(
                    context = applicationContext,
                    rootUri = uri,
                    rootId = rootId,
                    deviceId = repository.deviceId,
                )
            }
        }
        if (metadataOnly) {
            cloudFolderLogD(
                "event=metadata_local_scan_end ${traceFields(rootId)} targeted=$targetedMetadataSync " +
                    "complete=${scan.complete} files=${scan.files.size} directories=${scan.directories.size} " +
                    "errorStatus=${cloudFolderErrorStatus(scan.errorMessage)}",
            )
        }
        if (!scan.complete) {
            val message = scan.errorMessage ?: "SAF scan was incomplete"
            repository.markBindingError(rootId, message)
            CloudFolderSyncEvents.notifyStateChanged()
            cloudFolderLogW(
                "event=sync_root_scan root=$safeRoot result=incomplete " +
                    "errorStatus=${cloudFolderErrorStatus(message)}",
            )
            throw IOException(message)
        }
        cloudFolderLogD(
            "event=sync_root_scan root=$safeRoot result=complete files=${scan.files.size} " +
                "directories=${scan.directories.size}",
        )
        activeStage = "manifest_plan"
        val totalFiles = scan.files.size
        val totalBytes = scan.files.sumOf { it.node.sizeBytes.coerceAtLeast(0L) }
        saveRootProgress(
            rootId = rootId,
            phase = CloudFolderSyncPhase.UPLOADING,
            totalFiles = totalFiles,
            totalBytes = totalBytes,
        )
        val now = scan.scannedAt
        val base = repository.getManifest(rootId) ?: CloudFolderManifest(
            root = repository.getRoot(rootId) ?: CloudFolderRoot(
                rootId = rootId,
                name = "Local folder",
                createdAt = now,
                createdByDeviceId = repository.deviceId,
                updatedAt = now,
            ),
            generatedAt = now,
            generatedByDeviceId = repository.deviceId,
        )
        val remoteMissing = remoteResult is CloudFolderManifestReadResult.NotFound
        val remote = when (val result = remoteResult) {
            CloudFolderManifestReadResult.NotFound -> {
                base
            }
            is CloudFolderManifestReadResult.Found -> result.manifest
        }
        if (direction == Direction.PULL) {
            activeStage = "pull_materialization"
            saveRootProgress(
                rootId = rootId,
                phase = CloudFolderSyncPhase.FINALIZING,
                totalFiles = totalFiles,
                totalBytes = totalBytes,
            )
            pullRoot(accessToken, rootId)
            saveRootProgress(
                rootId = rootId,
                phase = CloudFolderSyncPhase.SUCCEEDED,
                completedFiles = totalFiles,
                totalFiles = totalFiles,
                completedBytes = totalBytes,
                totalBytes = totalBytes,
            )
            return
        }

        val local = buildLocalManifest(base, scan, now, repository.deviceId)
        var plan = planCloudFolderSync(
            base = base,
            local = local,
            remote = remote,
            nowMillis = now,
            deviceId = repository.deviceId,
        )
        cloudFolderLogD(
            "event=sync_plan ${traceFields(rootId)} operations=${plan.operations.size} " +
                "conflicts=${plan.conflicts.size} revision=${plan.mergedManifest.revision} " +
                "baseRevision=${plan.baseRevision} localRevision=${plan.localRevision} " +
                "remoteRevision=${plan.remoteRevision} localToCloud=${plan.operations.count { it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD }} " +
                "cloudToLocal=${plan.operations.count { it.direction == CloudFolderSyncDirection.CLOUD_TO_LOCAL }}",
        )
        if (plan.conflicts.isNotEmpty()) {
            plan.conflicts.forEach { conflict ->
                cloudFolderLogW(
                    "event=conflict_classified ${traceFields(rootId)} " +
                        "conflict=${cloudFolderSafeId(conflict.conflictId)} " +
                        "path=${cloudFolderSafeId(conflict.relativePath)} type=${conflict.type.name} " +
                        "related=${conflict.relatedNodeIds.size} baseRevision=${plan.baseRevision} " +
                        "localRevision=${plan.localRevision} remoteRevision=${plan.remoteRevision}",
                )
            }
            val records = repository.reconcileConflicts(plan, now)
            // Never stall sync on a manual decision. Fill any DEFER entry
            // with the type's deterministic default so the plan resolves
            // without asking the user; only fundamentally undecidable types
            // (invalid manifest/path/mismatch) keep the conflict visible.
            val resolutions = plan.conflicts.associate { conflict ->
                val stored = records.firstOrNull { it.conflictId == conflict.conflictId }?.resolution
                conflict.conflictId to (
                    stored?.takeIf { it != CloudFolderConflictResolution.DEFER }
                        ?: conflict.type.defaultResolution()
                    )
            }
            plan = resolveCloudFolderSync(
                base = base,
                local = local,
                remote = remote,
                plan = plan,
                resolutions = resolutions,
                nowMillis = now,
                deviceId = repository.deviceId,
            )
            cloudFolderLogD(
                "event=conflict_resolution_apply ${traceFields(rootId)} " +
                    "requested=${resolutions.size} " +
                    "autoResolved=${resolutions.values.count { it != CloudFolderConflictResolution.DEFER }} " +
                    "remaining=${plan.conflicts.size} " +
                    "result=${if (plan.conflicts.isEmpty()) "resolved" else "deferred"}",
            )
            if (plan.conflicts.isNotEmpty()) {
                // Only fundamentally undecidable conflicts reach this point
                // (invalid manifest/path, root mismatch). Leave the last
                // committed manifest untouched until the data is repaired.
                repository.reconcileConflicts(plan, now)
                val message = "Cloud-folder sync needs conflict resolution (${plan.conflicts.size} conflict(s))"
                repository.markBindingError(rootId, message)
                pendingMetadata.forEach { pending ->
                    repository.failMetadataOutbox(
                        row = pending,
                        error = message,
                        retryAt = System.currentTimeMillis() + METADATA_CONFLICT_RETRY_DELAY_MILLIS,
                    )
                }
                CloudFolderSyncEvents.notifyStateChanged()
                cloudFolderLogW(
                    "event=sync_root_end ${traceFields(rootId)} result=conflict " +
                        "conflicts=${plan.conflicts.size}",
                )
                return
            }
            // Every decidable conflict was auto-resolved; the durable DEFER
            // records are stale and must not resurface in the settings UI.
            repository.clearConflicts(rootId)
        } else {
            // Remove stale records after a later scan proves that the inputs
            // no longer conflict (for example after an external repair).
            repository.reconcileConflicts(plan, now)
        }

        // A PUSH request never silently folds a remote-only change into the
        // local inventory. Queue a separate idempotent PULL instead. Its
        // unique-work identity is different from this PUSH, so it cannot
        // recurse; pullRoot() revalidates account, binding, selection, and
        // local changes before materializing anything.
        val hasCloudToLocalOperations = plan.operations.any {
            it.direction == CloudFolderSyncDirection.CLOUD_TO_LOCAL
        }
        if (direction == Direction.PUSH && hasCloudToLocalOperations) {
            val remoteChangeMessage = "Remote folder changed; pull queued"
            repository.resetRunningMetadataOutboxForRoot(
                rootId = rootId,
                now = System.currentTimeMillis(),
                error = remoteChangeMessage,
            )
            val stillSelected = repository.isIncluded(rootId)
            val stillBound = repository.getBinding(rootId) != null
            val stillSignedIn = runCatching {
                ensureAccountStillActive()
                true
            }.getOrDefault(false)
            val canQueuePull = shouldQueueCloudFolderPullAfterRemoteChange(
                hasCloudToLocalOperations = hasCloudToLocalOperations,
                isSelected = stillSelected,
                hasBinding = stillBound,
                isSignedIn = stillSignedIn,
                syncEnabled = isCloudFolderSyncEnabled(applicationContext),
            )
            if (canQueuePull) {
                enqueuePull(
                    context = applicationContext,
                    accountId = repository.accountId,
                    rootId = rootId,
                    replace = true,
                )
                cloudFolderLogI(
                    "event=remote_change_pull_enqueue ${traceFields(rootId)} " +
                        "root=${cloudFolderSafeId(rootId)} result=queued replace=true " +
                        "cloudToLocal=${plan.operations.count { it.direction == CloudFolderSyncDirection.CLOUD_TO_LOCAL }} " +
                        "localToCloud=${plan.operations.count { it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD }}",
                )
                repository.markBindingError(rootId, remoteChangeMessage)
            } else {
                cloudFolderLogW(
                    "event=remote_change_pull_skip ${traceFields(rootId)} " +
                        "root=${cloudFolderSafeId(rootId)} result=not_queued " +
                        "selected=$stillSelected bound=$stillBound signedIn=$stillSignedIn " +
                        "syncEnabled=${isCloudFolderSyncEnabled(applicationContext)}",
                )
            }
            CloudFolderSyncEvents.notifyStateChanged()
            cloudFolderLogW(
                "event=sync_root_end ${traceFields(rootId)} result=remote_changed " +
                    "pull=${if (canQueuePull) "queued" else "skipped"}",
            )
            return
        }

        val localOperations = plan.operations.filter {
            it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD
        }
        activeStage = "outbox_upload"
        repository.enqueueAll(
            rootId = rootId,
            operations = localOperations,
            now = now,
            sourceUris = scan.files.associate { it.node.nodeId to it.uri.toString() },
        )
        cloudFolderLogD(
            "event=outbox_enqueue root=$safeRoot operations=${localOperations.size} " +
                "files=${localOperations.count { it.kind == CloudFolderSyncOperationKind.UPLOAD_FILE }}",
        )
        val uploadedObjectIds = drainUploadOutbox(
            accessToken = accessToken,
            rootId = rootId,
            scan = scan,
            manifest = plan.mergedManifest,
        )
        val uploadProgress = repository.getProgress(rootId)
        saveRootProgress(
            rootId = rootId,
            phase = CloudFolderSyncPhase.FINALIZING,
            completedFiles = uploadProgress?.completedFiles ?: totalFiles,
            totalFiles = totalFiles,
            completedBytes = uploadProgress?.completedBytes ?: totalBytes,
            totalBytes = totalBytes,
        )
        val publishedWithOutboxIds = plan.mergedManifest.copy(
            nodes = plan.mergedManifest.nodes.map { node ->
                node.copy(contentObjectId = uploadedObjectIds[node.nodeId] ?: node.contentObjectId)
            },
        ).withUpdatedRootStats(now)
        val (published, repairedObjectIds) = repairMissingContentObjectIds(
            accessToken = accessToken,
            rootId = rootId,
            manifest = publishedWithOutboxIds,
            scan = scan,
        )

        // The manifest is the commit record. It is deliberately uploaded last;
        // orphaned Drive objects are harmless and can be garbage-collected by a
        // later retention pass, while a premature manifest would expose bytes
        // that were not fully uploaded.
        val shouldMaterialize = direction == Direction.SYNC && plan.operations.any {
            it.direction == CloudFolderSyncDirection.CLOUD_TO_LOCAL
        }
        // A repair is a real content-identity change: other devices cannot
        // download the affected bytes until the restored pointers publish.
        val shouldPublish = repairedObjectIds > 0 ||
            remoteMissing ||
            localOperations.isNotEmpty() ||
            !cloudFolderRootsEquivalentForPublish(published.root, remote.root)
        val targetManifest = if (shouldPublish) published else remote
        if (shouldPublish) {
            activeStage = "manifest_publish"
            // A long outbox/repair run can outlive the token fetched at
            // worker start. Refresh before the CAS read+upload so publish
            // never fails on a stale token at the final commit step.
            val publishToken = runCatching { repositoryAccessToken() }.getOrNull()
                ?: accessToken
            publishManifestWithCas(
                accessToken = publishToken,
                rootId = rootId,
                initialRemote = remoteResult,
                manifest = published,
                persistLocalManifest = !shouldMaterialize,
            )
        } else if (!shouldMaterialize) {
            ensureAccountStillActive()
            repository.saveManifest(local)
        }
        if (!shouldMaterialize) {
            // A successful local-only commit supersedes any stale target left
            // by an older interrupted transfer. Keeping it would allow a
            // later resume to overwrite this newer committed base.
            repository.clearPendingMaterialization(rootId)
        }

        if (shouldMaterialize) {
            activeStage = "materialization"
            // The remote commit may succeed before local writes do. Keep the
            // target separate from the committed local base so a killed or
            // failed materialization is resumed before the next scan.
            repository.savePendingMaterialization(targetManifest, now)
            if (appStorageRoot != null) {
                materializeManifestToAppStorage(
                    accessToken = accessToken,
                    manifest = targetManifest,
                )
            } else {
                val contentFilesChanged = materializeManifest(
                    accessToken = accessToken,
                    manifest = targetManifest,
                    localRootUri = requireNotNull(rootUri),
                    expectedBase = base,
                    allowAlreadyMaterialized = true,
                )
                FolderSyncWorker.enqueueCloudFolderIndex(
                    context = applicationContext,
                    accountId = repository.accountId,
                    rootId = rootId,
                    metadataOnly = !contentFilesChanged,
                    localUri = requireNotNull(rootUri).toString(),
                )
            }
            ensureAccountStillActive()
            repository.saveManifest(targetManifest)
            repository.clearPendingMaterialization(rootId)
        }
        repository.clearConflicts(rootId)
        pendingMetadata.forEach { pending ->
            val completed = repository.completeMetadataOutbox(pending)
            cloudFolderLogD(
                "event=metadata_worker_item_end ${traceFields(rootId, pending.bookId, pending.generation)} " +
                    "result=${if (completed) "complete" else "newer_generation"} kinds=${pending.dirtyKinds}",
            )
        }
        ensureAccountStillActive()
        repository.saveBinding(
            binding.copy(
                permissionState = CloudFolderPermissionState.GRANTED,
                lastAcknowledgedRevision = targetManifest.revision,
                lastScanAt = now,
                lastError = null,
            )
        )
        saveRootProgress(
            rootId = rootId,
            phase = CloudFolderSyncPhase.SUCCEEDED,
            completedFiles = totalFiles,
            totalFiles = totalFiles,
            completedBytes = totalBytes,
            totalBytes = totalBytes,
        )
        CloudFolderSyncEvents.notifyStateChanged()
        cloudFolderLogI(
            "event=sync_root_end ${traceFields(rootId)} result=success revision=${targetManifest.revision} " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        if (metadataOnly) {
            cloudFolderLogI(
                "event=metadata_worker_end ${traceFields(rootId)} result=success " +
                    "revision=${targetManifest.revision} rows=${pendingMetadata.size} " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
        }
    }

    /**
     * Reserve the expected remote revision before the immutable Drive upload,
     * then commit the Firestore pointer only after the upload succeeds. A
     * failed upload restores the previous head; orphaned Drive bytes remain
     * unreferenced and are handled by maintenance GC.
     */
    private suspend fun publishManifestWithCas(
        accessToken: String,
        rootId: String,
        initialRemote: CloudFolderManifestReadResult,
        manifest: CloudFolderManifest,
        persistLocalManifest: Boolean = true,
    ) {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        cloudFolderLogD(
            "event=manifest_publish_start ${traceFields(rootId)} " +
                "root=$safeRoot revision=${manifest.revision} " +
                "pro=${BuildConfig.IS_PRO}",
        )
        ensureAccountStillActive()
        assertRemoteSnapshotUnchanged(accessToken, rootId, initialRemote)
        val expectedRevision = (initialRemote as? CloudFolderManifestReadResult.Found)
            ?.manifest
            ?.revision
        val lease = if (BuildConfig.IS_PRO) {
            when (
                val reservation = firestoreRepository.reserveCloudFolderManifest(
                    userId = repository.accountId,
                    rootId = rootId,
                    expectedRevision = expectedRevision,
                    revision = manifest.revision,
                    deviceId = repository.deviceId,
                )
            ) {
                is CloudFolderManifestLeaseResult.Acquired -> {
                    cloudFolderLogD(
                        "event=firestore_reserve ${traceFields(rootId)} root=$safeRoot " +
                            "result=acquired revision=${manifest.revision}",
                    )
                    reservation.lease
                }
                CloudFolderManifestLeaseResult.Conflict -> {
                    cloudFolderLogW(
                        "event=firestore_reserve ${traceFields(rootId)} root=$safeRoot result=conflict",
                    )
                    throw IOException("Cloud-folder manifest revision was claimed; replan before publishing")
                }
                CloudFolderManifestLeaseResult.Unsupported -> {
                    cloudFolderLogW(
                        "event=firestore_reserve ${traceFields(rootId)} root=$safeRoot result=unsupported",
                    )
                    throw IOException("Cloud-folder manifest CAS is unavailable")
                }
            }
        } else {
            null
        }
        var committed = false
        try {
            ensureAccountStillActive()
            val uploadedManifest = driveRepository.uploadCloudFolderManifest(
                accessToken = accessToken,
                manifest = manifest,
                operationId = activeOperationId,
                correlationId = activeCorrelationId,
            )
                ?: throw IOException("Unable to publish cloud-folder manifest")
            cloudFolderLogD(
                "event=manifest_drive_upload ${traceFields(rootId)} root=$safeRoot " +
                    "result=success revision=${manifest.revision}",
            )
            ensureAccountStillActive()
            if (lease != null) {
                val committedHead = firestoreRepository.commitCloudFolderManifest(
                    lease = lease,
                    manifestDriveFileId = uploadedManifest.id,
                    manifestHash = sha256CloudFolderManifest(manifest),
                )
                if (!committedHead) {
                    throw IOException("Cloud-folder manifest CAS was lost; replan before publishing")
                }
                committed = true
                cloudFolderLogD(
                    "event=firestore_commit ${traceFields(rootId)} root=$safeRoot " +
                        "result=success revision=${manifest.revision}",
                )
            }
            if (persistLocalManifest) {
                ensureAccountStillActive()
                repository.saveManifest(manifest)
            }
        } finally {
            if (lease != null && !committed) {
                // The worker can be cancelled at any await point (WorkManager
                // REPLACE, process death).  The remote lease must still be
                // released or every peer observes an abandoned COMMITTING
                // head for the full lease window, and a same-device wake can
                // never reserve again.  NonCancellable keeps this release
                // running even on a cancelled coroutine.
                withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { firestoreRepository.releaseCloudFolderManifest(lease) }
                        .onSuccess { released ->
                            cloudFolderLogW(
                                "event=firestore_release ${traceFields(rootId)} root=$safeRoot result=$released",
                            )
                        }
                        .onFailure { error ->
                            cloudFolderLogError(
                                event = "firestore_release",
                                error = error,
                                details = "${traceFields(rootId)} root=$safeRoot",
                            )
                        }
                }
            }
        }
        cloudFolderLogI(
            "event=manifest_publish_end ${traceFields(rootId)} root=$safeRoot " +
                "result=success revision=${manifest.revision} " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
    }

    private suspend fun drainUploadOutbox(
        accessToken: String,
        rootId: String,
        scan: CloudFolderSafScanResult,
        manifest: CloudFolderManifest,
    ): Map<String, String> {
        val sourceByNodeId = scan.files.associateBy { it.node.nodeId }
        val nodeById = manifest.nodes.associateBy { it.nodeId }
        val uploadedObjectIds = linkedMapOf<String, String>()
        var processed = 0
        var uploaded = 0
        var completedFiles = repository.getProgress(rootId)?.completedFiles ?: 0
        var completedBytes = repository.getProgress(rootId)?.completedBytes ?: 0L
        val totalFiles = repository.getProgress(rootId)?.totalFiles ?: manifest.activeFiles().size
        val totalBytes = repository.getProgress(rootId)?.totalBytes
            ?: manifest.activeFiles().sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        while (true) {
            val rows = repository.claimDueOutbox(rootId, limit = 500)
            if (rows.isEmpty()) break
            cloudFolderLogD(
                "event=upload_batch_start root=$safeRoot rows=${rows.size} " +
                    "attempted=${rows.sumOf { it.attempts }}",
            )
            for (row in rows) {
                try {
                    ensureAccountStillActive()
                    if (row.operationKind == CloudFolderSyncOperationKind.UPLOAD_FILE.name) {
                        val node = nodeById[row.nodeId]
                            ?: run {
                                // A complete scan has already reconciled this
                                // operation against the current local tree.
                                // The source was deleted, so retrying forever
                                // would only keep a stale outbox row alive.
                                repository.completeOutbox(row.operationId)
                                continue
                            }
                        val current = sourceByNodeId[row.sourceNodeId ?: row.nodeId]
                        if (current == null) {
                            // Never complete an upload merely because its
                            // persisted URI cannot be opened. Here the fresh,
                            // complete scan proves the node itself is gone,
                            // making the row stale and safe to cancel.
                            repository.completeOutbox(row.operationId)
                            continue
                        }
                        val persistedUri = row.sourceUri?.trim()?.takeIf { it.isNotBlank() }
                        val source = if (persistedUri == current.uri.toString()) {
                            current
                        } else {
                            // Refresh a locator when a provider rotates a
                            // document URI. The content hash/size check below
                            // still authenticates the bytes before completion.
                            repository.attachOutboxSourceUri(row.operationId, current.uri.toString())
                            current
                        }
                        val driveFile = uploadSafEntry(accessToken, rootId, source, node, row.attempts)
                        uploadedObjectIds[row.nodeId] = driveFile.id
                        uploaded++
                        completedFiles = (completedFiles + 1).coerceAtMost(totalFiles)
                        completedBytes = (completedBytes + node.sizeBytes.coerceAtLeast(0L))
                            .coerceAtMost(totalBytes)
                        saveRootProgress(
                            rootId = rootId,
                            phase = CloudFolderSyncPhase.UPLOADING,
                            completedFiles = completedFiles,
                            totalFiles = totalFiles,
                            completedBytes = completedBytes,
                            totalBytes = totalBytes,
                        )
                    }
                    // Directory, move, metadata, and delete operations are
                    // represented by the immutable manifest. Drive content
                    // objects stay retained until a future safe GC policy.
                    ensureAccountStillActive()
                    repository.completeOutbox(row.operationId)
                    processed++
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val message = error.message ?: "Cloud-folder upload failed"
                    if (row.attempts >= MAX_OUTBOX_ATTEMPTS) {
                        repository.quarantineOutbox(row.operationId, "Retry limit reached: $message")
                    } else {
                        repository.failOutbox(
                            operationId = row.operationId,
                            error = message,
                            retryAt = System.currentTimeMillis() + retryDelayMs(row.attempts),
                        )
                    }
                    cloudFolderLogError(
                        event = "upload_item",
                        error = error,
                        details = "result=${if (row.attempts >= MAX_OUTBOX_ATTEMPTS) "quarantined" else "retry"} " +
                            "root=$safeRoot operation=${cloudFolderSafeId(row.operationId)} attempt=${row.attempts}",
                    )
                    throw error
                }
            }
            cloudFolderLogD(
                "event=upload_batch_end root=$safeRoot processed=$processed uploaded=$uploaded",
            )
        }
        cloudFolderLogI(
            "event=upload_end root=$safeRoot processed=$processed uploaded=$uploaded " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return uploadedObjectIds
    }

    /**
     * Repair manifest nodes whose immutable object pointer was lost upstream
     * (older builds stripped it on metadata-only changes, and a source that
     * disappeared mid-sync used to publish without it). When this device
     * still holds bytes that match the node's authenticated hash and size,
     * re-upload them under the node identity and restore the pointer before
     * publishing. The repair is all-or-nothing: a single failed upload aborts
     * the whole pass with a zero repair count so the caller never publishes a
     * half-healed manifest. Nodes that cannot be repaired locally (no matching
     * bytes) are logged and left for the device that owns them.
     */
    private suspend fun repairMissingContentObjectIds(
        accessToken: String,
        rootId: String,
        manifest: CloudFolderManifest,
        scan: CloudFolderSafScanResult,
    ): Pair<CloudFolderManifest, Int> {
        val missing = manifest.filesMissingContentObjectIds()
        if (missing.isEmpty()) return manifest to 0
        val sourceByNodeId = scan.files.associateBy { it.node.nodeId }
        val repaired = linkedMapOf<String, String>()
        for (node in missing) {
            val source = sourceByNodeId[node.nodeId] ?: continue
            // The scan just hashed this source; only identical bytes may be
            // uploaded under the manifest node's authenticated identity.
            val bytesMatch = source.node.contentHash == node.contentHash &&
                source.node.sizeBytes == node.sizeBytes
            if (!bytesMatch) continue
            try {
                val driveFile = uploadSafEntryWithTokenRefresh(
                    accessToken = accessToken,
                    rootId = rootId,
                    source = source,
                    node = node,
                    attempt = 0,
                )
                repaired[node.nodeId] = driveFile.id
                cloudFolderLogW(
                    "event=manifest_object_repair ${traceFields(rootId)} " +
                        "root=${cloudFolderSafeId(rootId)} node=${cloudFolderSafeId(node.nodeId)} " +
                        "result=uploaded bytes=${node.sizeBytes}",
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                // A single failure (typically a token expiring mid-sequence)
                // aborts the pass without applying the partial set. A
                // half-healed manifest is worse than the status quo: retry
                // with a fresh token instead of publishing it.
                cloudFolderLogError(
                    event = "manifest_object_repair",
                    error = error,
                    details = "${traceFields(rootId)} root=${cloudFolderSafeId(rootId)} " +
                        "node=${cloudFolderSafeId(node.nodeId)} result=aborted " +
                        "repaired=${repaired.size} remaining=${missing.size - repaired.size}",
                )
                return manifest to 0
            }
        }
        val repairedManifest = if (repaired.isEmpty()) {
            manifest
        } else {
            manifest.copy(
                nodes = manifest.nodes.map { node ->
                    repaired[node.nodeId]?.let { objectId -> node.copy(contentObjectId = objectId) } ?: node
                },
            )
        }
        val stillMissing = repairedManifest.filesMissingContentObjectIds()
        cloudFolderLogW(
            "event=manifest_publish_validate ${traceFields(rootId)} " +
                "root=${cloudFolderSafeId(rootId)} revision=${repairedManifest.revision} " +
                "missingObjectIds=${missing.size} repaired=${repaired.size} " +
                "stillMissing=${stillMissing.size} " +
                "nodes=[${stillMissing.take(5).joinToString(",") { cloudFolderSafeId(it.nodeId) }}]",
        )
        return repairedManifest to repaired.size
    }

    /**
     * Upload once, and when Drive reports a stale token (HTTP 401/autherror),
     * re-fetch the token and retry a single time before giving up. This keeps
     * a long repair sequence from dying on a token that expired mid-run.
     */
    private suspend fun uploadSafEntryWithTokenRefresh(
        accessToken: String,
        rootId: String,
        source: CloudFolderSafEntry,
        node: CloudFolderNode,
        attempt: Int,
    ): com.aryan.reader.data.DriveFile {
        return try {
            uploadSafEntry(accessToken, rootId, source, node, attempt)
        } catch (error: Exception) {
            if (!cloudFolderAuthFailureIsTransient(error)) throw error
            val fresh = runCatching { repositoryAccessToken() }.getOrNull()
            if (fresh == null || fresh == accessToken) throw error
            cloudFolderLogW(
                "event=drive_token_refresh ${traceFields(rootId)} " +
                    "root=${cloudFolderSafeId(rootId)} node=${cloudFolderSafeId(node.nodeId)} " +
                    "result=retrying",
            )
            uploadSafEntry(fresh, rootId, source, node, attempt)
        }
    }

    private suspend fun uploadSafEntry(
        accessToken: String,
        rootId: String,
        source: CloudFolderSafEntry,
        node: CloudFolderNode,
        attempt: Int,
    ): com.aryan.reader.data.DriveFile {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        val safeNode = cloudFolderSafeId(node.nodeId)
        cloudFolderLogD(
            "event=drive_upload_start ${cloudFolderTraceFields(activeOperationId, activeCorrelationId)} " +
                "root=$safeRoot node=$safeNode revision=${node.revision} " +
                "bytes=${node.sizeBytes} sizeBucket=${cloudFolderSizeBucket(node.sizeBytes)} " +
                "uploadMode=resumable attempt=$attempt",
        )
        ensureAccountStillActive()
        val input = if (source.uri.scheme.equals("file", ignoreCase = true)) {
            source.uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
        } else {
            applicationContext.contentResolver.openInputStream(source.uri)
        } ?: throw IOException("Unable to open local stream for ${node.relativePath}")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashingInput = DigestCountingInputStream(input, digest)
        val uploaded = hashingInput.use {
            driveRepository.uploadCloudFolderFile(
                accessToken = accessToken,
                rootId = rootId,
                nodeId = node.nodeId,
                relativePath = node.relativePath,
                mimeType = node.mimeType,
                input = it,
                sizeBytes = node.sizeBytes,
                revision = node.revision,
                contentHash = node.contentHash,
                attempt = attempt,
                operationId = activeOperationId,
                correlationId = activeCorrelationId,
            )
        } ?: throw IOException("Drive rejected ${node.relativePath}")
        ensureAccountStillActive()
        val actualHash = "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        val expectedHash = canonicalCloudFolderContentHash(node.contentHash)
        if (expectedHash != null && actualHash != expectedHash) {
            throw IOException("SAF content changed while uploading ${node.relativePath}")
        }
        if (hashingInput.count != node.sizeBytes) {
            throw IOException("SAF size changed while uploading ${node.relativePath}")
        }
        cloudFolderLogD(
            "event=drive_upload_end ${cloudFolderTraceFields(activeOperationId, activeCorrelationId)} " +
                "root=$safeRoot node=$safeNode result=success " +
                "bytes=${hashingInput.count} durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return uploaded
    }

    private suspend fun materializeManifest(
        accessToken: String,
        manifest: CloudFolderManifest,
        localRootUri: Uri,
        expectedBase: CloudFolderManifest? = null,
        allowAlreadyMaterialized: Boolean = false,
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(manifest.rootId)
        val root = DocumentFile.fromTreeUri(applicationContext, localRootUri)
            ?: throw IOException("Local SAF root is unavailable")
        if (!root.isDirectory) throw IOException("Local SAF root is not a directory")
        var contentFilesChanged = false
        var skippedFiles = 0
        val directories = manifest.activeDirectories().sortedWith(
            compareBy<CloudFolderNode> { pathDepth(it.relativePath) }.thenBy { it.pathKey }
        )
        val files = manifest.activeFiles().sortedWith(
            compareBy<CloudFolderNode> { pathDepth(it.relativePath) }.thenBy { it.pathKey }
        )
        cloudFolderLogI(
            "event=materialize_start root=$safeRoot mode=saf " +
                "directories=${directories.size} files=${files.size} " +
                "bytes=${manifest.activeFiles().sumOf { it.sizeBytes.coerceAtLeast(0L) }}",
        )
        try {
            // Directory creation and verified-file skips are the steady-state
            // case; keep them quiet and summarize at the end so a no-op pull
            // stays readable in logcat.
            for (directory in directories) {
                currentCoroutineContext().ensureActive()
                ensureDirectory(root, directory.relativePath)
            }
            for ((index, node) in files.withIndex()) {
                currentCoroutineContext().ensureActive()
                val ordinal = index + 1
                // Verified local bytes take precedence; only a download needs
                // the immutable object pointer. See writeRemoteFileAtomically.
                val objectId = node.contentObjectId?.trim()?.takeIf(String::isNotBlank)
                val parent = ensureDirectory(root, parentPath(node.relativePath))
                val expectedLocalNode = expectedBase?.activeNodes()?.firstOrNull { baseNode ->
                    baseNode.nodeId == node.nodeId && baseNode.relativePath == node.relativePath
                }
                try {
                    val changed = writeRemoteFileAtomically(
                        accessToken = accessToken,
                        parent = parent,
                        node = node,
                        objectId = objectId,
                        expectedLocalNode = expectedLocalNode,
                        allowAlreadyMaterialized = allowAlreadyMaterialized,
                    )
                    if (changed && !isCloudFolderMetadataSidecarPath(node.relativePath)) {
                        contentFilesChanged = true
                    }
                    if (changed) {
                        cloudFolderLogD(
                            "event=materialize_file_end root=$safeRoot node=${cloudFolderSafeId(node.nodeId)} mode=saf " +
                                "result=success ordinal=$ordinal totalFiles=${files.size} " +
                                "changed=true",
                        )
                    } else {
                        skippedFiles++
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val safe = cloudFolderTransferFailure(
                        error = error,
                        stage = "materialize_file",
                        category = "file_transfer_failure",
                    )
                    cloudFolderLogError(
                        event = "materialize_file_end",
                        error = safe,
                        details = "root=$safeRoot node=${cloudFolderSafeId(node.nodeId)} mode=saf result=failure " +
                            "ordinal=$ordinal totalFiles=${files.size} stage=${safe.stage}",
                    )
                    throw safe
                }
            }
            if (applySafTombstones(root, manifest.tombstones)) {
                contentFilesChanged = true
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            val safe = cloudFolderTransferFailure(
                error = error,
                stage = "materialize_saf",
                category = "materialization_failure",
            )
            cloudFolderLogError(
                event = "materialize_end",
                error = safe,
                details = "root=$safeRoot mode=saf result=failure durationMs=" +
                    "${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)} stage=${safe.stage}",
            )
            throw safe
        }
        cloudFolderLogI(
            "event=materialize_end root=$safeRoot mode=saf result=success " +
                "files=${files.size} downloaded=${files.size - skippedFiles} " +
                "verifiedSkips=$skippedFiles durationMs=" +
                "${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
        return contentFilesChanged
    }

    /**
     * Apply deletions only when the local item still matches the bytes
     * recorded in the tombstone. Unknown or changed content is preserved.
     */
    private suspend fun applySafTombstones(
        root: DocumentFile,
        tombstones: List<CloudFolderTombstone>,
    ): Boolean {
        var contentDeleted = false
        for (tombstone in tombstones.sortedWith(
            compareByDescending<CloudFolderTombstone> { pathDepth(it.relativePath) }
                .thenBy { it.pathKey }
                .thenBy { it.nodeId }
        )) {
            currentCoroutineContext().ensureActive()
            ensureAccountStillActive()
            val target = findDocument(root, tombstone.relativePath) ?: continue
            if (tombstone.kind == CloudFolderNodeKind.FILE) {
                val expectedHash = canonicalCloudFolderContentHash(tombstone.lastKnownContentHash)
                    ?: throw IOException("Refusing to delete unverified local file: ${tombstone.relativePath}")
                val (actualHash, size) = hashDocument(target)
                if (actualHash != expectedHash || size != tombstone.lastKnownSizeBytes) {
                    throw IOException("Local file changed; tombstone preserved: ${tombstone.relativePath}")
                }
            } else {
                if (!target.isDirectory) {
                    throw IOException("Local path type changed; tombstone preserved: ${tombstone.relativePath}")
                }
                if (target.listFiles().orEmpty().isNotEmpty()) {
                    throw IOException("Local directory is not empty; tombstone preserved: ${tombstone.relativePath}")
                }
            }
            ensureAccountStillActive()
            if (!target.delete()) {
                throw IOException("Unable to apply cloud-folder tombstone: ${tombstone.relativePath}")
            }
            if (!isCloudFolderMetadataSidecarPath(tombstone.relativePath)) {
                contentDeleted = true
            }
        }
        return contentDeleted
    }

    private fun findDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        var current = root
        for (segment in relativePath.split('/')) {
            val normalized = normalizeCloudFolderRelativePath(segment)
                ?: throw IOException("Unsafe local folder path")
            current = current.findFile(normalized) ?: return null
        }
        return current
    }

    private suspend fun hashDocument(document: DocumentFile): Pair<String, Long> =
        withContext(Dispatchers.IO) {
            val input = applicationContext.contentResolver.openInputStream(document.uri)
                ?: throw IOException("Unable to read local file for tombstone: ${document.name}")
            input.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    digest.update(buffer, 0, read)
                    count += read
                }
                "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) } to count
            }
        }

    /** Materialize DOWNLOAD_ALL into app-private storage, without a SAF grant. */
    private suspend fun materializeManifestToAppStorage(
        accessToken: String,
        manifest: CloudFolderManifest,
    ) = withContext(Dispatchers.IO) {
        val safeRoot = cloudFolderSafeId(manifest.rootId)
        val directories = manifest.activeDirectories().sortedWith(
            compareBy<CloudFolderNode> { pathDepth(it.relativePath) }.thenBy { it.pathKey }
        )
        val files = manifest.activeFiles().sortedWith(
            compareBy<CloudFolderNode> { pathDepth(it.relativePath) }.thenBy { it.pathKey }
        )
        val totalBytes = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val startedAt = System.currentTimeMillis()
        cloudFolderLogI(
            "event=materialize_start root=$safeRoot mode=app_storage " +
                "directories=${directories.size} files=${files.size} bytes=$totalBytes",
        )
        try {
            val root = cloudFolderAppRootDirectory(applicationContext.filesDir, manifest.rootId)
            if (!root.exists() && !root.mkdirs()) throw IOException("Unable to create offline folder")
            if (!root.isDirectory) throw IOException("Offline folder is not a directory")

            for (directory in directories) {
                currentCoroutineContext().ensureActive()
                val target = safeAppPath(root, directory.relativePath)
                if (target.exists() && !target.isDirectory) {
                    throw IOException("Offline path is a file: ${directory.relativePath}")
                }
                if (!target.exists() && !target.mkdirs()) {
                    throw IOException("Unable to create offline directory: ${directory.relativePath}")
                }
            }

            // The existing progress schema has no DOWNLOADING phase. Reuse
            // its determinate transfer phase for this local materialization;
            // this changes only the persisted progress projection, not sync
            // or retry behavior.
            saveRootProgress(
                rootId = manifest.rootId,
                phase = CloudFolderSyncPhase.UPLOADING,
                totalFiles = files.size,
                totalBytes = totalBytes,
            )
            var completedFiles = 0
            var completedBytes = 0L
            var contentFilesChanged = false
            var skippedFiles = 0
            for ((index, node) in files.withIndex()) {
                currentCoroutineContext().ensureActive()
                val ordinal = index + 1
                val safeNode = cloudFolderSafeId(node.nodeId)
                // A missing object pointer is not immediately fatal: the
                // materializer first verifies any existing local bytes
                // against the authenticated hash and only downloads when
                // they differ. Only then is the object ID required.
                val objectId = node.contentObjectId?.trim()?.takeIf(String::isNotBlank)
                val target = safeAppPath(root, node.relativePath)
                target.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create offline parent")
                }
                try {
                    val materialized = writeAppFileAtomically(accessToken, target, node, objectId)
                    if (materialized && !isCloudFolderMetadataSidecarPath(node.relativePath)) {
                        contentFilesChanged = true
                    }
                    completedFiles = ordinal
                    completedBytes = (completedBytes + node.sizeBytes.coerceAtLeast(0L))
                        .coerceAtMost(totalBytes)
                    saveRootProgress(
                        rootId = manifest.rootId,
                        phase = CloudFolderSyncPhase.UPLOADING,
                        completedFiles = completedFiles,
                        totalFiles = files.size,
                        completedBytes = completedBytes,
                        totalBytes = totalBytes,
                    )
                    if (materialized) {
                        cloudFolderLogD(
                            "event=materialize_file_end root=$safeRoot node=$safeNode result=success " +
                                "ordinal=$ordinal totalFiles=${files.size} bytes=${node.sizeBytes} " +
                                "transfer=downloaded",
                        )
                    } else {
                        skippedFiles++
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val safe = cloudFolderTransferFailure(
                        error = error,
                        stage = "materialize_file",
                        category = "file_transfer_failure",
                    )
                    cloudFolderLogError(
                        event = "materialize_file_end",
                        error = safe,
                        details = "root=$safeRoot node=$safeNode result=failure " +
                            "ordinal=$ordinal totalFiles=${files.size} bytes=${node.sizeBytes} " +
                            "stage=${safe.stage}",
                    )
                    throw safe
                }
            }
            val tombstonesChanged = applyAppTombstones(root, manifest.tombstones)
            contentFilesChanged = contentFilesChanged || tombstonesChanged
            // Keep the folder tab and the legacy local index in step with a
            // completed download. The registry is separate from the SAF
            // folder list so older builds simply ignore this app-private
            // materialization.
            CloudFolderAppStoragePrefs.ensure(
                context = applicationContext,
                accountId = repository.accountId,
                rootId = manifest.rootId,
                name = manifest.root.name,
            )
            FolderSyncWorker.enqueueCloudFolderIndex(
                context = applicationContext,
                accountId = repository.accountId,
                rootId = manifest.rootId,
                metadataOnly = !contentFilesChanged,
            )
            cloudFolderLogI(
                "event=materialize_end root=$safeRoot mode=app_storage result=success " +
                    "files=$completedFiles downloaded=${completedFiles - skippedFiles} " +
                    "verifiedSkips=$skippedFiles bytes=$completedBytes durationMs=" +
                    "${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            val safe = cloudFolderTransferFailure(
                error = error,
                stage = "materialize_app_storage",
                category = "materialization_failure",
            )
            cloudFolderLogError(
                event = "materialize_end",
                error = safe,
                details = "root=$safeRoot mode=app_storage result=failure durationMs=" +
                    "${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)} stage=${safe.stage}",
            )
            throw safe
        }
    }

    private fun safeAppPath(root: File, relativePath: String): File {
        val normalized = normalizeCloudFolderRelativePath(relativePath)
            ?: throw IOException("Unsafe offline folder path")
        val target = File(root, normalized).canonicalFile
        val prefix = root.path + File.separator
        if (target.path != root.path && !target.path.startsWith(prefix)) {
            throw IOException("Offline folder path escapes root")
        }
        return target
    }

    /**
     * Build the cloud protocol inventory for an app-private KEEP_OFFLINE
     * root. Metadata-only wakes inspect only the two sidecars belonging to
     * each dirty book and reuse the authenticated base inventory for every
     * other node. A normal sync performs a complete hash inventory so
     * external edits/deletions are still detected accurately.
     */
    private suspend fun scanAppStorageForSync(
        root: File,
        rootId: String,
        deviceId: String,
        base: CloudFolderManifest?,
        metadataOnly: Boolean,
        requiredKindsByBook: Map<String, Set<String>>,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderSafScanResult = withContext(Dispatchers.IO) {
        val safeRoot = cloudFolderSafeId(rootId)
        cloudFolderLogD(
            "event=app_scan_start root=$safeRoot metadataOnly=$metadataOnly " +
                "pendingBooks=${requiredKindsByBook.size}",
        )
        if (!root.isDirectory) {
            return@withContext CloudFolderSafScanResult(
                entries = emptyList(),
                complete = false,
                scannedAt = now,
                errorMessage = "Offline folder is not a directory",
            )
        }

        val baseByPath = base?.activeNodes()
            ?.associateBy { it.pathKey }
            .orEmpty()
        val entries = linkedMapOf<String, CloudFolderSafEntry>()
        var complete = true
        var firstError: String? = null

        fun nodeIdFor(path: String): String =
            baseByPath[com.aryan.reader.shared.cloudFolderPathKey(path)]?.nodeId
                ?: com.aryan.reader.shared.cloudFolderNodeId(rootId, path)

        suspend fun addFile(file: File, path: String, baseNode: CloudFolderNode? = null) {
            val normalized = normalizeCloudFolderRelativePath(path)
            if (normalized == null) {
                complete = false
                firstError = firstError ?: "Offline folder returned an unsafe path"
                return
            }
            val identity = baseNode ?: baseByPath[com.aryan.reader.shared.cloudFolderPathKey(normalized)]
            val hashAndSize = try {
                hashAppFile(file)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                complete = false
                firstError = firstError ?: "Offline file read failed (${cloudFolderErrorStatus(error)})"
                return
            }
            val node = CloudFolderNode(
                nodeId = identity?.nodeId ?: nodeIdFor(normalized),
                rootId = rootId,
                relativePath = normalized,
                kind = CloudFolderNodeKind.FILE,
                contentHash = hashAndSize.first,
                sizeBytes = hashAndSize.second,
                mimeType = java.net.URLConnection.guessContentTypeFromName(file.name),
                fileModifiedAt = file.lastModified().coerceAtLeast(0L),
                revision = identity?.revision ?: 0L,
                modifiedAt = now,
                modifiedByDeviceId = deviceId,
                contentObjectId = identity?.contentObjectId,
            )
            entries[normalized] = CloudFolderSafEntry(Uri.fromFile(file), node)
        }

        if (metadataOnly && base != null) {
            // Preserve every known node so a targeted sidecar wake cannot
            // infer unrelated deletions or force a whole-root rehash. Only
            // the sidecars matching each book's dirty kinds are re-hashed.
            val targetPaths = metadataWakeTargetPaths(requiredKindsByBook)
            base.activeNodes().forEach { node ->
                val file = safeAppPath(root, node.relativePath)
                if (node.isDirectory) {
                    entries[node.relativePath] = CloudFolderSafEntry(Uri.fromFile(file), node.copy(modifiedAt = now))
                } else if (file.isFile && node.pathKey in targetPaths) {
                    addFile(file, node.relativePath, node)
                } else if (node.isFile) {
                    // The committed manifest already authenticates this
                    // content. Reuse its hash/object identity for a targeted
                    // sidecar wake; a subsequent full sync still checks the
                    // entire root and catches external edits.
                    entries[node.relativePath] = CloudFolderSafEntry(Uri.fromFile(file), node.copy(modifiedAt = now))
                }
            }
            requiredKindsByBook.forEach { (bookId, kinds) ->
                kinds.forEach { kind ->
                    val path = when (kind) {
                        CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS ->
                            "$LOCAL_FOLDER_SYNC_DATA_DIR/${localFolderSyncAnnotationFileName(bookId)}"
                        else -> "$LOCAL_FOLDER_SYNC_DATA_DIR/${localFolderSyncMetadataFileName(bookId)}"
                    }
                    val file = safeAppPath(root, path)
                    if (file.isFile) addFile(file, path)
                }
            }
        } else {
            val pending = ArrayDeque<Pair<File, String>>()
            pending.add(root to "")
            val visited = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (directory, parentPath) = pending.removeFirst()
                val key = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
                if (!visited.add(key)) {
                    complete = false
                    firstError = firstError ?: "Offline folder contains a directory cycle"
                    continue
                }
                val children = directory.listFiles()
                if (children == null) {
                    complete = false
                    firstError = firstError ?: "Offline folder could not be listed"
                    continue
                }
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    // Never promote interrupted atomic-transfer staging files
                    // into the logical cloud folder.
                    if (child.name.endsWith(".part") || child.name.endsWith(".bak") ||
                        child.name.startsWith(".cloud-folder-") ||
                        (child.name.startsWith(".$LOCAL_FOLDER_SIDECAR_HASH_PREFIX") && child.name.endsWith(".tmp"))
                    ) {
                        continue
                    }
                    val path = if (parentPath.isBlank()) child.name else "$parentPath/${child.name}"
                    val normalized = normalizeCloudFolderRelativePath(path)
                    if (normalized == null) {
                        complete = false
                        firstError = firstError ?: "Offline folder returned an unsafe path"
                        continue
                    }
                    if (child.isDirectory) {
                        val identity = baseByPath[com.aryan.reader.shared.cloudFolderPathKey(normalized)]
                        val node = CloudFolderNode(
                            nodeId = identity?.nodeId ?: nodeIdFor(normalized),
                            rootId = rootId,
                            relativePath = normalized,
                            kind = CloudFolderNodeKind.DIRECTORY,
                            fileModifiedAt = child.lastModified().coerceAtLeast(0L),
                            revision = identity?.revision ?: 0L,
                            modifiedAt = now,
                            modifiedByDeviceId = deviceId,
                            contentObjectId = identity?.contentObjectId,
                        )
                        entries[normalized] = CloudFolderSafEntry(Uri.fromFile(child), node)
                        pending.add(child to normalized)
                    } else if (child.isFile) {
                        addFile(child, normalized)
                    } else {
                        complete = false
                        firstError = firstError ?: "Offline folder returned an unknown entry type"
                    }
                }
            }
        }
        val result = CloudFolderSafScanResult(
            entries = entries.values.sortedWith(compareBy<CloudFolderSafEntry> { it.node.pathKey }.thenBy { it.node.nodeId }),
            complete = complete,
            scannedAt = now,
            errorMessage = firstError,
        )
        cloudFolderLogD(
            "event=app_scan_end root=$safeRoot metadataOnly=$metadataOnly complete=${result.complete} " +
                "files=${result.files.size} directories=${result.directories.size} " +
                "errorStatus=${cloudFolderErrorStatus(result.errorMessage)}",
        )
        result
    }

    /**
     * Required sidecar presence for a targeted metadata wake.
     *
     * Only the sidecars matching each book's dirty kinds must exist: the
     * annotation sidecar is a PDF artifact, so a METADATA-only EPUB wake
     * must not be downgraded into a full-tree re-hash because it is absent.
     */
    private fun appStorageMetadataTargetsAvailable(
        root: File,
        requiredKindsByBook: Map<String, Set<String>>,
    ): Boolean {
        if (requiredKindsByBook.isEmpty()) return false
        return requiredKindsByBook.all { (bookId, kinds) ->
            kinds.all { kind ->
                val name = when (kind) {
                    CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS ->
                        localFolderSyncAnnotationFileName(bookId)
                    else -> localFolderSyncMetadataFileName(bookId)
                }
                File(root, "$LOCAL_FOLDER_SYNC_DATA_DIR/$name").isFile
            }
        }
    }

    /** Targeted SAF inventory used after a validated sidecar commit. */
    private suspend fun scanSafStorageForMetadata(
        rootUri: Uri,
        rootId: String,
        deviceId: String,
        base: CloudFolderManifest?,
        requiredKindsByBook: Map<String, Set<String>>,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderSafScanResult = withContext(Dispatchers.IO) {
        val safeRoot = cloudFolderSafeId(rootId)
        cloudFolderLogD(
            "event=saf_targeted_scan_start root=$safeRoot mode=saf " +
                "pendingBooks=${requiredKindsByBook.size}",
        )
        val root = try {
            DocumentFile.fromTreeUri(applicationContext, rootUri)
        } catch (error: Exception) {
            logMetadataStageFailure(
                rootId = rootId,
                stage = "metadata_local_scan",
                category = "metadata_saf_root_lookup",
                error = error,
                details = "mode=saf pendingBooks=${requiredKindsByBook.size}",
            )
            throw error
        }
        if (root == null) {
            cloudFolderLogW(
                "event=saf_targeted_scan_end root=$safeRoot mode=saf result=failure " +
                    "category=metadata_saf_root_missing pendingBooks=${requiredKindsByBook.size}",
            )
            return@withContext CloudFolderSafScanResult(emptyList(), false, now, "SAF root is unavailable")
        }
        if (!root.isDirectory || base == null) {
            cloudFolderLogW(
                "event=saf_targeted_scan_end root=$safeRoot mode=saf result=failure " +
                    "category=${if (!root.isDirectory) "metadata_saf_root_not_directory" else "metadata_base_missing"} " +
                    "pendingBooks=${requiredKindsByBook.size}",
            )
            return@withContext CloudFolderSafScanResult(
                emptyList(),
                false,
                now,
                "Cannot target sidecar sync without a committed folder inventory",
            )
        }
        val targetPaths = metadataWakeTargetPaths(requiredKindsByBook)
        val requiredPathsByBook = requiredKindsByBook.mapValues { (bookId, kinds) ->
            kinds.mapNotNull { kind ->
                when (kind) {
                    CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS ->
                        "$LOCAL_FOLDER_SYNC_DATA_DIR/${localFolderSyncAnnotationFileName(bookId)}"
                    else -> "$LOCAL_FOLDER_SYNC_DATA_DIR/${localFolderSyncMetadataFileName(bookId)}"
                }
            }
        }
        val baseByPath = base.activeNodes().associateBy { it.pathKey }
        val entries = linkedMapOf<String, CloudFolderSafEntry>()

        base.activeNodes().forEach { node ->
            val existing = try {
                findDocument(root, node.relativePath)
            } catch (error: Exception) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "metadata_local_scan",
                    category = "metadata_saf_node_resolution",
                    error = error,
                    details = "mode=saf node=${cloudFolderSafeId(node.nodeId)}",
                )
                throw error
            }
            val uri = existing?.uri ?: Uri.EMPTY
            if (node.isDirectory) {
                entries[node.relativePath] = CloudFolderSafEntry(uri, node.copy(modifiedAt = now))
            } else if (node.pathKey in targetPaths && existing?.isFile == true) {
                val (hash, size) = try {
                    hashDocument(existing)
                } catch (error: Exception) {
                    logMetadataStageFailure(
                        rootId = rootId,
                        stage = "metadata_local_scan",
                        category = "metadata_saf_sidecar_hash",
                        error = error,
                        details = "mode=saf node=${cloudFolderSafeId(node.nodeId)}",
                    )
                    throw error
                }
                entries[node.relativePath] = CloudFolderSafEntry(
                    uri,
                    node.copy(
                        contentHash = hash,
                        sizeBytes = size,
                        mimeType = existing.type ?: node.mimeType,
                        fileModifiedAt = existing.lastModified().coerceAtLeast(0L),
                        modifiedAt = now,
                        modifiedByDeviceId = deviceId,
                    ),
                )
            } else {
                // Reuse the committed hash/object for all non-target nodes;
                // this avoids reopening and hashing the whole SAF tree.
                entries[node.relativePath] = CloudFolderSafEntry(uri, node.copy(modifiedAt = now))
            }
        }

        requiredPathsByBook.values.flatten().distinct().forEach { path ->
            val existing = try {
                findDocument(root, path)
            } catch (error: Exception) {
                logMetadataStageFailure(
                    rootId = rootId,
                    stage = "metadata_local_scan",
                    category = "metadata_saf_sidecar_resolution",
                    error = error,
                    details = "mode=saf path=${cloudFolderSafeId(path)}",
                )
                throw error
            }
            if (existing?.isFile == true && path !in entries) {
                val (hash, size) = try {
                    hashDocument(existing)
                } catch (error: Exception) {
                    logMetadataStageFailure(
                        rootId = rootId,
                        stage = "metadata_local_scan",
                        category = "metadata_saf_sidecar_hash",
                        error = error,
                        details = "mode=saf path=${cloudFolderSafeId(path)}",
                    )
                    throw error
                }
                val old = baseByPath[cloudFolderPathKey(path)]
                val node = CloudFolderNode(
                    nodeId = old?.nodeId ?: com.aryan.reader.shared.cloudFolderNodeId(rootId, path),
                    rootId = rootId,
                    relativePath = path,
                    kind = CloudFolderNodeKind.FILE,
                    contentHash = hash,
                    sizeBytes = size,
                    mimeType = existing.type,
                    fileModifiedAt = existing.lastModified().coerceAtLeast(0L),
                    revision = old?.revision ?: 0L,
                    modifiedAt = now,
                    modifiedByDeviceId = deviceId,
                    contentObjectId = old?.contentObjectId,
                )
                entries[path] = CloudFolderSafEntry(existing.uri, node)
            }
        }
        CloudFolderSafScanResult(
            entries = entries.values.sortedWith(compareBy<CloudFolderSafEntry> { it.node.pathKey }.thenBy { it.node.nodeId }),
            complete = true,
            scannedAt = now,
        ).also {
            cloudFolderLogD(
                "event=saf_targeted_scan_end root=$safeRoot files=${it.files.size} " +
                    "directories=${it.directories.size} pendingBooks=${requiredKindsByBook.size}",
            )
        }
    }

    /**
     * Required sidecar presence for a targeted metadata wake.
     *
     * Only the sidecars matching each book's dirty kinds must exist: the
     * annotation sidecar is a PDF artifact, so a METADATA-only EPUB wake
     * must not be downgraded into a full-tree re-hash because it is absent.
     */
    private fun safMetadataTargetsAvailable(
        rootUri: Uri,
        rootId: String,
        requiredKindsByBook: Map<String, Set<String>>,
    ): Boolean {
        if (requiredKindsByBook.isEmpty()) return false
        cloudFolderLogD(
            "event=metadata_target_resolution root=${cloudFolderSafeId(rootId)} mode=saf " +
                "result=start books=${requiredKindsByBook.size}",
        )
        val root = try {
            DocumentFile.fromTreeUri(applicationContext, rootUri)
        } catch (error: Exception) {
            logMetadataStageFailure(
                rootId = rootId,
                stage = "metadata_target_resolution",
                category = "metadata_saf_root_lookup",
                error = error,
                details = "mode=saf books=${requiredKindsByBook.size}",
            )
            throw error
        }
        if (root == null || !root.isDirectory) {
            cloudFolderLogW(
                "event=metadata_target_resolution root=${cloudFolderSafeId(rootId)} mode=saf " +
                    "result=unavailable category=metadata_saf_root_missing books=${requiredKindsByBook.size}",
            )
            return false
        }
        val dataDir = try {
            root.findFile(LOCAL_FOLDER_SYNC_DATA_DIR)?.takeIf { it.isDirectory }
        } catch (error: Exception) {
            logMetadataStageFailure(
                rootId = rootId,
                stage = "metadata_target_resolution",
                category = "metadata_saf_sidecar_directory_lookup",
                error = error,
                details = "mode=saf books=${requiredKindsByBook.size}",
            )
            throw error
        }
        if (dataDir == null) {
            cloudFolderLogW(
                "event=metadata_target_resolution root=${cloudFolderSafeId(rootId)} mode=saf " +
                    "result=missing category=metadata_sidecar_directory_missing books=${requiredKindsByBook.size}",
            )
            return false
        }
        return requiredKindsByBook.all { (bookId, kinds) ->
            kinds.all { kind ->
                val name = when (kind) {
                    CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS ->
                        localFolderSyncAnnotationFileName(bookId)
                    else -> localFolderSyncMetadataFileName(bookId)
                }
                val present = try {
                    dataDir.findFile(name)?.isFile == true
                } catch (error: Exception) {
                    logMetadataStageFailure(
                        rootId = rootId,
                        stage = "metadata_target_resolution",
                        category = "metadata_saf_sidecar_lookup",
                        error = error,
                        details = "mode=saf kind=${kind.lowercase()} book=${cloudFolderSafeId(bookId)}",
                    )
                    throw error
                }
                if (!present) {
                    cloudFolderLogW(
                        "event=metadata_target_resolution root=${cloudFolderSafeId(rootId)} mode=saf " +
                            "result=missing category=metadata_sidecar_missing " +
                            "book=${cloudFolderSafeId(bookId)} kind=$kind",
                    )
                }
                present
            }
        }
    }

    private suspend fun hashAppFile(file: File): Pair<String, Long> = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                count += read
            }
        }
        "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) } to count
    }

    private suspend fun writeAppFileAtomically(
        accessToken: String,
        target: File,
        node: CloudFolderNode,
        objectId: String?,
    ): Boolean {
        val safeRoot = cloudFolderSafeId(node.rootId)
        val safeNode = cloudFolderSafeId(node.nodeId)
        ensureAccountStillActive()
        if (target.isDirectory) throw IOException("Offline path is a directory: ${node.relativePath}")
        val suffix = stableTempSuffix(node.nodeId)
        val temp = File(target.parentFile, ".${target.name}.$suffix.part")
        val backup = File(target.parentFile, ".${target.name}.$suffix.bak")
        val expectedHash = canonicalCloudFolderContentHash(node.contentHash)
        if (expectedHash != null && cloudFolderAppFileMatches(target, expectedHash, node.sizeBytes)) {
            ensureAccountStillActive()
            // A previous cancelled attempt may have left hidden staging
            // files behind. The verified target is authoritative, so clean
            // only this node's deterministic staging names before returning.
            // The skip is counted in the materialize_end summary; logging it
            // per file made every steady-state pull dozens of lines long.
            temp.delete()
            backup.delete()
            // Keep the logical source mtime aligned with the manifest so a
            // later local scan sees the committed state even before the
            // stabilization rule is applied; harmless when it already agrees.
            if (node.fileModifiedAt > 0L) {
                runCatching { target.setLastModified(node.fileModifiedAt) }
            }
            return false
        }
        // The local bytes could not be verified, so the missing content is
        // required. Fail with a bounded, node-identifying transfer error so
        // the log pinpoints the poisoned manifest entry.
        val resolvedObjectId = objectId?.trim()?.takeIf(String::isNotBlank)
            ?: throw cloudFolderTransferFailure(
                error = IOException("Cloud object is missing for ${node.relativePath}"),
                stage = "drive_download",
                category = "missing_content_object",
            )
        cloudFolderLogD(
            "event=materialize_fs_download_start root=$safeRoot node=$safeNode " +
                "bytes=${node.sizeBytes} objectIdMissing=${objectId == null}",
        )
        temp.delete()
        backup.delete()
        val output = try {
            FileOutputStream(temp)
        } catch (error: Exception) {
            val safe = cloudFolderTransferFailure(error, "temp_open", "filesystem_temp_open")
            cloudFolderLogError(
                event = "materialize_fs_temp_end",
                error = safe,
                details = "root=$safeRoot node=$safeNode result=failure stage=${safe.stage}",
            )
            throw safe
        }
        var stage = "drive_download"
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashingOutput = CloudFolderDigestCountingOutputStream(output, digest)
            try {
                driveRepository.downloadCloudFolderFileTo(
                    accessToken = accessToken,
                    fileId = resolvedObjectId,
                    output = hashingOutput,
                    expectedRootId = node.rootId,
                    expectedNodeId = node.nodeId,
                    expectedRevision = node.revision,
                    expectedContentHash = canonicalCloudFolderContentHash(node.contentHash)
                        ?: throw IOException("Cloud file has no authenticated hash: ${node.relativePath}"),
                    expectedSizeBytes = node.sizeBytes,
                    operationId = activeOperationId,
                    correlationId = activeCorrelationId,
                )
            } finally {
                hashingOutput.close()
            }
            val actualHash = "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actualHash != canonicalCloudFolderContentHash(node.contentHash) ||
                hashingOutput.count != node.sizeBytes
            ) {
                stage = "payload_verify"
                throw CloudFolderTransferException(
                    stage = stage,
                    category = "offline_payload_mismatch",
                    statusCategory = "unknown",
                )
            }
            var stagedExisting = false
            if (target.exists()) {
                ensureAccountStillActive()
                stage = "backup_stage"
                if (!target.renameTo(backup)) throw IOException("Unable to stage offline file")
                stagedExisting = true
            }
            try {
                ensureAccountStillActive()
                stage = "temp_commit"
                if (!temp.renameTo(target)) throw IOException("Unable to commit offline file")
                // Restore the manifest's logical mtime so a later scan does
                // not mistake the download clock for a local edit. Sidecars
                // are already exempt from mtime equivalence; content files
                // still need a stable provider timestamp across devices.
                if (node.fileModifiedAt > 0L) {
                    runCatching { target.setLastModified(node.fileModifiedAt) }
                }
                backup.delete()
            } catch (error: Exception) {
                temp.delete()
                if (stagedExisting) backup.renameTo(target)
                throw error
            }
        } catch (error: Exception) {
            temp.delete()
            val safe = cloudFolderTransferFailure(error, stage, "filesystem_materialization_failure")
            cloudFolderLogError(
                event = "materialize_fs_end",
                error = safe,
                details = "root=$safeRoot node=$safeNode result=failure stage=${safe.stage}",
            )
            throw safe
        }
        return true
    }

    private suspend fun applyAppTombstones(root: File, tombstones: List<CloudFolderTombstone>): Boolean {
        var deletedAny = false
        for (tombstone in tombstones.sortedWith(
            compareByDescending<CloudFolderTombstone> { pathDepth(it.relativePath) }
                .thenBy { it.pathKey }
                .thenBy { it.nodeId }
        )) {
            currentCoroutineContext().ensureActive()
            ensureAccountStillActive()
            val target = safeAppPath(root, tombstone.relativePath)
            if (!target.exists()) continue
            if (tombstone.kind == CloudFolderNodeKind.FILE) {
                val expectedHash = canonicalCloudFolderContentHash(tombstone.lastKnownContentHash)
                    ?: throw IOException("Refusing to delete unverified offline file: ${tombstone.relativePath}")
                val (actualHash, size) = target.inputStream().use { stream ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var count = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        digest.update(buffer, 0, read)
                        count += read
                    }
                    "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) } to count
                }
                if (actualHash != expectedHash || size != tombstone.lastKnownSizeBytes) {
                    throw IOException("Offline file changed; tombstone preserved: ${tombstone.relativePath}")
                }
            } else {
                if (!target.isDirectory) throw IOException("Offline path type changed: ${tombstone.relativePath}")
                if (target.listFiles().orEmpty().isNotEmpty()) {
                    throw IOException("Offline directory is not empty; tombstone preserved: ${tombstone.relativePath}")
                }
            }
            ensureAccountStillActive()
            if (!target.delete()) throw IOException("Unable to apply offline tombstone: ${tombstone.relativePath}")
            if (!isCloudFolderMetadataSidecarPath(tombstone.relativePath)) {
                deletedAny = true
            }
        }
        return deletedAny
    }

    private fun ensureDirectory(root: DocumentFile, relativePath: String): DocumentFile {
        if (relativePath.isBlank()) return root
        var current = root
        for (segment in relativePath.split('/')) {
            val normalized = normalizeCloudFolderRelativePath(segment)
                ?: throw IOException("Unsafe local folder path")
            val child = current.findFile(normalized)
            current = when {
                child == null -> current.createDirectory(normalized)
                    ?: throw IOException("Unable to create local directory: $normalized")
                child.isDirectory -> child
                else -> throw IOException("Local path is a file: $relativePath")
            }
        }
        return current
    }

    private suspend fun writeRemoteFileAtomically(
        accessToken: String,
        parent: DocumentFile,
        node: CloudFolderNode,
        objectId: String?,
        expectedLocalNode: CloudFolderNode? = null,
        allowAlreadyMaterialized: Boolean = false,
    ): Boolean {
        ensureAccountStillActive()
        val name = node.relativePath.substringAfterLast('/')
        val existing = parent.findFile(name)
        if (existing?.isDirectory == true) throw IOException("Local path is a directory: ${node.relativePath}")
        if (allowAlreadyMaterialized && existing?.isFile == true &&
            cloudFolderDocumentMatches(existing, node)
        ) {
            ensureAccountStillActive()
            // Verified local bytes; counted in the materialize_end summary
            // rather than logged per file.
            return false
        }
        // Verified local bytes take precedence; only an actual download
        // requires the immutable object pointer.
        val resolvedObjectId = objectId?.trim()?.takeIf(String::isNotBlank)
            ?: throw cloudFolderTransferFailure(
                error = IOException("Cloud object is missing for ${node.relativePath}"),
                stage = "drive_download",
                category = "missing_content_object",
            )
        cloudFolderLogD(
            "event=materialize_fs_download_start root=${cloudFolderSafeId(node.rootId)} " +
                "node=${cloudFolderSafeId(node.nodeId)} bytes=${node.sizeBytes} " +
                "objectIdMissing=${objectId == null}",
        )
        val tempName = ".cloud-folder-${stableTempSuffix(node.nodeId)}.part"
        val temp = parent.createFile(node.mimeType ?: "application/octet-stream", tempName)
            ?: throw IOException("Unable to create temporary SAF file: ${node.relativePath}")
        try {
            val output = applicationContext.contentResolver.openOutputStream(temp.uri, "wt")
                ?: throw IOException("Unable to open temporary SAF output: ${node.relativePath}")
            val digest = MessageDigest.getInstance("SHA-256")
            val hashingOutput = CloudFolderDigestCountingOutputStream(output, digest)
            try {
                driveRepository.downloadCloudFolderFileTo(
                    accessToken = accessToken,
                    fileId = resolvedObjectId,
                    output = hashingOutput,
                    expectedRootId = node.rootId,
                    expectedNodeId = node.nodeId,
                    expectedRevision = node.revision,
                    expectedContentHash = canonicalCloudFolderContentHash(node.contentHash)
                        ?: throw IOException("Cloud file has no authenticated hash: ${node.relativePath}"),
                    expectedSizeBytes = node.sizeBytes,
                    operationId = activeOperationId,
                    correlationId = activeCorrelationId,
                )
            } finally {
                hashingOutput.close()
            }
            val actualHash = "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            val expectedHash = canonicalCloudFolderContentHash(node.contentHash)
            if (expectedHash != null && expectedHash != actualHash) {
                throw IOException("Downloaded hash mismatch: ${node.relativePath}")
            }
            if (hashingOutput.count != node.sizeBytes) {
                throw IOException("Downloaded size mismatch: ${node.relativePath}")
            }
            val latestExisting = verifySafTargetUnchanged(
                parent = parent,
                name = name,
                expectedLocalNode = expectedLocalNode,
                targetNode = node,
                allowAlreadyMaterialized = allowAlreadyMaterialized,
                relativePath = node.relativePath,
            )
            ensureAccountStillActive()
            commitSafTempFile(parent, temp, latestExisting, name, node.nodeId)
            return true
        } catch (error: kotlinx.coroutines.CancellationException) {
            temp.delete()
            throw error
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    /** Hash an existing SAF target only for resumable/pull materialization. */
    private suspend fun cloudFolderDocumentMatches(
        document: DocumentFile,
        node: CloudFolderNode,
    ): Boolean {
        val expectedHash = canonicalCloudFolderContentHash(node.contentHash)
        if (!isCloudFolderSha256(expectedHash) || node.sizeBytes < 0L) return false
        return try {
            val (actualHash, actualSize) = hashDocument(document)
            actualHash == expectedHash && actualSize == node.sizeBytes
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    /** Re-read a target after the network transfer and before replacement. */
    private suspend fun verifySafTargetUnchanged(
        parent: DocumentFile,
        name: String,
        expectedLocalNode: CloudFolderNode?,
        targetNode: CloudFolderNode,
        allowAlreadyMaterialized: Boolean,
        relativePath: String,
    ): DocumentFile? {
        ensureAccountStillActive()
        val current = parent.findFile(name)
        if (allowAlreadyMaterialized && current?.isFile == true) {
            val targetHash = canonicalCloudFolderContentHash(targetNode.contentHash)
            if (targetHash != null) {
                val (actualHash, size) = hashDocument(current)
                if (actualHash == targetHash && size == targetNode.sizeBytes) return current
            }
        }
        if (expectedLocalNode == null) {
            if (current != null) {
                throw IOException("Local file appeared during download; preserving it: $relativePath")
            }
            return null
        }
        if (current == null || !current.isFile || expectedLocalNode.kind != CloudFolderNodeKind.FILE) {
            throw IOException("Local target changed during download; preserving it: $relativePath")
        }
        val expectedHash = canonicalCloudFolderContentHash(expectedLocalNode.contentHash)
            ?: throw IOException("Cannot verify local target before replacement: $relativePath")
        val (actualHash, size) = hashDocument(current)
        if (actualHash != expectedHash || size != expectedLocalNode.sizeBytes) {
            throw IOException("Local file changed during download; preserving it: $relativePath")
        }
        return current
    }

    private fun commitSafTempFile(
        parent: DocumentFile,
        temp: DocumentFile,
        existing: DocumentFile?,
        name: String,
        nodeId: String,
    ) {
        val backupName = ".cloud-folder-${stableTempSuffix(nodeId)}.bak"
        var backup: DocumentFile? = null
        if (existing != null) {
            if (!existing.renameTo(backupName)) {
                // The existing file is intentionally preserved when the
                // provider cannot provide a safe replacement path.
                throw IOException("Unable to stage existing SAF file: $name")
            }
            backup = parent.findFile(backupName)
        }
        try {
            if (!temp.renameTo(name)) throw IOException("Unable to commit SAF file: $name")
            backup?.delete()
        } catch (error: Exception) {
            temp.delete()
            backup?.renameTo(name)
            throw error
        }
    }

    private fun stableTempSuffix(nodeId: String): String =
        com.aryan.reader.data.cloudFolderDriveSegment(nodeId).take(16)

    private fun parentPath(path: String): String = path.substringBeforeLast('/', "")

    private fun pathDepth(path: String): Int = path.count { it == '/' }

    /**
     * Immutable manifests provide a lightweight CAS check: if another device
     * published while uploads were in flight, abandon this plan and let the
     * retry read/replan against the new revision.
     */
    private suspend fun assertRemoteSnapshotUnchanged(
        accessToken: String,
        rootId: String,
        initial: CloudFolderManifestReadResult,
    ) {
        ensureAccountStillActive()
        val latest = readRemoteManifest(accessToken, rootId)
        val unchanged = when {
            initial is CloudFolderManifestReadResult.NotFound ->
                latest is CloudFolderManifestReadResult.NotFound
            initial is CloudFolderManifestReadResult.Found &&
                latest is CloudFolderManifestReadResult.Found ->
                initial.driveFileId == latest.driveFileId &&
                    initial.manifest.revision == latest.manifest.revision
            else -> false
        }
        if (!unchanged) {
            throw IOException("Remote cloud-folder manifest changed; replan before publishing")
        }
    }

    private fun sha256CloudFolderManifest(manifest: CloudFolderManifest): String {
        val bytes = CloudFolderManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8)
        return "sha256:" + MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun ensureAccountStillActive() {
        val currentAccountId = AuthRepository(applicationContext).getSignedInUser()?.uid?.trim().orEmpty()
        if (currentAccountId != repository.accountId) {
            throw IOException("Cloud-folder account changed during transfer")
        }
    }

    private fun retryDelayMs(attempts: Int): Long =
        (1L shl attempts.coerceIn(0, 8)) * 1_000L

    private class DigestCountingInputStream(
        input: InputStream,
        private val digest: MessageDigest,
    ) : FilterInputStream(input) {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                digest.update(value.toByte())
                count++
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) {
                digest.update(buffer, offset, read)
                count += read
            }
            return read
        }
    }

    private enum class Direction {
        PUSH,
        PULL,
        SYNC,
        GC,
        DELETE,
    }

    companion object {
        const val KEY_ACCOUNT_ID = "cloud_folder_account_id"
        const val KEY_ROOT_ID = "cloud_folder_root_id"
        const val KEY_DIRECTION = "cloud_folder_direction"
        const val KEY_METADATA_ONLY = "cloud_folder_metadata_only"
        const val WORK_NAME = CLOUD_FOLDER_ROOT_WORK_PREFIX
        const val MAX_OUTBOX_ATTEMPTS = 8
        private val GLOBAL_MUTEX = Mutex()

        /**
         * Remove a complete app-managed offline copy while sharing the same
         * mutex as normal cloud-folder work. The caller changes the durable
         * binding only after this returns, so a failure leaves KEEP_OFFLINE
         * as the truthful state and the bytes available for retry.
         */
        internal suspend fun clearOfflineMaterialization(
            context: Context,
            rootId: String,
        ) {
            GLOBAL_MUTEX.withLock {
                withContext(Dispatchers.IO) {
                    removeCloudFolderAppStorageTree(context.applicationContext.filesDir, rootId)
                }
            }
        }

        /**
         * Enqueue a destructive account-scoped "delete this folder from Drive"
         * request. A tombstone is published so other devices observe the
         * deletion, then all local state is removed on this device.
         */
        fun enqueueDeleteFolder(
            context: Context,
            accountId: String,
            rootId: String,
        ) {
            val normalizedAccountId = accountId.trim()
            val normalizedRootId = rootId.trim()
            require(normalizedAccountId.isNotBlank()) { "Cloud-folder work requires an account ID" }
            require(normalizedRootId.isNotBlank()) { "Cloud-folder delete requires a root ID" }
            val data = Data.Builder()
                .putString(KEY_ACCOUNT_ID, normalizedAccountId)
                .putString(KEY_ROOT_ID, normalizedRootId)
                .putString(KEY_DIRECTION, Direction.DELETE.name)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudFolderSyncWorker>()
                .setInputData(data)
                .addTag(accountTag(normalizedAccountId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(normalizedAccountId, normalizedRootId, Direction.DELETE),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueue(
            context: Context,
            accountId: String,
            rootId: String? = null,
            direction: CloudFolderSyncDirection = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
            replace: Boolean = false,
            metadataOnly: Boolean = false,
        ) {
            val normalizedAccountId = accountId.trim()
            require(normalizedAccountId.isNotBlank()) { "Cloud-folder work requires an account ID" }
            val normalizedRootId = rootId?.trim().orEmpty()
            val workerDirection = direction.toWorkerDirection()
            // Metadata wakes must not be swallowed by an already-enqueued
            // full scan (or accidentally cause a full scan to inherit a
            // metadata-only input). Keep the identities separate while the
            // account/root scope remains the same.
            val workName = workName(normalizedAccountId, normalizedRootId, workerDirection, metadataOnly)
            val data = Data.Builder()
                .putString(KEY_ACCOUNT_ID, normalizedAccountId)
                .putString(KEY_ROOT_ID, normalizedRootId)
                .putString(KEY_DIRECTION, workerDirection.name)
                .putBoolean(KEY_METADATA_ONLY, metadataOnly)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudFolderSyncWorker>()
                .setInputData(data)
                .addTag(accountTag(normalizedAccountId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS,
                )
                .build()
            val workManager = WorkManager.getInstance(context.applicationContext)
            val policy = when {
                metadataOnly && !replace -> {
                    metadataWorkPolicyForExisting(
                        workManager = workManager,
                        workName = workName,
                        rootId = normalizedRootId,
                    )
                }
                workerDirection == Direction.PULL && !replace -> {
                    // A foreground head event must not cancel an active
                    // transfer, but it must be able to revive a request
                    // stranded in exponential backoff.  The worker rereads
                    // the authoritative Firestore/Drive head, so replacing a
                    // retrying request is safe and idempotent.
                    pullWorkPolicyForExisting(
                        workManager = workManager,
                        workName = workName,
                        rootId = normalizedRootId,
                    )
                }
                replace -> ExistingWorkPolicy.REPLACE
                else -> ExistingWorkPolicy.KEEP
            }
            if (metadataOnly) {
                cloudFolderLogD(
                    "event=metadata_worker_policy root=${cloudFolderSafeId(normalizedRootId)} " +
                        "result=selected policy=${policy.name} forcedReplace=$replace",
                )
            }
            workManager.enqueueUniqueWork(
                workName,
                policy,
                request,
            )
        }

        private fun metadataWorkPolicyForExisting(
            workManager: WorkManager,
            workName: String,
            rootId: String,
        ): ExistingWorkPolicy {
            val infos = runCatching {
                // Metadata wakes are issued from an IO coroutine after a
                // sidecar commit. Querying here lets us distinguish an active
                // first attempt from a retrying request in backoff without
                // putting an unbounded wait on the UI thread.
                workManager.getWorkInfosForUniqueWork(workName).get()
            }.getOrElse { error ->
                // If WorkManager's state database cannot be read, ensuring a
                // durable wake is safer than KEEP, which could swallow it.
                cloudFolderLogError(
                    event = "metadata_worker_state",
                    error = error,
                    details = "root=${cloudFolderSafeId(rootId)} result=unavailable policy=replace",
                )
                return ExistingWorkPolicy.REPLACE
            }
            val states = infos.map { info ->
                CloudFolderMetadataWorkState(
                    state = info.state,
                    runAttemptCount = info.runAttemptCount,
                )
            }
            val policy = cloudFolderMetadataWorkPolicy(states)
            cloudFolderLogD(
                "event=metadata_worker_state root=${cloudFolderSafeId(rootId)} " +
                    "result=read requests=${states.size} " +
                    "active=${states.count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }} " +
                    "retrying=${states.count { it.runAttemptCount > 0 }} policy=${policy.name}",
            )
            return policy
        }

        private fun pullWorkPolicyForExisting(
            workManager: WorkManager,
            workName: String,
            rootId: String,
        ): ExistingWorkPolicy {
            val infos = runCatching {
                workManager.getWorkInfosForUniqueWork(workName).get()
            }.getOrElse { error ->
                // KEEP is safer when WorkManager cannot answer: an active
                // transfer must never be cancelled because its state is
                // temporarily unavailable. The next head/foreground/startup
                // trigger can retry the enqueue decision.
                cloudFolderLogError(
                    event = "head_pull_work_state",
                    error = error,
                    details = "root=${cloudFolderSafeId(rootId)} result=unavailable policy=keep",
                )
                return ExistingWorkPolicy.KEEP
            }
            val unfinished = infos.filter { info ->
                info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.BLOCKED
            }
            val policy = when {
                unfinished.any { it.state == WorkInfo.State.RUNNING } -> ExistingWorkPolicy.KEEP
                unfinished.any { it.runAttemptCount > 0 } -> ExistingWorkPolicy.REPLACE
                unfinished.isNotEmpty() -> ExistingWorkPolicy.KEEP
                else -> ExistingWorkPolicy.KEEP
            }
            cloudFolderLogD(
                "event=head_pull_work_state root=${cloudFolderSafeId(rootId)} " +
                    "requests=${infos.size} active=${unfinished.size} " +
                    "retrying=${unfinished.count { it.runAttemptCount > 0 }} policy=${policy.name}",
            )
            return policy
        }

        fun enqueuePull(
            context: Context,
            accountId: String,
            rootId: String? = null,
            replace: Boolean = false,
        ) = enqueue(context, accountId, rootId, CloudFolderSyncDirection.CLOUD_TO_LOCAL, replace)

        /** Schedule account-scoped immutable-object maintenance. */
        fun enqueueGarbageCollection(
            context: Context,
            accountId: String,
            replace: Boolean = false,
        ) {
            val normalizedAccountId = accountId.trim()
            require(normalizedAccountId.isNotBlank()) { "Cloud-folder work requires an account ID" }
            val data = Data.Builder()
                .putString(KEY_ACCOUNT_ID, normalizedAccountId)
                .putString(KEY_ROOT_ID, "")
                .putString(KEY_DIRECTION, Direction.GC.name)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudFolderSyncWorker>()
                .setInputData(data)
                .addTag(accountTag(normalizedAccountId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(normalizedAccountId, "", Direction.GC),
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelForAccount(context: Context, accountId: String) {
            val normalizedAccountId = accountId.trim()
            if (normalizedAccountId.isBlank()) return
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelAllWorkByTag(accountTag(normalizedAccountId))
            // Cancel work scheduled by the pre-account-scoped implementation;
            // those requests intentionally self-abort when they lack an ID.
            workManager.cancelUniqueWork(WORK_NAME)
        }

        private fun workName(
            accountId: String,
            rootId: String,
            direction: Direction,
            metadataOnly: Boolean = false,
        ): String {
            // Do not use String.hashCode here: collisions would cause two
            // accounts' unique work to replace one another. The UID itself
            // is intentionally not placed in WorkManager names or tags.
            val accountSuffix = accountIdDigest(accountId)
            val metadataSuffix = if (metadataOnly) ":metadata" else ""
            return if (rootId.isBlank()) {
                "$WORK_NAME:$accountSuffix:${direction.name.lowercase()}$metadataSuffix"
            } else {
                "$WORK_NAME:$accountSuffix:$rootId:${direction.name.lowercase()}$metadataSuffix"
            }
        }

        private fun accountTag(accountId: String): String =
            "$WORK_NAME:account:${accountIdDigest(accountId)}"

        private fun accountIdDigest(accountId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(accountId.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(32)

        private fun CloudFolderSyncDirection.toWorkerDirection(): Direction = when (this) {
            CloudFolderSyncDirection.LOCAL_TO_CLOUD -> Direction.PUSH
            CloudFolderSyncDirection.CLOUD_TO_LOCAL -> Direction.PULL
            CloudFolderSyncDirection.NONE -> Direction.SYNC
        }
    }
}

/** A manifest-only view of one node used for pull diagnostics. */
private data class CloudFolderPullNodeState(
    val nodeId: String,
    val relativePath: String,
    val kind: CloudFolderNodeKind,
    val isActive: Boolean,
    val contentHash: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val fileModifiedAt: Long,
)

private data class CloudFolderPullDeltaCounts(
    val metadataAdded: Int = 0,
    val metadataChanged: Int = 0,
    val metadataDeleted: Int = 0,
    val contentAdded: Int = 0,
    val contentChanged: Int = 0,
    val contentDeleted: Int = 0,
) {
    val classification: String
        get() {
            val hasMetadataChanges = metadataAdded + metadataChanged + metadataDeleted > 0
            val hasContentChanges = contentAdded + contentChanged + contentDeleted > 0
            return when {
                !hasMetadataChanges && !hasContentChanges -> "no_change"
                hasMetadataChanges && hasContentChanges -> "mixed"
                hasMetadataChanges -> "sidecar_only"
                else -> "content_only"
            }
        }
}

/**
 * A root can be pulled automatically only after the device has both made a
 * binding decision and included that root in its account-scoped selection.
 * Keeping this predicate explicit prevents discovery from accidentally
 * materializing a dismissed/unconfigured incoming root.
 */
internal fun shouldPullBoundCloudFolderRoot(
    isDeleted: Boolean,
    isIncluded: Boolean,
    hasBinding: Boolean,
): Boolean = !isDeleted && isIncluded && hasBinding

/**
 * A PUSH that observes cloud-to-local operations must hand off to PULL. The
 * worker's caller additionally owns the unique-work key, so this predicate is
 * deliberately limited to eligibility and never decides the WorkManager
 * replacement policy.
 */
internal fun shouldQueueCloudFolderPullAfterRemoteChange(
    hasCloudToLocalOperations: Boolean,
    isSelected: Boolean,
    hasBinding: Boolean,
    isSignedIn: Boolean,
    syncEnabled: Boolean,
): Boolean = hasCloudToLocalOperations && isSelected && hasBinding && isSignedIn && syncEnabled

private fun classifyCloudFolderPullDelta(
    local: CloudFolderManifest?,
    remote: CloudFolderManifest,
): CloudFolderPullDeltaCounts {
    val localStates = local?.pullNodeStates().orEmpty()
    val remoteStates = remote.pullNodeStates()
    val nodeIds = localStates.keys + remoteStates.keys
    var metadataAdded = 0
    var metadataChanged = 0
    var metadataDeleted = 0
    var contentAdded = 0
    var contentChanged = 0
    var contentDeleted = 0

    fun isMetadata(state: CloudFolderPullNodeState): Boolean =
        isCloudFolderMetadataSidecarPath(state.relativePath)

    fun add(state: CloudFolderPullNodeState, changed: String) {
        val metadata = isMetadata(state)
        when (changed) {
            "added" -> if (metadata) metadataAdded++ else contentAdded++
            "changed" -> if (metadata) metadataChanged++ else contentChanged++
            "deleted" -> if (metadata) metadataDeleted++ else contentDeleted++
        }
    }

    for (nodeId in nodeIds) {
        val before = localStates[nodeId]
        val after = remoteStates[nodeId]
        when {
            before == null && after != null -> {
                // A remote tombstone is a deletion even when the local
                // device has never observed the corresponding live node.
                add(after, if (after.isActive) "added" else "deleted")
            }
            before != null && after == null -> {
                // A remote tombstone may have expired after the local base
                // retained it. Garbage collection is not a live-node change.
                if (before.isActive) add(before, "deleted")
            }
            before != null && after != null && before != after -> {
                when {
                    before.isActive && !after.isActive -> add(after, "deleted")
                    !before.isActive && after.isActive -> add(after, "added")
                    else -> add(after, "changed")
                }
            }
        }
    }
    return CloudFolderPullDeltaCounts(
        metadataAdded = metadataAdded,
        metadataChanged = metadataChanged,
        metadataDeleted = metadataDeleted,
        contentAdded = contentAdded,
        contentChanged = contentChanged,
        contentDeleted = contentDeleted,
    )
}

private fun CloudFolderManifest.pullNodeStates(): Map<String, CloudFolderPullNodeState> {
    val states = activeNodes().associate { node ->
        node.nodeId to CloudFolderPullNodeState(
            nodeId = node.nodeId,
            relativePath = node.relativePath,
            kind = node.kind,
            isActive = true,
            contentHash = canonicalCloudFolderContentHash(node.contentHash),
            sizeBytes = node.sizeBytes,
            mimeType = node.mimeType,
            fileModifiedAt = node.fileModifiedAt,
        )
    }.toMutableMap()
    tombstones
        .filter { it.rootId == rootId }
        .forEach { tombstone ->
            // An active node is authoritative if malformed/legacy data ever
            // contains both records for the same logical ID.
            if (tombstone.nodeId !in states) {
                states[tombstone.nodeId] = CloudFolderPullNodeState(
                    nodeId = tombstone.nodeId,
                    relativePath = tombstone.relativePath,
                    kind = tombstone.kind,
                    isActive = false,
                    contentHash = canonicalCloudFolderContentHash(tombstone.lastKnownContentHash),
                    sizeBytes = tombstone.lastKnownSizeBytes,
                    mimeType = null,
                    fileModifiedAt = 0L,
                )
            }
        }
    return states
}

private fun selectedCloudFolderPullPath(
    binding: com.aryan.reader.shared.CloudFolderDeviceBinding?,
    classification: String,
    hasPendingMaterialization: Boolean,
): String {
    if (binding == null) return "incoming_discovery_manifest_only"
    if (binding.materializationMode == CloudFolderMaterializationMode.CLOUD_ONLY) {
        return "cloud_only_manifest_only"
    }
    val prefix = when (binding.materializationMode) {
        CloudFolderMaterializationMode.KEEP_OFFLINE -> "app_storage"
        CloudFolderMaterializationMode.LOCAL_MIRROR -> "saf_mirror"
        CloudFolderMaterializationMode.CLOUD_ONLY -> "cloud_only"
    }
    if (hasPendingMaterialization) return "${prefix}_resume"
    return when (classification) {
        "content_only", "mixed" -> "${prefix}_full_materialization"
        "sidecar_only" -> "${prefix}_metadata_only"
        else -> "${prefix}_verify"
    }
}

/**
 * Cloud-folder work shares the main cloud-sync switch.  Keep this check in
 * the worker as a second line of defence because WorkManager can start a
 * request after the setting has been changed (or after cancellation races
 * with worker startup).
 */
internal fun isCloudFolderSyncEnabled(context: Context): Boolean =
    context.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        .getBoolean(KEY_SYNC_ENABLED, false)

/** Compare only logical root metadata; revision/stats are commit bookkeeping. */
private fun cloudFolderRootsEquivalentForPublish(
    first: CloudFolderRoot,
    second: CloudFolderRoot,
): Boolean = first.rootId == second.rootId &&
    first.name.trim() == second.name.trim() &&
    first.isDeleted == second.isDeleted &&
    first.createdAt == second.createdAt &&
    first.createdByDeviceId == second.createdByDeviceId

/**
 * Resolve an app-private offline root without allowing a remote root ID to
 * become a path component. Existing generated IDs remain in the same
 * directory layout; malformed remote IDs fail before any filesystem access.
 */
internal fun cloudFolderAppRootDirectory(filesDir: File, rootId: String): File {
    val normalizedRootId = rootId.trim()
    require(
        normalizedRootId.isNotBlank() &&
            normalizedRootId != "." &&
            normalizedRootId != ".." &&
            normalizedRootId.none { character ->
                character == '/' || character == '\\' || character == '\u0000'
            },
    ) { "Unsafe cloud-folder root ID" }

    val base = File(filesDir, "cloud-folder-sync").canonicalFile
    val target = File(base, normalizedRootId).canonicalFile
    val prefix = base.path + File.separator
    require(target.path.startsWith(prefix)) { "Cloud-folder root escapes app storage" }
    return target
}

/**
 * The single canonical folder-URI spelling for an app-managed offline root.
 *
 * File.toURI() emits "file:/..." while Uri.fromFile emits "file:///..."; the
 * folder list, per-file scan URIs, and any WorkManager target must agree so
 * URI-keyed filtering and Room writes resolve consistently.
 */
internal fun cloudFolderAppStorageFolderUriString(root: File): String =
    android.net.Uri.fromFile(root).toString()

/**
 * Delete one app-managed offline tree. Idempotent when the tree is already
 * gone; throws when a path stands in the way or removal fails. Callers must
 * already hold the cloud-folder work mutex (or run on a single thread).
 */
internal fun removeCloudFolderAppStorageTree(filesDir: File, rootId: String) {
    val root = cloudFolderAppRootDirectory(filesDir, rootId)
    if (!root.exists()) return
    if (!root.isDirectory) {
        throw IOException("Offline materialization is not a directory")
    }
    if (!root.deleteRecursively() || root.exists()) {
        throw IOException("Unable to remove offline materialization")
    }
}

/**
 * Verify an app-private materialized file before re-downloading it. A size
 * check alone is insufficient because a local file can be replaced in place;
 * only the authenticated SHA-256 and exact byte count permit a safe skip.
 */
internal suspend fun cloudFolderAppFileMatches(
    target: File,
    expectedHash: String?,
    expectedSizeBytes: Long,
): Boolean = withContext(Dispatchers.IO) {
    val canonicalHash = canonicalCloudFolderContentHash(expectedHash)
    if (!isCloudFolderSha256(canonicalHash) || expectedSizeBytes < 0L || !target.isFile) {
        return@withContext false
    }
    if (target.length() != expectedSizeBytes) return@withContext false
    return@withContext try {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        target.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                count += read
            }
        }
        count == expectedSizeBytes &&
            "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) } == canonicalHash
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

/**
 * Required sidecar kinds for a metadata-only wake, per book.
 *
 * Kinds outside the known set are dropped; an empty/blank row still demands
 * METADATA so a legacy wake can never skip the durable sidecar entirely.
 */
internal fun requiredMetadataKindsByBook(
    rows: List<CloudFolderMetadataOutboxEntity>,
): Map<String, Set<String>> = rows.associate { row ->
    row.bookId to row.dirtyKinds.split(',')
        .map { kind -> kind.trim().uppercase() }
        .filter { kind ->
            kind == CloudFolderMetadataOutboxEntity.KIND_METADATA ||
                kind == CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS
        }
        .toSet()
        .ifEmpty { setOf(CloudFolderMetadataOutboxEntity.KIND_METADATA) }
}

/** The sidecar relative paths a metadata-only wake must hash. */
internal fun metadataWakeTargetPaths(requiredKindsByBook: Map<String, Set<String>>): Set<String> =
    requiredKindsByBook.entries.flatMap { (bookId, kinds) ->
        kinds.mapNotNull { kind ->
            when (kind) {
                CloudFolderMetadataOutboxEntity.KIND_METADATA ->
                    "$LOCAL_FOLDER_SYNC_DATA_DIR/${localFolderSyncMetadataFileName(bookId)}"
                CloudFolderMetadataOutboxEntity.KIND_ANNOTATIONS ->
                    "$LOCAL_FOLDER_SYNC_DATA_DIR/${localFolderSyncAnnotationFileName(bookId)}"
                else -> null
            }
        }
    }.mapTo(hashSetOf()) { cloudFolderPathKey(it) }

internal fun buildLocalManifest(
    base: CloudFolderManifest,
    scan: CloudFolderSafScanResult,
    now: Long,
    deviceId: String,
): CloudFolderManifest {
    val previousById = base.activeNodes().associateBy { it.nodeId }
    val scannedNodes = scan.nodes
    // Compare through the same logical-metadata stabilization as the node
    // mapping below, so the revision watermark and the emitted nodes can
    // never disagree about whether the snapshot changed.
    val stabilizedNodes = scannedNodes.map { node ->
        previousById[node.nodeId]?.let {
            stabilizedCloudFolderNodeMetadata(scanned = node, committed = it)
        } ?: node
    }
    val changed = stabilizedNodes.any { node ->
        val previous = previousById[node.nodeId]
        previous == null || !localNodesEquivalent(previous, node)
    } || previousById.keys.any { it !in scannedNodes.mapTo(hashSetOf(), CloudFolderNode::nodeId) }
    val nextRevision = if (changed) {
        if (base.revision == Long.MAX_VALUE) Long.MAX_VALUE else base.revision + 1L
    } else {
        base.revision
    }
    val nodes = stabilizedNodes.map { node ->
        val previous = previousById[node.nodeId]
        val same = previous != null && localNodesEquivalent(previous, node)
        // A stale mtime or re-guessed MIME type must not strip the only
        // pointer to the uploaded bytes: as long as the authenticated hash
        // and size are unchanged, the provider object ID stays valid and
        // publishing it keeps other devices able to download the content.
        val contentUnchanged = previous != null &&
            previous.sizeBytes == node.sizeBytes &&
            canonicalCloudFolderContentHash(previous.contentHash) ==
                canonicalCloudFolderContentHash(node.contentHash) &&
            canonicalCloudFolderContentHash(node.contentHash) != null
        node.copy(
            revision = if (same) previous!!.revision else nextRevision,
            modifiedAt = now,
            modifiedByDeviceId = deviceId,
            contentObjectId = if (same || contentUnchanged) previous?.contentObjectId else null,
        )
    }
    val scannedIds = nodes.mapTo(hashSetOf(), CloudFolderNode::nodeId)
    val newTombstones = previousById
        .filterKeys { it !in scannedIds }
        .map { (_, previous) ->
            CloudFolderTombstone(
                nodeId = previous.nodeId,
                rootId = base.rootId,
                relativePath = previous.relativePath,
                kind = previous.kind,
                deletedAt = now,
                deletedRevision = nextRevision,
                deletedByDeviceId = deviceId,
                lastKnownContentHash = previous.contentHash,
                lastKnownSizeBytes = previous.sizeBytes,
            )
        }
    val tombstones = (base.tombstones + newTombstones)
        .filterNot { it.nodeId in scannedIds }
        .distinctBy { it.nodeId }
    val stats = CloudFolderRootStats(
        fileCount = nodes.count { it.isFile },
        directoryCount = nodes.count { it.isDirectory },
        totalBytes = nodes.filter { it.isFile }.sumOf { it.sizeBytes.coerceAtLeast(0L) },
        scannedAt = now,
        scanComplete = true,
    )
    return CloudFolderManifest(
        root = base.root.copy(
            updatedAt = now,
            manifestRevision = nextRevision,
            stats = stats,
        ),
        revision = nextRevision,
        baseRevision = base.revision,
        generatedAt = now,
        generatedByDeviceId = deviceId,
        nodes = nodes,
        tombstones = tombstones,
    ).normalized()
}

/**
 * Local-snapshot equivalence for merge decisions. Sidecars are logical
 * records, not user documents: the shared planner deliberately ignores
 * their provider mtimes and MIME types (atomic temp-then-rename writes
 * always churn both), so a same-byte rewrite must not be promoted into a
 * local edit that every device would then re-publish in a loop.
 */
internal fun localNodesEquivalent(first: CloudFolderNode, second: CloudFolderNode): Boolean {
    if (first.rootId != second.rootId ||
        first.kind != second.kind ||
        first.relativePath != second.relativePath ||
        first.sizeBytes != second.sizeBytes ||
        canonicalCloudFolderContentHash(first.contentHash) !=
            canonicalCloudFolderContentHash(second.contentHash)
    ) {
        return false
    }
    if (isCloudFolderMetadataSidecarPath(first.relativePath)) return true
    return first.mimeType == second.mimeType &&
        first.fileModifiedAt == second.fileModifiedAt
}

/**
 * Raised by the direct-PULL no-overwrite guards when the local tree has
 * edits the pull cannot apply. The pull caller converts this into a SYNC
 * hand-off instead of a WorkManager retry loop.
 */
internal class CloudFolderPullUnsafeException(message: String) : IOException(message)
