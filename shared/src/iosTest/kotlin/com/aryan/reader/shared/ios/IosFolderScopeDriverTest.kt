package com.aryan.reader.shared.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosFolderScopeDriverTest {
    @Test
    fun `driver acquire holds scope across resolve plus read`() {
        // Regression for the device-only "EPUB path is unavailable": the short
        // resolver stops scope on return, so a resolve-then-read split dies on
        // device while passing in the simulator. Scoped helpers must keep the
        // driver-held scope for the whole block.
        val root = NSTemporaryDirectory() + "scope-driver-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null)
        val file = root + "/Book.epub"
        assertTrue(writeProbe(file, "hello"))
        var releases = 0
        val driver = object : IosFolderScopeDriver {
            override fun acquire(folderName: String): String? =
                if (folderName == "Books") root else null
            override fun release(folderName: String) {
                releases++
            }
        }
        IosFolderBookScope.installDriver(driver)
        try {
            val ref = SharedIosBookSourceRef.encode("Books", "Book.epub")
            val bytes = withIosFolderBookPath(ref) { resolved ->
                assertEquals(file, resolved)
                NSFileManager.defaultManager.contentsAtPath(resolved)?.toIosByteArray()
            }
            assertNotNull(bytes)
            assertEquals("hello", bytes.decodeToString())
            assertEquals(1, releases, "one acquire must balance with one release")

            val direct = readIosFolderBookBytes(ref)
            assertNotNull(direct)
            assertEquals("hello", direct.decodeToString())
        } finally {
            IosFolderBookScope.installDriver(null)
            NSFileManager.defaultManager.removeItemAtPath(root, error = null)
        }
    }

    @Test
    fun `scoped read returns null rather than a guess when unreachable`() {
        IosFolderBookScope.installDriver(object : IosFolderScopeDriver {
            override fun acquire(folderName: String): String? = null
            override fun release(folderName: String) = Unit
        })
        try {
            val ref = SharedIosBookSourceRef.encode("Missing", "Book.epub")
            assertNull(withIosFolderBookPath(ref) { it })
            assertNull(readIosFolderBookBytes(ref))
            assertNull(withResolvedIosBookPath(ref) { it })
        } finally {
            IosFolderBookScope.installDriver(null)
        }
    }

    @Test
    fun `provider refs are stat'ed inside the held scope`() {
        // Device-shaped regression: iOS reports "not found" for both an
        // unreachable path and a missing file, so the existence check has to
        // run while the folder scope is still held. A present file must read
        // as available and a missing file as unavailable — never the reverse.
        val root = NSTemporaryDirectory() + "scope-stat-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null)
        val present = root + "/Present.pdf"
        assertTrue(writeProbe(present, "pdf"))
        var held = 0
        IosFolderBookScope.installDriver(object : IosFolderScopeDriver {
            override fun acquire(folderName: String): String? {
                if (folderName != "Books") return null
                held++
                return root
            }

            override fun release(folderName: String) {
                held--
            }
        })
        try {
            val presentRef = SharedIosBookSourceRef.encode("Books", "Present.pdf")
            assertTrue(presentRef.isIosReadableBookPath())
            assertEquals(0, held, "every acquire must balance before the next check")
            assertEquals(present, resolveIosFolderBookPath(presentRef))
            assertEquals(0, held)

            val missingRef = SharedIosBookSourceRef.encode("Books", "Missing.pdf")
            assertFalse(missingRef.isIosReadableBookPath())
            assertNull(resolveIosFolderBookPath(missingRef))
            assertEquals(0, held)
        } finally {
            IosFolderBookScope.installDriver(null)
            NSFileManager.defaultManager.removeItemAtPath(root, error = null)
        }
    }

    @Test
    fun `suspend resolution holds scope across the whole block`() = runTest {
        // Long PDF work stays inside one held scope; releasing it early is the
        // device-only failure this guards.
        val root = NSTemporaryDirectory() + "scope-suspend-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null)
        val file = root + "/Book.epub"
        assertTrue(writeProbe(file, "held"))
        var held = 0
        var releasedInsideBlock = -1
        IosFolderBookScope.installDriver(object : IosFolderScopeDriver {
            override fun acquire(folderName: String): String? = root.also { held++ }
            override fun release(folderName: String) {
                held--
            }
        })
        try {
            val ref = SharedIosBookSourceRef.encode("Books", "Book.epub")
            val read = withResolvedIosBookPathSuspend(ref) { resolved ->
                assertEquals(file, resolved)
                releasedInsideBlock = held
                NSFileManager.defaultManager.contentsAtPath(resolved)?.toIosByteArray()
            }
            assertNotNull(read)
            assertEquals("held", read.decodeToString())
            assertEquals(1, releasedInsideBlock, "the scope must still be held inside the block")
            assertEquals(0, held, "the block's scope must be released afterwards")
        } finally {
            IosFolderBookScope.installDriver(null)
            NSFileManager.defaultManager.removeItemAtPath(root, error = null)
        }
    }

    @Test
    fun `plain paths bypass scope and read direct`() {
        val root = NSTemporaryDirectory() + "scope-plain-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null)
        val file = root + "/Plain.epub"
        assertTrue(writeProbe(file, "plain"))
        // No driver installed: plain paths must still work (app-owned files).
        val out = withResolvedIosBookPath(file) { it }
        assertEquals(file, out)
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    @Test
    fun `nested acquires balance with matching releases`() {
        var acquires = 0
        var releases = 0
        IosFolderBookScope.installDriver(object : IosFolderScopeDriver {
            override fun acquire(folderName: String): String? {
                acquires++
                return "/tmp/$folderName"
            }
            override fun release(folderName: String) {
                releases++
            }
        })
        try {
            val outer = withIosFolderScope("Books") { root ->
                withIosFolderScope("Books") { inner -> "$root|$inner" }
            }
            assertEquals("/tmp/Books|/tmp/Books", outer)
            assertEquals(2, acquires)
            assertEquals(2, releases)
        } finally {
            IosFolderBookScope.installDriver(null)
        }
    }

    private fun writeProbe(path: String, text: String): Boolean {
        val bytes = text.encodeToByteArray()
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
