package com.aryan.reader.shared

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryMutationControllerTest {
    private val store = RecordingLibraryMutationStore()
    private val synced = mutableListOf<String>()
    private val controller = LibraryMutationController(
        store = store,
        newId = { "generated-id" },
        nowMillis = { 42L },
        selectTagColor = { 123 },
        onShelfChanged = synced::add,
    )

    @Test
    fun `create shelf sanitizes inputs persists books then requests sync`() = runTest {
        assertEquals("generated-id", controller.createShelf("  Reading  ", listOf(" book ", "", "book")))
        assertEquals(ShelfRecord("generated-id", "Reading") to 42L, store.shelves.single())
        assertEquals("generated-id" to setOf("book"), store.addedBooks.single())
        assertEquals(listOf("generated-id"), synced)
    }

    @Test
    fun `invalid and protected shelf mutations do not touch store`() = runTest {
        assertNull(controller.createShelf("   "))
        assertFalse(controller.renameShelf("unshelved", "Changed"))
        assertFalse(controller.deleteShelf(" "))
        assertTrue(store.shelves.isEmpty())
        assertTrue(store.renamedShelves.isEmpty())
        assertTrue(store.deletedShelves.isEmpty())
        assertTrue(synced.isEmpty())
    }

    @Test
    fun `tag workflow owns sanitization identity color and bulk assignment`() = runTest {
        assertEquals("generated-id", controller.createAndAssignTag("  Favorite  ", listOf(" a ", "b", "a")))
        assertEquals(Tag("generated-id", "Favorite", 123) to 42L, store.tags.single())
        assertEquals("generated-id" to setOf("a", "b"), store.assignedTags.single())
        assertTrue(controller.setTagAssigned(" generated-id ", setOf("a"), assigned = false))
        assertEquals("generated-id" to setOf("a"), store.removedTags.single())
    }
}

private class RecordingLibraryMutationStore : LibraryMutationStore {
    val shelves = mutableListOf<Pair<ShelfRecord, Long>>()
    val addedBooks = mutableListOf<Pair<String, Set<String>>>()
    val renamedShelves = mutableListOf<Pair<String, String>>()
    val deletedShelves = mutableListOf<String>()
    val tags = mutableListOf<Pair<Tag, Long>>()
    val assignedTags = mutableListOf<Pair<String, Set<String>>>()
    val removedTags = mutableListOf<Pair<String, Set<String>>>()

    override suspend fun createShelf(record: ShelfRecord, createdAt: Long) { shelves += record to createdAt }
    override suspend fun addBooksToShelf(shelfId: String, bookIds: Set<String>) { addedBooks += shelfId to bookIds }
    override suspend fun renameShelf(shelfId: String, name: String) { renamedShelves += shelfId to name }
    override suspend fun deleteShelf(shelfId: String) { deletedShelves += shelfId }
    override suspend fun removeBooksFromShelf(shelfId: String, bookIds: Set<String>) = Unit
    override suspend fun createTag(tag: Tag, createdAt: Long) { tags += tag to createdAt }
    override suspend fun assignTagToBooks(tagId: String, bookIds: Set<String>) { assignedTags += tagId to bookIds }
    override suspend fun removeTagFromBooks(tagId: String, bookIds: Set<String>) { removedTags += tagId to bookIds }
    override suspend fun deleteTag(tagId: String) = Unit
}
