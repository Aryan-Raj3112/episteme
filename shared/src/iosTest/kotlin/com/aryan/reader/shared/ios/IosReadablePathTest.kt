package com.aryan.reader.shared.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosReadablePathTest {
    @Test
    fun `plain absolute paths pass through unchanged`() {
        assertEquals("/var/mobile/Books/Book.epub", "/var/mobile/Books/Book.epub".resolveIosReadablePath())
    }

    @Test
    fun `file urls are unwrapped and percent-decoded to a filesystem path`() {
        assertEquals(
            "/var/mobile/Books/Book One.epub",
            "file:///var/mobile/Books/Book%20One.epub".resolveIosReadablePath()
        )
    }

    @Test
    fun `blank and null paths do not resolve`() {
        assertNull(null.resolveIosReadablePath())
        assertNull("".resolveIosReadablePath())
        assertNull("   ".resolveIosReadablePath())
    }

    @Test
    fun `opds stream refs are left for the stream loader`() {
        // Not a filesystem path, and not a linked-folder ref either. Passing it
        // through keeps the existing stream handling intact.
        assertEquals(
            "opds-pse://example.com/stream",
            "opds-pse://example.com/stream".resolveIosReadablePath()
        )
    }

    @Test
    fun `a linked-folder ref is never resolved to a guessed path`() {
        // The ref needs a live security scope to resolve. Returning null makes
        // callers report "unavailable" instead of reading some other file.
        val ref = SharedIosBookSourceRef.encode("Books", "Book.epub")

        assertNull(ref.resolveIosReadablePath())
    }

    @Test
    fun `a linked-folder ref is not reported as readable without a resolver installed`() {
        val ref = SharedIosBookSourceRef.encode("Books", "Book.epub")

        // No resolver is installed in tests, so the ref cannot be proven
        // readable. It must not be reported missing either, because the folder
        // sync reconciliation deletes books whose path looks absent.
        assertFalse(ref.isIosReadableBookPath())
    }

    @Test
    fun `a missing plain path is reported unreadable`() {
        assertFalse("/var/mobile/definitely-missing-9f2b/Book.epub".isIosReadableBookPath())
    }

    @Test
    fun `the scope registry yields no token when the folder cannot be resolved`() {
        // Guards the default resolver: with nothing installed, acquiring must
        // fail cleanly rather than hand back an unscoped token.
        assertNull(IosFolderScopeRegistry.acquire("NoSuchFolder"))
    }
}
