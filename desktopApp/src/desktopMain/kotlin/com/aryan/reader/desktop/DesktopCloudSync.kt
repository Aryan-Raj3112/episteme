package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.EpubBookmark
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.reader.ReaderBookmark
import java.io.File

internal data class DesktopCloudSyncInput(
    val userId: String,
    val idToken: String,
    val driveAccessToken: String,
    val deviceId: String,
    val state: SharedReaderScreenState,
    val shelfRecords: List<ShelfRecord>,
    val shelfRefs: List<BookShelfRef>,
    val customFonts: List<CustomFontItem>,
    val includeFolderBooks: Boolean
)

internal data class DesktopCloudSyncResult(
    val state: SharedReaderScreenState,
    val shelfRecords: List<ShelfRecord>,
    val shelfRefs: List<BookShelfRef>,
    val customFonts: List<CustomFontItem>,
    val uploadedBooks: Int = 0,
    val downloadedBooks: Int = 0
)

internal class DesktopCloudSync(
    private val firestoreRepository: DesktopFirestoreRepository,
    private val driveRepository: DesktopGoogleDriveRepository,
    private val bookImporter: DesktopBookImporter,
    private val customFontStore: DesktopCustomFontStore
) {
    suspend fun sync(input: DesktopCloudSyncInput): DesktopCloudSyncResult {
        var state = input.state
        var shelfRecords = input.shelfRecords
        var shelfRefs = input.shelfRefs
        var customFonts = input.customFonts
        var uploadedBooks = 0
        var downloadedBooks = 0

        val remoteBooks = firestoreRepository.getAllBooks(input.userId, input.idToken)
            .filterNot { isDesktopPdfReflowBookId(it.bookId) }
            .filterNot { SharedFileCapabilities.isManualOnlyReaderFileName(it.displayName) }
        val remoteShelves = firestoreRepository.getAllShelves(input.userId, input.idToken)
        val remoteFonts = firestoreRepository.getAllFonts(input.userId, input.idToken)
        var driveFiles = driveRepository.getFiles(input.driveAccessToken).associateBy { it.name }

        val localBooks = state.rawLibraryBooks
            .filterNot { isDesktopPdfReflowBookId(it.id) }
            .filter { input.includeFolderBooks || it.sourceFolder == null }
            .filterNot { it.path?.startsWith("opds-pse") == true }
            .filterNot { SharedFileCapabilities.isManualOnlyReaderFileName(it.displayName) }
        val localBooksMap = localBooks.associateBy { it.id }
        val remoteBooksMap = remoteBooks.associateBy { it.bookId }
        val allBookIds = (localBooksMap.keys + remoteBooksMap.keys).distinct()

        allBookIds.forEach { bookId ->
            val local = localBooksMap[bookId]
            val remote = remoteBooksMap[bookId]
            if (local?.sourceFolder != null) return@forEach

            when {
                local != null && remote == null -> {
                    uploadBookAndMetadata(input, local, uploadContent = true)?.let { synced ->
                        state = state.upsertCloudBook(synced)
                        uploadedBooks += 1
                    }
                }

                local == null && remote != null -> {
                    if (remote.isDeleted) return@forEach
                    val downloaded = downloadRemoteBook(input.driveAccessToken, remote, null, driveFiles)
                    val remoteBook = downloaded ?: remote.toDesktopBookItem()
                    state = state.upsertCloudBook(remoteBook)
                    if (downloaded != null) downloadedBooks += 1
                    if (remote.hasAnnotations) {
                        downloadAnnotations(input.driveAccessToken, remoteBook, remote.lastModifiedTimestamp)
                    }
                }

                local != null && remote != null -> {
                    if (remote.isDeleted) {
                        state = state.removeCloudBook(bookId)
                        return@forEach
                    }

                    val remoteBook = remote.toDesktopBookItem(existing = local)
                    val shouldDownloadContent = shouldDownloadRemoteBookContent(local, remote)
                    val downloaded = if (shouldDownloadContent) {
                        downloadRemoteBook(input.driveAccessToken, remote, local, driveFiles)
                    } else {
                        null
                    }
                    val localSidecarTimestampBeforeMerge = DesktopCloudSidecarSync.localAnnotationTimestamp(local)
                    val localMetadataTimestamp = maxOf(local.timestamp, localSidecarTimestampBeforeMerge)

                    if (localMetadataTimestamp > remote.lastModifiedTimestamp) {
                        uploadBookAndMetadata(input, local, uploadContent = shouldUploadLocalBookContent(local, remote))?.let { synced ->
                            state = state.upsertCloudBook(synced)
                            uploadedBooks += 1
                        }
                    } else if (remote.lastModifiedTimestamp > local.timestamp || downloaded != null) {
                        state = state.upsertCloudBook(downloaded ?: remoteBook)
                    }

                    val localSidecarTimestamp = DesktopCloudSidecarSync.localAnnotationTimestamp(downloaded ?: local)
                    val needsAnnotationDownload = remote.hasAnnotations &&
                        (remote.lastModifiedTimestamp > localSidecarTimestamp || localSidecarTimestamp == 0L)
                    if (needsAnnotationDownload) {
                        val targetBook = downloaded ?: state.rawLibraryBooks.firstOrNull { it.id == bookId } ?: local
                        downloadAnnotations(input.driveAccessToken, targetBook, remote.lastModifiedTimestamp)
                    }
                }
            }
        }

        driveFiles = driveRepository.getFiles(input.driveAccessToken).associateBy { it.name }
        state.rawLibraryBooks
            .filterNot { isDesktopPdfReflowBookId(it.id) }
            .filter { it.sourceFolder == null }
            .filterNot { it.path?.startsWith("opds-pse") == true }
            .filterNot { SharedFileCapabilities.isManualOnlyReaderFileName(it.displayName) }
            .forEach { book ->
                val driveName = desktopCloudBookDriveFileName(book.id, book.type) ?: return@forEach
                val localFile = book.path?.let(::File)
                when {
                    localFile?.isFile == true && driveFiles[driveName] == null -> {
                        if (driveRepository.uploadFile(input.driveAccessToken, book.id, localFile, book.type) != null) {
                            uploadedBooks += 1
                        }
                    }

                    (localFile == null || !localFile.isFile) && driveFiles[driveName] != null -> {
                        val remote = remoteBooksMap[book.id] ?: return@forEach
                        downloadRemoteBook(input.driveAccessToken, remote, book, driveFiles)?.let { downloaded ->
                            state = state.upsertCloudBook(downloaded)
                            downloadedBooks += 1
                        }
                    }
                }
            }

        val shelfSync = syncShelves(
            userId = input.userId,
            idToken = input.idToken,
            deviceId = input.deviceId,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            syncableBookIds = state.rawLibraryBooks
                .filterNot { isDesktopPdfReflowBookId(it.id) }
                .mapTo(mutableSetOf()) { it.id },
            remoteShelves = remoteShelves
        )
        shelfRecords = shelfSync.records
        shelfRefs = shelfSync.refs

        customFonts = syncFonts(
            userId = input.userId,
            idToken = input.idToken,
            accessToken = input.driveAccessToken,
            localFonts = customFonts,
            remoteFonts = remoteFonts
        )

        return DesktopCloudSyncResult(
            state = state,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            customFonts = customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() },
            uploadedBooks = uploadedBooks,
            downloadedBooks = downloadedBooks
        )
    }

    suspend fun uploadBookAndMetadata(
        input: DesktopCloudSyncInput,
        book: BookItem,
        uploadContent: Boolean
    ): BookItem? {
        if (isDesktopPdfReflowBookId(book.id)) return null
        if (book.sourceFolder != null) return null
        if (book.path?.startsWith("opds-pse") == true) return null
        if (SharedFileCapabilities.isManualOnlyReaderFileName(book.displayName)) return null
        if (uploadContent) {
            val source = book.path?.let(::File)?.takeIf { it.isFile }
            if (source != null && driveRepository.uploadFile(input.driveAccessToken, book.id, source, book.type) == null) {
                return null
            }
        }

        val bundle = DesktopCloudSidecarSync.exportAnnotationBundle(book)
        try {
            if (bundle != null && driveRepository.uploadAnnotationFile(input.driveAccessToken, book.id, bundle) == null) {
                return null
            }
        } finally {
            bundle?.delete()
        }

        val now = System.currentTimeMillis()
        val syncedBook = book.copy(timestamp = now)
        firestoreRepository.syncBookMetadata(
            userId = input.userId,
            book = syncedBook.toDesktopCloudBookMetadata(
                hasAnnotations = bundle != null,
                timestamp = now
            ),
            originDeviceId = input.deviceId,
            idToken = input.idToken
        )
        return syncedBook
    }

    suspend fun deleteBooksFromCloud(
        userId: String,
        idToken: String,
        accessToken: String,
        deviceId: String,
        books: List<BookItem>
    ) {
        val driveFiles = driveRepository.getFiles(accessToken).associateBy { it.name }
        books
            .filterNot { isDesktopPdfReflowBookId(it.id) }
            .filter { it.sourceFolder == null }
            .filterNot { it.path?.startsWith("opds-pse") == true }
            .filterNot { SharedFileCapabilities.isManualOnlyReaderFileName(it.displayName) }
            .forEach { book ->
                firestoreRepository.syncBookMetadata(
                    userId = userId,
                    book = book.toDesktopCloudBookMetadata(
                        hasAnnotations = false,
                        timestamp = System.currentTimeMillis()
                    ).copy(isDeleted = true),
                    originDeviceId = deviceId,
                    idToken = idToken
                )
                desktopCloudBookDriveFileName(book.id, book.type)
                    ?.let { driveFiles[it]?.id }
                    ?.let { driveRepository.deleteDriveFile(accessToken, it) }
                driveFiles["annotation_${book.id}.json"]?.id
                    ?.let { driveRepository.deleteDriveFile(accessToken, it) }
            }
    }

    suspend fun syncShelfChange(
        userId: String,
        idToken: String,
        deviceId: String,
        record: ShelfRecord,
        refs: List<BookShelfRef>,
        isDeleted: Boolean = false
    ) {
        if (record.isSmart) return
        firestoreRepository.syncShelf(
            userId = userId,
            shelf = DesktopCloudShelfMetadata(
                name = record.name,
                bookIds = refs.filter { it.shelfId == record.id }.map { it.bookId }.distinct(),
                lastModifiedTimestamp = System.currentTimeMillis(),
                isDeleted = isDeleted
            ),
            originDeviceId = deviceId,
            idToken = idToken
        )
    }

    suspend fun clearCloudData(userId: String, idToken: String, accessToken: String) {
        driveRepository.deleteAllFiles(accessToken)
        firestoreRepository.deleteAllUserFirestoreData(userId, idToken)
    }

    suspend fun deleteFontFromCloud(
        userId: String,
        idToken: String,
        accessToken: String,
        font: CustomFontItem
    ) {
        val driveFiles = driveRepository.getFiles(accessToken).associateBy { it.name }
        driveFiles[font.fileName]?.id?.let { driveRepository.deleteDriveFile(accessToken, it) }
        firestoreRepository.deleteFontMetadata(userId, font.id, idToken)
    }

    private suspend fun downloadAnnotations(accessToken: String, book: BookItem, timestamp: Long): Boolean {
        val temp = File(desktopUserCacheRoot(), "temp_download_${book.id.toDesktopSafeFileName()}_${System.nanoTime()}.json")
        return try {
            if (!driveRepository.downloadAnnotationFile(accessToken, book.id, temp) || !temp.isFile) return false
            DesktopCloudSidecarSync.importAnnotationBundle(book, temp.readText(), timestamp)
        } finally {
            temp.delete()
        }
    }

    private suspend fun downloadRemoteBook(
        accessToken: String,
        remote: DesktopCloudBookMetadata,
        existing: BookItem?,
        driveFiles: Map<String, DesktopDriveFile>
    ): BookItem? {
        val type = remote.fileType()
        val driveName = desktopCloudBookDriveFileName(remote.bookId, type) ?: return null
        val driveFile = driveFiles[driveName] ?: return null
        val extension = SharedFileCapabilities.primaryExtensionFor(type) ?: return null
        val destination = bookImporter.createBookFile("${remote.bookId.toDesktopSafeFileName()}.$extension")
        if (!driveRepository.downloadFile(accessToken, driveFile.id, destination)) {
            destination.delete()
            return null
        }
        val contentTimestamp = remote.fileContentModifiedTimestamp.takeIf { it > 0L } ?: destination.lastModified()
        if (contentTimestamp > 0L) destination.setLastModified(contentTimestamp)
        return remote.toDesktopBookItem(existing = existing, downloadedPath = destination.absolutePath).copy(
            fileSize = destination.length(),
            fileContentModifiedTimestamp = contentTimestamp
        )
    }

    private suspend fun syncFonts(
        userId: String,
        idToken: String,
        accessToken: String,
        localFonts: List<CustomFontItem>,
        remoteFonts: List<DesktopCloudFontMetadata>
    ): List<CustomFontItem> {
        val localFontsMap = localFonts.associateBy { it.id }
        val remoteFontsMap = remoteFonts.associateBy { it.id }
        val driveFiles = driveRepository.getFiles(accessToken).associateBy { it.name }
        val nextFonts = localFonts.toMutableList()

        (localFontsMap.keys + remoteFontsMap.keys).forEach { fontId ->
            val local = localFontsMap[fontId]
            val remote = remoteFontsMap[fontId]
            when {
                local != null && remote == null -> {
                    firestoreRepository.syncFontMetadata(userId, local.toDesktopCloudFontMetadata(), idToken)
                }

                local == null && remote != null && !remote.isDeleted -> {
                    val target = customFontStore.getFontFile(remote.fileName)
                    driveFiles[remote.fileName]?.id?.let { driveRepository.downloadFile(accessToken, it, target) }
                    nextFonts += customFontStore.syncedFontItem(remote)
                }

                local != null && remote != null -> {
                    when {
                        local.isDeleted && !remote.isDeleted -> {
                            firestoreRepository.syncFontMetadata(userId, remote.copy(isDeleted = true), idToken)
                        }

                        !local.isDeleted && remote.isDeleted -> {
                            customFontStore.deleteFont(local)
                            nextFonts.removeAll { it.id == local.id }
                        }
                    }
                }
            }
        }

        nextFonts.toList().forEach { font ->
            val localFile = File(font.path)
            if (!font.isDeleted && localFile.isFile && driveFiles[font.fileName] == null) {
                driveRepository.uploadFont(accessToken, font.fileName, localFile, font.fileExtension)
            } else if (!font.isDeleted && !localFile.isFile) {
                driveFiles[font.fileName]?.id?.let { driveRepository.downloadFile(accessToken, it, localFile) }
            }
        }
        return nextFonts.distinctBy { it.id }
    }

    private suspend fun syncShelves(
        userId: String,
        idToken: String,
        deviceId: String,
        shelfRecords: List<ShelfRecord>,
        shelfRefs: List<BookShelfRef>,
        syncableBookIds: Set<String>,
        remoteShelves: List<DesktopCloudShelfMetadata>
    ): ShelfSyncResult {
        val localShelves = shelfRecords
            .filterNot { it.isSmart }
            .map { record ->
                DesktopCloudShelfRecord(
                    record = record,
                    metadata = DesktopCloudShelfMetadata(
                        name = record.name,
                        bookIds = shelfRefs.filter { it.shelfId == record.id }
                            .map { it.bookId }
                            .filter { it in syncableBookIds }
                            .distinct(),
                        lastModifiedTimestamp = desktopShelfTimestamp(record, shelfRefs),
                        isDeleted = false
                    )
                )
            }
        val localShelvesByName = localShelves.associateBy { it.metadata.name }
        val remoteShelvesByName = remoteShelves.associateBy { it.name }
        var records = shelfRecords
        var refs = shelfRefs

        (localShelvesByName.keys + remoteShelvesByName.keys).forEach { shelfName ->
            val local = localShelvesByName[shelfName]
            val remote = remoteShelvesByName[shelfName]
            when {
                local != null && remote == null -> {
                    firestoreRepository.syncShelf(userId, local.metadata, deviceId, idToken)
                }

                local == null && remote != null -> {
                    if (!remote.isDeleted) {
                        val record = ShelfRecord(id = "shelf_${remote.lastModifiedTimestamp}_${shelfName.hashCode()}", name = remote.name)
                        records += record
                        refs = refs.filterNot { it.shelfId == record.id } +
                            remote.bookIds.filter { it in syncableBookIds }.map { bookId ->
                                BookShelfRef(bookId, record.id, remote.lastModifiedTimestamp)
                            }
                    }
                }

                local != null && remote != null -> {
                    if (local.metadata.lastModifiedTimestamp > remote.lastModifiedTimestamp) {
                        firestoreRepository.syncShelf(userId, local.metadata, deviceId, idToken)
                    } else if (remote.lastModifiedTimestamp > local.metadata.lastModifiedTimestamp) {
                        if (remote.isDeleted) {
                            records = records.filterNot { it.id == local.record.id }
                            refs = refs.filterNot { it.shelfId == local.record.id }
                        } else {
                            refs = refs.filterNot { it.shelfId == local.record.id } +
                                remote.bookIds.filter { it in syncableBookIds }.map { bookId ->
                                    BookShelfRef(bookId, local.record.id, remote.lastModifiedTimestamp)
                                }
                        }
                    }
                }
            }
        }
        return ShelfSyncResult(records, refs)
    }
}

internal fun BookItem.toDesktopCloudBookMetadata(
    hasAnnotations: Boolean,
    timestamp: Long = this.timestamp
): DesktopCloudBookMetadata {
    val position = readerPosition
    val bookmarksJson = readerBookmarks
        .mapNotNull { it.toDesktopCloudEpubBookmarkOrNull() }
        .takeIf { it.isNotEmpty() }
        ?.let(EpubAnnotationSerializer::bookmarksToJson)
    val highlightsJson = readerHighlights
        .takeIf { it.isNotEmpty() }
        ?.let(EpubAnnotationSerializer::highlightsToJson)
    val localFile = path?.let(::File)
    val contentTimestamp = fileContentModifiedTimestamp.takeIf { it > 0L }
        ?: localFile?.takeIf { it.isFile }?.lastModified()
        ?: 0L
    return DesktopCloudBookMetadata(
        bookId = id,
        title = title,
        author = author,
        displayName = displayName,
        type = type.name,
        lastPositionCfi = position?.cloudPositionCfi(),
        lastChapterIndex = position?.chapterIndex,
        locatorBlockIndex = null,
        locatorCharOffset = null,
        lastPage = position?.pageIndex ?: lastPageIndex,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        isDeleted = false,
        lastModifiedTimestamp = timestamp,
        bookmarksJson = bookmarksJson,
        hasAnnotations = hasAnnotations,
        fileContentModifiedTimestamp = contentTimestamp,
        customName = null,
        highlightsJson = highlightsJson,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        description = description,
        originalTitle = originalTitle ?: title,
        originalAuthor = originalAuthor ?: author,
        originalSeriesName = originalSeriesName ?: seriesName,
        originalSeriesIndex = originalSeriesIndex ?: seriesIndex,
        originalDescription = originalDescription ?: description
    )
}

internal fun DesktopCloudBookMetadata.toDesktopBookItem(
    existing: BookItem? = null,
    downloadedPath: String? = null
): BookItem {
    val type = fileType()
    val pageIndex = lastPage
    val locator = ReaderLocator.fromLegacy(
        chapterIndex = lastChapterIndex,
        cfi = lastPositionCfi,
        pageIndex = pageIndex
    )
    return BookItem(
        id = bookId,
        path = downloadedPath ?: existing?.path,
        type = type,
        displayName = displayName.ifBlank { existing?.displayName ?: bookId },
        timestamp = lastModifiedTimestamp,
        coverImagePath = existing?.coverImagePath,
        title = title ?: existing?.title,
        author = author ?: existing?.author,
        description = description ?: existing?.description,
        originalTitle = originalTitle ?: existing?.originalTitle,
        originalAuthor = originalAuthor ?: existing?.originalAuthor,
        originalSeriesName = originalSeriesName ?: existing?.originalSeriesName,
        originalSeriesIndex = originalSeriesIndex ?: existing?.originalSeriesIndex,
        originalDescription = originalDescription ?: existing?.originalDescription,
        progressPercentage = progressPercentage ?: existing?.progressPercentage,
        isRecent = isRecent,
        fileSize = existing?.fileSize ?: 0L,
        fileContentModifiedTimestamp = fileContentModifiedTimestamp.takeIf { it > 0L }
            ?: existing?.fileContentModifiedTimestamp
            ?: 0L,
        sourceFolder = null,
        folderTextMetadataParsed = existing?.folderTextMetadataParsed ?: false,
        seriesName = seriesName ?: existing?.seriesName,
        seriesIndex = seriesIndex ?: existing?.seriesIndex,
        tags = existing?.tags.orEmpty(),
        lastPageIndex = pageIndex ?: existing?.lastPageIndex,
        readerPosition = locator.takeIf {
            it.chapterIndex != null || it.pageIndex != null || it.cfi != null || it.startOffset != null
        } ?: existing?.readerPosition,
        readerSettings = existing?.readerSettings,
        readerBookmarks = if (bookmarksJson.isNullOrBlank()) {
            existing?.readerBookmarks.orEmpty()
        } else {
            EpubAnnotationSerializer.parseBookmarksJson(bookmarksJson).map { bookmark ->
                ReaderBookmark(
                    id = "${bookmark.chapterIndex}:${bookmark.cfi}",
                    pageIndex = bookmark.pageInChapter?.minus(1) ?: bookmark.locator.pageIndex ?: 0,
                    chapterTitle = bookmark.chapterTitle,
                    preview = bookmark.snippet,
                    locator = bookmark.locator
                )
            }
        },
        readerHighlights = if (highlightsJson.isNullOrBlank()) {
            existing?.readerHighlights.orEmpty()
        } else {
            EpubAnnotationSerializer.parseHighlightsJson(highlightsJson)
        },
        pdfReaderViewport = existing?.pdfReaderViewport
    )
}

internal fun CustomFontItem.toDesktopCloudFontMetadata(): DesktopCloudFontMetadata {
    return DesktopCloudFontMetadata(
        id = id,
        displayName = displayName,
        fileName = fileName,
        fileExtension = fileExtension,
        timestamp = timestamp,
        isDeleted = isDeleted
    )
}

internal fun desktopCloudBookDriveFileName(bookId: String, type: FileType): String? {
    val extension = SharedFileCapabilities.primaryExtensionFor(type) ?: return null
    return "$bookId.$extension"
}

private data class DesktopCloudShelfRecord(
    val record: ShelfRecord,
    val metadata: DesktopCloudShelfMetadata
)

private data class ShelfSyncResult(
    val records: List<ShelfRecord>,
    val refs: List<BookShelfRef>
)

private fun DesktopCloudBookMetadata.fileType(): FileType {
    return runCatching { FileType.valueOf(type) }.getOrDefault(FileType.EPUB)
}

private fun SharedReaderScreenState.upsertCloudBook(book: BookItem): SharedReaderScreenState {
    val existing = rawLibraryBooks.any { it.id == book.id }
    val nextBooks = if (existing) {
        rawLibraryBooks.map { if (it.id == book.id) book else it }
    } else {
        listOf(book) + rawLibraryBooks
    }
    return copy(rawLibraryBooks = nextBooks)
}

private fun SharedReaderScreenState.removeCloudBook(bookId: String): SharedReaderScreenState {
    return copy(
        rawLibraryBooks = rawLibraryBooks.filterNot { it.id == bookId },
        selectedBookIds = selectedBookIds - bookId,
        pinnedHomeBookIds = pinnedHomeBookIds - bookId,
        pinnedLibraryBookIds = pinnedLibraryBookIds - bookId,
        openTabIds = openTabIds.filterNot { it == bookId },
        activeTabBookId = activeTabBookId?.takeUnless { it == bookId }
    )
}

private fun shouldDownloadRemoteBookContent(local: BookItem, remote: DesktopCloudBookMetadata): Boolean {
    val localFile = local.path?.let(::File)
    val localTimestamp = local.fileContentModifiedTimestamp.takeIf { it > 0L }
        ?: localFile?.takeIf { it.isFile }?.lastModified()
        ?: 0L
    return local.sourceFolder == null &&
        !remote.isDeleted &&
        remote.fileType() == local.type &&
        remote.fileContentModifiedTimestamp > 0L &&
        (localFile == null || !localFile.isFile || remote.fileContentModifiedTimestamp > localTimestamp)
}

private fun shouldUploadLocalBookContent(local: BookItem, remote: DesktopCloudBookMetadata?): Boolean {
    val localFile = local.path?.let(::File)?.takeIf { it.isFile } ?: return false
    val localTimestamp = local.fileContentModifiedTimestamp.takeIf { it > 0L } ?: localFile.lastModified()
    return local.sourceFolder == null &&
        localTimestamp > 0L &&
        localTimestamp > (remote?.fileContentModifiedTimestamp ?: 0L)
}

private fun desktopShelfTimestamp(record: ShelfRecord, refs: List<BookShelfRef>): Long {
    val idTimestamp = record.id.split('_').firstNotNullOfOrNull { it.toLongOrNull() }
    val refsTimestamp = refs.filter { it.shelfId == record.id }.maxOfOrNull { it.addedAt }
    return maxOf(idTimestamp ?: 0L, refsTimestamp ?: 0L)
}

private fun ReaderLocator.cloudPositionCfi(): String? {
    cfi?.let { return it }
    val chapter = chapterIndex
    val start = startOffset
    val end = endOffset ?: start
    return if (chapter != null && start != null && end != null) {
        "desktop:$chapter:$start:$end"
    } else if (chapter != null && pageIndex != null) {
        "desktop:$chapter:$pageIndex"
    } else {
        null
    }
}

private fun ReaderBookmark.toDesktopCloudEpubBookmarkOrNull(): EpubBookmark? {
    val chapterIndex = locator.chapterIndex ?: 0
    val cfi = locator.cloudPositionCfi() ?: "desktop:$chapterIndex:$pageIndex"
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
