package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.SharedFolderPathResolver
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState

internal object AndroidSharedStateBridge {
    private val projectionBookItemCache = SharedProjectionBookItemCache()

    fun prepareLibraryProjection(
        input: LibraryProjectionInput,
        folderPathResolver: FolderPathResolver
    ): AndroidSharedLibraryProjectionContext {
        val taggedBooks = input.recentFilesFromDb.withResolvedTags(input.dbTags, input.tagRefs)
        val androidBooksById = taggedBooks
            .filterNot { it.bookId.endsWith("_reflow") }
            .associateBy { it.bookId }
        val projectionState = input.state.withAndroidFolderFallbacks(androidBooksById.values)
        val sharedInput = SharedLibraryProjectionInput(
            state = projectionState.toSharedReaderScreenState(
                rawBooks = taggedBooks,
                dbTags = input.dbTags,
                includeReaderAnnotations = false
            ),
            booksFromStore = taggedBooks
                .filterNot { it.bookId.endsWith("_reflow") }
                .let(projectionBookItemCache::map),
            shelfRecords = input.dbShelves.map { it.toSharedShelfRecord() },
            shelfRefs = input.shelfRefs.map { it.toSharedBookShelfRef() },
            tags = input.dbTags.map { it.toSharedTag() }
        )
        return AndroidSharedLibraryProjectionContext(
            projectionState = projectionState,
            sharedInput = sharedInput,
            androidBooksById = androidBooksById,
            tagEntitiesById = input.dbTags.associateBy { it.id },
            folderKeys = projectionState.syncedFolders.map { AndroidSharedFolderProjectionKey(it.uriString, it.name) },
            folderPathResolver = SharedFolderPathResolver { item ->
                androidBooksById[item.id]?.let(folderPathResolver::relativeFolderSegments).orEmpty()
            }
        )
    }

    fun projectLibrary(context: AndroidSharedLibraryProjectionContext): SharedReaderScreenState {
        return SharedLibraryStateProjector(context.folderPathResolver).project(context.sharedInput)
    }

    fun toAndroidState(
        base: ReaderScreenState,
        sharedState: SharedReaderScreenState,
        androidBooksById: Map<String, RecentFileItem>,
        tagEntitiesById: Map<String, TagEntity>
    ): ReaderScreenState {
        return sharedState.toAndroidReaderScreenState(
            base = base,
            androidBooksById = androidBooksById,
            tagEntitiesById = tagEntitiesById
        )
    }

}

internal data class AndroidSharedLibraryProjectionContext(
    val projectionState: ReaderScreenState,
    val sharedInput: SharedLibraryProjectionInput,
    val androidBooksById: Map<String, RecentFileItem>,
    val tagEntitiesById: Map<String, TagEntity>,
    val folderKeys: List<AndroidSharedFolderProjectionKey>,
    val folderPathResolver: SharedFolderPathResolver
)

internal data class AndroidSharedFolderProjectionKey(
    val uriString: String,
    val name: String
)

private fun ReaderScreenState.withAndroidFolderFallbacks(books: Collection<RecentFileItem>): ReaderScreenState {
    val knownFolders = syncedFolders.mapTo(mutableSetOf()) { it.uriString }
    val missingFolders = books
        .mapNotNull { it.sourceFolderUri }
        .filterTo(linkedSetOf()) { it !in knownFolders }
        .map { uri -> SyncedFolder(uriString = uri, name = "Local Folder", lastScanTime = 0L) }
    return if (missingFolders.isEmpty()) {
        this
    } else {
        copy(syncedFolders = syncedFolders + missingFolders)
    }
}
