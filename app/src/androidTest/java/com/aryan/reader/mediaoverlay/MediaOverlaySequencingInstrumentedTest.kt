package com.aryan.reader.mediaoverlay

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The Android design decision, on a device: **ExoPlayer is the SMIL sequencer**.
 *
 * The alternative is one `MediaItem` per audio file plus a position poll that works out which `par`
 * you are inside, and the claim being tested here is that handing the boundaries to the player is
 * better. That claim is only worth anything if it holds up through the framework — the clip has to
 * actually be clipped, a clip starting mid-file has to start mid-file, `onMediaItemTransition` has to
 * fire in clip order, and a clip's reported duration has to be the *clip's*, because that duration is
 * what the reader's scrubber shows.
 *
 * Instrumented for the same reason as the data-source test: `DataSpec` carries an `android.net.Uri`,
 * which JVM tests stub to throw. The audio is generated here (a small non-silent PCM WAV) so no binary
 * fixture has to live in the repo.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaOverlaySequencingInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var archiveFile: File
    private lateinit var holder: SharedMediaOverlayArchiveHolder
    private var player: ExoPlayer? = null

    /** Long enough to be clipped into several segments; see [wavBytes]. */
    private val audioDurationMs = 1_200
    private val audioEntry = "OEBPS/Audio/chapter1.wav"

    @Before
    fun setUp() {
        archiveFile = File.createTempFile("mo-seq", ".epub")
        ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
            zip.putNextEntry(ZipEntry(audioEntry))
            zip.write(mediaOverlayTestWavBytes(audioDurationMs))
            zip.closeEntry()
        }
        holder = SharedMediaOverlayArchiveHolder()
        assertNull("the archive should attach cleanly", holder.attach(archiveFile))
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { player?.release() }
        player = null
        holder.close()
        archiveFile.delete()
    }

    /**
     * A `par`'s playable length is the clip's, not the file's.
     *
     * Every clip in a chapter points at the same audio file, so a player that ignored the clipping
     * configuration would report the whole file and play straight past the end of the paragraph.
     */
    @Test
    fun aClipPlaysAndReportsOnlyItsOwnSpan() {
        val reachedEnd = CountDownLatch(1)
        playMain(0, clip(0, 300), clip(300, 600)) { index ->
            if (index == 1) reachedEnd.countDown()
        }
        assertTrue("the second clip never started", reachedEnd.await(10, TimeUnit.SECONDS))

        val clippedDuration = awaitClipDurationMs()
        // 300 ms of PCM at a fixed sample rate, so the tolerance only absorbs container rounding.
        assertTrue(
            "a clip must report its own length, not the file's (was $clippedDuration ms)",
            clippedDuration in 280..320
        )
    }

    /**
     * All four clips of a chapter, in order, over one audio file.
     *
     * The transition sequence *is* the active-fragment signal the reader paints from, so a clip that
     * is skipped or replayed would show as the wrong paragraph highlighted.
     */
    @Test
    fun everyClipTransitionsInOrderOverOneFile() {
        val transitions = Collections.synchronizedList(mutableListOf<Int>())
        val reachedLast = CountDownLatch(1)
        val clips = listOf(clip(0, 150), clip(150, 300), clip(300, 450), clip(450, 600))
        playMain(0, *clips.toTypedArray()) { index ->
            transitions += index
            if (index == clips.lastIndex) reachedLast.countDown()
        }
        assertTrue("the last clip never started: $transitions", reachedLast.await(15, TimeUnit.SECONDS))
        assertEquals((0..clips.lastIndex).toList(), transitions.toList())
    }

    /** Starting mid-list, which is what a `par` seek is: `seekTo(index, clipBegin)`. */
    @Test
    fun playbackCanStartAtALaterClip() {
        val reachedFirst = CountDownLatch(1)
        val clips = listOf(clip(0, 150), clip(150, 300), clip(300, 450))
        playMain(2, *clips.toTypedArray()) { index ->
            if (index == 2) reachedFirst.countDown()
        }
        assertTrue("playback did not start at the requested clip", reachedFirst.await(10, TimeUnit.SECONDS))
        assertEquals(2, instrumentation.runOnMainSyncWithResult { player!!.currentMediaItemIndex })
    }

    /**
     * media3 has no "silent item", and it refuses this one *inside* `setMediaItems`.
     *
     * A `MediaItem` with no URI — the shape a TTS-shaped `par` would take — fails on a null local
     * configuration in `DefaultMediaSourceFactory`. Because the throw happens while the whole playlist
     * is being handed over, one narration-less clip would cost the entire chapter rather than itself.
     * That is why `sharedMediaOverlayPlaybackPlan` drops those clips and why the engine builds items
     * only from clips that have an audio path. This case pins the media3 behaviour the decision rests
     * on: if a future media3 accepts a URI-less item, the engine's filter can be simplified.
     */
    @Test
    fun media3RefusesAMediaItemWithNoUri() {
        val silent = MediaItem.Builder().setMediaId("silent").build()
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val built = ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setDataSourceFactory(holder.dataSourceFactory(context))
                )
                .build()
            player = built
            failure = runCatching { built.setMediaItems(listOf(silent, clip(0, 300)), 0, 0L) }
                .exceptionOrNull()
        }
        assertTrue(
            "media3 accepted a URI-less item, so the engine's filter has become unnecessary",
            failure != null
        )
    }

    /**
     * The player's reported duration for the current clip, once it has one.
     *
     * Polled because a transition can be delivered a beat before the timeline carries the new period's
     * duration, and reading that beat early yields `C.TIME_UNSET` rather than a number.
     */
    private fun awaitClipDurationMs(): Long {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val duration = instrumentation.runOnMainSyncWithResult { player?.duration ?: -1L }
            if (duration >= 0) return duration
            Thread.sleep(50)
        }
        return -1L
    }

    private fun clip(beginMs: Long, endMs: Long): MediaItem = MediaItem.Builder()
        .setMediaId("$beginMs-$endMs")
        .setUri(MediaOverlayUri.uriFor(audioEntry))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(beginMs)
                .setEndPositionMs(endMs)
                .build()
        )
        .build()

    private fun playMain(
        startIndex: Int,
        vararg items: MediaItem,
        onTransition: (Int) -> Unit
    ) {
        instrumentation.runOnMainSync {
            val built = ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setDataSourceFactory(holder.dataSourceFactory(context))
                )
                .build()
            player = built
            built.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                    onTransition(built.currentMediaItemIndex)
                }
            })
            built.setMediaItems(items.toList(), startIndex, 0L)
            built.prepare()
            built.play()
        }
    }

}

/** `runOnMainSync` is a `Runnable` API; the player must be read and written on its own looper. */
private fun <T> android.app.Instrumentation.runOnMainSyncWithResult(block: () -> T): T {
    var result: T? = null
    runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
