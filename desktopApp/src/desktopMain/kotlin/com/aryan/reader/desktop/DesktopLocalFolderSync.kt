package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LOCAL_FOLDER_ANNOTATION_SUFFIX
import com.aryan.reader.shared.LOCAL_FOLDER_SIDECAR_HASH_PREFIX
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.LocalFolderSyncEngine
import com.aryan.reader.shared.LocalFolderSyncStats
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedFolderBookMetadata
import com.aryan.reader.shared.SharedFolderScannedFile
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.localFolderSyncAnnotationFileName
import com.aryan.reader.shared.localFolderSyncAnnotationTempFileName
import com.aryan.reader.shared.localFolderSyncMetadataFileName
import com.aryan.reader.shared.localFolderSyncMetadataTempFileName
import com.aryan.reader.shared.localFolderSyncSidecarStem
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.toSharedFolderBookMetadata
import com.aryan.reader.shared.toStablePositionCfi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class DesktopLocalFolderSyncResult(
    val state: SharedReaderScreenState,
    val shelfRefs: List<BookShelfRef>,
    val stats: LocalFolderSyncStats,
    val metadataStats: DesktopFolderMetadataExtractionStats = DesktopFolderMetadataExtractionStats(),
    val idMigrations: Map<String, String> = emptyMap(),
    val removedBookIds: Set<String> = emptySet(),
    val failedFolders: List<String> = emptyList(),
    val processedFolderUris: List<String> = emptyList()
)

object DesktopLocalFolderSync {
    private val desktopSyncableTypes = SharedFileCapabilities.syncableTypesFor(ReaderPlatform.DESKTOP)

    fun hasSupportedFiles(folder: File): Boolean {
        if (!folder.isDirectory) return false
        return folder.walkTopDown()
            .onEnter { it == folder || it.shouldEnterSyncedFolder() }
            .onFail { file, error ->
                logDesktopFolderSync(
                    "folder.supportedFiles.skipInaccessible path=\"${file.absolutePath.folderSyncPreview()}\" " +
                        "error=${error.folderSyncSummary()}"
                )
            }
            .any { file ->
                runCatching {
                    file.isFile &&
                        file.shouldSyncBookFile() &&
                        SharedFileCapabilities.fileTypeForName(file.name) in desktopSyncableTypes
                }.getOrDefault(false)
            }
    }

    fun sync(
        state: SharedReaderScreenState,
        shelfRefs: List<BookShelfRef>,
        targetFolder: File? = null,
        nowMillis: Long = System.currentTimeMillis(),
        metadataOnly: Boolean = false,
        extractMetadata: Boolean = true
    ): DesktopLocalFolderSyncResult {
        val requestedFolders = foldersToSync(state, targetFolder, nowMillis)
            .filter { it.localSyncEnabled }
        val mode = if (metadataOnly) "metadata" else "full"
        logDesktopFolderSync(
            "sync.start mode=$mode target=\"${targetFolder?.absolutePath?.folderSyncPreview() ?: "ALL"}\" " +
                "requestedFolders=${requestedFolders.size} linkedFolders=${state.syncedFolders.size} " +
                "books=${state.rawLibraryBooks.size}"
        )
        var nextState = state
        var nextShelfRefs = shelfRefs
        var totalStats = LocalFolderSyncStats()
        var totalMetadataStats = DesktopFolderMetadataExtractionStats()
        val allMigrations = linkedMapOf<String, String>()
        val allRemovedBookIds = linkedSetOf<String>()
        val failedFolders = mutableListOf<String>()
        val processedFolderUris = mutableListOf<String>()

        requestedFolders.forEach { folder ->
            val root = File(folder.uriString)
            if (!root.isDirectory) {
                logDesktopFolderSync(
                    "folder.skipMissing mode=$mode name=\"${folder.name.folderSyncPreview()}\" " +
                        "root=\"${root.absolutePath.folderSyncPreview()}\""
                )
                failedFolders += folder.name
                return@forEach
            }
            processedFolderUris += folder.uriString

            logDesktopFolderSync(
                "folder.start mode=$mode name=\"${folder.name.folderSyncPreview()}\" " +
                    "root=\"${root.absolutePath.folderSyncPreview()}\" allowed=${folder.allowedFileTypes.sortedBy { it.name }}"
            )
            val scannedFiles = if (metadataOnly) {
                emptyList()
            } else {
                scanFolder(root = root, sourceFolder = folder.uriString)
            }
            val remoteMetadata = readAllMetadata(root)
            logDesktopFolderSync(
                "folder.inputs mode=$mode name=\"${folder.name.folderSyncPreview()}\" " +
                    "scanned=${scannedFiles.size} supported=${scannedFiles.count { it.type in folder.allowedFileTypes }} " +
                    "remoteMetadata=${remoteMetadata.size}"
            )
            val syncResult = LocalFolderSyncEngine.syncFolder(
                state = nextState,
                folder = folder,
                files = scannedFiles,
                remoteMetadata = remoteMetadata,
                nowMillis = nowMillis,
                metadataOnly = metadataOnly
            )
            nextState = syncResult.state
            nextShelfRefs = LocalFolderSyncEngine.applyIdMigrationsToShelfRefs(
                nextShelfRefs,
                syncResult.idMigrations
            ).filterNot { it.bookId in syncResult.removedBookIds }
            allMigrations += syncResult.idMigrations
            allRemovedBookIds += syncResult.removedBookIds
            totalStats += syncResult.stats
            logDesktopFolderSync(
                "folder.engine mode=$mode name=\"${folder.name.folderSyncPreview()}\" " +
                    "new=${syncResult.stats.newBooks} updated=${syncResult.stats.updatedBooks} " +
                    "remoteUpdates=${syncResult.stats.remoteMetadataUpdates} removed=${syncResult.stats.removedBooks} " +
                    "migrated=${syncResult.stats.migratedBooks} idMigrations=${syncResult.idMigrations.size}"
            )

            var syncedBooks = nextState.rawLibraryBooks.filter { it.sourceFolder == folder.uriString }
            logDesktopFolderSync(
                "folder.sidecars.importCheck mode=$mode name=\"${folder.name.folderSyncPreview()}\" books=${syncedBooks.size}"
            )
            runCatching {
                importAnnotationSidecars(root, syncedBooks)
            }.onFailure { error ->
                logDesktopFolderSync(
                    "annotation.import.failed mode=$mode name=\"${folder.name.folderSyncPreview()}\" " +
                        "root=\"${root.absolutePath.folderSyncPreview()}\" error=${error.folderSyncSummary()}"
                )
            }
            syncedBooks.forEach { book ->
                remoteMetadata[book.id]?.let { metadata ->
                    runCatching {
                        importDesktopPdfBookmarksMetadata(book, metadata.bookmarksJson, metadata.lastModifiedTimestamp)
                    }.onFailure { error ->
                        logDesktopFolderSync(
                            "metadata.bookmarks.importFailed book=${book.id} " +
                                "error=${error.folderSyncSummary()}"
                        )
                    }
                }
            }
            if (!metadataOnly && extractMetadata) {
                val metadataResult = DesktopFolderMetadataExtractor.enrichFolderBooks(
                    books = nextState.rawLibraryBooks,
                    sourceFolder = folder.uriString
                )
                if (metadataResult.stats.updatedBooks > 0) {
                    nextState = nextState.copy(rawLibraryBooks = metadataResult.books)
                    syncedBooks = nextState.rawLibraryBooks.filter { it.sourceFolder == folder.uriString }
                }
                totalMetadataStats += metadataResult.stats
                logDesktopFolderSync(
                    "folder.metadataExtraction name=\"${folder.name.folderSyncPreview()}\" " +
                        "updated=${metadataResult.stats.updatedBooks} covers=${metadataResult.stats.coversUpdated}"
                )
            }
            syncedBooks.forEach { book ->
                saveBookMetadata(book)
                if (!metadataOnly) {
                    savePdfAnnotationSidecar(book)
                }
            }
            logDesktopFolderSync(
                "folder.done mode=$mode name=\"${folder.name.folderSyncPreview()}\" " +
                    "savedCandidates=${syncedBooks.size}"
            )
        }

        val result = DesktopLocalFolderSyncResult(
            state = nextState,
            shelfRefs = nextShelfRefs,
            stats = totalStats,
            metadataStats = totalMetadataStats,
            idMigrations = allMigrations,
            removedBookIds = allRemovedBookIds,
            failedFolders = failedFolders,
            processedFolderUris = processedFolderUris
        )
        logDesktopFolderSync(
            "sync.done mode=$mode failed=${failedFolders.size} new=${totalStats.newBooks} " +
                "updated=${totalStats.updatedBooks} remoteUpdates=${totalStats.remoteMetadataUpdates} " +
                "removed=${totalStats.removedBooks} metadataExtracted=${totalMetadataStats.updatedBooks}"
        )
        return result
    }

    fun saveBookSidecars(book: BookItem) {
        saveBookMetadata(book)
        savePdfAnnotationSidecar(book)
    }

    fun deleteSyncDataFolder(root: File): Boolean {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR)
        return !syncDir.exists() || syncDir.isDirectory && syncDir.deleteRecursively()
    }

    fun saveBookMetadata(book: BookItem) {
        val metadata = book.toDesktopFolderBookMetadata()
        if (metadata == null) {
            logDesktopFolderSync(
                "metadata.export.skipClean book=${book.id} title=\"${book.title.orEmpty().folderSyncPreview()}\" " +
                    "progress=${book.progressPercentage} recent=${book.isRecent} bookmarks=${book.readerBookmarks.size} " +
                    "highlights=${book.readerHighlights.size}"
            )
            return
        }
        val root = book.sourceFolder?.let(::File)?.takeIf { it.isDirectory }
        if (root == null) {
            logDesktopFolderSync(
                "metadata.export.skipNoFolder book=${book.id} sourceFolder=\"${book.sourceFolder.orEmpty().folderSyncPreview()}\""
            )
            return
        }
        logDesktopFolderSync(
            "metadata.export.request book=${book.id} timestamp=${metadata.lastModifiedTimestamp} " +
                "progress=${metadata.progressPercentage} recent=${metadata.isRecent} " +
                "root=\"${root.absolutePath.folderSyncPreview()}\""
        )
        saveMetadataToFolder(root, metadata)
    }

    fun savePdfAnnotationSidecar(book: BookItem) {
        val path = book.path?.takeIf { it.isNotBlank() }
        if (path == null) {
            logDesktopFolderSync("annotation.export.skipNoPath book=${book.id}")
            return
        }
        if (book.type != FileType.PDF) {
            logDesktopFolderSync("annotation.export.skipNonPdf book=${book.id} type=${book.type}")
            return
        }
        val root = book.sourceFolder?.let(::File)?.takeIf { it.isDirectory }
        if (root == null) {
            logDesktopFolderSync(
                "annotation.export.skipNoFolder book=${book.id} sourceFolder=\"${book.sourceFolder.orEmpty().folderSyncPreview()}\""
            )
            return
        }
        val annotationFile = desktopPdfAnnotationFile(path)
        val bookmarkFile = desktopPdfBookmarkFile(path)
        val richTextFile = desktopPdfRichTextFile(path)
        logDesktopFolderSync(
            "annotation.export.check book=${book.id} root=\"${root.absolutePath.folderSyncPreview()}\" " +
                "pdfPath=\"${path.folderSyncPreview()}\" hasAnnotations=${annotationFile.isFile} " +
                "hasBookmarks=${bookmarkFile.isFile} hasText=${richTextFile.isFile} " +
                "localTs=${maxOf(annotationFile.lastModifiedIfFile(), bookmarkFile.lastModifiedIfFile(), richTextFile.lastModifiedIfFile())}"
        )
        val data = buildMap {
            if (annotationFile.isFile) {
                val annotationJson = annotationFile.readText().trim()
                desktopPdfAnnotationElementForSync(annotationJson)?.let { annotations ->
                    put(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS, annotations)
                }
            }
            if (bookmarkFile.isFile) {
                val bookmarksJson = bookmarkFile.readText().trim()
                desktopFolderSyncJson.parseElementOrNull(bookmarksJson)?.let { put("bookmarks", it) }
            }
            if (richTextFile.isFile) {
                val richTextJson = richTextFile.readText().trim()
                val richTextElement = desktopFolderSyncJson.parseElementOrNull(richTextJson)
                if (richTextElement == null) {
                    SharedPdfRichTextLog.d(
                        "desktop.sync.exportRichTextParseFailed book=${book.id} " +
                            "file=\"${richTextFile.absolutePath.richSyncPreview()}\" rawLen=${richTextJson.length}"
                    )
                } else {
                    val richTextDocument = SharedPdfRichTextSerializer.decodeElement(richTextElement)
                    SharedPdfRichTextLog.d(
                        "desktop.sync.exportRichText book=${book.id} " +
                            "file=\"${richTextFile.absolutePath.richSyncPreview()}\" rawLen=${richTextJson.length} " +
                            "textLen=${richTextDocument.text.length} spans=${richTextDocument.spans.size}"
                    )
                    desktopPdfRichTextElementForSync(richTextJson)?.let { put("text", it) }
                }
            }
        }
        if (data.isEmpty()) {
            logDesktopFolderSync(
                "annotation.export.skipNoLocalData book=${book.id} pdfPath=\"${path.folderSyncPreview()}\""
            )
            SharedPdfRichTextLog.d("desktop.sync.exportSkipNoSidecarData book=${book.id} pdfPath=\"${path.richSyncPreview()}\"")
            return
        }
        val timestamp = maxOf(
            annotationFile.lastModifiedIfSyncableAnnotations(),
            bookmarkFile.lastModifiedIfFile(),
            richTextFile.lastModifiedIfSyncableRichText(),
            System.currentTimeMillis()
        )
        val dataJson = desktopFolderSyncJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(data)
        )
        logDesktopFolderSync(
            "annotation.export.request book=${book.id} timestamp=$timestamp keys=${data.keys.sorted()} " +
                "root=\"${root.absolutePath.folderSyncPreview()}\""
        )
        if (data.containsKey("text")) {
            SharedPdfRichTextLog.d(
                "desktop.sync.exportSidecar book=${book.id} timestamp=$timestamp " +
                    "keys=${data.keys.sorted()} root=\"${root.absolutePath.richSyncPreview()}\""
            )
        }
        saveAnnotationSidecar(
            root = root,
            bookId = book.id,
            jsonPayload = dataJson,
            timestamp = timestamp
        )
    }

    private fun foldersToSync(
        state: SharedReaderScreenState,
        targetFolder: File?,
        nowMillis: Long
    ): List<SyncedFolder> {
        if (targetFolder == null) return state.syncedFolders
        val root = targetFolder.canonicalOrAbsolute()
        val rootPath = root.absolutePath
        val existing = state.syncedFolders.firstOrNull { File(it.uriString).canonicalOrAbsolute() == root }
        return listOf(
            existing ?: SyncedFolder(
                uriString = rootPath,
                name = root.name.takeIf { it.isNotBlank() } ?: rootPath,
                lastScanTime = nowMillis,
                allowedFileTypes = desktopSyncableTypes
            )
        )
    }

    private fun scanFolder(root: File, sourceFolder: String): List<SharedFolderScannedFile> {
        val rootPath = root.toPath().toAbsolutePath().normalize()
        return root.walkTopDown()
            .onEnter { it == root || it.shouldEnterSyncedFolder() }
            .onFail { file, error ->
                logDesktopFolderSync(
                    "folder.scan.skipInaccessible root=\"${root.absolutePath.folderSyncPreview()}\" " +
                        "path=\"${file.absolutePath.folderSyncPreview()}\" error=${error.folderSyncSummary()}"
                )
            }
            .filter { file ->
                runCatching { file.isFile && file.shouldSyncBookFile() }.getOrDefault(false)
            }
            .mapNotNull { file ->
                val type = SharedFileCapabilities.fileTypeForName(file.name)
                    .takeIf { it in desktopSyncableTypes }
                    ?: return@mapNotNull null
                val relativePath = runCatching {
                    rootPath.relativize(file.toPath().toAbsolutePath().normalize())
                        .joinToString("/")
                }.getOrNull() ?: file.name
                SharedFolderScannedFile(
                    name = file.name,
                    path = file.absolutePath,
                    sourceFolder = sourceFolder,
                    relativePath = relativePath,
                    type = type,
                    size = runCatching { file.length() }.getOrDefault(0L),
                    lastModified = runCatching { file.lastModified() }.getOrDefault(0L)
                )
            }
            .toList()
    }

    private fun readAllMetadata(root: File): Map<String, SharedFolderBookMetadata> {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR)
        if (!syncDir.isDirectory) {
            logDesktopFolderSync("metadata.read.noSyncDir root=\"${root.absolutePath.folderSyncPreview()}\"")
            return emptyMap()
        }
        var candidates = 0
        var parsed = 0
        var failed = 0
        val result = syncDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.isMetadataSidecarCandidate() }
            .mapNotNull { file ->
                candidates++
                runCatching { SharedFolderBookMetadata.fromJsonString(file.readText()) }
                    .onSuccess { parsed++ }
                    .onFailure { error ->
                        failed++
                        logDesktopFolderSync(
                            "metadata.read.parseFailed file=\"${file.absolutePath.folderSyncPreview()}\" " +
                                "error=${error.folderSyncSummary()}"
                        )
                    }
                    .getOrNull()
            }
            .groupBy { it.bookId }
            .mapValues { (_, metadata) -> metadata.maxBy { it.lastModifiedTimestamp } }
            .toMap()
        logDesktopFolderSync(
            "metadata.read.done root=\"${root.absolutePath.folderSyncPreview()}\" " +
                "candidates=$candidates parsed=$parsed failed=$failed winners=${result.size}"
        )
        return result
    }

    private fun saveMetadataToFolder(root: File, metadata: SharedFolderBookMetadata) {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR).apply { mkdirs() }
        val existing = resolveMetadataConflicts(syncDir, metadata.bookId, cleanup = true)
        if (existing != null && existing.lastModifiedTimestamp > metadata.lastModifiedTimestamp) {
            logDesktopFolderSync(
                "metadata.save.skipNewerRemote book=${metadata.bookId} existingTs=${existing.lastModifiedTimestamp} " +
                    "candidateTs=${metadata.lastModifiedTimestamp} root=\"${root.absolutePath.folderSyncPreview()}\""
            )
            return
        }

        val target = File(syncDir, localFolderSyncMetadataFileName(metadata.bookId))
        val temp = File(syncDir, uniqueFolderSyncTempName(localFolderSyncMetadataTempFileName(metadata.bookId)))
        runCatching {
            temp.writeText(metadata.toJsonString())
            moveReplacing(temp, target)
            logDesktopFolderSync(
                "metadata.save.done book=${metadata.bookId} timestamp=${metadata.lastModifiedTimestamp} " +
                    "target=\"${target.absolutePath.folderSyncPreview()}\" bytes=${target.length()}"
            )
        }.onFailure {
            logDesktopFolderSync(
                "metadata.save.failed book=${metadata.bookId} timestamp=${metadata.lastModifiedTimestamp} " +
                    "target=\"${target.absolutePath.folderSyncPreview()}\" error=${it.folderSyncSummary()}"
            )
            runCatching { temp.delete() }
        }
    }

    private fun resolveMetadataConflicts(
        syncDir: File,
        bookId: String,
        cleanup: Boolean
    ): SharedFolderBookMetadata? {
        val hashedStem = localFolderSyncSidecarStem(bookId)
        val candidates = syncDir.listFiles().orEmpty().filter { file ->
            val normalized = file.normalizedSidecarName()
            file.isFile &&
                file.isMetadataSidecarCandidate() &&
                (
                    normalized.matchesJsonSidecarStem(hashedStem) ||
                        normalized.matchesJsonSidecarStem(bookId)
                    )
        }
        if (candidates.isEmpty()) return null
        if (candidates.size > 1) {
            logDesktopFolderSync(
                "metadata.conflicts book=$bookId candidates=${candidates.size} " +
                    "dir=\"${syncDir.absolutePath.folderSyncPreview()}\" cleanup=$cleanup"
            )
        }

        val parsed = candidates.mapNotNull { file ->
            val metadata = runCatching { SharedFolderBookMetadata.fromJsonString(file.readText()) }
                .onFailure { error ->
                    logDesktopFolderSync(
                        "metadata.conflict.parseFailed book=$bookId file=\"${file.absolutePath.folderSyncPreview()}\" " +
                            "error=${error.folderSyncSummary()}"
                    )
                }
                .getOrNull()
            metadata?.takeIf { it.bookId == bookId }?.let { file to it }
        }
        val winner = parsed.maxByOrNull { it.second.lastModifiedTimestamp } ?: return null

        if (cleanup) {
            candidates
                .filterNot { it == winner.first }
                .forEach { file ->
                    runCatching { file.delete() }
                    logDesktopFolderSync(
                        "metadata.conflict.deleteLoser book=$bookId file=\"${file.absolutePath.folderSyncPreview()}\""
                    )
                }
            val correctName = localFolderSyncMetadataFileName(bookId)
            if (winner.first.name != correctName) {
                val target = File(syncDir, correctName)
                runCatching { moveReplacing(winner.first, target) }
                    .onSuccess {
                        logDesktopFolderSync(
                            "metadata.conflict.renameWinner book=$bookId target=\"${target.absolutePath.folderSyncPreview()}\""
                        )
                    }
            }
        }

        return winner.second
    }

    private fun preloadAnnotationSidecars(root: File): Map<String, AnnotationSidecar> {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR)
        if (!syncDir.isDirectory) {
            logDesktopFolderSync("annotation.read.noSyncDir root=\"${root.absolutePath.folderSyncPreview()}\"")
            return emptyMap()
        }
        var candidates = 0
        var parsed = 0
        val result = syncDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && it.isAnnotationSidecarCandidate() }
            .mapNotNull { file ->
                candidates++
                file.readAnnotationSidecarOrNull(fallbackBookId = file.legacyAnnotationBookIdOrNull())
                    ?.also { parsed++ }
            }
            .groupBy { it.bookId }
            .mapValues { (_, sidecars) -> sidecars.maxBy { it.timestamp } }
            .toMap()
        logDesktopFolderSync(
            "annotation.read.done root=\"${root.absolutePath.folderSyncPreview()}\" " +
                "candidates=$candidates parsed=$parsed winners=${result.size}"
        )
        return result
    }

    private fun importAnnotationSidecars(root: File, books: List<BookItem>) {
        if (books.isEmpty()) {
            logDesktopFolderSync("annotation.import.skipNoBooks root=\"${root.absolutePath.folderSyncPreview()}\"")
            return
        }
        val sidecars = preloadAnnotationSidecars(root)
        if (sidecars.isEmpty()) {
            logDesktopFolderSync(
                "annotation.import.skipNoSidecars root=\"${root.absolutePath.folderSyncPreview()}\" books=${books.size}"
            )
            return
        }

        books.forEach { book ->
            val path = book.path?.takeIf { it.isNotBlank() }
            if (path == null) {
                logDesktopFolderSync("annotation.import.skipNoPath book=${book.id}")
                return@forEach
            }
            if (book.type != FileType.PDF) {
                logDesktopFolderSync("annotation.import.skipNonPdf book=${book.id} type=${book.type}")
                return@forEach
            }
            val sidecar = sidecars[book.id]
            if (sidecar == null) {
                logDesktopFolderSync(
                    "annotation.import.skipNoMatchingSidecar book=${book.id} available=${sidecars.keys.size} " +
                        "root=\"${root.absolutePath.folderSyncPreview()}\""
                )
                return@forEach
            }
            val annotationFile = desktopPdfAnnotationFile(path)
            val bookmarkFile = desktopPdfBookmarkFile(path)
            val richTextFile = desktopPdfRichTextFile(path)
            val localTimestamp = maxOf(
                annotationFile.lastModifiedIfFile(),
                bookmarkFile.lastModifiedIfFile(),
                richTextFile.lastModifiedIfFile()
            )
            logDesktopFolderSync(
                "annotation.import.compare book=${book.id} remoteTs=${sidecar.timestamp} localTs=$localTimestamp " +
                    "keys=${sidecar.data.keys.sorted()}"
            )
            if (sidecar.timestamp <= localTimestamp + 1000L) {
                logDesktopFolderSync(
                    "annotation.import.skipOlder book=${book.id} remoteTs=${sidecar.timestamp} localTs=$localTimestamp"
                )
                if (sidecar.data.containsKey("text") || richTextFile.isFile) {
                    SharedPdfRichTextLog.d(
                        "desktop.sync.importSkipOlder book=${book.id} sidecarTs=${sidecar.timestamp} " +
                            "localTs=$localTimestamp hasSidecarText=${sidecar.data.containsKey("text")} " +
                            "richFile=\"${richTextFile.absolutePath.richSyncPreview()}\""
                    )
                }
                return@forEach
            }
            if (sidecar.data.hasPdfAnnotationPayload()) {
                val annotations = SharedPdfAnnotationSidecarCodec.annotationsFromData(sidecar.data)
                if (annotations.isEmpty()) {
                    if (annotationFile.isFile) annotationFile.delete()
                    logDesktopFolderSync("annotation.import.deleteEmptyAnnotations book=${book.id}")
                } else {
                    annotationFile.parentFile?.mkdirs()
                    annotationFile.writeText(SharedPdfAnnotationSerializer.encode(annotations))
                    annotationFile.setLastModified(sidecar.timestamp)
                    logDesktopFolderSync(
                        "annotation.import.writeAnnotations book=${book.id} count=${annotations.size} " +
                            "file=\"${annotationFile.absolutePath.folderSyncPreview()}\""
                    )
                }
            }
            sidecar.data["bookmarks"]?.let { bookmarks ->
                bookmarkFile.parentFile?.mkdirs()
                bookmarkFile.writeText(desktopFolderSyncJson.encodeToString(JsonElement.serializer(), bookmarks))
                bookmarkFile.setLastModified(sidecar.timestamp)
                logDesktopFolderSync(
                    "annotation.import.writeBookmarks book=${book.id} file=\"${bookmarkFile.absolutePath.folderSyncPreview()}\""
                )
            }
            sidecar.data["text"]?.let { richText ->
                val richDocument = SharedPdfRichTextSerializer.decodeElement(richText)
                SharedPdfRichTextLog.d(
                    "desktop.sync.importRichText book=${book.id} timestamp=${sidecar.timestamp} " +
                        "textLen=${richDocument.text.length} spans=${richDocument.spans.size} " +
                        "file=\"${richTextFile.absolutePath.richSyncPreview()}\""
                )
                if (richDocument.text.isEmpty() && richDocument.spans.isEmpty()) {
                    if (richTextFile.isFile) richTextFile.delete()
                    logDesktopFolderSync("annotation.import.deleteEmptyText book=${book.id}")
                } else {
                    richTextFile.parentFile?.mkdirs()
                    richTextFile.writeText(SharedPdfRichTextSerializer.encode(richDocument))
                    richTextFile.setLastModified(sidecar.timestamp)
                    logDesktopFolderSync(
                        "annotation.import.writeText book=${book.id} textLen=${richDocument.text.length} " +
                            "spans=${richDocument.spans.size} file=\"${richTextFile.absolutePath.folderSyncPreview()}\""
                    )
                }
            }
        }
    }

    private fun saveAnnotationSidecar(
        root: File,
        bookId: String,
        jsonPayload: String,
        timestamp: Long
    ) {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR).apply { mkdirs() }
        val data = desktopFolderSyncJson.parseElementOrNull(jsonPayload)?.jsonObjectOrNull()
        if (data == null) {
            logDesktopFolderSync(
                "annotation.save.skipInvalidPayload book=$bookId timestamp=$timestamp " +
                    "root=\"${root.absolutePath.folderSyncPreview()}\" payloadLen=${jsonPayload.length}"
            )
            return
        }
        val existing = resolveAnnotationConflicts(syncDir, bookId, cleanup = true)
        if (existing != null && existing.timestamp >= timestamp) {
            logDesktopFolderSync(
                "annotation.save.skipNewerExisting book=$bookId existingTs=${existing.timestamp} " +
                    "candidateTs=$timestamp root=\"${root.absolutePath.folderSyncPreview()}\" keys=${data.keys.sorted()}"
            )
            if (data.containsKey("text")) {
                SharedPdfRichTextLog.d(
                    "desktop.sync.saveSidecarSkipExisting book=$bookId existingTs=${existing.timestamp} " +
                        "candidateTs=$timestamp targetRoot=\"${root.absolutePath.richSyncPreview()}\""
                )
            }
            return
        }

        val wrapper = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "bookId" to JsonPrimitive(bookId),
                "timestamp" to JsonPrimitive(timestamp),
                "data" to data
            )
        )
        val target = File(syncDir, localFolderSyncAnnotationFileName(bookId))
        val temp = File(syncDir, uniqueFolderSyncTempName(localFolderSyncAnnotationTempFileName(bookId)))
        runCatching {
            temp.writeText(desktopFolderSyncJson.encodeToString(JsonElement.serializer(), wrapper))
            moveReplacing(temp, target)
            logDesktopFolderSync(
                "annotation.save.done book=$bookId timestamp=$timestamp keys=${data.keys.sorted()} " +
                    "target=\"${target.absolutePath.folderSyncPreview()}\" bytes=${target.length()}"
            )
            if (data.containsKey("text")) {
                SharedPdfRichTextLog.d(
                    "desktop.sync.saveSidecar book=$bookId timestamp=$timestamp " +
                        "target=\"${target.absolutePath.richSyncPreview()}\""
                )
            }
        }.onFailure {
            logDesktopFolderSync(
                "annotation.save.failed book=$bookId timestamp=$timestamp keys=${data.keys.sorted()} " +
                    "target=\"${target.absolutePath.folderSyncPreview()}\" error=${it.folderSyncSummary()}"
            )
            if (data.containsKey("text")) {
                SharedPdfRichTextLog.d(
                    "desktop.sync.saveSidecarFailed book=$bookId timestamp=$timestamp " +
                        "target=\"${target.absolutePath.richSyncPreview()}\" error=${it.message}"
                )
            }
            runCatching { temp.delete() }
        }
    }

    private fun resolveAnnotationConflicts(
        syncDir: File,
        bookId: String,
        cleanup: Boolean
    ): AnnotationSidecar? {
        val parsed = syncDir.listFiles().orEmpty()
            .filter { file -> file.isFile && file.isAnnotationSidecarCandidate() }
            .mapNotNull { file ->
                file.readAnnotationSidecarOrNull(fallbackBookId = file.legacyAnnotationBookIdOrNull())
                    ?.takeIf { it.bookId == bookId }
                    ?.let { file to it }
            }
        if (parsed.isEmpty()) return null
        if (parsed.size > 1) {
            logDesktopFolderSync(
                "annotation.conflicts book=$bookId candidates=${parsed.size} " +
                    "dir=\"${syncDir.absolutePath.folderSyncPreview()}\" cleanup=$cleanup"
            )
        }
        val winner = parsed.maxByOrNull { it.second.timestamp } ?: return null

        if (cleanup) {
            parsed.map { it.first }
                .filterNot { it == winner.first }
                .forEach { file ->
                    runCatching { file.delete() }
                    logDesktopFolderSync(
                        "annotation.conflict.deleteLoser book=$bookId file=\"${file.absolutePath.folderSyncPreview()}\""
                    )
                }
            val correctName = localFolderSyncAnnotationFileName(bookId)
            if (winner.first.name != correctName) {
                val target = File(syncDir, correctName)
                runCatching { moveReplacing(winner.first, target) }
                    .onSuccess {
                        logDesktopFolderSync(
                            "annotation.conflict.renameWinner book=$bookId target=\"${target.absolutePath.folderSyncPreview()}\""
                        )
                    }
            }
        }

        return winner.second
    }
}

private data class AnnotationSidecar(
    val bookId: String,
    val timestamp: Long,
    val data: JsonObject
)

private val desktopFolderSyncJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

private fun File.shouldEnterSyncedFolder(): Boolean {
    if (!isDirectory) return false
    if (name == LOCAL_FOLDER_SYNC_DATA_DIR) return false
    if (name.startsWith(".")) return false
    return runCatching { !isHidden }.getOrDefault(true)
}

private fun File.shouldSyncBookFile(): Boolean {
    if (name.startsWith(".")) return false
    if (extension.equals("json", ignoreCase = true)) return false
    return parentFile?.name != LOCAL_FOLDER_SYNC_DATA_DIR
}

private fun File.isMetadataSidecarCandidate(): Boolean {
    val fileName = name
    if (fileName.contains(LOCAL_FOLDER_ANNOTATION_SUFFIX)) return false
    if (fileName.contains(".tmp") || fileName.contains(".syncthing.")) return false
    return fileName.endsWith(".json") || fileName.contains(".sync-conflict")
}

private fun File.isAnnotationSidecarCandidate(): Boolean {
    val fileName = name
    if (!fileName.contains(LOCAL_FOLDER_ANNOTATION_SUFFIX)) return false
    if (fileName.contains(".tmp") || fileName.contains(".syncthing.")) return false
    return fileName.endsWith(".json") || fileName.contains(".sync-conflict")
}

private fun File.legacyAnnotationBookIdOrNull(): String? {
    var candidate = name
    if (!isAnnotationSidecarCandidate()) return null
    if (candidate.contains(".sync-conflict")) {
        candidate = candidate.substringBefore(".sync-conflict")
    }
    candidate = candidate.substringBeforeLast(".json")
    if (candidate.endsWith(LOCAL_FOLDER_ANNOTATION_SUFFIX)) {
        candidate = candidate.substring(0, candidate.length - LOCAL_FOLDER_ANNOTATION_SUFFIX.length)
    }
    val normalized = candidate.removePrefix(".")
    if (normalized.startsWith(LOCAL_FOLDER_SIDECAR_HASH_PREFIX)) return null
    return normalized.takeIf { it.isNotBlank() }
}

private fun File.normalizedSidecarName(): String {
    return name.removePrefix(".")
}

private fun String.matchesJsonSidecarStem(stem: String): Boolean {
    return this == "$stem.json" ||
        startsWith("$stem.sync-conflict") ||
        startsWith("$stem.json.sync-conflict")
}

private fun File.readAnnotationSidecarOrNull(fallbackBookId: String? = null): AnnotationSidecar? {
    return runCatching {
        val root = desktopFolderSyncJson.parseToJsonElement(readText()).jsonObject
        val bookId = root["bookId"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?: fallbackBookId
            ?: error("Missing annotation sidecar bookId")
        val timestamp = root["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
        val data = root["data"]?.jsonObjectOrNull() ?: error("Missing annotation sidecar data")
        AnnotationSidecar(bookId = bookId, timestamp = timestamp, data = data)
    }.onFailure { error ->
        logDesktopFolderSync(
            "annotation.read.parseFailed file=\"${absolutePath.folderSyncPreview()}\" error=${error.folderSyncSummary()}"
        )
    }.getOrNull()
}

private fun Json.parseElementOrNull(raw: String): JsonElement? {
    return runCatching { parseToJsonElement(raw) }.getOrNull()
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? {
    if (this is JsonNull) return null
    return runCatching { jsonObject }.getOrNull()
}

private fun JsonObject.hasPdfAnnotationPayload(): Boolean {
    return containsKey(SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS) ||
        containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_INK) ||
        containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_TEXT_BOXES) ||
        containsKey(SharedPdfAnnotationSidecarCodec.KEY_LEGACY_HIGHLIGHTS)
}

private fun File.canonicalOrAbsolute(): File {
    return runCatching { canonicalFile }.getOrElse { absoluteFile }
}

private fun File.lastModifiedIfFile(): Long {
    return if (isFile) lastModified() else 0L
}

private fun File.hasSyncablePdfAnnotations(): Boolean {
    return isFile && desktopPdfAnnotationElementForSync(readText()) != null
}

private fun File.lastModifiedIfSyncableAnnotations(): Long {
    return if (hasSyncablePdfAnnotations()) lastModified() else 0L
}

private fun File.hasSyncablePdfRichText(): Boolean {
    return isFile && desktopPdfRichTextElementForSync(readText()) != null
}

private fun File.lastModifiedIfSyncableRichText(): Long {
    return if (hasSyncablePdfRichText()) lastModified() else 0L
}

private fun BookItem.toDesktopFolderBookMetadata(): SharedFolderBookMetadata? {
    val base = toSharedFolderBookMetadata()
    val pdfBookmarksJson = desktopPdfBookmarksMetadataJson(this)
    if (base == null && pdfBookmarksJson == null) return null

    val timestamp = maxOf(
        base?.lastModifiedTimestamp ?: 0L,
        desktopPdfBookmarkMetadataTimestamp(this),
        this.timestamp
    )

    return (base ?: SharedFolderBookMetadata(
        bookId = id,
        title = null,
        author = null,
        displayName = displayName,
        type = type.name,
        lastChapterIndex = readerPosition?.chapterIndex,
        lastPage = readerPosition?.pageIndex ?: lastPageIndex,
        lastPositionCfi = readerPosition?.toStablePositionCfi(),
        progressPercentage = progressPercentage ?: 0f,
        isRecent = isRecent,
        lastModifiedTimestamp = timestamp,
        bookmarksJson = null,
        locatorBlockIndex = readerPosition?.blockIndex,
        locatorCharOffset = readerPosition?.charOffset,
        customName = null,
        highlightsJson = null
    )).copy(
        lastModifiedTimestamp = timestamp,
        bookmarksJson = pdfBookmarksJson ?: base?.bookmarksJson
    )
}

private fun uniqueFolderSyncTempName(baseName: String): String {
    val stem = baseName.removeSuffix(".tmp")
    val nonce = "${System.currentTimeMillis()}_${Thread.currentThread().id}_${System.nanoTime().toString(36)}"
    return "$stem.$nonce.tmp"
}

private fun String.richSyncPreview(maxLength: Int = 160): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

private fun moveReplacing(source: File, target: File) {
    target.parentFile?.mkdirs()
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}
