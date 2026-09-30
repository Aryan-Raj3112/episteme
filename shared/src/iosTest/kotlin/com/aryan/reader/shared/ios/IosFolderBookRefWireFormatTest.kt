package com.aryan.reader.shared.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The provider ref is encoded in Swift (`encodeRefComponent` /
 * `encodedFolderBookRef` in `ContentView.swift`) and decoded in Kotlin
 * (`SharedIosBookSourceRef`). The two implementations cannot see each other, so
 * these cases pin the wire format. A mismatch would not throw — it would make
 * every linked-folder book resolve to a folder that does not exist.
 */
class IosFolderBookRefWireFormatTest {
    @Test
    fun `encoding matches the verified swift output byte for byte`() {
        // Captured by running the real `encodeRefComponent` /
        // `encodedFolderBookRef` from ContentView.swift in a standalone Swift
        // program. If this list and the encoder ever disagree, every
        // linked-folder book stops resolving, because the folder segment would
        // decode to a name that does not exist.
        val verified = mapOf(
            "Books" to "Books",
            "My Books" to "My%20Books",
            "a/b" to "a%2Fb",
            "Café" to "Caf%C3%A9",
            "本棚" to "%E6%9C%AC%E6%A3%9A",
            "emoji 📚" to "emoji%20%F0%9F%93%9A",
            "quote\"and'apostrophe" to "quote%22and%27apostrophe",
            "semi;colon&amp" to "semi%3Bcolon%26amp",
            "100%" to "100%25",
            ".." to "..",
            " leading and trailing " to "%20leading%20and%20trailing%20",
        )
        verified.forEach { (folderName, expectedSegment) ->
            val ref = SharedIosBookSourceRef.encode(folderName, "Book.epub")
            assertEquals(
                "ios-folder-book://$expectedSegment/Book.epub",
                ref,
                "encoding drifted for folder name [$folderName]",
            )
            // And it must still decode back to the original.
            assertEquals(
                folderName,
                SharedIosBookSourceRef.decode(ref)?.folderName,
                "round trip failed for folder name [$folderName]",
            )
        }
    }

    @Test
    fun `unreserved characters stay literal`() {
        assertEquals(
            "ios-folder-book://Books-1_a.b/Series/Book.epub",
            SharedIosBookSourceRef.encode("Books-1_a.b", "Series/Book.epub")
        )
    }

    @Test
    fun `a space is percent-encoded as uppercase hex`() {
        // Swift uses String(format: "%%%02X", byte) which is uppercase; the
        // decoder is case-insensitive, but pinning it keeps the two in step.
        val ref = SharedIosBookSourceRef.encode("My Books", "Book.epub")

        assertEquals("ios-folder-book://My%20Books/Book.epub", ref)
    }

    @Test
    fun `a slash in a folder name is encoded so the split stays unambiguous`() {
        val ref = SharedIosBookSourceRef.encode("a/b", "Book.epub")

        assertEquals("ios-folder-book://a%2Fb/Book.epub", ref)
        assertEquals("a/b", SharedIosBookSourceRef.decode(ref)?.folderName)
        assertEquals(1, ref.removePrefix(SharedIosBookSourceRef.providerScheme).count { it == '/' })
    }

    @Test
    fun `every byte round trips through the decoder`() {
        // A representative spread of the byte ranges Swift's utf8 view can
        // produce, including multi-byte UTF-8.
        listOf(
            "Books",
            "My Books",
            "a/b",
            "Café",
            "本棚",
            "emoji 📚",
            "quote\"and'apostrophe",
            "semi;colon&amp",
            "100%",
            "..",
            " leading and trailing ",
        ).forEach { folderName ->
            val ref = SharedIosBookSourceRef.encode(folderName, "Book.epub")
            assertEquals(
                folderName,
                SharedIosBookSourceRef.decode(ref)?.folderName,
                "round trip failed for folder name [$folderName] via [$ref]",
            )
        }
    }

    @Test
    fun `a folder name that encodes to nothing still decodes to a space`() {
        // Kotlin's encoder substitutes %20 for an empty result; a name of only
        // separators is the case that can reach it.
        val ref = SharedIosBookSourceRef.encode("/", "Book.epub")

        assertTrue(ref.startsWith(SharedIosBookSourceRef.providerScheme))
        assertEquals("Book.epub", SharedIosBookSourceRef.decode(ref)?.relativePath)
    }
}
