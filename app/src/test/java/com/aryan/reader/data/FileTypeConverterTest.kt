package com.aryan.reader.data

import com.aryan.reader.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTypeConverterTest {

    @Test
    fun `room converter stores enum names and preserves unknown fallback`() {
        val converter = FileTypeConverter()

        assertEquals("PDF", converter.fromFileType(FileType.PDF))
        assertEquals(FileType.PDF, converter.toFileType("PDF"))
        assertEquals("UNKNOWN", converter.fromFileType(FileType.UNKNOWN))
        assertEquals(FileType.UNKNOWN, converter.toFileType("UNKNOWN"))
        assertNull(converter.fromFileType(null))
        assertNull(converter.toFileType(null))
    }
}
