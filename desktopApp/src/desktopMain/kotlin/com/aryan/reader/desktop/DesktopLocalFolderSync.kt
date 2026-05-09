package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LOCAL_FOLDER_ANNOTATION_SUFFIX
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.LocalFolderSyncEngine
import com.aryan.reader.shared.LocalFolderSyncStats
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedFolderBookMetadata
import com.aryan.reader.shared.SharedFolderScannedFile
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfRichTextLog
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.toSharedFolderBookMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    val failedFolders: List<String> = emptyList()
)

object DesktopLocalFolderSync {
    private val desktopSyncableTypes = SharedFileCapabilities.syncableTypesFor(ReaderPlatform.DESKTOP)

    fun hasSupportedFiles(folder: File): Boolean {
        if (!folder.isDirectory) return false
        return folder.walkTopDown()
            .onEnter { it == folder || it.shouldEnterSyncedFolder() }
            .any { file ->
                file.isFile &&
                    file.shouldSyncBookFile() &&
                    SharedFileCapabilities.fileTypeForName(file.name) in desktopSyncableTypes
            }
    }

    fun sync(
        state: SharedReaderScreenState,
        shelfRefs: List<BookShelfRef>,
        targetFolder: File? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): DesktopLocalFolderSyncResult {
        val requestedFolders = foldersToSync(state, targetFolder, nowMillis)
        var nextState = state
        var nextShelfRefs = shelfRefs
        var totalStats = LocalFolderSyncStats()
        var totalMetadataStats = DesktopFolderMetadataExtractionStats()
        val allMigrations = linkedMapOf<String, String>()
        val allRemovedBookIds = linkedSetOf<String>()
        val failedFolders = mutableListOf<String>()

        requestedFolders.forEach { folder ->
            val root = File(folder.uriString)
            if (!root.isDirectory) {
                failedFolders += folder.name
                return@forEach
            }

            val scannedFiles = scanFolder(root = root, sourceFolder = folder.uriString)
            val remoteMetadata = readAllMetadata(root)
            val syncResult = LocalFolderSyncEngine.syncFolder(
                state = nextState,
                folder = folder,
                files = scannedFiles,
                remoteMetadata = remoteMetadata,
                nowMillis = nowMillis
            )
            nextState = syncResult.state
            nextShelfRefs = LocalFolderSyncEngine.applyIdMigrationsToShelfRefs(
                nextShelfRefs,
                syncResult.idMigrations
            ).filterNot { it.bookId in syncResult.removedBookIds }
            allMigrations += syncResult.idMigrations
            allRemovedBookIds += syncResult.removedBookIds
            totalStats += syncResult.stats

            var syncedBooks = nextState.rawLibraryBooks.filter { it.sourceFolder == folder.uriString }
            importAnnotationSidecars(root, syncedBooks)
            val metadataResult = DesktopFolderMetadataExtractor.enrichFolderBooks(
                books = nextState.rawLibraryBooks,
                sourceFolder = folder.uriString
            )
            if (metadataResult.stats.updatedBooks > 0) {
                nextState = nextState.copy(rawLibraryBooks = metadataResult.books)
                syncedBooks = nextState.rawLibraryBooks.filter { it.sourceFolder == folder.uriString }
            }
            totalMetadataStats += metadataResult.stats
            syncedBooks.forEach { book ->
                saveBookMetadata(book)
                savePdfAnnotationSidecar(book)
            }
        }

        return DesktopLocalFolderSyncResult(
            state = nextState,
            shelfRefs = nextShelfRefs,
            stats = totalStats,
            metadataStats = totalMetadataStats,
            idMigrations = allMigrations,
            removedBookIds = allRemovedBookIds,
            failedFolders = failedFolders
        )
    }

    fun saveBookSidecars(book: BookItem) {
        saveBookMetadata(book)
        savePdfAnnotationSidecar(book)
    }

    fun saveBookMetadata(book: BookItem) {
        val metadata = book.toSharedFolderBookMetadata() ?: return
        val root = book.sourceFolder?.let(::File)?.takeIf { it.isDirectory } ?: return
        saveMetadataToFolder(root, metadata)
    }

    fun savePdfAnnotationSidecar(book: BookItem) {
        val path = book.path?.takeIf { it.isNotBlank() } ?: return
        if (book.type != FileType.PDF) return
        val root = book.sourceFolder?.let(::File)?.takeIf { it.isDirectory } ?: return
        val annotationFile = desktopPdfAnnotationFile(path)
        val bookmarkFile = desktopPdfBookmarkFile(path)
        val richTextFile = desktopPdfRichTextFile(path)
        val data = buildMap {
            if (annotationFile.isFile) {
                val annotationJson = annotationFile.readText().trim()
                val annotations = SharedPdfAnnotationSerializer.decode(annotationJson)
                put(
                    SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS,
                    SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(annotations)
                )
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
                    put("text", SharedPdfRichTextSerializer.encodeElement(richTextDocument))
                }
            }
        }
        if (data.isEmpty()) {
            SharedPdfRichTextLog.d("desktop.sync.exportSkipNoSidecarData book=${book.id} pdfPath=\"${path.richSyncPreview()}\"")
            return
        }
        val timestamp = maxOf(
            annotationFile.lastModifiedIfFile(),
            bookmarkFile.lastModifiedIfFile(),
            richTextFile.lastModifiedIfFile(),
            System.currentTimeMillis()
        )
        val dataJson = desktopFolderSyncJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(data)
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
            .filter { it.isFile && it.shouldSyncBookFile() }
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
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }
            .toList()
    }

    private fun readAllMetadata(root: File): Map<String, SharedFolderBookMetadata> {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR)
        if (!syncDir.isDirectory) return emptyMap()
        return syncDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .mapNotNull { file -> file.metadataBookIdOrNull()?.let { it to file } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (bookId, files) ->
                val best = files
                    .mapNotNull { file ->
                        runCatching { SharedFolderBookMetadata.fromJsonString(file.readText()) }.getOrNull()
                    }
                    .filter { it.bookId == bookId }
                    .maxByOrNull { it.lastModifiedTimestamp }
                best?.let { bookId to it }
            }
            .toMap()
    }

    private fun saveMetadataToFolder(root: File, metadata: SharedFolderBookMetadata) {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR).apply { mkdirs() }
        val existing = resolveMetadataConflicts(syncDir, metadata.bookId, cleanup = true)
        if (existing != null && existing.lastModifiedTimestamp > metadata.lastModifiedTimestamp) return

        val target = File(syncDir, ".${metadata.bookId}.json")
        val temp = File(syncDir, ".${metadata.bookId}.tmp")
        runCatching {
            temp.writeText(metadata.toJsonString())
            moveReplacing(temp, target)
        }.onFailure {
            runCatching { temp.delete() }
        }
    }

    private fun resolveMetadataConflicts(
        syncDir: File,
        bookId: String,
        cleanup: Boolean
    ): SharedFolderBookMetadata? {
        val candidates = syncDir.listFiles().orEmpty().filter { file ->
            val normalized = file.name.removePrefix(".")
            file.isFile && (
                normalized == "$bookId.json" ||
                    normalized.startsWith("$bookId.sync-conflict") ||
                    normalized.startsWith("$bookId.json.sync-conflict")
                )
        }
        if (candidates.isEmpty()) return null

        val parsed = candidates.mapNotNull { file ->
            val metadata = runCatching { SharedFolderBookMetadata.fromJsonString(file.readText()) }.getOrNull()
            metadata?.takeIf { it.bookId == bookId }?.let { file to it }
        }
        val winner = parsed.maxByOrNull { it.second.lastModifiedTimestamp } ?: return null

        if (cleanup) {
            candidates
                .filterNot { it == winner.first }
                .forEach { runCatching { it.delete() } }
            val correctName = ".${bookId}.json"
            if (winner.first.name != correctName) {
                runCatching { moveReplacing(winner.first, File(syncDir, correctName)) }
            }
        }

        return winner.second
    }

    private fun preloadAnnotationSidecars(root: File): Map<String, AnnotationSidecar> {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR)
        if (!syncDir.isDirectory) return emptyMap()
        return syncDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .mapNotNull { file -> file.annotationBookIdOrNull()?.let { it to file } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (bookId, files) ->
                val best = files
                    .mapNotNull { it.readAnnotationSidecarOrNull() }
                    .maxByOrNull { it.timestamp }
                best?.let { bookId to it }
            }
            .toMap()
    }

    private fun importAnnotationSidecars(root: File, books: List<BookItem>) {
        if (books.isEmpty()) return
        val sidecars = preloadAnnotationSidecars(root)
        if (sidecars.isEmpty()) return

        books.forEach { book ->
            val path = book.path?.takeIf { it.isNotBlank() } ?: return@forEach
            if (book.type != FileType.PDF) return@forEach
            val sidecar = sidecars[book.id] ?: return@forEach
            val annotationFile = desktopPdfAnnotationFile(path)
            val bookmarkFile = desktopPdfBookmarkFile(path)
            val richTextFile = desktopPdfRichTextFile(path)
            val localTimestamp = maxOf(
                annotationFile.lastModifiedIfFile(),
                bookmarkFile.lastModifiedIfFile(),
                richTextFile.lastModifiedIfFile()
            )
            if (sidecar.timestamp <= localTimestamp + 1000L) {
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
                annotationFile.parentFile?.mkdirs()
                annotationFile.writeText(SharedPdfAnnotationSerializer.encode(annotations))
                annotationFile.setLastModified(sidecar.timestamp)
            }
            sidecar.data["bookmarks"]?.let { bookmarks ->
                bookmarkFile.parentFile?.mkdirs()
                bookmarkFile.writeText(desktopFolderSyncJson.encodeToString(JsonElement.serializer(), bookmarks))
                bookmarkFile.setLastModified(sidecar.timestamp)
            }
            sidecar.data["text"]?.let { richText ->
                val richDocument = SharedPdfRichTextSerializer.decodeElement(richText)
                SharedPdfRichTextLog.d(
                    "desktop.sync.importRichText book=${book.id} timestamp=${sidecar.timestamp} " +
                        "textLen=${richDocument.text.length} spans=${richDocument.spans.size} " +
                        "file=\"${richTextFile.absolutePath.richSyncPreview()}\""
                )
                richTextFile.parentFile?.mkdirs()
                richTextFile.writeText(SharedPdfRichTextSerializer.encode(richDocument))
                richTextFile.setLastModified(sidecar.timestamp)
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
        val data = desktopFolderSyncJson.parseElementOrNull(jsonPayload)?.jsonObjectOrNull() ?: return
        val existing = resolveAnnotationConflicts(syncDir, bookId, cleanup = true)
        if (existing != null && existing.timestamp >= timestamp) {
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
                "timestamp" to JsonPrimitive(timestamp),
                "data" to data
            )
        )
        val target = File(syncDir, ".${bookId}${LOCAL_FOLDER_ANNOTATION_SUFFIX}.json")
        val temp = File(syncDir, ".${bookId}${LOCAL_FOLDER_ANNOTATION_SUFFIX}.tmp")
        runCatching {
            temp.writeText(desktopFolderSyncJson.encodeToString(JsonElement.serializer(), wrapper))
            moveReplacing(temp, target)
            if (data.containsKey("text")) {
                SharedPdfRichTextLog.d(
                    "desktop.sync.saveSidecar book=$bookId timestamp=$timestamp " +
                        "target=\"${target.absolutePath.richSyncPreview()}\""
                )
            }
        }.onFailure {
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
        val candidates = syncDir.listFiles().orEmpty().filter { file ->
            file.isFile && file.annotationBookIdOrNull() == bookId
        }
        if (candidates.isEmpty()) return null
        val parsed = candidates.mapNotNull { file ->
            file.readAnnotationSidecarOrNull()?.let { file to it }
        }
        val winner = parsed.maxByOrNull { it.second.timestamp } ?: return null

        if (cleanup) {
            candidates
                .filterNot { it == winner.first }
                .forEach { runCatching { it.delete() } }
            val correctName = ".${bookId}${LOCAL_FOLDER_ANNOTATION_SUFFIX}.json"
            if (winner.first.name != correctName) {
                runCatching { moveReplacing(winner.first, File(syncDir, correctName)) }
            }
        }

        return winner.second
    }
}

private data class AnnotationSidecar(
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

private fun File.metadataBookIdOrNull(): String? {
    val fileName = name
    if (fileName.contains(LOCAL_FOLDER_ANNOTATION_SUFFIX)) return null
    if (fileName.endsWith(".tmp") || fileName.contains(".syncthing.")) return null
    if (!fileName.endsWith(".json") && !fileName.contains(".sync-conflict")) return null
    val normalized = fileName.removePrefix(".")
    val base = if (normalized.contains(".sync-conflict")) {
        normalized.substringBefore(".sync-conflict")
    } else {
        normalized.substringBeforeLast(".json")
    }
    return base.removeSuffix(".json").takeIf { it.isNotBlank() }
}

private fun File.annotationBookIdOrNull(): String? {
    var candidate = name
    if (!candidate.contains(LOCAL_FOLDER_ANNOTATION_SUFFIX)) return null
    if (!candidate.endsWith(".json") || candidate.endsWith(".tmp")) return null
    if (candidate.contains(".syncthing.")) return null
    if (candidate.contains(".sync-conflict")) {
        candidate = candidate.substringBefore(".sync-conflict")
    }
    candidate = candidate.substringBeforeLast(".json")
    if (candidate.endsWith(LOCAL_FOLDER_ANNOTATION_SUFFIX)) {
        candidate = candidate.substring(0, candidate.length - LOCAL_FOLDER_ANNOTATION_SUFFIX.length)
    }
    return candidate.removePrefix(".").takeIf { it.isNotBlank() }
}

private fun File.readAnnotationSidecarOrNull(): AnnotationSidecar? {
    return runCatching {
        val root = desktopFolderSyncJson.parseToJsonElement(readText()).jsonObject
        val timestamp = root["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
        val data = root["data"]?.jsonObjectOrNull() ?: error("Missing annotation sidecar data")
        AnnotationSidecar(timestamp = timestamp, data = data)
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
