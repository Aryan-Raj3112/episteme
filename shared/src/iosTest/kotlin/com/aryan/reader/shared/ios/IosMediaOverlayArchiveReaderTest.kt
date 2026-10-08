@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.reader.SharedMediaOverlayArchiveReader
import com.aryan.reader.shared.reader.SharedMediaOverlayAudioUri
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import com.aryan.reader.shared.reader.SharedMediaOverlayIndex
import com.aryan.reader.shared.reader.parseSharedMediaOverlayXml
import com.aryan.reader.shared.reader.sharedMediaOverlayPlaybackPlan
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * iOS archive access for media overlays: the SMIL reader and the byte ranges the player streams from.
 *
 * This is the iOS half of what `SharedJvmEpubArchiveReaderTest` proves on Android, and it is worth
 * proving separately because the iOS implementation has no second archive to fall back on. Android
 * opens a `ZipFile` over the path, so a mistake there costs a file descriptor; iOS *borrows* the
 * archive the loader already has open, so a mistake costs the whole feature — there is nothing to
 * open, and a book that fails here simply has no narration.
 *
 * The book is built here rather than read from `app/src/androidTest/assets`, which is the shape every
 * other iOS test uses and the only one that works: the test binary runs in a simulator sandbox with no
 * access to the repository, so a fixture copied from a checkout is a test that fails on a machine where
 * the checkout is absent. It mirrors the Android fixture's narration — three clips, then two, then an
 * un-narrated chapter — because those differing clip counts are what a continuation test asserts on.
 */
class IosMediaOverlayArchiveReaderTest {

    private val bookId = "ios-media-overlay-fixture"
    private var directory: String? = null
    private var fixturePath: String? = null

    @AfterTest
    fun tearDown() {
        IosEpubResourceStore.clear()
        directory?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        directory = null
        fixturePath = null
    }

    /**
     * Writes the narrated fixture and registers it exactly as the loader does.
     *
     * Registration is the whole mechanism under test, so it is not optional: a reader that reached the
     * archive by some other route would prove nothing about the one that borrows it.
     */
    private fun installFixture(): String {
        fixturePath?.let { return it }
        val root = "${NSTemporaryDirectory().trimEnd('/')}/ios-media-overlay-$bookId"
        NSFileManager.defaultManager.removeItemAtPath(root, null)
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = root,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        directory = root
        val path = "$root/narrated.epub"
        writeIosZipArchive(path, NarrationFixture.entries.keys.toList(), NarrationFixture.entries::get)
        fixturePath = path
        IosEpubResourceStore.register(bookId, path)
        return path
    }

    // --- the borrow ---------------------------------------------------------------------------

    /**
     * The archive is borrowed, never reopened.
     *
     * This is the property that makes iOS narration affordable at all: an `IosZipEpubArchive` holds
     * its whole zip as a `ByteArray`, so a second one for a book the reader already has open would
     * double resident memory on a large archive for as long as the reader is open. Asserting the
     * *identity* rather than the behaviour is the point — a reader that worked by reopening would
     * still be a regression.
     */
    @Test
    fun `the archive registered for a book is the one narration reads from`() {
        val path = installFixture()
        val registered = assertNotNull(IosEpubResourceStore.registeredArchive(bookId))

        val borrowed = assertNotNull(
            IosEpubResourceStore.registeredArchiveAtPath(path),
            "Narration must find the archive by the path the reader screen resolved"
        )

        assertTrue(borrowed === registered, "Narration opened a second archive instead of borrowing")
    }

    /**
     * A path with no registered archive yields nothing, and that is the answer rather than a failure:
     * `RS §9` requires a reader that cannot reach the overlays to open the book normally.
     */
    @Test
    fun `an unregistered path yields no archive rather than opening one`() {
        assertNull(IosEpubResourceStore.registeredArchiveAtPath("/nowhere/" + bookId + ".epub"))
        assertNull(SharedMediaOverlayArchiveReader.at("/nowhere/" + bookId + ".epub"))
    }

    // --- the SMIL reader ----------------------------------------------------------------------

    /**
     * The SMIL reader through the reader the composable seam builds.
     *
     * `rememberSharedMediaOverlaySmilReader` is a composable and cannot be called from a test, so this
     * asserts on the reader underneath it — which is where the borrowing and the entry lookup actually
     * live, and where a mistake would cost the whole feature.
     */
    /**
     * The archive reader the composable seam builds, or a failure.
     *
     * `rememberSharedMediaOverlaySmilReader` is a composable and cannot be called from a test, so this
     * asserts on the reader underneath it — which is where the borrowing and the entry lookup actually
     * live, and where a mistake would cost the whole feature.
     */
    private fun installArchiveReader(): SharedMediaOverlayArchiveReader = assertNotNull(
        SharedMediaOverlayArchiveReader.at(installFixture()),
        "iOS could not reach the archive for a book it has open"
    )

    /**
     * A reader that insists the entry exists, for the tests that expect to find one.
     *
     * Decoding happens here rather than through `readTextOrNull` so a missing entry and an undecodable
     * one are different failures — `readTextOrNull` collapses both to null, and this helper's job is
     * to prove the entry was found.
     */
    private fun installSmilReader(): (String) -> String {
        val reader = installArchiveReader()
        return { entryPath ->
            val bytes = assertNotNull(
                reader.readEntryRange(entryPath, 0, 1 shl 20),
                "The archive has no entry at $entryPath"
            )
            bytes.decodeToString()
        }
    }

    /** The seam's real reader, which answers null for an entry the archive does not hold. */
    private fun smilReaderOrNull(): (String) -> String? {
        val reader = installArchiveReader()
        return { entryPath -> reader.readTextOrNull(entryPath) }
    }

    /**
     * The fixture's own SMIL is well-formed XML that the shared parser accepts.
     *
     * A fixture with an undeclared namespace prefix on `epub:textref` fails the XML parse outright,
     * and `parseSharedMediaOverlayXml` reports that as `null` — the same answer it gives for a chapter
     * the book does not narrate. So "the reader found no SMIL" and "the fixture's SMIL is broken" are
     * one symptom, and only this assertion tells them apart. It exists because that is exactly the
     * mistake this fixture was first written with.
     */
    @Test
    fun `the fixture's smil is well formed xml the shared parser accepts`() {
        val reader = installSmilReader()
        val body = assertNotNull(reader("OEBPS/mo/chapter-01.smil"))
        assertTrue(
            body.contains("""xmlns:epub="http://www.idpf.org/2007/ops""""),
            "An undeclared epub namespace makes the parse fail and reads as 'no narration': " +
                body.take(200)
        )
        assertNotNull(
            parseSharedMediaOverlayXml(body, "OEBPS/mo/chapter-01.smil", 0),
            "The fixture's own SMIL did not parse, so it is a broken fixture rather than a bug"
        )
    }

    /**
     * A narrated chapter's SMIL body parses into playable clips.
     *
     * The three clips in one chapter and two in the next are the fixture's whole point — a
     * continuation test asserts the bar's position label changes from "1 / 3" to "1 / 2" — so this
     * pins both counts rather than only the first chapter's.
     */
    @Test
    fun `narrated chapters parse into the clip counts the fixture was built with`() {
        val reader = installSmilReader()

        val first = assertNotNull(
            parseSharedMediaOverlayXml(reader("OEBPS/mo/chapter-01.smil")!!, "OEBPS/mo/chapter-01.smil", 0),
            "The fixture's first narrated chapter has no readable SMIL body"
        )
        val second = assertNotNull(
            parseSharedMediaOverlayXml(reader("OEBPS/mo/chapter-02.smil")!!, "OEBPS/mo/chapter-02.smil", 1),
            "The fixture's second narrated chapter has no readable SMIL body"
        )

        val firstPlan = sharedMediaOverlayPlaybackPlan(
            first,
            mediaDurationMsByPath = mapOf(FIRST_AUDIO to FIRST_DURATION_MS)
        )
        val secondPlan = sharedMediaOverlayPlaybackPlan(
            second,
            mediaDurationMsByPath = mapOf(SECOND_AUDIO to SECOND_DURATION_MS)
        )

        assertContentEquals(listOf(0, 1, 2), firstPlan.sourceClipIndices)
        assertContentEquals(listOf(0, 1), secondPlan.sourceClipIndices)
        assertTrue(
            firstPlan.entries.all { it.audioPath == FIRST_AUDIO },
            "Every clip must resolve to its own chapter's one recording"
        )
        assertTrue(
            secondPlan.entries.all { it.audioPath == SECOND_AUDIO },
            "Chapters must not resolve to each other's audio"
        )
        // A clip with no declared end runs to the end of the file, and the plan can only know that
        // from the media duration the loader supplies — so this also proves the duration is reaching
        // the plan rather than leaving the last clip unbounded.
        assertEquals(4500L, firstPlan.totalDurationMs)
        assertEquals(3000L, secondPlan.totalDurationMs)
    }

    /**
     * The lazy cache over the same reader finds its own SMIL.
     *
     * "The SMIL parsed" and "the reader found it" are different failures: a cache keyed on the wrong
     * path reports no overlay at all, which the reader surfaces as a book that does not narrate itself.
     */
    @Test
    fun `the document cache finds a chapter through the borrowed reader`() {
        val reader = installSmilReader()
        val cache = SharedMediaOverlayDocumentCache(
            SharedMediaOverlayIndex(
                smilPathBySpineItem = mapOf(0 to "OEBPS/mo/chapter-01.smil"),
                contentPathBySpineItem = mapOf(0 to "OEBPS/chapters/chapter-01.xhtml"),
                smilIdBySpineItem = mapOf(0 to "smil-01"),
                totalDurationMs = null,
                narrator = "Fixture Narrator",
                activeClass = null,
                playbackActiveClass = null,
                declaredDurationMsBySpineItem = emptyMap()
            ),
            reader
        )

        assertNotNull(cache.document(0))
        assertEquals(1, cache.parseCount, "The cache must parse once and reuse, not per lookup")
    }

    /**
     * An un-narrated entry reads as absent rather than as an error, including one that tries to climb
     * out of the book.
     */
    @Test
    fun `an un-narrated entry reads as absent`() {
        val reader = smilReaderOrNull()

        assertNull(reader("OEBPS/mo/chapter-03.smil"))
        assertNull(reader("OEBPS/mo/chapter-does-not-exist.smil"))
        assertNull(reader("../../../etc/passwd"))
    }

    // --- the audio byte ranges ----------------------------------------------------------------

    /**
     * The recording is served straight out of the archive, and in the coordinates a clip asks for.
     *
     * A clip's `clipBegin` is an offset into *decoded* audio, not into the zip, so the range API is
     * deliberately uncompressed. Getting that wrong produces audio starting at the wrong place under a
     * highlight that names the right line, which is the exact bug this feature exists to avoid.
     */
    @Test
    fun `a byte range of the recording is served in uncompressed coordinates`() {
        installFixture()
        val archive = assertNotNull(IosEpubResourceStore.registeredArchive(bookId))

        val whole = assertNotNull(
            archive.readEntryRange(FIRST_AUDIO, 0, Long.MAX_VALUE),
            "The fixture's narration recording could not be read"
        )
        assertEquals(44 + FIRST_DURATION_MS.toInt(), whole.size, "44 bytes of RIFF header plus the data chunk")

        // A window past the first clip's end, of the same length as one from the start. The fixture's
        // audio is a ramp rather than silence precisely so this can tell the two apart — silence
        // would make every offset look like every other and the assertion would pass for free.
        val opening = assertNotNull(archive.readEntryRange(FIRST_AUDIO, 0, 1_200))
        val later = assertNotNull(archive.readEntryRange(FIRST_AUDIO, 1_600, 1_200))
        assertEquals(1_200, later.size)
        assertNotEquals(
            opening.toList(),
            later.toList(),
            "A seek returned the file's opening bytes, so the range was not offset at all"
        )
    }

    /**
     * The length the resource loader seeks against is the *uncompressed* size, and it is the number
     * that decides whether AVPlayer believes the file can be seeked.
     */
    @Test
    fun `the declared length is the uncompressed size`() {
        installFixture()
        val archive = assertNotNull(IosEpubResourceStore.registeredArchive(bookId))

        assertEquals(44L + FIRST_DURATION_MS, archive.entryLengthOrNull(FIRST_AUDIO))
    }

    /**
     * A window past the end of the entry comes back short, and one starting at the end comes back
     * empty.
     *
     * A media framework routinely asks for a window the file does not have. Treating that as an error
     * fails a read that had already been told how much data to expect, which surfaces as a playback
     * error on a book that merely has no audio there.
     */
    @Test
    fun `a window past the end of an entry is clamped rather than refused`() {
        installFixture()
        val archive = assertNotNull(IosEpubResourceStore.registeredArchive(bookId))
        val length = assertNotNull(archive.entryLengthOrNull(FIRST_AUDIO))

        val overrun = assertNotNull(archive.readEntryRange(FIRST_AUDIO, length - 10, 4_096))
        assertEquals(10, overrun.size)
        assertContentEquals(assertNotNull(archive.readEntryRange(FIRST_AUDIO, length - 10, 10)), overrun)

        assertEquals(0, assertNotNull(archive.readEntryRange(FIRST_AUDIO, length, 10)).size)
        assertNull(archive.readEntryRange(FIRST_AUDIO, -1, 10))
        assertNull(archive.readEntryRange(FIRST_AUDIO, 0, 0))
    }

    /**
     * A missing entry is null, which the resource loader reports as a missing file. Not an empty
     * array: an empty array is how a loader is told "this is the end of the resource", i.e. a
     * successful read of nothing.
     */
    @Test
    fun `a missing entry yields null at every window`() {
        installFixture()
        val archive = assertNotNull(IosEpubResourceStore.registeredArchive(bookId))

        assertNull(archive.readEntryRange("OEBPS/audio/absent.mp3", 0, 1_024))
        assertNull(archive.entryLengthOrNull("OEBPS/audio/absent.mp3"))
    }

    // --- the addressing scheme ----------------------------------------------------------------

    /**
     * The URI a range is requested through round-trips to the same entry.
     *
     * The scheme is defined in shared code so both platforms address an overlay's audio identically,
     * and this is the half of that contract iOS can prove alone: the percent-encoding is what keeps a
     * nested path opaque, and a decoding mismatch would silently stream the wrong entry.
     */
    @Test
    fun `the audio uri round trips through the shared scheme`() {
        for (entryPath in listOf(
            FIRST_AUDIO,
            "OEBPS/Audio/Deeply/Nested Name — chapter 1.mp3",
            "OEBPS/audio/日本語.mp3"
        )) {
            val uri = SharedMediaOverlayAudioUri.uriFor(entryPath)
            assertEquals(entryPath, SharedMediaOverlayAudioUri.entryPathOf(uri), "uri did not round trip")
        }

        // And the URI is one the loader would actually be asked about: a `file:` URI must not be
        // mistaken for an archive entry, or an in-spec external `src` would read the wrong file.
        assertNull(SharedMediaOverlayAudioUri.entryPathOf("file:///tmp/a.mp3"))
    }
}

/**
 * The narrated fixture, built entry by entry.
 *
 * Shaped after `app/src/androidTest/fixtures/epub` so the two platforms assert the same book: two
 * narrated chapters with *different* clip counts, and a third that is not narrated at all. That last
 * part is not filler — it is what lets a continuation test assert the run ends rather than inventing
 * narration for a chapter that was never recorded.
 */
private object NarrationFixture {
    private const val FIRST_AUDIO = "OEBPS/audio/chapter-01.wav"
    private const val SECOND_AUDIO = "OEBPS/audio/chapter-02.wav"
    private const val FIRST_DURATION_MS = 4_500L
    private const val SECOND_DURATION_MS = 3_000L

    val entries: Map<String, ByteArray> = linkedMapOf(
        "mimetype" to "application/epub+zip".encodeToByteArray(),
        "META-INF/container.xml" to """
            <container><rootfiles>
              <rootfile full-path="OEBPS/package.opf"/>
            </rootfiles></container>
        """.trimIndent().encodeToByteArray(),
        "OEBPS/package.opf" to """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:media="http://www.idpf.org/2007/ops">
                <dc:identifier id="book-id">urn:uuid:ios-media-overlay-fixture</dc:identifier>
                <dc:title>Narration Fixture</dc:title>
                <dc:language>en</dc:language>
                <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
                <meta property="media:duration">7.5s</meta>
                <meta property="media:narrator">Fixture Narrator</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c1" href="chapters/chapter-01.xhtml" media-type="application/xhtml+xml" media-overlay="smil-01"/>
                <item id="c2" href="chapters/chapter-02.xhtml" media-type="application/xhtml+xml" media-overlay="smil-02"/>
                <item id="c3" href="chapters/chapter-03.xhtml" media-type="application/xhtml+xml"/>
                <item id="smil-01" href="mo/chapter-01.smil" media-type="application/smil+xml"/>
                <item id="smil-02" href="mo/chapter-02.smil" media-type="application/smil+xml"/>
                <item id="a1" href="audio/chapter-01.wav" media-type="audio/mpeg"/>
                <item id="a2" href="audio/chapter-02.wav" media-type="audio/mpeg"/>
              </manifest>
              <spine>
                <itemref idref="nav"/>
                <itemref idref="c1"/>
                <itemref idref="c2"/>
                <itemref idref="c3"/>
              </spine>
            </package>
        """.trimIndent().encodeToByteArray(),
        "OEBPS/nav.xhtml" to chapter(
            title = "Contents",
            paragraphs = listOf("opening-paragraph" to "One", "position-target-alpha" to "Two")
        ),
        "OEBPS/chapters/chapter-01.xhtml" to chapter(
            title = "Chapter One",
            paragraphs = listOf(
                "opening-paragraph" to "The first narrated line.",
                "position-target-alpha" to "The second narrated line.",
                "highlight-target-bravo" to "The third narrated line."
            )
        ),
        "OEBPS/chapters/chapter-02.xhtml" to chapter(
            title = "Chapter Two",
            paragraphs = listOf(
                "search-target-delta" to "The fourth narrated line.",
                "bookmark-target-echo" to "The fifth narrated line."
            )
        ),
        "OEBPS/chapters/chapter-03.xhtml" to chapter(
            title = "Chapter Three",
            paragraphs = listOf("annotation-target-golf" to "This chapter was never recorded.")
        ),
        "OEBPS/mo/chapter-01.smil" to smil(
            // Relative to the SMIL, which sits in `mo/`. Not an already-resolved absolute path:
            // `parseSharedMediaOverlayXml` resolves every `src` against the SMIL's own location, so
            // `audio/...` would silently become `OEBPS/mo/audio/...` and no clip would find its
            // recording. That is what a real book writes, so the fixture does too.
            textHref = "../chapters/chapter-01.xhtml",
            audioSrc = "../audio/chapter-01.wav",
            pars = listOf(
                Triple("opening-paragraph", "0:00:00.000", "0:00:01.500"),
                Triple("position-target-alpha", "0:00:01.500", "0:00:03.000"),
                Triple("highlight-target-bravo", "0:00:03.000", "0:00:04.500")
            )
        ),
        "OEBPS/mo/chapter-02.smil" to smil(
            textHref = "../chapters/chapter-02.xhtml",
            audioSrc = "../audio/chapter-02.wav",
            pars = listOf(
                Triple("search-target-delta", "0:00:00.000", "0:00:01.500"),
                Triple("bookmark-target-echo", "0:00:01.500", "0:00:03.000")
            )
        ),
        FIRST_AUDIO to rampedWav(FIRST_DURATION_MS),
        SECOND_AUDIO to rampedWav(SECOND_DURATION_MS)
    )

    private fun chapter(title: String, paragraphs: List<Pair<String, String>>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
        append("""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>""")
        append(title)
        append("</title></head><body><h1>").append(title).append("</h1>")
        paragraphs.forEach { (id, text) -> append("""<p id=""""").append(id).append("""">""").append(text).append("</p>") }
        append("</body></html>")
    }.encodeToByteArray()

    private fun smil(textHref: String, audioSrc: String, pars: List<Triple<String, String, String>>): ByteArray =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""").append('\n')
            // Both namespaces declared. The parser is a strict XML parser, so an `epub:textref` with
            // no `xmlns:epub` is a *parse* failure that returns null for the whole document — which
            // reads exactly like "this book has no narration" from the reader.
            append(
                """<smil xmlns="http://www.w3.org/ns/SMIL" """ +
                    """xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">"""
            )
            append("""<body><seq id="s" epub:textref="""").append(textHref).append("\">")
            pars.forEach { (id, begin, end) ->
                append("""<par id="""").append(id).append("\">")
                append("""<text src="""").append(textHref).append("#").append(id).append("\"/>")
                append("""<audio src="""").append(audioSrc)
                append("\" clipBegin=\"").append(begin).append("\" clipEnd=\"").append(end).append("\"/>")
                append("</par>")
            }
            append("</seq></body></smil>")
        }.encodeToByteArray()

    /**
     * 8-bit mono PCM at 8 kHz whose data chunk counts up.
     *
     * A ramp rather than silence, and that is the entire reason it is a ramp: every other entry in
     * this fixture is text, so the audio is the only thing that can prove a byte *range* was honoured.
     * Silence would make a window at offset 1600 identical to one at offset 0, and the assertion that
     * matters would pass without ever testing anything.
     */
    private fun rampedWav(durationMs: Long): ByteArray {
        val dataSize = durationMs.toInt() // 8 kHz, 8-bit, mono: one byte per millisecond.
        val out = ByteArray(44 + dataSize)
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        writeLeInt(out, 4, 36 + dataSize)
        "WAVEfmt ".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        writeLeInt(out, 16, 16)        // PCM fmt chunk size
        out[20] = 1                    // PCM
        out[22] = 1                    // mono
        writeLeShort(out, 24, 8_000)   // sample rate
        writeLeInt(out, 28, 8_000)     // byte rate
        writeLeShort(out, 32, 1)       // block align
        writeLeShort(out, 34, 8)       // bits per sample
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        writeLeInt(out, 40, dataSize)
        for (i in 0 until dataSize) {
            out[44 + i] = (i % 251).toByte()
        }
        return out
    }

    private fun writeLeInt(target: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) target[offset + i] = ((value ushr (8 * i)) and 0xFF).toByte()
    }

    private fun writeLeShort(target: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 2) target[offset + i] = ((value ushr (8 * i)) and 0xFF).toByte()
    }
}

private const val FIRST_AUDIO = "OEBPS/audio/chapter-01.wav"
private const val SECOND_AUDIO = "OEBPS/audio/chapter-02.wav"
private const val FIRST_DURATION_MS = 4_500L
private const val SECOND_DURATION_MS = 3_000L