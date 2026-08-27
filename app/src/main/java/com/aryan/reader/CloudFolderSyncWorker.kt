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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.data.CloudFolderManifestReadResult
import com.aryan.reader.data.CloudFolderManifestLeaseResult
import com.aryan.reader.data.legacyCloudFolderManifestHeadCandidate
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
import com.aryan.reader.shared.isCloudFolderSha256
import com.aryan.reader.shared.normalizeCloudFolderRelativePath
import com.aryan.reader.shared.planCloudFolderSync
import com.aryan.reader.shared.resolveCloudFolderSync
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CLOUD_FOLDER_ROOT_WORK_PREFIX = "CloudFolderSyncWorker"
private const val CLOUD_FOLDER_GC_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

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
            val requestedAccountId = inputData.getString(KEY_ACCOUNT_ID)?.trim().orEmpty()
            val currentAccountId = AuthRepository(applicationContext).getSignedInUser()?.uid?.trim().orEmpty()
            cloudFolderLogI(
                "event=worker_start direction=${direction.name} " +
                    "account=${cloudFolderSafeId(requestedAccountId)} " +
                    "root=${cloudFolderSafeId(requestedRootId)}",
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
            repository.resetRunningOutbox(now = System.currentTimeMillis())
            cloudFolderLogD("event=outbox_recovery result=reset root=${cloudFolderSafeId(requestedRootId)}")

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
                    runGarbageCollection(accessToken)
                } else if (requestedRootId.isNotBlank()) {
                    if (direction == Direction.PULL) {
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
                        syncRoot(accessToken, requestedRootId, direction)
                    }
                } else if (direction == Direction.PULL) {
                    discoverAndPull(accessToken)
                } else {
                    // A normal cloud sync also performs a metadata-only
                    // discovery pass. This makes device-2 roots visible to
                    // settings even when no local folder is indexed, while
                    // keeping byte materialization behind an explicit choice.
                    discoverAndPull(accessToken)
                    syncSelectedRoots(accessToken, direction)
                }
                // Wake an already-running ViewModel so a newly discovered
                // device-2 root is visible immediately, even when Settings
                // is not the current route. Persisted state remains the
                // source of truth across process death.
                CloudFolderSyncEvents.notifyStateChanged()
                cloudFolderLogI(
                    "event=worker_end result=success direction=${direction.name} " +
                        "root=${cloudFolderSafeId(requestedRootId)} " +
                        "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
                )
                Result.success()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestedRootId.isNotBlank()) {
                    markRootFailure(requestedRootId, error)
                }
                cloudFolderLogError(
                    event = "worker_end",
                    error = error,
                    details = "result=${if (cloudFolderFailureIsDeterministic(error)) "failure" else "retry"} " +
                        "direction=${direction.name} root=${cloudFolderSafeId(requestedRootId)} " +
                        "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
                )
                if (cloudFolderFailureIsDeterministic(error)) Result.failure() else Result.retry()
            }
        }
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

    private suspend fun discoverAndPull(accessToken: String) {
        val startedAt = System.currentTimeMillis()
        cloudFolderLogD("event=discovery_start account=${cloudFolderSafeId(repository.accountId)}")
        val refs = driveRepository.listCloudFolderManifestRefs(accessToken)
        cloudFolderLogD("event=discovery_refs count=${refs.size}")
        var failure: Throwable? = null
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
        failure?.let { throw it }
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
        val result = driveRepository.downloadCloudFolderManifest(accessToken, rootId)
        if (!BuildConfig.IS_PRO) {
            cloudFolderLogD(
                "event=manifest_read_end root=$safeRoot source=drive " +
                    "result=${if (result is CloudFolderManifestReadResult.Found) "found" else "not_found"} " +
                    "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
            )
            return result
        }
        var head = firestoreRepository.getCloudFolderManifestHead(repository.accountId, rootId)
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
        when (binding.materializationMode) {
            CloudFolderMaterializationMode.CLOUD_ONLY -> {
                // The user explicitly removed local materialization while a
                // previous transfer was pending. No local bytes are required
                // in this mode, so discard only the pending local target.
                repository.clearPendingMaterialization(rootId)
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
                if (!canResume) return false
            }
        }
        when (binding.materializationMode) {
            CloudFolderMaterializationMode.CLOUD_ONLY -> return false
            CloudFolderMaterializationMode.KEEP_OFFLINE ->
                materializeManifestToAppStorage(accessToken, pending)
            CloudFolderMaterializationMode.LOCAL_MIRROR -> {
                val localUri = binding.localUri?.takeIf { it.isNotBlank() } ?: return false
                materializeManifest(
                    accessToken = accessToken,
                    manifest = pending,
                    localRootUri = Uri.parse(localUri),
                    expectedBase = repository.getManifest(rootId),
                    allowAlreadyMaterialized = true,
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
        return true
    }

    private suspend fun pullRoot(accessToken: String, rootId: String) {
        val existingBinding = repository.getBinding(rootId)
        if (existingBinding != null) {
            if (!repository.isIncluded(rootId)) return
        }
        val remoteResult = readRemoteManifest(accessToken, rootId)
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
                        repository.savePendingMaterialization(manifest)
                        materializeManifestToAppStorage(accessToken, manifest)
                    }
                    CloudFolderMaterializationMode.LOCAL_MIRROR -> {
                        val localUri = binding.localUri?.takeIf { it.isNotBlank() } ?: return
                        verifyLocalMirrorIsPullSafe(rootId, manifest, Uri.parse(localUri))
                        repository.savePendingMaterialization(manifest)
                        materializeManifest(
                            accessToken = accessToken,
                            manifest = manifest,
                            localRootUri = Uri.parse(localUri),
                            expectedBase = repository.getManifest(rootId),
                            allowAlreadyMaterialized = true,
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
     * A direct PULL must not overwrite edits made after the last committed
     * local snapshot. The normal SYNC planner performs this check as part of
     * its merge; this guard gives explicit PULL the same protection.
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
            throw IOException("Local and remote folder changes conflict; pull skipped")
        }
        if (plan.operations.any { it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD }) {
            throw IOException("Local folder changed; sync before pulling remote changes")
        }
    }

    private suspend fun syncRoot(
        accessToken: String,
        rootId: String,
        direction: Direction,
    ) {
        val startedAt = System.currentTimeMillis()
        val safeRoot = cloudFolderSafeId(rootId)
        if (!repository.isIncluded(rootId)) {
            cloudFolderLogD("event=root_gate root=$safeRoot gate=selection result=excluded")
            return
        }
        val binding = repository.getBinding(rootId) ?: run {
            cloudFolderLogW("event=root_gate root=$safeRoot gate=binding result=missing")
            return
        }
        cloudFolderLogD(
            "event=sync_root_start root=$safeRoot direction=${direction.name} " +
                "mode=${binding.materializationMode.name}",
        )
        saveRootProgress(rootId, CloudFolderSyncPhase.SCANNING)
        if (binding.materializationMode == CloudFolderMaterializationMode.KEEP_OFFLINE) {
            // KEEP_OFFLINE has no SAF mirror to scan. A normal SYNC still
            // needs to pull newer cloud revisions; an explicit PUSH cannot
            // safely operate on this binding and is therefore a no-op.
            if (direction == Direction.SYNC) pullRoot(accessToken, rootId)
            saveRootProgress(
                rootId = rootId,
                phase = CloudFolderSyncPhase.SUCCEEDED,
                errorStatus = null,
            )
            cloudFolderLogD("event=sync_root_end root=$safeRoot result=offline_mode")
            return
        }
        val remoteResult = readRemoteManifest(accessToken, rootId)
        if (resumePendingMaterialization(accessToken, rootId, binding, remoteResult)) {
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
        val localUri = binding.localUri?.takeIf { it.isNotBlank() } ?: return
        val rootUri = Uri.parse(localUri)
        val scan = CloudFolderSafScanner.scan(
            context = applicationContext,
            rootUri = rootUri,
            rootId = rootId,
            deviceId = repository.deviceId,
        )
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
            "event=sync_plan root=$safeRoot operations=${plan.operations.size} " +
                "conflicts=${plan.conflicts.size} revision=${plan.mergedManifest.revision}",
        )
        if (plan.conflicts.isNotEmpty()) {
            val records = repository.reconcileConflicts(plan, now)
            val resolutions = records.associate { it.conflictId to it.resolution }
            plan = resolveCloudFolderSync(
                base = base,
                local = local,
                remote = remote,
                plan = plan,
                resolutions = resolutions,
                nowMillis = now,
                deviceId = repository.deviceId,
            )
            if (plan.conflicts.isNotEmpty()) {
                // Conflicts are durable user-action state, not a transient
                // network failure. Leave the last committed manifest
                // untouched until every decision is explicit.
                repository.reconcileConflicts(plan, now)
                val message = "Cloud-folder sync needs conflict resolution (${plan.conflicts.size} conflict(s))"
                repository.markBindingError(rootId, message)
                CloudFolderSyncEvents.notifyStateChanged()
                cloudFolderLogW(
                    "event=sync_root_end root=$safeRoot result=conflict " +
                        "conflicts=${plan.conflicts.size}",
                )
                return
            }
        } else {
            // Remove stale records after a later scan proves that the inputs
            // no longer conflict (for example after an external repair).
            repository.reconcileConflicts(plan, now)
        }

        // A PUSH request never silently folds a remote-only change into the
        // local inventory. The caller can enqueue PULL/SYNC to materialize it;
        // this prevents a stale local file from being treated as authoritative.
        if (direction == Direction.PUSH && plan.operations.any {
                it.direction == CloudFolderSyncDirection.CLOUD_TO_LOCAL
            }) {
            repository.markBindingError(rootId, "Remote folder changed; pull before pushing local changes")
            CloudFolderSyncEvents.notifyStateChanged()
            cloudFolderLogW("event=sync_root_end root=$safeRoot result=remote_changed")
            return
        }

        val localOperations = plan.operations.filter {
            it.direction == CloudFolderSyncDirection.LOCAL_TO_CLOUD
        }
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
        val published = plan.mergedManifest.copy(
            nodes = plan.mergedManifest.nodes.map { node ->
                node.copy(contentObjectId = uploadedObjectIds[node.nodeId] ?: node.contentObjectId)
            },
        ).withUpdatedRootStats(now)

        // The manifest is the commit record. It is deliberately uploaded last;
        // orphaned Drive objects are harmless and can be garbage-collected by a
        // later retention pass, while a premature manifest would expose bytes
        // that were not fully uploaded.
        val shouldMaterialize = direction == Direction.SYNC && plan.operations.any {
            it.direction == CloudFolderSyncDirection.CLOUD_TO_LOCAL
        }
        val shouldPublish = remoteMissing || localOperations.isNotEmpty() ||
            !cloudFolderRootsEquivalentForPublish(published.root, remote.root)
        val targetManifest = if (shouldPublish) published else remote
        if (shouldPublish) {
            publishManifestWithCas(
                accessToken = accessToken,
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
            // The remote commit may succeed before local writes do. Keep the
            // target separate from the committed local base so a killed or
            // failed materialization is resumed before the next scan.
            repository.savePendingMaterialization(targetManifest, now)
            materializeManifest(
                accessToken = accessToken,
                manifest = targetManifest,
                localRootUri = rootUri,
                expectedBase = base,
                allowAlreadyMaterialized = true,
            )
            ensureAccountStillActive()
            repository.saveManifest(targetManifest)
            repository.clearPendingMaterialization(rootId)
        }
        repository.clearConflicts(rootId)
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
            "event=sync_root_end root=$safeRoot result=success revision=${targetManifest.revision} " +
                "durationMs=${(System.currentTimeMillis() - startedAt).coerceAtLeast(0L)}",
        )
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
            "event=manifest_publish_start root=$safeRoot revision=${manifest.revision} " +
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
                    cloudFolderLogD("event=firestore_reserve root=$safeRoot result=acquired revision=${manifest.revision}")
                    reservation.lease
                }
                CloudFolderManifestLeaseResult.Conflict -> {
                    cloudFolderLogW("event=firestore_reserve root=$safeRoot result=conflict")
                    throw IOException("Cloud-folder manifest revision was claimed; replan before publishing")
                }
                CloudFolderManifestLeaseResult.Unsupported -> {
                    cloudFolderLogW("event=firestore_reserve root=$safeRoot result=unsupported")
                    throw IOException("Cloud-folder manifest CAS is unavailable")
                }
            }
        } else {
            null
        }
        var committed = false
        try {
            ensureAccountStillActive()
            val uploadedManifest = driveRepository.uploadCloudFolderManifest(accessToken, manifest)
                ?: throw IOException("Unable to publish cloud-folder manifest")
            cloudFolderLogD(
                "event=manifest_drive_upload root=$safeRoot result=success revision=${manifest.revision}",
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
                cloudFolderLogD("event=firestore_commit root=$safeRoot result=success revision=${manifest.revision}")
            }
            if (persistLocalManifest) {
                ensureAccountStillActive()
                repository.saveManifest(manifest)
            }
        } finally {
            if (lease != null && !committed) {
                runCatching { firestoreRepository.releaseCloudFolderManifest(lease) }
                    .onSuccess { released ->
                        cloudFolderLogW("event=firestore_release root=$safeRoot result=$released")
                    }
                    .onFailure { error ->
                        cloudFolderLogError(
                            event = "firestore_release",
                            error = error,
                            details = "root=$safeRoot",
                        )
                    }
            }
        }
        cloudFolderLogI(
            "event=manifest_publish_end root=$safeRoot result=success revision=${manifest.revision} " +
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
            "event=drive_upload_start root=$safeRoot node=$safeNode revision=${node.revision} " +
                "bytes=${node.sizeBytes} sizeBucket=${com.aryan.reader.data.cloudFolderDriveSizeBucket(node.sizeBytes)} " +
                "uploadMode=resumable attempt=$attempt",
        )
        ensureAccountStillActive()
        val input = applicationContext.contentResolver.openInputStream(source.uri)
            ?: throw IOException("Unable to open SAF stream for ${node.relativePath}")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashingInput = DigestCountingInputStream(input, digest)
        val uploaded = driveRepository.uploadCloudFolderFile(
            accessToken = accessToken,
            rootId = rootId,
            nodeId = node.nodeId,
            relativePath = node.relativePath,
            mimeType = node.mimeType,
            input = hashingInput,
            sizeBytes = node.sizeBytes,
            revision = node.revision,
            contentHash = node.contentHash,
            attempt = attempt,
        ) ?: throw IOException("Drive rejected ${node.relativePath}")
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
            "event=drive_upload_end root=$safeRoot node=$safeNode result=success " +
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
    ) {
        val root = DocumentFile.fromTreeUri(applicationContext, localRootUri)
            ?: throw IOException("Local SAF root is unavailable")
        if (!root.isDirectory) throw IOException("Local SAF root is not a directory")
        val directories = manifest.activeDirectories().sortedWith(
            compareBy<CloudFolderNode> { pathDepth(it.relativePath) }.thenBy { it.pathKey }
        )
        for (directory in directories) {
            currentCoroutineContext().ensureActive()
            ensureDirectory(root, directory.relativePath)
        }
        val files = manifest.activeFiles().sortedWith(
            compareBy<CloudFolderNode> { pathDepth(it.relativePath) }.thenBy { it.pathKey }
        )
        for (node in files) {
            currentCoroutineContext().ensureActive()
            val objectId = node.contentObjectId?.takeIf { it.isNotBlank() }
                ?: throw IOException("Cloud object is missing for ${node.relativePath}")
            val parent = ensureDirectory(root, parentPath(node.relativePath))
            val expectedLocalNode = expectedBase?.activeNodes()?.firstOrNull { baseNode ->
                baseNode.nodeId == node.nodeId && baseNode.relativePath == node.relativePath
            }
            writeRemoteFileAtomically(
                accessToken = accessToken,
                parent = parent,
                node = node,
                objectId = objectId,
                expectedLocalNode = expectedLocalNode,
                allowAlreadyMaterialized = allowAlreadyMaterialized,
            )
        }
        applySafTombstones(root, manifest.tombstones)
    }

    /**
     * Apply deletions only when the local item still matches the bytes
     * recorded in the tombstone. Unknown or changed content is preserved.
     */
    private suspend fun applySafTombstones(
        root: DocumentFile,
        tombstones: List<CloudFolderTombstone>,
    ) {
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
        }
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

            for ((index, directory) in directories.withIndex()) {
                currentCoroutineContext().ensureActive()
                val safeNode = cloudFolderSafeId(directory.nodeId)
                cloudFolderLogD(
                    "event=materialize_directory_start root=$safeRoot node=$safeNode " +
                        "ordinal=${index + 1} totalDirectories=${directories.size}",
                )
                val target = safeAppPath(root, directory.relativePath)
                if (target.exists() && !target.isDirectory) {
                    throw IOException("Offline path is a file: ${directory.relativePath}")
                }
                if (!target.exists() && !target.mkdirs()) {
                    throw IOException("Unable to create offline directory: ${directory.relativePath}")
                }
                cloudFolderLogD(
                    "event=materialize_directory_end root=$safeRoot node=$safeNode result=success",
                )
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
            for ((index, node) in files.withIndex()) {
                currentCoroutineContext().ensureActive()
                val ordinal = index + 1
                val safeNode = cloudFolderSafeId(node.nodeId)
                val objectId = node.contentObjectId?.takeIf { it.isNotBlank() }
                    ?: throw cloudFolderTransferFailure(
                        error = IOException("Cloud object is missing"),
                        stage = "manifest_validate",
                        category = "missing_content_object",
                    )
                cloudFolderLogD(
                    "event=materialize_file_start root=$safeRoot node=$safeNode " +
                        "ordinal=$ordinal totalFiles=${files.size} bytes=${node.sizeBytes} " +
                        "transfer=download",
                )
                val target = safeAppPath(root, node.relativePath)
                target.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create offline parent")
                }
                try {
                    writeAppFileAtomically(accessToken, target, node, objectId)
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
                    cloudFolderLogD(
                        "event=materialize_file_end root=$safeRoot node=$safeNode result=success " +
                            "ordinal=$ordinal totalFiles=${files.size} bytes=${node.sizeBytes}",
                    )
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
            applyAppTombstones(root, manifest.tombstones)
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
            )
            cloudFolderLogI(
                "event=materialize_end root=$safeRoot mode=app_storage result=success " +
                    "files=$completedFiles bytes=$completedBytes durationMs=" +
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

    private suspend fun writeAppFileAtomically(
        accessToken: String,
        target: File,
        node: CloudFolderNode,
        objectId: String,
    ) {
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
            temp.delete()
            backup.delete()
            cloudFolderLogD(
                "event=materialize_file_skip root=$safeRoot node=$safeNode " +
                    "reason=verified_existing bytes=${node.sizeBytes}",
            )
            return
        }
        cloudFolderLogD(
            "event=materialize_fs_temp_start root=$safeRoot node=$safeNode " +
                "tempExists=${temp.exists()} targetExists=${target.exists()}",
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
        cloudFolderLogD(
            "event=materialize_fs_temp_end root=$safeRoot node=$safeNode result=success",
        )
        var stage = "drive_download"
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashingOutput = CloudFolderDigestCountingOutputStream(output, digest)
            try {
                driveRepository.downloadCloudFolderFileTo(
                    accessToken = accessToken,
                    fileId = objectId,
                    output = hashingOutput,
                    expectedRootId = node.rootId,
                    expectedNodeId = node.nodeId,
                    expectedRevision = node.revision,
                    expectedContentHash = canonicalCloudFolderContentHash(node.contentHash)
                        ?: throw IOException("Cloud file has no authenticated hash: ${node.relativePath}"),
                    expectedSizeBytes = node.sizeBytes,
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
                cloudFolderLogD(
                    "event=materialize_fs_backup_start root=$safeRoot node=$safeNode",
                )
                if (!target.renameTo(backup)) throw IOException("Unable to stage offline file")
                stagedExisting = true
                cloudFolderLogD(
                    "event=materialize_fs_backup_end root=$safeRoot node=$safeNode result=success",
                )
            }
            try {
                ensureAccountStillActive()
                stage = "temp_commit"
                cloudFolderLogD(
                    "event=materialize_fs_commit_start root=$safeRoot node=$safeNode",
                )
                if (!temp.renameTo(target)) throw IOException("Unable to commit offline file")
                backup.delete()
                cloudFolderLogD(
                    "event=materialize_fs_commit_end root=$safeRoot node=$safeNode result=success",
                )
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
    }

    private suspend fun applyAppTombstones(root: File, tombstones: List<CloudFolderTombstone>) {
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
        }
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
        objectId: String,
        expectedLocalNode: CloudFolderNode? = null,
        allowAlreadyMaterialized: Boolean = false,
    ) {
        ensureAccountStillActive()
        val name = node.relativePath.substringAfterLast('/')
        val existing = parent.findFile(name)
        if (existing?.isDirectory == true) throw IOException("Local path is a directory: ${node.relativePath}")
        if (allowAlreadyMaterialized && existing?.isFile == true &&
            cloudFolderDocumentMatches(existing, node)
        ) {
            ensureAccountStillActive()
            cloudFolderLogD(
                "event=materialize_file_skip root=${cloudFolderSafeId(node.rootId)} " +
                    "node=${cloudFolderSafeId(node.nodeId)} reason=verified_existing bytes=${node.sizeBytes}",
            )
            return
        }
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
                    fileId = objectId,
                    output = hashingOutput,
                    expectedRootId = node.rootId,
                    expectedNodeId = node.nodeId,
                    expectedRevision = node.revision,
                    expectedContentHash = canonicalCloudFolderContentHash(node.contentHash)
                        ?: throw IOException("Cloud file has no authenticated hash: ${node.relativePath}"),
                    expectedSizeBytes = node.sizeBytes,
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
    }

    companion object {
        const val KEY_ACCOUNT_ID = "cloud_folder_account_id"
        const val KEY_ROOT_ID = "cloud_folder_root_id"
        const val KEY_DIRECTION = "cloud_folder_direction"
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
                    val root = cloudFolderAppRootDirectory(context.applicationContext.filesDir, rootId)
                    if (!root.exists()) return@withContext
                    if (!root.isDirectory) {
                        throw IOException("Offline materialization is not a directory")
                    }
                    if (!root.deleteRecursively() || root.exists()) {
                        throw IOException("Unable to remove offline materialization")
                    }
                }
            }
        }

        fun enqueue(
            context: Context,
            accountId: String,
            rootId: String? = null,
            direction: CloudFolderSyncDirection = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
            replace: Boolean = false,
        ) {
            val normalizedAccountId = accountId.trim()
            require(normalizedAccountId.isNotBlank()) { "Cloud-folder work requires an account ID" }
            val normalizedRootId = rootId?.trim().orEmpty()
            val workerDirection = direction.toWorkerDirection()
            val workName = workName(normalizedAccountId, normalizedRootId, workerDirection)
            val data = Data.Builder()
                .putString(KEY_ACCOUNT_ID, normalizedAccountId)
                .putString(KEY_ROOT_ID, normalizedRootId)
                .putString(KEY_DIRECTION, workerDirection.name)
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
                workName,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
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

        private fun workName(accountId: String, rootId: String, direction: Direction): String {
            // Do not use String.hashCode here: collisions would cause two
            // accounts' unique work to replace one another. The UID itself
            // is intentionally not placed in WorkManager names or tags.
            val accountSuffix = accountIdDigest(accountId)
            return if (rootId.isBlank()) {
                "$WORK_NAME:$accountSuffix:${direction.name.lowercase()}"
            } else {
                "$WORK_NAME:$accountSuffix:$rootId:${direction.name.lowercase()}"
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

internal fun buildLocalManifest(
    base: CloudFolderManifest,
    scan: CloudFolderSafScanResult,
    now: Long,
    deviceId: String,
): CloudFolderManifest {
    val previousById = base.activeNodes().associateBy { it.nodeId }
    val scannedNodes = scan.nodes
    val changed = scannedNodes.any { node ->
        val previous = previousById[node.nodeId]
        previous == null || !localNodesEquivalent(previous, node)
    } || previousById.keys.any { it !in scannedNodes.mapTo(hashSetOf(), CloudFolderNode::nodeId) }
    val nextRevision = if (changed) {
        if (base.revision == Long.MAX_VALUE) Long.MAX_VALUE else base.revision + 1L
    } else {
        base.revision
    }
    val nodes = scannedNodes.map { node ->
        val previous = previousById[node.nodeId]
        val same = previous != null && localNodesEquivalent(previous, node)
        node.copy(
            revision = if (same) previous!!.revision else nextRevision,
            modifiedAt = now,
            modifiedByDeviceId = deviceId,
            contentObjectId = if (same) previous!!.contentObjectId else null,
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

internal fun localNodesEquivalent(first: CloudFolderNode, second: CloudFolderNode): Boolean =
    first.rootId == second.rootId &&
        first.kind == second.kind &&
        first.relativePath == second.relativePath &&
        first.sizeBytes == second.sizeBytes &&
        canonicalCloudFolderContentHash(first.contentHash) ==
            canonicalCloudFolderContentHash(second.contentHash) &&
        first.mimeType == second.mimeType &&
        first.fileModifiedAt == second.fileModifiedAt
