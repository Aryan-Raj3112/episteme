package com.aryan.reader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import timber.log.Timber

/**
 * Device-private cloud-folder state.
 *
 * SAF tree/document URIs are capabilities tied to one installation.  They are
 * intentionally kept out of [AppDatabase], because that database is included
 * in Android backup/device transfer for the rest of the Reader library.
 */
@Entity(
    tableName = "cloud_folder_binding_uris",
    primaryKeys = ["accountId", "rootId", "deviceId"],
    indices = [
        Index(value = ["accountId", "deviceId", "localUri"], unique = true),
    ],
)
data class CloudFolderBindingUriEntity(
    val accountId: String,
    val rootId: String,
    val deviceId: String,
    val localUri: String,
)

@Entity(
    tableName = "cloud_folder_outbox_sources",
    primaryKeys = ["accountId", "operationId"],
)
data class CloudFolderOutboxSourceEntity(
    val accountId: String,
    val operationId: String,
    val sourceUri: String,
)

/**
 * Rows created by the 29 -> 30 migration cannot be assigned to an account
 * safely.  Keep a small, one-time rebind record so the user can explicitly
 * attach the old logical root to the account currently signed in.
 */
@Entity(
    tableName = "cloud_folder_migration_recovery",
    primaryKeys = ["legacyRootId", "legacyDeviceId"],
)
data class CloudFolderMigrationRecoveryEntity(
    val legacyRootId: String,
    val legacyDeviceId: String,
    val displayName: String,
    val localUri: String?,
    val manifestRevision: Long,
    val createdAt: Long,
    val state: String = STATE_PENDING,
    val claimedAccountId: String? = null,
    val claimedAt: Long? = null,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_CLAIMED = "CLAIMED"
        const val STATE_DISMISSED = "DISMISSED"
    }
}

data class CloudFolderMigrationRecovery(
    val legacyRootId: String,
    val legacyDeviceId: String,
    val displayName: String,
    val localUri: String?,
    val manifestRevision: Long,
    val createdAt: Long,
    val state: String,
    val claimedAccountId: String?,
    val claimedAt: Long?,
)

@Dao
abstract class CloudFolderPrivateDao {
    @Query(
        "SELECT * FROM cloud_folder_binding_uris " +
            "WHERE accountId = :accountId AND rootId = :rootId AND deviceId = :deviceId LIMIT 1"
    )
    abstract fun getBindingUri(accountId: String, rootId: String, deviceId: String): CloudFolderBindingUriEntity?

    @Query(
        "SELECT * FROM cloud_folder_binding_uris " +
            "WHERE accountId = :accountId AND deviceId = :deviceId AND localUri = :localUri LIMIT 1"
    )
    abstract fun getBindingForLocalUri(
        accountId: String,
        deviceId: String,
        localUri: String,
    ): CloudFolderBindingUriEntity?

    @Query(
        "SELECT * FROM cloud_folder_binding_uris " +
            "WHERE accountId = :accountId AND deviceId = :deviceId ORDER BY rootId"
    )
    abstract fun getBindingUrisForDevice(accountId: String, deviceId: String): List<CloudFolderBindingUriEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertBindingUri(binding: CloudFolderBindingUriEntity)

    @Query(
        "DELETE FROM cloud_folder_binding_uris " +
            "WHERE accountId = :accountId AND rootId = :rootId AND deviceId = :deviceId"
    )
    abstract fun deleteBindingUri(accountId: String, rootId: String, deviceId: String): Int

    @Query(
        "SELECT * FROM cloud_folder_outbox_sources " +
            "WHERE accountId = :accountId AND operationId = :operationId LIMIT 1"
    )
    abstract fun getOutboxSource(accountId: String, operationId: String): CloudFolderOutboxSourceEntity?

    @Query(
        "SELECT * FROM cloud_folder_outbox_sources " +
            "WHERE accountId = :accountId ORDER BY operationId"
    )
    abstract fun getOutboxSources(accountId: String): List<CloudFolderOutboxSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertOutboxSource(source: CloudFolderOutboxSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertOutboxSources(sources: List<CloudFolderOutboxSourceEntity>)

    @Query("DELETE FROM cloud_folder_outbox_sources WHERE accountId = :accountId AND operationId = :operationId")
    abstract fun deleteOutboxSource(accountId: String, operationId: String): Int

    @Query("DELETE FROM cloud_folder_binding_uris WHERE accountId = :accountId")
    abstract fun deleteBindingUrisForAccount(accountId: String): Int

    @Query("DELETE FROM cloud_folder_outbox_sources WHERE accountId = :accountId")
    abstract fun deleteOutboxSourcesForAccount(accountId: String): Int

    @Query(
        "SELECT * FROM cloud_folder_migration_recovery " +
            "WHERE state = :state ORDER BY createdAt, legacyRootId, legacyDeviceId"
    )
    abstract fun getPendingRecovery(
        state: String = CloudFolderMigrationRecoveryEntity.STATE_PENDING,
    ): List<CloudFolderMigrationRecoveryEntity>

    @Query(
        "SELECT * FROM cloud_folder_migration_recovery " +
            "WHERE legacyRootId = :rootId AND legacyDeviceId = :deviceId LIMIT 1"
    )
    abstract fun getRecovery(rootId: String, deviceId: String): CloudFolderMigrationRecoveryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertRecovery(recovery: CloudFolderMigrationRecoveryEntity)

    @Query(
        "UPDATE cloud_folder_migration_recovery SET state = :state, " +
            "claimedAccountId = :accountId, claimedAt = :claimedAt " +
            "WHERE legacyRootId = :rootId AND legacyDeviceId = :deviceId AND state = :pendingState"
    )
    abstract fun claimRecovery(
        rootId: String,
        deviceId: String,
        accountId: String,
        claimedAt: Long,
        state: String = CloudFolderMigrationRecoveryEntity.STATE_CLAIMED,
        pendingState: String = CloudFolderMigrationRecoveryEntity.STATE_PENDING,
    ): Int

    @Query(
        "UPDATE cloud_folder_migration_recovery SET state = :state, claimedAt = :dismissedAt " +
            "WHERE legacyRootId = :rootId AND legacyDeviceId = :deviceId AND state = :pendingState"
    )
    abstract fun dismissRecovery(
        rootId: String,
        deviceId: String,
        dismissedAt: Long,
        state: String = CloudFolderMigrationRecoveryEntity.STATE_DISMISSED,
        pendingState: String = CloudFolderMigrationRecoveryEntity.STATE_PENDING,
    ): Int

    @Query(
        "SELECT * FROM cloud_folder_binding_uris " +
            "WHERE accountId = :accountId AND rootId = :rootId ORDER BY deviceId"
    )
    abstract fun getBindingUrisForRoot(accountId: String, rootId: String): List<CloudFolderBindingUriEntity>

    @Query(
        "SELECT * FROM cloud_folder_outbox_sources " +
            "WHERE accountId = :accountId ORDER BY operationId"
    )
    abstract fun getAllOutboxSources(accountId: String): List<CloudFolderOutboxSourceEntity>

    @Transaction
    open fun clearAccount(accountId: String) {
        deleteBindingUrisForAccount(accountId)
        deleteOutboxSourcesForAccount(accountId)
    }
}

@Database(
    entities = [
        CloudFolderBindingUriEntity::class,
        CloudFolderOutboxSourceEntity::class,
        CloudFolderMigrationRecoveryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CloudFolderPrivateDatabase : RoomDatabase() {
    abstract fun cloudFolderPrivateDao(): CloudFolderPrivateDao

    companion object {
        internal const val DATABASE_FILE_NAME = "cloud_folder_private.db"

        @Volatile
        private var INSTANCE: CloudFolderPrivateDatabase? = null

        fun getDatabase(context: Context): CloudFolderPrivateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloudFolderPrivateDatabase::class.java,
                    File(context.applicationContext.noBackupFilesDir, DATABASE_FILE_NAME).absolutePath,
                ).build().also { INSTANCE = it }
            }
        }
    }
}

internal fun CloudFolderMigrationRecoveryEntity.toModel(): CloudFolderMigrationRecovery =
    CloudFolderMigrationRecovery(
        legacyRootId = legacyRootId,
        legacyDeviceId = legacyDeviceId,
        displayName = displayName,
        localUri = localUri,
        manifestRevision = manifestRevision,
        createdAt = createdAt,
        state = state,
        claimedAccountId = claimedAccountId,
        claimedAt = claimedAt,
    )

/**
 * Copies URI-bearing values out of the pre-32 public database before the
 * 31 -> 32 Room migration removes those columns.  It is intentionally a
 * best-effort, idempotent bridge: a failed copy must not cause the public
 * database migration to be skipped, and all imported values are protected by
 * the no-backup database on the next attempt.
 */
internal object CloudFolderPrivateStateMigrator {
    fun importLegacyState(context: Context) {
        val databaseFile = context.applicationContext.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (!databaseFile.isFile) return

        val legacy = runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        }.getOrElse { error ->
            Timber.e(error, "Unable to open legacy Reader database for cloud-folder URI migration")
            return
        }
        try {
            if (!hasTable(legacy, "cloud_folder_bindings") ||
                !hasTable(legacy, "cloud_folder_outbox")
            ) {
                return
            }
            val privateDatabase = CloudFolderPrivateDatabase.getDatabase(context)
            val target = privateDatabase.openHelper.writableDatabase
            target.beginTransaction()
            try {
                val roots = readRoots(legacy)
                copyBindings(legacy, target, roots)
                copyOutboxSources(legacy, target)
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        } catch (error: Exception) {
            // Keep the migration idempotent.  AppDatabase can still migrate;
            // the bridge will retry when the next process opens the database.
            Timber.e(error, "Unable to migrate cloud-folder private state")
        } finally {
            legacy.close()
        }
    }

    private fun copyBindings(
        legacy: SQLiteDatabase,
        target: androidx.sqlite.db.SupportSQLiteDatabase,
        roots: Map<String, LegacyRootInfo>,
    ) {
        val columns = tableColumns(legacy, "cloud_folder_bindings")
        val accountExpression = if ("accountId" in columns) "accountId" else "'' AS accountId"
        legacy.rawQuery(
            "SELECT $accountExpression, rootId, deviceId, localUri FROM cloud_folder_bindings",
            null,
        ).use { cursor ->
            val accountIndex = cursor.getColumnIndexOrThrow("accountId")
            val rootIndex = cursor.getColumnIndexOrThrow("rootId")
            val deviceIndex = cursor.getColumnIndexOrThrow("deviceId")
            val uriIndex = cursor.getColumnIndexOrThrow("localUri")
            while (cursor.moveToNext()) {
                val accountId = cursor.getString(accountIndex)?.trim().orEmpty()
                val rootId = cursor.getString(rootIndex)?.trim().orEmpty()
                val deviceId = cursor.getString(deviceIndex)?.trim().orEmpty()
                if (rootId.isBlank() || deviceId.isBlank()) continue
                val localUri = cursor.getString(uriIndex)?.trim()?.takeIf { it.isNotBlank() }
                if (accountId.isBlank()) {
                    val root = roots[rootId]
                    target.execSQL(
                        "INSERT OR REPLACE INTO cloud_folder_migration_recovery " +
                            "(legacyRootId, legacyDeviceId, displayName, localUri, manifestRevision, createdAt) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            rootId,
                            deviceId,
                            root?.name?.ifBlank { rootId } ?: rootId,
                            localUri,
                            root?.manifestRevision ?: 0L,
                            root?.updatedAt ?: System.currentTimeMillis(),
                        ),
                    )
                } else if (localUri != null) {
                    target.execSQL(
                        "INSERT OR REPLACE INTO cloud_folder_binding_uris " +
                            "(accountId, rootId, deviceId, localUri) VALUES (?, ?, ?, ?)",
                        arrayOf(accountId, rootId, deviceId, localUri),
                    )
                }
            }
        }
    }

    private fun copyOutboxSources(
        legacy: SQLiteDatabase,
        target: androidx.sqlite.db.SupportSQLiteDatabase,
    ) {
        val columns = tableColumns(legacy, "cloud_folder_outbox")
        if ("sourceUri" !in columns) return
        val accountExpression = if ("accountId" in columns) "accountId" else "'' AS accountId"
        legacy.rawQuery(
            "SELECT $accountExpression, operationId, sourceUri FROM cloud_folder_outbox " +
                "WHERE sourceUri IS NOT NULL",
            null,
        ).use { cursor ->
            val accountIndex = cursor.getColumnIndexOrThrow("accountId")
            val operationIndex = cursor.getColumnIndexOrThrow("operationId")
            val uriIndex = cursor.getColumnIndexOrThrow("sourceUri")
            while (cursor.moveToNext()) {
                val accountId = cursor.getString(accountIndex)?.trim().orEmpty()
                val operationId = cursor.getString(operationIndex)?.trim().orEmpty()
                val sourceUri = cursor.getString(uriIndex)?.trim()?.takeIf { it.isNotBlank() }
                if (operationId.isBlank() || sourceUri == null) continue
                target.execSQL(
                    "INSERT OR REPLACE INTO cloud_folder_outbox_sources " +
                        "(accountId, operationId, sourceUri) VALUES (?, ?, ?)",
                    arrayOf(accountId, operationId, sourceUri),
                )
            }
        }
    }

    private fun readRoots(legacy: SQLiteDatabase): Map<String, LegacyRootInfo> {
        if (!hasTable(legacy, "cloud_folder_roots")) return emptyMap()
        return legacy.rawQuery(
            "SELECT rootId, name, manifestRevision, updatedAt FROM cloud_folder_roots",
            null,
        ).use { cursor ->
            buildMap {
                val rootIndex = cursor.getColumnIndexOrThrow("rootId")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val revisionIndex = cursor.getColumnIndexOrThrow("manifestRevision")
                val updatedIndex = cursor.getColumnIndexOrThrow("updatedAt")
                while (cursor.moveToNext()) {
                    val rootId = cursor.getString(rootIndex)?.trim().orEmpty()
                    if (rootId.isNotBlank()) {
                        put(
                            rootId,
                            LegacyRootInfo(
                                name = cursor.getString(nameIndex).orEmpty(),
                                manifestRevision = cursor.getLong(revisionIndex),
                                updatedAt = cursor.getLong(updatedIndex),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun hasTable(database: SQLiteDatabase, tableName: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(tableName),
        ).use { it.moveToFirst() }

    private fun tableColumns(database: SQLiteDatabase, tableName: String): Set<String> =
        database.rawQuery("PRAGMA table_info(`$tableName`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private data class LegacyRootInfo(
        val name: String,
        val manifestRevision: Long,
        val updatedAt: Long,
    )
}
