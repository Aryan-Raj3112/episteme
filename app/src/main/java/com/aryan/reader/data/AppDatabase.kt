/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aryan.reader.audiobook.BookTtsListeningProgressDao
import com.aryan.reader.audiobook.BookTtsListeningProgressEntity

@Database(
    entities =[
        RecentFileEntity::class,
        CustomFontEntity::class,
        ShelfEntity::class,
        BookShelfCrossRef::class,
        TagEntity::class,
        BookTagCrossRef::class,
        AudiobookEntity::class,
        BookTtsListeningProgressEntity::class,
        PendingFolderAnnotationExportEntity::class,
        CloudFolderRootEntity::class,
        CloudFolderDeviceBindingEntity::class,
        CloudFolderNodeEntity::class,
        CloudFolderTombstoneEntity::class,
        CloudFolderOutboxEntity::class,
        CloudFolderConflictEntity::class,
        CloudFolderPendingMaterializationEntity::class,
    ],
    version = 34,
    exportSchema = false
)
@TypeConverters(FileTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun customFontDao(): CustomFontDao
    abstract fun shelfDao(): ShelfDao
    abstract fun tagDao(): TagDao
    abstract fun audiobookDao(): AudiobookDao
    abstract fun bookTtsListeningProgressDao(): BookTtsListeningProgressDao
    abstract fun pendingFolderAnnotationExportDao(): PendingFolderAnnotationExportDao
    abstract fun cloudFolderSyncDao(): CloudFolderSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN lastChapterIndex INTEGER")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN lastScrollYPosition INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN lastPositionCfi TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN progressPercentage REAL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN isRecent INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE recent_files_new (
                        bookId TEXT NOT NULL PRIMARY KEY,
                        uriString TEXT,
                        type TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        coverImagePath TEXT,
                        title TEXT,
                        author TEXT,
                        lastChapterIndex INTEGER,
                        lastScrollYPosition INTEGER,
                        lastPage INTEGER,
                        lastPositionCfi TEXT,
                        progressPercentage REAL,
                        isRecent INTEGER NOT NULL DEFAULT 1,
                        isAvailable INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("""
                    INSERT INTO recent_files_new (bookId, uriString, type, displayName, timestamp, coverImagePath, title, author, lastChapterIndex, lastScrollYPosition, lastPositionCfi, progressPercentage, isRecent)
                    SELECT uriString, uriString, type, displayName, timestamp, coverImagePath, title, author, lastChapterIndex, lastScrollYPosition, lastPositionCfi, progressPercentage, isRecent FROM recent_files
                """)
                db.execSQL("DROP TABLE recent_files")
                db.execSQL("ALTER TABLE recent_files_new RENAME TO recent_files")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN lastModifiedTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN locatorBlockIndex INTEGER")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN locatorCharOffset INTEGER")
                db.execSQL("""
                    CREATE TABLE recent_files_new (
                        bookId TEXT NOT NULL PRIMARY KEY, uriString TEXT, type TEXT NOT NULL,
                        displayName TEXT NOT NULL, timestamp INTEGER NOT NULL, coverImagePath TEXT,
                        title TEXT, author TEXT, lastChapterIndex INTEGER, lastPage INTEGER,
                        lastPositionCfi TEXT, progressPercentage REAL,
                        isRecent INTEGER NOT NULL DEFAULT 1,
                        isAvailable INTEGER NOT NULL DEFAULT 1,
                        lastModifiedTimestamp INTEGER NOT NULL DEFAULT 0,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        locatorBlockIndex INTEGER, locatorCharOffset INTEGER
                    )
                """)
                db.execSQL("""
                    INSERT INTO recent_files_new (
                        bookId, uriString, type, displayName, timestamp, coverImagePath, title, author,
                        lastChapterIndex, lastPage, lastPositionCfi, progressPercentage, isRecent,
                        isAvailable, lastModifiedTimestamp, isDeleted, locatorBlockIndex, locatorCharOffset
                    )
                    SELECT
                        bookId, uriString, type, displayName, timestamp, coverImagePath, title, author,
                        lastChapterIndex, lastPage, lastPositionCfi, progressPercentage, isRecent,
                        isAvailable, lastModifiedTimestamp, isDeleted, locatorBlockIndex, locatorCharOffset
                    FROM recent_files
                """)
                db.execSQL("DROP TABLE recent_files")
                db.execSQL("ALTER TABLE recent_files_new RENAME TO recent_files")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN bookmarks TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN sourceFolderUri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_fonts` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileExtension` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN isReflowPreferred INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN customName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN highlights TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN seriesName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN seriesIndex REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN description TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shelves` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `isSmart` INTEGER NOT NULL, 
                        `smartRulesJson` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER, 
                        `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `book_shelf_cross_ref` (
                        `bookId` TEXT NOT NULL, `shelfId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`bookId`, `shelfId`),
                        FOREIGN KEY(`bookId`) REFERENCES `recent_files`(`bookId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`shelfId`) REFERENCES `shelves`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_shelf_cross_ref_shelfId` ON `book_shelf_cross_ref` (`shelfId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_shelf_cross_ref_bookId` ON `book_shelf_cross_ref` (`bookId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `book_tag_cross_ref` (
                        `bookId` TEXT NOT NULL, `tagId` TEXT NOT NULL, 
                        PRIMARY KEY(`bookId`, `tagId`),
                        FOREIGN KEY(`bookId`) REFERENCES `recent_files`(`bookId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_cross_ref_tagId` ON `book_tag_cross_ref` (`tagId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_cross_ref_bookId` ON `book_tag_cross_ref` (`bookId`)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN folderTextMetadataParsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN folderCoverMetadataParsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN originalTitle TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN originalAuthor TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN originalSeriesName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN originalSeriesIndex REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE recent_files ADD COLUMN originalDescription TEXT DEFAULT NULL")
                db.execSQL("""
                    UPDATE recent_files
                    SET
                        originalTitle = title,
                        originalAuthor = author,
                        originalSeriesName = seriesName,
                        originalSeriesIndex = seriesIndex,
                        originalDescription = description
                """)
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN fileContentModifiedTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN readingPositionModifiedTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    UPDATE recent_files
                    SET readingPositionModifiedTimestamp = lastModifiedTimestamp
                    WHERE lastModifiedTimestamp > 0
                    AND (
                        lastChapterIndex IS NOT NULL OR
                        lastPage IS NOT NULL OR
                        lastPositionCfi IS NOT NULL OR
                        locatorBlockIndex IS NOT NULL OR
                        locatorCharOffset IS NOT NULL OR
                        COALESCE(progressPercentage, 0) > 0
                    )
                """)
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audiobooks` (
                        `bookId` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `format` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT,
                        `album` TEXT,
                        `narrator` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `coverPath` TEXT,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`),
                        FOREIGN KEY(`bookId`) REFERENCES `recent_files`(`bookId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recent_files ADD COLUMN dateAddedTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE recent_files SET dateAddedTimestamp = timestamp")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `book_tts_listening_progress` (
                        `bookId` TEXT NOT NULL,
                        `chapterIndex` INTEGER NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `sourceCfi` TEXT,
                        `sourceOffset` INTEGER NOT NULL,
                        `progressPercent` REAL NOT NULL,
                        `speechRate` REAL NOT NULL,
                        `pitch` REAL NOT NULL,
                        `voiceId` TEXT,
                        `completed` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`),
                        FOREIGN KEY(`bookId`) REFERENCES `recent_files`(`bookId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_folder_annotation_exports` (
                        `bookId` TEXT NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `dirtySince` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        PRIMARY KEY(`bookId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_roots` (
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
                        PRIMARY KEY(`rootId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_bindings` (
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_bindings_rootId` ON `cloud_folder_bindings` (`rootId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_bindings_deviceId` ON `cloud_folder_bindings` (`deviceId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_nodes` (
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_nodes_rootId` ON `cloud_folder_nodes` (`rootId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_nodes_rootId_relativePath` ON `cloud_folder_nodes` (`rootId`, `relativePath`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_tombstones` (
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_tombstones_rootId` ON `cloud_folder_tombstones` (`rootId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_tombstones_rootId_relativePath` ON `cloud_folder_tombstones` (`rootId`, `relativePath`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_outbox` (
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
                        PRIMARY KEY(`operationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_outbox_rootId_state_nextAttemptAt` ON `cloud_folder_outbox` (`rootId`, `state`, `nextAttemptAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_folder_outbox_rootId_nodeId` ON `cloud_folder_outbox` (`rootId`, `nodeId`)")
            }
        }

        /**
         * Account scope was absent from the initial cloud-folder schema.  The
         * old rows are deliberately quarantined under an empty account ID:
         * there is no safe way to infer which authenticated account owned them
         * during an upgrade, so they must never be processed automatically.
         */
        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_roots_v30` (
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
                    INSERT INTO `cloud_folder_roots_v30` (
                        `accountId`, `rootId`, `name`, `createdAt`, `createdByDeviceId`,
                        `updatedAt`, `manifestRevision`, `fileCount`, `directoryCount`,
                        `totalBytes`, `scannedAt`, `scanComplete`, `isDeleted`
                    )
                    SELECT '', `rootId`, `name`, `createdAt`, `createdByDeviceId`,
                        `updatedAt`, `manifestRevision`, `fileCount`, `directoryCount`,
                        `totalBytes`, `scannedAt`, `scanComplete`, `isDeleted`
                    FROM `cloud_folder_roots`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_bindings_v30` (
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
                // Keep at most one pre-30 binding for each device/local URI.
                // The new unique index covers account + device + URI, and
                // INSERT OR IGNORE alone would not collapse rows whose roots
                // differ (their composite primary keys are different).
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `cloud_folder_bindings_v30` (
                        `accountId`, `rootId`, `deviceId`, `localUri`, `permissionState`,
                        `materializationMode`, `lastAcknowledgedRevision`, `lastScanAt`, `lastError`
                    )
                    SELECT '', `rootId`, `deviceId`, `localUri`, `permissionState`,
                        `materializationMode`, `lastAcknowledgedRevision`, `lastScanAt`, `lastError`
                    FROM `cloud_folder_bindings`
                    WHERE `localUri` IS NULL
                       OR `rowid` = (
                           SELECT MIN(`candidate`.`rowid`)
                           FROM `cloud_folder_bindings` AS `candidate`
                           WHERE `candidate`.`deviceId` = `cloud_folder_bindings`.`deviceId`
                             AND `candidate`.`localUri` = `cloud_folder_bindings`.`localUri`
                       )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_nodes_v30` (
                        `accountId` TEXT NOT NULL,
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
                        PRIMARY KEY(`accountId`, `rootId`, `nodeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cloud_folder_nodes_v30` (
                        `accountId`, `rootId`, `nodeId`, `relativePath`, `kind`, `contentHash`,
                        `sizeBytes`, `mimeType`, `fileModifiedAt`, `revision`, `modifiedAt`,
                        `modifiedByDeviceId`, `contentObjectId`
                    )
                    SELECT '', `rootId`, `nodeId`, `relativePath`, `kind`, `contentHash`,
                        `sizeBytes`, `mimeType`, `fileModifiedAt`, `revision`, `modifiedAt`,
                        `modifiedByDeviceId`, `contentObjectId`
                    FROM `cloud_folder_nodes`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_tombstones_v30` (
                        `accountId` TEXT NOT NULL,
                        `rootId` TEXT NOT NULL,
                        `nodeId` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        `deletedRevision` INTEGER NOT NULL,
                        `deletedByDeviceId` TEXT NOT NULL,
                        `lastKnownContentHash` TEXT,
                        `lastKnownSizeBytes` INTEGER NOT NULL,
                        PRIMARY KEY(`accountId`, `rootId`, `nodeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cloud_folder_tombstones_v30` (
                        `accountId`, `rootId`, `nodeId`, `relativePath`, `kind`, `deletedAt`,
                        `deletedRevision`, `deletedByDeviceId`, `lastKnownContentHash`,
                        `lastKnownSizeBytes`
                    )
                    SELECT '', `rootId`, `nodeId`, `relativePath`, `kind`, `deletedAt`,
                        `deletedRevision`, `deletedByDeviceId`, `lastKnownContentHash`,
                        `lastKnownSizeBytes`
                    FROM `cloud_folder_tombstones`
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_outbox_v30` (
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
                        PRIMARY KEY(`accountId`, `operationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cloud_folder_outbox_v30` (
                        `accountId`, `operationId`, `rootId`, `nodeId`, `operationKind`, `direction`,
                        `relativePath`, `previousRelativePath`, `contentHash`, `sizeBytes`, `revision`,
                        `state`, `attempts`, `nextAttemptAt`, `lastAttemptAt`, `lastError`
                    )
                    SELECT '', `operationId`, `rootId`, `nodeId`, `operationKind`, `direction`,
                        `relativePath`, `previousRelativePath`, `contentHash`, `sizeBytes`, `revision`,
                        `state`, `attempts`, `nextAttemptAt`, `lastAttemptAt`, `lastError`
                    FROM `cloud_folder_outbox`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `cloud_folder_roots`")
                db.execSQL("ALTER TABLE `cloud_folder_roots_v30` RENAME TO `cloud_folder_roots`")
                db.execSQL("DROP TABLE `cloud_folder_bindings`")
                db.execSQL("ALTER TABLE `cloud_folder_bindings_v30` RENAME TO `cloud_folder_bindings`")
                db.execSQL("DROP TABLE `cloud_folder_nodes`")
                db.execSQL("ALTER TABLE `cloud_folder_nodes_v30` RENAME TO `cloud_folder_nodes`")
                db.execSQL("DROP TABLE `cloud_folder_tombstones`")
                db.execSQL("ALTER TABLE `cloud_folder_tombstones_v30` RENAME TO `cloud_folder_tombstones`")
                db.execSQL("DROP TABLE `cloud_folder_outbox`")
                db.execSQL("ALTER TABLE `cloud_folder_outbox_v30` RENAME TO `cloud_folder_outbox`")

                db.execSQL("CREATE INDEX `index_cloud_folder_bindings_accountId_rootId` ON `cloud_folder_bindings` (`accountId`, `rootId`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_bindings_accountId_deviceId` ON `cloud_folder_bindings` (`accountId`, `deviceId`)")
                db.execSQL("CREATE UNIQUE INDEX `index_cloud_folder_bindings_accountId_deviceId_localUri` ON `cloud_folder_bindings` (`accountId`, `deviceId`, `localUri`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_nodes_accountId_rootId` ON `cloud_folder_nodes` (`accountId`, `rootId`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_nodes_accountId_rootId_relativePath` ON `cloud_folder_nodes` (`accountId`, `rootId`, `relativePath`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_tombstones_accountId_rootId` ON `cloud_folder_tombstones` (`accountId`, `rootId`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_tombstones_accountId_rootId_relativePath` ON `cloud_folder_tombstones` (`accountId`, `rootId`, `relativePath`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_outbox_accountId_rootId_state_nextAttemptAt` ON `cloud_folder_outbox` (`accountId`, `rootId`, `state`, `nextAttemptAt`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_outbox_accountId_rootId_nodeId` ON `cloud_folder_outbox` (`accountId`, `rootId`, `nodeId`)")
            }
        }

        /** Persist the SAF source locator with each durable upload operation. */
        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cloud_folder_outbox ADD COLUMN sourceUri TEXT DEFAULT NULL")
            }
        }

        /**
         * SAF locators are installation capabilities and must not live in the
         * database included by Android backup/device transfer.  The private
         * sidecar imports the old values before this migration runs; this
         * migration then removes the URI columns from the backed-up schema.
         */
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_bindings_v32` (
                        `accountId` TEXT NOT NULL,
                        `rootId` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
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
                    INSERT INTO `cloud_folder_bindings_v32` (
                        `accountId`, `rootId`, `deviceId`, `permissionState`,
                        `materializationMode`, `lastAcknowledgedRevision`, `lastScanAt`, `lastError`
                    )
                    SELECT `accountId`, `rootId`, `deviceId`, `permissionState`,
                        `materializationMode`, `lastAcknowledgedRevision`, `lastScanAt`, `lastError`
                    FROM `cloud_folder_bindings`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `cloud_folder_bindings`")
                db.execSQL("ALTER TABLE `cloud_folder_bindings_v32` RENAME TO `cloud_folder_bindings`")
                db.execSQL("CREATE INDEX `index_cloud_folder_bindings_accountId_rootId` ON `cloud_folder_bindings` (`accountId`, `rootId`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_bindings_accountId_deviceId` ON `cloud_folder_bindings` (`accountId`, `deviceId`)")

                db.execSQL(
                    """
                    CREATE TABLE `cloud_folder_outbox_v32` (
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
                        PRIMARY KEY(`accountId`, `operationId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cloud_folder_outbox_v32` (
                        `accountId`, `operationId`, `rootId`, `nodeId`, `operationKind`, `direction`,
                        `relativePath`, `previousRelativePath`, `contentHash`, `sizeBytes`, `revision`,
                        `state`, `attempts`, `nextAttemptAt`, `lastAttemptAt`, `lastError`
                    )
                    SELECT `accountId`, `operationId`, `rootId`, `nodeId`, `operationKind`, `direction`,
                        `relativePath`, `previousRelativePath`, `contentHash`, `sizeBytes`, `revision`,
                        `state`, `attempts`, `nextAttemptAt`, `lastAttemptAt`, `lastError`
                    FROM `cloud_folder_outbox`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `cloud_folder_outbox`")
                db.execSQL("ALTER TABLE `cloud_folder_outbox_v32` RENAME TO `cloud_folder_outbox`")
                db.execSQL("CREATE INDEX `index_cloud_folder_outbox_accountId_rootId_state_nextAttemptAt` ON `cloud_folder_outbox` (`accountId`, `rootId`, `state`, `nextAttemptAt`)")
                db.execSQL("CREATE INDEX `index_cloud_folder_outbox_accountId_rootId_nodeId` ON `cloud_folder_outbox` (`accountId`, `rootId`, `nodeId`)")
            }
        }

        /** Persist conflict-copy source identity and user conflict choices. */
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cloud_folder_outbox ADD COLUMN sourceNodeId TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_conflicts` (
                        `accountId` TEXT NOT NULL,
                        `rootId` TEXT NOT NULL,
                        `conflictId` TEXT NOT NULL,
                        `conflictJson` TEXT NOT NULL,
                        `baseRevision` INTEGER NOT NULL,
                        `localRevision` INTEGER NOT NULL,
                        `remoteRevision` INTEGER NOT NULL,
                        `resolution` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`accountId`, `rootId`, `conflictId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cloud_folder_conflicts_accountId_rootId_resolution` " +
                        "ON `cloud_folder_conflicts` (`accountId`, `rootId`, `resolution`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cloud_folder_conflicts_accountId_updatedAt` " +
                        "ON `cloud_folder_conflicts` (`accountId`, `updatedAt`)"
                )
            }
        }

        /** Persist manifests waiting for complete local materialization. */
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloud_folder_pending_materializations` (
                        `accountId` TEXT NOT NULL,
                        `rootId` TEXT NOT NULL,
                        `manifestJson` TEXT NOT NULL,
                        `targetRevision` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`accountId`, `rootId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cloud_folder_pending_materializations_accountId_updatedAt` " +
                        "ON `cloud_folder_pending_materializations` (`accountId`, `updatedAt`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                CloudFolderPrivateStateMigrator.importLegacyState(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                        MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
                        MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
                        MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32,
                        MIGRATION_32_33, MIGRATION_33_34
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        const val DATABASE_NAME = "reader_database"
    }
}
