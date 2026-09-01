package com.aryan.reader.shared

import com.aryan.reader.shared.pdf.SharedPdfCloudSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfCloudSidecarSnapshot

data class CloudBookTombstone(
    val bookId: String,
    val type: String? = null,
    val deletedAt: Long,
)

private fun BookItem.cloudModifiedTimestamp(): Long = maxOf(
    timestamp,
    metadataModifiedTimestamp,
    readingPositionModifiedTimestamp,
    fileContentModifiedTimestamp,
)

private fun BookItem.withNewerCloudMetadata(remote: BookItem): BookItem {
    if (remote.metadataModifiedTimestamp <= metadataModifiedTimestamp) return this
    return copy(
        displayName = remote.displayName,
        title = remote.title,
        author = remote.author,
        description = remote.description,
        originalTitle = remote.originalTitle,
        originalAuthor = remote.originalAuthor,
        originalSeriesName = remote.originalSeriesName,
        originalSeriesIndex = remote.originalSeriesIndex,
        originalDescription = remote.originalDescription,
        seriesName = remote.seriesName,
        seriesIndex = remote.seriesIndex,
        titleSortKey = remote.titleSortKey,
        metadataModifiedTimestamp = remote.metadataModifiedTimestamp,
    )
}

private fun mergedCloudBookTombstones(
    local: SharedLibrarySnapshot,
    remote: SharedLibrarySnapshot,
): List<CloudBookTombstone> {
    val activeBooks = (local.books + remote.books)
        .groupBy(BookItem::id)
        .mapValues { (_, books) -> books.maxOf(BookItem::cloudModifiedTimestamp) }
    return mergeCloudBookTombstones(local.bookTombstones + remote.bookTombstones)
        .filter { it.deletedAt > (activeBooks[it.bookId] ?: Long.MIN_VALUE) }
}

/**
 * Applies cloud reading state to books already present on this device.
 *
 * Android downloads missing book content before exposing remote-only entries. iOS
 * must follow the same rule, so a metadata-only sync never creates an unopenable
 * library card. Device-local paths and file facts always remain local.
 */
fun mergeCloudReadingState(
    local: SharedLibrarySnapshot,
    remote: SharedLibrarySnapshot,
): SharedLibrarySnapshot {
    val remoteById = remote.books.associateBy(BookItem::id)
    val tombstones = mergedCloudBookTombstones(local, remote)
    val deletedIds = tombstones.mapTo(mutableSetOf(), CloudBookTombstone::bookId)
    return local.copy(
        books = local.books.filterNot { it.id in deletedIds }.map { localBook ->
            val remoteBook = remoteById[localBook.id] ?: return@map localBook
            val metadataMergedBook = localBook.withNewerCloudMetadata(remoteBook)
            if (!shouldApplyRemoteCloudBookMetadataUpdate(
                    localModifiedTimestamp = localBook.readingPositionModifiedTimestamp,
                    remoteModifiedTimestamp = remoteBook.readingPositionModifiedTimestamp,
                )
            ) {
                metadataMergedBook
            } else {
                metadataMergedBook.copy(
                    progressPercentage = remoteBook.progressPercentage,
                    lastPageIndex = remoteBook.lastPageIndex,
                    readerPosition = remoteBook.readerPosition,
                    readerSettings = remoteBook.readerSettings,
                    readerFormatIsLocal = remoteBook.readerFormatIsLocal,
                    readerLocalFormatSettings = remoteBook.readerLocalFormatSettings,
                    readerAutoScrollIsLocal = remoteBook.readerAutoScrollIsLocal,
                    readerAutoScrollLocalSpeed = remoteBook.readerAutoScrollLocalSpeed,
                    readerAutoScrollLocalMinSpeed = remoteBook.readerAutoScrollLocalMinSpeed,
                    readerAutoScrollLocalMaxSpeed = remoteBook.readerAutoScrollLocalMaxSpeed,
                    pdfAutoScrollIsLocal = remoteBook.pdfAutoScrollIsLocal,
                    pdfAutoScrollLocalSpeed = remoteBook.pdfAutoScrollLocalSpeed,
                    pdfAutoScrollLocalMinSpeed = remoteBook.pdfAutoScrollLocalMinSpeed,
                    pdfAutoScrollLocalMaxSpeed = remoteBook.pdfAutoScrollLocalMaxSpeed,
                    readerBookmarks = remoteBook.readerBookmarks,
                    readerHighlights = remoteBook.readerHighlights,
                    pdfReaderViewport = remoteBook.pdfReaderViewport,
                    readingPositionModifiedTimestamp = remoteBook.readingPositionModifiedTimestamp,
                )
            }
        },
        bookTombstones = tombstones,
    )
}

fun mergeCloudLibrarySnapshotWithDownloadedBooks(
    local: SharedLibrarySnapshot,
    remote: SharedLibrarySnapshot,
    downloadedBookPaths: Map<String, String>,
    downloadedFontPaths: Map<String, String> = emptyMap(),
): SharedLibrarySnapshot {
    val remoteById = remote.books.associateBy(BookItem::id)
    val mergedReadingState = mergeCloudReadingState(local, remote).let { merged ->
        merged.copy(
            books = merged.books.map { book ->
                downloadedBookPaths[book.id]?.let { path ->
                    book.copy(
                        path = path,
                        isAvailable = true,
                        fileSize = remoteById[book.id]?.fileSize ?: book.fileSize,
                        fileContentModifiedTimestamp = remoteById[book.id]
                            ?.fileContentModifiedTimestamp
                            ?: book.fileContentModifiedTimestamp,
                    )
                } ?: book
            }
        )
    }
    val localBookIds = mergedReadingState.books.mapTo(mutableSetOf(), BookItem::id)
    val deletedIds = mergedReadingState.bookTombstones.mapTo(mutableSetOf(), CloudBookTombstone::bookId)
    val downloadedBooks = remote.books.mapNotNull { remoteBook ->
        val path = downloadedBookPaths[remoteBook.id] ?: return@mapNotNull null
        if (remoteBook.id in localBookIds || remoteBook.id in deletedIds) return@mapNotNull null
        remoteBook.copy(path = path, sourceFolder = null, isAvailable = true)
    }
    val mergedShelfRecords = mergeCloudShelfRecords(
        local = local.shelfRecords,
        remote = remote.shelfRecords,
    )
    return mergedReadingState.copy(
        books = mergedReadingState.books + downloadedBooks,
        shelfRecords = mergedShelfRecords,
        shelfRefs = mergeCloudShelfRefs(
            local = local.shelfRefs,
            remote = remote.shelfRefs,
            activeBookIds = (mergedReadingState.books + downloadedBooks).mapTo(mutableSetOf(), BookItem::id),
            activeShelfIds = mergedShelfRecords.mapTo(mutableSetOf(), ShelfRecord::id),
        ),
        tags = mergeCloudTags(local.tags, remote.tags),
        customFonts = mergeCloudCustomFonts(local.customFonts, remote.customFonts, downloadedFontPaths),
        // Folder provider identifiers are intentionally device-scoped. A remote
        // folder entry is safe to apply only when this device already has the
        // same provider identity; otherwise keep the local grant instead of
        // creating an unusable folder card.
        syncedFolders = mergeCloudSyncedFolders(local.syncedFolders, remote.syncedFolders),
        pdfSidecars = mergeCloudPdfSidecars(
            local = local.pdfSidecars,
            remote = remote.pdfSidecars,
            deletedBookIds = deletedIds,
        ),
    )
}

private fun mergeCloudPdfSidecars(
    local: List<SharedPdfCloudSidecarSnapshot>,
    remote: List<SharedPdfCloudSidecarSnapshot>,
    deletedBookIds: Set<String>,
): List<SharedPdfCloudSidecarSnapshot> {
    val localByBookId = local.associateBy(SharedPdfCloudSidecarSnapshot::bookId)
    val remoteByBookId = remote.associateBy(SharedPdfCloudSidecarSnapshot::bookId)
    return (localByBookId.keys + remoteByBookId.keys)
        .sorted()
        .mapNotNull { bookId ->
            if (bookId in deletedBookIds) return@mapNotNull null
            val localSidecar = localByBookId[bookId]
            val remoteSidecar = remoteByBookId[bookId]
            when {
                localSidecar == null -> remoteSidecar
                remoteSidecar == null -> localSidecar
                else -> SharedPdfCloudSidecarSnapshot(
                    bookId = bookId,
                    timestamp = maxOf(localSidecar.timestamp, remoteSidecar.timestamp),
                    data = SharedPdfCloudSidecarCodec.merge(
                        localDataJson = localSidecar.data,
                        remoteDataJson = remoteSidecar.data,
                        preferRemoteOnConflict = remoteSidecar.timestamp >= localSidecar.timestamp,
                    ),
                )
            }
        }
}

private fun mergeCloudShelfRecords(
    local: List<ShelfRecord>,
    remote: List<ShelfRecord>,
): List<ShelfRecord> {
    val localById = local.associateBy { it.id }
    val remoteById = remote.associateBy { it.id }
    return (localById.keys + remoteById.keys)
        .sorted()
        .mapNotNull { id ->
            val localRecord = localById[id]
            val remoteRecord = remoteById[id]
            when {
                localRecord == null -> remoteRecord
                remoteRecord == null -> localRecord
                remoteRecord.isDeleted && !localRecord.isDeleted -> {
                    if (remoteRecord.modifiedAt >= localRecord.modifiedAt) remoteRecord else localRecord
                }
                localRecord.isDeleted && !remoteRecord.isDeleted -> {
                    if (localRecord.modifiedAt > remoteRecord.modifiedAt) localRecord else remoteRecord
                }
                remoteRecord.modifiedAt > localRecord.modifiedAt -> remoteRecord
                remoteRecord.modifiedAt < localRecord.modifiedAt -> localRecord
                else -> remoteRecord
            }
        }
        .filterNot { it.isDeleted }
}

private fun mergeCloudShelfRefs(
    local: List<BookShelfRef>,
    remote: List<BookShelfRef>,
    activeBookIds: Set<String>,
    activeShelfIds: Set<String>,
): List<BookShelfRef> {
    // A shelf membership is an append-only fact in the Android schema. Keep
    // the newest add clock for each (book, shelf) pair and discard refs for
    // books that were deleted by the book tombstone merge.
    return (local + remote)
        .asSequence()
        .filter { it.bookId in activeBookIds && it.shelfId in activeShelfIds }
        .groupBy { it.bookId to it.shelfId }
        .values
        .mapNotNull { refs -> refs.maxByOrNull(BookShelfRef::addedAt) }
        .sortedWith(compareBy<BookShelfRef> { it.shelfId }.thenBy { it.addedAt }.thenBy { it.bookId })
        .toList()
}

private fun mergeCloudTags(
    local: List<Tag>,
    remote: List<Tag>,
): List<Tag> {
    // Tags have no modification clock in the Android schema. Remote wins on
    // an ID collision, matching the Android Firestore read/replace behavior.
    return (local + remote)
        .associateBy(Tag::id)
        .values
        .sortedBy { it.name.lowercase() }
}

private fun mergeCloudCustomFonts(
    local: List<CustomFontItem>,
    remote: List<CustomFontItem>,
    downloadedPaths: Map<String, String>,
): List<CustomFontItem> {
    val localById = local.associateBy { it.id }
    val remoteById = remote.associateBy { it.id }
    return (localById.keys + remoteById.keys)
        .sorted()
        .mapNotNull { id ->
            val localFont = localById[id]
            val remoteFont = remoteById[id]
            val remoteContentAvailable = remoteFont?.path?.isNotBlank() == true ||
                downloadedPaths.containsKey(id)
            when {
                localFont == null -> remoteFont?.takeIf { remoteContentAvailable }
                remoteFont == null -> localFont
                remoteFont.timestamp > localFont.timestamp -> remoteFont.takeIf { remoteContentAvailable }
                    ?: localFont
                remoteFont.timestamp < localFont.timestamp -> localFont
                else -> {
                    // A metadata-only cloud read intentionally has no local
                    // font path. Preserve the installed local file when the
                    // clocks tie; otherwise a no-op sync can hide the font
                    // until it is downloaded again.
                    if (remoteContentAvailable || localFont.path.isBlank()) remoteFont else localFont
                }
            }
        }
        .map { font ->
            downloadedPaths[font.id]?.let { path -> font.copy(path = path) } ?: font
        }
        .filterNot(CustomFontItem::isDeleted)
}

private fun mergeCloudSyncedFolders(
    local: List<SyncedFolder>,
    remote: List<SyncedFolder>,
): List<SyncedFolder> {
    val localByUri = local.associateBy { it.uriString }
    val remoteByUri = remote.associateBy { it.uriString }
    return (localByUri.keys + remoteByUri.keys)
        .sorted()
        .mapNotNull { uri ->
            val localFolder = localByUri[uri]
            val remoteFolder = remoteByUri[uri]
            when {
                localFolder == null -> null
                remoteFolder == null -> localFolder
                remoteFolder.lastScanTime >= localFolder.lastScanTime -> remoteFolder
                else -> localFolder
            }
        }
}
