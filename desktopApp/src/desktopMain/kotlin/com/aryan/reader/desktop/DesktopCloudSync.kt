package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.BookShelfRef
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.EpubBookmark
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.SharedCloudBookMetadataWinner
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.sharedCloudBookMetadataWinner
import com.aryan.reader.shared.shouldDownloadRemoteCloudBookContent
import com.aryan.reader.shared.shouldUploadLocalCloudBookContent
import com.aryan.reader.shared.sharedCloudBookContentFileName
import com.aryan.reader.shared.toStablePositionCfi
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSidecarCodec
import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
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
    val downloadedBooks: Int = 0,
    val pendingContentDownloads: Int = 0
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
        var pendingContentDownloads = 0

        logDesktopCloudSync {
            "desktop.engine.full_sync.start user=${input.userId} device=${input.deviceId} " +
                "localBooks=${input.state.rawLibraryBooks.size} includeFolderBooks=${input.includeFolderBooks}"
        }
        val remoteBooks = firestoreRepository.getAllBooks(input.userId, input.idToken)
            .filterNot { isDesktopPdfReflowBookId(it.bookId) }
            .filterNot { SharedFileCapabilities.isManualOnlyReaderFileName(it.displayName) }
        val remoteShelves = firestoreRepository.getAllShelves(input.userId, input.idToken)
        val remoteFonts = firestoreRepository.getAllFonts(input.userId, input.idToken)
        var driveFiles = driveRepository.getFiles(input.driveAccessToken).associateBy { it.name }
        logDesktopCloudSync {
            "desktop.engine.full_sync.loaded user=${input.userId} remoteBooks=${remoteBooks.size} " +
                "remoteShelves=${remoteShelves.size} remoteFonts=${remoteFonts.size} driveFiles=${driveFiles.size}"
        }

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
                    logDesktopCloudSync { "desktop.engine.book_decision action=upload_new ${local.desktopCloudSyncSummary()}" }
                    uploadBookAndMetadata(input, local, uploadContent = true)?.let { synced ->
                        state = state.upsertCloudBook(synced)
                        uploadedBooks += 1
                    }
                }

                local == null && remote != null -> {
                    if (remote.isDeleted) {
                        logDesktopCloudSync { "desktop.engine.book_decision action=skip_deleted_remote_only ${remote.desktopCloudSyncSummary()}" }
                        return@forEach
                    }
                    logDesktopCloudSync { "desktop.engine.book_decision action=apply_remote_new ${remote.desktopCloudSyncSummary()}" }
                    val downloaded = downloadRemoteBook(input.driveAccessToken, remote, null, driveFiles)
                    if (downloaded == null) {
                        pendingContentDownloads += 1
                        logDesktopCloudSync {
                            "desktop.engine.book_decision action=defer_remote_new_pending_content " +
                                remote.desktopCloudSyncSummary()
                        }
                        return@forEach
                    }
                    val remoteBook = downloaded
                    state = state.upsertCloudBook(remoteBook)
                    downloadedBooks += 1
                    importDesktopPdfBookmarksMetadata(remoteBook, remote.bookmarksJson, remote.lastModifiedTimestamp)
                    if (remote.hasAnnotations) {
                        val remoteAnnotationTimestamp = remote.effectiveCloudAnnotationModifiedTimestamp(
                            remoteAnnotationDriveFileTimestamp(remote.bookId, driveFiles)
                        )
                        downloadAnnotations(input.driveAccessToken, remoteBook, remoteAnnotationTimestamp)
                    }
                }

                local != null && remote != null -> {
                    val remoteBook = remote.toDesktopBookItem(existing = local)
                    val shouldDownloadContent = shouldDownloadRemoteBookContent(local, remote)
                    val downloaded = if (shouldDownloadContent) {
                        downloadRemoteBook(input.driveAccessToken, remote, local, driveFiles)
                    } else {
                        null
                    }
                    if (shouldDownloadContent && downloaded == null) {
                        pendingContentDownloads += 1
                    }
                    val localContentAvailable = local.path?.let(::File)?.isFile == true
                    if (shouldDownloadContent && downloaded == null && !localContentAvailable) {
                        logDesktopCloudSync {
                            "desktop.engine.book_decision action=defer_existing_pending_content book=$bookId " +
                                local.desktopCloudSyncSummary() + " " + remote.desktopCloudSyncSummary()
                        }
                        state = state.removeCloudBook(bookId)
                        return@forEach
                    }
                    val localSidecarTimestampBeforeMerge = DesktopCloudSidecarSync.localAnnotationTimestamp(local)
                    val metadataWinner = sharedCloudBookMetadataWinner(
                        localModifiedTimestamp = local.timestamp,
                        remoteModifiedTimestamp = remote.lastModifiedTimestamp
                    )
                    val localMetadataWins = metadataWinner == SharedCloudBookMetadataWinner.LOCAL
                    val localReadingTimestamp = local.effectiveCloudReadingPositionModifiedTimestamp()
                    val remoteReadingTimestamp = remote.effectiveCloudReadingPositionModifiedTimestamp()
                    val remoteAnnotationDriveTimestamp = remoteAnnotationDriveFileTimestamp(bookId, driveFiles)
                    val remoteAnnotationTimestamp = remote.effectiveCloudAnnotationModifiedTimestamp(remoteAnnotationDriveTimestamp)
                    val localReadingPositionShouldUpload = localReadingTimestamp > remoteReadingTimestamp
                    val localAnnotationsShouldUpload = shouldUploadLocalAnnotations(
                        local = local,
                        remote = remote,
                        remoteAnnotationModifiedTimestamp = remoteAnnotationTimestamp,
                        localSidecarTimestamp = localSidecarTimestampBeforeMerge
                    )
                    logDesktopCloudAnnotations {
                        "desktop.sync.inspect book=$bookId remoteHas=${remote.hasAnnotations} " +
                            "remoteTs=${remote.lastModifiedTimestamp} remoteAnnTs=$remoteAnnotationTimestamp " +
                            "remoteDriveAnnTs=$remoteAnnotationDriveTimestamp localTs=${local.timestamp} " +
                            "remoteReadTs=$remoteReadingTimestamp localReadTs=$localReadingTimestamp " +
                            "localShouldUpload=$localAnnotationsShouldUpload " +
                            DesktopCloudSidecarSync.localAnnotationDebugSummary(local)
                    }
                    logDesktopCloudSync {
                        "desktop.engine.book_compare book=$bookId winner=$metadataWinner shouldDownloadContent=$shouldDownloadContent " +
                            "downloadedContent=${downloaded != null} sidecarTs=$localSidecarTimestampBeforeMerge " +
                            "uploadAnnotations=$localAnnotationsShouldUpload uploadReading=$localReadingPositionShouldUpload " +
                            local.desktopCloudSyncSummary() + " " + remote.desktopCloudSyncSummary()
                    }

                    if (remote.isDeleted) {
                        if (localMetadataWins) {
                            logDesktopCloudSync { "desktop.engine.book_decision action=resurrect_upload_local book=$bookId" }
                            uploadBookAndMetadata(
                                input = input,
                                book = local,
                                uploadContent = shouldUploadLocalBookContent(local, null),
                                uploadAnnotations = DesktopCloudSidecarSync.hasLocalAnnotationData(local)
                            )?.let { synced ->
                                state = state.upsertCloudBook(synced)
                                uploadedBooks += 1
                            }
                        } else if (metadataWinner == SharedCloudBookMetadataWinner.REMOTE) {
                            logDesktopCloudSync { "desktop.engine.book_decision action=apply_remote_delete book=$bookId" }
                            state = state.removeCloudBook(bookId)
                        } else {
                            logDesktopCloudSync { "desktop.engine.book_decision action=skip_equal_delete book=$bookId" }
                        }
                        return@forEach
                    }

                    if (localMetadataWins) {
                        logDesktopCloudSync {
                            "desktop.engine.book_decision action=upload_local book=$bookId " +
                                "uploadContent=${shouldUploadLocalBookContent(local, remote)} " +
                                "uploadAnnotations=$localAnnotationsShouldUpload " +
                                "preserveRemoteReading=${remoteReadingTimestamp > localReadingTimestamp}"
                        }
                        val localForMetadata = if (remoteReadingTimestamp > localReadingTimestamp) {
                            local.withCloudReadingPosition(remote)
                        } else {
                            local
                        }
                        val bookForMetadata = localForMetadata.withDownloadedCloudContent(downloaded, replacePath = false)
                        uploadBookAndMetadata(
                            input = input,
                            book = bookForMetadata,
                            uploadContent = shouldUploadLocalBookContent(local, remote),
                            uploadAnnotations = localAnnotationsShouldUpload,
                            remoteHasAnnotations = remote.hasAnnotations,
                            remoteAnnotationModifiedTimestamp = remoteAnnotationTimestamp,
                            remoteContentModifiedTimestamp = remote.fileContentModifiedTimestamp
                        )?.let { synced ->
                            state = state.upsertCloudBook(synced.withDownloadedCloudContent(downloaded))
                            uploadedBooks += 1
                        }
                    } else if (metadataWinner == SharedCloudBookMetadataWinner.REMOTE || downloaded != null) {
                        logDesktopCloudSync {
                            "desktop.engine.book_decision action=apply_remote book=$bookId " +
                                "metadataWinner=$metadataWinner downloadedContent=${downloaded != null}"
                        }
                        val mergedBook = downloaded ?: remoteBook
                        state = state.upsertCloudBook(mergedBook)
                        importDesktopPdfBookmarksMetadata(mergedBook, remote.bookmarksJson, remote.lastModifiedTimestamp)
                    }

                    if (!localMetadataWins && (localAnnotationsShouldUpload || localReadingPositionShouldUpload)) {
                        val metadataBook = state.rawLibraryBooks.firstOrNull { it.id == bookId }
                            ?: remoteBook
                        logDesktopCloudAnnotations {
                            "desktop.sync.upload_local_supplement book=$bookId winner=$metadataWinner " +
                                "remoteHas=${remote.hasAnnotations} remoteTs=${remote.lastModifiedTimestamp} " +
                                "remoteAnnTs=$remoteAnnotationTimestamp " +
                                "localSidecarTs=$localSidecarTimestampBeforeMerge " +
                                "uploadAnnotations=$localAnnotationsShouldUpload uploadReading=$localReadingPositionShouldUpload " +
                                "localReadTs=$localReadingTimestamp remoteReadTs=$remoteReadingTimestamp"
                        }
                        logDesktopCloudSync {
                            "desktop.engine.book_decision action=upload_local_supplement book=$bookId " +
                                "metadataWinner=$metadataWinner sidecarTs=$localSidecarTimestampBeforeMerge " +
                                "uploadAnnotations=$localAnnotationsShouldUpload uploadReading=$localReadingPositionShouldUpload " +
                                metadataBook.desktopCloudSyncSummary()
                        }
                        uploadBookAndMetadata(
                            input = input,
                            book = metadataBook,
                            uploadContent = false,
                            uploadAnnotations = localAnnotationsShouldUpload,
                            remoteHasAnnotations = remote.hasAnnotations,
                            remoteAnnotationModifiedTimestamp = remoteAnnotationTimestamp,
                            remoteContentModifiedTimestamp = remote.fileContentModifiedTimestamp
                        )?.let { synced ->
                            state = state.upsertCloudBook(synced.withDownloadedCloudContent(downloaded))
                            uploadedBooks += 1
                        }
                    }

                    val localSidecarTimestamp = DesktopCloudSidecarSync.localAnnotationPayloadTimestamp(downloaded ?: local)
                    val needsAnnotationDownload = !localMetadataWins &&
                        !localAnnotationsShouldUpload &&
                        remote.hasAnnotations &&
                        (remoteAnnotationTimestamp > localSidecarTimestamp || localSidecarTimestamp == 0L)
                    if (needsAnnotationDownload) {
                        logDesktopCloudAnnotations {
                            "desktop.sync.download_remote_annotations book=$bookId remoteTs=${remote.lastModifiedTimestamp} " +
                                "remoteAnnTs=$remoteAnnotationTimestamp " +
                                "localSidecarTs=$localSidecarTimestamp localMetadataWins=$localMetadataWins " +
                                "localShouldUpload=$localAnnotationsShouldUpload"
                        }
                        logDesktopCloudSync {
                            "desktop.engine.sidecar_download_start book=$bookId remoteTs=${remote.lastModifiedTimestamp} " +
                                "remoteAnnTs=$remoteAnnotationTimestamp localSidecarTs=$localSidecarTimestamp localMetadataWins=$localMetadataWins"
                        }
                        val targetBook = downloaded ?: state.rawLibraryBooks.firstOrNull { it.id == bookId } ?: local
                        downloadAnnotations(input.driveAccessToken, targetBook, remoteAnnotationTimestamp)
                    } else {
                        logDesktopCloudAnnotations {
                            "desktop.sync.skip_remote_annotations book=$bookId remoteHas=${remote.hasAnnotations} " +
                                "remoteAnnTs=$remoteAnnotationTimestamp localSidecarTs=$localSidecarTimestamp localMetadataWins=$localMetadataWins " +
                                "localShouldUpload=$localAnnotationsShouldUpload"
                        }
                        logDesktopCloudSync {
                            "desktop.engine.sidecar_download_skip book=$bookId remoteHasAnnotations=${remote.hasAnnotations} " +
                                "localSidecarTs=$localSidecarTimestamp localMetadataWins=$localMetadataWins"
                        }
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
                        val remote = remoteBooksMap[book.id]
                        if (remote == null || shouldUploadLocalBookContent(book, remote)) {
                            logDesktopCloudSync { "desktop.engine.content_upload_missing_remote book=${book.id} driveName=$driveName" }
                            uploadBookAndMetadata(
                                input = input,
                                book = book,
                                uploadContent = true,
                                uploadAnnotations = false,
                                remoteHasAnnotations = remote?.hasAnnotations == true,
                                remoteAnnotationModifiedTimestamp = remote?.effectiveCloudAnnotationModifiedTimestamp(
                                    remoteAnnotationDriveFileTimestamp(book.id, driveFiles)
                                ) ?: 0L,
                                remoteContentModifiedTimestamp = remote?.fileContentModifiedTimestamp
                            )?.let { synced ->
                                state = state.upsertCloudBook(synced)
                                uploadedBooks += 1
                            }
                        } else {
                            pendingContentDownloads += 1
                            logDesktopCloudSync {
                                "desktop.engine.content_wait_missing_remote book=${book.id} driveName=$driveName " +
                                    "localContentTs=${book.fileContentModifiedTimestamp} remoteContentTs=${remote.fileContentModifiedTimestamp}"
                            }
                        }
                    }

                    (localFile == null || !localFile.isFile) && driveFiles[driveName] != null -> {
                        val remote = remoteBooksMap[book.id] ?: return@forEach
                        logDesktopCloudSync { "desktop.engine.content_download_missing_local book=${book.id} driveName=$driveName" }
                        val downloaded = downloadRemoteBook(input.driveAccessToken, remote, book, driveFiles)
                        if (downloaded != null) {
                            state = state.upsertCloudBook(downloaded)
                            downloadedBooks += 1
                        } else {
                            pendingContentDownloads += 1
                        }
                    }

                    (localFile == null || !localFile.isFile) && driveFiles[driveName] == null -> {
                        pendingContentDownloads += 1
                        logDesktopCloudSync { "desktop.engine.content_wait_missing_remote book=${book.id} driveName=$driveName" }
                        state = state.removeCloudBook(book.id)
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

        logDesktopCloudSync {
            "desktop.engine.full_sync.complete user=${input.userId} uploaded=$uploadedBooks downloaded=$downloadedBooks " +
                "pendingContent=$pendingContentDownloads books=${state.rawLibraryBooks.size}"
        }
        return DesktopCloudSyncResult(
            state = state,
            shelfRecords = shelfRecords,
            shelfRefs = shelfRefs,
            customFonts = customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() },
            uploadedBooks = uploadedBooks,
            downloadedBooks = downloadedBooks,
            pendingContentDownloads = pendingContentDownloads
        )
    }

    suspend fun uploadBookAndMetadata(
        input: DesktopCloudSyncInput,
        book: BookItem,
        uploadContent: Boolean,
        uploadAnnotations: Boolean = true,
        remoteHasAnnotations: Boolean = false,
        remoteAnnotationModifiedTimestamp: Long = 0L,
        remoteContentModifiedTimestamp: Long? = null
    ): BookItem? {
        if (isDesktopPdfReflowBookId(book.id)) {
            logDesktopCloudSync { "desktop.upload.skip reason=reflow ${book.desktopCloudSyncSummary()}" }
            return null
        }
        if (book.sourceFolder != null) {
            logDesktopCloudSync { "desktop.upload.skip reason=folder_book ${book.desktopCloudSyncSummary()}" }
            return null
        }
        if (book.path?.startsWith("opds-pse") == true) {
            logDesktopCloudSync { "desktop.upload.skip reason=opds_stream ${book.desktopCloudSyncSummary()}" }
            return null
        }
        if (SharedFileCapabilities.isManualOnlyReaderFileName(book.displayName)) {
            logDesktopCloudSync { "desktop.upload.skip reason=manual_only ${book.desktopCloudSyncSummary()}" }
            return null
        }
        logDesktopCloudSync {
            "desktop.upload.start uploadContent=$uploadContent uploadAnnotations=$uploadAnnotations " +
                "remoteHasAnnotations=$remoteHasAnnotations ${book.desktopCloudSyncSummary()}"
        }
        if (uploadContent) {
            val source = book.path?.let(::File)?.takeIf { it.isFile }
            if (source != null && driveRepository.uploadFile(input.driveAccessToken, book.id, source, book.type) == null) {
                logDesktopCloudSync { "desktop.upload.content_failed book=${book.id} path=${source.absolutePath}" }
                return null
            }
            logDesktopCloudSync { "desktop.upload.content_success book=${book.id} path=${source?.absolutePath ?: "none"}" }
        }

        val hasLocalAnnotations = DesktopCloudSidecarSync.hasLocalAnnotationData(book)
        val shouldUploadAnnotations = uploadAnnotations || (!remoteHasAnnotations && hasLocalAnnotations)
        val bundle = if (shouldUploadAnnotations) DesktopCloudSidecarSync.exportAnnotationBundle(book) else null
        var uploadedAnnotationTimestamp = 0L
        logDesktopCloudAnnotations {
            "desktop.upload.annotation_decision book=${book.id} uploadAnnotations=$uploadAnnotations " +
                "remoteHas=$remoteHasAnnotations hasLocal=$hasLocalAnnotations shouldUpload=$shouldUploadAnnotations " +
                "bundleBytes=${bundle?.length() ?: 0L} " + DesktopCloudSidecarSync.localAnnotationDebugSummary(book)
        }
        try {
            if (bundle != null) {
                val mergedRemoteIntoUpload = mergeRemoteAnnotationsIntoUploadBundle(
                    accessToken = input.driveAccessToken,
                    book = book,
                    bundle = bundle,
                    remoteHasAnnotations = remoteHasAnnotations
                )
                val uploadedAnnotationFile = driveRepository.uploadAnnotationFile(input.driveAccessToken, book.id, bundle)
                if (uploadedAnnotationFile == null) {
                    logDesktopCloudAnnotations { "desktop.upload.sidecar_failed book=${book.id} bytes=${bundle.length()}" }
                    logDesktopCloudSync { "desktop.upload.sidecar_failed book=${book.id} bytes=${bundle.length()}" }
                    return null
                }
                uploadedAnnotationTimestamp = uploadedAnnotationFile.modifiedTimeMillis
                if (mergedRemoteIntoUpload) {
                    val appliedMergedLocal = DesktopCloudSidecarSync.importAnnotationBundle(
                        book = book,
                        rawJson = bundle.readText(),
                        timestamp = uploadedAnnotationTimestamp
                    )
                    logDesktopCloudAnnotations {
                        "desktop.upload.local_apply_merged book=${book.id} applied=$appliedMergedLocal " +
                            "driveTs=$uploadedAnnotationTimestamp bytes=${bundle.length()}"
                    }
                }
                DesktopCloudSidecarSync.markAnnotationPayloadSynced(book, uploadedAnnotationTimestamp)
            }
            if (bundle != null) {
                logDesktopCloudAnnotations {
                    "desktop.upload.sidecar_success book=${book.id} bytes=${bundle.length()} driveTs=$uploadedAnnotationTimestamp"
                }
            } else {
                logDesktopCloudAnnotations {
                    "desktop.upload.sidecar_skipped book=${book.id} shouldUpload=$shouldUploadAnnotations hasLocal=$hasLocalAnnotations"
                }
            }
            logDesktopCloudSync {
                "desktop.upload.sidecar_decision book=${book.id} hasLocal=$hasLocalAnnotations " +
                    "shouldUpload=$shouldUploadAnnotations uploaded=${bundle != null} bytes=${bundle?.length() ?: 0L}"
            }
        } finally {
            bundle?.delete()
        }

        val now = System.currentTimeMillis()
        val syncedBook = book.copy(
            timestamp = now,
            readingPositionModifiedTimestamp = book.effectiveCloudReadingPositionModifiedTimestamp()
        )
        val localAnnotationTimestamp = DesktopCloudSidecarSync.localAnnotationPayloadTimestamp(book)
        val syncedAnnotationTimestamp = if (bundle != null) {
            uploadedAnnotationTimestamp.takeIf { it > 0L } ?: maxOf(localAnnotationTimestamp, now)
        } else if (remoteHasAnnotations) {
            remoteAnnotationModifiedTimestamp
        } else {
            0L
        }
        val syncedHasAnnotations = if (uploadAnnotations) {
            syncedAnnotationTimestamp > 0L || (bundle != null && hasLocalAnnotations)
        } else {
            remoteHasAnnotations || syncedAnnotationTimestamp > 0L || bundle != null || hasLocalAnnotations
        }
        firestoreRepository.syncBookMetadata(
            userId = input.userId,
            book = syncedBook.toDesktopCloudBookMetadata(
                hasAnnotations = syncedHasAnnotations,
                timestamp = now,
                annotationModifiedTimestamp = syncedAnnotationTimestamp,
                contentTimestampOverride = if (uploadContent) null else remoteContentModifiedTimestamp
            ),
            originDeviceId = input.deviceId,
            idToken = input.idToken
        )
        logDesktopCloudSync {
            "desktop.upload.metadata_success user=${input.userId} device=${input.deviceId} " +
                "oldTs=${book.timestamp} newTs=$now hasAnnotations=$syncedHasAnnotations " +
                syncedBook.desktopCloudSyncSummary("synced")
        }
        logDesktopCloudAnnotations {
            "desktop.upload.metadata_success book=${book.id} oldTs=${book.timestamp} newTs=$now " +
                "readTs=${syncedBook.effectiveCloudReadingPositionModifiedTimestamp()} " +
                "annTs=$syncedAnnotationTimestamp hasAnnotations=$syncedHasAnnotations"
        }
        return syncedBook
    }

    private suspend fun mergeRemoteAnnotationsIntoUploadBundle(
        accessToken: String,
        book: BookItem,
        bundle: File,
        remoteHasAnnotations: Boolean
    ): Boolean {
        if (!remoteHasAnnotations || !bundle.isFile) return false
        val remoteTemp = File(desktopUserCacheRoot(), "remote_annotation_${book.id.toDesktopSafeFileName()}_${System.nanoTime()}.json")
        try {
            val didDownload = driveRepository.downloadAnnotationFile(accessToken, book.id, remoteTemp)
            if (!didDownload || !remoteTemp.isFile) {
                logDesktopCloudAnnotations {
                    "desktop.upload.merge_remote_missing book=${book.id} didDownload=$didDownload " +
                        "tempExists=${remoteTemp.exists()} localBytes=${bundle.length()}"
                }
                return false
            }
            val localRaw = bundle.readText()
            val remoteRaw = remoteTemp.readText()
            val mergedRaw = SharedPdfAnnotationSidecarCodec.mergeAnnotationDataJson(
                localDataJson = localRaw,
                remoteDataJson = remoteRaw,
                preferRemoteOnConflict = false
            )
            val localCount = SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(localRaw)
            val remoteCount = SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(remoteRaw)
            val mergedCount = SharedPdfAnnotationSidecarCodec.annotationCountFromDataJson(mergedRaw)
            if (mergedRaw != localRaw) {
                bundle.writeText(mergedRaw)
                logDesktopCloudAnnotations {
                    "desktop.upload.merge_remote_applied book=${book.id} localCount=$localCount " +
                        "remoteCount=$remoteCount mergedCount=$mergedCount mergedBytes=${bundle.length()}"
                }
                return true
            } else {
                logDesktopCloudAnnotations {
                    "desktop.upload.merge_remote_noop book=${book.id} localCount=$localCount " +
                        "remoteCount=$remoteCount mergedCount=$mergedCount"
                }
            }
        } catch (error: Exception) {
            logDesktopCloudAnnotations {
                "desktop.upload.merge_remote_failed book=${book.id} error=${error.message.orEmpty().logPreview(240)}"
            }
        } finally {
            remoteTemp.delete()
        }
        return false
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
                driveFiles[desktopCloudAnnotationDriveFileName(book.id)]?.id
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
            logDesktopCloudAnnotations {
                "desktop.download.start book=${book.id} remoteTs=$timestamp temp=${temp.name} " +
                    DesktopCloudSidecarSync.localAnnotationDebugSummary(book)
            }
            logDesktopCloudSync { "desktop.sidecar_download.start book=${book.id} remoteTs=$timestamp temp=${temp.name}" }
            if (!driveRepository.downloadAnnotationFile(accessToken, book.id, temp) || !temp.isFile) {
                logDesktopCloudAnnotations {
                    "desktop.download.missing book=${book.id} remoteTs=$timestamp tempExists=${temp.exists()} tempBytes=${temp.length()}"
                }
                logDesktopCloudSync { "desktop.sidecar_download.missing book=${book.id} remoteTs=$timestamp" }
                return false
            }
            val raw = temp.readText()
            logDesktopCloudAnnotations {
                "desktop.download.success book=${book.id} remoteTs=$timestamp bytes=${raw.length}"
            }
            val appliedTimestamp = timestamp.takeIf { it > 0L } ?: temp.lastModified().takeIf { it > 0L } ?: 0L
            val applied = DesktopCloudSidecarSync.importAnnotationBundle(book, raw, appliedTimestamp)
            logDesktopCloudAnnotations {
                "desktop.download.applied book=${book.id} remoteTs=$timestamp appliedTs=$appliedTimestamp applied=$applied " +
                    DesktopCloudSidecarSync.localAnnotationDebugSummary(book)
            }
            logDesktopCloudSync {
                "desktop.sidecar_download.applied book=${book.id} remoteTs=$timestamp appliedTs=$appliedTimestamp bytes=${temp.length()} applied=$applied"
            }
            applied
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
        logDesktopCloudSync { "desktop.content_download.start book=${remote.bookId} driveName=$driveName remoteContentTs=${remote.fileContentModifiedTimestamp}" }
        if (!driveRepository.downloadFile(accessToken, driveFile.id, destination)) {
            destination.delete()
            logDesktopCloudSync { "desktop.content_download.failed book=${remote.bookId} driveName=$driveName" }
            return null
        }
        val contentTimestamp = remote.fileContentModifiedTimestamp.takeIf { it > 0L } ?: destination.lastModified()
        if (contentTimestamp > 0L) destination.setLastModified(contentTimestamp)
        val downloaded = remote.toDesktopBookItem(existing = existing, downloadedPath = destination.absolutePath).copy(
            fileSize = destination.length(),
            fileContentModifiedTimestamp = contentTimestamp
        )
        logDesktopCloudSync {
            "desktop.content_download.success book=${remote.bookId} bytes=${destination.length()} contentTs=$contentTimestamp " +
                downloaded.desktopCloudSyncSummary("downloaded")
        }
        return downloaded
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
    timestamp: Long = this.timestamp,
    annotationModifiedTimestamp: Long = 0L,
    contentTimestampOverride: Long? = null
): DesktopCloudBookMetadata {
    val position = readerPosition.takeIf { type.usesCloudLocatorMetadata() }
    val supportsReaderAnnotations = type.usesCloudLocatorMetadata()
    val bookmarksJson = desktopPdfBookmarksMetadataJson(this)
        ?: if (supportsReaderAnnotations) {
            readerBookmarks
                .mapNotNull { it.toDesktopCloudEpubBookmarkOrNull() }
                .let(EpubAnnotationSerializer::bookmarksToJson)
        } else {
            null
        }
    val highlightsJson = if (supportsReaderAnnotations) {
        EpubAnnotationSerializer.highlightsToJson(readerHighlights)
    } else {
        null
    }
    val localFile = path?.let(::File)
    val contentTimestamp = contentTimestampOverride
        ?: fileContentModifiedTimestamp.takeIf { it > 0L }
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
        locatorBlockIndex = position?.blockIndex,
        locatorCharOffset = position?.charOffset,
        lastPage = if (type.usesCloudLocatorMetadata()) position?.pageIndex ?: lastPageIndex else lastPageIndex,
        progressPercentage = progressPercentage,
        isRecent = isRecent,
        isDeleted = false,
        lastModifiedTimestamp = timestamp,
        readingPositionModifiedTimestamp = effectiveCloudReadingPositionModifiedTimestamp(),
        annotationModifiedTimestamp = annotationModifiedTimestamp,
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
    val locator = if (type.usesCloudLocatorMetadata()) {
        ReaderLocator.fromLegacy(
            chapterIndex = lastChapterIndex,
            cfi = lastPositionCfi,
            pageIndex = pageIndex
        ).withFallbacks(
            blockIndex = locatorBlockIndex,
            charOffset = locatorCharOffset
        )
    } else {
        null
    }
    val remoteReadingTimestamp = effectiveCloudReadingPositionModifiedTimestamp()
    val localReadingTimestamp = existing?.effectiveCloudReadingPositionModifiedTimestamp() ?: 0L
    val useRemoteReadingPosition = existing == null ||
        remoteReadingTimestamp > localReadingTimestamp ||
        (localReadingTimestamp == 0L && hasCloudReadingPosition())
    val restoredPageIndex = if (useRemoteReadingPosition) pageIndex ?: existing?.lastPageIndex else existing?.lastPageIndex
    val restoredReaderPosition = if (type.usesCloudLocatorMetadata()) {
        if (useRemoteReadingPosition) {
            locator?.takeIf {
                it.chapterIndex != null ||
                    it.pageIndex != null ||
                    it.cfi != null ||
                    it.startOffset != null ||
                    it.blockIndex != null
            } ?: existing?.readerPosition
        } else {
            existing?.readerPosition
        }
    } else {
        null
    }
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
        progressPercentage = if (useRemoteReadingPosition) progressPercentage ?: existing?.progressPercentage else existing?.progressPercentage,
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
        lastPageIndex = restoredPageIndex,
        readerPosition = restoredReaderPosition,
        readerSettings = existing?.readerSettings,
        readerBookmarks = if (type == FileType.PDF || bookmarksJson.isNullOrBlank()) {
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
        pdfReaderViewport = if (useRemoteReadingPosition) remotePdfViewport(existing, pageIndex) else existing?.pdfReaderViewport,
        readingPositionModifiedTimestamp = if (useRemoteReadingPosition) remoteReadingTimestamp else localReadingTimestamp
    )
}

internal fun BookItem.withCloudReadingPosition(remote: DesktopCloudBookMetadata): BookItem {
    val remoteType = remote.fileType()
    val pageIndex = remote.lastPage
    val locator = if (remoteType.usesCloudLocatorMetadata()) {
        ReaderLocator.fromLegacy(
            chapterIndex = remote.lastChapterIndex,
            cfi = remote.lastPositionCfi,
            pageIndex = pageIndex
        ).withFallbacks(
            blockIndex = remote.locatorBlockIndex,
            charOffset = remote.locatorCharOffset
        ).takeIf {
            it.chapterIndex != null ||
                it.pageIndex != null ||
                it.cfi != null ||
                it.startOffset != null ||
                it.blockIndex != null
        }
    } else {
        null
    }
    return copy(
        lastPageIndex = pageIndex ?: lastPageIndex,
        readerPosition = if (remoteType.usesCloudLocatorMetadata()) locator ?: readerPosition else null,
        progressPercentage = remote.progressPercentage ?: progressPercentage,
        pdfReaderViewport = if (remoteType.usesCloudLocatorMetadata()) {
            pdfReaderViewport
        } else {
            remote.remotePdfViewport(this, pageIndex)
        },
        readingPositionModifiedTimestamp = remote.effectiveCloudReadingPositionModifiedTimestamp()
    )
}

private fun DesktopCloudBookMetadata.remotePdfViewport(
    existing: BookItem?,
    pageIndex: Int?
): SharedPdfReaderViewport? {
    if (fileType().usesCloudLocatorMetadata() || pageIndex == null) return existing?.pdfReaderViewport
    val base = existing?.pdfReaderViewport ?: SharedPdfReaderViewport()
    return base.copy(
        pageIndex = pageIndex,
        horizontalScrollOffset = 0,
        paginatedVerticalScrollOffset = 0,
        verticalFirstPageIndex = pageIndex,
        verticalFirstPageScrollOffset = 0
    )
}

private fun FileType.usesCloudLocatorMetadata(): Boolean {
    return this != FileType.PDF && this != FileType.PPTX && !SharedFileCapabilities.isComicArchive(this)
}

internal fun BookItem.hasCloudReadingPosition(): Boolean {
    return lastPageIndex != null ||
        readerPosition != null ||
        (progressPercentage ?: 0f) > 0f
}

internal fun BookItem.effectiveCloudReadingPositionModifiedTimestamp(): Long {
    return readingPositionModifiedTimestamp.takeIf { it > 0L }
        ?: timestamp.takeIf { hasCloudReadingPosition() }
        ?: 0L
}

internal fun DesktopCloudBookMetadata.hasCloudReadingPosition(): Boolean {
    return lastChapterIndex != null ||
        lastPage != null ||
        !lastPositionCfi.isNullOrBlank() ||
        locatorBlockIndex != null ||
        locatorCharOffset != null ||
        (progressPercentage ?: 0f) > 0f
}

internal fun DesktopCloudBookMetadata.effectiveCloudReadingPositionModifiedTimestamp(): Long {
    return readingPositionModifiedTimestamp.takeIf { it > 0L }
        ?: lastModifiedTimestamp.takeIf { hasCloudReadingPosition() }
        ?: 0L
}

internal fun DesktopCloudBookMetadata.effectiveCloudAnnotationModifiedTimestamp(): Long {
    return annotationModifiedTimestamp.takeIf { it > 0L }
        ?: 0L
}

internal fun DesktopCloudBookMetadata.effectiveCloudAnnotationModifiedTimestamp(sidecarModifiedTimestamp: Long): Long {
    return sidecarModifiedTimestamp.takeIf { it > 0L }
        ?: effectiveCloudAnnotationModifiedTimestamp()
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
    return sharedCloudBookContentFileName(bookId, type)
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
        remote.fileType() == local.type &&
        shouldDownloadRemoteCloudBookContent(
            localFileAvailable = localFile?.isFile == true,
            localContentModifiedTimestamp = localTimestamp,
            remoteContentModifiedTimestamp = remote.fileContentModifiedTimestamp,
            remoteDeleted = remote.isDeleted
        )
}

private fun shouldUploadLocalBookContent(local: BookItem, remote: DesktopCloudBookMetadata?): Boolean {
    val localFile = local.path?.let(::File)?.takeIf { it.isFile } ?: return false
    val localTimestamp = local.fileContentModifiedTimestamp.takeIf { it > 0L } ?: localFile.lastModified()
    return local.sourceFolder == null &&
        shouldUploadLocalCloudBookContent(
            localFileAvailable = true,
            localContentModifiedTimestamp = localTimestamp,
            remoteContentModifiedTimestamp = remote?.fileContentModifiedTimestamp
        )
}

private fun shouldUploadLocalAnnotations(
    local: BookItem,
    remote: DesktopCloudBookMetadata?,
    remoteAnnotationModifiedTimestamp: Long = remote?.effectiveCloudAnnotationModifiedTimestamp() ?: 0L,
    localSidecarTimestamp: Long = DesktopCloudSidecarSync.localAnnotationTimestamp(local)
): Boolean {
    return DesktopCloudSidecarSync.hasLocalAnnotationData(local) &&
        (remote == null || !remote.hasAnnotations || localSidecarTimestamp > remoteAnnotationModifiedTimestamp)
}

private fun remoteAnnotationDriveFileTimestamp(
    bookId: String,
    driveFiles: Map<String, DesktopDriveFile>
): Long {
    return driveFiles[desktopCloudAnnotationDriveFileName(bookId)]?.modifiedTimeMillis ?: 0L
}

internal fun desktopCloudAnnotationDriveFileName(bookId: String): String = "annotation_$bookId.json"

private fun BookItem.withDownloadedCloudContent(downloaded: BookItem?, replacePath: Boolean = true): BookItem {
    if (downloaded == null) return this
    return copy(
        path = if (replacePath) downloaded.path ?: path else path,
        fileSize = downloaded.fileSize.takeIf { it > 0L } ?: fileSize,
        fileContentModifiedTimestamp = downloaded.fileContentModifiedTimestamp.takeIf { it > 0L }
            ?: fileContentModifiedTimestamp
    )
}

private fun desktopShelfTimestamp(record: ShelfRecord, refs: List<BookShelfRef>): Long {
    val idTimestamp = record.id.split('_').firstNotNullOfOrNull { it.toLongOrNull() }
    val refsTimestamp = refs.filter { it.shelfId == record.id }.maxOfOrNull { it.addedAt }
    return maxOf(idTimestamp ?: 0L, refsTimestamp ?: 0L)
}

private fun ReaderLocator.cloudPositionCfi(): String? {
    return toStablePositionCfi()
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
