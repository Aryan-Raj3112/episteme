package com.aryan.reader.shared.docparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedOdtDocumentParserTest {

    @Test
    fun parsesZipOdtWithStylesHeadingsAndLists() {
        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:body>
                <text:p>Intro paragraph with <text:span text:style-name="T1">bold</text:span> text.</text:p>
                <text:h text:outline-level="2">Chapter Heading</text:h>
                <text:list>
                  <text:list-item><text:p>first</text:p></text:list-item>
                  <text:list-item><text:p>second</text:p></text:list-item>
                </text:list>
                <text:p>Image: <draw:image xlink:href="Pictures/pic.png"/></text:p>
              </office:body>
            </office:document-content>
        """.trimIndent()
        val stylesXml = """
            <office:document-styles xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0">
              <office:styles>
                <style:style style:name="T1">
                  <style:text-properties fo:font-weight="bold"/>
                </style:style>
              </office:styles>
            </office:document-styles>
        """.trimIndent()

        val result = assertNotNull(
            SharedOdtDocumentParser.parse(contentXml, stylesXml, isFlat = false, fileNameHint = "doc.odt"),
        )
        assertEquals(1, result.chapters.size)
        assertEquals("Part 1", result.chapters.first().title)
        val html = result.chapters.single().html
        assertTrue("<b>bold</b>" in html)
        assertTrue("<h2>Chapter Heading</h2>" in html)
        assertTrue("<ul>" in html)
        assertTrue("<li>" in html)
        assertTrue("""<img src="Pictures/pic.png" />""" in html)
        assertEquals(listOf("Pictures/pic.png"), result.imagePaths)
    }

    @Test
    fun splitsChaptersWhenHtmlExceedsLimit() {
        val filler = "Lorem ipsum dolor sit amet. ".repeat(60)
        val contentXml = buildString {
            append("<office:document-content>")
            repeat(1200) { append("<text:p>$filler</text:p>") }
            append("</office:document-content>")
        }
        val result = assertNotNull(
            SharedOdtDocumentParser.parse(contentXml, null, isFlat = false, fileNameHint = "big.odt"),
        )
        assertTrue(result.chapters.size >= 2, "expected multiple parts, got ${result.chapters.size}")
        assertEquals("Part 1", result.chapters.first().title)
    }

    @Test
    fun parsesFlatOdtBinaryImagesAsDataUris() {
        val contentXml = """
            <office:document xmlns:xlink="http://www.w3.org/1999/xlink">
              <office:body>
                <text:p><draw:image xlink:href="Pictures/Logo.png">
                  <office:binary-data>iVBORw0KGgo=</office:binary-data>
                </draw:image></text:p>
              </office:body>
            </office:document>
        """.trimIndent()
        val result = assertNotNull(
            SharedOdtDocumentParser.parse(contentXml, null, isFlat = true, fileNameHint = "flat.fodt"),
        )
        val html = result.chapters.single().html
        assertTrue("""<img src="data:image/png;base64,iVBORw0KGgo=" />""" in html)
    }

    @Test
    fun extractsTitleAndCreator() {
        val contentXml = """
            <office:document-content>
              <office:meta><dc:title>Odt Title</dc:title><dc:creator>Jane Doe</dc:creator></office:meta>
              <office:body><text:p>hi</text:p></office:body>
            </office:document-content>
        """.trimIndent()
        val result = assertNotNull(
            SharedOdtDocumentParser.parse(contentXml, null, isFlat = true, fileNameHint = "x.odt"),
        )
        assertEquals("Odt Title", result.title)
        assertEquals("Jane Doe", result.author)
    }
}
