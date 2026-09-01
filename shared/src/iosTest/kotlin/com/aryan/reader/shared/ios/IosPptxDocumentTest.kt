package com.aryan.reader.shared.ios

import com.aryan.reader.shared.pptx.SharedPptxColor
import com.aryan.reader.shared.pptx.SharedPptxGradientFill
import com.aryan.reader.shared.pptx.SharedPptxParagraph
import com.aryan.reader.shared.pptx.SharedPptxRect
import com.aryan.reader.shared.pptx.SharedPptxShapeElement
import com.aryan.reader.shared.pptx.SharedPptxSlide
import com.aryan.reader.shared.pptx.SharedPptxTextRun
import kotlin.test.Test
import kotlin.test.assertContains

class IosPptxDocumentTest {
    @Test
    fun `html renderer preserves slide geometry gradient and rich text`() {
        val slide = SharedPptxSlide(
            widthPoint = 720,
            heightPoint = 405,
            backgroundColor = SharedPptxColor.WHITE,
            elements = listOf(
                SharedPptxShapeElement(
                    bounds = SharedPptxRect(24f, 30f, 360f, 120f),
                    preset = "roundRect",
                    fillColor = null,
                    gradientFill = SharedPptxGradientFill(SharedPptxColor.BLUE, SharedPptxColor.CYAN, 90f),
                    lineColor = SharedPptxColor.BLACK,
                    lineWidthPoint = 1f,
                    paragraphs = listOf(
                        SharedPptxParagraph(
                            runs = listOf(SharedPptxTextRun("Hello", sizePt = 26f, bold = true)),
                        ),
                    ),
                    hyperlink = "https://example.com",
                    placeholderKey = null,
                ),
            ),
            text = "Hello",
            charBoxes = emptyList(),
        )

        val html = IosPptxHtmlRenderer.render(slide, slideNumber = 1)
        assertContains(html, "data-slide=\"1\"")
        assertContains(html, "aspect-ratio:720/405")
        assertContains(html, "linear-gradient")
        assertContains(html, "font-weight:700")
        assertContains(html, "https://example.com")
        assertContains(html, "Hello")
    }
}
