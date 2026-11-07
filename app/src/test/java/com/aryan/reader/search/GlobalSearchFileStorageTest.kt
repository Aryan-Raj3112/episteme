package com.aryan.reader.search

import com.aryan.reader.shared.search.SharedGlobalSearchIndex
import com.aryan.reader.shared.search.SharedSearchChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GlobalSearchFileStorageTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun storage() = GlobalSearchFileStorage.fromFilesDir(temp.root)

    private fun persisted(bookId: String, hash: String = "h1") = SharedGlobalSearchIndex.build(
        bookId,
        hash,
        listOf(SharedSearchChunk(chunkIndex = 0, chapterIndex = 1, text = "hello world")),
    ).toPersisted()

    @Test
    fun `round trip preserves index and search works`() {
        val store = storage()
        store.saveBook(persisted("book-1"))
        val loaded = store.loadBook("book-1")!!
        assertEquals("h1", loaded.contentHash)
        assertEquals(listOf("hello world"), loaded.chunkTexts)
        val restored = SharedGlobalSearchIndex.fromPersisted(loaded)
        assertEquals(1, restored.search(com.aryan.reader.shared.search.parseSharedSearchQuery("hello")).size)
    }

    @Test
    fun `missing book loads null and delete removes file`() {
        val store = storage()
        assertNull(store.loadBook("absent"))
        store.saveBook(persisted("gone"))
        store.deleteBook("gone")
        assertNull(store.loadBook("gone"))
    }

    @Test
    fun `corrupt file reads as missing and is removed`() {
        val store = storage()
        store.saveBook(persisted("bad"))
        store.fileFor("bad").writeText("{not json")
        assertNull(store.loadBook("bad"))
        assertTrue(store.fileFor("bad").exists().not())
    }

    @Test
    fun `long book ids stay within sidecar byte cap`() {
        val store = storage()
        val longId = "b".repeat(400)
        store.saveBook(persisted(longId))
        val nameBytes = store.fileFor(longId).name.toByteArray(Charsets.UTF_8).size
        assertTrue(nameBytes <= 200)
        assertEquals("h1", store.loadBook(longId)!!.contentHash)
    }
}
