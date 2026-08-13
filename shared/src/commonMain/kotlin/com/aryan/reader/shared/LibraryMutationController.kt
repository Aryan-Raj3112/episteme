package com.aryan.reader.shared

import kotlin.random.Random

/** Persistence boundary for library mutations. Platform implementations own transactions and storage types. */
interface LibraryMutationStore {
    suspend fun createShelf(record: ShelfRecord, createdAt: Long)
    suspend fun addBooksToShelf(shelfId: String, bookIds: Set<String>)
    suspend fun renameShelf(shelfId: String, name: String)
    suspend fun deleteShelf(shelfId: String)
    suspend fun removeBooksFromShelf(shelfId: String, bookIds: Set<String>)
    suspend fun createTag(tag: Tag, createdAt: Long)
    suspend fun assignTagToBooks(tagId: String, bookIds: Set<String>)
    suspend fun removeTagFromBooks(tagId: String, bookIds: Set<String>)
    suspend fun deleteTag(tagId: String)
}

class LibraryMutationController(
    private val store: LibraryMutationStore,
    private val newId: () -> String,
    private val nowMillis: () -> Long,
    private val selectTagColor: () -> Int = { TAG_COLORS.random(Random.Default) },
    private val onShelfChanged: suspend (String) -> Unit = {},
) {
    suspend fun createShelf(name: String, selectedBookIds: Iterable<String> = emptyList()): String? {
        val id = newId()
        val now = nowMillis()
        val record = SharedLibraryEditor.createShelfRecord(name, id) ?: return null
        val books = SharedLibraryEditor.cleanBookIds(selectedBookIds)
        store.createShelf(record, now)
        if (books.isNotEmpty()) store.addBooksToShelf(id, books)
        onShelfChanged(id)
        return id
    }

    suspend fun renameShelf(shelfId: String, name: String): Boolean {
        val cleanId = shelfId.trim()
        val cleanName = SharedLibraryEditor.cleanShelfName(name)
        if (!SharedLibraryEditor.canMutateShelf(cleanId) || cleanName == null) return false
        store.renameShelf(cleanId, cleanName)
        onShelfChanged(cleanId)
        return true
    }

    suspend fun deleteShelf(shelfId: String): Boolean {
        val cleanId = shelfId.trim()
        if (!SharedLibraryEditor.canMutateShelf(cleanId)) return false
        store.deleteShelf(cleanId)
        onShelfChanged(cleanId)
        return true
    }

    suspend fun deleteShelves(shelfIds: Iterable<String>): Set<String> {
        val cleaned = shelfIds
            .mapTo(linkedSetOf()) { it.trim() }
            .filterTo(linkedSetOf(), SharedLibraryEditor::canMutateShelf)
        cleaned.forEach { deleteShelf(it) }
        return cleaned
    }

    suspend fun addBooksToShelves(bookIds: Iterable<String>, shelfIds: Iterable<String>): Set<String> {
        val books = SharedLibraryEditor.cleanBookIds(bookIds)
        if (books.isEmpty()) return emptySet()
        val shelves = shelfIds
            .mapTo(linkedSetOf()) { it.trim() }
            .filterTo(linkedSetOf(), SharedLibraryEditor::canMutateShelf)
        shelves.forEach { shelfId ->
            store.addBooksToShelf(shelfId, books)
            onShelfChanged(shelfId)
        }
        return shelves
    }

    suspend fun removeBooksFromShelf(shelfId: String, bookIds: Iterable<String>): Boolean {
        val cleanId = shelfId.trim()
        val books = SharedLibraryEditor.cleanBookIds(bookIds)
        if (!SharedLibraryEditor.canMutateShelf(cleanId) || books.isEmpty()) return false
        store.removeBooksFromShelf(cleanId, books)
        onShelfChanged(cleanId)
        return true
    }

    suspend fun createAndAssignTag(name: String, bookIds: Iterable<String>): String? {
        val books = SharedLibraryEditor.cleanBookIds(bookIds)
        if (books.isEmpty()) return null
        val tag = SharedLibraryEditor.createTag(name, newId(), selectTagColor()) ?: return null
        store.createTag(tag, nowMillis())
        store.assignTagToBooks(tag.id, books)
        return tag.id
    }

    suspend fun setTagAssigned(tagId: String, bookIds: Iterable<String>, assigned: Boolean): Boolean {
        val cleanId = tagId.trim()
        val books = SharedLibraryEditor.cleanBookIds(bookIds)
        if (cleanId.isBlank() || books.isEmpty()) return false
        if (assigned) store.assignTagToBooks(cleanId, books) else store.removeTagFromBooks(cleanId, books)
        return true
    }

    suspend fun deleteTag(tagId: String): String? {
        val cleanId = tagId.trim().takeIf { it.isNotBlank() } ?: return null
        store.deleteTag(cleanId)
        return cleanId
    }

    companion object {
        val TAG_COLORS: List<Int> = listOf(
            0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
            0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(),
            0xFF4DB6AC.toInt(), 0xFF81C784.toInt(), 0xFFAED581.toInt(), 0xFFFF8A65.toInt(),
            0xFFA1887F.toInt(), 0xFF90A4AE.toInt(),
        )
    }
}
