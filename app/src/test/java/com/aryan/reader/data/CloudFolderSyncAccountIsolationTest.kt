package com.aryan.reader.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.aryan.reader.CloudFolderSyncPrefs
import com.aryan.reader.shared.CloudFolderDeviceBinding
import com.aryan.reader.shared.CloudFolderConflictResolution
import com.aryan.reader.shared.CloudFolderManifest
import com.aryan.reader.shared.CloudFolderMaterializationMode
import com.aryan.reader.shared.CloudFolderNode
import com.aryan.reader.shared.CloudFolderNodeKind
import com.aryan.reader.shared.CloudFolderPermissionState
import com.aryan.reader.shared.CloudFolderRoot
import com.aryan.reader.shared.CloudFolderSyncDirection
import com.aryan.reader.shared.CloudFolderSyncOperation
import com.aryan.reader.shared.CloudFolderSyncOperationKind
import com.aryan.reader.shared.CloudFolderSyncPhase
import com.aryan.reader.shared.CloudFolderSyncProgress
import com.aryan.reader.shared.CloudFolderSyncSelection
import com.aryan.reader.shared.CloudFolderSyncSelectionMode
import com.aryan.reader.shared.cloudFolderRootId
import com.aryan.reader.shared.planCloudFolderSync
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CloudFolderSyncAccountIsolationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val accountA = "cloud-folder-account-a"
    private val accountB = "cloud-folder-account-b"

    @Before
    fun setUp() {
        CloudFolderSyncPrefs.clear(context, accountA)
        CloudFolderSyncPrefs.clear(context, accountB)
    }

    @After
    fun tearDown() {
        CloudFolderSyncPrefs.clear(context, accountA)
        CloudFolderSyncPrefs.clear(context, accountB)
    }

    @Test
    fun `repository state and selection are isolated by account`() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val root = manifest("shared-root")
            val repositoryA = CloudFolderSyncRepository(
                context = context,
                accountId = accountA,
                database = database,
                deviceId = "shared-device",
            )
            val repositoryB = CloudFolderSyncRepository(
                context = context,
                accountId = accountB,
                database = database,
                deviceId = "shared-device",
            )
            val binding = CloudFolderDeviceBinding(
                rootId = root.rootId,
                deviceId = "shared-device",
                localUri = "content://tree/shared",
                permissionState = CloudFolderPermissionState.GRANTED,
                materializationMode = CloudFolderMaterializationMode.LOCAL_MIRROR,
            )
            val operation = CloudFolderSyncOperation(
                nodeId = "book",
                kind = CloudFolderSyncOperationKind.UPLOAD_FILE,
                direction = CloudFolderSyncDirection.LOCAL_TO_CLOUD,
                relativePath = "Book.epub",
                revision = 1L,
            )

            repositoryA.saveManifest(root)
            repositoryB.saveManifest(root)
            repositoryA.savePendingMaterialization(root.copy(revision = 2L), now = 42L)
            repositoryA.saveBinding(binding)
            repositoryB.saveBinding(binding)
            val operationIdA = repositoryA.enqueue(root.rootId, operation)
            val operationIdB = repositoryB.enqueue(root.rootId, operation)
            repositoryA.saveProgress(
                CloudFolderSyncProgress(
                    rootId = root.rootId,
                    phase = CloudFolderSyncPhase.UPLOADING,
                    completedFiles = 2,
                    totalFiles = 4,
                    completedBytes = 20L,
                    totalBytes = 40L,
                    updatedAt = 10L,
                )
            )
            repositoryB.saveProgress(
                CloudFolderSyncProgress(
                    rootId = root.rootId,
                    phase = CloudFolderSyncPhase.FAILED,
                    errorStatus = "network",
                    updatedAt = 11L,
                )
            )
            repositoryA.setSelection(
                CloudFolderSyncSelection(
                    mode = CloudFolderSyncSelectionMode.SELECTED,
                    selectedRootIds = setOf(root.rootId),
                )
            )

            assertNotNull(repositoryA.getManifest(root.rootId))
            assertNotNull(repositoryB.getManifest(root.rootId))
            assertEquals(2L, repositoryA.getPendingMaterialization(root.rootId)?.revision)
            assertNull(repositoryB.getPendingMaterialization(root.rootId))
            assertNotNull(repositoryA.getBinding(root.rootId))
            assertNotNull(repositoryB.findBindingForLocalUri(binding.localUri!!, "shared-device"))
            assertEquals(1, repositoryA.getOutbox(root.rootId).size)
            assertEquals(1, repositoryB.getOutbox(root.rootId).size)
            assertNotNull(repositoryA.getOutbox(root.rootId).single { it.operationId == operationIdA })
            assertNotNull(repositoryB.getOutbox(root.rootId).single { it.operationId == operationIdB })
            assertEquals(2, repositoryA.getProgress(root.rootId)?.completedFiles)
            assertEquals(CloudFolderSyncPhase.FAILED, repositoryB.getProgress(root.rootId)?.phase)
            assertTrue(repositoryA.selection().includes(root.rootId))
            assertFalse(repositoryB.selection().includes(root.rootId))
            assertNotEquals(operationIdA, operationIdB)

            repositoryA.clearAccountState()

            assertNull(repositoryA.getManifest(root.rootId))
            assertNull(repositoryA.getPendingMaterialization(root.rootId))
            assertNull(repositoryA.getBinding(root.rootId))
            assertTrue(repositoryA.getOutbox(root.rootId).isEmpty())
            assertNull(repositoryA.getProgress(root.rootId))
            assertEquals(CloudFolderSyncSelection.Default, repositoryA.selection())
            assertNotNull(repositoryB.getManifest(root.rootId))
            assertNull(repositoryB.getPendingMaterialization(root.rootId))
            assertNotNull(repositoryB.getBinding(root.rootId))
            assertEquals(1, repositoryB.getOutbox(root.rootId).size)
            assertEquals(CloudFolderSyncPhase.FAILED, repositoryB.getProgress(root.rootId)?.phase)
            assertFalse(repositoryB.selection().includes(root.rootId))
        } finally {
            database.close()
        }
    }

    @Test
    fun `incoming prompt state survives reload and reappears only for a newer revision`() {
        val rootId = "incoming-root"

        CloudFolderSyncPrefs.markIncomingPromptPending(context, accountA, rootId, revision = 4L)
        assertEquals(setOf(rootId), CloudFolderSyncPrefs.pendingIncomingRootIds(context, accountA))
        assertTrue(CloudFolderSyncPrefs.pendingIncomingRootIds(context, accountB).isEmpty())

        CloudFolderSyncPrefs.dismissIncomingPrompt(context, accountA, rootId, revision = 4L)
        assertTrue(CloudFolderSyncPrefs.pendingIncomingRootIds(context, accountA).isEmpty())

        // Discovery of the same manifest revision is suppressed; a newer
        // manifest revision creates a fresh actionable prompt.
        CloudFolderSyncPrefs.markIncomingPromptPending(context, accountA, rootId, revision = 4L)
        assertTrue(CloudFolderSyncPrefs.pendingIncomingRootIds(context, accountA).isEmpty())
        CloudFolderSyncPrefs.markIncomingPromptPending(context, accountA, rootId, revision = 5L)
        assertEquals(setOf(rootId), CloudFolderSyncPrefs.pendingIncomingRootIds(context, accountA))
    }

    @Test
    fun `local registration retains device persisted logical root id`() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = CloudFolderSyncRepository(
                context = context,
                accountId = accountA,
                database = database,
                deviceId = "device-a",
            )
            val binding = repository.registerLocalFolder(
                localUri = "content://tree/books",
                name = "Books",
                rootId = "logical-root-123",
            )

            assertEquals("logical-root-123", binding.rootId)
            assertNotNull(repository.getRoot("logical-root-123"))
            assertNotNull(repository.getBinding("logical-root-123"))
            assertNull(repository.getRoot(cloudFolderRootId("android-saf:content://tree/books")))
        } finally {
            database.close()
        }
    }

    @Test
    fun `conflict records and decisions are account scoped and reset for newer snapshots`() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repositoryA = CloudFolderSyncRepository(
                context = context,
                accountId = accountA,
                database = database,
                deviceId = "device-a",
            )
            val repositoryB = CloudFolderSyncRepository(
                context = context,
                accountId = accountB,
                database = database,
                deviceId = "device-b",
            )
            val original = CloudFolderNode(
                nodeId = "book",
                rootId = "conflict-root",
                relativePath = "Book.epub",
                kind = CloudFolderNodeKind.FILE,
                contentHash = "a".repeat(64),
                sizeBytes = 10L,
                revision = 1L,
            )
            val base = CloudFolderManifest(
                root = CloudFolderRoot(rootId = "conflict-root", name = "Books"),
                revision = 1L,
                nodes = listOf(original),
            )
            val local = base.copy(
                revision = 2L,
                nodes = listOf(original.copy(contentHash = "b".repeat(64), revision = 2L)),
            )
            val remote = base.copy(
                revision = 3L,
                nodes = listOf(original.copy(contentHash = "c".repeat(64), revision = 3L)),
            )
            val plan = planCloudFolderSync(base, local, remote)
            assertEquals(1, plan.conflicts.size)
            val conflictId = plan.conflicts.single().conflictId

            repositoryA.reconcileConflicts(plan, now = 10L)
            assertEquals(1, repositoryA.getConflicts("conflict-root").size)
            assertTrue(repositoryB.getConflicts("conflict-root").isEmpty())
            assertTrue(
                repositoryA.resolveConflict(
                    rootId = "conflict-root",
                    conflictId = conflictId,
                    resolution = CloudFolderConflictResolution.KEEP_LOCAL,
                    now = 11L,
                )
            )
            assertEquals(
                CloudFolderConflictResolution.KEEP_LOCAL,
                repositoryA.getConflicts("conflict-root").single().resolution,
            )
            assertTrue(repositoryB.getConflicts("conflict-root").isEmpty())

            val newerLocal = local.copy(
                revision = 4L,
                nodes = listOf(original.copy(contentHash = "d".repeat(64), revision = 4L)),
            )
            val newerPlan = planCloudFolderSync(base, newerLocal, remote)
            repositoryA.reconcileConflicts(newerPlan, now = 20L)
            assertEquals(
                CloudFolderConflictResolution.DEFER,
                repositoryA.getConflicts("conflict-root").single().resolution,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `29 to 30 migration quarantines legacy rows and deduplicates local bindings`() = runTest {
        val databaseName = "cloud-folder-account-migration-test.db"
        context.deleteDatabase(databaseName)
        val current = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        // Room opens lazily; force creation of the current schema before the
        // SQLite helper performs the intentional 31 -> 29 downgrade below.
        current.openHelper.writableDatabase
        current.close()

        val legacyHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration(
                context = context,
                name = databaseName,
                callback = LegacyCloudFolderCallback(),
            )
        )
        legacyHelper.writableDatabase
        legacyHelper.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
            )
            .allowMainThreadQueries()
            .build()
        try {
            val dao = migrated.cloudFolderSyncDao()
            assertNotNull(dao.getRoot("", "legacy-root"))
            assertNull(dao.getRoot(accountA, "legacy-root"))
            assertEquals(1, dao.getNodes("", "legacy-root").size)
            assertEquals(1, dao.getTombstones("", "legacy-root").size)
            assertEquals(1, dao.getOutbox("", "legacy-root").size)
            assertEquals(1, dao.getBindingsForDevice("", "legacy-device").size)

            // The newest migration creates the durable progress projection;
            // inserting a row here also verifies all of its columns exist.
            dao.upsertProgress(
                CloudFolderSyncProgressEntity(
                    accountId = "",
                    rootId = "legacy-root",
                    phase = CloudFolderSyncPhase.SCANNING.name,
                    completedFiles = 0,
                    totalFiles = 2,
                    completedBytes = 0L,
                    totalBytes = 20L,
                    updatedAt = 2L,
                    errorStatus = null,
                )
            )
            assertEquals(2, dao.getProgress("", "legacy-root")?.totalFiles)
            assertTrue(dao.getProgress(accountA, "legacy-root") == null)

            // Provider URIs are intentionally absent from the backup-eligible
            // database after 31 -> 32; recovery requires an explicit rebind in
            // the separate no-backup private database.
            assertNull(dao.getBindingsForDevice("", "legacy-device").single().localUri)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun manifest(rootId: String): CloudFolderManifest = CloudFolderManifest(
        root = CloudFolderRoot(
            rootId = rootId,
            name = "Shared folder",
            createdAt = 1L,
            createdByDeviceId = "device",
        ),
        revision = 1L,
        generatedAt = 1L,
        generatedByDeviceId = "device",
        nodes = listOf(
            CloudFolderNode(
                nodeId = "book",
                rootId = rootId,
                relativePath = "Book.epub",
                kind = CloudFolderNodeKind.FILE,
                sizeBytes = 10L,
                revision = 1L,
                modifiedAt = 1L,
                modifiedByDeviceId = "device",
            )
        ),
    )
}

private class LegacyCloudFolderCallback : SupportSQLiteOpenHelper.Callback(29) {
    override fun onCreate(db: SupportSQLiteDatabase) = error("legacy database must already exist")

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        error("legacy database must be downgraded from the current schema")

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf(
            "cloud_folder_roots",
            "cloud_folder_bindings",
            "cloud_folder_nodes",
            "cloud_folder_tombstones",
            "cloud_folder_outbox",
            "cloud_folder_conflicts",
            "cloud_folder_pending_materializations",
            "cloud_folder_sync_progress",
            "cloud_book_delete_intents",
        ).forEach { table -> db.execSQL("DROP TABLE IF EXISTS `$table`") }
        db.execSQL(
            """
            CREATE TABLE `cloud_folder_roots` (
                `rootId` TEXT NOT NULL PRIMARY KEY,
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
                `isDeleted` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE `cloud_folder_bindings` (
                `rootId` TEXT NOT NULL,
                `deviceId` TEXT NOT NULL,
                `localUri` TEXT,
                `permissionState` TEXT NOT NULL,
                `materializationMode` TEXT NOT NULL,
                `lastAcknowledgedRevision` INTEGER NOT NULL,
                `lastScanAt` INTEGER NOT NULL,
                `lastError` TEXT,
                PRIMARY KEY(`rootId`, `deviceId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE `cloud_folder_nodes` (
                `rootId` TEXT NOT NULL,
                `nodeId` TEXT NOT NULL,
                `relativePath` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `contentHash` TEXT,
                `sizeBytes` INTEGER NOT NULL,
                `mimeType` TEXT,
                `fileModifiedAt` INTEGER NOT NULL,
                `revision` INTEGER NOT NULL,
                `modifiedAt` INTEGER NOT NULL,
                `modifiedByDeviceId` TEXT NOT NULL,
                `contentObjectId` TEXT,
                PRIMARY KEY(`rootId`, `nodeId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE `cloud_folder_tombstones` (
                `rootId` TEXT NOT NULL,
                `nodeId` TEXT NOT NULL,
                `relativePath` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                `deletedRevision` INTEGER NOT NULL,
                `deletedByDeviceId` TEXT NOT NULL,
                `lastKnownContentHash` TEXT,
                `lastKnownSizeBytes` INTEGER NOT NULL,
                PRIMARY KEY(`rootId`, `nodeId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE `cloud_folder_outbox` (
                `operationId` TEXT NOT NULL PRIMARY KEY,
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
                `lastError` TEXT
            )
            """.trimIndent()
        )

        db.execSQL("INSERT INTO cloud_folder_roots VALUES ('legacy-root', 'Legacy', 1, 'legacy-device', 1, 1, 1, 0, 10, 1, 1, 0)")
        db.execSQL("INSERT INTO cloud_folder_bindings VALUES ('legacy-root', 'legacy-device', 'content://tree/legacy', 'UNKNOWN', 'CLOUD_ONLY', 0, 0, NULL)")
        db.execSQL("INSERT INTO cloud_folder_bindings VALUES ('another-root', 'legacy-device', 'content://tree/legacy', 'UNKNOWN', 'CLOUD_ONLY', 0, 0, NULL)")
        db.execSQL("INSERT INTO cloud_folder_nodes VALUES ('legacy-root', 'legacy-node', 'Book.epub', 'FILE', NULL, 10, 'application/epub+zip', 1, 1, 1, 'legacy-device', NULL)")
        db.execSQL("INSERT INTO cloud_folder_tombstones VALUES ('legacy-root', 'deleted-node', 'Old.epub', 'FILE', 1, 1, 'legacy-device', NULL, 5)")
        db.execSQL("INSERT INTO cloud_folder_outbox VALUES ('legacy-operation', 'legacy-root', 'legacy-node', 'UPLOAD_FILE', 'LOCAL_TO_CLOUD', 'Book.epub', NULL, NULL, 10, 1, 'PENDING', 0, 0, 0, NULL)")
    }
}
