package com.aryan.reader.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import timber.log.Timber

/**
 * The private-state bridge runs on every process start, before Room opens the
 * public database.  It therefore has to tolerate public databases that were
 * already upgraded past the version that removed the URI columns from the
 * backed-up schema.
 */
@RunWith(RobolectricTestRunner::class)
class CloudFolderPrivateStateMigratorTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val publicDatabaseName = AppDatabase.DATABASE_NAME
    private val errors = mutableListOf<String>()

    @Before
    fun setUp() {
        context.deleteDatabase(publicDatabaseName)
        privateDatabaseFile().delete()
        Timber.plant(
            object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= android.util.Log.ERROR) errors += message
                }
            }
        )
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        context.deleteDatabase(publicDatabaseName)
        privateDatabaseFile().delete()
    }

    @Test
    fun `imports URI values from a public database that still carries them`() = runTest {
        writeLegacyPublicDatabase()

        CloudFolderPrivateStateMigrator.importLegacyState(context)

        val dao = CloudFolderPrivateDatabase.getDatabase(context).cloudFolderPrivateDao()
        assertEquals(
            "content://tree/scoped",
            dao.getBindingUri("account-1", "root-1", "device-1")?.localUri,
        )
        assertEquals(
            listOf("content://doc/operation-1"),
            dao.getOutboxSources("account-1").map { it.sourceUri },
        )

        // Rows that predate account scoping cannot be assigned to an account,
        // so they are quarantined for an explicit rebind instead.
        assertNull(dao.getBindingUri("", "legacy-root", "legacy-device"))
        val recovery = dao.getPendingRecovery().single()
        assertEquals("legacy-root", recovery.legacyRootId)
        assertEquals("Legacy", recovery.displayName)
        assertEquals("content://tree/legacy", recovery.localUri)
    }

    @Test
    fun `skips a public database that already dropped the URI columns`() = runTest {
        createCurrentPublicDatabase()

        CloudFolderPrivateStateMigrator.importLegacyState(context)

        // The removed columns make the legacy query throw.  The bridge catches
        // it, so the only observable symptom is the error it reports on every
        // process start for every already upgraded install.
        assertTrue(errors.isEmpty())
    }

    /**
     * Creates the public database with the current schema, i.e. the state an
     * already upgraded install is in when the app restarts.
     */
    private fun createCurrentPublicDatabase() {
        Room.databaseBuilder(context, AppDatabase::class.java, publicDatabaseName)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase }
            .close()
    }

    /**
     * Creates the current public schema and then intentionally downgrades it to
     * the last version that still owned `localUri` and `sourceUri`.
     */
    private fun writeLegacyPublicDatabase() {
        createCurrentPublicDatabase()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration(
                context = context,
                name = publicDatabaseName,
                callback = LegacyPublicSchema(),
            )
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun privateDatabaseFile(): File =
        File(context.applicationContext.noBackupFilesDir, CloudFolderPrivateDatabase.DATABASE_FILE_NAME)

    private class LegacyPublicSchema : SupportSQLiteOpenHelper.Callback(PRE_URI_REMOVAL_VERSION) {
        override fun onCreate(db: SupportSQLiteDatabase) = error("legacy database must already exist")

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            error("legacy database must be downgraded, not upgraded")

        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            listOf("cloud_folder_roots", "cloud_folder_bindings", "cloud_folder_outbox")
                .forEach { table -> db.execSQL("DROP TABLE IF EXISTS `$table`") }
            db.execSQL(
                """
                CREATE TABLE `cloud_folder_roots` (
                    `accountId` TEXT NOT NULL,
                    `rootId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `createdByDeviceId` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `manifestRevision` INTEGER NOT NULL,
                    `fileCount` INTEGER NOT NULL,
                    `directoryCount` INTEGER NOT NULL,
                    `totalBytes` INTEGER NOT NULL,
                    `scannedAt` INTEGER NOT NULL,
                    `scanComplete` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`accountId`, `rootId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE `cloud_folder_bindings` (
                    `accountId` TEXT NOT NULL,
                    `rootId` TEXT NOT NULL,
                    `deviceId` TEXT NOT NULL,
                    `localUri` TEXT,
                    `permissionState` TEXT NOT NULL,
                    `materializationMode` TEXT NOT NULL,
                    `lastAcknowledgedRevision` INTEGER NOT NULL,
                    `lastScanAt` INTEGER NOT NULL,
                    `lastError` TEXT,
                    PRIMARY KEY(`accountId`, `rootId`, `deviceId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE `cloud_folder_outbox` (
                    `accountId` TEXT NOT NULL,
                    `operationId` TEXT NOT NULL,
                    `rootId` TEXT NOT NULL,
                    `nodeId` TEXT NOT NULL,
                    `operationKind` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `relativePath` TEXT NOT NULL,
                    `previousRelativePath` TEXT,
                    `contentHash` TEXT,
                    `sizeBytes` INTEGER NOT NULL,
                    `revision` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `nextAttemptAt` INTEGER NOT NULL,
                    `lastAttemptAt` INTEGER NOT NULL,
                    `lastError` TEXT,
                    `sourceUri` TEXT,
                    PRIMARY KEY(`accountId`, `operationId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO cloud_folder_roots " +
                    "(accountId, rootId, name, createdAt, createdByDeviceId, updatedAt, " +
                    "manifestRevision, fileCount, directoryCount, totalBytes, scannedAt, " +
                    "scanComplete, isDeleted) " +
                    "VALUES ('account-1', 'root-1', 'One', 1, 'device-1', 1, 7, 1, 0, 10, 1, 1, 0)"
            )
            db.execSQL(
                "INSERT INTO cloud_folder_roots " +
                    "(accountId, rootId, name, createdAt, createdByDeviceId, updatedAt, " +
                    "manifestRevision, fileCount, directoryCount, totalBytes, scannedAt, " +
                    "scanComplete, isDeleted) " +
                    "VALUES ('', 'legacy-root', 'Legacy', 1, 'legacy-device', 1, 3, 1, 0, 20, 1, 1, 0)"
            )
            db.execSQL(
                "INSERT INTO cloud_folder_bindings " +
                    "(accountId, rootId, deviceId, localUri, permissionState, materializationMode, " +
                    "lastAcknowledgedRevision, lastScanAt, lastError) " +
                    "VALUES ('account-1', 'root-1', 'device-1', 'content://tree/scoped', 'GRANTED', " +
                    "'LOCAL_MIRROR', 0, 0, NULL)"
            )
            db.execSQL(
                "INSERT INTO cloud_folder_bindings " +
                    "(accountId, rootId, deviceId, localUri, permissionState, materializationMode, " +
                    "lastAcknowledgedRevision, lastScanAt, lastError) " +
                    "VALUES ('', 'legacy-root', 'legacy-device', 'content://tree/legacy', 'UNKNOWN', " +
                    "'CLOUD_ONLY', 0, 0, NULL)"
            )
            db.execSQL(
                "INSERT INTO cloud_folder_outbox " +
                    "(accountId, operationId, rootId, nodeId, operationKind, direction, relativePath, " +
                    "previousRelativePath, contentHash, sizeBytes, revision, state, attempts, " +
                    "nextAttemptAt, lastAttemptAt, lastError, sourceUri) " +
                    "VALUES ('account-1', 'operation-1', 'root-1', 'book', 'UPLOAD_FILE', " +
                    "'LOCAL_TO_CLOUD', 'Book.epub', NULL, NULL, 10, 1, 'PENDING', 0, 0, 0, NULL, " +
                    "'content://doc/operation-1')"
            )
        }

        private companion object {
            /** The last public schema version that owned the URI columns. */
            const val PRE_URI_REMOVAL_VERSION = 31
        }
    }
}
