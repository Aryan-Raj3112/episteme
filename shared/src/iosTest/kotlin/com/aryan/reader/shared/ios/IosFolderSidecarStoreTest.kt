package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LOCAL_FOLDER_SCAN_LOG_TAG
import com.aryan.reader.shared.LocalFolderSidecars
import com.aryan.reader.shared.SharedFolderBookMetadata
import com.aryan.reader.shared.toSharedFolderBookMetadata
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * Exercises the real filesystem, because the value here is entirely in the
 * temp-then-move sequence and the directory creation — both of which a mock
 * would not exercise.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFolderSidecarStoreTest {
    private val fileManager = NSFileManager.defaultManager
    private var root: String = ""

    @BeforeTest
    fun setUp() {
        val base = platform.Foundation.NSTemporaryDirectory()
        root = NSURL.fileURLWithPath(base)
            .URLByAppendingPathComponent("sidecar-test-" + NSUUID().UUIDString, isDirectory = true)!!
            .path!!
        fileManager.createDirectoryAtPath(
            path = root,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    @AfterTest
    fun tearDown() {
        if (root.isNotBlank()) {
            fileManager.removeItemAtPath(root, error = null)
        }
    }

    private fun sampleMetadata(bookId: String = "local_Book.pdf") = SharedFolderBookMetadata(
        bookId = bookId,
        displayName = "Book.epub",
        type = "EPUB",
        lastChapterIndex = 4,
        lastPage = 12,
        lastPositionCfi = "epubcfi(/6/4!/4/2/1:0)",
        progressPercentage = 0.42f,
        isRecent = true,
        lastModifiedTimestamp = 1_700_000_000_000L,
        bookmarksJson = null,
        locatorBlockIndex = 7,
        locatorCharOffset = 128,
        customName = "My title",
        highlightsJson = null,
        seriesName = "A series",
    )

    @Test
    fun `write then read round trips a metadata sidecar`() {
        val metadata = sampleMetadata()

        assertTrue(IosFolderSidecarStore.writeMetadata(root, metadata))
        val read = IosFolderSidecarStore.readAllMetadata(root)

        val loaded = assertNotNull(read["local_Book.pdf"], "sidecar was not found after write")
        assertEquals(metadata.bookId, loaded.bookId)
        assertEquals(metadata.displayName, loaded.displayName)
        assertEquals(metadata.customName, loaded.customName)
        assertEquals(metadata.seriesName, loaded.seriesName)
        assertEquals(metadata.lastChapterIndex, loaded.lastChapterIndex)
        assertEquals(metadata.lastPage, loaded.lastPage)
        assertEquals(metadata.progressPercentage, loaded.progressPercentage)
        assertEquals(metadata.lastModifiedTimestamp, loaded.lastModifiedTimestamp)
    }

    @Test
    fun `the sidecar lands at the canonical android-compatible path`() {
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata())

        val expected = LocalFolderSidecars.syncDirPath(root) + "/" +
            LocalFolderSidecars.metadataFileName("local_Book.pdf")
        assertTrue(
            fileManager.fileExistsAtPath(expected),
            "expected sidecar at $expected",
        )
    }

    @Test
    fun `reading a folder with no sync dir yields nothing instead of failing`() {
        val read = IosFolderSidecarStore.readAllMetadata(root)

        assertTrue(read.isEmpty())
    }

    @Test
    fun `a newer record wins when a rename left two sidecars behind`() {
        val older = sampleMetadata().copy(lastModifiedTimestamp = 1_000L)
        val newer = sampleMetadata().copy(lastModifiedTimestamp = 2_000L, customName = "Newer")
        IosFolderSidecarStore.writeMetadata(root, older)
        // Write the newer payload under a second, non-canonical name, which is
        // what an interrupted rename leaves on disk.
        val aliasName = "${LocalFolderSidecars.metadataFileName("local_Book.pdf")}.sync-backup-1.json"
        val aliasPath = LocalFolderSidecars.syncDirPath(root) + "/" + aliasName
        fileManager.createDirectoryAtPath(
            path = LocalFolderSidecars.syncDirPath(root),
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        writeTextFile(aliasPath, assertNotNull(newer.toJsonString()))

        val loaded = assertNotNull(IosFolderSidecarStore.readAllMetadata(root)["local_Book.pdf"])

        assertEquals("Newer", loaded.customName)
        assertEquals(2_000L, loaded.lastModifiedTimestamp)
    }

    @Test
    fun `a malformed sidecar is skipped rather than failing the whole folder`() {
        val dirPath = LocalFolderSidecars.syncDirPath(root)
        fileManager.createDirectoryAtPath(
            path = dirPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        writeTextFile("$dirPath/${LocalFolderSidecars.metadataFileName("local_Broken.pdf")}", "{not json")
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata())

        val read = IosFolderSidecarStore.readAllMetadata(root)

        assertEquals(1, read.size, "a malformed sidecar should be skipped, not fatal")
        assertNotNull(read["local_Book.pdf"])
    }

    @Test
    fun `no temp files are left behind after a successful write`() {
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata())

        val dirPath = LocalFolderSidecars.syncDirPath(root)
        val names = fileManager.contentsOfDirectoryAtPath(dirPath, error = null)
            ?.filterIsInstance<String>()
            .orEmpty()

        assertEquals(
            emptyList(),
            names.filter { it.endsWith(".tmp") },
            "a temp sidecar was left behind, which a folder scan would treat as a book",
        )
    }

    @Test
    fun `overwriting an existing sidecar keeps exactly one file`() {
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata().copy(customName = "First"))
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata().copy(customName = "Second"))

        val dirPath = LocalFolderSidecars.syncDirPath(root)
        val jsonNames = fileManager.contentsOfDirectoryAtPath(dirPath, error = null)
            ?.filterIsInstance<String>()
            .orEmpty()
            .filter { it.endsWith(".json") }

        assertEquals(1, jsonNames.size, "expected a single canonical sidecar, found $jsonNames")
        assertEquals("Second", IosFolderSidecarStore.readAllMetadata(root)["local_Book.pdf"]?.customName)
    }

    @Test
    fun `deleting a book removes both its sidecars and leaves others alone`() {
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata("local_Keep.pdf"))
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata("local_Remove.pdf"))

        assertTrue(IosFolderSidecarStore.deleteBookSidecars(root, "local_Remove.pdf"))

        val remaining = IosFolderSidecarStore.readAllMetadata(root)
        assertNotNull(remaining["local_Keep.pdf"])
        assertNull(remaining["local_Remove.pdf"])
    }

    @Test
    fun `deleting the sync dir is a no-op when it does not exist`() {
        assertTrue(IosFolderSidecarStore.deleteSyncDir(root))
    }

    @Test
    fun `deleting the sync dir removes it once it does`() {
        IosFolderSidecarStore.writeMetadata(root, sampleMetadata())

        assertTrue(IosFolderSidecarStore.deleteSyncDir(root))
        assertTrue(IosFolderSidecarStore.readAllMetadata(root).isEmpty())
    }

    @Test
    fun `a clean book writes no sidecar matching the shared dirty gate`() {
        // Mirrors `toSharedFolderBookMetadata`: a book with no progress, no
        // recent flag and no annotations must not leave a file in the folder.
        val clean = BookItem(
            id = "local_Clean.epub",
            path = SharedIosBookSourceRef.encode("Books", "Clean.epub"),
            type = FileType.EPUB,
            displayName = "Clean.epub",
            timestamp = 0L,
            sourceFolder = "Books",
            isRecent = false,
            progressPercentage = 0f,
        )

        assertNull(clean.toSharedFolderBookMetadata())
    }

    private fun writeTextFile(path: String, contents: String) {
        fileManager.createDirectoryAtPath(
            path = path.substringBeforeLast('/'),
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        val data = contents.encodeToByteArray()
        val ok = data.usePinned { pinned ->
            fopen(path, "wb")?.let { file ->
                try {
                    fwrite(pinned.addressOf(0), 1u, data.size.convert(), file).toInt() == data.size
                } finally {
                    fclose(file)
                }
            } ?: false
        }
        assertTrue(ok, "could not stage test file at $path")
    }
}
