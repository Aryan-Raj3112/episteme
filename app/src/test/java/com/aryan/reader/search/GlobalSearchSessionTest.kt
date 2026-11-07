package com.aryan.reader.search

import com.aryan.reader.shared.search.SharedGlobalSearchCoordinator
import com.aryan.reader.shared.search.SharedSearchChunk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GlobalSearchSessionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun session(): GlobalSearchSession {
        // Pre-index one book straight through a writer-side coordinator.
        val storage = GlobalSearchFileStorage.fromFilesDir(temp.root)
        SharedGlobalSearchCoordinator(storage).upsertBookChunks(
            "indexed",
            "10:20",
            listOf(SharedSearchChunk(chunkIndex = 0, text = "the harvest festival")),
        )
        return GlobalSearchSession(temp.root, storage)
    }

    private fun entries() = listOf(
        GlobalSearchSession.Entry("indexed", "10:20"),
        GlobalSearchSession.Entry("missing", "1:2"),
    )

    @Test
    fun `sync reports only books without a fresh payload`() = runTest {
        assertEquals(listOf(GlobalSearchSession.Entry("missing", "1:2")), session().syncLibrary(entries()))
    }

    @Test
    fun `changed hash needs work even with a payload on disk`() = runTest {
        val missing = session().syncLibrary(listOf(GlobalSearchSession.Entry("indexed", "10:21")))
        assertEquals(listOf(GlobalSearchSession.Entry("indexed", "10:21")), missing)
    }

    @Test
    fun `query finds indexed book and ignores blank queries`() = runTest {
        val s = session()
        val hits = s.query(entries(), "harvest")
        assertEquals(listOf("indexed"), hits.map { it.bookId })
        // Snippets travel with the hit so library rows can preview the match.
        val snippet = hits.single().hits.single()
        assertTrue(snippet.snippet.contains("harvest"))
        assertTrue(snippet.matchRanges.isNotEmpty())
        assertTrue(s.query(entries(), "").isEmpty())
        assertTrue(s.query(entries(), "absent-term").isEmpty())
    }

    @Test
    fun `query never surfaces a book that left the library`() = runTest {
        val s = session()
        // "indexed" exists on disk but is not in the entries passed in.
        val results = s.query(listOf(GlobalSearchSession.Entry("missing", "1:2")), "harvest")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `remove rediscovers the payload until its file is deleted`() = runTest {
        val storage = GlobalSearchFileStorage.fromFilesDir(temp.root)
        SharedGlobalSearchCoordinator(storage).upsertBookChunks(
            "indexed",
            "10:20",
            listOf(SharedSearchChunk(chunkIndex = 0, text = "the harvest festival")),
        )
        val s = GlobalSearchSession(temp.root, storage)
        // First sync loads the payload; only the unindexed book needs work.
        assertEquals(listOf(GlobalSearchSession.Entry("missing", "1:2")), s.syncLibrary(entries()))
        // Forgetting memory alone re-discovers the file on next sync.
        s.remove("indexed")
        assertEquals(listOf(GlobalSearchSession.Entry("missing", "1:2")), s.syncLibrary(entries()))
        // The delete path (file + memory) needs work again.
        storage.deleteBook("indexed")
        s.remove("indexed")
        assertEquals(entries(), s.syncLibrary(entries()))
    }
}
