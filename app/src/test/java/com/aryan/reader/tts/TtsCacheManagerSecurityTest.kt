package com.aryan.reader.tts

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TtsCacheManagerSecurityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `book cache directory for traversal title remains inside tts cache root`() {
        val manager = TtsCacheManager(context)
        val root = File(context.filesDir, "TTS_Cache").canonicalFile
        val cacheDir = manager.getBookCacheDir("..").canonicalFile

        assertTrue(cacheDir.path.startsWith(root.path + File.separator))
    }

    @Test
    fun `clearBookCache with traversal title does not delete app files directory`() {
        val sentinel = File(context.filesDir, "tts-sentinel-${System.nanoTime()}.txt")
        sentinel.writeText("keep")

        TtsCacheManager(context).clearBookCache("..")

        assertTrue(sentinel.exists())
        sentinel.delete()
    }
}
