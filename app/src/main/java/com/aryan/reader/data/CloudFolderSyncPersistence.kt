package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Ignore
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Device-local inventory for the cloud-folder protocol.  Provider URIs and
 * permission state deliberately live only in the binding table; neither is
 * part of the portable manifest uploaded to Drive.
 */
@Entity(
    tableName = "cloud_folder_roots",
    primaryKeys = ["accountId", "rootId"],
)
data class CloudFolderRootEntity(
    val accountId: String,
    val rootId: String,
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
    primaryKeys = ["accountId", "rootId", "deviceId"],
    indices = [
        Index(value = ["accountId", "rootId"]),
        Index(value = ["accountId", "deviceId"]),
    ],
)
data class CloudFolderDeviceBindingEntity(
    val accountId: String,
    val rootId: String,
    val deviceId: String,
    val permissionState: String,
    val materializationMode: String,
    val lastAcknowledgedRevision: Long,
    val lastScanAt: Long,
    val lastError: String?,
) {
    /** Loaded from [CloudFolderPrivateDatabase], never persisted here. */
    @Ignore
    var localUri: String? = null
}

@Entity(
    tableName = "cloud_folder_nodes",
    primaryKeys = ["accountId", "rootId", "nodeId"],
    indices = [
        Index(value = ["accountId", "rootId"]),
        Index(value = ["accountId", "rootId", "relativePath"]),
    ],
)
data class CloudFolderNodeEntity(
    val accountId: String,
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
    primaryKeys = ["accountId", "rootId", "nodeId"],
    indices = [
        Index(value = ["accountId", "rootId"]),
        Index(value = ["accountId", "rootId", "relativePath"]),
    ],
)
data class CloudFolderTombstoneEntity(
    val accountId: String,
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
    primaryKeys = ["accountId", "operationId"],
    indices = [
        Index(value = ["accountId", "rootId", "state", "nextAttemptAt"]),
        Index(value = ["accountId", "rootId", "nodeId"]),
    ],
)
data class CloudFolderOutboxEntity(
    val accountId: String,
    val operationId: String,
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
    /** Loaded from [CloudFolderPrivateDatabase], never persisted here. */
    @Ignore
    var sourceUri: String? = null

    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_QUARANTINED = "QUARANTINED"
    }
}

@Dao
abstract class CloudFolderSyncDao {
    @Query("SELECT * FROM cloud_folder_roots WHERE accountId = :accountId AND rootId = :rootId LIMIT 1")
    abstract suspend fun getRoot(accountId: String, rootId: String): CloudFolderRootEntity?

    @Query("SELECT * FROM cloud_folder_roots WHERE accountId = :accountId ORDER BY name COLLATE NOCASE, rootId")
    abstract suspend fun getRoots(accountId: String): List<CloudFolderRootEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRoot(root: CloudFolderRootEntity)

    @Query("UPDATE cloud_folder_roots SET isDeleted = 1, updatedAt = :updatedAt WHERE accountId = :accountId AND rootId = :rootId")
    abstract suspend fun markRootDeleted(accountId: String, rootId: String, updatedAt: Long): Int

    @Query("SELECT * FROM cloud_folder_bindings WHERE accountId = :accountId AND rootId = :rootId AND deviceId = :deviceId LIMIT 1")
    abstract suspend fun getBinding(accountId: String, rootId: String, deviceId: String): CloudFolderDeviceBindingEntity?

    @Query("SELECT * FROM cloud_folder_bindings WHERE accountId = :accountId AND deviceId = :deviceId ORDER BY rootId")
    abstract suspend fun getBindingsForDevice(accountId: String, deviceId: String): List<CloudFolderDeviceBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBinding(binding: CloudFolderDeviceBindingEntity)

    @Query("DELETE FROM cloud_folder_bindings WHERE accountId = :accountId AND rootId = :rootId AND deviceId = :deviceId")
    abstract suspend fun deleteBinding(accountId: String, rootId: String, deviceId: String): Int

    @Query("SELECT * FROM cloud_folder_nodes WHERE accountId = :accountId AND rootId = :rootId ORDER BY relativePath COLLATE NOCASE, nodeId")
    abstract suspend fun getNodes(accountId: String, rootId: String): List<CloudFolderNodeEntity>

    @Query("SELECT * FROM cloud_folder_tombstones WHERE accountId = :accountId AND rootId = :rootId ORDER BY relativePath COLLATE NOCASE, nodeId")
    abstract suspend fun getTombstones(accountId: String, rootId: String): List<CloudFolderTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertNodes(nodes: List<CloudFolderNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTombstones(tombstones: List<CloudFolderTombstoneEntity>)

    @Query("DELETE FROM cloud_folder_nodes WHERE accountId = :accountId AND rootId = :rootId")
    abstract suspend fun deleteNodes(accountId: String, rootId: String): Int

    @Query("DELETE FROM cloud_folder_tombstones WHERE accountId = :accountId AND rootId = :rootId")
    abstract suspend fun deleteTombstones(accountId: String, rootId: String): Int

    @Transaction
    open suspend fun replaceManifest(
        root: CloudFolderRootEntity,
        nodes: List<CloudFolderNodeEntity>,
        tombstones: List<CloudFolderTombstoneEntity>,
    ) {
        upsertRoot(root)
        deleteNodes(root.accountId, root.rootId)
        deleteTombstones(root.accountId, root.rootId)
        if (nodes.isNotEmpty()) upsertNodes(nodes)
        if (tombstones.isNotEmpty()) upsertTombstones(tombstones)
    }

    @Query(
        "SELECT * FROM cloud_folder_outbox " +
            "WHERE accountId = :accountId AND rootId = :rootId AND state = :pendingState AND nextAttemptAt <= :now " +
            "ORDER BY revision, relativePath COLLATE NOCASE, operationId LIMIT :limit"
    )
    abstract suspend fun getDueOutbox(
        accountId: String,
        rootId: String,
        now: Long,
        limit: Int,
        pendingState: String = CloudFolderOutboxEntity.STATE_PENDING,
    ): List<CloudFolderOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutbox(row: CloudFolderOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutbox(rows: List<CloudFolderOutboxEntity>)

    @Query("DELETE FROM cloud_folder_outbox WHERE accountId = :accountId AND operationId = :operationId")
    abstract suspend fun deleteOutbox(accountId: String, operationId: String): Int

    @Query("SELECT * FROM cloud_folder_outbox WHERE accountId = :accountId AND operationId = :operationId LIMIT 1")
    abstract suspend fun getOutboxByOperation(accountId: String, operationId: String): CloudFolderOutboxEntity?

    @Query(
        "UPDATE cloud_folder_outbox SET state = :runningState, attempts = attempts + 1, " +
            "lastAttemptAt = :now WHERE accountId = :accountId AND operationId = :operationId AND state = :pendingState " +
            "AND nextAttemptAt <= :now"
    )
    abstract suspend fun claimOutbox(
        accountId: String,
        operationId: String,
        now: Long,
        pendingState: String = CloudFolderOutboxEntity.STATE_PENDING,
        runningState: String = CloudFolderOutboxEntity.STATE_RUNNING,
    ): Int

    @Query("DELETE FROM cloud_folder_outbox WHERE accountId = :accountId AND rootId = :rootId")
    abstract suspend fun clearOutbox(accountId: String, rootId: String): Int

    @Query("SELECT * FROM cloud_folder_outbox WHERE accountId = :accountId AND rootId = :rootId ORDER BY revision, relativePath COLLATE NOCASE, operationId")
    abstract suspend fun getOutbox(accountId: String, rootId: String): List<CloudFolderOutboxEntity>

    @Query(
        "UPDATE cloud_folder_outbox SET state = :state, attempts = attempts + 1, " +
            "lastAttemptAt = :attemptedAt, nextAttemptAt = :nextAttemptAt, lastError = :error " +
            "WHERE accountId = :accountId AND operationId = :operationId"
    )
    abstract suspend fun recordOutboxAttempt(
        accountId: String,
        operationId: String,
        state: String,
        attemptedAt: Long,
        nextAttemptAt: Long,
        error: String?,
    ): Int

    @Query(
        "UPDATE cloud_folder_outbox SET state = :quarantinedState, " +
            "lastAttemptAt = :attemptedAt, nextAttemptAt = :nextAttemptAt, lastError = :error " +
            "WHERE accountId = :accountId AND operationId = :operationId"
    )
    abstract suspend fun quarantineOutbox(
        accountId: String,
        operationId: String,
        attemptedAt: Long,
        nextAttemptAt: Long,
        error: String?,
        quarantinedState: String = CloudFolderOutboxEntity.STATE_QUARANTINED,
    ): Int

    @Query(
        "UPDATE cloud_folder_outbox SET state = :pendingState, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE accountId = :accountId AND state = :runningState"
    )
    abstract suspend fun resetRunningOutbox(
        accountId: String,
        nextAttemptAt: Long,
        error: String?,
        runningState: String = CloudFolderOutboxEntity.STATE_RUNNING,
        pendingState: String = CloudFolderOutboxEntity.STATE_PENDING,
    ): Int

    @Query("DELETE FROM cloud_folder_roots WHERE accountId = :accountId")
    abstract suspend fun deleteRootsForAccount(accountId: String): Int

    @Query("DELETE FROM cloud_folder_bindings WHERE accountId = :accountId")
    abstract suspend fun deleteBindingsForAccount(accountId: String): Int

    @Query("DELETE FROM cloud_folder_nodes WHERE accountId = :accountId")
    abstract suspend fun deleteNodesForAccount(accountId: String): Int

    @Query("DELETE FROM cloud_folder_tombstones WHERE accountId = :accountId")
    abstract suspend fun deleteTombstonesForAccount(accountId: String): Int

    @Query("DELETE FROM cloud_folder_outbox WHERE accountId = :accountId")
    abstract suspend fun deleteOutboxForAccount(accountId: String): Int

    @Transaction
    open suspend fun clearAccountState(accountId: String) {
        deleteOutboxForAccount(accountId)
        deleteTombstonesForAccount(accountId)
        deleteNodesForAccount(accountId)
        deleteBindingsForAccount(accountId)
        deleteRootsForAccount(accountId)
    }
}
