package com.aryan.reader.desktop

import com.aryan.reader.shared.pptx.SharedPptxDeckCache
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopPptxDocumentTest {
    @Test
    fun `pptx document loads text links and renderable slides for pdf reader surface`() = withTempDir { dir ->
        val pptx = File(dir, "slides.pptx")
        writeMinimalPptx(pptx)

        val sharedDeck = SharedPptxDeckCache.load(pptx)
        assertEquals(1, sharedDeck.slides.size)
        assertTrue(sharedDeck.slides.single().text.contains("Hello PPTX"))

        val document = DesktopPdfium.loadPptx(pptx)
        try {
            assertEquals("PPTX", document.formatLabel)
            assertEquals(1, document.pageCount)
            assertEquals(720f, document.pageSizes.single().width)
            assertEquals(540f, document.pageSizes.single().height)

            val textPage = document.textPageData(0)
            assertTrue(textPage.text.contains("Hello PPTX"))
            assertTrue(textPage.chars.isNotEmpty())
            assertNotNull(DesktopPdfium.charIndexAt(document, pageIndex = 0, normalizedX = 0.13f, normalizedY = 0.16f))
            assertTrue(DesktopPdfium.textRectsForRange(document, pageIndex = 0, startIndex = 0, endIndex = 4).isNotEmpty())

            val link = DesktopPdfium.linkAt(document, pageIndex = 0, normalizedX = 0.3f, normalizedY = 0.2f)
            assertEquals("https://example.com/slides", link?.uri)

            val image = DesktopPdfium.renderPageBufferedImage(document, pageIndex = 0, scale = 1f)
            assertEquals(720, image.width)
            assertEquals(540, image.height)
        } finally {
            document.close()
        }
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("reader-desktop-pptx").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeMinimalPptx(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeEntry(
                "[Content_Types].xml",
                """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
                    <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
                </Types>
                """.trimIndent()
            )
            zip.writeEntry(
                "ppt/presentation.xml",
                """
                <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <p:sldSz cx="9144000" cy="6858000"/>
                    <p:sldIdLst>
                        <p:sldId id="256" r:id="rId1"/>
                    </p:sldIdLst>
                </p:presentation>
                """.trimIndent()
            )
            zip.writeEntry(
                "ppt/_rels/presentation.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"
                        Target="slides/slide1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.writeEntry(
                "ppt/slides/slide1.xml",
                """
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <p:cSld>
                        <p:bg>
                            <p:bgPr>
                                <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                            </p:bgPr>
                        </p:bg>
                        <p:spTree>
                            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                            <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
                            <p:sp>
                                <p:nvSpPr>
                                    <p:cNvPr id="2" name="Title"/>
                                    <p:cNvSpPr/>
                                    <p:nvPr><a:hlinkClick r:id="rId2"/></p:nvPr>
                                </p:nvSpPr>
                                <p:spPr>
                                    <a:xfrm><a:off x="914400" y="914400"/><a:ext cx="3657600" cy="914400"/></a:xfrm>
                                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                                    <a:solidFill><a:srgbClr val="EAF2FF"/></a:solidFill>
                                    <a:ln><a:solidFill><a:srgbClr val="4472C4"/></a:solidFill></a:ln>
                                </p:spPr>
                                <p:txBody>
                                    <a:bodyPr/>
                                    <a:lstStyle/>
                                    <a:p>
                                        <a:pPr algn="l"/>
                                        <a:r>
                                            <a:rPr sz="2400"><a:solidFill><a:srgbClr val="1F1F1F"/></a:solidFill></a:rPr>
                                            <a:t>Hello PPTX</a:t>
                                        </a:r>
                                    </a:p>
                                </p:txBody>
                            </p:sp>
                        </p:spTree>
                    </p:cSld>
                </p:sld>
                """.trimIndent()
            )
            zip.writeEntry(
                "ppt/slides/_rels/slide1.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId2"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                        Target="https://example.com/slides"
                        TargetMode="External"/>
                </Relationships>
                """.trimIndent()
            )
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
