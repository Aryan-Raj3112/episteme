package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfTextBoxParagraphsTest {

    @Test
    fun `legacy codec round trips alignment and lists sparsely`() {
        val box = SharedPdfLegacyTextBox(
            id = "b1",
            pageIndex = 0,
            bounds = PdfPageBounds(0f, 0f, 0.4f, 0.1f),
            text = "• a\n1. b\nc",
            colorArgb = 0xFF000000.toInt(),
            backgroundArgb = 0,
            fontSize = 0.032f,
            paragraphs = listOf(
                SharedPdfRichParagraph(
                    alignment = SharedPdfRichTextAlign.CENTER,
                    listType = SharedPdfRichListType.BULLET
                ),
                SharedPdfRichParagraph(
                    alignment = SharedPdfRichTextAlign.LEFT,
                    listType = SharedPdfRichListType.NUMBERED
                ),
                SharedPdfRichParagraph(),
            ),
        )
        val decoded = SharedPdfLegacyTextBoxCodec.decode(SharedPdfLegacyTextBoxCodec.encode(listOf(box))).single()
        assertEquals(box.text, decoded.text)
        assertEquals(box.paragraphs.trimmedRichParagraphs(), decoded.paragraphs)
    }

    @Test
    fun `legacy codec omits paragraphs for plain boxes and decodes old payloads`() {
        val plain = SharedPdfLegacyTextBox(
            id = "plain",
            pageIndex = 1,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.5f, 0.2f),
            text = "hello",
            colorArgb = 0xFF000000.toInt(),
            backgroundArgb = 0,
            fontSize = 0.032f,
        )
        val encoded = SharedPdfLegacyTextBoxCodec.encode(listOf(plain))
        assertTrue(!encoded.contains("paragraphs"))
        assertEquals(emptyList(), SharedPdfLegacyTextBoxCodec.decode(encoded).single().paragraphs)

        val oldPayload = """[{"id":"old","pageIndex":0,"text":"note","color":-16777216,"backgroundColor":0,"fontSize":0.032,"bounds":{"left":0,"top":0,"right":0.4,"bottom":0.1}}]"""
        assertEquals(emptyList(), SharedPdfLegacyTextBoxCodec.decode(oldPayload).single().paragraphs)
    }

    @Test
    fun `annotated round trip preserves alignment and list tags`() {
        val text = "• hello\nworld"
        val paragraphs = listOf(
            SharedPdfRichParagraph(
                alignment = SharedPdfRichTextAlign.RIGHT,
                listType = SharedPdfRichListType.BULLET
            ),
            SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
        )
        val annotated = sharedPdfTextBoxAnnotatedString(text, paragraphs, SpanStyle())
        assertEquals(paragraphs.trimmedRichParagraphs(), sharedPdfTextBoxParagraphs(annotated))
        val state = sharedPdfTextBoxParagraphUiState(annotated, TextRange(0))
        assertEquals(SharedPdfRichTextAlign.RIGHT, state.alignment)
        assertTrue(state.isBulleted)
    }

    @Test
    fun `toggle and alignment helpers match rich text behavior`() {
        val annotated = AnnotatedString("a\nb")
        val listed = sharedPdfToggleTextBoxList(annotated, TextRange(0, 3), SharedPdfRichListType.NUMBERED)
        assertEquals("1. a\n2. b", listed.text)
        val rebuilt = sharedPdfTextBoxAnnotatedString(listed.text, listed.paragraphs, SpanStyle())
        val centered = sharedPdfSetTextBoxAlignment(rebuilt, TextRange(0, listed.text.length), SharedPdfRichTextAlign.CENTER)
        assertTrue(sharedPdfTextBoxParagraphs(centered).all { it.alignment == SharedPdfRichTextAlign.CENTER })
        assertEquals(
            listOf(SharedPdfRichTextAlign.CENTER, SharedPdfRichTextAlign.CENTER),
            sharedPdfTextBoxParagraphAlignments(centered.text, sharedPdfTextBoxParagraphs(centered))
        )
    }

    @Test
    fun `dock state reports stored alignment for empty boxes`() {
        val paragraphs = listOf(
            SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER)
        )
        val state = sharedPdfTextBoxDockState("", paragraphs, TextRange(0))
        assertEquals(SharedPdfRichTextAlign.CENTER, state.alignment)
        val plain = sharedPdfTextBoxDockState("", emptyList(), TextRange(0))
        assertEquals(SharedPdfRichTextAlign.LEFT, plain.alignment)
    }

    @Test
    fun `first keystroke in empty centered box preserves alignment`() {
        val stored = listOf(
            SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER)
        )
        val result = sharedPdfTextBoxKeystroke(
            oldText = "",
            oldParagraphs = stored,
            newText = "hi",
            newSelection = TextRange(2),
        )
        assertEquals("hi", result.text)
        assertEquals(SharedPdfRichTextAlign.CENTER, result.paragraphs.single().alignment)
    }

    @Test
    fun `typing in trailing empty centered paragraph preserves alignment`() {
        val text = "hello\n"
        val stored = listOf(
            SharedPdfRichParagraph(),
            SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.RIGHT),
        )
        val result = sharedPdfTextBoxKeystroke(
            oldText = text,
            oldParagraphs = stored,
            newText = "hello\nx",
            newSelection = TextRange(7),
        )
        assertEquals("hello\nx", result.text)
        assertEquals(SharedPdfRichTextAlign.LEFT, result.paragraphs[0].alignment)
        assertEquals(SharedPdfRichTextAlign.RIGHT, result.paragraphs[1].alignment)
    }
}
