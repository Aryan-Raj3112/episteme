package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.SharedFolderPathResolver
import com.aryan.reader.shared.SharedLibraryProjectionInput
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState

/** Android persistence/model conversion around the shared library projector. */
internal object AndroidLibraryProjectionAdapter {
    private val projectionBookItemCache = SharedProjectionBookItemCache()

    fun prepare(
        input: LibraryProjectionInput,
        folderPathResolver: FolderPathResolver
    ): AndroidLibraryProjectionContext {
        val taggedBooks = input.recentFilesFromDb.withResolvedTags(input.dbTags, input.tagRefs)
        val androidBooksById = taggedBooks
            .filterNot { it.bookId.endsWith("_reflow") }
            .associateBy { it.bookId }
        val projectionState = input.state.withAndroidFolderFallbacks(androidBooksById.values)
        val sharedInput = SharedLibraryProjectionInput(
            state = projectionState.toSharedLibraryProjectionState(
                rawBooks = taggedBooks,
                dbTags = input.dbTags
            ),
            booksFromStore = taggedBooks
                .filterNot { it.bookId.endsWith("_reflow") }
                .let(projectionBookItemCache::map),
            shelfRecords = input.dbShelves.map { it.toSharedShelfRecord() },
            shelfRefs = input.shelfRefs.map { it.toSharedBookShelfRef() },
            tags = input.dbTags.map { it.toSharedTag() }
        )
        return AndroidLibraryProjectionContext(
            projectionState = projectionState,
            sharedInput = sharedInput,
            androidBooksById = androidBooksById,
            tagEntitiesById = input.dbTags.associateBy { it.id },
            folderKeys = projectionState.syncedFolders.map { AndroidFolderProjectionKey(it.uriString, it.name) },
            folderPathResolver = SharedFolderPathResolver { item ->
                androidBooksById[item.id]?.let(folderPathResolver::relativeFolderSegments).orEmpty()
            }
        )
    }

    fun project(context: AndroidLibraryProjectionContext): SharedReaderScreenState =
        SharedLibraryStateProjector(context.folderPathResolver).project(context.sharedInput)

    fun restoreAndroidState(
        base: ReaderScreenState,
        sharedState: SharedReaderScreenState,
        androidBooksById: Map<String, RecentFileItem>,
        tagEntitiesById: Map<String, TagEntity>
    ): ReaderScreenState = sharedState.toAndroidLibraryProjectionState(
        base = base,
        androidBooksById = androidBooksById,
        tagEntitiesById = tagEntitiesById
    )
}

internal data class AndroidLibraryProjectionContext(
    val projectionState: ReaderScreenState,
    val sharedInput: SharedLibraryProjectionInput,
    val androidBooksById: Map<String, RecentFileItem>,
    val tagEntitiesById: Map<String, TagEntity>,
    val folderKeys: List<AndroidFolderProjectionKey>,
    val folderPathResolver: SharedFolderPathResolver
)

internal data class AndroidFolderProjectionKey(
    val uriString: String,
    val name: String
)

private fun ReaderScreenState.withAndroidFolderFallbacks(books: Collection<RecentFileItem>): ReaderScreenState {
    val knownFolders = syncedFolders.mapTo(mutableSetOf()) { it.uriString }
    val missingFolders = books
        .mapNotNull { it.sourceFolderUri }
        .filterTo(linkedSetOf()) { it !in knownFolders }
        .map { uri -> SyncedFolder(uriString = uri, name = "Local Folder", lastScanTime = 0L) }
    return if (missingFolders.isEmpty()) this else copy(syncedFolders = syncedFolders + missingFolders)
}
