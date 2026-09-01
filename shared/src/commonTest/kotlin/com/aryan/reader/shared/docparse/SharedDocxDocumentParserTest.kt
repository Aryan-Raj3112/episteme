package com.aryan.reader.shared.docparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedDocxDocumentParserTest {

    @Test
    fun splitsChaptersAtHeadingStylesAndFormatsRuns() {
        val documentXml = """
            <w:document xmlns:w="urn:schemas">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Book Title</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>Bold italic</w:t></w:r><w:r><w:t> plain</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter One</w:t></w:r></w:p>
                <w:p><w:r><w:t>Body text</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = assertNotNull(SharedDocxDocumentParser.parse(documentXml))
        // Content before the first heading forms its own untitled section, like
        // Android's HTML import pipeline.
        assertEquals(listOf("Book Title", null, "Chapter One", null), result.chapters.map { it.title })
        assertTrue("<h1>Book Title</h1>" in result.chapters[0].html)
        assertTrue("<strong><em>Bold italic</em></strong>" in result.chapters[1].html)
        assertTrue(" plain</p>" in result.chapters[1].html)
        assertTrue("<p>Body text</p>" in result.chapters[3].html)
    }

    @Test
    fun rendersNumberedAndBulletedListsFromNumberingXml() {
        val documentXml = """
            <w:document xmlns:w="urn">
              <w:body>
                <w:p><w:pPr><w:numPr><w:numId w:val="5"/></w:numPr></w:pPr><w:r><w:t>first</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:numId w:val="5"/></w:numPr></w:pPr><w:r><w:t>second</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:numId w:val="7"/></w:numPr></w:pPr><w:r><w:t>bullet</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val numberingXml = """
            <w:numbering xmlns:w="urn">
              <w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl></w:abstractNum>
              <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl></w:abstractNum>
              <w:num w:numId="5"><w:abstractNumId w:val="0"/></w:num>
              <w:num w:numId="7"><w:abstractNumId w:val="1"/></w:num>
            </w:numbering>
        """.trimIndent()

        val result = assertNotNull(SharedDocxDocumentParser.parse(documentXml, numberingXml))
        val html = result.chapters.single().html
        assertTrue("<ol>" in html)
        assertTrue("<li>first</li>" in html)
        assertTrue("</ol>\n<ul>" in html)
        assertTrue("<li>bullet</li>" in html)
    }

    @Test
    fun resolvesHyperlinksThroughRelationships() {
        val rels = """
            <Relationships xmlns="ns">
              <Relationship Id="rId2" Target="https://example.com" TargetMode="External"/>
            </Relationships>
        """.trimIndent()
        val documentXml = """
            <w:document xmlns:w="urn" xmlns:r="urnr">
              <w:body>
                <w:p><w:hyperlink r:id="rId2"><w:r><w:t>link text</w:t></w:r></w:hyperlink></w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val targets = SharedDocxDocumentParser.parseHyperlinkTargets(rels)
        assertEquals("https://example.com", targets["rId2"])
        val result = assertNotNull(
            SharedDocxDocumentParser.parse(documentXml, hyperlinkTargets = targets),
        )
        assertTrue("""<a href="https://example.com">link text</a>""" in result.chapters.single().html)
    }

    @Test
    fun embedsMediaThroughResolver() {
        val rels = """
            <Relationships xmlns="ns">
              <Relationship Id="img1" Target="media/pic.png"/>
            </Relationships>
        """.trimIndent()
        val documentXml = """
            <w:document xmlns:w="urn" xmlns:r="urnr" xmlns:a="adraw">
              <w:body>
                <w:p><w:r><w:drawing><a:graphic><pic:pic><a:blip r:embed="img1"/></pic:pic></a:graphic></w:drawing></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val mediaTargets = SharedDocxDocumentParser.parseMediaTargets(rels)
        assertEquals("media/pic.png", mediaTargets["img1"])
        val result = assertNotNull(
            SharedDocxDocumentParser.parse(documentXml, mediaSrcResolver = { id ->
                if (id == "img1") "data:image/png;base64,QUJD" else null
            }),
        )
        assertTrue("""<img src="data:image/png;base64,QUJD" />""" in result.chapters.single().html)
    }
}
