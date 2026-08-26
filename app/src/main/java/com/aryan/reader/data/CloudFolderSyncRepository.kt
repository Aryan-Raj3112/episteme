package com.aryan.reader.data

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.aryan.reader.CloudFolderSyncPrefs
import com.aryan.reader.shared.CloudFolderDeviceBinding
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
        val manifest = json.decodeFromString(CloudFolderManifest.serializer(), raw).normalized()
        require(manifest.validationIssues().isEmpty()) {
            "Invalid cloud-folder manifest: ${manifest.validationIssues().joinToString()}"
        }
        return manifest
    }
}

internal fun CloudFolderRoot.toEntity(): CloudFolderRootEntity = CloudFolderRootEntity(
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

internal fun CloudFolderNode.toEntity(): CloudFolderNodeEntity = CloudFolderNodeEntity(
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

internal fun CloudFolderTombstone.toEntity(): CloudFolderTombstoneEntity = CloudFolderTombstoneEntity(
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

internal fun CloudFolderDeviceBinding.toEntity(): CloudFolderDeviceBindingEntity = CloudFolderDeviceBindingEntity(
    rootId = rootId,
    deviceId = deviceId,
    localUri = localUri,
    permissionState = permissionState.name,
    materializationMode = materializationMode.name,
    lastAcknowledgedRevision = lastAcknowledgedRevision,
    lastScanAt = lastScanAt,
    lastError = lastError,
)

internal fun CloudFolderDeviceBindingEntity.toModel(): CloudFolderDeviceBinding? = runCatching {
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
    rootId: String = "",
): String {
    val material = listOf(
        rootId,
        operation.nodeId,
        operation.kind.name,
        operation.direction.name,
        operation.relativePath,
        operation.previousRelativePath.orEmpty(),
        operation.revision.toString(),
    ).joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "folder_op_${digest.take(32)}"
}

internal fun CloudFolderSyncOperation.toOutboxEntity(
    rootId: String,
    operationId: String = "folder_op_${UUID.randomUUID()}",
    now: Long = 0L,
): CloudFolderOutboxEntity = CloudFolderOutboxEntity(
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
    nextAttemptAt = now,
)

/**
 * Android's local half of the cloud-folder protocol.  It is intentionally
 * independent from the Drive SDK so the same inventory and outbox work in
 * OSS builds and can be tested without a network or an emulator.
 */
class CloudFolderSyncRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val preferences: android.content.SharedPreferences =
        context.getSharedPreferences(CLOUD_FOLDER_PREFS, Context.MODE_PRIVATE),
    val deviceId: String = cloudFolderDeviceId(context),
) {
    private val dao = database.cloudFolderSyncDao()

    suspend fun getManifest(rootId: String): CloudFolderManifest? {
        val root = dao.getRoot(rootId)?.toModel() ?: return null
        val nodes = dao.getNodes(rootId).mapNotNull { it.toModel() }
        val tombstones = dao.getTombstones(rootId).mapNotNull { it.toModel() }
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
        val normalized = manifest.normalized()
        val issues = normalized.validationIssues()
        require(issues.isEmpty()) { "Cannot persist invalid cloud-folder manifest: $issues" }
        val root = normalized.root.copy(
            manifestRevision = normalized.revision,
            stats = normalized.root.stats.sanitized(),
        )
        dao.replaceManifest(
            root = root.toEntity(),
            nodes = normalized.nodes.map { it.toEntity() },
            tombstones = normalized.tombstones.map { it.toEntity() },
        )
    }

    suspend fun getRoot(rootId: String): CloudFolderRoot? = dao.getRoot(rootId)?.toModel()

    suspend fun getRoots(): List<CloudFolderRoot> = dao.getRoots().map { it.toModel() }

    suspend fun getBinding(rootId: String, deviceId: String = this.deviceId): CloudFolderDeviceBinding? =
        dao.getBinding(rootId, deviceId)?.toModel()

    suspend fun getBindingsForDevice(deviceId: String = this.deviceId): List<CloudFolderDeviceBinding> =
        dao.getBindingsForDevice(deviceId).mapNotNull { it.toModel() }

    suspend fun saveBinding(binding: CloudFolderDeviceBinding) {
        require(binding.rootId.isNotBlank() && binding.deviceId.isNotBlank()) {
            "Cloud-folder bindings require root and device IDs"
        }
        dao.upsertBinding(binding.toEntity())
    }

    suspend fun findBindingForLocalUri(localUri: String, deviceId: String = this.deviceId): CloudFolderDeviceBinding? =
        dao.getBindingForLocalUri(localUri, deviceId)?.toModel()

    /**
     * Adds only a local binding.  The account selection remains EXCLUDED until
     * the user explicitly opts this root into cloud sync through future UI.
     */
    suspend fun registerLocalFolder(
        localUri: String,
        name: String,
        materializationMode: CloudFolderMaterializationMode = CloudFolderMaterializationMode.CLOUD_ONLY,
        now: Long = System.currentTimeMillis(),
    ): CloudFolderDeviceBinding {
        val uri = localUri.trim()
        require(uri.isNotBlank()) { "A local folder URI is required" }
        val rootId = cloudFolderRootId("android-saf:$uri")
        val existingRoot = dao.getRoot(rootId)?.toModel()
        val root = existingRoot ?: CloudFolderRoot(
            rootId = rootId,
            name = name.trim().ifBlank { uri.substringAfterLast('/').ifBlank { "Local folder" } },
            createdAt = now,
            createdByDeviceId = deviceId,
            updatedAt = now,
        )
        dao.upsertRoot(root.copy(updatedAt = now, isDeleted = false).toEntity())
        val binding = CloudFolderDeviceBinding(
            rootId = rootId,
            deviceId = deviceId,
            localUri = uri,
            permissionState = CloudFolderPermissionState.UNKNOWN,
            materializationMode = materializationMode,
            lastAcknowledgedRevision = existingRoot?.manifestRevision ?: 0L,
        )
        dao.upsertBinding(binding.toEntity())
        return binding
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

    suspend fun enqueue(rootId: String, operation: CloudFolderSyncOperation, now: Long = System.currentTimeMillis()): String {
        val operationId = cloudFolderOutboxOperationId(operation, rootId)
        dao.upsertOutbox(operation.toOutboxEntity(rootId, operationId, now))
        return operationId
    }

    suspend fun enqueueAll(
        rootId: String,
        operations: Collection<CloudFolderSyncOperation>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (operations.isEmpty()) return
        dao.upsertOutbox(
            operations.map { operation ->
                operation.toOutboxEntity(
                    rootId = rootId,
                    operationId = cloudFolderOutboxOperationId(operation, rootId),
                    now = now,
                )
            }
        )
    }

    suspend fun getOutbox(rootId: String): List<CloudFolderOutboxEntity> = dao.getOutbox(rootId)

    suspend fun claimDueOutbox(
        rootId: String,
        now: Long = System.currentTimeMillis(),
        limit: Int = 50,
    ): List<CloudFolderOutboxEntity> {
        val rows = dao.getDueOutbox(rootId, now, limit.coerceIn(1, 500))
        return rows.filter {
            dao.claimOutbox(it.operationId, now) == 1
        }.map { it.copy(state = CloudFolderOutboxEntity.STATE_RUNNING, attempts = it.attempts + 1, lastAttemptAt = now) }
    }

    suspend fun completeOutbox(operationId: String) {
        dao.deleteOutbox(operationId)
    }

    suspend fun failOutbox(
        operationId: String,
        error: String,
        retryAt: Long,
    ) {
        dao.recordOutboxAttempt(
            operationId = operationId,
            state = CloudFolderOutboxEntity.STATE_PENDING,
            attemptedAt = System.currentTimeMillis(),
            nextAttemptAt = retryAt,
            error = error.take(500),
        )
    }

    suspend fun resetRunningOutbox(now: Long = System.currentTimeMillis(), error: String? = "Worker restarted") {
        dao.resetRunningOutbox(nextAttemptAt = now, error = error)
    }

    fun selection(): CloudFolderSyncSelection {
        return CloudFolderSyncPrefs.load(context)
    }

    fun setSelection(selection: CloudFolderSyncSelection) {
        CloudFolderSyncPrefs.save(context, selection)
    }

    fun isIncluded(rootId: String): Boolean = selection().includes(rootId)
}

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
