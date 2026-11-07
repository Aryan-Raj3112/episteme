package com.aryan.reader.office

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.office.SharedOfficeOxide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The Rust `office_oxide` bridge, end to end on a real device: JNI in
 * `app/src/main/cpp/office_oxide_bridge_jni.cpp`, the cdylib built from
 * `rust/office-oxide-bridge`, and the shared expect/actual surface.
 *
 * Instrumented rather than a JVM unit test because the `.so` only exists on
 * device — and a native bridge whose failure modes are link/load/ABI problems
 * is exactly what a host test cannot cover.
 */
@RunWith(AndroidJUnit4::class)
class OfficeOxideBridgeInstrumentedTest {

    @Test
    fun bridgeLoadsAndAdvertisesOfficeFormats() {
        assertTrue(
            "native office_oxide bridge did not load",
            SharedOfficeOxide.isAvailable,
        )
        for (extension in listOf("docx", ".docx", "DOCX", "xlsx", "pptx", "doc", "xls", "ppt")) {
            assertTrue(
                ".$extension should be supported",
                SharedOfficeOxide.supports(extension),
            )
        }
        for (extension in listOf("odt", "fodt", "pdf", "epub", "zip", "")) {
            assertFalse(
                ".$extension should not be supported",
                SharedOfficeOxide.supports(extension),
            )
        }
    }

    @Test
    fun rendersDocxFormattingToHtml() {
        val html = SharedOfficeOxide.renderHtml(minimalDocx(), "docx", embedImages = false)
        assertNotNull("renderHtml returned null", html)
        checkNotNull(html)
        assertTrue("bold run missing: $html", html.contains("<strong>bold</strong>"))
        assertTrue("italic run missing: $html", html.contains("<em>italic</em>"))
        assertTrue("table cell missing: $html", html.contains("CellA"))
    }

    @Test
    fun extractsPlainText() {
        val text = SharedOfficeOxide.renderPlainText(minimalDocx(), "docx")
        assertNotNull("renderPlainText returned null", text)
        checkNotNull(text)
        assertTrue("body text missing: $text", text.contains("Plain bold and italic."))
    }

    @Test
    fun embedsAlignPositionedFloatingImage() {
        // Word center-aligns pictures with wp:anchor + wp:align (no posOffset).
        // office_oxide 0.1.13 drops those drawings; the bridge shims them to
        // posOffset so the bytes reach the HTML. This test pins the shipped
        // .so/.a, not just the Rust unit tests.
        val html = SharedOfficeOxide.renderHtml(imageDocx(), "docx", embedImages = true)
        assertNotNull("renderHtml returned null", html)
        checkNotNull(html)
        assertTrue("body text missing", html.contains("Before"))
        assertTrue(
            "align-positioned image missing from HTML",
            html.contains("<img src=\"data:image/png;base64,")
        )
    }

    @Test
    fun rejectsWhatItCannotRead() {
        val docx = minimalDocx()
        // Unsupported extension, before any parsing happens.
        assertNull(SharedOfficeOxide.renderHtml(docx, "odt", embedImages = false))
        assertNull(SharedOfficeOxide.renderPlainText(docx, "odt"))
        // Bytes that are not a ZIP container at all.
        val garbage = "this is definitely not an office document".toByteArray()
        assertNull(SharedOfficeOxide.renderHtml(garbage, "docx", embedImages = false))
        assertNull(SharedOfficeOxide.renderPlainText(garbage, "docx"))
        // Empty input.
        assertNull(SharedOfficeOxide.renderHtml(ByteArray(0), "docx", embedImages = false))
    }

    private fun minimalDocx(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putAndWrite("[Content_Types].xml", CONTENT_TYPES)
            zip.putAndWrite("_rels/.rels", RELS)
            zip.putAndWrite("word/document.xml", DOCUMENT)
        }
        return buffer.toByteArray()
    }

    private fun imageDocx(): ByteArray {
        // Content is irrelevant (raw bytes are embedded, never decoded).
        val png = "fakepngbytes".toByteArray(Charsets.UTF_8)
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putAndWrite("[Content_Types].xml", CONTENT_TYPES_WITH_PNG)
            zip.putAndWrite("_rels/.rels", RELS)
            zip.putAndWrite("word/document.xml", ANCHORED_IMAGE_DOCUMENT)
            zip.putAndWrite("word/_rels/document.xml.rels", IMAGE_RELS)
            zip.putNextEntry(ZipEntry("word/media/image1.png"))
            zip.write(png)
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }

    private fun ZipOutputStream.putAndWrite(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.toByteArray(Charsets.UTF_8))
        closeEntry()
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

        private const val CONTENT_TYPES_WITH_PNG =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels"""" +
            """ ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Default Extension="png" ContentType="image/png"/>""" +
            """<Override PartName="/word/document.xml"""" +
            """ ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """</Types>"""

        private const val IMAGE_RELS =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rIdImg"""" +
            """ Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"""" +
            """ Target="media/image1.png"/>""" +
            """</Relationships>"""

        private const val ANCHORED_IMAGE_DOCUMENT =
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"""" +
            """ xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"""" +
            """ xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">""" +
            """<w:body>""" +
            """<w:p><w:r><w:t>Before</w:t></w:r></w:p>""" +
            """<w:p><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0"""" +
            """ simplePos="0" locked="0" layoutInCell="1" allowOverlap="1" relativeHeight="1">""" +
            """<wp:simplePos x="0" y="0"/>""" +
            """<wp:positionH relativeFrom="column"><wp:align>center</wp:align></wp:positionH>""" +
            """<wp:positionV relativeFrom="paragraph"><wp:align>top</wp:align></wp:positionV>""" +
            """<wp:extent cx="9525" cy="9525"/>""" +
            """<wp:effectExtent l="0" t="0" r="0" b="0"/>""" +
            """<wp:wrapSquare wrapText="largest"/>""" +
            """<wp:docPr id="1" name="Picture 1" descr=""/>""" +
            """<wp:cNvGraphicFramePr/>""" +
            """<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
            """<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
            """<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
            """<pic:nvPicPr><pic:cNvPr id="1" name="image1.png"/><pic:cNvPicPr/></pic:nvPicPr>""" +
            """<pic:blipFill><a:blip r:embed="rIdImg"/>""" +
            """<a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
            """<pic:spPr><a:xfrm><a:off x="0" y="0"/>""" +
            """<a:ext cx="9525" cy="9525"/></a:xfrm>""" +
            """<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""" +
            """</pic:pic></a:graphicData></a:graphic>""" +
            """</wp:anchor></w:drawing></w:r></w:p>""" +
            """</w:body></w:document>"""
    }
}
