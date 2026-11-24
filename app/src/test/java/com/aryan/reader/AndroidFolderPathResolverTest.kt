package com.aryan.reader

import android.net.Uri
import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The projector resolves every folder book on each projection miss, so the synced folder's tree
 * document id has to be parsed once per folder rather than once per book: re-decoding the same SAF
 * path per book is what an OOM report caught inside `UriCodec.decode`.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidFolderPathResolverTest {

    private val resolver = AndroidFolderPathResolver()

    @Test
    fun `a folder's root is parsed once no matter how many books it holds`() {
        val folder = "primary:Books"

        val fantasy = resolver.relativeFolderSegments(book(folder, "$folder/Fantasy/One.epub"))
        val nested = resolver.relativeFolderSegments(book(folder, "$folder/Fantasy/Sub/Two.epub"))
        val rootLevel = resolver.relativeFolderSegments(book(folder, "$folder/Three.epub"))

        assertEquals(listOf("Fantasy"), fantasy)
        assertEquals(listOf("Fantasy", "Sub"), nested)
        assertEquals(emptyList<String>(), rootLevel)
        assertEquals(1, resolver.rootDocumentIdParseCount)
    }

    @Test
    fun `each synced folder is parsed separately`() {
        assertEquals(
            listOf("A"),
            resolver.relativeFolderSegments(book("primary:Books", "primary:Books/A/x.epub"))
        )
        assertEquals(
            listOf("B"),
            resolver.relativeFolderSegments(book("primary:Other", "primary:Other/B/y.epub"))
        )

        assertEquals(2, resolver.rootDocumentIdParseCount)
    }

    @Test
    fun `a book outside its synced folder keeps its full document path`() {
        val segments = resolver.relativeFolderSegments(
            book("primary:Books", "primary:Other/Fantasy/One.epub")
        )

        assertEquals(listOf("Other", "Fantasy"), segments)
    }

    @Test
    fun `a root uri without a tree document id is answered from the memo`() {
        val nonTree = RecentFileItem(
            bookId = "id",
            uriString = "content://com.android.externalstorage.documents/document/primary%3ABooks%2FOne.epub",
            type = FileType.EPUB,
            displayName = "One.epub",
            timestamp = 0L,
            sourceFolderUri = "content://authority/",
        )

        assertEquals(emptyList<String>(), resolver.relativeFolderSegments(nonTree))
        assertEquals(emptyList<String>(), resolver.relativeFolderSegments(nonTree))
        assertEquals(1, resolver.rootDocumentIdParseCount)
    }

    private fun book(rootDocumentId: String, documentId: String): RecentFileItem =
        RecentFileItem(
            bookId = documentId,
            uriString = treeUri(rootDocumentId) + "/document/" + Uri.encode(documentId),
            type = FileType.EPUB,
            displayName = documentId.substringAfterLast('/'),
            timestamp = 0L,
            sourceFolderUri = treeUri(rootDocumentId),
        )

    private fun treeUri(rootDocumentId: String): String =
        "content://com.android.externalstorage.documents/tree/" + Uri.encode(rootDocumentId)
}
