package com.aryan.reader

import android.net.Uri
import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The projector resolves every folder book on each projection miss, so the synced folder's tree
 * document id has to be parsed once per folder rather than once per book: re-decoding the same SAF
 * path per book is what an OOM report caught inside `UriCodec.decode`.
 *
 * Book URIs are unique per book, so the resolver cannot memoize those the same way; it walks the
 * encoded path instead of building a `Uri` (that `Uri.parse` allocation was the allocating frame of
 * a later report). [legacySegments] keeps the previous `Uri`-based implementation so the two are
 * compared directly instead of against hand-written expectations.
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

    @Test
    fun `every safery shape resolves exactly as the uri-based implementation did`() {
        val cases = listOf(
            "primary:Books" to "primary:Books/Fantasy/One.epub",
            "primary:Books" to "primary:Books/Fantasy/Sub/Two.epub",
            "primary:Books" to "primary:Books/Three.epub",
            "primary:Other" to "primary:Other/Fantasy/One.epub",
            "primary:Books" to "primary:Books With Spaces/One.epub",
            "primary:Books" to "primary:Книги/One.epub",
            "primary:Books" to "primary:Books/A%2FB/One.epub",
            "primary:Books" to "primary:Books/50%/One.epub",
            "primary:Books" to "primary:Books",
        )
        cases.forEachIndexed { index, (folder, document) ->
            val item = book(folder, document)
            assertEquals(
                "case " + index + ": " + document + " in " + folder,
                legacySegments(item),
                AndroidFolderPathResolver().relativeFolderSegments(item)
            )
        }
    }

    @Test
    fun `uris the old parser answered empty keep answering empty`() {
        val tree = treeUri("primary:Books")
        val oddUris = listOf(
            "mailto:someone@example.com",
            "content://authority",
            "content://authority/",
            "file:///storage/emulated/0/Books/One.epub",
            tree,
            "$tree/document",
            "$tree/document/primary%3ABooks%2FOne.epub?next=document%2Fignored",
            "$tree/document#fragment",
        )
        oddUris.forEach { uri ->
            val item = RecentFileItem(
                bookId = "id",
                uriString = uri,
                type = FileType.EPUB,
                displayName = "One.epub",
                timestamp = 0L,
                sourceFolderUri = tree,
            )
            assertEquals(uri, legacySegments(item), AndroidFolderPathResolver().relativeFolderSegments(item))
        }
    }

    @Test
    fun `the resolver never builds a Uri for a book`() {
        val source = listOf(
            File("src/main/java/com/aryan/reader/AndroidFolderPathResolver.kt"),
            File("app/src/main/java/com/aryan/reader/AndroidFolderPathResolver.kt")
        ).first { it.isFile }.readText()

        val code = source.lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")

        assertFalse(code.contains("Uri.parse("))
        assertFalse(code.contains(".toUri()"))
    }

    /** The pre-fix implementation, kept verbatim so the string walker is compared against it. */
    private fun legacySegments(item: RecentFileItem): List<String> {
        val documentUriString = item.uriString ?: return emptyList()
        val rootFolderUriString = item.sourceFolderUri ?: return emptyList()
        val rootDocId = Uri.parse(rootFolderUriString).treeDocumentIdOrNull() ?: return emptyList()
        val documentId = Uri.parse(documentUriString).documentIdOrNull() ?: return emptyList()

        val rootPath = rootDocId.substringAfter(':', "")
        val documentPath = documentId.substringAfter(':', "")
        val relativeDocumentPath = when {
            rootPath.isBlank() -> documentPath
            documentPath == rootPath -> ""
            documentPath.startsWith("$rootPath/") -> documentPath.removePrefix("$rootPath/")
            else -> documentPath
        }

        return relativeDocumentPath
            .substringBeforeLast('/', "")
            .split('/')
            .map { Uri.decode(it).trim() }
            .filter { it.isNotEmpty() }
    }

    private fun Uri.treeDocumentIdOrNull(): String? {
        val segments = pathSegments
        val treeIndex = segments.indexOf("tree")
        return segments.getOrNull(treeIndex + 1)
    }

    private fun Uri.documentIdOrNull(): String? {
        val segments = pathSegments
        val documentIndex = segments.indexOf("document")
        if (documentIndex >= 0) {
            return segments.getOrNull(documentIndex + 1)
        }
        return treeDocumentIdOrNull()
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
