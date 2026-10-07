package com.aryan.reader.mediaoverlay

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackRequest
import com.aryan.reader.shared.reader.parseSharedMediaOverlayXml
import com.aryan.reader.shared.reader.sharedMediaOverlayPlaybackPlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The real Android narration path, from SMIL text to audible clip changes.
 *
 * Everything below the UI in one run: a SMIL body parses, `sharedMediaOverlayPlaybackPlan` decides
 * what is playable, `AndroidSharedMediaOverlayPlayback` attaches the book to
 * `MediaOverlayPlaybackService` and loads a clipped `MediaItem` per clip, and the media session plays
 * them in order. The claims that only a device can settle:
 *
 * - the archive really travels to the service as a path and can be opened there (the reader's UI and
 *   the player are in different components, which is the whole reason `ATTACH_BOOK_COMMAND` exists);
 * - the sequence advances on its own, which is what the highlight follows;
 * - a TTS-shaped `par` is dropped by the plan and does not stall what follows it.
 */
@RunWith(AndroidJUnit4::class)
class AndroidMediaOverlayPlaybackInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var epubFile: File
    private var engine: AndroidSharedMediaOverlayPlayback? = null

    private val audioEntry = "OEBPS/Audio/chapter1.wav"

    @Before
    fun setUp() {
        // The service posts a media notification while it plays; on API 33+ that is a runtime
        // permission, and being denied would fail the foreground transition rather than the test's
        // subject. Granted rather than assumed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                instrumentation.uiAutomation
                    .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        epubFile = File.createTempFile("mo-engine", ".epub")
        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            zip.putNextEntry(ZipEntry(audioEntry))
            zip.write(mediaOverlayTestWavBytes(durationMs = 1_200))
            zip.closeEntry()
        }
    }

    @After
    fun tearDown() {
        engine?.release()
        engine = null
        epubFile.delete()
    }

    /**
     * A clip after a narration-less `par` still gets narrated.
     *
     * The dropped clip is the interesting one: three `par`s, the middle one a TTS-shaped `par` with no
     * `audio`. Its source index has to vanish from the plan while the clip after it still plays — the
     * failure mode otherwise is a chapter that stops dead at the silent paragraph, or a highlight that
     * lands on the wrong element from then on.
     */
    @Test
    fun playbackAdvancesPastANarrationlessPar() {
        val document = parseSharedMediaOverlayXml(smilBody(), "OEBPS/mo/chapter1.smil", spineItemIndex = 0)
        assertNotNull("the fixture smil must parse", document)
        val plan = sharedMediaOverlayPlaybackPlan(document!!)

        assertEquals("the TTS-shaped par must be dropped", listOf(0, 2), plan.sourceClipIndices)

        val engine = startEngine()
        engine.play(request(plan.entries))

        assertTrue(
            "narration never reached the clip after the silent par (state=${engineState(engine)})",
            awaitClipIndex(engine, 1)
        )
        assertEquals(0, engineState(engine).spineItemIndex)
    }

    /**
     * A request that breaks the "already filtered" contract is skipped, not fatal.
     *
     * Handing media3 a clip with no audio path throws inside `setMediaItems`, which would take the
     * whole chapter down. The engine filters instead, and its index map is what keeps the stream after
     * the dropped clip pointing at its own text.
     */
    @Test
    fun anUnfilteredRequestSkipsTheUnplayableClipInsteadOfFailing() {
        val document = parseSharedMediaOverlayXml(smilBody(), "OEBPS/mo/chapter1.smil", spineItemIndex = 0)!!

        val engine = startEngine()
        engine.play(request(document.clips))

        // Player index 1 is the third par, i.e. clip index 2 in the unfiltered list.
        assertTrue(
            "the engine gave up on a clip it should have skipped (state=${engineState(engine)})",
            awaitClipIndex(engine, 2)
        )
        assertNull("playback must not carry an error here", engineState(engine).error)
    }

    /** Playback can be started at a clip in the middle, which is what a `par` seek is. */
    @Test
    fun playbackCanStartAtALaterClip() {
        val document = parseSharedMediaOverlayXml(smilBody(), "OEBPS/mo/chapter1.smil", spineItemIndex = 0)!!
        val plan = sharedMediaOverlayPlaybackPlan(document)

        val engine = startEngine()
        engine.play(request(plan.entries, startPlaybackIndex = 1))

        val started = awaitState(engine) { it.clipIndex == 1 && it.hasBook }
        assertTrue("playback did not start at the requested clip", started)
    }

    private fun startEngine(): AndroidSharedMediaOverlayPlayback {
        val created = AndroidSharedMediaOverlayPlayback(context)
        created.attachBook(epubFile)
        engine = created
        return created
    }

    private fun request(
        clips: List<com.aryan.reader.shared.reader.SharedMediaOverlayClip>,
        startPlaybackIndex: Int = 0
    ) = SharedMediaOverlayPlaybackRequest(
        sourceId = "media-overlay-engine-test",
        bookTitle = "Narrated Fixture",
        spineItemIndex = 0,
        clips = clips,
        startPlaybackIndex = startPlaybackIndex,
        playWhenReady = true,
        narrator = "Chris Hughes"
    )

    private fun engineState(engine: AndroidSharedMediaOverlayPlayback) = engine.state.value

    private fun awaitClipIndex(engine: AndroidSharedMediaOverlayPlayback, clipIndex: Int): Boolean =
        awaitState(engine) { it.clipIndex == clipIndex && it.hasBook }

    private fun awaitState(
        engine: AndroidSharedMediaOverlayPlayback,
        timeoutMs: Long = 20_000,
        predicate: (com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackState) -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(engineState(engine))) return true
            Thread.sleep(50)
        }
        return predicate(engineState(engine))
    }

    /** Three `par`s over one file, with the middle one carrying no `audio`: a TTS-shaped `par`. */
    private fun smilBody(): String = """
        <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
            <body>
                <seq epub:textref="../Text/chapter1.xhtml" epub:type="bodymatter chapter">
                    <par id="par1">
                        <text src="../Text/chapter1.xhtml#p1"/>
                        <audio clipBegin="0:00:00.000" clipEnd="0:00:00.300" src="../Audio/chapter1.wav"/>
                    </par>
                    <par id="par2">
                        <text src="../Text/chapter1.xhtml#p2"/>
                    </par>
                    <par id="par3">
                        <text src="../Text/chapter1.xhtml#p3"/>
                        <audio clipBegin="0:00:00.300" clipEnd="0:00:00.600" src="../Audio/chapter1.wav"/>
                    </par>
                </seq>
            </body>
        </smil>
    """.trimIndent()
}
