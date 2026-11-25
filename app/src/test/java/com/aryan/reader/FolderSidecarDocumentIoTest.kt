package com.aryan.reader

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.aryan.reader.data.FolderBookMetadata
import com.aryan.reader.data.LocalSyncUtils
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.localFolderSyncAnnotationFileName
import com.aryan.reader.shared.localFolderSyncMetadataFileName
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver

/**
 * Regression coverage for the sidecar write path: it used to walk the sync-data directory with
 * [androidx.documentfile.provider.DocumentFile], which issues one provider query per file name, so
 * a 2000-book folder spent thousands of IPC round trips on every reading-position flush.
 *
 * The fake provider counts every query, so a regression back to per-child lookups fails the
 * bounded-call assertions instead of only showing up as slowness on a huge folder.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class FolderSidecarDocumentIoTest {

    private lateinit var context: Context
    private lateinit var provider: FakeDocumentsProvider
    private lateinit var treeUri: Uri

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val resolver = context.contentResolver
        provider = FakeDocumentsProvider(File(context.cacheDir, "fake-docs").apply { deleteRecursively(); mkdirs() })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        provider.attachInfo(context, ProviderInfo().apply { authority = AUTHORITY })
        treeUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
    }

    @Test
    fun `saving metadata for a large folder stays bounded in provider queries`() = runBlocking {
        provider.addDirectory(ROOT_ID, LOCAL_FOLDER_SYNC_DATA_DIR)
        val bookId = "book-target"
        provider.addFile(
            syncDirId(),
            localFolderSyncMetadataFileName(bookId),
            JSON_MIME,
            metadata(bookId, timestamp = 100L).toJsonString().toByteArray()
        )
        repeat(500) { index ->
            val otherId = "book-$index"
            provider.addFile(
                syncDirId(),
                localFolderSyncMetadataFileName(otherId),
                JSON_MIME,
                metadata(otherId, timestamp = 50L).toJsonString().toByteArray()
            )
        }
        provider.resetCounters()

        val saved = LocalSyncUtils.saveMetadataToFolder(context, treeUri, metadata(bookId, timestamp = 200L))

        assertTrue("save should succeed", saved)
        assertTrue(
            "save must not scan the directory per child, queries=${provider.queryCount}",
            provider.queryCount <= 4
        )
        val reloaded = LocalSyncUtils.getAllFolderMetadata(context, treeUri)
        assertEquals(200L, reloaded[bookId]?.lastModifiedTimestamp)
        assertEquals(501, provider.childCount(syncDirId()))
    }

    @Test
    fun `saving annotations for a large folder stays bounded in provider queries`() = runBlocking {
        provider.addDirectory(ROOT_ID, LOCAL_FOLDER_SYNC_DATA_DIR)
        val bookId = "book-annotation"
        repeat(400) { index ->
            val otherId = "other-$index"
            provider.addFile(
                syncDirId(),
                localFolderSyncAnnotationFileName(otherId),
                JSON_MIME,
                annotationJson(otherId, timestamp = 10L)
            )
        }
        provider.resetCounters()

        val saved = LocalSyncUtils.saveAnnotationSidecar(
            context = context,
            sourceFolderUri = treeUri,
            bookId = bookId,
            jsonPayload = "{\"page\":3}",
            timestamp = 20L
        )

        assertTrue("annotation save should succeed", saved)
        assertTrue(
            "annotation save must not scan the directory per child, queries=${provider.queryCount}",
            provider.queryCount <= 4
        )
        val readBack = LocalSyncUtils.getAnnotationSidecar(context, treeUri, bookId)
        assertNotNull(readBack)
        assertEquals(20L, readBack?.first)
        assertTrue(readBack?.second.orEmpty().contains("\"page\":3"))
    }

    @Test
    fun `metadata reads only changed sidecars after the first pass`() = runBlocking {
        provider.addDirectory(ROOT_ID, LOCAL_FOLDER_SYNC_DATA_DIR)
        val bookIds = (0 until 400).map { "bulk-$it" }
        bookIds.forEachIndexed { index, id ->
            provider.addFile(
                syncDirId(),
                localFolderSyncMetadataFileName(id),
                JSON_MIME,
                metadata(id, timestamp = 10L + index).toJsonString().toByteArray()
            )
        }

        provider.resetCounters()
        val firstPass = LocalSyncUtils.getAllFolderMetadata(context, treeUri)
        assertEquals(400, firstPass.size)
        assertEquals("first pass reads every sidecar", 400, provider.openCount)

        provider.resetCounters()
        val secondPass = LocalSyncUtils.getAllFolderMetadata(
            context,
            treeUri,
            knownLocalTimestamps = knownTimestamps(firstPass),
        )
        assertEquals(0, provider.openCount)
        assertTrue("unchanged sidecars are not re-applied", secondPass.isEmpty())

        // Losing the rows must re-read the sidecars so the metadata can be restored.
        provider.resetCounters()
        val recoveryPass = LocalSyncUtils.getAllFolderMetadata(context, treeUri)
        assertEquals(400, provider.openCount)
        assertEquals(400, recoveryPass.size)

        val changedId = bookIds[7]
        provider.replaceContent(
            syncDirId(),
            localFolderSyncMetadataFileName(changedId),
            metadata(changedId, timestamp = 900L).toJsonString().toByteArray()
        )
        provider.resetCounters()
        val thirdPass = LocalSyncUtils.getAllFolderMetadata(
            context,
            treeUri,
            knownLocalTimestamps = knownTimestamps(recoveryPass),
        )
        assertEquals(1, provider.openCount)
        assertEquals(900L, thirdPass[changedId]?.lastModifiedTimestamp)
    }

    @Test
    fun `older save keeps the newer sidecar and drops older conflict copies`() = runBlocking {
        provider.addDirectory(ROOT_ID, LOCAL_FOLDER_SYNC_DATA_DIR)
        val bookId = "book-newer-remote"
        provider.addFile(
            syncDirId(),
            localFolderSyncMetadataFileName(bookId),
            JSON_MIME,
            metadata(bookId, timestamp = 500L).toJsonString().toByteArray()
        )
        provider.addFile(
            syncDirId(),
            "$bookId.sync-conflict-20250101-120000-ABCD123.json",
            JSON_MIME,
            metadata(bookId, timestamp = 100L).toJsonString().toByteArray()
        )

        val saved = LocalSyncUtils.saveMetadataToFolder(
            context = context,
            sourceFolderUri = treeUri,
            metadata = metadata(bookId, timestamp = 50L)
        )

        assertTrue("the save is a successful no-op", saved)
        assertEquals("losing copies are removed", 1, provider.childCount(syncDirId()))
        val reloaded = LocalSyncUtils.getAllFolderMetadata(context, treeUri)
        assertEquals(500L, reloaded[bookId]?.lastModifiedTimestamp)
    }

    @Test
    fun `annotation index reads each sidecar once and payloads on demand`() = runBlocking {
        File(context.cacheDir, "folder_annotation_index").deleteRecursively()
        provider.addDirectory(ROOT_ID, LOCAL_FOLDER_SYNC_DATA_DIR)
        val bookIds = (0 until 300).map { "annotated-$it" }
        bookIds.forEachIndexed { index, id ->
            provider.addFile(
                syncDirId(),
                localFolderSyncAnnotationFileName(id),
                JSON_MIME,
                annotationJson(id, timestamp = 10L + index)
            )
        }

        provider.resetCounters()
        val firstPass = LocalSyncUtils.preloadAnnotationSidecarIndex(context, treeUri)
        assertEquals(300, firstPass.size)
        assertEquals("first pass parses every sidecar", 300, provider.openCount)
        assertEquals(309L, firstPass.getValue(bookIds[299]).timestamp)

        provider.resetCounters()
        val secondPass = LocalSyncUtils.preloadAnnotationSidecarIndex(context, treeUri)
        assertEquals(300, secondPass.size)
        assertEquals("unchanged sidecars are not read again", 0, provider.openCount)

        provider.resetCounters()
        val payload = LocalSyncUtils.readAnnotationSidecarPayload(context, secondPass.getValue(bookIds[7]))
        assertEquals("payload is read only for the imported book", 1, provider.openCount)
        assertTrue(payload.orEmpty().contains("\"page\":1"))

        val changedId = bookIds[3]
        provider.replaceContent(
            syncDirId(),
            localFolderSyncAnnotationFileName(changedId),
            annotationJson(changedId, timestamp = 777L)
        )
        provider.resetCounters()
        val thirdPass = LocalSyncUtils.preloadAnnotationSidecarIndex(context, treeUri)
        assertEquals("a changed sidecar is re-read", 1, provider.openCount)
        assertEquals(777L, thirdPass.getValue(changedId).timestamp)
    }

    @Test
    fun `deleting a book removes only its own sidecars`() = runBlocking {
        provider.addDirectory(ROOT_ID, LOCAL_FOLDER_SYNC_DATA_DIR)
        val bookId = "book-delete"
        provider.addFile(
            syncDirId(),
            localFolderSyncMetadataFileName(bookId),
            JSON_MIME,
            metadata(bookId, timestamp = 5L).toJsonString().toByteArray()
        )
        provider.addFile(
            syncDirId(),
            localFolderSyncAnnotationFileName(bookId),
            JSON_MIME,
            annotationJson(bookId, timestamp = 5L)
        )
        val untouched = "book-keep"
        provider.addFile(
            syncDirId(),
            localFolderSyncMetadataFileName(untouched),
            JSON_MIME,
            metadata(untouched, timestamp = 5L).toJsonString().toByteArray()
        )
        provider.resetCounters()

        LocalSyncUtils.deleteBookSidecars(context, treeUri, bookId)

        assertEquals(1, provider.childCount(syncDirId()))
        assertTrue(provider.queryCount <= 3)
    }

    private fun knownTimestamps(
        metadataByBook: Map<String, FolderBookMetadata>
    ): Map<String, Long> = metadataByBook.mapValues { it.value.lastModifiedTimestamp }

    private fun metadata(bookId: String, timestamp: Long) = FolderBookMetadata(
        bookId = bookId,
        title = "Title $bookId",
        author = "Author",
        displayName = "$bookId.epub",
        type = "EPUB",
        lastChapterIndex = 2,
        lastPage = null,
        lastPositionCfi = "epubcfi(/6/4)",
        progressPercentage = 0.4f,
        isRecent = true,
        lastModifiedTimestamp = timestamp,
        bookmarksJson = null,
        locatorBlockIndex = null,
        locatorCharOffset = null,
        customName = null,
        highlightsJson = null
    )

    private fun annotationJson(bookId: String, timestamp: Long): ByteArray =
        """{"version":1,"bookId":"$bookId","timestamp":$timestamp,"data":{"page":1}}"""
            .toByteArray()

    private fun syncDirId(): String = "$ROOT_ID/$LOCAL_FOLDER_SYNC_DATA_DIR"

    private class Node(
        val id: String,
        var name: String,
        val mimeType: String,
        var lastModified: Long
    )

    private class FakeDocumentsProvider(private val storageDir: File) : ContentProvider() {

        private val childrenByParent = mutableMapOf<String, MutableList<Node>>()
        private val nodesById = mutableMapOf<String, Node>()

        var queryCount = 0
            private set
        var openCount = 0
            private set

        fun resetCounters() {
            queryCount = 0
            openCount = 0
        }

        fun addDirectory(parentId: String, name: String): Node {
            val id = "$parentId/$name"
            val node = Node(id, name, DocumentsContract.Document.MIME_TYPE_DIR, System.currentTimeMillis())
            nodesById[id] = node
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(node)
            return node
        }

        fun addFile(parentId: String, name: String, mimeType: String, bytes: ByteArray): Node {
            val id = "$parentId/$name"
            val node = Node(id, name, mimeType, System.currentTimeMillis())
            nodesById[id] = node
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(node)
            backingFile(id).writeBytes(bytes)
            return node
        }

        fun replaceContent(parentId: String, name: String, bytes: ByteArray) {
            val node = childrenByParent[parentId]?.firstOrNull { it.name == name } ?: return
            backingFile(node.id).writeBytes(bytes)
            node.lastModified = System.currentTimeMillis() + 5_000
        }

        fun childCount(parentId: String): Int = childrenByParent[parentId]?.size ?: 0

        private fun backingFile(id: String): File = File(storageDir, id.replace('/', '_'))

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            queryCount++
            val documentId = documentIdOf(uri)
            val columns = projection ?: DEFAULT_PROJECTION
            val cursor = MatrixCursor(columns)
            val rows = if (isChildrenUri(uri)) {
                childrenByParent[documentId].orEmpty()
            } else {
                listOfNotNull(nodesById[documentId])
            }
            rows.forEach { node ->
                cursor.addRow(columns.map { column -> columnValue(node, column) })
            }
            return cursor
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openCount++
            val documentId = documentIdOf(uri)
                ?: throw FileNotFoundException("no document id in $uri")
            val node = nodesById[documentId] ?: throw FileNotFoundException("no node $documentId")
            val file = backingFile(node.id)
            val flags = when (mode) {
                "r" -> ParcelFileDescriptor.MODE_READ_ONLY
                "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
                else -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            }
            if (mode == "r" && !file.isFile) throw FileNotFoundException("missing $file")
            return ParcelFileDescriptor.open(file, flags)
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            val bundle = extras ?: return null
            val target = bundle.getParcelable<Uri>(FakeDocumentsProvider.EXTRA_URI)
                ?: (bundle.get("uri") as? Uri)
                ?: return null
            return when (method) {
                METHOD_CREATE_DOCUMENT -> {
                    val parentId = documentIdOf(target) ?: return null
                    val name = bundle.getString(FakeDocumentsProvider.EXTRA_DISPLAY_NAME) ?: return null
                    val mimeType = bundle.getString(FakeDocumentsProvider.EXTRA_MIME_TYPES)
                        ?: "application/octet-stream"
                    val node = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        addDirectory(parentId, name)
                    } else {
                        addFile(parentId, name, mimeType, ByteArray(0))
                    }
                    Bundle().apply { putParcelable(FakeDocumentsProvider.EXTRA_URI, documentUri(node.id)) }
                }
                METHOD_RENAME_DOCUMENT -> {
                    val documentId = documentIdOf(target) ?: return null
                    val node = nodesById[documentId] ?: return null
                    node.name = bundle.getString(FakeDocumentsProvider.EXTRA_DISPLAY_NAME) ?: return null
                    Bundle().apply { putParcelable(FakeDocumentsProvider.EXTRA_URI, documentUri(node.id)) }
                }
                METHOD_DELETE_DOCUMENT -> {
                    val documentId = documentIdOf(target) ?: return null
                    deleteRecursively(documentId)
                    Bundle.EMPTY
                }
                else -> null
            }
        }

        private fun deleteRecursively(documentId: String) {
            val node = nodesById.remove(documentId) ?: return
            childrenByParent.remove(documentId)?.forEach { child -> deleteRecursively(child.id) }
            childrenByParent.values.forEach { siblings -> siblings.removeAll { it.id == node.id } }
            backingFile(documentId).delete()
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0

        private fun documentUri(documentId: String): Uri =
            DocumentsContract.buildDocumentUriUsingTree(
                DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID),
                documentId
            )

        private fun columnValue(node: Node, column: String): Any? = when (column) {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> node.id
            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> node.name
            DocumentsContract.Document.COLUMN_MIME_TYPE -> node.mimeType
            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> node.lastModified
            DocumentsContract.Document.COLUMN_SIZE -> backingFile(node.id).takeIf { it.isFile }?.length() ?: 0L
            else -> null
        }

        private fun isChildrenUri(uri: Uri): Boolean = uri.path?.endsWith("/children") == true

        private fun documentIdOf(uri: Uri): String? {
            val path = uri.path ?: return null
            val documentMarker = "/document/"
            val index = path.lastIndexOf(documentMarker)
            if (index >= 0) {
                return path.substring(index + documentMarker.length).removeSuffix("/children")
            }
            val treeMarker = "/tree/"
            val treeIndex = path.lastIndexOf(treeMarker)
            return if (treeIndex >= 0) path.substring(treeIndex + treeMarker.length) else null
        }

        companion object {
            const val EXTRA_URI = "uri"
            const val EXTRA_DISPLAY_NAME = "_display_name"
            const val EXTRA_MIME_TYPES = "mime_type"
            private const val METHOD_CREATE_DOCUMENT = "android:createDocument"
            private const val METHOD_RENAME_DOCUMENT = "android:renameDocument"
            private const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
            private val DEFAULT_PROJECTION = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
        }
    }

    companion object {
        private const val AUTHORITY = "com.aryan.reader.test.documents"
        private const val ROOT_ID = "primary:Books"
        private const val JSON_MIME = "application/json"
    }
}
