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
    fun `reader ai one model setting matches Android model selection logic`() {
        val oneModel = ReaderByokTextRequests.build(
            settings = ReaderAiByokSettings(
                geminiKey = "gemini_test",
                groqKey = "gsk_test",
                useOneModel = true,
                modelForAll = "groq:qwen/qwen3-32b",
                defineModel = "gemini:gemini-flash-lite-latest"
            ),
            feature = ReaderAiFeature.DEFINE,
            text = "epistemic"
        )
        val perFeature = ReaderByokTextRequests.build(
            settings = ReaderAiByokSettings(
                geminiKey = "gemini_test",
                groqKey = "gsk_test",
                useOneModel = false,
                modelForAll = "groq:qwen/qwen3-32b",
                defineModel = "gemini:gemini-flash-lite-latest"
            ),
            feature = ReaderAiFeature.DEFINE,
            text = "epistemic"
        )

        assertEquals("groq:qwen/qwen3-32b", assertIs<ReaderByokTextRequestResult.Ready>(oneModel).request.model.id)
        assertEquals("gemini:gemini-flash-lite-latest", assertIs<ReaderByokTextRequestResult.Ready>(perFeature).request.model.id)
    }

    @Test
    fun `BYOK cloud tts is available only with gemini key and cloud tts model`() {
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
    fun `server backed reader AI and cloud tts availability do not require BYOK keys`() {
        val serverBacked = ReaderAiByokSettings(
            serverBackedReaderAiFeatures = true,
            serverBackedCloudTts = true
        )

        assertTrue(serverBacked.areReaderAiFeaturesAvailable)
        assertTrue(serverBacked.isCloudTtsAvailable)
        assertFalse(serverBacked.isByokCloudTtsAvailable)
        assertFalse(serverBacked.copy(hideReaderAiFeatures = true).areReaderAiFeaturesAvailable)
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
    fun `cloud tts overlay is visible only for active reader playback`() {
        assertFalse(readerCloudTtsControlsModel(ReaderCloudTtsState(isAvailable = true)).isVisible)
        assertTrue(readerCloudTtsControlsModel(ReaderCloudTtsState(isLoading = true)).isVisible)
        assertTrue(readerCloudTtsControlsModel(ReaderCloudTtsState(isPlaying = true)).isVisible)
        assertTrue(readerCloudTtsControlsModel(ReaderCloudTtsState(isPaused = true)).isVisible)
    }

    @Test
    fun `cloud tts overlay exposes chunk navigation only when a chunk can be skipped`() {
        val chunks = List(3) { index ->
            ReaderTtsChunk(
                index = index,
                pageIndex = index,
                chapterIndex = 0,
                chapterTitle = "Chapter",
                text = "Part ${index + 1}.",
                startOffset = index * 10,
                endOffset = index * 10 + 7
            )
        }

        val first = readerCloudTtsControlsModel(
            ReaderCloudTtsState(
                isPlaying = true,
                progress = ReaderTtsProgress(chunks = chunks, currentChunkIndex = 0)
            )
        )
        val middle = readerCloudTtsControlsModel(
            ReaderCloudTtsState(
                isPlaying = true,
                progress = ReaderTtsProgress(chunks = chunks, currentChunkIndex = 1)
            )
        )
        val loading = readerCloudTtsControlsModel(
            ReaderCloudTtsState(
                isLoading = true,
                progress = ReaderTtsProgress(chunks = chunks, currentChunkIndex = 1)
            )
        )

        assertFalse(first.canSkipPrevious)
        assertTrue(first.canSkipNext)
        assertTrue(first.canLocateCurrentChunk)
        assertTrue(middle.canSkipPrevious)
        assertTrue(middle.canSkipNext)
        assertFalse(loading.canSkipPrevious)
        assertFalse(loading.canSkipNext)
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
    fun `tts planner keeps synthetic desktop locators at android style chunk boundary`() {
        val visibleLine = "Gilberte's either noticing or suffering by his peculations. Tears came to my eyes."
        val source = "Hidden before this visual line. $visibleLine Later visible sentence."
        val visibleStart = source.indexOf(visibleLine)
        val book = SharedEpubBook(
            id = "tts-desktop-line",
            fileName = "tts-desktop-line.epub",
            title = "TTS desktop line",
            chapters = listOf(SharedEpubChapter("one", "One", source))
        )
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = source,
            startOffset = 0,
            endOffset = source.length
        )
        val session = ReaderSessionState(
            reader = PaginatedReaderState(
                book = book,
                pages = listOf(page),
                currentPageIndex = 0
            ),
            navigationLocator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = visibleStart,
                endOffset = visibleStart,
                textQuote = visibleLine,
                cfi = "desktop:0:$visibleStart:$visibleStart"
            )
        )

        val first = ReaderTtsPlanner.chunksFromCurrentLocation(session).first()

        assertEquals(visibleStart, first.startOffset)
        assertTrue(first.text.startsWith(visibleLine))
        assertFalse(first.text.startsWith("Hidden before"))
    }

    @Test
    fun `tts planner trims onward chunks with source offsets after sentence gaps`() {
        val source = "First hidden sentence.\n\nSecond visible sentence starts on the top line."
        val visibleOffset = source.indexOf("Second")
        val book = SharedEpubBook(
            id = "tts-visible-gap",
            fileName = "tts-visible-gap.epub",
            title = "TTS visible gap",
            chapters = listOf(SharedEpubChapter("one", "One", source))
        )
        val session = ReaderEngine().createSession(book).copy(
            navigationLocator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = visibleOffset,
                endOffset = visibleOffset,
                textQuote = "Second visible sentence starts on the top line."
            )
        )

        val first = ReaderTtsPlanner.chunksFromCurrentLocation(session).first()

        assertEquals(visibleOffset, first.startOffset)
        assertTrue(first.text.startsWith("Second visible sentence starts"))
        assertFalse(first.text.startsWith("cond visible"))
    }

    @Test
    fun `tts planner matches android source cfi before slicing initial chunk`() {
        val hidden = "Hidden block text that should never be trimmed into."
        val visible = "Visible line starts here and should be spoken."
        val visibleOffset = 20
        val hiddenBlock = SemanticParagraph(
            text = hidden,
            spans = emptyList(),
            style = CssStyle(),
            elementId = null,
            cfi = "/4/2",
            startCharOffsetInSource = 0,
            blockIndex = 0
        )
        val visibleBlock = SemanticParagraph(
            text = visible,
            spans = emptyList(),
            style = CssStyle(),
            elementId = null,
            cfi = "/4/4",
            startCharOffsetInSource = visibleOffset,
            blockIndex = 1
        )
        val book = SharedEpubBook(
            id = "tts-cfi-match",
            fileName = "tts-cfi-match.epub",
            title = "TTS CFI match",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = "$hidden\n$visible",
                    semanticBlocks = listOf(hiddenBlock, visibleBlock)
                )
            )
        )
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "$hidden\n$visible",
            startOffset = 0,
            endOffset = hidden.length + visible.length + visibleOffset
        )
        val session = ReaderSessionState(
            reader = PaginatedReaderState(
                book = book,
                pages = listOf(page),
                currentPageIndex = 0
            ),
            navigationLocator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = visibleOffset,
                endOffset = visibleOffset,
                textQuote = visible,
                cfi = "/4/4:0"
            )
        )

        val first = ReaderTtsPlanner.chunksFromCurrentLocation(session).first()

        assertEquals("/4/4", first.sourceCfi)
        assertTrue(first.text.startsWith("Visible line starts here"))
        assertFalse(first.text.contains("Hidden block"))
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
