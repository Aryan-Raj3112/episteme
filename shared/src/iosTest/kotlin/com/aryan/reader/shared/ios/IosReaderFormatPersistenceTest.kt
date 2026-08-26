package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import platform.Foundation.NSUserDefaults

class IosReaderFormatPersistenceTest {
    @Test
    fun restoringIosSnapshotNormalizesAndPersistsAndroidFormatBoundsOnce() {
        val defaults = NSUserDefaults.standardUserDefaults
        val snapshotKey = "reader_ios_library_snapshot_v1"
        val legacyKey = "reader_ios_reader_preferences_v1"
        val previousSnapshot = defaults.stringForKey(snapshotKey)
        val previousLegacy = defaults.stringForKey(legacyKey)
        val legacySettings = ReaderSettings(
            fontSize = 120,
            fontWeight = 1500,
            letterSpacing = 2f,
            lineSpacing = 0.1f,
            margin = 500,
            paragraphSpacing = -1f,
            imageScale = 4f,
            horizontalMargin = -1,
            verticalMargin = 500,
            readingMode = ReaderReadingMode.PAGINATED,
            textAlign = SharedReaderTextAlign.JUSTIFY,
        )
        val localSettings = ReaderSettings(
            fontSize = 6,
            lineSpacing = 10f,
            margin = -10,
            paragraphSpacing = 4f,
            imageScale = 0.1f,
            horizontalMargin = 200,
            verticalMargin = -20,
        )
        val rawSnapshot = SharedLibrarySnapshot(
            readerDefaultSettings = legacySettings,
            books = listOf(
                BookItem(
                    id = "epub-book",
                    path = null,
                    type = FileType.EPUB,
                    displayName = "Book.epub",
                    timestamp = 1L,
                    readerSettings = legacySettings,
                    readerFormatIsLocal = true,
                    readerLocalFormatSettings = localSettings,
                ),
                BookItem(
                    id = "pdf-book",
                    path = null,
                    type = FileType.PDF,
                    displayName = "Book.pdf",
                    timestamp = 1L,
                    readerSettings = legacySettings,
                ),
            ),
        )

        try {
            defaults.setObject(SharedLibrarySnapshotJson.encode(rawSnapshot), forKey = snapshotKey)
            defaults.removeObjectForKey(legacyKey)

            val restored = loadIosLibrarySnapshot()
            val restoredDefault = restored.readerDefaultSettings
            assertEquals(54, restoredDefault.fontSize)
            assertEquals(1000, restoredDefault.fontWeight)
            assertEquals(0.5f, restoredDefault.letterSpacing)
            assertEquals(1.45f, restoredDefault.lineSpacing)
            assertEquals(144, restoredDefault.margin)
            assertEquals(0f, restoredDefault.paragraphSpacing)
            assertEquals(2f, restoredDefault.imageScale)
            assertEquals(0, restoredDefault.horizontalMargin)
            assertEquals(144, restoredDefault.verticalMargin)
            assertEquals(ReaderReadingMode.PAGINATED, restoredDefault.readingMode)
            assertEquals(SharedReaderTextAlign.JUSTIFY, restoredDefault.textAlign)

            val restoredBook = restored.books.first { it.id == "epub-book" }
            assertEquals(9, restoredBook.readerSettings?.fontSize)
            assertEquals(4.35f, restoredBook.readerSettings?.lineSpacing)
            assertEquals(0, restoredBook.readerSettings?.resolvedHorizontalMargin)
            assertEquals(144, restoredBook.readerSettings?.resolvedVerticalMargin)
            assertEquals(9, restoredBook.readerLocalFormatSettings?.fontSize)
            assertEquals(4.35f, restoredBook.readerLocalFormatSettings?.lineSpacing)
            assertEquals(144, restoredBook.readerLocalFormatSettings?.margin)

            // PDF settings are intentionally outside the EPUB migration boundary.
            assertEquals(legacySettings, restored.books.first { it.id == "pdf-book" }.readerSettings)

            val persisted = defaults.stringForKey(snapshotKey)
            assertNotNull(persisted)
            val persistedSnapshot = SharedLibrarySnapshotJson.decodeOrEmpty(persisted)
            assertEquals(restoredDefault, persistedSnapshot.readerDefaultSettings)
            assertEquals(
                restoredBook.readerLocalFormatSettings,
                persistedSnapshot.books.first { it.id == "epub-book" }.readerLocalFormatSettings,
            )

            // A second restore reads the already-normalized snapshot without further drift.
            assertEquals(restoredDefault, loadIosLibrarySnapshot().readerDefaultSettings)
        } finally {
            if (previousSnapshot == null) {
                defaults.removeObjectForKey(snapshotKey)
            } else {
                defaults.setObject(previousSnapshot, forKey = snapshotKey)
            }
            if (previousLegacy == null) {
                defaults.removeObjectForKey(legacyKey)
            } else {
                defaults.setObject(previousLegacy, forKey = legacyKey)
            }
        }
    }
}
