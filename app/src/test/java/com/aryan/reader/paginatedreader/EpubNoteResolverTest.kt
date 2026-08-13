package com.aryan.reader.paginatedreader

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubNoteResolverTest {
    @Test
    fun `noteref targeting a label anchor returns the enclosing note content`() {
        val source = """<p>Text<a epub:type="noteref" href="notes.xhtml#fn1">1</a></p>"""
        val target = """
            <ol>
              <li><a id="fn1" href="chapter.xhtml#ref1">1</a> Actual note content.</li>
            </ol>
        """.trimIndent()

        val result = resolveEpubNoteHtml(source, target, "notes.xhtml#fn1", "fn1")

        assertTrue(result.orEmpty().contains("Actual note content."))
    }

    @Test
    fun `ordinary link containing fn is not treated as a note`() {
        val source = """<p><a href="often.xhtml#part">Continue</a></p>"""
        val target = """<section id="part"><p>Regular chapter content.</p></section>"""

        assertNull(resolveEpubNoteHtml(source, target, "often.xhtml#part", "part"))
    }

    @Test
    fun `semantic footnote target opens without a marked source link`() {
        val source = """<p><a href="#note1">1</a></p>"""
        val target = """<aside id="note1" epub:type="footnote">Semantic note content.</aside>"""

        val result = resolveEpubNoteHtml(source, target, "#note1", "note1")

        assertTrue(result.orEmpty().contains("Semantic note content."))
    }

    @Test
    fun `links in note content resolve against the notes document`() {
        val result = resolveEpubNoteHtml(
            sourceHtml = """<a epub:type="noteref" href="notes/notes.xhtml#n1">1</a>""",
            targetHtml = """<aside id="n1" epub:type="footnote"><a href="../chapter.xhtml#source">Back</a></aside>""",
            href = "notes/notes.xhtml#n1",
            anchor = "n1",
            targetBaseUri = "OEBPS/notes/notes.xhtml"
        )

        val href = org.jsoup.Jsoup.parseBodyFragment(result.orEmpty()).selectFirst("a")?.attr("href")
        assertEquals("OEBPS/chapter.xhtml#source", href)
    }
}
