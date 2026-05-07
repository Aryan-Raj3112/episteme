package com.aryan.reader.shared.pdf

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedPdfAnnotationSerializerTest {

    @Test
    fun `serializer round trips text highlight annotations`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight",
            pageIndex = 3,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = PdfPageBounds(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.24f),
            text = "Selected text",
            colorArgb = 0x8CFFEB3B.toInt(),
            createdAt = 42L
        )

        val decoded = SharedPdfAnnotationSerializer.decode(
            SharedPdfAnnotationSerializer.encode(listOf(annotation))
        )

        assertEquals(listOf(annotation), decoded)
    }

    @Test
    fun `sidecar codec canonicalizes legacy android annotation payloads`() {
        val legacyPayload = """
            {
              "ink": [
                {
                  "pageIndex": 1,
                  "annotationType": "INK",
                  "inkType": "PENCIL",
                  "color": -16777216,
                  "strokeWidth": 0.008,
                  "points": [{"x":0.1,"y":0.2,"t":10},{"x":0.3,"y":0.4,"t":12}]
                }
              ],
              "textBoxes": [
                {
                  "id": "box-1",
                  "pageIndex": 2,
                  "text": "Typed note",
                  "color": -15654349,
                  "backgroundColor": 1712398870,
                  "fontSize": 0.032,
                  "isBold": true,
                  "bounds": {"left":0.1,"top":0.2,"right":0.5,"bottom":0.3}
                }
              ],
              "highlights": [
                {
                  "id": "highlight-1",
                  "pageIndex": 3,
                  "color": "BLUE",
                  "text": "Selected text",
                  "rangeStart": 4,
                  "rangeEnd": 18,
                  "note": "Keep this",
                  "bounds": []
                }
              ]
            }
        """.trimIndent()

        val canonical = SharedPdfAnnotationSidecarCodec.canonicalizeDataJson(legacyPayload)
        val data = testJson.parseToJsonElement(canonical).jsonObject
        val annotations = SharedPdfAnnotationSidecarCodec.annotationsFromData(data)

        assertNotNull(data[SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS])
        assertEquals(listOf(PdfAnnotationKind.INK, PdfAnnotationKind.TEXT, PdfAnnotationKind.HIGHLIGHT), annotations.map { it.kind })
        assertEquals(PdfInkTool.PENCIL, annotations[0].tool)
        assertEquals(16f, annotations[1].fontSize, 0.001f)
        assertTrue(annotations[1].isBold)
        assertEquals("Keep this", annotations[2].note)
        assertEquals(4, annotations[2].rangeStartIndex)
        assertEquals(17, annotations[2].rangeEndIndex)
    }

    @Test
    fun `sidecar codec expands canonical annotations for android legacy readers`() {
        val annotations = listOf(
            SharedPdfAnnotation(
                id = "ink-1",
                pageIndex = 0,
                kind = PdfAnnotationKind.INK,
                tool = PdfInkTool.FOUNTAIN_PEN,
                points = listOf(PdfPagePoint(0.1f, 0.2f, 1L), PdfPagePoint(0.2f, 0.3f, 2L)),
                colorArgb = 0xFF0000FF.toInt(),
                strokeWidth = 0.009f
            ),
            SharedPdfAnnotation(
                id = "text-1",
                pageIndex = 1,
                kind = PdfAnnotationKind.TEXT,
                tool = PdfInkTool.TEXT,
                bounds = PdfPageBounds(0.2f, 0.3f, 0.6f, 0.5f),
                text = "Desktop text",
                colorArgb = 0xFF112233.toInt(),
                backgroundArgb = 0x66112233,
                fontSize = 20f
            ),
            SharedPdfAnnotation(
                id = "highlight-1",
                pageIndex = 2,
                kind = PdfAnnotationKind.HIGHLIGHT,
                tool = PdfInkTool.HIGHLIGHTER,
                text = "Desktop highlight",
                note = "Synced note",
                colorArgb = 0x8C64B5F6.toInt(),
                rangeStartIndex = 7,
                rangeEndIndex = 21
            )
        )
        val canonicalPayload = testJson.encodeToString(
            JsonElement.serializer(),
            JsonObject(
                mapOf(
                    SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS to
                        SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(annotations)
                )
            )
        )

        val legacyPayload = SharedPdfAnnotationSidecarCodec.legacyAndroidDataJsonFromCanonical(canonicalPayload)
        val legacy = testJson.parseToJsonElement(legacyPayload).jsonObject

        assertEquals(1, legacy.getValue("ink").jsonArray.size)
        assertEquals("FOUNTAIN_PEN", legacy.getValue("ink").jsonArray[0].jsonObject.getValue("inkType").jsonPrimitive.content)
        assertEquals(1, legacy.getValue("textBoxes").jsonArray.size)
        assertEquals(
            0.04,
            legacy.getValue("textBoxes").jsonArray[0].jsonObject.getValue("fontSize").jsonPrimitive.content.toDouble(),
            0.0001
        )
        assertEquals(1, legacy.getValue("highlights").jsonArray.size)
        assertEquals("Synced note", legacy.getValue("highlights").jsonArray[0].jsonObject.getValue("note").jsonPrimitive.content)
        assertEquals(22, legacy.getValue("highlights").jsonArray[0].jsonObject.getValue("rangeEnd").jsonPrimitive.content.toInt())
    }

    @Test
    fun `embedded annotation threads link replies and nearby orphan comments`() {
        val root = embeddedAnnotation(
            id = "root",
            index = 0,
            contents = "Root comment",
            name = "root-name",
            bounds = PdfPageBounds(0.1f, 0.1f, 0.2f, 0.2f)
        )
        val reply = embeddedAnnotation(
            id = "reply",
            index = 1,
            contents = "Reply comment",
            name = "reply-name",
            inReplyTo = "root-name",
            bounds = PdfPageBounds(0.11f, 0.11f, 0.21f, 0.21f)
        )
        val nearbyOrphan = embeddedAnnotation(
            id = "nearby",
            index = 2,
            contents = "Nearby comment",
            name = "nearby-name",
            bounds = PdfPageBounds(0.12f, 0.12f, 0.22f, 0.22f)
        )
        val empty = embeddedAnnotation(
            id = "empty",
            index = 3,
            contents = "",
            name = "empty-name",
            bounds = PdfPageBounds(0.8f, 0.8f, 0.9f, 0.9f)
        )

        val grouped = SharedPdfEmbeddedAnnotationThreads.group(listOf(root, reply, nearbyOrphan, empty))

        assertEquals(listOf("root"), grouped.map { it.id })
        assertEquals(listOf("reply", "nearby"), grouped.single().replies.map { it.id })
    }

    private fun embeddedAnnotation(
        id: String,
        index: Int,
        contents: String,
        name: String,
        bounds: PdfPageBounds,
        inReplyTo: String = ""
    ): SharedPdfEmbeddedAnnotation {
        return SharedPdfEmbeddedAnnotation(
            id = id,
            pageIndex = 0,
            index = index,
            subtype = PdfiumAnnotationSubtype.TEXT,
            bounds = bounds,
            contents = contents,
            author = "Reader",
            name = name,
            inReplyTo = inReplyTo
        )
    }

    private val testJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
