package com.aryan.reader.shared.docparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedFb2DocumentParserTest {

    private val fb2 = """
        <?xml version="1.0" encoding="utf-8"?>
        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
          <description>
            <title-info>
              <author><first-name>Lev</first-name><last-name>Tolstoy</last-name></author>
              <book-title>War &amp; Peace</book-title>
              <coverpage><image l:href="#cover.jpg"/></coverpage>
            </title-info>
          </description>
          <body>
            <section>
              <title><p>Book One</p></title>
              <p>Well, Prince, so Genoa and Lucca are now just family estates.</p>
              <empty-line/>
              <emphasis>important</emphasis> and <strikethrough>gone</strikethrough>
            </section>
            <section>
              <title><p>Book Two</p></title>
              <p>An image follows <image l:href="#img1.png"/>.</p>
            </section>
          </body>
          <binary id="cover.jpg" content-type="image/jpeg">/9j/base64cover</binary>
          <binary id="img1.png" content-type="image/png">iVBORbase64png</binary>
        </FictionBook>
    """.trimIndent()

    @Test
    fun parsesTitleAuthorAndOneChapterPerSection() {
        val result = SharedFb2DocumentParser.parse(fb2, "war.fb2")
        assertNotNull(result)
        assertEquals("War &amp; Peace".replace("&amp;", "&"), result.title)
        assertEquals("Lev Tolstoy", result.author)
        assertEquals(listOf("Book One", "Book Two"), result.chapters.map { it.title })
    }

    @Test
    fun mapsInlineMarkupLikeAndroid() {
        val result = assertNotNull(SharedFb2DocumentParser.parse(fb2, "war.fb2"))
        val firstHtml = result.chapters.first().html
        assertTrue("<i>important</i>" in firstHtml)
        assertTrue("<s>gone</s>" in firstHtml)
        assertTrue("<div class='empty-line'></div>" in firstHtml)
    }

    @Test
    fun referencesBinariesAndDetectsCover() {
        val result = assertNotNull(SharedFb2DocumentParser.parse(fb2, "war.fb2"))
        assertEquals("cover.jpg", result.coverBinaryId)
        assertEquals("iVBORbase64png", result.binariesById.getValue("img1.png").base64)
        assertEquals("image/png", result.binariesById.getValue("img1.png").contentType)
        val secondHtml = result.chapters[1].html
        assertTrue("""<img src="img1.png" />""" in secondHtml)
    }
}
