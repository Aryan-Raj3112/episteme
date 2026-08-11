package com.aryan.reader

import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.shared.LibraryMutationStore
import com.aryan.reader.shared.ShelfRecord
import com.aryan.reader.shared.Tag

/** Room-backed execution adapter; mutation policy and sequencing live in shared. */
internal class AndroidLibraryMutationStore(
    private val repository: RecentFilesRepository,
) : LibraryMutationStore {
    override suspend fun createShelf(record: ShelfRecord, createdAt: Long) {
        repository.addShelf(record.toShelfEntity(createdAt))
    }

    override suspend fun addBooksToShelf(shelfId: String, bookIds: Set<String>) {
        repository.addBooksToShelf(shelfId, bookIds.toList())
    }

    override suspend fun renameShelf(shelfId: String, name: String) {
        repository.renameShelf(shelfId, name)
    }

    override suspend fun deleteShelf(shelfId: String) {
        repository.deleteShelf(shelfId)
    }

    override suspend fun removeBooksFromShelf(shelfId: String, bookIds: Set<String>) {
        repository.removeBooksFromShelf(shelfId, bookIds.toList())
    }

    override suspend fun createTag(tag: Tag, createdAt: Long) {
        repository.createTag(tag.toTagEntity(createdAt))
    }

    override suspend fun assignTagToBooks(tagId: String, bookIds: Set<String>) {
        bookIds.forEach { repository.assignTagToBook(it, tagId) }
    }

    override suspend fun removeTagFromBooks(tagId: String, bookIds: Set<String>) {
        bookIds.forEach { repository.removeTagFromBook(it, tagId) }
    }

    override suspend fun deleteTag(tagId: String) {
        repository.deleteTag(tagId)
    }
}
