package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderBookmark
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

const val LOCAL_FOLDER_SYNC_DATA_DIR = "EpistemeSyncData"
const val LOCAL_FOLDER_ANNOTATION_SUFFIX = "_annotations"

internal expect fun localFolderSyncSha256ShortHex(value: String): String

data class SharedFolderBookMetadata(
    val bookId: String,
    val title: String?,
    val author: String?,
    val displayName: String,
    val type: String,
    val lastChapterIndex: Int?,
    val lastPage: Int?,
    val lastPositionCfi: String?,
    val progressPercentage: Float,
    val isRecent: Boolean,
    val lastModifiedTimestamp: Long,
    val bookmarksJson: String?,
    val locatorBlockIndex: Int?,
    val locatorCharOffset: Int?,
    val customName: String?,
    val highlightsJson: String?
) {
    fun toJsonString(): String {
        return folderSyncJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(
                mapOf(
                    "bookId" to JsonPrimitive(bookId),
                    "title" to title.asJson(),
                    "author" to author.asJson(),
                    "displayName" to JsonPrimitive(displayName),
                    "type" to JsonPrimitive(type),
                    "lastChapterIndex" to JsonPrimitive(lastChapterIndex ?: -1),
                    "lastPage" to JsonPrimitive(lastPage ?: -1),
                    "lastPositionCfi" to lastPositionCfi.asJson(),
                    "progressPercentage" to JsonPrimitive(progressPercentage.toDouble()),
                    "isRecent" to JsonPrimitive(isRecent),
                    "lastModifiedTimestamp" to JsonPrimitive(lastModifiedTimestamp),
                    "bookmarksJson" to bookmarksJson.asJson(),
                    "locatorBlockIndex" to JsonPrimitive(locatorBlockIndex ?: -1),
                    "locatorCharOffset" to JsonPrimitive(locatorCharOffset ?: -1),
                    "customName" to customName.asJson(),
                    "highlightsJson" to highlightsJson.asJson()
                )
            )
        )
    }

    fun toBookItem(
        file: SharedFolderScannedFile,
        existing: BookItem? = null,
        nowMillis: Long = currentTimestamp()
    ): BookItem {
        val parsedHighlights = highlightsJson
            ?.let(EpubAnnotationSerializer::parseHighlightsJson)
            ?.takeIf { it.isNotEmpty() }
        val parsedBookmarks = parseReaderBookmarks(bookId)
            .takeIf { it.isNotEmpty() }
        val parsedType = runCatching { FileType.valueOf(type) }.getOrNull() ?: file.type
        val metadataTimestamp = lastModifiedTimestamp.takeIf { it > 0L } ?: nowMillis

        return (existing ?: BookItem(
            id = bookId,
            path = file.path,
            type = parsedType,
            displayName = displayName.ifBlank { file.name },
            timestamp = metadataTimestamp,
            title = title ?: displayName.ifBlank { file.name },
            author = author,
            fileSize = file.size,
            sourceFolder = file.sourceFolder,
            isRecent = isRecent
        )).copy(
            id = bookId,
            path = file.path,
            type = parsedType,
            displayName = displayName.ifBlank { file.name },
            timestamp = if (isRecent || existing == null) metadataTimestamp else existing.timestamp,
            coverImagePath = existing?.coverImagePath,
            title = title ?: existing?.title ?: displayName.ifBlank { file.name },
            author = author ?: existing?.author,
            progressPercentage = progressPercentage,
            isRecent = isRecent || (existing?.isRecent ?: false),
            fileSize = file.size.takeIf { it > 0L } ?: existing?.fileSize ?: 0L,
            sourceFolder = file.sourceFolder,
            folderTextMetadataParsed = existing?.folderTextMetadataParsed ?: false,
            lastPageIndex = lastPage,
            readerBookmarks = parsedBookmarks ?: existing?.readerBookmarks.orEmpty(),
            readerHighlights = parsedHighlights ?: existing?.readerHighlights.orEmpty()
        )
    }

    private fun parseReaderBookmarks(bookId: String): List<ReaderBookmark> {
        return EpubAnnotationSerializer.parseBookmarksJson(bookmarksJson)
            .mapIndexed { index, bookmark ->
                val locator = bookmark.locator.withFallbacks(
                    chapterIndex = bookmark.chapterIndex,
                    cfi = bookmark.cfi,
                    pageIndex = bookmark.pageInChapter?.minus(1),
                    textQuote = bookmark.snippet
                )
                val pageIndex = locator.pageIndex ?: bookmark.pageInChapter?.minus(1) ?: 0
                ReaderBookmark(
                    id = "bookmark_${localFolderSyncSha256ShortHex("$bookId:$index:${bookmark.cfi}")}",
                    pageIndex = pageIndex.coerceAtLeast(0),
                    chapterTitle = bookmark.chapterTitle,
                    preview = bookmark.snippet,
                    locator = locator
                )
            }
    }

    companion object {
        fun fromJsonString(rawJson: String): SharedFolderBookMetadata? {
            val obj = runCatching { folderSyncJson.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return null
            val bookId = obj.string("bookId")?.takeIf { it.isNotBlank() } ?: return null
            return SharedFolderBookMetadata(
                bookId = bookId,
                title = obj.string("title"),
                author = obj.string("author"),
                displayName = obj.string("displayName") ?: "Unknown",
                type = obj.string("type") ?: FileType.PDF.name,
                lastChapterIndex = obj.sentinelInt("lastChapterIndex"),
                lastPage = obj.sentinelInt("lastPage"),
                lastPositionCfi = obj.string("lastPositionCfi"),
                progressPercentage = obj.double("progressPercentage")?.toFloat() ?: 0f,
                isRecent = obj.boolean("isRecent") ?: true,
                lastModifiedTimestamp = obj.long("lastModifiedTimestamp") ?: 0L,
                bookmarksJson = obj.string("bookmarksJson"),
                locatorBlockIndex = obj.sentinelInt("locatorBlockIndex"),
                locatorCharOffset = obj.sentinelInt("locatorCharOffset"),
                customName = obj.string("customName"),
                highlightsJson = obj.string("highlightsJson")
            )
        }
    }
}

data class SharedFolderScannedFile(
    val name: String,
    val path: String,
    val sourceFolder: String,
    val relativePath: String,
    val type: FileType,
    val size: Long,
    val lastModified: Long
) {
    val stableBookId: String
        get() = LocalFolderSyncEngine.buildStableBookId(name, relativePath)
}

data class LocalFolderSyncStats(
    val scannedFiles: Int = 0,
    val supportedFiles: Int = 0,
    val newBooks: Int = 0,
    val updatedBooks: Int = 0,
    val unchangedBooks: Int = 0,
    val removedBooks: Int = 0,
    val migratedBooks: Int = 0,
    val remoteMetadataUpdates: Int = 0
) {
    operator fun plus(other: LocalFolderSyncStats): LocalFolderSyncStats {
        return LocalFolderSyncStats(
            scannedFiles = scannedFiles + other.scannedFiles,
            supportedFiles = supportedFiles + other.supportedFiles,
            newBooks = newBooks + other.newBooks,
            updatedBooks = updatedBooks + other.updatedBooks,
            unchangedBooks = unchangedBooks + other.unchangedBooks,
            removedBooks = removedBooks + other.removedBooks,
            migratedBooks = migratedBooks + other.migratedBooks,
            remoteMetadataUpdates = remoteMetadataUpdates + other.remoteMetadataUpdates
        )
    }
}

data class LocalFolderSyncResult(
    val state: SharedReaderScreenState,
    val idMigrations: Map<String, String>,
    val removedBookIds: Set<String>,
    val stats: LocalFolderSyncStats
)

object LocalFolderSyncEngine {
    fun buildStableBookId(name: String, relativePath: String): String {
        val normalizedRelativePath = relativePath.toSyncRelativePath().ifBlank { name }
        return if (normalizedRelativePath.equals(name, ignoreCase = true)) {
            "local_$name"
        } else {
            "local_${name}_${localFolderSyncSha256ShortHex(normalizedRelativePath.lowercase())}"
        }
    }

    fun syncFolder(
        state: SharedReaderScreenState,
        folder: SyncedFolder,
        files: List<SharedFolderScannedFile>,
        remoteMetadata: Map<String, SharedFolderBookMetadata>,
        nowMillis: Long = currentTimestamp(),
        metadataOnly: Boolean = false
    ): LocalFolderSyncResult {
        val folderRoot = folder.uriString
        val allowedTypes = folder.allowedFileTypes
        val booksById = linkedMapOf<String, BookItem>()
        state.rawLibraryBooks.forEach { booksById[it.id] = it }
        val idMigrations = linkedMapOf<String, String>()
        var stats = LocalFolderSyncStats(
            scannedFiles = files.size,
            supportedFiles = files.count { it.type in allowedTypes }
        )
        var removedIds = emptySet<String>()

        val existingFolderBookIds = booksById.values
            .filter { it.sourceFolder == folderRoot }
            .mapTo(linkedSetOf()) { it.id }

        remoteMetadata.forEach { (bookId, metadata) ->
            val existing = booksById[bookId]?.takeIf { it.sourceFolder == folderRoot }
            if (existing != null && metadata.lastModifiedTimestamp > existing.localFolderModifiedTimestamp()) {
                booksById[bookId] = existing.withAppliedFolderMetadata(metadata, nowMillis)
                stats = stats.copy(remoteMetadataUpdates = stats.remoteMetadataUpdates + 1)
            }
        }

        if (!metadataOnly) {
            val foundBookIds = linkedSetOf<String>()
            val folderBooksByPath = booksById.values
                .filter { it.sourceFolder == folderRoot && !it.path.isNullOrBlank() }
                .associateBy { it.path.orEmpty() }
                .toMutableMap()
            val legacyItemsByName = booksById.values
                .asSequence()
                .filter { it.sourceFolder == folderRoot }
                .filter { it.id.startsWith("local_${it.displayName}_") || it.id == it.path }
                .groupBy { it.displayName }
                .mapValues { (_, books) -> ArrayDeque<BookItem>().apply { addAll(books) } }
                .toMutableMap()

            files
                .asSequence()
                .filter { it.type in allowedTypes }
                .sortedBy { it.relativePath.lowercase() }
                .forEach { file ->
                    val stableId = file.stableBookId
                    foundBookIds += stableId
                    var existing = booksById[stableId]?.takeIf { it.sourceFolder == folderRoot }

                    if (existing == null) {
                        val migrated = folderBooksByPath[file.path]?.takeIf { it.id != stableId }
                            ?: legacyItemsByName[file.name]?.firstOrNull { it.id != stableId }
                        if (migrated != null) {
                            val oldId = migrated.id
                            val migratedBook = migrated.copy(id = stableId).withScannedFile(file)
                            booksById.remove(oldId)
                            booksById[stableId] = migratedBook
                            idMigrations[oldId] = stableId
                            legacyItemsByName[file.name]?.remove(migrated)
                            existing = migratedBook
                            stats = stats.copy(migratedBooks = stats.migratedBooks + 1)
                        }
                    }

                    val metadata = remoteMetadata[stableId]
                    if (existing == null) {
                        booksById[stableId] = metadata?.toBookItem(file, nowMillis = nowMillis)
                            ?: file.toBookItem(stableId, nowMillis)
                        stats = stats.copy(newBooks = stats.newBooks + 1)
                    } else {
                        val updatedForFile = existing.withScannedFile(file)
                        val updated = metadata
                            ?.takeIf { it.lastModifiedTimestamp > updatedForFile.localFolderModifiedTimestamp() }
                            ?.toBookItem(file = file, existing = updatedForFile, nowMillis = nowMillis)
                            ?: updatedForFile
                        booksById[stableId] = updated
                        if (updated != existing) {
                            stats = stats.copy(updatedBooks = stats.updatedBooks + 1)
                        } else {
                            stats = stats.copy(unchangedBooks = stats.unchangedBooks + 1)
                        }
                    }
                }

            removedIds = existingFolderBookIds
                .map { idMigrations[it] ?: it }
                .filter { it !in foundBookIds }
                .toSet()
            removedIds.forEach(booksById::remove)
            stats = stats.copy(removedBooks = removedIds.size)
        }

        val syncedFolder = folder.copy(lastScanTime = nowMillis)
        val syncedFolders = (state.syncedFolders.filterNot { it.uriString == folderRoot } + syncedFolder)
            .sortedBy { it.name.lowercase() }
        val migratedState = state
            .withMigratedBookIds(idMigrations)
        val nextState = migratedState
            .withoutBookIds(removedIds)
            .copy(
                rawLibraryBooks = booksById.values.toList(),
                syncedFolders = syncedFolders,
                lastFolderScanTime = nowMillis
            )

        return LocalFolderSyncResult(
            state = nextState,
            idMigrations = idMigrations,
            removedBookIds = removedIds,
            stats = stats
        )
    }

    fun applyIdMigrationsToShelfRefs(
        shelfRefs: List<BookShelfRef>,
        migrations: Map<String, String>
    ): List<BookShelfRef> {
        if (migrations.isEmpty()) return shelfRefs
        return shelfRefs.map { ref ->
            migrations[ref.bookId]?.let { ref.copy(bookId = it) } ?: ref
        }.distinctBy { it.bookId to it.shelfId }
    }
}

fun BookItem.toSharedFolderBookMetadata(): SharedFolderBookMetadata? {
    if (sourceFolder.isNullOrBlank()) return null

    val bookmarksJson = readerBookmarks
        .mapNotNull { it.toEpubBookmarkOrNull() }
        .takeIf { it.isNotEmpty() }
        ?.let(EpubAnnotationSerializer::bookmarksToJson)
    val highlightsJson = readerHighlights
        .takeIf { it.isNotEmpty() }
        ?.let(EpubAnnotationSerializer::highlightsToJson)
    val hasProgress = (progressPercentage ?: 0f) > 0f || lastPageIndex != null
    val isDirty = isRecent || hasProgress || !bookmarksJson.isNullOrBlank() || !highlightsJson.isNullOrBlank()
    if (!isDirty) return null

    return SharedFolderBookMetadata(
        bookId = id,
        title = title,
        author = author,
        displayName = displayName,
        type = type.name,
        lastChapterIndex = null,
        lastPage = lastPageIndex,
        lastPositionCfi = null,
        progressPercentage = progressPercentage ?: 0f,
        isRecent = isRecent,
        lastModifiedTimestamp = localFolderModifiedTimestamp(),
        bookmarksJson = bookmarksJson,
        locatorBlockIndex = null,
        locatorCharOffset = null,
        customName = null,
        highlightsJson = highlightsJson
    )
}

private val folderSyncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun BookItem.withAppliedFolderMetadata(
    metadata: SharedFolderBookMetadata,
    nowMillis: Long
): BookItem {
    val file = SharedFolderScannedFile(
        name = displayName,
        path = path.orEmpty(),
        sourceFolder = sourceFolder.orEmpty(),
        relativePath = displayName,
        type = runCatching { FileType.valueOf(metadata.type) }.getOrNull() ?: type,
        size = fileSize,
        lastModified = 0L
    )
    return metadata.toBookItem(file = file, existing = this, nowMillis = nowMillis)
}

private fun SharedFolderScannedFile.toBookItem(bookId: String, nowMillis: Long): BookItem {
    return BookItem(
        id = bookId,
        path = path,
        type = type,
        displayName = name,
        timestamp = nowMillis,
        title = name.substringBeforeLast('.', missingDelimiterValue = name),
        fileSize = size,
        sourceFolder = sourceFolder,
        isRecent = false
    )
}

private fun BookItem.withScannedFile(file: SharedFolderScannedFile): BookItem {
    val sizeChanged = fileSize > 0L && file.size > 0L && fileSize != file.size
    return copy(
        path = file.path,
        type = file.type,
        displayName = file.name,
        coverImagePath = if (sizeChanged) null else coverImagePath,
        fileSize = file.size.takeIf { it > 0L } ?: fileSize,
        sourceFolder = file.sourceFolder,
        folderTextMetadataParsed = if (sizeChanged) false else folderTextMetadataParsed
    )
}

private fun BookItem.localFolderModifiedTimestamp(): Long {
    return timestamp
}

private fun ReaderBookmark.toEpubBookmarkOrNull(): EpubBookmark? {
    val chapterIndex = locator.chapterIndex ?: 0
    val cfi = locator.cfi ?: "desktop:$chapterIndex:$pageIndex"
    return EpubBookmark(
        cfi = cfi,
        chapterTitle = chapterTitle,
        label = null,
        snippet = preview,
        pageInChapter = pageIndex + 1,
        totalPagesInChapter = null,
        chapterIndex = chapterIndex,
        locator = locator.withFallbacks(
            chapterIndex = chapterIndex,
            cfi = cfi,
            pageIndex = pageIndex,
            textQuote = preview
        )
    )
}

private fun SharedReaderScreenState.withMigratedBookIds(
    migrations: Map<String, String>
): SharedReaderScreenState {
    if (migrations.isEmpty()) return this

    fun String.migrated(): String = migrations[this] ?: this
    fun Set<String>.migrated(): Set<String> = mapTo(linkedSetOf()) { it.migrated() }

    return copy(
        selectedBookIds = selectedBookIds.migrated(),
        booksSelectedForAdding = booksSelectedForAdding.migrated(),
        pinnedHomeBookIds = pinnedHomeBookIds.migrated(),
        pinnedLibraryBookIds = pinnedLibraryBookIds.migrated(),
        openTabIds = openTabIds.map { it.migrated() }.distinct(),
        activeTabBookId = activeTabBookId?.migrated(),
        selectedBookId = selectedBookId?.migrated()
    )
}

private fun SharedReaderScreenState.withoutBookIds(bookIds: Set<String>): SharedReaderScreenState {
    if (bookIds.isEmpty()) return this
    return copy(
        selectedBookIds = selectedBookIds - bookIds,
        booksSelectedForAdding = booksSelectedForAdding - bookIds,
        pinnedHomeBookIds = pinnedHomeBookIds - bookIds,
        pinnedLibraryBookIds = pinnedLibraryBookIds - bookIds,
        openTabIds = openTabIds.filterNot { it in bookIds },
        activeTabBookId = activeTabBookId?.takeUnless { it in bookIds },
        selectedBookId = selectedBookId?.takeUnless { it in bookIds }
    )
}

private fun String.toSyncRelativePath(): String {
    return replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() && it != "." }
        .joinToString("/")
}

private fun JsonObject.string(name: String): String? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull }.getOrNull()
}

private fun JsonObject.long(name: String): Long? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull }.getOrNull()
}

private fun JsonObject.double(name: String): Double? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull }.getOrNull()
}

private fun JsonObject.boolean(name: String): Boolean? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull }.getOrNull()
}

private fun JsonObject.sentinelInt(name: String): Int? {
    val value = runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull }.getOrNull()
    return value?.takeUnless { it == -1 }
}

private fun String?.asJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
