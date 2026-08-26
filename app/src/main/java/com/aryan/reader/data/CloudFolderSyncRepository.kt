package com.aryan.reader.data

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.aryan.reader.CloudFolderSyncPrefs
import com.aryan.reader.shared.CloudFolderDeviceBinding
import com.aryan.reader.shared.CloudFolderConflict
import com.aryan.reader.shared.CloudFolderConflictRecord
import com.aryan.reader.shared.CloudFolderConflictResolution
import com.aryan.reader.shared.CloudFolderManifest
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderNode
import com.aryan.reader.shared.CloudFolderNodeKind
import com.aryan.reader.shared.CloudFolderPermissionState
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderRootStats
import com.aryan.reader.shared.CloudFolderSyncOperation
import com.aryan.reader.shared.CloudFolderSyncOperationKind
import com.aryan.reader.shared.CloudFolderSyncDirection
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.CloudFolderTombstone
import com.aryan.reader.shared.CloudFolderConflictUiItem
import com.aryan.reader.shared.cloudFolderRootId
import com.aryan.reader.shared.validationIssues
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json

private const val CLOUD_FOLDER_PREFS = "cloud_folder_sync"
private const val CLOUD_FOLDER_DEVICE_ID_KEY = "device_id_v1"

/**
 * The JSON codec is shared by Room-adjacent tests and the Drive gateway.  It
 * accepts newer manifests so a client can retain a downloaded record while
 * an app update is rolling out, but writes only the current schema.
 */
internal object CloudFolderManifestCodec {
    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(manifest: CloudFolderManifest): String =
        json.encodeToString(CloudFolderManifest.serializer(), manifest.normalized())

    fun decode(raw: String): CloudFolderManifest {
        val decoded = json.decodeFromString(CloudFolderManifest.serializer(), raw)
        val issues = decoded.validationIssues()
        require(issues.isEmpty()) {
            "Invalid cloud-folder manifest: ${issues.joinToString()}"
        }
        return decoded.normalized()
    }
}

internal fun CloudFolderRoot.toEntity(accountId: String): CloudFolderRootEntity = CloudFolderRootEntity(
    accountId = accountId,
    rootId = rootId,
    name = name,
    createdAt = createdAt,
    createdByDeviceId = createdByDeviceId,
    updatedAt = updatedAt,
    manifestRevision = manifestRevision,
    fileCount = stats.fileCount,
    directoryCount = stats.directoryCount,
    totalBytes = stats.totalBytes,
    scannedAt = stats.scannedAt,
    scanComplete = stats.scanComplete,
    isDeleted = isDeleted,
)

internal fun CloudFolderRootEntity.toModel(): CloudFolderRoot = CloudFolderRoot(
    rootId = rootId,
    name = name,
    createdAt = createdAt,
    createdByDeviceId = createdByDeviceId,
    updatedAt = updatedAt,
    manifestRevision = manifestRevision,
    stats = CloudFolderRootStats(
        fileCount = fileCount,
        directoryCount = directoryCount,
        totalBytes = totalBytes,
        scannedAt = scannedAt,
        scanComplete = scanComplete,
    ),
    isDeleted = isDeleted,
)

internal fun CloudFolderNode.toEntity(accountId: String): CloudFolderNodeEntity = CloudFolderNodeEntity(
    accountId = accountId,
    rootId = rootId,
    nodeId = nodeId,
    relativePath = relativePath,
    kind = kind.name,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    fileModifiedAt = fileModifiedAt,
    revision = revision,
    modifiedAt = modifiedAt,
    modifiedByDeviceId = modifiedByDeviceId,
    contentObjectId = contentObjectId,
)

internal fun CloudFolderNodeEntity.toModel(): CloudFolderNode? = runCatching {
    CloudFolderNode(
        nodeId = nodeId,
        rootId = rootId,
        relativePath = relativePath,
        kind = CloudFolderNodeKind.valueOf(kind),
        contentHash = contentHash,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        fileModifiedAt = fileModifiedAt,
        revision = revision,
        modifiedAt = modifiedAt,
        modifiedByDeviceId = modifiedByDeviceId,
        contentObjectId = contentObjectId,
    )
}.getOrNull()

internal fun CloudFolderTombstone.toEntity(accountId: String): CloudFolderTombstoneEntity = CloudFolderTombstoneEntity(
    accountId = accountId,
    rootId = rootId,
    nodeId = nodeId,
    relativePath = relativePath,
    kind = kind.name,
    deletedAt = deletedAt,
    deletedRevision = deletedRevision,
    deletedByDeviceId = deletedByDeviceId,
    lastKnownContentHash = lastKnownContentHash,
    lastKnownSizeBytes = lastKnownSizeBytes,
)

internal fun CloudFolderTombstoneEntity.toModel(): CloudFolderTombstone? = runCatching {
    CloudFolderTombstone(
        nodeId = nodeId,
        rootId = rootId,
        relativePath = relativePath,
        kind = CloudFolderNodeKind.valueOf(kind),
        deletedAt = deletedAt,
        deletedRevision = deletedRevision,
        deletedByDeviceId = deletedByDeviceId,
        lastKnownContentHash = lastKnownContentHash,
        lastKnownSizeBytes = lastKnownSizeBytes,
    )
}.getOrNull()

internal fun CloudFolderDeviceBinding.toEntity(accountId: String): CloudFolderDeviceBindingEntity = CloudFolderDeviceBindingEntity(
    accountId = accountId,
    rootId = rootId,
    deviceId = deviceId,
    permissionState = permissionState.name,
    materializationMode = materializationMode.name,
    lastAcknowledgedRevision = lastAcknowledgedRevision,
    lastScanAt = lastScanAt,
    lastError = lastError,
)

internal fun CloudFolderDeviceBindingEntity.toModel(localUri: String? = null): CloudFolderDeviceBinding? = runCatching {
    CloudFolderDeviceBinding(
        rootId = rootId,
        deviceId = deviceId,
        localUri = localUri,
        permissionState = CloudFolderPermissionState.valueOf(permissionState),
        materializationMode = CloudFolderMaterializationMode.valueOf(materializationMode),
        lastAcknowledgedRevision = lastAcknowledgedRevision,
        lastScanAt = lastScanAt,
        lastError = lastError,
    )
}.getOrNull()

internal fun cloudFolderOutboxOperationId(
    operation: CloudFolderSyncOperation,
    accountId: String,
    rootId: String,
): String {
    val material = listOf(
        accountId,
        rootId,
        operation.nodeId,
        operation.kind.name,
        operation.direction.name,
        operation.relativePath,
        operation.previousRelativePath.orEmpty(),
        operation.revision.toString(),
        operation.sourceNodeId.orEmpty(),
    ).joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "folder_op_${digest.take(32)}"
}

/** Compatibility helper for callers that predate account-scoped outboxes. */
internal fun cloudFolderOutboxOperationId(
    operation: CloudFolderSyncOperation,
    rootId: String,
): String = cloudFolderOutboxOperationId(operation, accountId = "", rootId = rootId)

internal fun CloudFolderSyncOperation.toOutboxEntity(
    accountId: String,
    rootId: String,
    operationId: String = "folder_op_${UUID.randomUUID()}",
    now: Long = 0L,
    sourceUri: String? = null,
): CloudFolderOutboxEntity = CloudFolderOutboxEntity(
    accountId = accountId,
    operationId = operationId,
    rootId = rootId,
    nodeId = nodeId,
    operationKind = kind.name,
    direction = direction.name,
    relativePath = relativePath,
    previousRelativePath = previousRelativePath,
    contentHash = contentHash,
    sizeBytes = sizeBytes,
    revision = revision,
    sourceNodeId = sourceNodeId,
    nextAttemptAt = now,
)

/**
 * Android's local half of the cloud-folder protocol.  It is intentionally
 * independent from the Drive SDK so the same inventory and outbox work in
 * OSS builds and can be tested without a network or an emulator.
 */
class CloudFolderSyncRepository(
    private val context: Context,
    accountId: String,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val privateDatabase: CloudFolderPrivateDatabase = CloudFolderPrivateDatabase.getDatabase(context),
    private val preferences: android.content.SharedPreferences =
        context.getSharedPreferences(CLOUD_FOLDER_PREFS, Context.MODE_PRIVATE),
    val deviceId: String = cloudFolderDeviceId(context),
) {
    val accountId: String = accountId.trim()

    init {
        require(accountId.isNotBlank()) { "Cloud-folder repository requires an account ID" }
    }

    private val dao = database.cloudFolderSyncDao()
    private val privateDao = privateDatabase.cloudFolderPrivateDao()

    suspend fun getManifest(rootId: String): CloudFolderManifest? {
        val root = dao.getRoot(accountId, rootId)?.toModel() ?: return null
        val nodes = dao.getNodes(accountId, rootId).mapNotNull { it.toModel() }
        val tombstones = dao.getTombstones(accountId, rootId).mapNotNull { it.toModel() }
        return CloudFolderManifest(
            root = root,
            revision = root.manifestRevision,
            baseRevision = root.manifestRevision,
            generatedAt = root.stats.scannedAt,
            generatedByDeviceId = root.createdByDeviceId,
            nodes = nodes,
            tombstones = tombstones,
        ).normalized()
    }

    suspend fun saveManifest(manifest: CloudFolderManifest) {
        val issues = manifest.validationIssues()
        require(issues.isEmpty()) { "Cannot persist invalid cloud-folder manifest: $issues" }
        val normalized = manifest.normalized()
        val root = normalized.root.copy(
            manifestRevision = normalized.revision,
            stats = normalized.root.stats.sanitized(),
        )
        dao.replaceManifest(
            root = root.toEntity(accountId),
            nodes = normalized.nodes.map { it.toEntity(accountId) },
            tombstones = normalized.tombstones.map { it.toEntity(accountId) },
        )
    }

    /**
     * Return the last target that was published but not fully materialized.
     * The target is validated on decode; a corrupt pending row must never be
     * interpreted as executable local state.
     */
    suspend fun getPendingMaterialization(rootId: String): CloudFolderManifest? {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return null
        val pending = dao.getPendingMaterialization(accountId, normalizedRootId) ?: return null
        val manifest = CloudFolderManifestCodec.decode(pending.manifestJson)
        require(manifest.rootId == normalizedRootId) {
            "Pending cloud-folder materialization root mismatch"
        }
        require(manifest.revision == pending.targetRevision) {
            "Pending cloud-folder materialization revision mismatch"
        }
        return manifest
    }

    /** Save a portable target before starting a potentially long transfer. */
    suspend fun savePendingMaterialization(
        manifest: CloudFolderManifest,
        now: Long = System.currentTimeMillis(),
    ) {
        val issues = manifest.validationIssues()
        require(issues.isEmpty()) { "Cannot persist invalid pending materialization: $issues" }
        val normalized = manifest.normalized()
        dao.upsertPendingMaterialization(
            CloudFolderPendingMaterializationEntity(
                accountId = accountId,
                rootId = normalized.rootId,
                manifestJson = CloudFolderManifestCodec.encode(normalized),
                targetRevision = normalized.revision,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun clearPendingMaterialization(rootId: String) {
        val normalizedRootId = rootId.trim().takeIf { it.isNotBlank() } ?: return
        dao.clearPendingMaterialization(accountId, normalizedRootId)
    }

    suspend fun getRoot(rootId: String): CloudFolderRoot? = dao.getRoot(accountId, rootId)?.toModel()

    suspend fun getRoots(): List<CloudFolderRoot> = dao.getRoots(accountId).map { it.toModel() }

    suspend fun getBinding(rootId: String, deviceId: String = this.deviceId): CloudFolderDeviceBinding? =
        dao.getBinding(accountId, rootId, deviceId)?.toModel(
            localUri = privateDao.getBindingUri(accountId, rootId, deviceId)?.localUri,
        )

    suspend fun getBindingsForDevice(deviceId: String = this.deviceId): List<CloudFolderDeviceBinding> =
        dao.getBindingsForDevice(accountId, deviceId).mapNotNull { entity ->
            entity.toModel(
                localUri = privateDao.getBindingUri(accountId, entity.rootId, entity.deviceId)?.localUri,
            )
        }

    suspend fun saveBinding(binding: CloudFolderDeviceBinding) {
        require(binding.rootId.isNotBlank() && binding.deviceId.isNotBlank()) {
            "Cloud-folder bindings require root and device IDs"
        }
        val normalizedBinding = binding.copy(localUri = binding.localUri?.trim())
        normalizedBinding.localUri?.let { localUri ->
            val existing = privateDao.getBindingForLocalUri(accountId, normalizedBinding.deviceId, localUri)
            require(existing == null || existing.rootId == normalizedBinding.rootId) {
                "A local folder is already bound to another cloud root"
            }
        }
        if (normalizedBinding.localUri.isNullOrBlank()) {
            privateDao.deleteBindingUri(accountId, normalizedBinding.rootId, normalizedBinding.deviceId)
        } else {
            privateDao.upsertBindingUri(
                CloudFolderBindingUriEntity(
                    accountId = accountId,
                    rootId = normalizedBinding.rootId,
                    deviceId = normalizedBinding.deviceId,
                    localUri = requireNotNull(normalizedBinding.localUri),
                )
            )
        }
        dao.upsertBinding(normalizedBinding.toEntity(accountId))
    }

    suspend fun findBindingForLocalUri(localUri: String, deviceId: String = this.deviceId): CloudFolderDeviceBinding? {
        val uri = localUri.trim()
        if (uri.isBlank()) return null
        val privateBinding = privateDao.getBindingForLocalUri(accountId, deviceId, uri) ?: return null
        return getBinding(privateBinding.rootId, deviceId)
    }

    /**
     * Adds only a local binding.  The account selection remains EXCLUDED until
     * the user explicitly opts this root into cloud sync through future UI.
     */
    suspend fun registerLocalFolder(
        localUri: String,
        name: String,
        /**
         * The logical root ID is generated by the device-local folder
         * configuration and must be retained when the repository is created.
         * Deriving it from a provider URI is kept only for older callers.
         */
        rootId: String? = null,
        materializationMode: CloudFolderMaterializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderDeviceBinding {
        val uri = localUri.trim()
        require(uri.isNotBlank()) { "A local folder URI is required" }
        val normalizedRootId = rootId?.trim()?.takeIf { it.isNotBlank() }
            ?: cloudFolderRootId("android-saf:$uri")
        val existingRoot = dao.getRoot(accountId, normalizedRootId)?.toModel()
        val root = existingRoot ?: CloudFolderRoot(
            rootId = normalizedRootId,
            name = name.trim().ifBlank { uri.substringAfterLast('/').ifBlank { "Local folder" } },
            createdAt = now,
            createdByDeviceId = deviceId,
            updatedAt = now,
        )
        dao.upsertRoot(root.copy(updatedAt = now, isDeleted = false).toEntity(accountId))
        val binding = CloudFolderDeviceBinding(
            rootId = normalizedRootId,
            deviceId = deviceId,
            localUri = uri,
            permissionState = CloudFolderPermissionState.UNKNOWN,
            materializationMode = materializationMode,
            lastAcknowledgedRevision = existingRoot?.manifestRevision ?: 0L,
        )
        saveBinding(binding)
        return binding
    }

    /**
     * Detach this device's provider grant without deleting the account-level
     * logical root. Other devices may still be using the same cloud folder.
     */
    suspend fun detachLocalFolder(rootId: String, deviceId: String = this.deviceId) {
        val normalizedRootId = rootId.trim()
        if (normalizedRootId.isBlank()) return
        dao.deleteBinding(accountId, normalizedRootId, deviceId)
        dao.clearOutbox(accountId, normalizedRootId)
        dao.clearPendingMaterialization(accountId, normalizedRootId)
    }

    suspend fun markBindingError(
        rootId: String,
        message: String?,
        permissionState: CloudFolderPermissionState? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = getBinding(rootId) ?: return
        saveBinding(
            current.copy(
                permissionState = permissionState ?: current.permissionState,
                lastError = message?.take(500),
                lastScanAt = if (message == null) now else current.lastScanAt,
            )
        )
    }

    suspend fun enqueue(
        rootId: String,
        operation: CloudFolderSyncOperation,
        now: Long = System.currentTimeMillis(),
        sourceUri: String? = null,
    ): String {
        val operationId = cloudFolderOutboxOperationId(operation, accountId, rootId)
        dao.upsertOutbox(operation.toOutboxEntity(accountId, rootId, operationId, now, sourceUri = null))
        persistOutboxSource(operationId, sourceUri)
        return operationId
    }

    suspend fun attachOutboxSourceUri(operationId: String, sourceUri: String) {
        val normalized = sourceUri.trim()
        require(normalized.isNotBlank()) { "An outbox source URI is required" }
        check(dao.getOutboxByOperation(accountId, operationId) != null) {
            "Cloud-folder outbox row is no longer available: $operationId"
        }
        privateDao.upsertOutboxSource(
            CloudFolderOutboxSourceEntity(accountId, operationId, normalized)
        )
    }

    suspend fun enqueueAll(
        rootId: String,
        operations: Collection<CloudFolderSyncOperation>,
        now: Long = System.currentTimeMillis(),
        sourceUris: Map<String, String> = emptyMap(),
    ) {
        if (operations.isEmpty()) return
        dao.upsertOutbox(
            operations.map { operation ->
                operation.toOutboxEntity(
                    accountId = accountId,
                    rootId = rootId,
                    operationId = cloudFolderOutboxOperationId(operation, accountId, rootId),
                    now = now,
                    sourceUri = null,
                )
            }
        )
        privateDao.upsertOutboxSources(
            operations.mapNotNull { operation ->
                sourceUris[operation.sourceNodeId ?: operation.nodeId]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sourceUri ->
                        CloudFolderOutboxSourceEntity(
                            accountId = accountId,
                            operationId = cloudFolderOutboxOperationId(operation, accountId, rootId),
                            sourceUri = sourceUri,
                        )
                    }
            }
        )
    }

    suspend fun getOutbox(rootId: String): List<CloudFolderOutboxEntity> = enrichOutbox(dao.getOutbox(accountId, rootId))

    /** Return durable conflicts for settings/recovery UI in deterministic order. */
    suspend fun getConflicts(rootId: String): List<CloudFolderConflictRecord> =
        dao.getConflicts(accountId, rootId).mapNotNull { entity ->
            runCatching {
                CloudFolderConflictRecord(
                    conflict = cloudFolderConflictJson.decodeFromString(
                        CloudFolderConflict.serializer(),
                        entity.conflictJson,
                    ),
                    baseRevision = entity.baseRevision,
                    localRevision = entity.localRevision,
                    remoteRevision = entity.remoteRevision,
                    resolution = CloudFolderConflictResolution.valueOf(entity.resolution),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            }.getOrNull()
        }

    suspend fun getConflictUiItems(): List<CloudFolderConflictUiItem> {
        val items = mutableListOf<CloudFolderConflictUiItem>()
        getRoots().filterNot { it.isDeleted }.forEach { root ->
            getConflicts(root.rootId).forEach { record ->
                items += CloudFolderConflictUiItem(
                    rootId = root.rootId,
                    folderName = root.name,
                    conflictId = record.conflictId,
                    relativePath = record.conflict.relativePath,
                    type = record.conflict.type,
                    resolution = record.resolution,
                    baseRevision = record.baseRevision,
                    localRevision = record.localRevision,
                    remoteRevision = record.remoteRevision,
                )
            }
        }
        return items.sortedWith(
            compareBy<CloudFolderConflictUiItem> { it.normalizedFolderName.lowercase() }
                .thenBy { it.normalizedPath.lowercase() }
                .thenBy { it.conflictId },
        )
    }

    /**
     * Reconcile the durable conflict set with a fresh plan. Existing choices
     * survive only when the exact conflict payload and revision triple still
     * match; changed bytes always reset the decision to DEFER.
     */
    suspend fun reconcileConflicts(
        plan: com.aryan.reader.shared.CloudFolderSyncPlan,
        now: Long = System.currentTimeMillis(),
    ): List<CloudFolderConflictRecord> {
        if (plan.conflicts.isEmpty()) {
            dao.clearConflicts(accountId, plan.rootId)
            return emptyList()
        }
        val existing = dao.getConflicts(accountId, plan.rootId).mapNotNull { entity ->
            runCatching {
                CloudFolderConflictRecord(
                    conflict = cloudFolderConflictJson.decodeFromString(
                        CloudFolderConflict.serializer(),
                        entity.conflictJson,
                    ),
                    baseRevision = entity.baseRevision,
                    localRevision = entity.localRevision,
                    remoteRevision = entity.remoteRevision,
                    resolution = CloudFolderConflictResolution.valueOf(entity.resolution),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            }.getOrNull()
        }.associateBy(CloudFolderConflictRecord::conflictId)
        val records = plan.conflicts.distinctBy(CloudFolderConflict::conflictId).map { conflict ->
            val old = existing[conflict.conflictId]
            val sameSnapshot = old != null &&
                old.baseRevision == plan.baseRevision &&
                old.localRevision == plan.localRevision &&
                old.remoteRevision == plan.remoteRevision &&
                old.conflict == conflict
            CloudFolderConflictRecord(
                conflict = conflict,
                baseRevision = plan.baseRevision,
                localRevision = plan.localRevision,
                remoteRevision = plan.remoteRevision,
                resolution = if (sameSnapshot) old!!.resolution else CloudFolderConflictResolution.DEFER,
                createdAt = if (sameSnapshot) old!!.createdAt else now,
                updatedAt = if (sameSnapshot) old!!.updatedAt else now,
            )
        }
        dao.clearConflicts(accountId, plan.rootId)
        dao.upsertConflicts(records.map { it.toEntity(accountId, now) })
        return records
    }

    /** Persist one decision; stale or unknown conflict IDs are rejected. */
    suspend fun resolveConflict(
        rootId: String,
        conflictId: String,
        resolution: CloudFolderConflictResolution,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val rows = dao.getConflicts(accountId, rootId)
        val row = rows.firstOrNull { it.conflictId == conflictId } ?: return false
        val updated = row.copy(resolution = resolution.name, updatedAt = now)
        dao.upsertConflict(updated)
        return true
    }

    suspend fun clearConflicts(rootId: String) {
        dao.clearConflicts(accountId, rootId)
    }

    suspend fun claimDueOutbox(
        rootId: String,
        now: Long = System.currentTimeMillis(),
        limit: Int = 50,
    ): List<CloudFolderOutboxEntity> {
        val rows = dao.getDueOutbox(accountId, rootId, now, limit.coerceIn(1, 500))
        return enrichOutbox(rows.filter {
            dao.claimOutbox(accountId, it.operationId, now) == 1
        }.map { it.copy(state = CloudFolderOutboxEntity.STATE_RUNNING, attempts = it.attempts + 1, lastAttemptAt = now) })
    }

    suspend fun completeOutbox(operationId: String) {
        privateDao.deleteOutboxSource(accountId, operationId)
        dao.deleteOutbox(accountId, operationId)
    }

    suspend fun failOutbox(
        operationId: String,
        error: String,
        retryAt: Long,
    ) {
        dao.recordOutboxAttempt(
            accountId = accountId,
            operationId = operationId,
            state = CloudFolderOutboxEntity.STATE_PENDING,
            attemptedAt = System.currentTimeMillis(),
            nextAttemptAt = retryAt,
            error = error.take(500),
        )
    }

    suspend fun quarantineOutbox(operationId: String, error: String) {
        check(
            dao.quarantineOutbox(
                accountId = accountId,
                operationId = operationId,
                attemptedAt = System.currentTimeMillis(),
                nextAttemptAt = Long.MAX_VALUE,
                error = error.take(500),
            ) == 1
        ) { "Cloud-folder outbox row is no longer available: $operationId" }
    }

    suspend fun resetRunningOutbox(now: Long = System.currentTimeMillis(), error: String? = "Worker restarted") {
        dao.resetRunningOutbox(accountId = accountId, nextAttemptAt = now, error = error)
    }

    /** Remove account-owned folder metadata and pending transfers on sign-out. */
    suspend fun clearAccountState() {
        dao.clearAccountState(accountId)
        privateDao.clearAccount(accountId)
        CloudFolderSyncPrefs.clear(context, accountId)
    }

    /**
     * Legacy rows whose owner was unknown during migration are deliberately
     * not attached to the first account that signs in.  The UI can show these
     * records and ask the user to explicitly recover or dismiss each one.
     */
    suspend fun pendingMigrationRecovery(): List<CloudFolderMigrationRecovery> =
        privateDao.getPendingRecovery().map(CloudFolderMigrationRecoveryEntity::toModel)

    /**
     * Explicitly rebind one quarantined logical root to this authenticated
     * account.  The old manifest/outbox state is copied under the new account
     * key, while the selected local URI is stored only in the no-backup DB.
     */
    suspend fun claimMigrationRecovery(
        legacyRootId: String,
        legacyDeviceId: String,
        localUri: String? = null,
        materializationMode: CloudFolderMaterializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderDeviceBinding? {
        val recovery = privateDao.getRecovery(legacyRootId, legacyDeviceId)
            ?.takeIf { it.state == CloudFolderMigrationRecoveryEntity.STATE_PENDING }
            ?: return null
        val legacyRoot = dao.getRoot("", legacyRootId) ?: return null
        val legacyNodes = dao.getNodes("", legacyRootId)
        val legacyTombstones = dao.getTombstones("", legacyRootId)
        val legacyOutbox = dao.getOutbox("", legacyRootId)
        val legacySources: Map<String, String?> = privateDao.getAllOutboxSources("")
            .associate { it.operationId to it.sourceUri }

        val copiedRoot = legacyRoot.copy(accountId = accountId)
        val copiedNodes = legacyNodes.map { it.copy(accountId = accountId) }
        val copiedTombstones = legacyTombstones.map { it.copy(accountId = accountId) }
        dao.replaceManifest(copiedRoot, copiedNodes, copiedTombstones)

        val copiedOutbox: List<Pair<CloudFolderOutboxEntity, String?>> = legacyOutbox.map { row ->
            val operation = CloudFolderSyncOperation(
                nodeId = row.nodeId,
                kind = CloudFolderSyncOperationKind.valueOf(row.operationKind),
                direction = CloudFolderSyncDirection.valueOf(row.direction),
                relativePath = row.relativePath,
                previousRelativePath = row.previousRelativePath,
                contentHash = row.contentHash,
                sizeBytes = row.sizeBytes,
                revision = row.revision,
                sourceNodeId = row.sourceNodeId,
            )
            val operationId = cloudFolderOutboxOperationId(operation, accountId, row.rootId)
            Pair(
                row.copy(
                    accountId = accountId,
                    operationId = operationId,
                ),
                legacySources[row.operationId],
            )
        }
        if (copiedOutbox.isNotEmpty()) {
            dao.upsertOutbox(copiedOutbox.map { it.first })
            privateDao.upsertOutboxSources(
                copiedOutbox.mapNotNull { (row, source) ->
                    source?.trim()?.takeIf { it.isNotBlank() }?.let {
                        CloudFolderOutboxSourceEntity(accountId, row.operationId, it)
                    }
                }
            )
        }

        val resolvedUri = (localUri ?: recovery.localUri)?.trim()?.takeIf { it.isNotBlank() }
        val binding = CloudFolderDeviceBinding(
            rootId = legacyRootId,
            deviceId = deviceId,
            localUri = resolvedUri,
            permissionState = if (resolvedUri == null) {
                CloudFolderPermissionState.UNKNOWN
            } else {
                CloudFolderPermissionState.GRANTED
            },
            materializationMode = materializationMode,
            lastAcknowledgedRevision = legacyRoot.manifestRevision,
        )
        saveBinding(binding)
        check(privateDao.claimRecovery(legacyRootId, legacyDeviceId, accountId, now) == 1) {
            "Cloud-folder migration recovery was already claimed"
        }
        return binding
    }

    suspend fun dismissMigrationRecovery(
        legacyRootId: String,
        legacyDeviceId: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean =
        privateDao.dismissRecovery(legacyRootId, legacyDeviceId, now) == 1

    fun selection(): CloudFolderSyncSelection {
        return CloudFolderSyncPrefs.load(context, accountId)
    }

    fun setSelection(selection: CloudFolderSyncSelection) {
        CloudFolderSyncPrefs.save(context, accountId, selection)
    }

    fun isIncluded(rootId: String): Boolean = selection().includes(rootId)

    private suspend fun persistOutboxSource(operationId: String, sourceUri: String?) {
        val normalized = sourceUri?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            privateDao.deleteOutboxSource(accountId, operationId)
        } else {
            privateDao.upsertOutboxSource(
                CloudFolderOutboxSourceEntity(
                    accountId = accountId,
                    operationId = operationId,
                    sourceUri = normalized,
                )
            )
        }
    }

    private suspend fun enrichOutbox(rows: List<CloudFolderOutboxEntity>): List<CloudFolderOutboxEntity> =
        rows.map { row ->
            row.copy().apply {
                sourceUri = privateDao.getOutboxSource(accountId, row.operationId)?.sourceUri
            }
        }
}

private val cloudFolderConflictJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

private fun CloudFolderConflictRecord.toEntity(
    accountId: String,
    now: Long,
): CloudFolderConflictEntity = CloudFolderConflictEntity(
    accountId = accountId,
    rootId = rootId,
    conflictId = conflictId,
    conflictJson = cloudFolderConflictJson.encodeToString(CloudFolderConflict.serializer(), conflict),
    baseRevision = baseRevision,
    localRevision = localRevision,
    remoteRevision = remoteRevision,
    resolution = resolution.name,
    createdAt = createdAt,
    updatedAt = maxOf(updatedAt, now),
)

internal fun cloudFolderDeviceId(context: Context): String {
    val preferences = context.getSharedPreferences(CLOUD_FOLDER_PREFS, Context.MODE_PRIVATE)
    preferences.getString(CLOUD_FOLDER_DEVICE_ID_KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }
    val androidId = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()?.trim().orEmpty()
    val id = androidId.takeIf { it.isNotBlank() } ?: "android-${UUID.randomUUID()}"
    preferences.edit(commit = true) { putString(CLOUD_FOLDER_DEVICE_ID_KEY, id) }
    return id
}
