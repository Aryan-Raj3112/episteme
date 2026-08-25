package com.aryan.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.pm.ProviderInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
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
import org.robolectric.Shadows
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
        val providerPathsId = context.resources.getIdentifier(
            "provider_paths",
            "xml",
            context.packageName,
        )
        check(providerPathsId != 0) { "Robolectric did not load provider_paths" }
        Shadows.shadowOf(context.packageManager).addOrUpdateProvider(
            ProviderInfo().apply {
                packageName = context.packageName
                authority = "${context.packageName}.provider"
                name = FileProvider::class.java.name
                applicationInfo = ApplicationInfo().apply { packageName = context.packageName }
                metaData = Bundle().apply {
                    putInt("android.support.FILE_PROVIDER_PATHS", providerPathsId)
                }
            }
        )
        check(
            context.packageManager.resolveContentProvider(
                "${context.packageName}.provider",
                PackageManager.GET_META_DATA,
            )?.metaData?.getInt("android.support.FILE_PROVIDER_PATHS") == providerPathsId,
        )
    }

    @After
    fun tearDown() {
        shareRoot.deleteRecursively()
    }

    @Test
    fun `each artifact gets an isolated sanitized file and provider uri`() {
        val first = AndroidShareArtifactManager.create(
            context = context,
            requestedFileName = "../book/name.pdf",
            write = { it.write(byteArrayOf(1, 2, 3)) },
        )
        val second = AndroidShareArtifactManager.create(
            context = context,
            requestedFileName = "book/name.pdf",
            write = { it.write(byteArrayOf(4, 5, 6)) },
        )

        assertNotEquals(first.requestId, second.requestId)
        assertFalse(first.fileName.contains('/'))
        assertFalse(second.fileName.contains('/'))
        assertTrue(first.uri.toString().contains("/shared_files/share-${first.requestId}/"))
        assertTrue(second.uri.toString().contains("/shared_files/share-${second.requestId}/"))
        assertTrue(File(shareRoot, "share-${first.requestId}/${first.fileName}").isFile)
        assertTrue(File(shareRoot, "share-${second.requestId}/${second.fileName}").isFile)
    }

    @Test
    fun `request names and file names stay bounded`() {
        assertEquals("share-request-1", AndroidShareArtifactManager.requestDirectoryName("request-1"))
        assertEquals(".._book_name.pdf", AndroidShareArtifactManager.sanitizeFileName("../book/name.pdf"))
        assertEquals("shared-file", AndroidShareArtifactManager.sanitizeFileName("  "))
        assertFalse(AndroidShareArtifactManager.sanitizeFileName("book/name.pdf").contains('/'))
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
    fun `provider rejects files outside the dedicated share root`() {
        val outside = File(context.cacheDir, "unrelated-cache-file.pdf").apply {
            writeText("not shareable")
        }

        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                outside,
            )
        }
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
