package com.aryan.reader.epub

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The MOBI chapter split slices the concatenated flow by NCX file positions, which are **byte**
 * offsets, so the text has to be encoded once. That encode was the reported OOM allocation
 * (`String.getBytes` on the whole book, docs/crashlytics-triage.md#91); it now happens only while
 * the encoded bytes exist, and the chapters are decoded from those bytes one at a time so neither
 * the whole-book String nor all chapter Strings stay resident while a chapter is serialized
 * (docs/crashlytics-triage.md#162). These pin the slicing, the decode, and the fallback split that
 * made those reductions safe to do.
 */
class MobiChapterSplitTest {

    /**
     * The chapter rewrite hands back the parsed document so the caller can take the chapter's
     * plain text from it, instead of re-parsing the HTML it had just serialized (a second full
     * jsoup tree per chapter, docs/crashlytics-triage.md#116). That reduction is only valid while
     * the text of the document equals the text of its own serialization - which is what this pins,
     * alongside the asset rewriting the function exists for.
     */
    @Test
    fun `chapter rewrite returns one document whose text matches its own serialization`() {
        val html = """<html><body>
            <p>Caf&eacute; &amp; cr&egrave;me br&ucirc;l&eacute;e</p>
            <link rel="stylesheet" href="kindle:flow:0001?mime=text/css">
            <p>Second <b>para</b> with <i>markup</i></p>
            <img src="kindle:embed:0002?mime=image/jpeg">
            <img recindex="3">
        </body></html>"""

        val doc = rewriteMobiChapterDocument(
            html = html,
            cssFlowMap = mapOf("kindle:flow:0001?mime=text/css" to "flow_0001.css"),
            sequentialImageMap = mapOf(2 to "image_0002.jpg", 3 to "image_0003.png"),
        )

        assertEquals("flow_0001.css", doc.select("link[href]").attr("href"))
        assertEquals(listOf("image_0002.jpg", "image_0003.png"), doc.select("img").map { it.attr("src") })
        assertFalse("recindex must be cleared once rewritten", doc.select("img").any { it.hasAttr("recindex") })

        assertEquals(doc.text(), Jsoup.parse(doc.outerHtml()).text())
    }

    @Test
    fun `splits at byte offsets, not character offsets`() {
        // 'é' and 'ï' are two bytes each, so byte offsets run ahead of character indexes.
        val firstChapter = "<p>café</p>"
        val secondChapter = "<p>naïve</p>"
        val secondChapterByte = firstChapter.toByteArray(Charsets.UTF_8).size
        assertNotEquals(
            "the test only proves something while the two offsets differ",
            firstChapter.length,
            secondChapterByte
        )

        val bytes = (firstChapter + secondChapter).toByteArray(Charsets.UTF_8)
        val slices = splitMobiChapterSlices(
            listOf(
                MobiParser.ParsedMobiTocEntry("First", 0),
                MobiParser.ParsedMobiTocEntry("Second", secondChapterByte),
            ),
            bytes.size
        )

        assertEquals(listOf("First", "Second"), slices.map { it.title })
        assertEquals(listOf(firstChapter, secondChapter), slices.map { it.decode(bytes) })
    }

    @Test
    fun `skips entries that do not advance and runs the last one to the end`() {
        val bytes = "abcdef".toByteArray(Charsets.UTF_8)
        val slices = splitMobiChapterSlices(
            listOf(
                MobiParser.ParsedMobiTocEntry("A", 0),
                MobiParser.ParsedMobiTocEntry("stale", 3),
                MobiParser.ParsedMobiTocEntry("B", 3),
            ),
            bytes.size
        )

        assertEquals(
            listOf("A" to "abc", "B" to "def"),
            slices.map { it.title to it.decode(bytes) }
        )
    }

    @Test
    fun `an empty toc yields no chapters`() {
        assertEquals(emptyList<MobiChapterSlice>(), splitMobiChapterSlices(emptyList(), 3))
    }

    @Test
    fun `a boundary inside a multibyte character decodes exactly as the old slice did`() {
        val html = "aé" // 'é' encodes as 0xC3 0xA9
        val bytes = html.toByteArray(Charsets.UTF_8)
        val boundary = bytes.size - 1 // between the two bytes of 'é'

        val slices = splitMobiChapterSlices(
            listOf(
                MobiParser.ParsedMobiTocEntry("A", 0),
                MobiParser.ParsedMobiTocEntry("B", boundary),
            ),
            bytes.size
        )

        // Byte slicing, then decoding: the split character becomes the replacement char, which is
        // what the previous sliceArray + String(bytes) pair produced.
        assertEquals(String(bytes.sliceArray(0 until boundary), Charsets.UTF_8), slices[0].decode(bytes))
        assertEquals(String(bytes.sliceArray(boundary until bytes.size), Charsets.UTF_8), slices[1].decode(bytes))
    }

    /**
     * The TOC-less fallback now splits the flow while it builds, instead of materializing every
     * chapter first ([docs/crashlytics-triage.md#162]). Titles keep the old numbering - the
     * position in the `split` result, blank parts included - while file names keep the position
     * among the kept chapters, which is what the old two-pass shape produced.
     */
    @Test
    fun `toc-less split keeps old numbering while building one chapter at a time`() {
        val extractionDir = Files.createTempDirectory("mobi-chapters").toFile()
        try {
            val chapters = buildMobiChaptersWithoutToc(
                rawHtml = "<p>one</p><mbp:pagebreak/><mbp:pagebreak/><p>two</p><MBP:PAGEBREAK />",
                extractionDir = extractionDir,
                cssFlowMap = emptyMap(),
                sequentialImageMap = emptyMap()
            )

            // Parts: ["<p>one</p>", "", "<p>two</p>", ""]; blank parts do not become chapters but
            // still advance the title number, and the trailing empty part is dropped.
            assertEquals(listOf("Chapter 1", "Chapter 3"), chapters.map { it.title })
            assertEquals(listOf("chapter_0.html", "chapter_1.html"), chapters.map { it.absPath })
            assertEquals(listOf("mobi_chapter_0", "mobi_chapter_1"), chapters.map { it.chapterId })
            assertTrue(File(extractionDir, "chapter_0.html").isFile)
            assertTrue(File(extractionDir, "chapter_1.html").isFile)
            assertTrue(chapters[0].htmlContent.contains("one"))
            assertEquals("two", chapters[1].plainTextContent)
        } finally {
            extractionDir.deleteRecursively()
        }
    }
}
