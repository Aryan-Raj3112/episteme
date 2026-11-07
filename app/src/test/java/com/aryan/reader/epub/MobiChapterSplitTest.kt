package com.aryan.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The MOBI chapter split slices the concatenated flow by NCX file positions, which are **byte**
 * offsets, so the text has to be encoded once. That encode was the reported OOM allocation
 * (`String.getBytes` on the whole book, docs/crashlytics-triage.md#91); it now lives inside
 * `splitMobiChaptersByFilePosition` so it cannot outlive the split. These pin the slicing itself,
 * which is what made the reduction safe to do.
 */
class MobiChapterSplitTest {

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

        val chapters = splitMobiChaptersByFilePosition(
            firstChapter + secondChapter,
            listOf(
                MobiParser.ParsedMobiTocEntry("First", 0),
                MobiParser.ParsedMobiTocEntry("Second", secondChapterByte),
            )
        )

        // Pairs are (html, title), as the chapter mapping destructures them.
        assertEquals(listOf(firstChapter to "First", secondChapter to "Second"), chapters)
    }

    @Test
    fun `skips entries that do not advance and runs the last one to the end`() {
        val chapters = splitMobiChaptersByFilePosition(
            "abcdef",
            listOf(
                MobiParser.ParsedMobiTocEntry("A", 0),
                MobiParser.ParsedMobiTocEntry("stale", 3),
                MobiParser.ParsedMobiTocEntry("B", 3),
            )
        )

        assertEquals(listOf("abc" to "A", "def" to "B"), chapters)
    }

    @Test
    fun `an empty toc yields no chapters`() {
        assertEquals(emptyList<Pair<String, String>>(), splitMobiChaptersByFilePosition("abc", emptyList()))
    }

    @Test
    fun `a boundary inside a multibyte character decodes exactly as the old slice did`() {
        val html = "aé" // 'é' encodes as 0xC3 0xA9
        val bytes = html.toByteArray(Charsets.UTF_8)
        val boundary = bytes.size - 1 // between the two bytes of 'é'

        val chapters = splitMobiChaptersByFilePosition(
            html,
            listOf(
                MobiParser.ParsedMobiTocEntry("A", 0),
                MobiParser.ParsedMobiTocEntry("B", boundary),
            )
        )

        // Byte slicing, then decoding: the split character becomes the replacement char, which is
        // what the previous sliceArray + String(bytes) pair produced.
        assertEquals(String(bytes.sliceArray(0 until boundary), Charsets.UTF_8), chapters[0].first)
        assertEquals(String(bytes.sliceArray(boundary until bytes.size), Charsets.UTF_8), chapters[1].first)
    }
}
