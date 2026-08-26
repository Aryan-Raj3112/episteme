package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * Device-local inventory for the cloud-folder protocol.  Provider URIs and
 * permission state deliberately live only in the binding table; neither is
 * part of the portable manifest uploaded to Drive.
 */
@Entity(
    tableName = "cloud_folder_roots",
)
data class CloudFolderRootEntity(
    @PrimaryKey val rootId: String,
    val name: String,
    val createdAt: Long,
    val createdByDeviceId: String,
    val updatedAt: Long,
    val manifestRevision: Long,
    val fileCount: Int,
    val directoryCount: Int,
    val totalBytes: Long,
    val scannedAt: Long,
    val scanComplete: Boolean,
    val isDeleted: Boolean,
)

@Entity(
    tableName = "cloud_folder_bindings",
    primaryKeys = ["rootId", "deviceId"],
    indices = [Index(value = ["rootId"]), Index(value = ["deviceId"])],
)
data class CloudFolderDeviceBindingEntity(
    val rootId: String,
    val deviceId: String,
    val localUri: String?,
    val permissionState: String,
    val materializationMode: String,
    val lastAcknowledgedRevision: Long,
    val lastScanAt: Long,
    val lastError: String?,
)

@Entity(
    tableName = "cloud_folder_nodes",
    primaryKeys = ["rootId", "nodeId"],
    indices = [Index(value = ["rootId"]), Index(value = ["rootId", "relativePath"])],
)
data class CloudFolderNodeEntity(
    val rootId: String,
    val nodeId: String,
    val relativePath: String,
    val kind: String,
    val contentHash: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val fileModifiedAt: Long,
    val revision: Long,
    val modifiedAt: Long,
    val modifiedByDeviceId: String,
    val contentObjectId: String?,
)

@Entity(
    tableName = "cloud_folder_tombstones",
    primaryKeys = ["rootId", "nodeId"],
    indices = [Index(value = ["rootId"]), Index(value = ["rootId", "relativePath"])],
)
data class CloudFolderTombstoneEntity(
    val rootId: String,
    val nodeId: String,
    val relativePath: String,
    val kind: String,
    val deletedAt: Long,
    val deletedRevision: Long,
    val deletedByDeviceId: String,
    val lastKnownContentHash: String?,
    val lastKnownSizeBytes: Long,
)

/**
 * Outbox rows are removed only after the corresponding transfer has
 * succeeded.  A process death therefore leaves enough information for the
 * next WorkManager run to retry without relying on in-memory state.
 */
@Entity(
    tableName = "cloud_folder_outbox",
    indices = [
        Index(value = ["rootId", "state", "nextAttemptAt"]),
        Index(value = ["rootId", "nodeId"]),
    ],
)
data class CloudFolderOutboxEntity(
    @PrimaryKey val operationId: String,
    val rootId: String,
    val nodeId: String,
    val operationKind: String,
    val direction: String,
    val relativePath: String,
    val previousRelativePath: String?,
    val contentHash: String?,
    val sizeBytes: Long,
    val revision: Long,
    val state: String = STATE_PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val lastError: String? = null,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_RUNNING = "RUNNING"
    }
}

@Dao
abstract class CloudFolderSyncDao {
    @Query("SELECT * FROM cloud_folder_roots WHERE rootId = :rootId LIMIT 1")
    abstract suspend fun getRoot(rootId: String): CloudFolderRootEntity?

    @Query("SELECT * FROM cloud_folder_roots ORDER BY name COLLATE NOCASE, rootId")
    abstract suspend fun getRoots(): List<CloudFolderRootEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRoot(root: CloudFolderRootEntity)

    @Query("UPDATE cloud_folder_roots SET isDeleted = 1, updatedAt = :updatedAt WHERE rootId = :rootId")
    abstract suspend fun markRootDeleted(rootId: String, updatedAt: Long): Int

    @Query("SELECT * FROM cloud_folder_bindings WHERE rootId = :rootId AND deviceId = :deviceId LIMIT 1")
    abstract suspend fun getBinding(rootId: String, deviceId: String): CloudFolderDeviceBindingEntity?

    @Query("SELECT * FROM cloud_folder_bindings WHERE localUri = :localUri AND deviceId = :deviceId LIMIT 1")
    abstract suspend fun getBindingForLocalUri(localUri: String, deviceId: String): CloudFolderDeviceBindingEntity?

    @Query("SELECT * FROM cloud_folder_bindings WHERE deviceId = :deviceId ORDER BY rootId")
    abstract suspend fun getBindingsForDevice(deviceId: String): List<CloudFolderDeviceBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBinding(binding: CloudFolderDeviceBindingEntity)

    @Query("DELETE FROM cloud_folder_bindings WHERE rootId = :rootId AND deviceId = :deviceId")
    abstract suspend fun deleteBinding(rootId: String, deviceId: String): Int

    @Query("SELECT * FROM cloud_folder_nodes WHERE rootId = :rootId ORDER BY relativePath COLLATE NOCASE, nodeId")
    abstract suspend fun getNodes(rootId: String): List<CloudFolderNodeEntity>

    @Query("SELECT * FROM cloud_folder_tombstones WHERE rootId = :rootId ORDER BY relativePath COLLATE NOCASE, nodeId")
    abstract suspend fun getTombstones(rootId: String): List<CloudFolderTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertNodes(nodes: List<CloudFolderNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTombstones(tombstones: List<CloudFolderTombstoneEntity>)

    @Query("DELETE FROM cloud_folder_nodes WHERE rootId = :rootId")
    abstract suspend fun deleteNodes(rootId: String): Int

    @Query("DELETE FROM cloud_folder_tombstones WHERE rootId = :rootId")
    abstract suspend fun deleteTombstones(rootId: String): Int

    @Transaction
    open suspend fun replaceManifest(
        root: CloudFolderRootEntity,
        nodes: List<CloudFolderNodeEntity>,
        tombstones: List<CloudFolderTombstoneEntity>,
    ) {
        upsertRoot(root)
        deleteNodes(root.rootId)
        deleteTombstones(root.rootId)
        if (nodes.isNotEmpty()) upsertNodes(nodes)
        if (tombstones.isNotEmpty()) upsertTombstones(tombstones)
    }

    @Query(
        "SELECT * FROM cloud_folder_outbox " +
            "WHERE rootId = :rootId AND state = :pendingState AND nextAttemptAt <= :now " +
            "ORDER BY revision, relativePath COLLATE NOCASE, operationId LIMIT :limit"
    )
    abstract suspend fun getDueOutbox(
        rootId: String,
        now: Long,
        limit: Int,
        pendingState: String = CloudFolderOutboxEntity.STATE_PENDING,
    ): List<CloudFolderOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutbox(row: CloudFolderOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutbox(rows: List<CloudFolderOutboxEntity>)

    @Query("DELETE FROM cloud_folder_outbox WHERE operationId = :operationId")
    abstract suspend fun deleteOutbox(operationId: String): Int

    @Query(
        "UPDATE cloud_folder_outbox SET state = :runningState, attempts = attempts + 1, " +
            "lastAttemptAt = :now WHERE operationId = :operationId AND state = :pendingState " +
            "AND nextAttemptAt <= :now"
    )
    abstract suspend fun claimOutbox(
        operationId: String,
        now: Long,
        pendingState: String = CloudFolderOutboxEntity.STATE_PENDING,
        runningState: String = CloudFolderOutboxEntity.STATE_RUNNING,
    ): Int

    @Query("DELETE FROM cloud_folder_outbox WHERE rootId = :rootId")
    abstract suspend fun clearOutbox(rootId: String): Int

    @Query("SELECT * FROM cloud_folder_outbox WHERE rootId = :rootId ORDER BY revision, relativePath COLLATE NOCASE, operationId")
    abstract suspend fun getOutbox(rootId: String): List<CloudFolderOutboxEntity>

    @Query(
        "UPDATE cloud_folder_outbox SET state = :state, attempts = attempts + 1, " +
            "lastAttemptAt = :attemptedAt, nextAttemptAt = :nextAttemptAt, lastError = :error " +
            "WHERE operationId = :operationId"
    )
    abstract suspend fun recordOutboxAttempt(
        operationId: String,
        state: String,
        attemptedAt: Long,
        nextAttemptAt: Long,
        error: String?,
    ): Int

    @Query(
        "UPDATE cloud_folder_outbox SET state = :pendingState, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE state = :runningState"
    )
    abstract suspend fun resetRunningOutbox(
        nextAttemptAt: Long,
        error: String?,
        runningState: String = CloudFolderOutboxEntity.STATE_RUNNING,
        pendingState: String = CloudFolderOutboxEntity.STATE_PENDING,
    ): Int
}
