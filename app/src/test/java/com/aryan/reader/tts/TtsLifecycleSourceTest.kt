package com.aryan.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TtsLifecycleSourceTest {

    @Test
    fun `main view model does not eagerly connect the tts service`() {
        val source = sourceFile("com/aryan/reader/MainViewModel.kt").readText()

        assertTrue(source.contains("val ttsController by lazy { TtsController(appContext) }"))
        assertFalse(source.contains("TtsController(appContext).apply { connect() }"))
    }

    @Test
    fun `service initializes local engine on synthesis instead of creation`() {
        val source = sourceFile("com/aryan/reader/tts/TtsService.kt").readText()

        assertFalse(source.contains("service-local-engine-warmup-start"))
        assertTrue(source.contains("baseTtsSynthesizer.synthesizeToFile(chunkToSpeak)"))
    }

    @Test
    fun `task removal stops playback engine and service`() {
        val source = sourceFile("com/aryan/reader/tts/TtsService.kt").readText()
        val taskRemovalBody = source.substringAfter("override fun onTaskRemoved")
            .substringBefore("override fun onGetSession")

        assertTrue(taskRemovalBody.contains("playbackManager.stopForAppTaskRemoval()"))
        assertTrue(taskRemovalBody.contains("shutdownLocalTtsEngine(\"task-removed\")"))
        assertTrue(taskRemovalBody.contains("stopSelf()"))
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
