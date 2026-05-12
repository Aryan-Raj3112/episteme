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
        assertFalse(deck.slides.single().text.contains("Layout Placeholder Prompt"))
        assertTrue(
            deck.slides.single().elements.any {
                it is PptxImageElement && it.bytes.contentEquals(byteArrayOf(1, 2, 3, 4))
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
                                <p:blipFill><a:blip r:embed="rId2"/></p:blipFill>
                                <p:spPr><a:xfrm><a:off x="4000000" y="500000"/><a:ext cx="1000000" cy="1000000"/></a:xfrm></p:spPr>
                            </p:pic>
                            <p:sp>
                                <p:nvSpPr><p:cNvPr id="4" name="Body"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>
                                <p:txBody><a:bodyPr/><a:p><a:r><a:t>Inherited Placeholder</a:t></a:r></a:p></p:txBody>
                            </p:sp>
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
                        <p:txBody><a:bodyPr/><a:p><a:r><a:t>Layout Placeholder Prompt</a:t></a:r></a:p></p:txBody>
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
