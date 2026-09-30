package com.aryan.reader.shared.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosBookSourceRefTest {
    @Test
    fun `encode and decode round trip a folder and nested relative path`() {
        val ref = SharedIosBookSourceRef.encode("My Books", "Series/Season 1/Book.epub")

        val decoded = assertNotNull(SharedIosBookSourceRef.decode(ref))
        assertEquals("My Books", decoded.folderName)
        assertEquals("Series/Season 1/Book.epub", decoded.relativePath)
    }

    @Test
    fun `decode rejects values that are not provider refs`() {
        assertNull(SharedIosBookSourceRef.decode("/var/mobile/Books/Book.epub"))
        assertNull(SharedIosBookSourceRef.decode("file:///var/mobile/Books/Book.epub"))
        assertNull(SharedIosBookSourceRef.decode(""))
        assertNull(SharedIosBookSourceRef.decode(null))
        assertNull(SharedIosBookSourceRef.decode("opds-pse://example.com/stream"))
    }

    @Test
    fun `decode rejects refs without both a folder and a relative path`() {
        assertNull(SharedIosBookSourceRef.decode("ios-folder-book://Books"))
        assertNull(SharedIosBookSourceRef.decode("ios-folder-book://Books/"))
        assertNull(SharedIosBookSourceRef.decode("ios-folder-book:///Book.epub"))
    }

    @Test
    fun `encoded refs are not filesystem paths and are recognised as provider refs`() {
        val ref = SharedIosBookSourceRef.encode("Books", "Book.epub")

        assertTrue(SharedIosBookSourceRef.isProviderRef(ref))
        // A provider ref must never satisfy a plain local-path existence check,
        // or destructive code would treat it as an app-owned file.
        assertFalse(ref.startsWith("/"))
        assertFalse(ref.startsWith("file://"))
    }

    @Test
    fun `managed copy paths are detected for migration and provider refs are not`() {
        assertTrue(
            SharedIosBookSourceRef.isManagedCopyPath(
                "/var/mobile/Containers/Data/Application/ABC/Library/Application Support/LocalFolders/Books/Book.epub"
            )
        )
        assertTrue(
            SharedIosBookSourceRef.isManagedCopyPath(
                "/var/mobile/Containers/Data/Application/XYZ/Library/Application Support/LocalFolders/Books/Series/Book.epub"
            )
        )
        assertFalse(
            SharedIosBookSourceRef.isManagedCopyPath(SharedIosBookSourceRef.encode("Books", "Book.epub"))
        )
        assertFalse(SharedIosBookSourceRef.isManagedCopyPath("/var/mobile/Books/LocalFoldersLookalike/Book.epub"))
        assertFalse(SharedIosBookSourceRef.isManagedCopyPath("/var/mobile/Documents/Book.epub"))
    }

    @Test
    fun `relative path normalising drops redundant separators`() {
        val ref = SharedIosBookSourceRef.encode("Books", "/Series//Book.epub/")

        assertEquals("Series/Book.epub", assertNotNull(SharedIosBookSourceRef.decode(ref)).relativePath)
    }

    @Test
    fun `resolve joins the ref onto a resolved folder root`() {
        val ref = SharedIosBookSourceRef.decode(SharedIosBookSourceRef.encode("Books", "Series/Book.epub"))

        assertEquals("/private/Books/Series/Book.epub", assertNotNull(ref).resolveAgainst("/private/Books"))
        assertEquals("/private/Books/Series/Book.epub", assertNotNull(ref).resolveAgainst("/private/Books/"))
    }

    @Test
    fun `a folder name containing a separator cannot be confused with the path`() {
        // A picker cannot produce such a name, but a ref that mis-parsed would
        // silently address a different folder and read the wrong book. The
        // separator must be encoded so the folder segment stays unambiguous.
        val ref = SharedIosBookSourceRef.encode("../../etc", "Book.epub")

        val decoded = assertNotNull(SharedIosBookSourceRef.decode(ref))
        assertEquals("../../etc", decoded.folderName)
        assertEquals("Book.epub", decoded.relativePath)
        // The folder segment carries no literal separator, so `indexOf('/')`
        // splits the ref at exactly one place.
        val body = ref.removePrefix(SharedIosBookSourceRef.providerScheme)
        assertEquals(1, body.count { it == '/' })
    }

    @Test
    fun `round trip is lossless for non-ascii folder names`() {
        val ref = SharedIosBookSourceRef.encode("本棚 / Café", "Serie/Book.epub")

        val decoded = assertNotNull(SharedIosBookSourceRef.decode(ref))
        assertEquals("本棚 / Café", decoded.folderName)
        assertEquals("Serie/Book.epub", decoded.relativePath)
    }
}
