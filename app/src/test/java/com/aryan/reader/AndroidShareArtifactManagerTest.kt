package com.aryan.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aryan.reader.shared.AndroidShareArtifactManager
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AndroidShareArtifactManagerTest {
    private lateinit var context: Context
    private lateinit var shareRoot: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shareRoot = File(context.cacheDir, AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY)
    }

    @After
    fun tearDown() {
        shareRoot.deleteRecursively()
    }

    @Test
    fun `request names and file names stay bounded`() {
        assertEquals("share-request-1", AndroidShareArtifactManager.requestDirectoryName("request-1"))
        assertEquals(".._book_name.pdf", AndroidShareArtifactManager.sanitizeFileName("../book/name.pdf"))
        assertEquals("shared-file", AndroidShareArtifactManager.sanitizeFileName("  "))
        assertFalse(AndroidShareArtifactManager.sanitizeFileName("book/name.pdf").contains('/'))
        assertThrows(IllegalArgumentException::class.java) {
            AndroidShareArtifactManager.requestDirectoryName("../escape")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidShareArtifactManager.requestDirectoryName(" ")
        }
        assertNotEquals(
            AndroidShareArtifactManager.requestDirectoryName(UUID.randomUUID().toString()),
            AndroidShareArtifactManager.requestDirectoryName(UUID.randomUUID().toString()),
        )
    }

    @Test
    fun `failed write removes only the new request directory`() {
        val existing = File(shareRoot, "share-existing").apply {
            mkdirs()
            File(this, "existing.pdf").writeText("keep")
        }

        assertThrows(IllegalStateException::class.java) {
            AndroidShareArtifactManager.create(
                context = context,
                requestedFileName = "failed.pdf",
                write = { error("write failed") },
            )
        }

        assertTrue(existing.isDirectory)
        assertTrue(File(existing, "existing.pdf").isFile)
        assertEquals(listOf(existing), shareRoot.listFiles()?.toList())
    }

    @Test
    fun `share intent carries matching stream clip data and read grant`() {
        val uri = Uri.parse("content://com.aryan.reader.provider/shared_files/share-id/book.pdf")
        val artifact = AndroidShareArtifactManager.Artifact("id", "book.pdf", uri)

        val intent = AndroidShareArtifactManager.buildShareIntent(
            artifact = artifact,
            mimeType = "application/pdf",
            title = "Book",
            subject = "Share Book",
        )

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `sweep removes expired artifacts but retains fresh and active chooser files`() {
        val now = 100_000L
        val stale = File(shareRoot, "share-stale").apply { mkdirs(); setLastModified(1_000L) }
        val fresh = File(shareRoot, "share-fresh").apply { mkdirs(); setLastModified(95_000L) }
        val active = File(shareRoot, "share-active").apply { mkdirs(); setLastModified(1_000L) }
        val legacy = File(shareRoot, "legacy.pdf").apply { writeText("legacy"); setLastModified(1_000L) }

        val deleted = AndroidShareArtifactManager.sweep(
            context = context,
            nowMillis = now,
            ttlMillis = 10_000L,
            activeRequestIds = setOf("active"),
        )

        assertEquals(2, deleted)
        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertTrue(active.exists())
        assertFalse(legacy.exists())
    }
}
