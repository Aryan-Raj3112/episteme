package com.aryan.reader

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.AndroidShareArtifactManager
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidFileProviderSafetyTest {
    private lateinit var context: Context
    private lateinit var shareRoot: File
    private lateinit var unrelatedCacheFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shareRoot = File(context.cacheDir, AndroidShareArtifactManager.SHARE_ROOT_DIRECTORY)
        unrelatedCacheFile = File(context.cacheDir, "unrelated-cache-file.pdf").apply {
            writeText("not shareable")
        }
    }

    @After
    fun tearDown() {
        unrelatedCacheFile.delete()
        shareRoot.deleteRecursively()
    }

    @Test
    fun createsIsolatedSanitizedArtifacts() {
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
        assertTrue(first.fileName.indexOf('/') < 0)
        assertTrue(second.fileName.indexOf('/') < 0)
        assertTrue(first.uri.toString().contains("/shared_files/share-${first.requestId}/"))
        assertTrue(second.uri.toString().contains("/shared_files/share-${second.requestId}/"))
        assertEquals(
            byteArrayOf(1, 2, 3).toList(),
            File(shareRoot, "share-${first.requestId}/${first.fileName}").readBytes().toList(),
        )
        assertEquals(
            byteArrayOf(4, 5, 6).toList(),
            File(shareRoot, "share-${second.requestId}/${second.fileName}").readBytes().toList(),
        )
    }

    @Test
    fun providerRejectsFilesOutsideDedicatedShareRoot() {
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                unrelatedCacheFile,
            )
        }
    }
}
