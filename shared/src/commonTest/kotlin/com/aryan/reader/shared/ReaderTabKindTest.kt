package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderTabKindTest {

    @Test
    fun `page renderer formats belong to the fixed layout tab set`() {
        listOf(FileType.PDF, FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT, FileType.PPTX)
            .forEach { type ->
                assertEquals(ReaderTabKind.FIXED_LAYOUT, type.readerTabKind(), "type=$type")
            }
    }

    @Test
    fun `reflow formats belong to the text tab set`() {
        listOf(
            FileType.EPUB,
            FileType.TXT,
            FileType.MD,
            FileType.HTML,
            FileType.MOBI,
            FileType.FB2,
            FileType.DOCX,
            FileType.DOC,
            FileType.ODT,
            FileType.FODT,
        ).forEach { type ->
            assertEquals(ReaderTabKind.TEXT, type.readerTabKind(), "type=$type")
        }
    }

    @Test
    fun `a type without a mobile reader has no tab set`() {
        assertNull(FileType.UNKNOWN.readerTabKind())
        assertNull(FileType.AUDIOBOOK.readerTabKind())
    }

    @Test
    fun `persisted values round trip and unknown values fall back to no kind`() {
        ReaderTabKind.entries.forEach { kind ->
            assertEquals(kind, ReaderTabKind.fromPersisted(kind.persistedValue))
        }
        assertNull(ReaderTabKind.fromPersisted("audio"))
        assertNull(ReaderTabKind.fromPersisted(null))
    }

    @Test
    fun `reflow views are not books that own tabs`() {
        assertEquals(true, "abc123_reflow".isDerivedReflowBookId())
        assertEquals(false, "abc123".isDerivedReflowBookId())
        assertEquals(false, "reflow_abc123".isDerivedReflowBookId())
    }
}
