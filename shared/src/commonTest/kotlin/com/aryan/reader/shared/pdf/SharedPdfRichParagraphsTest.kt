package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfRichParagraphsTest {

    private fun plain(text: String): AnnotatedString = AnnotatedString(text)

    @Test
    fun `toggle bullet inserts marker and tracks selection`() {
        val result = toggleRichParagraphList(
            annotated = plain("hello"),
            selection = TextRange(5),
            type = SharedPdfRichListType.BULLET,
        )
        assertEquals("• hello", result.text)
        assertEquals(TextRange(7), result.selection)
        assertEquals(listOf(SharedPdfRichParagraph(listType = SharedPdfRichListType.BULLET)), result.paragraphs)
    }

    @Test
    fun `toggle bullet twice removes marker`() {
        val listed = toggleRichParagraphList(plain("hello"), TextRange(5), SharedPdfRichListType.BULLET)
        val listedAnnotated = AnnotatedString(
            text = listed.text,
            spanStyles = emptyList(),
            paragraphStyles = emptyList(),
        )
        // Rebuild annotated with the list tag the way the controller would.
        val builder = AnnotatedString.Builder(listed.text)
        applyRichParagraphsToBuilder(builder, listed.text, listed.paragraphs)
        val second = toggleRichParagraphList(builder.toAnnotatedString(), TextRange(2), SharedPdfRichListType.BULLET)
        assertEquals("hello", second.text)
        assertEquals(SharedPdfRichListType.NONE, second.paragraphs.single().listType)
        assertTrue(listedAnnotated.text.isNotEmpty())
    }

    @Test
    fun `toggle numbered numbers consecutive paragraphs`() {
        val result = toggleRichParagraphList(
            annotated = plain("a\nb\nc"),
            selection = TextRange(0, 5),
            type = SharedPdfRichListType.NUMBERED,
        )
        assertEquals("1. a\n2. b\n3. c", result.text)
        assertTrue(result.paragraphs.all { it.listType == SharedPdfRichListType.NUMBERED })
    }

    @Test
    fun `toggle numbered across existing run renumbers`() {
        val before = toggleRichParagraphList(plain("a\nb"), TextRange(0, 3), SharedPdfRichListType.NUMBERED)
        assertEquals("1. a\n2. b", before.text)
        val builder = AnnotatedString.Builder(before.text)
        applyRichParagraphsToBuilder(builder, before.text, before.paragraphs)
        val after = toggleRichParagraphList(builder.toAnnotatedString(), TextRange(0, 8), SharedPdfRichListType.BULLET)
        assertEquals("• a\n• b", after.text)
    }

    @Test
    fun `enter at end of bullet item continues list`() {
        val old = plain("• hello")
        val oldWithTag = AnnotatedString.Builder("• hello").apply {
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_BULLET, 0, 7)
        }.toAnnotatedString()
        assertTrue(old.text.isNotEmpty())
        val result = applyRichParagraphKeystroke(
            old = oldWithTag,
            newText = "• hello\n",
            newSelection = TextRange(8),
        )
        assertEquals("• hello\n• ", result.text)
        assertEquals(TextRange(10), result.selection)
        assertTrue(result.paragraphs.all { it.listType == SharedPdfRichListType.BULLET })
    }

    @Test
    fun `enter on empty bullet item exits list`() {
        val old = AnnotatedString.Builder("• ").apply {
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_BULLET, 0, 2)
        }.toAnnotatedString()
        val result = applyRichParagraphKeystroke(
            old = old,
            newText = "• \n",
            newSelection = TextRange(3),
        )
        assertEquals("\n", result.text)
        assertTrue(result.paragraphs.all { it.listType == SharedPdfRichListType.NONE })
    }

    @Test
    fun `backspace over bullet marker exits list`() {
        val old = AnnotatedString.Builder("• hello").apply {
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_BULLET, 0, 7)
        }.toAnnotatedString()
        val result = applyRichParagraphKeystroke(
            old = old,
            newText = "hello",
            newSelection = TextRange(0),
        )
        assertEquals("hello", result.text)
        assertEquals(SharedPdfRichListType.NONE, result.paragraphs.single().listType)
    }

    @Test
    fun `typing at list start lands after marker`() {
        val old = AnnotatedString.Builder("• hello").apply {
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_BULLET, 0, 7)
        }.toAnnotatedString()
        val result = applyRichParagraphKeystroke(
            old = old,
            newText = "x• hello",
            newSelection = TextRange(1),
        )
        assertEquals("• xhello", result.text)
        assertEquals(TextRange(3), result.selection)
        assertEquals(SharedPdfRichListType.BULLET, result.paragraphs.single().listType)
    }

    @Test
    fun `backspace merging paragraphs drops merged list and orphan marker`() {
        val old = AnnotatedString.Builder("one\n• two").apply {
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_BULLET, 4, 9)
        }.toAnnotatedString()
        // Delete the newline joining "two" up: "one• two".
        val result = applyRichParagraphKeystroke(
            old = old,
            newText = "one• two",
            newSelection = TextRange(3),
        )
        assertEquals("onetwo", result.text)
        assertEquals(SharedPdfRichListType.NONE, result.paragraphs.single().listType)
    }

    @Test
    fun `alignment set and read back`() {
        val annotated = plain("left\ncenter me")
        val centered = setRichParagraphAlignment(annotated, TextRange(5, 13), SharedPdfRichTextAlign.CENTER)
        val attrs = readRichParagraphs(centered)
        assertEquals(SharedPdfRichTextAlign.LEFT, attrs[0].alignment)
        assertEquals(SharedPdfRichTextAlign.CENTER, attrs[1].alignment)
        // Toggle preserves alignment.
        val toggled = toggleRichParagraphList(centered, TextRange(5, 13), SharedPdfRichListType.BULLET)
        assertEquals("left\n• center me", toggled.text)
        assertEquals(SharedPdfRichTextAlign.CENTER, toggled.paragraphs[1].alignment)
    }

    @Test
    fun `ui state requires unanimous list type`() {
        val listed = toggleRichParagraphList(plain("a\nb"), TextRange(0, 3), SharedPdfRichListType.BULLET)
        val builder = AnnotatedString.Builder(listed.text)
        applyRichParagraphsToBuilder(builder, listed.text, listed.paragraphs)
        val annotated = builder.toAnnotatedString()
        val full = richParagraphUiState(annotated, TextRange(0, annotated.length))
        assertTrue(full.isBulleted)
        assertFalse(full.isNumbered)
        val partial = richParagraphUiState(annotated, TextRange(0, 1))
        assertTrue(partial.isBulleted)
    }

    @Test
    fun `global normalize renumbers and strips orphans`() {
        // Numbered run with wrong numbers + an orphan tag mid-paragraph
        // (managed marker debris is removed at its known offset).
        val broken = AnnotatedString.Builder("1. a\n5. b\nplain 3. x").apply {
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_NUMBERED, 0, 4)
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_NUMBERED, 5, 9)
            addStringAnnotation(SHARED_PDF_RICH_LIST_TAG, SHARED_PDF_RICH_LIST_BULLET, 16, 19)
        }.toAnnotatedString()
        val result = normalizeRichParagraphsGlobal(broken)
        assertEquals("1. a\n2. b\nplain x", result.text)
    }

    @Test
    fun `paragraph style round-trips through builder`() {
        val annotated = AnnotatedString.Builder("a\nb").apply {
            addStyle(ParagraphStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center), 2, 3)
        }.toAnnotatedString()
        val attrs = readRichParagraphs(annotated)
        assertEquals(SharedPdfRichTextAlign.LEFT, attrs[0].alignment)
        assertEquals(SharedPdfRichTextAlign.CENTER, attrs[1].alignment)
    }

    @Test
    fun `marker length matches digits`() {
        assertEquals(2, richMarkerLengthAt("• x", 0))
        assertEquals(3, richMarkerLengthAt("1. x", 0))
        assertEquals(4, richMarkerLengthAt("10. x", 0))
        assertEquals(0, richMarkerLengthAt("hello", 0))
        assertEquals(0, richMarkerLengthAt("1. x", 1))
    }

    @Test
    fun `span style import is live`() {
        val style = SpanStyle()
        assertTrue(style.toString().isNotEmpty())
    }

    private fun assertAnnotatedInBounds(tag: String, annotated: AnnotatedString) {
        val len = annotated.length
        annotated.spanStyles.forEach { range ->
            assertTrue(
                range.start in 0..len && range.end in range.start..len,
                "$tag: span ${range.start}..${range.end} beyond length $len in \"${annotated.text}\"",
            )
        }
        annotated.paragraphStyles.forEach { range ->
            assertTrue(
                range.start in 0..len && range.end in range.start..len,
                "$tag: paragraph ${range.start}..${range.end} beyond length $len",
            )
        }
        annotated.getStringAnnotations(0, len).forEach { annotation ->
            assertTrue(
                annotation.start in 0..len && annotation.end in annotation.start..len,
                "$tag: annotation ${annotation.start}..${annotation.end} beyond length $len",
            )
        }
    }

    @Test
    fun `controller list type enter flow keeps every range in bounds`() = runTest {
        val controller = SharedPdfRichTextController(
            scope = this,
            onDocumentChange = {},
            documentToAnnotatedString = { document, _ -> AnnotatedString(document.text) },
            annotatedStringToDocument = { text, _ -> SharedPdfRichDocument(text.text) },
        )
        fun check(tag: String) {
            assertAnnotatedInBounds(tag, controller.globalTextFieldValue.annotatedString)
        }
        controller.toggleRichListType(SharedPdfRichListType.NUMBERED)
        check("toggle")
        // Bold on: marker/typed spans now carry font attributes — the exact
        // setFontAttributes crash vector (unguarded setSpan in Compose).
        controller.updateCurrentStyle(
            androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )
        controller.onValueChanged(TextFieldValue("1. Hi", TextRange(5)))
        check("type")
        controller.onValueChanged(TextFieldValue("1. Hi\n", TextRange(6)))
        check("enter")
        controller.onValueChanged(TextFieldValue("1. Hi\n2. Yo", TextRange(11)))
        check("type2")
        controller.onValueChanged(TextFieldValue("1. Hi\n2. Yo\n", TextRange(12)))
        check("enter2")
        // Exit on empty item, then keep typing.
        controller.onValueChanged(TextFieldValue("1. Hi\n2. Yo\nplain", TextRange(17)))
        check("exit-type")
        controller.toggleRichListType(SharedPdfRichListType.BULLET)
        check("retoggle")
        controller.onValueChanged(TextFieldValue("hello", TextRange(0, 5)))
        check("select-all-replace")
    }

    @Test
    fun `serializer round-trips sparse paragraphs`() {
        val document = SharedPdfRichDocument(
            text = "plain\ncentered\n• item\n1. one",
            paragraphs = listOf(
                SharedPdfRichParagraph(),
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.BULLET),
                SharedPdfRichParagraph(
                    alignment = SharedPdfRichTextAlign.RIGHT,
                    listType = SharedPdfRichListType.NUMBERED,
                ),
            ),
        )
        val encoded = SharedPdfRichTextSerializer.encode(document)
        // Sparse: only non-default paragraphs stored.
        assertTrue(encoded.contains("\"paragraphs\""))
        val decoded = SharedPdfRichTextSerializer.decode(encoded)
        assertEquals(document, decoded)
    }

    @Test
    fun `serializer omits paragraphs when all default`() {
        val document = SharedPdfRichDocument(text = "plain text")
        val encoded = SharedPdfRichTextSerializer.encode(document)
        assertFalse(encoded.contains("paragraphs"))
        assertEquals(document, SharedPdfRichTextSerializer.decode(encoded))
    }

    @Test
    fun `legacy doc without paragraphs decodes to defaults`() {
        val decoded = SharedPdfRichTextSerializer.decode("{\"text\":\"hi\",\"spans\":[]}")
        assertEquals(SharedPdfRichDocument(text = "hi"), decoded)
    }

    @Test
    fun `mapper writes and reads paragraph attributes`() {
        val document = SharedPdfRichDocument(
            text = "a\nb\nc",
            paragraphs = listOf(
                SharedPdfRichParagraph(),
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.BULLET),
            ),
        )
        val annotated = SharedPdfRichTextMapper.toAnnotatedString(document, pageHeightPx = 1000f)
        assertEquals(
            listOf(
                SharedPdfRichParagraph(),
                SharedPdfRichParagraph(alignment = SharedPdfRichTextAlign.CENTER),
                SharedPdfRichParagraph(listType = SharedPdfRichListType.BULLET),
            ),
            readRichParagraphs(annotated).trimmedRichParagraphs(),
        )
        val roundTripped = SharedPdfRichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 1000f)
        assertEquals(document.paragraphs, roundTripped.paragraphs)
        assertEquals(document.text, roundTripped.text)
    }
}
