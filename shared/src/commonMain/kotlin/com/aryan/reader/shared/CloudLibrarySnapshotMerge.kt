package com.aryan.reader.shared

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
    return (local.bookTombstones + remote.bookTombstones)
        .groupBy(CloudBookTombstone::bookId)
        .mapNotNull { (bookId, tombstones) ->
            val newest = tombstones.maxBy(CloudBookTombstone::deletedAt)
            newest.takeIf { it.deletedAt > (activeBooks[bookId] ?: Long.MIN_VALUE) }
        }
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
    return mergedReadingState.copy(books = mergedReadingState.books + downloadedBooks)
}
