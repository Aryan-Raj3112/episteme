package com.aryan.reader.tts

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Every framework `TextToSpeech` call is a synchronous binder transaction executed on the calling
 * thread (`TextToSpeech.runAction` -> `Connection.runAction`), so `stop()`/`speak()` on the main thread
 * block the UI until the engine answers - an ANR when the engine is busy or hung
 * (docs/crashlytics-triage.md#213). These pin the dispatch contract, not the audio: the engine is a
 * recording fake, so an assertion fails only if engine work runs on the caller's thread or if listener
 * callbacks stop arriving on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectLocalTtsPlayerEngineThreadTest {

    private class RecordingEngine(
        context: Context,
        private val onCall: (String) -> Unit,
    ) : TextToSpeech(context, null) {
        override fun stop(): Int {
            onCall("stop")
            return TextToSpeech.SUCCESS
        }

        override fun speak(
            text: CharSequence?,
            queueMode: Int,
            params: Bundle?,
            utteranceId: String?,
        ): Int {
            onCall("speak")
            return TextToSpeech.SUCCESS
        }
    }

    private class RecordingListener(
        private val onReadyCalled: () -> Unit = {},
    ) : DirectLocalTtsListener {
        override fun onReady(generation: Int, chunkIndex: Int) = onReadyCalled()
        override fun onStart(utterance: LocalTtsUtterance) = Unit
        override fun onRangeStart(utterance: LocalTtsUtterance, start: Int, end: Int) = Unit
        override fun onDone(utterance: LocalTtsUtterance) = Unit
        override fun onError(utterance: LocalTtsUtterance?, errorCode: Int) = Unit
    }

    /** Runs engine work on whichever thread submits it; used to place the test's caller deliberately. */
    private class InlineExecutorService : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    @Test
    fun `engine calls run on the engine thread instead of the calling thread`() {
        val callerThread = Thread.currentThread().name
        val engineThreads = Collections.synchronizedList(mutableListOf<String>())
        val engine = RecordingEngine(RuntimeEnvironment.getApplication()) {
            engineThreads += Thread.currentThread().name
        }
        val executor = Executors.newSingleThreadExecutor()
        val player = DirectLocalTtsPlayer(
            context = RuntimeEnvironment.getApplication(),
            listener = RecordingListener(),
            engineExecutor = executor,
            engineFactory = { _, _ -> engine },
        )

        player.speak("chapter text", "episteme-local:1:0:0", 1f, 1f, TextToSpeech.QUEUE_FLUSH)
        player.stop()
        executor.shutdown()
        assertTrue("engine work did not finish", executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, engineThreads.size)
        assertNotEquals("engine work must not run on the caller thread", callerThread, engineThreads.single())
    }

    @Test
    fun `readiness is delivered on the main thread even when the engine reports elsewhere`() {
        val readyThreads = Collections.synchronizedList(mutableListOf<String>())
        val engine = RecordingEngine(RuntimeEnvironment.getApplication()) { }
        val player = DirectLocalTtsPlayer(
            context = RuntimeEnvironment.getApplication(),
            listener = RecordingListener { readyThreads += Thread.currentThread().name },
            engineExecutor = InlineExecutorService(),
            // Reports success while constructing, i.e. from the engine side, not from main.
            engineFactory = { _, onInit ->
                onInit(TextToSpeech.SUCCESS)
                engine
            },
        )

        val engineWorker = Thread {
            player.speak("chapter text", "episteme-local:1:0:0", 1f, 1f, TextToSpeech.QUEUE_FLUSH)
        }.apply { start() }
        engineWorker.join(5_000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "readiness must reach the playback manager on the main thread",
            listOf(Looper.getMainLooper().thread.name),
            readyThreads.toList(),
        )
    }
}
