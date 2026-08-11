package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedDocumentMetadataTest {
    @Test
    fun `office archive metadata paths match android`() {
        assertEquals("docProps/core.xml", sharedDocumentMetadataArchivePath(FileType.DOCX))
        assertEquals("docProps/core.xml", sharedDocumentMetadataArchivePath(FileType.PPTX))
        assertEquals("meta.xml", sharedDocumentMetadataArchivePath(FileType.ODT))
        assertEquals(null, sharedDocumentMetadataArchivePath(FileType.FODT))
    }

    @Test
    fun `office metadata matches local names across namespace prefixes`() {
        val metadata = parseSharedDocumentXmlMetadata(
            """<office:meta><dc:title>A &amp; B</dc:title><meta:initial-creator>Ada Lovelace</meta:initial-creator></office:meta>"""
        )

        assertEquals(SharedDocumentMetadata("A & B", "Ada Lovelace"), metadata)
    }

    @Test
    fun `first title and creator win like android xml extraction`() {
        val metadata = parseSharedDocumentXmlMetadata(
            """<root><title>First</title><title>Second</title><creator>One</creator><initial-creator>Two</initial-creator></root>"""
        )

        assertEquals(SharedDocumentMetadata("First", "One"), metadata)
    }

    @Test
    fun `fb2 metadata joins header authors and ignores body authors`() {
        val metadata = parseSharedFb2Metadata(
            """<FictionBook><description><title-info><book-title>Shared Book</book-title><author><first-name>Ada</first-name><last-name>Lovelace</last-name></author><author><nickname>Editor</nickname></author></title-info></description><body><author><nickname>Body</nickname></author></body></FictionBook>"""
        )

        assertEquals(SharedDocumentMetadata("Shared Book", "Ada Lovelace, Editor"), metadata)
    }
}
