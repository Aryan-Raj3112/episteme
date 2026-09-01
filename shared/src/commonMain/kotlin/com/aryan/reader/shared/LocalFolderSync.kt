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
const val LOCAL_FOLDER_SIDECAR_HASH_PREFIX = "book_"
/** Version that makes null-valued metadata fields explicit clears. */
const val LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION = 2

internal expect fun localFolderSyncSha256Hex(value: String): String

internal fun localFolderSyncSha256ShortHex(value: String): String =
    localFolderSyncSha256Hex(value).take(12)

fun localFolderSyncSidecarStem(bookId: String): String {
    return LOCAL_FOLDER_SIDECAR_HASH_PREFIX + localFolderSyncSha256ShortHex(bookId)
}

fun localFolderSyncMetadataFileName(bookId: String): String {
    return ".${localFolderSyncSidecarStem(bookId)}.json"
}

fun localFolderSyncMetadataTempFileName(bookId: String): String {
    return ".${localFolderSyncSidecarStem(bookId)}.tmp"
}

fun localFolderSyncAnnotationFileName(bookId: String): String {
    return ".${localFolderSyncSidecarStem(bookId)}$LOCAL_FOLDER_ANNOTATION_SUFFIX.json"
}

fun localFolderSyncAnnotationTempFileName(bookId: String): String {
    return ".${localFolderSyncSidecarStem(bookId)}$LOCAL_FOLDER_ANNOTATION_SUFFIX.tmp"
}

data class SharedFolderBookMetadata(
    val bookId: String,
    val title: String? = null,
    val author: String? = null,
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
    val highlightsJson: String?,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
    val originalTitle: String? = null,
    val originalAuthor: String? = null,
    val originalSeriesName: String? = null,
    val originalSeriesIndex: Double? = null,
    val originalDescription: String? = null,
    /** v1 sidecars omitted several fields; v2 records their presence. */
    val schemaVersion: Int = LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION,
    val presentFields: Set<String> = emptySet(),
) {
    fun toJsonString(): String {
        return folderSyncJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(
                mapOf(
                    "bookId" to JsonPrimitive(bookId),
                    "schemaVersion" to JsonPrimitive(LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION),
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
                    "highlightsJson" to highlightsJson.asJson(),
                    "seriesName" to seriesName.asJson(),
                    "seriesIndex" to (seriesIndex?.let { JsonPrimitive(it) } ?: JsonNull),
                    "description" to description.asJson(),
                    "originalTitle" to originalTitle.asJson(),
                    "originalAuthor" to originalAuthor.asJson(),
                    "originalSeriesName" to originalSeriesName.asJson(),
                    "originalSeriesIndex" to (originalSeriesIndex?.let { JsonPrimitive(it) } ?: JsonNull),
                    "originalDescription" to originalDescription.asJson()
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
        val parsedHighlightsWithClear = if (fieldIsPresent("highlightsJson", highlightsJson)) {
            highlightsJson?.let(EpubAnnotationSerializer::parseHighlightsJson).orEmpty()
        } else {
            parsedHighlights
        }
        val parsedBookmarks = if (fieldIsPresent("bookmarksJson", bookmarksJson)) {
            parseReaderBookmarks(bookId)
        } else {
            parseReaderBookmarks(bookId).takeIf { it.isNotEmpty() }
        }
        val parsedType = runCatching { FileType.valueOf(type) }.getOrNull() ?: file.type
        val metadataTimestamp = lastModifiedTimestamp.takeIf { it > 0L } ?: nowMillis
        val parsedReaderPosition = readerPositionOrNull()
        val positionFieldsPresent = schemaVersion >= LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION &&
            setOf("lastChapterIndex", "lastPage", "lastPositionCfi", "locatorBlockIndex", "locatorCharOffset", "progressPercentage")
                .any(presentFields::contains)

        return (existing ?: BookItem(
            id = bookId,
            path = file.path,
            type = parsedType,
            displayName = displayName.ifBlank { file.name },
            timestamp = metadataTimestamp,
            title = title ?: file.name.substringBeforeLast('.', missingDelimiterValue = file.name),
            author = author,
            fileSize = file.size,
            fileContentModifiedTimestamp = file.lastModified,
            sourceFolder = file.sourceFolder,
            isRecent = isRecent
        )).copy(
            id = bookId,
            path = file.path,
            type = parsedType,
            displayName = displayName.ifBlank { file.name },
            timestamp = if (isRecent || existing == null) metadataTimestamp else existing.timestamp,
            coverImagePath = existing?.coverImagePath,
            title = if (fieldIsPresent("title", title)) title else existing?.title ?: file.name.substringBeforeLast('.', missingDelimiterValue = file.name),
            author = if (fieldIsPresent("author", author)) author else existing?.author,
            description = if (fieldIsPresent("description", description)) description else existing?.description,
            originalTitle = if (fieldIsPresent("originalTitle", originalTitle)) originalTitle else existing?.originalTitle,
            originalAuthor = if (fieldIsPresent("originalAuthor", originalAuthor)) originalAuthor else existing?.originalAuthor,
            originalSeriesName = if (fieldIsPresent("originalSeriesName", originalSeriesName)) originalSeriesName else existing?.originalSeriesName,
            originalSeriesIndex = if (fieldIsPresent("originalSeriesIndex", originalSeriesIndex)) originalSeriesIndex else existing?.originalSeriesIndex,
            originalDescription = if (fieldIsPresent("originalDescription", originalDescription)) originalDescription else existing?.originalDescription,
            progressPercentage = if (positionFieldsPresent) progressPercentage else existing?.progressPercentage ?: progressPercentage,
            isRecent = isRecent || (existing?.isRecent ?: false),
            fileSize = file.size.takeIf { it > 0L } ?: existing?.fileSize ?: 0L,
            fileContentModifiedTimestamp = file.lastModified.takeIf { it > 0L } ?: existing?.fileContentModifiedTimestamp ?: 0L,
            sourceFolder = file.sourceFolder,
            folderTextMetadataParsed = existing?.folderTextMetadataParsed ?: false,
            seriesName = if (fieldIsPresent("seriesName", seriesName)) seriesName else existing?.seriesName,
            seriesIndex = if (fieldIsPresent("seriesIndex", seriesIndex)) seriesIndex else existing?.seriesIndex,
            lastPageIndex = if (positionFieldsPresent) lastPage else existing?.lastPageIndex ?: lastPage,
            readerPosition = if (positionFieldsPresent) parsedReaderPosition else parsedReaderPosition ?: existing?.readerPosition,
            readingPositionModifiedTimestamp = if (
                positionFieldsPresent || parsedReaderPosition != null || lastPage != null || progressPercentage > 0f
            ) {
                metadataTimestamp
            } else {
                existing?.readingPositionModifiedTimestamp ?: 0L
            },
            readerBookmarks = parsedBookmarks ?: existing?.readerBookmarks.orEmpty(),
            readerHighlights = parsedHighlightsWithClear ?: existing?.readerHighlights.orEmpty(),
            titleSortKey = if (fieldIsPresent("customName", customName)) customName else existing?.titleSortKey,
            metadataModifiedTimestamp = metadataTimestamp,
        )
    }

    private fun fieldIsPresent(name: String, value: Any?): Boolean =
        (schemaVersion >= LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION && name in presentFields) || value != null

    private fun readerPositionOrNull(): ReaderLocator? {
        if (lastChapterIndex == null && lastPage == null && lastPositionCfi.isNullOrBlank()) return null
        return ReaderLocator.fromLegacy(
            chapterIndex = lastChapterIndex,
            cfi = lastPositionCfi,
            pageIndex = lastPage
        ).withFallbacks(
            blockIndex = locatorBlockIndex,
            charOffset = locatorCharOffset
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
            val schemaVersion = obj.int("schemaVersion") ?: 1
            val hasV2Fields = schemaVersion >= LOCAL_FOLDER_SYNC_METADATA_SCHEMA_VERSION
            return SharedFolderBookMetadata(
                bookId = bookId,
                schemaVersion = schemaVersion,
                presentFields = if (hasV2Fields) obj.keys else emptySet(),
                title = if (hasV2Fields) obj.string("title") else null,
                author = if (hasV2Fields) obj.string("author") else null,
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
                customName = if (hasV2Fields) obj.string("customName") else null,
                highlightsJson = obj.string("highlightsJson"),
                seriesName = if (hasV2Fields) obj.string("seriesName") else null,
                seriesIndex = if (hasV2Fields) obj.double("seriesIndex") else null,
                description = if (hasV2Fields) obj.string("description") else null,
                originalTitle = if (hasV2Fields) obj.string("originalTitle") else null,
                originalAuthor = if (hasV2Fields) obj.string("originalAuthor") else null,
                originalSeriesName = if (hasV2Fields) obj.string("originalSeriesName") else null,
                originalSeriesIndex = if (hasV2Fields) obj.double("originalSeriesIndex") else null,
                originalDescription = if (hasV2Fields) obj.string("originalDescription") else null
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

/**
 * Describes how trustworthy a physical folder enumeration is.
 *
 * A provider can return a subset of children before failing (or can simply
 * return null for a query).  Treating that subset as the complete folder is
 * unsafe because the reconciliation step would infer deletions.  Callers
 * must use [COMPLETE] only after the whole tree has been enumerated.
 */
enum class LocalFolderScanStatus {
    COMPLETE,
    PARTIAL,
    UNAVAILABLE,
    NOT_SCANNED;

    val canReconcileMissingBooks: Boolean
        get() = this == COMPLETE
}

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
        metadataOnly: Boolean = false,
        scanStatus: LocalFolderScanStatus = LocalFolderScanStatus.COMPLETE
    ): LocalFolderSyncResult {
        if (!folder.localSyncEnabled) {
            return LocalFolderSyncResult(
                state = state,
                idMigrations = emptyMap(),
                removedBookIds = emptySet(),
                stats = LocalFolderSyncStats()
            )
        }

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
            val scannedFilesByPath = files
                .asSequence()
                .filter { it.type in allowedTypes && it.path.isNotBlank() }
                .associateBy { it.path }
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

                    if (existing != null && existing.path != file.path) {
                        val collidedFile = existing.path?.let(scannedFilesByPath::get)
                        val collidedStableId = collidedFile?.stableBookId
                        if (
                            collidedFile != null &&
                            collidedStableId != null &&
                            collidedStableId != stableId &&
                            collidedStableId != existing.id &&
                            collidedStableId !in booksById
                        ) {
                            val oldId = existing.id
                            val migratedBook = existing.copy(id = collidedStableId).withScannedFile(collidedFile)
                            booksById.remove(oldId)
                            booksById[collidedStableId] = migratedBook
                            folderBooksByPath[collidedFile.path] = migratedBook
                            idMigrations[oldId] = collidedStableId
                            legacyItemsByName[existing.displayName]?.remove(existing)
                            existing = booksById[stableId]?.takeIf { it.sourceFolder == folderRoot }
                            stats = stats.copy(migratedBooks = stats.migratedBooks + 1)
                        }
                    }

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
                            ?.takeIf {
                                it.lastModifiedTimestamp > 0L &&
                                    it.lastModifiedTimestamp >= updatedForFile.localFolderModifiedTimestamp()
                            }
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

            if (scanStatus.canReconcileMissingBooks) {
                removedIds = existingFolderBookIds
                    .map { idMigrations[it] ?: it }
                    .filter { it !in foundBookIds }
                    .toSet()
                removedIds.forEach(booksById::remove)
                stats = stats.copy(removedBooks = removedIds.size)
            }
        }

        // A metadata-only pass, a partial enumeration, or an unavailable
        // provider must not advance the scan watermark.  Keeping the old
        // value makes the UI and cloud merge accurately reflect the last
        // complete physical scan.
        val shouldRecordScan = !metadataOnly && scanStatus == LocalFolderScanStatus.COMPLETE
        val syncedFolder = if (shouldRecordScan) folder.copy(lastScanTime = nowMillis) else folder
        val syncedFolders = (state.syncedFolders.filterNot { it.uriString == folderRoot } + syncedFolder)
            .sortedBy { it.name.lowercase() }
        val migratedState = state
            .withMigratedBookIds(idMigrations)
        val nextState = migratedState
            .withoutBookIds(removedIds)
            .copy(
                rawLibraryBooks = booksById.values.toList(),
                syncedFolders = syncedFolders,
                lastFolderScanTime = if (shouldRecordScan) nowMillis else state.lastFolderScanTime
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
    val position = readerPosition
    val hasProgress = (progressPercentage ?: 0f) > 0f || lastPageIndex != null || position != null
    val isDirty = isRecent ||
        hasProgress ||
        !bookmarksJson.isNullOrBlank() ||
        !highlightsJson.isNullOrBlank()
    if (!isDirty) return null
    val positionCfi = position?.toStablePositionCfi()

    return SharedFolderBookMetadata(
        bookId = id,
        title = title,
        author = author,
        displayName = displayName,
        type = type.name,
        lastChapterIndex = position?.chapterIndex,
        lastPage = position?.pageIndex ?: lastPageIndex,
        lastPositionCfi = positionCfi,
        progressPercentage = progressPercentage ?: 0f,
        isRecent = isRecent,
        lastModifiedTimestamp = localFolderModifiedTimestamp(),
        bookmarksJson = bookmarksJson,
        locatorBlockIndex = position?.blockIndex,
        locatorCharOffset = position?.charOffset,
        customName = titleSortKey,
        highlightsJson = highlightsJson,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        description = description,
        originalTitle = originalTitle,
        originalAuthor = originalAuthor,
        originalSeriesName = originalSeriesName,
        originalSeriesIndex = originalSeriesIndex,
        originalDescription = originalDescription
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
        fileContentModifiedTimestamp = lastModified,
        sourceFolder = sourceFolder,
        isRecent = false
    )
}

private fun BookItem.withScannedFile(file: SharedFolderScannedFile): BookItem {
    val sizeChanged = fileSize > 0L && file.size > 0L && fileSize != file.size
    val modifiedChanged = file.lastModified > 0L &&
        file.lastModified != fileContentModifiedTimestamp
    val contentChanged = sizeChanged || modifiedChanged
    return copy(
        path = file.path,
        type = file.type,
        displayName = file.name,
        coverImagePath = if (contentChanged) null else coverImagePath,
        title = if (contentChanged) file.name.substringBeforeLast('.', missingDelimiterValue = file.name) else title,
        author = if (contentChanged) null else author,
        description = if (contentChanged) null else description,
        originalTitle = if (contentChanged) null else originalTitle,
        originalAuthor = if (contentChanged) null else originalAuthor,
        originalSeriesName = if (contentChanged) null else originalSeriesName,
        originalSeriesIndex = if (contentChanged) null else originalSeriesIndex,
        originalDescription = if (contentChanged) null else originalDescription,
        seriesName = if (contentChanged) null else seriesName,
        seriesIndex = if (contentChanged) null else seriesIndex,
        fileSize = file.size.takeIf { it > 0L } ?: fileSize,
        fileContentModifiedTimestamp = file.lastModified.takeIf { it > 0L } ?: fileContentModifiedTimestamp,
        sourceFolder = file.sourceFolder,
        folderTextMetadataParsed = if (contentChanged) false else folderTextMetadataParsed
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

private fun JsonObject.int(name: String): Int? {
    return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull }.getOrNull()
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
