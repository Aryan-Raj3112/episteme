package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EPUB media overlay SMIL bodies and the package-level index.
 *
 * The SMIL fixtures below are copied from real books, because the shapes that break a parser are the
 * ones a hand-written example leaves out:
 *
 * - `a00e164a.epub` (ReadBeyond, Shakespeare) — line-level `par`s, one mp3 per chapter, contiguous
 *   clips, `seq` per chapter with `epub:type="bodymatter chapter"`.
 * - `moby-mo.epub` (DAISY) — paragraph-level `par`s, **one mp4 spanning two chapters**, attributes
 *   split across lines.
 * - `SmokeTestFXL.epub` (Readium) — `par`s directly under `<body>` with no `seq` at all, plus
 *   `pagebreak` typing for skippability.
 */
class SharedMediaOverlayTest {

    // -------------------------------------------------------------------------------------------
    // Package index
    // -------------------------------------------------------------------------------------------

    private val manifest = mapOf(
        "cover" to MobileEpubManifestItem("cover", "OEBPS/cover.xhtml", "application/xhtml+xml", "cover-image"),
        "c1" to MobileEpubManifestItem("c1", "OEBPS/Text/c1.xhtml", "application/xhtml+xml", "", mediaOverlay = "s1"),
        "c2" to MobileEpubManifestItem("c2", "OEBPS/Text/c2.xhtml", "application/xhtml+xml", "", mediaOverlay = "s2"),
        "s1" to MobileEpubManifestItem("s1", "OEBPS/Text/c1.xhtml.smil", SmilMediaType, ""),
        "s2" to MobileEpubManifestItem("s2", "OEBPS/Text/c2.xhtml.smil", SmilMediaType, "")
    )

    private fun meta(property: String, content: String? = null, refines: String? = null) =
        MobileEpubMetaElement(property = property, content = content, text = content, refines = refines)

    @Test
    fun `the index maps spine items to their overlays without reading any smil`() {
        val index = sharedMediaOverlayIndex(
            manifest = manifest,
            spineIds = listOf("cover", "c1", "c2"),
            metaElements = emptyList()
        )
        // Index 0 is the cover, which has no overlay, so it is absent rather than mapped to nothing.
        assertEquals(mapOf(1 to "OEBPS/Text/c1.xhtml.smil", 2 to "OEBPS/Text/c2.xhtml.smil"), index.smilPathBySpineItem)
        assertEquals(mapOf(1 to "s1", 2 to "s2"), index.smilIdBySpineItem)
        assertTrue(index.hasOverlays)
    }

    @Test
    fun `a book without overlays produces an empty index`() {
        val index = sharedMediaOverlayIndex(
            manifest = mapOf("c1" to MobileEpubManifestItem("c1", "c1.xhtml", "application/xhtml+xml", "")),
            spineIds = listOf("c1"),
            metaElements = listOf(meta("media:duration", "0:10:00"))
        )
        assertFalse(index.hasOverlays)
        assertNull(index.totalDurationMs?.takeIf { index.hasOverlays })
    }

    /**
     * A `media:duration` that refines an overlay id is that overlay's duration; one with no
     * `refines` is the whole book. Conflating them would make a 155-overlay book report one
     * chapter's runtime as its total.
     */
    @Test
    fun `a refining duration is per overlay and an unrefined one is the total`() {
        val index = sharedMediaOverlayIndex(
            manifest = manifest,
            spineIds = listOf("c1", "c2"),
            metaElements = listOf(
                meta("media:duration", "0:05:00", refines = "#s1"),
                meta("media:duration", "0:07:30", refines = "#s2"),
                meta("media:duration", "2:11:22.930")
            )
        )
        // `spineIds` here starts at c1, so these are spine item 0 and 1.
        assertEquals(300_000L, index.declaredDurationMsBySpineItem[0])
        assertEquals(450_000L, index.declaredDurationMsBySpineItem[1])
        assertEquals(7_882_930L, index.totalDurationMs)
    }

    /** `2:11:22.930` in the reference book: fractional seconds are padded, not truncated. */
    @Test
    fun `a total duration parsed from a real book`() {
        val index = sharedMediaOverlayIndex(manifest, listOf("c1"), listOf(meta("media:duration", "2:11:22.930")))
        assertEquals(7_882_930L, index.totalDurationMs)
    }

    @Test
    fun `narrator and the two publisher css classes are read`() {
        val index = sharedMediaOverlayIndex(
            manifest = manifest,
            spineIds = listOf("c1"),
            metaElements = listOf(
                meta("media:narrator", "Chris Hughes"),
                meta("media:active-class", "-epub-media-overlay-active"),
                meta("media:playback-active-class", "-epub-media-overlay-playing")
            )
        )
        assertEquals("Chris Hughes", index.narrator)
        assertEquals("-epub-media-overlay-active", index.activeClass)
        assertEquals("-epub-media-overlay-playing", index.playbackActiveClass)
    }

    /** A `media-overlay` pointing at a smil id that is not in the manifest must not crash the load. */
    @Test
    fun `a dangling media-overlay reference is skipped rather than fatal`() {
        val index = sharedMediaOverlayIndex(
            manifest = mapOf("c1" to MobileEpubManifestItem("c1", "c1.xhtml", "application/xhtml+xml", "", mediaOverlay = "gone")),
            spineIds = listOf("c1"),
            metaElements = emptyList()
        )
        assertFalse(index.hasOverlays)
    }

    // -------------------------------------------------------------------------------------------
    // SMIL bodies
    // -------------------------------------------------------------------------------------------

    /** ReadBeyond, chapter 1. Line-level pars, contiguous clips, one mp3. */
    private val readBeyondSmil = """
        <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
         <body>
          <seq id="seq1" epub:textref="p001.xhtml" epub:type="bodymatter chapter">
           <par id="p000001"><text src="p001.xhtml#f000001"/><audio clipBegin="0:00:00.000" clipEnd="0:00:02.200" src="../Audio/001_cover.mp3"/></par>
           <par id="p000002"><text src="p001.xhtml#f000002"/><audio clipBegin="0:00:02.200" clipEnd="0:00:05.300" src="../Audio/001_cover.mp3"/></par>
           <par id="p000003"><text src="p001.xhtml#f000003"/><audio clipBegin="0:00:05.300" clipEnd="0:00:11.600" src="../Audio/001_cover.mp3"/></par>
          </seq>
         </body>
        </smil>
    """.trimIndent()

    private fun parse(raw: String, path: String = "OEBPS/Text/p001.xhtml.smil", index: Int = 3) =
        parseSharedMediaOverlayXml(raw, path, index)

    @Test
    fun `a readbeyond chapter parses to line level clips in playback order`() {
        val doc = parse(readBeyondSmil)!!
        assertEquals(3, doc.spineItemIndex)
        assertEquals("OEBPS/Text/p001.xhtml", doc.textHref)
        assertEquals(3, doc.clips.size)
        assertEquals(listOf("p000001", "p000002", "p000003"), doc.clips.map { it.parId })
        assertEquals(listOf("f000001", "f000002", "f000003"), doc.clips.map { it.elementId })
        // Audio is relative to the SMIL, which lives beside the text, so `../Audio` is one level up.
        assertEquals("OEBPS/Audio/001_cover.mp3", doc.clips[0].audioPath)
        assertTrue(doc.isPlayable)
    }

    @Test
    fun `clip boundaries are preserved exactly`() {
        val clips = parse(readBeyondSmil)!!.clips
        assertEquals(0L, clips[0].clipBeginMs)
        assertEquals(2_200L, clips[0].clipEndMs)
        assertEquals(2_200L, clips[1].clipBeginMs)
        assertEquals(5_300L, clips[1].clipEndMs)
        assertEquals(11_600L, clips[2].clipEndMs)
    }

    /**
     * `audio.mp3#t=begin,end` — the DAISY-derived media fragment a converter emits when it has no
     * `clipBegin` to write.
     *
     * Not a cosmetic omission: the path resolver strips the fragment, so without this the clip comes
     * out as "0 to end of media". `par`s in a chapter usually share one audio file, so every clip
     * would replay that whole file from the start — a player that looks like it works and never
     * advances, which is harder to diagnose than a missing highlight.
     */
    @Test
    fun `a media fragment clock on the audio src supplies the clip boundaries`() {
        val raw = """
            <smil xmlns:epub="http://www.idpf.org/2007/ops" xmlns="http://www.w3.org/ns/SMIL" version="3.0">
              <body epub:textref="../xhtml/c.xhtml">
                <par id="a"><text src="c.xhtml#a"/><audio src="../audio/a.mp3#t=3.72,7.24"/></par>
                <par id="b"><text src="c.xhtml#b"/><audio src="../audio/a.mp3#t=0:00:07.240"/></par>
                <par id="c"><text src="c.xhtml#c"/><audio src="../audio/a.mp3#t=12"/></par>
              </body>
            </smil>
        """.trimIndent()
        val clips = parse(raw, "OEBPS/mo/c.smil")!!.clips

        assertEquals("OEBPS/audio/a.mp3", clips[0].audioPath)
        assertEquals(3_720L, clips[0].clipBeginMs)
        assertEquals(7_240L, clips[0].clipEndMs)
        // A begin-only fragment runs to the end of the media, same as a missing `clipEnd`.
        assertEquals(7_240L, clips[1].clipBeginMs)
        assertNull(clips[1].clipEndMs)
        assertEquals(12_000L, clips[2].clipBeginMs)
    }

    @Test
    fun `an explicit clip attribute wins over a media fragment clock`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL" version="3.0">
              <body epub:textref="c.xhtml">
                <par id="a"><text src="c.xhtml#a"/><audio src="a.mp3#t=3.72,7.24" clipBegin="0:00:01.000" clipEnd="0:00:02.000"/></par>
                <par id="b"><text src="c.xhtml#b"/><audio src="a.mp3#t=3.72,7.24" clipEnd="0:00:02.000"/></par>
                <par id="c"><text src="c.xhtml#c"/><audio src="a.mp3#t=notaclock"/></par>
              </body>
            </smil>
        """.trimIndent()
        val clips = parse(raw)!!.clips

        assertEquals(1_000L, clips[0].clipBeginMs)
        assertEquals(2_000L, clips[0].clipEndMs)
        // The fragment fills only what the attribute leaves unset.
        assertEquals(3_720L, clips[1].clipBeginMs)
        assertEquals(2_000L, clips[1].clipEndMs)
        // An unusable fragment is not a boundary, and must not cost the `par` its narration either.
        assertEquals(0L, clips[2].clipBeginMs)
        assertNull(clips[2].clipEndMs)
    }

    /** A publisher types the enclosing `seq`, not each `par`. A player reading only `par` skips nothing. */
    @Test
    fun `epub types are inherited from ancestor seqs and merged with the pars own`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops">
              <body><seq epub:textref="c.xhtml" epub:type="bodymatter chapter">
                <par id="a"><text src="c.xhtml#a"/><audio src="a.mp3"/></par>
                <seq epub:type="footnote"><par id="b"><text src="c.xhtml#b"/><audio src="b.mp3"/></par></seq>
                <par id="c" epub:type="pagebreak"><text src="c.xhtml#c"/><audio src="c.mp3"/></par>
              </seq></body>
            </smil>
        """.trimIndent()
        val clips = parse(raw)!!.clips
        assertEquals(setOf("bodymatter", "chapter"), clips[0].epubTypes)
        assertEquals(setOf("bodymatter", "chapter", "footnote"), clips[1].epubTypes)
        assertEquals(setOf("bodymatter", "chapter", "pagebreak"), clips[2].epubTypes)
        assertEquals(listOf(1, 2, 1), clips.map { it.seqDepth })
    }

    @Test
    fun `skippable and escapable follow the RS section 9_4 types`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <par id="a" epub:type="pagebreak"><text src="c.xhtml#a"/><audio src="a.mp3"/></par>
                <par id="b" epub:type="dtbn:footnote"><text src="c.xhtml#b"/><audio src="b.mp3"/></par>
                <par id="c" epub:type="table"><text src="c.xhtml#c"/><audio src="c.mp3"/></par>
                <par id="d" epub:type="chapter bodymatter"><text src="c.xhtml#d"/><audio src="d.mp3"/></par>
              </body>
            </smil>
        """.trimIndent()
        val clips = parse(raw)!!.clips
        assertTrue(clips[0].isSkippable)
        // A namespaced type is recognised: `dtbn:footnote` is still a footnote.
        assertTrue(clips[1].isSkippable)
        assertTrue(clips[2].isEscapable)
        assertFalse(clips[3].isSkippable)
        assertFalse(clips[3].isEscapable)
    }

    /**
     * `SmokeTestFXL`'s `basic_tests.smil` puts `par`s directly under `<body>` with no `seq` at all,
     * so `seqDepth == 0` and the body-level `epub:textref` is the only text hint available.
     */
    @Test
    fun `pars directly under body are parsed with a seq depth of zero`() {
        val raw = """
            <smil xmlns:epub="http://www.idpf.org/2007/ops" xmlns="http://www.w3.org/ns/SMIL" version="3.0">
              <body epub:textref="../xhtml/chapter.xhtml">
                <par>
                  <text src="../xhtml/chapter.xhtml#mo-1"/>
                  <audio src="../audio/basic_tests.mp3" clipBegin="00:00:00.000" clipEnd="00:00:01.063"/>
                </par>
                <seq epub:textref="../xhtml/chapter.xhtml#mo-basic-010">
                  <par>
                    <text src="../xhtml/chapter.xhtml#mo-2"/>
                    <audio src="../audio/basic_tests.mp3" clipBegin="00:00:01.063" clipEnd="00:00:02.871"/>
                  </par>
                </seq>
              </body>
            </smil>
        """.trimIndent()
        val doc = parse(raw, "OEBPS/mo/basic_tests.smil")!!
        assertEquals("OEBPS/xhtml/chapter.xhtml", doc.textHref)
        assertEquals(listOf(0, 1), doc.clips.map { it.seqDepth })
        assertEquals("OEBPS/audio/basic_tests.mp3", doc.clips[0].audioPath)
        assertEquals(1_063L, doc.clips[0].clipEndMs)
    }

    /** DAISY's Moby Dick: one mp4 spanning two chapters, attributes split across lines. */
    @Test
    fun `a par whose attributes wrap across lines still parses`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
              <body>
                <seq id="id1" epub:textref="chapter_002.xhtml" epub:type="bodymatter chapter">
                  <par id="heading1">
                    <text src="chapter_002.xhtml#c02h01"/>
                    <audio src="audio/mobydick_001_002_melville.mp4" clipBegin="0:14:45.000"
                      clipEnd="0:14:48.500"/>
                  </par>
                </seq>
              </body>
            </smil>
        """.trimIndent()
        val clips = parse(raw, "OPS/chapter_002_overlay.smil")!!.clips
        assertEquals(1, clips.size)
        assertEquals("c02h01", clips[0].elementId)
        assertEquals("OPS/audio/mobydick_001_002_melville.mp4", clips[0].audioPath)
        assertEquals(885_000L, clips[0].clipBeginMs)
        assertEquals(888_500L, clips[0].clipEndMs)
    }

    // --- RS 9.2.2 timing edge cases ------------------------------------------------------------

    @Test
    fun `a missing clipBegin is zero and a missing clipEnd runs to the end of the media`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="c.xhtml#a"/><audio src="a.mp3"/></par></body>
            </smil>
        """.trimIndent()
        val clip = parse(raw)!!.clips.single()
        assertEquals(0L, clip.clipBeginMs)
        assertNull(clip.clipEndMs)
        assertTrue(clip.runsToEndOfMedia)
    }

    /**
     * `clipEnd` past EOF is clamped by the player, not here — this code does not decode the audio.
     * Pinning that the value is preserved verbatim is what tells a future reader where the clamp
     * has to live.
     */
    @Test
    fun `a clipEnd past the end of the media is kept verbatim for the player to clamp`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="c.xhtml#a"/><audio src="a.mp3" clipBegin="0:00:01" clipEnd="9:00:00"/></par></body>
            </smil>
        """.trimIndent()
        val clip = parse(raw)!!.clips.single()
        assertEquals(1_000L, clip.clipBeginMs)
        assertEquals(32_400_000L, clip.clipEndMs)
    }

    /** A malformed clock drops that `par` rather than starting the wrong paragraph at 0. */
    @Test
    fun `a par with an unparseable clock value is dropped`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body>
                <par id="good"><text src="c.xhtml#a"/><audio src="a.mp3" clipBegin="0:00:01"/></par>
                <par id="bad"><text src="c.xhtml#b"/><audio src="a.mp3" clipBegin="not-a-clock"/></par>
              </body>
            </smil>
        """.trimIndent()
        val clips = parse(raw)!!.clips
        assertEquals(listOf("good"), clips.map { it.parId })
    }

    // --- text anchoring ------------------------------------------------------------------------

    /** A bare `#fragment` means "the document this overlay narrates". Producers do emit it. */
    @Test
    fun `a bare fragment falls back to the body textref`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops">
              <body epub:textref="c.xhtml"><par id="a"><text src="#frag"/><audio src="a.mp3"/></par></body>
            </smil>
        """.trimIndent()
        val doc = parse(raw)!!
        assertEquals("OEBPS/Text/c.xhtml", doc.textHref)
        assertEquals("OEBPS/Text/c.xhtml", doc.clips.single().textHref)
        assertEquals("frag", doc.clips.single().elementId)
    }

    @Test
    fun `a text src with no fragment has a null element id rather than an empty one`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="c.xhtml"/><audio src="a.mp3"/></par></body>
            </smil>
        """.trimIndent()
        val clip = parse(raw)!!.clips.single()
        assertEquals("OEBPS/Text/c.xhtml", clip.textHref)
        assertNull(clip.elementId)
    }

    /** A `par` with no `text` cannot be highlighted, so there is nothing useful to keep. */
    @Test
    fun `a par without a text child is dropped`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body>
                <par id="a"><audio src="a.mp3"/></par>
                <par id="b"><text src="c.xhtml#b"/><audio src="a.mp3"/></par>
              </body>
            </smil>
        """.trimIndent()
        assertEquals(listOf("b"), parse(raw)!!.clips.map { it.parId })
    }

    /** `RS §9.3.3`: a `par` with `text` and no `audio` is legal and is to be spoken by a TTS engine. */
    @Test
    fun `a par with text but no audio is kept and marked narrationless`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="c.xhtml#a"/></par></body>
            </smil>
        """.trimIndent()
        val doc = parse(raw)!!
        assertEquals(1, doc.clips.size)
        assertTrue(doc.clips.single().isNarrationless)
        assertNull(doc.clips.single().audioPath)
        assertFalse(doc.isPlayable)
    }

    // --- failure handling ----------------------------------------------------------------------

    /** A malformed overlay costs that chapter its narration, never the whole book. */
    @Test
    fun `a malformed document is null rather than an exception`() {
        assertNull(parse("<smil><body><par>unclosed"))
        assertNull(parse(""))
        assertNull(parse("not xml at all"))
        // A well-formed document that is not a SMIL at all.
        assertNull(parse("""<html xmlns="http://www.w3.org/1999/xhtml"><body/></html>"""))
    }

    @Test
    fun `an empty body is an empty document not a parse failure`() {
        val doc = parse("""<smil xmlns="http://www.w3.org/ns/SMIL"><body/></smil>""")
        assertEquals(0, doc?.clips?.size)
        assertTrue(doc!!.isEmpty)
    }

    /** A DOCTYPE must not stop the parse, and neither must namespace prefixes on the elements. */
    @Test
    fun `a doctype and prefixed element names are tolerated`() {
        val raw = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE smil>
            <smil:smil xmlns:smil="http://www.w3.org/ns/SMIL">
              <smil:body><smil:par id="a"><smil:text src="c.xhtml#a"/><smil:audio src="a.mp3"/></smil:par></smil:body>
            </smil:smil>
        """.trimIndent()
        val clips = parse(raw)!!.clips
        assertEquals(1, clips.size)
        assertEquals("a", clips.single().elementId)
        assertEquals("OEBPS/Text/c.xhtml", clips.single().textHref)
    }

    // --- path safety ---------------------------------------------------------------------------

    @Test
    fun `a traversal reference is refused rather than escaping the container`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="c.xhtml#a"/><audio src="../../../../etc/passwd"/></par></body>
            </smil>
        """.trimIndent()
        val clip = parse(raw, "OEBPS/Text/c.xhtml.smil")!!.clips.single()
        // The `par` survives — the text anchor is still valid — but its audio does not resolve.
        assertNull(clip.audioPath)
    }

    @Test
    fun `a remote reference is refused rather than truncated at the scheme colon`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="c.xhtml#a"/><audio src="https://cdn.example.com/a.mp3"/></par></body>
            </smil>
        """.trimIndent()
        assertNull(parse(raw)!!.clips.single().audioPath)
    }

    @Test
    fun `percent encoded references are decoded like every other epub path`() {
        val raw = """
            <smil xmlns="http://www.w3.org/ns/SMIL">
              <body><par id="a"><text src="my%20chapter.xhtml#a"/><audio src="my%20audio.mp3"/></par></body>
            </smil>
        """.trimIndent()
        val clip = parse(raw)!!.clips.single()
        assertEquals("OEBPS/Text/my chapter.xhtml", clip.textHref)
        assertEquals("OEBPS/Text/my audio.mp3", clip.audioPath)
    }
}