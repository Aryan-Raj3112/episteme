package com.aryan.reader.shared.office

import com.aryan.reader.shared.ios.writeIosZipArchive
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Rust `office_oxide` bridge, end to end on iOS: the cinterop binding over
 * `shared/src/nativeInterop/cinterop/office_oxide.def` and the `staticlib`
 * linked into `ReaderShared`. This test is what proves the archive actually
 * links — compiling alone only proves the headers resolve.
 *
 * The DOCX fixture is written with the same libarchive ZIP writer the app
 * already uses ([writeIosZipArchive]), so the test exercises the real
 * container layout rather than a hand-rolled byte blob.
 */
@OptIn(ExperimentalForeignApi::class)
class SharedOfficeOxideIosTest {

    @Test
    fun bridgeReadsDocx() {
        assertTrue(SharedOfficeOxide.isAvailable, "office_oxide bridge unavailable")
        assertTrue(SharedOfficeOxide.supports("docx"))
        assertTrue(SharedOfficeOxide.supports(".DOCX"))
        assertTrue(SharedOfficeOxide.supports("doc"))
        assertTrue(SharedOfficeOxide.supports(".DOT"))
        assertFalse(SharedOfficeOxide.supports("odt"))

        val bytes = fixtureDocxBytes()
        val html = SharedOfficeOxide.renderHtml(bytes, "docx", embedImages = false)
        assertNotNull(html, "renderHtml returned null")
        assertContains(html, "<strong>bold</strong>")
        assertContains(html, "<em>italic</em>")
        assertContains(html, "CellA")

        val text = SharedOfficeOxide.renderPlainText(bytes, "docx")
        assertNotNull(text, "renderPlainText returned null")
        assertContains(text, "Plain bold and italic.")
    }

    @Test
    fun bridgeRejectsWhatItCannotRead() {
        val bytes = fixtureDocxBytes()
        assertNull(SharedOfficeOxide.renderHtml(bytes, "odt", embedImages = false))
        assertNull(SharedOfficeOxide.renderPlainText(bytes, "odt"))
        val garbage = "this is definitely not an office document".encodeToByteArray()
        assertNull(SharedOfficeOxide.renderHtml(garbage, "docx", embedImages = false))
        assertNull(SharedOfficeOxide.renderPlainText(garbage, "docx"))
    }

    private fun fixtureDocxBytes(): ByteArray {
        val path = NSTemporaryDirectory() + "office-oxide-fixture.docx"
        try {
            writeIosZipArchive(
                destinationPath = path,
                orderedEntries = listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"),
                bytesForPath = { name ->
                    when (name) {
                        "[Content_Types].xml" -> CONTENT_TYPES.encodeToByteArray()
                        "_rels/.rels" -> RELS.encodeToByteArray()
                        "word/document.xml" -> DOCUMENT.encodeToByteArray()
                        else -> null
                    }
                },
            )
            return NSData.dataWithContentsOfFile(path)?.toByteArray()
                ?: error("Fixture DOCX missing at $path")
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val output = ByteArray(length.toInt())
        if (output.isNotEmpty()) {
            output.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
        return output
    }

    companion object {
        private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels"""" +
            """ ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/word/document.xml"""" +
            """ ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """</Types>"""

        private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1"""" +
            """ Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"""" +
            """ Target="word/document.xml"/>""" +
            """</Relationships>"""

        private const val DOCUMENT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            """<w:body>""" +
            """<w:p><w:r><w:t xml:space="preserve">Plain </w:t></w:r>""" +
            """<w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r>""" +
            """<w:r><w:t xml:space="preserve"> and </w:t></w:r>""" +
            """<w:r><w:rPr><w:i/></w:rPr><w:t>italic</w:t></w:r><w:r><w:t>.</w:t></w:r></w:p>""" +
            """<w:tbl><w:tr><w:tc><w:p><w:r><w:t>CellA</w:t></w:r></w:p></w:tc>""" +
            """<w:tc><w:p><w:r><w:t>CellB</w:t></w:r></w:p></w:tc></w:tr></w:tbl>""" +
            """</w:body></w:document>"""
    }
}
