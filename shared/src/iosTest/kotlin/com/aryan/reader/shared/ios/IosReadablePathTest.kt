package com.aryan.reader.shared.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
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
    fun `a linked-folder ref resolves through the installed bookmark resolver`() {
        // Regression: `resolveIosReadablePath` used to return null for every
        // provider ref, which made all twelve PDF read sites fail with
        // "PDF path is unavailable" and marked the book "not available" in the
        // library. A ref must resolve to a real path.
        val root = NSTemporaryDirectory() + "ref-resolve-" + NSUUID().UUIDString
        val file = root + "/Weir_Egg.pdf"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = root,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        assertTrue(writeProbe(file))
        val previous = IosFolderBookScope.current()
        try {
            IosFolderBookScope.install { folderName -> if (folderName == "Books") root else null }
            val ref = SharedIosBookSourceRef.encode("Books", "Weir_Egg.pdf")

            assertEquals(file, ref.resolveIosReadablePath())
            assertTrue(ref.isIosReadableBookPath(), "a resolvable ref must report as readable")
        } finally {
            IosFolderBookScope.install(previous)
            NSFileManager.defaultManager.removeItemAtPath(root, error = null)
        }
    }

    @Test
    fun `a linked-folder ref resolves for every file in a nested folder`() {
        val root = NSTemporaryDirectory() + "ref-nested-" + NSUUID().UUIDString
        val dir = root + "/Series"
        val file = dir + "/Book.pdf"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        assertTrue(writeProbe(file))
        val previous = IosFolderBookScope.current()
        try {
            IosFolderBookScope.install { root }
            val ref = SharedIosBookSourceRef.encode("Books", "Series/Book.pdf")

            assertEquals(file, ref.resolveIosReadablePath())
        } finally {
            IosFolderBookScope.install(previous)
            NSFileManager.defaultManager.removeItemAtPath(root, error = null)
        }
    }

    @Test
    fun `a ref whose folder cannot be resolved is unreadable rather than wrong`() {
        val previous = IosFolderBookScope.current()
        try {
            IosFolderBookScope.install { null }
            val ref = SharedIosBookSourceRef.encode("Missing", "Book.epub")

            assertNull(ref.resolveIosReadablePath(), "must not guess a path for an unresolvable ref")
            assertFalse(ref.isIosReadableBookPath())
        } finally {
            IosFolderBookScope.install(previous)
        }
    }

    @Test
    fun `the scope registry yields no token when the folder cannot be resolved`() {
        // Guards the default resolver: with nothing installed, acquiring must
        // fail cleanly rather than hand back an unscoped token.
        assertNull(IosFolderScopeRegistry.acquire("NoSuchFolder"))
    }

    private fun writeProbe(path: String): Boolean {
        val bytes = "probe".encodeToByteArray()
        val file = fopen(path, "wb") ?: return false
        return try {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), file).toInt() == bytes.size
            }
        } finally {
            fclose(file)
        }
    }
}
