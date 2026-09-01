package com.aryan.reader.desktop

import com.aryan.reader.shared.CloudBookTombstone
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.sharedCloudBookContentFileName
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val TestDesktopCloudConfig = DesktopCloudConfig(
    aiWorkerUrl = "",
    ttsWorkerUrl = "",
    firebaseWebApiKey = "test-api-key",
    firebaseProjectId = "test-project",
    googleOAuthClientId = "test-client-id",
    googleOAuthClientSecret = "test-client-secret"
)

private class RecordingDriveRepository(
    private val events: MutableList<String>
) : DesktopGoogleDriveRepository() {
    var files: List<DesktopDriveFile> = emptyList()
    var failingFileIds: Set<String> = emptySet()

    val deletedFileIds = mutableListOf<String>()

    override suspend fun getFiles(accessToken: String): List<DesktopDriveFile> = files

    override suspend fun deleteDriveFileOrThrow(accessToken: String, fileId: String) {
        if (fileId in failingFileIds) throw IllegalStateException("drive delete failed")
        deletedFileIds += fileId
        events += "drive_deleted:$fileId"
    }
}

private class RecordingFirestoreRepository(
    private val events: MutableList<String>
) : DesktopFirestoreRepository(TestDesktopCloudConfig) {
    var failWrites = false
    val writtenBooks = mutableListOf<DesktopCloudBookMetadata>()

    override suspend fun syncBookMetadata(
        userId: String,
        book: DesktopCloudBookMetadata,
        originDeviceId: String,
        idToken: String
    ) {
        check(!failWrites) { "firestore write failed" }
        writtenBooks += book
        events += "firestore_tombstone:${book.bookId}"
    }
}

class DesktopCloudDeleteOrderingTest {

    private fun newEngine(
        drive: DesktopGoogleDriveRepository,
        firestore: DesktopFirestoreRepository
    ): DesktopCloudSync {
        val tempRoot = Files.createTempDirectory("episteme-delete-ordering").toFile()
        return DesktopCloudSync(
            firestoreRepository = firestore,
            driveRepository = drive,
            bookImporter = DesktopBookImporter(File(tempRoot, "books")),
            customFontStore = DesktopCustomFontStore(googleFontsDownloadAvailable = { false })
        )
    }

    @Test
    fun `pending delete removes drive payloads before publishing tombstone`() = runBlocking {
        val events = mutableListOf<String>()
        val drive = RecordingDriveRepository(events)
        val firestore = RecordingFirestoreRepository(events)
        val contentName = sharedCloudBookContentFileName("book-1", FileType.EPUB)
        val annotationName = desktopCloudAnnotationDriveFileName("book-1")
        drive.files = listOf(
            DesktopDriveFile(id = "content-1", name = contentName.orEmpty()),
            DesktopDriveFile(id = "annotation-1", name = annotationName)
        )

        val engine = newEngine(drive, firestore)
        val result = engine.deleteBooksFromCloud(
            userId = "user",
            idToken = "id-token",
            accessToken = "drive-token",
            deviceId = "device",
            tombstones = listOf(CloudBookTombstone(bookId = "book-1", type = "EPUB", deletedAt = 100L))
        )

        assertEquals(setOf("book-1"), result.succeededBookIds)
        assertTrue(result.failedBookIds.isEmpty())
        assertEquals(listOf("content-1", "annotation-1"), drive.deletedFileIds)
        // Remote payloads first, tombstone last — the Android ordering contract.
        assertEquals(
            listOf("drive_deleted:content-1", "drive_deleted:annotation-1", "firestore_tombstone:book-1"),
            events
        )
    }

    @Test
    fun `published tombstone is deleted with preserved type and bumped clock`() = runBlocking {
        val before = System.currentTimeMillis()
        val events = mutableListOf<String>()
        val drive = RecordingDriveRepository(events)
        val firestore = RecordingFirestoreRepository(events)
        val engine = newEngine(drive, firestore)

        engine.deleteBooksFromCloud(
            userId = "user",
            idToken = "id-token",
            accessToken = "drive-token",
            deviceId = "device",
            tombstones = listOf(CloudBookTombstone(bookId = "book-1", type = "EPUB", deletedAt = 100L))
        )

        val tombstone = firestore.writtenBooks.single()
        assertEquals("book-1", tombstone.bookId)
        assertEquals("EPUB", tombstone.type)
        assertTrue(tombstone.isDeleted)
        assertEquals("", tombstone.displayName)
        // Retries keep the clock at least as late as the original deletion.
        assertTrue(tombstone.lastModifiedTimestamp >= maxOf(100L, before))
    }

    @Test
    fun `failed drive deletion never publishes a consumable tombstone`() = runBlocking {
        val events = mutableListOf<String>()
        val drive = RecordingDriveRepository(events)
        val firestore = RecordingFirestoreRepository(events)
        val annotationName = desktopCloudAnnotationDriveFileName("book-1")
        drive.files = listOf(
            DesktopDriveFile(id = "content-1", name = sharedCloudBookContentFileName("book-1", FileType.EPUB).orEmpty()),
            DesktopDriveFile(id = "annotation-1", name = annotationName)
        )
        drive.failingFileIds = setOf("content-1")

        val engine = newEngine(drive, firestore)
        val result = engine.deleteBooksFromCloud(
            userId = "user",
            idToken = "id-token",
            accessToken = "drive-token",
            deviceId = "device",
            tombstones = listOf(CloudBookTombstone(bookId = "book-1", type = "EPUB", deletedAt = 100L))
        )

        assertEquals(setOf("book-1"), result.failedBookIds)
        assertTrue(result.succeededBookIds.isEmpty())
        assertTrue(firestore.writtenBooks.isEmpty())
        assertTrue(events.none { it.startsWith("firestore_tombstone") })
    }

    @Test
    fun `one failing book does not block other pending deletions`() = runBlocking {
        val events = mutableListOf<String>()
        val drive = RecordingDriveRepository(events)
        val firestore = RecordingFirestoreRepository(events)
        drive.files = listOf(
            DesktopDriveFile(id = "content-bad", name = sharedCloudBookContentFileName("book-bad", FileType.EPUB).orEmpty()),
            DesktopDriveFile(id = "content-good", name = sharedCloudBookContentFileName("book-good", FileType.EPUB).orEmpty())
        )
        drive.failingFileIds = setOf("content-bad")

        val engine = newEngine(drive, firestore)
        val result = engine.deleteBooksFromCloud(
            userId = "user",
            idToken = "id-token",
            accessToken = "drive-token",
            deviceId = "device",
            tombstones = listOf(
                CloudBookTombstone(bookId = "book-bad", type = "EPUB", deletedAt = 10L),
                CloudBookTombstone(bookId = "book-good", type = "EPUB", deletedAt = 20L)
            )
        )

        assertEquals(setOf("book-bad"), result.failedBookIds)
        assertEquals(setOf("book-good"), result.succeededBookIds)
        assertEquals("book-good", firestore.writtenBooks.single().bookId)
    }

    @Test
    fun `listing failure aborts the whole drain so callers can retry later`() = runBlocking {
        val events = mutableListOf<String>()
        val failingList = object : DesktopGoogleDriveRepository() {
            override suspend fun getFiles(accessToken: String): List<DesktopDriveFile> {
                throw IllegalStateException("drive offline")
            }
        }
        val firestore = RecordingFirestoreRepository(events)
        val engine = newEngine(failingList, firestore)

        assertFailsWith<IllegalStateException> {
            engine.deleteBooksFromCloud(
                userId = "user",
                idToken = "id-token",
                accessToken = "drive-token",
                deviceId = "device",
                tombstones = listOf(CloudBookTombstone(bookId = "book-1", deletedAt = 1L))
            )
        }
        assertTrue(firestore.writtenBooks.isEmpty())
    }
}
