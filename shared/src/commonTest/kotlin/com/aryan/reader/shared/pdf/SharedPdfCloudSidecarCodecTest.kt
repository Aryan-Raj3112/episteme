package com.aryan.reader.shared.pdf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedPdfCloudSidecarCodecTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `state payload round trips with android compatible annotation fields`() {
        val annotation = SharedPdfAnnotation(
            id = "text-1",
            pageIndex = 1,
            kind = PdfAnnotationKind.TEXT,
            tool = PdfInkTool.TEXT,
            bounds = PdfPageBounds(0.1f, 0.2f, 0.6f, 0.4f),
            text = "iOS note",
            colorArgb = 0xFF112233.toInt(),
            pageRelativeFontSize = 0.025f,
            createdAt = 10L
        )
        val state = SharedPdfReaderState.initial(pageCount = 2, initialPageIndex = 1).copy(
            themeId = "sepia",
            reverseColorMode = PdfReverseColorMode.LIGHTNESS,
            preserveImageColors = true,
            annotations = listOf(annotation),
            blankPageInsertions = listOf(
                SharedPdfBlankPageInsertion(afterPdfIndex = 0, id = "blank-1")
            ),
            richTextDocumentJson = "{\"text\":\"hello\",\"spans\":[]}"
        )

        val encoded = SharedPdfCloudSidecarCodec.encode(
            bookId = "book-1",
            state = state,
            sourceFingerprint = "sha256:abc",
            modifiedTimestamp = 500L
        )
        val root = json.parseToJsonElement(encoded).jsonObject
        val decoded = assertNotNull(SharedPdfCloudSidecarCodec.decode(encoded))

        assertEquals(SharedPdfCloudSidecarCodec.CURRENT_VERSION, root["version"]?.toString()?.toInt())
        assertTrue(root.containsKey("ink"))
        assertTrue(root.containsKey("textBoxes"))
        assertTrue(root.containsKey(SharedPdfCloudSidecarCodec.KEY_READER_STATE))
        assertEquals("book-1", decoded.bookId)
        assertEquals("sha256:abc", decoded.sourceFingerprint)
        assertEquals(500L, decoded.modifiedTimestamp)
        assertEquals(state.themeId, decoded.readerState?.themeId)
        assertEquals(state.reverseColorMode, decoded.readerState?.reverseColorMode)
        assertEquals(state.preserveImageColors, decoded.readerState?.preserveImageColors)
        assertEquals(state.blankPageInsertions, decoded.readerState?.blankPageInsertions)
        assertEquals(listOf(annotation.sanitizedSharedPdfTextAnnotation()), decoded.annotations)
        assertEquals(
            json.parseToJsonElement(state.richTextDocumentJson),
            decoded.richTextDocumentJson?.let(json::parseToJsonElement)
        )
    }

    @Test
    fun `android bundle without reader state still restores annotations and rich text`() {
        val annotation = SharedPdfAnnotation(
            id = "ink-1",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            points = listOf(PdfPagePoint(0.1f, 0.2f), PdfPagePoint(0.2f, 0.3f)),
            colorArgb = 0xFF000000.toInt()
        )
        val payload = JsonObject(
            mapOf(
                "version" to JsonPrimitive(2),
                SharedPdfAnnotationSidecarCodec.KEY_PDF_ANNOTATIONS to
                    SharedPdfAnnotationSidecarCodec.encodeAnnotationsElement(listOf(annotation)),
                "text" to json.parseToJsonElement("{\"text\":\"remote\",\"spans\":[]}")
            )
        ).toString()

        val decoded = assertNotNull(SharedPdfCloudSidecarCodec.decode(payload))
        assertEquals(listOf(annotation), decoded.annotations)
        assertEquals(
            json.parseToJsonElement("{\"text\":\"remote\",\"spans\":[]}"),
            decoded.richTextDocumentJson?.let(json::parseToJsonElement)
        )
        assertEquals(null, decoded.readerState)
    }

    @Test
    fun `merge keeps concurrent annotations and chooses newer reader state`() {
        fun annotation(id: String, page: Int) = SharedPdfAnnotation(
            id = id,
            pageIndex = page,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.4f, 0.2f),
            text = id,
            colorArgb = 0x8CFFEB3B.toInt()
        )
        val localState = SharedPdfReaderState.initial(pageCount = 2).copy(
            pageIndex = 0,
            themeId = "light",
            annotations = listOf(annotation("local", 0))
        )
        val remoteState = SharedPdfReaderState.initial(pageCount = 2).copy(
            pageIndex = 1,
            themeId = "dark",
            annotations = listOf(annotation("remote", 1))
        )
        val local = SharedPdfCloudSidecarCodec.encode("book-1", localState, modifiedTimestamp = 100L)
        val remote = SharedPdfCloudSidecarCodec.encode("book-1", remoteState, modifiedTimestamp = 200L)

        val merged = SharedPdfCloudSidecarCodec.merge(
            localDataJson = local,
            remoteDataJson = remote,
            preferRemoteOnConflict = false
        )
        val decoded = assertNotNull(SharedPdfCloudSidecarCodec.decode(merged))

        assertEquals(1, decoded.readerState?.pageIndex)
        assertEquals("dark", decoded.readerState?.themeId)
        assertEquals(listOf("local", "remote"), decoded.annotations.map { it.id })
        assertEquals(listOf("local", "remote"), decoded.readerState?.annotations?.map { it.id })
    }

    @Test
    fun `merge preserves bookmarks when a legacy state omits the bookmarks field`() {
        val localState = SharedPdfReaderState.initial(pageCount = 10).copy(
            bookmarks = listOf(SharedPdfBookmark(pageIndex = 8, label = "Chapter"))
        )
        val local = SharedPdfCloudSidecarCodec.encode("book-1", localState, modifiedTimestamp = 100L)
        val remoteRoot = json.parseToJsonElement(
            SharedPdfCloudSidecarCodec.encode(
                "book-1",
                SharedPdfReaderState.initial(pageCount = 10).copy(pageIndex = 2),
                modifiedTimestamp = 200L
            )
        ).jsonObject
        val legacyRemoteState = JsonObject(
            remoteRoot.getValue(SharedPdfCloudSidecarCodec.KEY_READER_STATE).jsonObject
                .filterKeys { it != "bookmarks" }
        )
        val remote = JsonObject(
            remoteRoot + (SharedPdfCloudSidecarCodec.KEY_READER_STATE to legacyRemoteState)
        ).toString()

        val merged = SharedPdfCloudSidecarCodec.merge(local, remote, preferRemoteOnConflict = false)
        val decoded = assertNotNull(SharedPdfCloudSidecarCodec.decode(merged))

        assertEquals(listOf(8), decoded.readerState?.bookmarks?.map { it.pageIndex })
    }

    @Test
    fun `drive filename and compatibility match android transport`() {
        assertEquals("annotation_book-1.json", SharedPdfCloudSidecarCodec.driveFileName("book-1"))
        assertTrue(SharedPdfCloudSidecarCodec.isCompatiblePayload("{\"version\":2,\"ink\":[]}"))
        assertTrue(SharedPdfCloudSidecarCodec.isCompatiblePayload("{\"pdfReaderState\":{}}"))
    }
}
