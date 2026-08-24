package com.aryan.reader.shared.ui

import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedMobileEpubFootnotesTest {

    @Test
    fun `noteref resolves a cross chapter note without navigation`() {
        val note = assertNotNull(
            footnoteBook().resolveMobileEpubFootnote(
                rawHref = "../notes/notes.xhtml#fn1",
                ownerHref = "OEBPS/text/chapter.xhtml",
            )
        )

        assertEquals("OEBPS/notes/notes.xhtml", note.targetHref)
        assertEquals(1, note.targetChapterIndex)
        assertEquals("fn1", note.fragment)
        assertEquals("1 Actual note content.", note.plainText)
    }

    @Test
    fun `ordinary internal anchor remains an ordinary link`() {
        val book = SharedEpubBook(
            id = "ordinary",
            fileName = "ordinary.epub",
            title = "Ordinary",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter",
                    title = "Chapter",
                    plainText = "Continue",
                    baseHref = "chapter.xhtml",
                    htmlContent = "<div><p><a href=\"chapter.xhtml#part\">Continue</a></p></div>",
                ),
                SharedEpubChapter(
                    id = "target",
                    title = "Target",
                    plainText = "Regular content",
                    baseHref = "target.xhtml",
                    htmlContent = "<div><section id=\"part\">Regular content</section></div>",
                ),
            ),
        )

        assertNull(book.resolveMobileEpubFootnote("target.xhtml#part", "chapter.xhtml"))
    }

    @Test
    fun `semantic note target resolves even when source anchor has no noteref marker`() {
        val book = SharedEpubBook(
            id = "semantic",
            fileName = "semantic.epub",
            title = "Semantic",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter",
                    title = "Chapter",
                    plainText = "One",
                    baseHref = "chapter.xhtml",
                    htmlContent = "<div><p><a href=\"#note-1\">1</a></p></div>",
                ),
                SharedEpubChapter(
                    id = "target",
                    title = "Target",
                    plainText = "Semantic note",
                    baseHref = "notes.xhtml",
                    htmlContent = "<div><aside id=\"note-1\" epub:type=\"footnote\">Semantic note content.</aside></div>",
                ),
            ),
        )

        val note = assertNotNull(book.resolveMobileEpubFootnote("notes.xhtml#note-1", "chapter.xhtml"))
        assertEquals("Semantic note content.", note.plainText)
    }

    @Test
    fun `unsupported and external schemes never resolve as notes`() {
        val book = footnoteBook()

        assertNull(book.resolveMobileEpubFootnote("javascript:alert(1)#fn1", "OEBPS/text/chapter.xhtml"))
        assertNull(book.resolveMobileEpubFootnote("https://example.com/notes.xhtml#fn1", "OEBPS/text/chapter.xhtml"))
        assertNull(book.resolveMobileEpubFootnote("../missing.xhtml#fn1", "OEBPS/text/chapter.xhtml"))
    }

    private fun footnoteBook(): SharedEpubBook = SharedEpubBook(
        id = "footnotes",
        fileName = "footnotes.epub",
        title = "Footnotes",
        chapters = listOf(
            SharedEpubChapter(
                id = "chapter",
                title = "Chapter",
                plainText = "Text 1",
                baseHref = "OEBPS/text/chapter.xhtml",
                htmlContent = """
                    <div>
                      <p>Text<a epub:type="noteref" href="../notes/notes.xhtml#fn1">1</a></p>
                    </div>
                """.trimIndent(),
            ),
            SharedEpubChapter(
                id = "notes",
                title = "Notes",
                plainText = "Actual note content.",
                baseHref = "OEBPS/notes/notes.xhtml",
                htmlContent = """
                    <div>
                      <ol>
                        <li><a id="fn1" href="../text/chapter.xhtml#ref1">1</a> Actual note content.</li>
                      </ol>
                    </div>
                """.trimIndent(),
            ),
        ),
    )
}
