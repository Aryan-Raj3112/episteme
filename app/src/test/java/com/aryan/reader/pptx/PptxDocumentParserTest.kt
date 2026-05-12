package com.aryan.reader.pptx

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aryan.reader.FileType
import com.aryan.reader.pdf.DocumentFactory
import com.aryan.reader.pdf.PdfiumCoreProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class PptxDocumentParserTest {

    @Test
    fun `parser resolves slide order inheritance and media relationships`() {
        val file = createTinyPptx()

        val deck = PptxDocumentParser.parse(file)

        assertEquals(720, deck.widthPoint)
        assertEquals(405, deck.heightPoint)
        assertEquals(1, deck.slides.size)
        assertTrue(deck.slides.single().text.contains("Master Text"))
        assertTrue(deck.slides.single().text.contains("Layout Text"))
        assertTrue(deck.slides.single().text.contains("Hello PPTX"))
        assertTrue(deck.slides.single().text.contains("Inherited Placeholder"))
        assertTrue(deck.slides.single().text.contains("Cell A"))
        assertTrue(deck.slides.single().text.contains("Grouped Text"))
        assertFalse(deck.slides.single().text.contains("Layout Placeholder Prompt"))
        val inheritedPlaceholder = deck.slides.single().elements
            .filterIsInstance<PptxShapeElement>()
            .single { shape -> shape.paragraphs.any { paragraph -> paragraph.runs.any { it.text.contains("Inherited Placeholder") } } }
        assertEquals(PptxTextAlign.CENTER, inheritedPlaceholder.paragraphs.single().alignment)
        assertEquals(24f, inheritedPlaceholder.paragraphs.single().runs.first().sizePt)
        assertEquals(PptxAutoFitMode.NORMAL, inheritedPlaceholder.autoFitMode)
        assertEquals(0.8f, inheritedPlaceholder.fontScale, 0.001f)
        val centeredShape = deck.slides.single().elements
            .filterIsInstance<PptxShapeElement>()
            .single { shape -> shape.paragraphs.any { paragraph -> paragraph.runs.any { it.text.contains("Centered") } } }
        assertEquals(PptxTextAlign.CENTER, centeredShape.paragraphs.single().alignment)
        assertEquals(PptxVerticalAnchor.MIDDLE, centeredShape.verticalAnchor)
        assertEquals(36f, centeredShape.paragraphs.single().runs.first().sizePt)
        val table = deck.slides.single().elements.filterIsInstance<PptxTableElement>().single()
        assertEquals(1, table.rows.size)
        assertEquals(PptxVerticalAnchor.MIDDLE, table.rows.single().cells.first().verticalAnchor)
        val groupedShape = deck.slides.single().elements
            .filterIsInstance<PptxShapeElement>()
            .single { shape -> shape.paragraphs.any { paragraph -> paragraph.runs.any { it.text.contains("Grouped Text") } } }
        assertTrue(groupedShape.bounds.left > 35f)
        assertEquals(PptxAutoFitMode.SHAPE, groupedShape.autoFitMode)
        assertTrue(
            deck.slides.single().elements.any {
                it is PptxImageElement &&
                    it.bytes.contentEquals(byteArrayOf(1, 2, 3, 4)) &&
                    it.crop.left > 0f
            }
        )
    }

    @Test
    fun `document wrapper renders slide bitmap and exposes indexed text`() = runTest {
        val file = createTinyPptx()
        PptxDocumentWrapper(file).use { document ->
            assertEquals(1, document.getPageCount())
            val page = document.openPage(0)!!
            page.use {
                val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
                it.renderPageBitmap(bitmap, 0, 0, 320, 180, false)
                assertEquals(720, it.getPageWidthPoint())
                assertEquals(405, it.getPageHeightPoint())
                it.openTextPage().use { textPage ->
                    val count = textPage.textPageCountChars()
                    assertTrue(count > 0)
                    assertTrue(textPage.textPageGetText(0, count).orEmpty().contains("Hello PPTX"))
                    assertTrue(textPage.textPageGetRectsForRanges(intArrayOf(0, 5)).orEmpty().isNotEmpty())
                }
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `document factory routes pptx to native pptx wrapper`() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context
        val file = createTinyPptx()

        val document = DocumentFactory.loadDocument(
            context = context,
            uri = Uri.fromFile(file),
            type = FileType.PPTX,
            password = null,
            pdfiumCore = PdfiumCoreProvider.core
        )

        document.use {
            assertTrue(it is PptxDocumentWrapper)
            assertEquals(1, it.getPageCount())
        }
    }

    private fun createTinyPptx(): File {
        val file = File.createTempFile("reader-test", ".pptx").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putText(
                "ppt/presentation.xml",
                """
                <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <p:sldSz cx="9144000" cy="5143500"/>
                    <p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst>
                </p:presentation>
                """.trimIndent()
            )
            zip.putText(
                "ppt/_rels/presentation.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.putText(
                "ppt/slides/slide1.xml",
                """
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <p:cSld>
                        <p:spTree>
                            <p:sp>
                                <p:nvSpPr><p:cNvPr id="2" name="Title"/></p:nvSpPr>
                                <p:spPr><a:xfrm><a:off x="500000" y="500000"/><a:ext cx="3000000" cy="800000"/></a:xfrm><a:solidFill><a:schemeClr val="accent1"/></a:solidFill></p:spPr>
                                <p:txBody><a:bodyPr/><a:p><a:r><a:rPr sz="2800"/><a:t>Hello PPTX</a:t></a:r></a:p></p:txBody>
                            </p:sp>
                            <p:pic>
                                <p:nvPicPr><p:cNvPr id="3" name="Image"/></p:nvPicPr>
                                <p:blipFill><a:blip r:embed="rId2"/><a:srcRect l="10000"/></p:blipFill>
                                <p:spPr><a:xfrm><a:off x="4000000" y="500000"/><a:ext cx="1000000" cy="1000000"/></a:xfrm></p:spPr>
                            </p:pic>
                            <p:sp>
                                <p:nvSpPr><p:cNvPr id="4" name="Body"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>
                                <p:txBody><a:bodyPr><a:normAutofit fontScale="80000" lnSpcReduction="10000"/></a:bodyPr><a:p><a:r><a:t>Inherited Placeholder</a:t></a:r></a:p></p:txBody>
                            </p:sp>
                            <p:sp>
                                <p:nvSpPr><p:cNvPr id="5" name="Centered"/></p:nvSpPr>
                                <p:spPr><a:xfrm><a:off x="4500000" y="1800000"/><a:ext cx="3500000" cy="900000"/></a:xfrm><a:gradFill><a:gsLst><a:gs pos="0"><a:srgbClr val="FFFFFF"/></a:gs><a:gs pos="100000"><a:srgbClr val="DDEEFF"/></a:gs></a:gsLst><a:lin ang="5400000"/></a:gradFill></p:spPr>
                                <p:txBody><a:bodyPr anchor="ctr" lIns="182880" rIns="182880"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="3600" b="1"/><a:t>Centered</a:t></a:r><a:r><a:rPr sz="1800"/><a:t> Small</a:t></a:r></a:p></p:txBody>
                            </p:sp>
                            <p:graphicFrame>
                                <p:nvGraphicFramePr><p:cNvPr id="6" name="Table"/></p:nvGraphicFramePr>
                                <p:xfrm><a:off x="4500000" y="3000000"/><a:ext cx="3000000" cy="800000"/></p:xfrm>
                                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">
                                    <a:tbl><a:tblGrid><a:gridCol w="1500000"/><a:gridCol w="1500000"/></a:tblGrid>
                                        <a:tr h="800000">
                                            <a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>Cell A</a:t></a:r></a:p></a:txBody><a:tcPr marL="12700" anchor="ctr"><a:solidFill><a:srgbClr val="FFF2CC"/></a:solidFill></a:tcPr></a:tc>
                                            <a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>Cell B</a:t></a:r></a:p></a:txBody><a:tcPr/></a:tc>
                                        </a:tr>
                                    </a:tbl>
                                </a:graphicData></a:graphic>
                            </p:graphicFrame>
                            <p:grpSp>
                                <p:nvGrpSpPr><p:cNvPr id="7" name="Group"/></p:nvGrpSpPr>
                                <p:grpSpPr><a:xfrm><a:off x="500000" y="3000000"/><a:ext cx="2000000" cy="900000"/><a:chOff x="0" y="0"/><a:chExt cx="2000000" cy="900000"/></a:xfrm></p:grpSpPr>
                                <p:sp>
                                    <p:nvSpPr><p:cNvPr id="8" name="Grouped"/></p:nvSpPr>
                                    <p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1600000" cy="500000"/></a:xfrm></p:spPr>
                                    <p:txBody><a:bodyPr><a:spAutoFit/></a:bodyPr><a:p><a:r><a:t>Grouped Text</a:t></a:r></a:p></p:txBody>
                                </p:sp>
                            </p:grpSp>
                        </p:spTree>
                    </p:cSld>
                </p:sld>
                """.trimIndent()
            )
            zip.putText(
                "ppt/slides/_rels/slide1.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
                </Relationships>
                """.trimIndent()
            )
            zip.putText(
                "ppt/slideLayouts/slideLayout1.xml",
                layoutPart()
            )
            zip.putText(
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.putText(
                "ppt/slideMasters/slideMaster1.xml",
                textPart("Master Text")
            )
            zip.putText(
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            zip.putText(
                "ppt/theme/theme1.xml",
                """
                <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <a:themeElements><a:clrScheme name="Reader">
                        <a:dk1><a:srgbClr val="000000"/></a:dk1>
                        <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
                        <a:accent1><a:srgbClr val="3366CC"/></a:accent1>
                    </a:clrScheme></a:themeElements>
                </a:theme>
                """.trimIndent()
            )
            zip.putBytes("ppt/media/image1.png", byteArrayOf(1, 2, 3, 4))
        }
        return file
    }

    private fun textPart(text: String): String {
        return """
            <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld><p:spTree><p:sp>
                    <p:spPr><a:xfrm><a:off x="100000" y="4200000"/><a:ext cx="3000000" cy="500000"/></a:xfrm></p:spPr>
                    <p:txBody><a:bodyPr/><a:p><a:r><a:t>$text</a:t></a:r></a:p></p:txBody>
                </p:sp></p:spTree></p:cSld>
            </p:sldLayout>
        """.trimIndent()
    }

    private fun layoutPart(): String {
        return """
            <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld><p:spTree>
                    <p:sp>
                        <p:spPr><a:xfrm><a:off x="100000" y="4200000"/><a:ext cx="3000000" cy="500000"/></a:xfrm></p:spPr>
                        <p:txBody><a:bodyPr/><a:p><a:r><a:t>Layout Text</a:t></a:r></a:p></p:txBody>
                    </p:sp>
                    <p:sp>
                        <p:nvSpPr><p:cNvPr id="9" name="Body Placeholder"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="1000000" y="1800000"/><a:ext cx="4000000" cy="900000"/></a:xfrm></p:spPr>
                        <p:txBody><a:bodyPr/><a:lstStyle><a:lvl1pPr algn="ctr"><a:defRPr sz="2400"/></a:lvl1pPr></a:lstStyle><a:p><a:r><a:t>Layout Placeholder Prompt</a:t></a:r></a:p></p:txBody>
                    </p:sp>
                </p:spTree></p:cSld>
            </p:sldLayout>
        """.trimIndent()
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putBytes(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }
}
