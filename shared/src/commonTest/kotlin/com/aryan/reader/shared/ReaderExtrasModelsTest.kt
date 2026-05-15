package com.aryan.reader.shared

import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.PaginatedReaderState
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderExtrasModelsTest {

    @Test
    fun `reader ai settings require BYO key and selected model`() {
        val missingModel = ReaderByokTextRequests.build(
            settings = ReaderAiByokSettings(groqKey = "gsk_test"),
            feature = ReaderAiFeature.DEFINE,
            text = "epistemic"
        )

        assertIs<ReaderByokTextRequestResult.MissingModel>(missingModel)

        val missingKey = ReaderByokTextRequests.build(
            settings = ReaderAiByokSettings(modelForAll = "groq:qwen/qwen3-32b"),
            feature = ReaderAiFeature.DEFINE,
            text = "epistemic"
        )

        assertIs<ReaderByokTextRequestResult.MissingKey>(missingKey)

        val ready = ReaderByokTextRequests.build(
            settings = ReaderAiByokSettings(
                groqKey = "gsk_test",
                modelForAll = "groq:qwen/qwen3-32b"
            ),
            feature = ReaderAiFeature.DEFINE,
            text = "epistemic"
        )

        assertIs<ReaderByokTextRequestResult.Ready>(ready)
    }

    @Test
    fun `cloud tts is available only with gemini key and cloud tts model`() {
        assertFalse(ReaderAiByokSettings(geminiKey = "key").isCloudTtsAvailable)
        assertFalse(ReaderAiByokSettings(ttsModel = GEMINI_CLOUD_TTS_MODEL_ID).isCloudTtsAvailable)

        assertTrue(
            ReaderAiByokSettings(
                geminiKey = "key",
                ttsModel = GEMINI_CLOUD_TTS_MODEL_ID
            ).isCloudTtsAvailable
        )
    }

    @Test
    fun `shared cloud tts voices mirror android voice catalog`() {
        assertEquals("Aoede", DEFAULT_CLOUD_TTS_SPEAKER_ID)
        assertTrue(ReaderCloudTtsVoices.size >= 30)
        assertEquals(ReaderCloudTtsVoices.map { it.id }, ReaderCloudTtsSpeakers)
        assertEquals("Breezy, Middle pitch", readerCloudTtsVoiceById("Aoede")?.description)
    }

    @Test
    fun `shared cloud tts chunking keeps android sentence behavior`() {
        val chunks = splitReaderTextIntoTtsChunks(
            "First sentence. Second sentence? Third sentence!",
            maxLength = 32
        )

        assertEquals(
            listOf("First sentence. Second sentence?", "Third sentence!"),
            chunks
        )
    }

    @Test
    fun `shared cloud tts cache summary formats current voice label`() {
        val empty = ReaderTtsCacheSummary()
        val populated = ReaderTtsCacheSummary(
            cachedChapterCount = 2,
            cachedChunkCount = 3,
            currentVoiceChunkCount = 2,
            totalSizeBytes = 4096,
            currentVoiceSizeBytes = 2048
        )

        assertEquals("No cached chunks for this voice", empty.currentVoiceLabel)
        assertEquals("2 chunks, 2.0 KB", populated.currentVoiceLabel)
        assertFalse(empty.hasCurrentVoiceCachedAudio)
        assertTrue(populated.hasCurrentVoiceCachedAudio)
    }

    @Test
    fun `hidden reader ai follows android availability logic`() {
        val visible = ReaderAiByokSettings(
            groqKey = "gsk_test",
            modelForAll = "groq:qwen/qwen3-32b"
        )
        val hidden = visible.copy(hideReaderAiFeatures = true)

        assertTrue(visible.areReaderAiFeaturesAvailable)
        assertFalse(hidden.areReaderAiFeaturesAvailable)
        assertIs<ReaderByokTextRequestResult.Hidden>(
            ReaderByokTextRequests.build(hidden, ReaderAiFeature.DEFINE, "epistemic")
        )
    }

    @Test
    fun `chapter summary context follows current chapter in pagination and vertical modes`() {
        val book = SharedEpubBook(
            id = "context",
            fileName = "context.epub",
            title = "Context",
            chapters = listOf(
                SharedEpubChapter("one", "One", "First chapter text"),
                SharedEpubChapter("two", "Two", "Second chapter text")
            )
        )
        val engine = ReaderEngine()
        val paginated = engine.createSession(book, settings = ReaderSettings(readingMode = ReaderReadingMode.PAGINATED))
            .reduce(ReaderAction.GoToChapter(1), engine)
        val vertical = engine.createSession(book, settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL))
            .reduce(ReaderAction.GoToChapter(1), engine)

        assertEquals("Second chapter text", ReaderContextExtractor.currentChapterText(paginated))
        assertEquals("Second chapter text", ReaderContextExtractor.currentChapterText(vertical))
    }

    @Test
    fun `tts planner follows android sentence chunking`() {
        val sentenceOne = "First " + "word ".repeat(20).trim() + "."
        val sentenceTwo = "Second " + "word ".repeat(20).trim() + "!"
        val sentenceThree = "Third " + "word ".repeat(20).trim() + "?"
        val text = listOf(sentenceOne, sentenceTwo, sentenceThree).joinToString(" ")
        val chunks = ReaderTtsPlanner.chunksForText(
            text = text,
            pageIndex = 4,
            chapterIndex = 2,
            chapterTitle = "Offsets",
            sourceStartOffset = 12
        )

        assertEquals(
            listOf(
                "$sentenceOne $sentenceTwo",
                sentenceThree
            ),
            chunks.map { it.text }
        )
        assertTrue(chunks.all { it.text.length <= READER_TTS_CHUNK_MAX_LENGTH })
        assertEquals(chunks.indices.toList(), chunks.map { it.index })
        assertEquals(12, chunks.first().startOffset)
        assertEquals(12 + text.trimEnd().length, chunks.last().endOffset)
        assertTrue(chunks.all { it.pageIndex == 4 && it.chapterIndex == 2 })
    }

    @Test
    fun `tts planner keeps android long sentence behavior`() {
        val text = "word ".repeat(80).trim()
        val chunks = ReaderTtsPlanner.chunksForText(
            text = text,
            pageIndex = 4,
            chapterIndex = 2,
            chapterTitle = "Offsets"
        )

        assertEquals(listOf(text), chunks.map { it.text })
    }

    @Test
    fun `tts planner can read page chapter or onward from current location`() {
        val book = SharedEpubBook(
            id = "tts",
            fileName = "tts.epub",
            title = "TTS",
            chapters = listOf(
                SharedEpubChapter("one", "One", "First page text."),
                SharedEpubChapter("two", "Two", "Second page text.")
            )
        )
        val session = ReaderEngine().createSession(book)

        assertEquals(listOf(0), ReaderTtsPlanner.chunksForCurrentPage(session).map { it.chapterIndex }.distinct())
        assertEquals(listOf(0), ReaderTtsPlanner.chunksForCurrentChapter(session).map { it.chapterIndex }.distinct())
        assertEquals(listOf(0, 1), ReaderTtsPlanner.chunksFromCurrentLocation(session).map { it.chapterIndex }.distinct())
    }

    @Test
    fun `tts planner starts onward reading at visible locator offset`() {
        val source = "First hidden sentence. Second visible sentence. Third visible sentence."
        val visibleOffset = source.indexOf("Second")
        val book = SharedEpubBook(
            id = "tts-visible",
            fileName = "tts-visible.epub",
            title = "TTS visible",
            chapters = listOf(SharedEpubChapter("one", "One", source))
        )
        val session = ReaderEngine().createSession(book).copy(
            navigationLocator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = visibleOffset,
                endOffset = visibleOffset,
                textQuote = "Second visible sentence."
            )
        )

        val chunks = ReaderTtsPlanner.chunksFromCurrentLocation(session)

        assertEquals(visibleOffset, chunks.first().startOffset)
        assertTrue(chunks.first().text.startsWith("Second visible sentence."))
        assertFalse(chunks.any { it.text.startsWith("First hidden") })
    }

    @Test
    fun `tts planner maps trimmed page text back to source offsets`() {
        val source = "Intro.\n\n   Leading words continue."
        val book = SharedEpubBook(
            id = "tts-offsets",
            fileName = "tts-offsets.epub",
            title = "TTS offsets",
            chapters = listOf(SharedEpubChapter("one", "One", source))
        )
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "Leading words continue.",
            startOffset = 8,
            endOffset = source.length
        )
        val session = ReaderSessionState(
            reader = PaginatedReaderState(
                book = book,
                pages = listOf(page),
                currentPageIndex = 0
            )
        )

        val chunk = ReaderTtsPlanner.chunksForCurrentPage(session).first()

        assertEquals(source.indexOf("Leading"), chunk.startOffset)
        assertEquals("Leading words continue.", source.substring(chunk.startOffset, chunk.endOffset))
    }

    @Test
    fun `tts planner prefers semantic source cfi chunks when available`() {
        val source = "First sentence. Second sentence."
        val semanticBlock = SemanticParagraph(
            text = source,
            spans = emptyList(),
            style = CssStyle(),
            elementId = null,
            cfi = "/4/2",
            startCharOffsetInSource = 5,
            blockIndex = 1
        )
        val book = SharedEpubBook(
            id = "tts-semantic",
            fileName = "tts-semantic.epub",
            title = "TTS semantic",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = source,
                    semanticBlocks = listOf(semanticBlock)
                )
            )
        )
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = source,
            startOffset = 0,
            endOffset = source.length + 5
        )
        val session = ReaderSessionState(
            reader = PaginatedReaderState(
                book = book,
                pages = listOf(page),
                currentPageIndex = 0
            )
        )

        val chunks = ReaderTtsPlanner.chunksForCurrentPage(session)

        assertEquals("/4/2", chunks.first().sourceCfi)
        assertEquals(5, chunks.first().startOffset)
        assertEquals("/4/2", chunks.first().toLocator().cfi)
    }

    @Test
    fun `external lookup urls encode selected text`() {
        assertEquals(
            "https://www.google.com/search?q=define+hello+world",
            externalLookupUrl(ReaderExternalLookupAction.DICTIONARY, "hello world")
        )
        assertEquals(
            "https://translate.google.com/?sl=auto&tl=en&text=hello+world&op=translate",
            externalLookupUrl(ReaderExternalLookupAction.TRANSLATE, "hello world")
        )
    }
}
