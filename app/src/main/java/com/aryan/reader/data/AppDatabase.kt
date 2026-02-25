/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecentFileEntity::class, CustomFontEntity::class], version = 12, exportSchema = false)
@TypeConverters(FileTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun customFontDao(): CustomFontDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reader_database"
                )
                    // 4. Add migration to builder
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}