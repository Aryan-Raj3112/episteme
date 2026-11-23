package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComicInfoMetadataTest {
    @Test
    fun `extracts the fields the library models can hold`() {
        val metadata = parseSharedComicInfoXml(
            """
            <ComicInfo>
                <Title>The Court of Owls</Title>
                <Series>Batman</Series>
                <Number>5</Number>
                <Summary>Bruce Wayne vs. the owls.</Summary>
                <Writer>Scott Snyder</Writer>
                <Penciller>Greg Capullo</Penciller>
            </ComicInfo>
            """.trimIndent()
        )

        assertEquals(
            SharedComicInfoMetadata(
                title = "The Court of Owls",
                author = "Scott Snyder",
                seriesName = "Batman",
                seriesIndex = 5.0,
                description = "Bruce Wayne vs. the owls.",
            ),
            metadata
        )
    }

    @Test
    fun `keeps the filename title when ComicInfo has no Title`() {
        val metadata = parseSharedComicInfoXml("<ComicInfo><Series>Batman</Series><Number>1</Number></ComicInfo>")

        assertNull(metadata.title)
        assertEquals("Batman", metadata.seriesName)
        assertEquals(1.0, metadata.seriesIndex)
    }

    @Test
    fun `writer falls back to penciller and keeps comma separated names`() {
        val writers = parseSharedComicInfoXml("<ComicInfo><Writer>Stan Lee, Jack Kirby</Writer></ComicInfo>")
        val penciller = parseSharedComicInfoXml("<ComicInfo><Penciller>Jim Lee</Penciller></ComicInfo>")
        val writerWins = parseSharedComicInfoXml(
            "<ComicInfo><Writer>Scott Snyder</Writer><Penciller>Greg Capullo</Penciller></ComicInfo>"
        )

        assertEquals("Stan Lee, Jack Kirby", writers.author)
        assertEquals("Jim Lee", penciller.author)
        assertEquals("Scott Snyder", writerWins.author)
    }

    @Test
    fun `number accepts only a pure numeric value`() {
        assertEquals(3.0, parseSharedComicInfoXml("<ComicInfo><Number>3</Number></ComicInfo>").seriesIndex)
        assertEquals(3.0, parseSharedComicInfoXml("<ComicInfo><Number> #3 </Number></ComicInfo>").seriesIndex)
        assertEquals(2.5, parseSharedComicInfoXml("<ComicInfo><Number>2.5</Number></ComicInfo>").seriesIndex)
        assertEquals(1.0, parseSharedComicInfoXml("<ComicInfo><Number>001</Number></ComicInfo>").seriesIndex)

        assertNull(parseSharedComicInfoXml("<ComicInfo><Number>Annual 2</Number></ComicInfo>").seriesIndex)
        assertNull(parseSharedComicInfoXml("<ComicInfo><Number>1-2</Number></ComicInfo>").seriesIndex)
        assertNull(parseSharedComicInfoXml("<ComicInfo><Number>2 (2021)</Number></ComicInfo>").seriesIndex)
        assertNull(parseSharedComicInfoXml("<ComicInfo><Number></Number></ComicInfo>").seriesIndex)
    }

    @Test
    fun `decodes entities and cdata and collapses whitespace`() {
        val metadata = parseSharedComicInfoXml(
            """
            <ComicInfo>
                <Title>Batman &amp; Robin</Title>
                <Summary><![CDATA[Line one
                line two]]></Summary>
                <Writer><![CDATA[Ann &amp; Bob]]></Writer>
            </ComicInfo>
            """.trimIndent()
        )

        assertEquals("Batman & Robin", metadata.title)
        assertEquals("Line one line two", metadata.description)
        assertEquals("Ann & Bob", metadata.author)
    }

    @Test
    fun `tolerates missing malformed and namespace prefixed documents`() {
        val namespaced = parseSharedComicInfoXml(
            "<ci:ComicInfo><ci:Title>Namespaced</ci:Title><ci:Series>Run</ci:Series></ci:ComicInfo>"
        )

        assertEquals("Namespaced", namespaced.title)
        assertEquals("Run", namespaced.seriesName)
        assertEquals(SharedComicInfoMetadata(), parseSharedComicInfoXml(""))
        assertEquals(SharedComicInfoMetadata(), parseSharedComicInfoXml("<ComicInfo>"))
        assertEquals(SharedComicInfoMetadata(), parseSharedComicInfoXml("<ComicInfo><Title> </Title></ComicInfo>"))
    }

    @Test
    fun `matches the sidecar by leaf name at any depth and casing`() {
        assertTrue(isComicInfoArchivePath("ComicInfo.xml"))
        assertTrue(isComicInfoArchivePath("comicinfo.XML"))
        assertTrue(isComicInfoArchivePath("Batman Vol 1/ComicInfo.xml"))
        assertTrue(isComicInfoArchivePath("Batman Vol 1\\ComicInfo.xml"))
        assertFalse(isComicInfoArchivePath("ComicInfo.xml.bak"))
        assertFalse(isComicInfoArchivePath("Art/ComicInfo.txt"))
    }
}
