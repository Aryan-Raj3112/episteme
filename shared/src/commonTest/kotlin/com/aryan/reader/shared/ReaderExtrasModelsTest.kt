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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderExtrasModelsTest {

    @Test
    fun `epub auto scroll uses android effective speed contract`() {
        assertEquals(3f, readerAutoScrollPixelsPerSecond(0f))
        assertEquals(24f, readerAutoScrollPixelsPerSecond(0.8f))
        assertEquals(300f, readerAutoScrollPixelsPerSecond(20f))
    }

    @Test
    fun `epub auto scroll profile applies android bound correction`() {
        assertEquals(
            ReaderAutoScrollProfile(speed = 4f, minSpeed = 4f, maxSpeed = 4f),
            ReaderAutoScrollProfile(speed = 0.8f, minSpeed = 4f, maxSpeed = 2f).sanitized(),
        )
        assertEquals(
            ReaderAutoScrollProfile(speed = 5f, minSpeed = 5f, maxSpeed = 5f),
            ReaderAutoScrollProfile(speed = 2f, minSpeed = 1f, maxSpeed = 5f).withMinSpeed(5f),
        )
        assertEquals(
            ReaderAutoScrollProfile(speed = 1f, minSpeed = 1f, maxSpeed = 1f),
            ReaderAutoScrollProfile(speed = 4f, minSpeed = 1f, maxSpeed = 5f).withMaxSpeed(1f),
        )
    }

    @Test
    fun `legacy ios epub pixel speeds migrate to android multipliers`() {
        assertEquals(0.6f, migrateLegacyIosReaderAutoScrollSpeed(36f))
        assertEquals(0.8f, migrateLegacyIosReaderAutoScrollSpeed(0.8f))
    }

    @Test
    fun `epub musician gestures match android targets and pause timing`() {
        assertEquals(
            ReaderMusicianGesturePlan(ReaderMusicianNavigationTarget.RELATIVE, -0.75f, 600L),
            planReaderMusicianGesture(isRightRegion = false, isLongPress = false),
        )
        assertEquals(
            ReaderMusicianGesturePlan(ReaderMusicianNavigationTarget.RELATIVE, 0.75f, 600L),
            planReaderMusicianGesture(isRightRegion = true, isLongPress = false),
        )
        assertEquals(
            ReaderMusicianGesturePlan(ReaderMusicianNavigationTarget.START, 0f, 1_000L),
            planReaderMusicianGesture(isRightRegion = false, isLongPress = true),
        )
        assertEquals(
            ReaderMusicianGesturePlan(ReaderMusicianNavigationTarget.END, 0f, 1_000L),
            planReaderMusicianGesture(isRightRegion = true, isLongPress = true),
        )
    }

    @Test
    fun `epub search ime request bypasses android debounce once`() {
        assertEquals(350L, readerSearchDelayMillis(requestId = 4L, immediateRequestId = 3L))
        assertEquals(0L, readerSearchDelayMillis(requestId = 4L, immediateRequestId = 4L))
        assertEquals(350L, readerSearchDelayMillis(requestId = 5L, immediateRequestId = 4L))
    }

    @Test
    fun `epub auto scroll continues between chapters and stops at book end`() {
        assertEquals(ReaderAutoScrollBoundaryAction.NEXT_CHAPTER, readerAutoScrollBoundaryAction(0, 2))
        assertEquals(ReaderAutoScrollBoundaryAction.STOP, readerAutoScrollBoundaryAction(1, 2))
        assertEquals(ReaderAutoScrollBoundaryAction.STOP, readerAutoScrollBoundaryAction(0, 0))
    }

    @Test
    fun `selection lookup ids map to android external actions`() {
        assertEquals(ReaderExternalLookupAction.DICTIONARY, readerExternalLookupActionForSelectionId("dictionary"))
        assertEquals(ReaderExternalLookupAction.TRANSLATE, readerExternalLookupActionForSelectionId("translate"))
        assertEquals(ReaderExternalLookupAction.SEARCH, readerExternalLookupActionForSelectionId("web-search"))
        assertEquals(ReaderExternalLookupAction.SEARCH, readerExternalLookupActionForSelectionId("search"))
        assertNull(readerExternalLookupActionForSelectionId("define"))
    }

    @Test
    fun `external lookup actions use android selection length boundary`() {
        assertTrue(readerExternalLookupActionsAvailable(0))
        assertTrue(readerExternalLookupActionsAvailable(ReaderExternalLookupSelectionLimit))
        assertFalse(readerExternalLookupActionsAvailable(ReaderExternalLookupSelectionLimit + 1))
        assertFalse(readerExternalLookupActionsAvailable(-1))
    }

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
    fun `fish voice list cache freshness honors the ttl`() {
        assertTrue(isFishVoiceListCacheFresh(fetchedAtMs = 1000L, nowMs = 1000L + FISH_VOICE_LIST_CACHE_TTL_MS))
        assertFalse(isFishVoiceListCacheFresh(fetchedAtMs = 1000L, nowMs = 1000L + FISH_VOICE_LIST_CACHE_TTL_MS + 1L))
        // Future timestamps (clock skew) count as fresh rather than
        // triggering a refetch storm.
        assertTrue(isFishVoiceListCacheFresh(fetchedAtMs = 2000L, nowMs = 1000L))
    }

    @Test
    fun `hasByokModel is true only with known model and matching provider key`() {
        assertFalse(ReaderAiByokSettings().hasByokModel(ReaderAiFeature.DEFINE))
        assertFalse(
            ReaderAiByokSettings(groqKey = "gsk_test").hasByokModel(ReaderAiFeature.DEFINE)
        )
        assertFalse(
            ReaderAiByokSettings(modelForAll = "groq:qwen/qwen3-32b").hasByokModel(ReaderAiFeature.DEFINE)
        )
        assertFalse(
            ReaderAiByokSettings(
                geminiKey = "gemini_test",
                modelForAll = "groq:qwen/qwen3-32b"
            ).hasByokModel(ReaderAiFeature.DEFINE)
        )
        assertTrue(
            ReaderAiByokSettings(
                groqKey = "gsk_test",
                modelForAll = "groq:qwen/qwen3-32b"
            ).hasByokModel(ReaderAiFeature.DEFINE)
        )
        // Per-feature selection is honored when "one model" is off.
        assertFalse(
            ReaderAiByokSettings(
                useOneModel = false,
                groqKey = "gsk_test",
                modelForAll = "groq:qwen/qwen3-32b"
            ).hasByokModel(ReaderAiFeature.SUMMARIZE)
        )
        assertTrue(
            ReaderAiByokSettings(
                useOneModel = false,
                geminiKey = "gemini_test",
                summarizeModel = "gemini:gemini-flash-lite-latest"
            ).hasByokModel(ReaderAiFeature.SUMMARIZE)
        )
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
    fun `proper gemini tts models are available with gemini key`() {
        assertFalse(ReaderAiByokSettings(geminiKey = "key", ttsModel = GEMINI_TTS_MODEL_LITE_ID).isByokCloudTtsAvailable)
        assertTrue(ReaderAiByokSettings(geminiKey = "key", ttsModel = GEMINI_TTS_MODEL_LITE_ID).isGeminiRestByokTtsAvailable)
        assertTrue(ReaderAiByokSettings(geminiKey = "key", ttsModel = GEMINI_TTS_MODEL_PREVIEW_ID).isGeminiRestByokTtsAvailable)
        assertTrue(ReaderAiByokSettings(geminiKey = "key", ttsModel = GEMINI_TTS_MODEL_LITE_ID).isAnyByokTtsAvailable)
        assertEquals("gemini", ReaderAiByokSettings(ttsModel = GEMINI_TTS_MODEL_LITE_ID).ttsProvider)
    }

    @Test
    fun `fish byok tts is available with fish key and fish model`() {
        assertFalse(ReaderAiByokSettings(fishKey = "key").isAnyByokTtsAvailable)
        assertFalse(ReaderAiByokSettings(ttsModel = FISH_TTS_MODEL_ID).isAnyByokTtsAvailable)
        assertFalse(ReaderAiByokSettings(geminiKey = "key", ttsModel = FISH_TTS_MODEL_ID).isFishByokTtsAvailable)

        val settings = ReaderAiByokSettings(fishKey = "key", ttsModel = FISH_TTS_MODEL_ID)
        assertTrue(settings.isFishByokTtsAvailable)
        assertTrue(settings.isAnyByokTtsAvailable)
        assertTrue(settings.isCloudTtsAvailable)
        assertTrue(settings.hasAnyAiKey)
        assertEquals("key", settings.apiKeyFor("fish"))
        assertEquals("fish", settings.ttsProvider)
    }

    @Test
    fun `tts model sanitization keeps proper and legacy models`() {
        assertEquals(
            GEMINI_TTS_MODEL_LITE_ID,
            ReaderAiByokSettings(ttsModel = GEMINI_TTS_MODEL_LITE_ID).sanitized().ttsModel
        )
        assertEquals(
            FISH_TTS_MODEL_ID,
            ReaderAiByokSettings(ttsModel = FISH_TTS_MODEL_ID).sanitized().ttsModel
        )
        assertEquals(
            GEMINI_CLOUD_TTS_MODEL_ID,
            ReaderAiByokSettings(ttsModel = GEMINI_CLOUD_TTS_MODEL_ID).sanitized().ttsModel
        )
        assertEquals(
            "",
            ReaderAiByokSettings(ttsModel = "gemini:unknown-model").sanitized().ttsModel
        )
    }

    @Test
    fun `tts byok options cover both gemini models and fish`() {
        val ids = ReaderTtsByokOptions.map { it.id }.toSet()
        assertTrue(ids.contains(GEMINI_TTS_MODEL_LITE_ID))
        assertTrue(ids.contains(GEMINI_TTS_MODEL_PREVIEW_ID))
        assertTrue(ids.contains(FISH_TTS_MODEL_ID))
        assertTrue(ReaderTtsByokOptions.all { it.priceLabel == null })
    }

    @Test
    fun `cache speaker parsing supports fish mp3 chunks`() {
        assertEquals("fish-voice-a", readerTtsCacheSpeakerId("cached_chunk_fish-voice-a_ab12cd34ef56.mp3"))
        assertEquals("Aoede", readerTtsCacheSpeakerId("cached_chunk_Aoede_ab12cd34ef56.wav"))
        assertNull(readerTtsCacheSpeakerId("cached_chunk_Aoede_ab12cd34ef56.ogg"))
    }

    @Test
    fun `micros wallet formats as usd`() {
        assertEquals("$10.00", formatMicrosUsd(10_000_000L))
        assertEquals("$5.25", formatMicrosUsd(5_250_000L))
        assertEquals("$0.04", formatMicrosUsd(41_670L))
        assertEquals("$0.00", formatMicrosUsd(7_500L))
        assertEquals("$0.00", formatMicrosUsd(0L))
    }

    @Test
    fun `spendable balance covers legacy credits and usd wallet`() {
        assertTrue(hasSpendableBalance(10, 0L))
        assertTrue(hasSpendableBalance(0, 5_000L))
        assertFalse(hasSpendableBalance(0, 0L))
        assertEquals("⭐ 10", spendableDisplayText(10, 0L, false))
        assertEquals("$5.00", spendableDisplayText(0, 5_000_000L, true))
    }

    @Test
    fun `cloud tts mode and voice settings keep canonical android ids`() {
        val settings = ReaderAiByokSettings(
            ttsModel = GEMINI_CLOUD_TTS_MODEL_ID,
            ttsSpeakerId = "Kore",
        ).sanitized()

        assertEquals(GEMINI_CLOUD_TTS_MODEL_ID, settings.ttsModel)
        assertEquals("Kore", settings.ttsSpeakerId)
        assertFalse(settings.isByokCloudTtsAvailable)
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
    fun `tts planner reads one chapter at a time for chaining`() {
        val book = SharedEpubBook(
            id = "tts-chained",
            fileName = "tts-chained.epub",
            title = "TTS chained",
            chapters = listOf(
                SharedEpubChapter("one", "One", "First chapter text."),
                SharedEpubChapter("two", "Two", "Second chapter text.")
            )
        )
        val session = ReaderEngine().createSession(book)

        assertEquals(listOf(0), ReaderTtsPlanner.chunksForChapterFromLocation(session, 0).map { it.chapterIndex }.distinct())
        assertEquals(listOf(1), ReaderTtsPlanner.chunksForChapterFromLocation(session, 1).map { it.chapterIndex }.distinct())
        assertTrue(ReaderTtsPlanner.chunksForChapterFromLocation(session, 2).isEmpty())
        assertTrue(ReaderTtsPlanner.chunksForChapterFromLocation(session, -1).isEmpty())
    }

    @Test
    fun `tts planner slices chapter head at visible locator offset`() {
        val source = "First hidden sentence. Second visible sentence. Third visible sentence."
        val visibleOffset = source.indexOf("Second")
        val book = SharedEpubBook(
            id = "tts-chain-visible",
            fileName = "tts-chain-visible.epub",
            title = "TTS chain visible",
            chapters = listOf(
                SharedEpubChapter("zero", "Zero", "Earlier chapter text."),
                SharedEpubChapter("one", "One", source)
            )
        )
        val session = ReaderEngine().createSession(book).copy(
            navigationLocator = ReaderLocator(
                chapterIndex = 1,
                pageIndex = 1,
                startOffset = visibleOffset,
                endOffset = visibleOffset,
                textQuote = "Second visible sentence."
            )
        )

        val chunks = ReaderTtsPlanner.chunksForChapterFromLocation(session, 1)

        assertEquals(listOf(1), chunks.map { it.chapterIndex }.distinct())
        assertTrue(chunks.first().text.startsWith("Second visible sentence."))
        assertFalse(chunks.any { it.text.startsWith("First hidden") })
        assertFalse(chunks.any { it.text.startsWith("Earlier chapter") })
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

    @Test
    fun `external lookup urls honor configured services`() {
        assertEquals(
            "https://translate.google.com/?sl=auto&tl=en&text=hello&op=translate",
            externalLookupUrl(ReaderExternalLookupAction.TRANSLATE, "hello", ReaderExternalLookupService.GOOGLE_TRANSLATE)
        )
        assertEquals(
            "https://www.bing.com/translator/?text=hello",
            externalLookupUrl(ReaderExternalLookupAction.TRANSLATE, "hello", ReaderExternalLookupService.BING)
        )
        assertEquals(
            "https://duckduckgo.com/?q=hello",
            externalLookupUrl(ReaderExternalLookupAction.SEARCH, "hello", ReaderExternalLookupService.DUCKDUCKGO)
        )
        assertEquals(
            "https://www.bing.com/search?q=hello",
            externalLookupUrl(ReaderExternalLookupAction.SEARCH, "hello", ReaderExternalLookupService.BING)
        )
        assertEquals(
            "https://www.google.com/search?q=define+hello",
            externalLookupUrl(ReaderExternalLookupAction.DICTIONARY, "hello", ReaderExternalLookupService.GOOGLE)
        )
        assertEquals(
            "https://www.google.com/search?q=hello",
            externalLookupUrl(ReaderExternalLookupAction.SEARCH, "hello", ReaderExternalLookupService.GOOGLE)
        )
    }

    @Test
    fun `external lookup service ids round trip`() {
        assertEquals(ReaderExternalLookupService.SYSTEM, ReaderExternalLookupService.fromId("system"))
        assertEquals(ReaderExternalLookupService.GOOGLE, ReaderExternalLookupService.fromId("google"))
        assertEquals(ReaderExternalLookupService.GOOGLE_TRANSLATE, ReaderExternalLookupService.fromId("google_translate"))
        assertEquals(ReaderExternalLookupService.DUCKDUCKGO, ReaderExternalLookupService.fromId("DUCKDUCKGO"))
        assertEquals(ReaderExternalLookupService.BING, ReaderExternalLookupService.fromId("bing"))
        assertEquals(ReaderExternalLookupService.SAFARI, ReaderExternalLookupService.fromId("safari"))
        assertEquals(ReaderExternalLookupService.GOOGLE_TRANSLATE_APP, ReaderExternalLookupService.fromId("google_translate_app"))
        assertEquals(ReaderExternalLookupService.ITRANSLATE_APP, ReaderExternalLookupService.fromId("itranslate_app"))
        assertEquals(ReaderExternalLookupService.SYSTEM, ReaderExternalLookupService.fromId(null))
        assertEquals(ReaderExternalLookupService.SYSTEM, ReaderExternalLookupService.fromId("unknown"))
    }

    @Test
    fun `dictionary service options exclude system for translate and search`() {
        // Android benchmark (Smart AI default): the dictionary offers Smart
        // AI first, then the explicit "choose each time" entry, then browser.
        assertEquals(3, ReaderDictionaryServiceOptions.size)
        assertEquals(1, ReaderTranslateServiceOptions.size)
        assertEquals(1, ReaderSearchServiceOptions.size)
        // Android parity: every action can hand off to the user's installed apps,
        // and the dictionary keeps the Smart AI route first.
        assertEquals(ReaderExternalLookupService.AI, ReaderDictionaryServiceOptions.first())
        assertEquals(ReaderExternalLookupService.SAFARI, ReaderTranslateServiceOptions.first())
        assertEquals(ReaderExternalLookupService.SAFARI, ReaderSearchServiceOptions.first())
        assertTrue(ReaderDictionaryServiceOptions.contains(ReaderExternalLookupService.ANY_APP))
        assertTrue(ReaderDictionaryServiceOptions.contains(ReaderExternalLookupService.SAFARI))
    }

    @Test
    fun `smart ai is the default dictionary engine`() {
        // Android benchmark (PdfPreferences/EpubReaderPreferences.loadUseOnlineDict):
        // with nothing persisted the Dict action routes to the in-app AI
        // definition, which is also what drives the Pro upsell for free accounts.
        assertEquals(ReaderExternalLookupService.AI, ReaderDefaultDictionaryLookupService)
    }

    @Test
    fun `safari urls match the default engine per action`() {
        assertEquals(
            "https://www.google.com/search?q=define+hello",
            externalLookupUrl(ReaderExternalLookupAction.DICTIONARY, "hello", ReaderExternalLookupService.SAFARI)
        )
        assertEquals(
            "https://translate.google.com/?sl=auto&tl=en&text=hello&op=translate",
            externalLookupUrl(ReaderExternalLookupAction.TRANSLATE, "hello", ReaderExternalLookupService.SAFARI)
        )
        assertEquals(
            "https://www.google.com/search?q=hello",
            externalLookupUrl(ReaderExternalLookupAction.SEARCH, "hello", ReaderExternalLookupService.SAFARI)
        )
    }

    @Test
    fun `installed app urls deep link with encoded text`() {
        assertEquals(
            "googletranslate://?sl=auto&tl=en&text=hello+world",
            readerExternalLookupAppUrl(
                ReaderExternalLookupService.GOOGLE_TRANSLATE_APP,
                ReaderExternalLookupAction.TRANSLATE,
                "hello world"
            )
        )
        assertEquals(
            "itranslate://translate?from=auto&to=en&text=bonjour",
            readerExternalLookupAppUrl(
                ReaderExternalLookupService.ITRANSLATE_APP,
                ReaderExternalLookupAction.TRANSLATE,
                "bonjour"
            )
        )
        // No app mapping for search/define actions or plain web services.
        assertNull(
            readerExternalLookupAppUrl(
                ReaderExternalLookupService.GOOGLE_TRANSLATE_APP,
                ReaderExternalLookupAction.SEARCH,
                "hello"
            )
        )
        assertNull(
            readerExternalLookupAppUrl(
                ReaderExternalLookupService.GOOGLE,
                ReaderExternalLookupAction.TRANSLATE,
                "hello"
            )
        )
        assertEquals("", externalLookupUrl(ReaderExternalLookupAction.TRANSLATE, "hello", ReaderExternalLookupService.GOOGLE_TRANSLATE_APP))
    }

    @Test
    fun `visible lookup options gate installed apps but keep selection`() {
        val options = listOf(
            ReaderExternalLookupService.SAFARI,
            ReaderExternalLookupService.GOOGLE_TRANSLATE,
            ReaderExternalLookupService.GOOGLE_TRANSLATE_APP,
            ReaderExternalLookupService.ITRANSLATE_APP,
            ReaderExternalLookupService.ANY_APP,
        )
        // Nothing installed: scheme-gated apps hidden, web/share stay.
        val bare = visibleReaderLookupOptions(
            options,
            ReaderExternalLookupService.SAFARI,
            emptySet()
        )
        assertTrue(bare.contains(ReaderExternalLookupService.SAFARI))
        assertTrue(bare.contains(ReaderExternalLookupService.GOOGLE_TRANSLATE))
        assertTrue(bare.contains(ReaderExternalLookupService.ANY_APP))
        assertEquals(false, bare.contains(ReaderExternalLookupService.GOOGLE_TRANSLATE_APP))
        assertEquals(false, bare.contains(ReaderExternalLookupService.ITRANSLATE_APP))
        // Installed: gated entries appear.
        val installed = visibleReaderLookupOptions(
            options,
            ReaderExternalLookupService.SAFARI,
            setOf("googletranslate")
        )
        assertTrue(installed.contains(ReaderExternalLookupService.GOOGLE_TRANSLATE_APP))
        assertEquals(false, installed.contains(ReaderExternalLookupService.ITRANSLATE_APP))
        // Stale pick (app since uninstalled) stays selectable so it can be changed.
        val stale = visibleReaderLookupOptions(
            options,
            ReaderExternalLookupService.ITRANSLATE_APP,
            emptySet()
        )
        assertTrue(stale.contains(ReaderExternalLookupService.ITRANSLATE_APP))
    }

    @Test
    fun `tts cache speaker parsed from chunk file name`() {
        assertEquals("Aoede", readerTtsCacheSpeakerId("cached_chunk_Aoede_a1b2c3d4e5f60718.wav"))
        assertNull(readerTtsCacheSpeakerId("voice_sample_Aoede.wav"))
        assertNull(readerTtsCacheSpeakerId("chapter_notes.txt"))
        assertNull(readerTtsCacheSpeakerId("cached_chunk_.wav"))
    }

    @Test
    fun `tts cache segment label drops digest and restores spaces`() {
        assertEquals("Chapter 1", readerTtsCacheDisplayLabel("Chapter_1_a1b2c3d4e5f60718"))
        assertEquals("Pride and Prejudice", readerTtsCacheDisplayLabel("Pride_and_Prejudice_0123456789abcdef"))
        assertEquals("plain", readerTtsCacheDisplayLabel("plain"))
    }

    @Test
    fun `spend guard error body parses kind and retry`() {
        assertEquals(
            Pair("RATE_LIMITED", 42),
            parseSpendGuardError("""{"error":"RATE_LIMITED","retry_after_seconds":42}""")
        )
        assertEquals(
            Pair("DAILY_SPEND_LIMIT", 3600),
            parseSpendGuardError("""{"error": "DAILY_SPEND_LIMIT", "retry_after_seconds": 3600}""")
        )
        assertNull(parseSpendGuardError("""{"error":"INSUFFICIENT_CREDITS"}"""))
        assertNull(parseSpendGuardError(""))
        assertNull(parseSpendGuardError(null))
        assertNull(parseSpendGuardError("not json"))
    }

    @Test
    fun `spend guard sentinel round-trips`() {
        assertEquals(Pair("RATE_LIMITED", 30), parseSpendGuardSentinel(spendGuardSentinel("RATE_LIMITED", 30)))
        assertEquals(Pair("DAILY_SPEND_LIMIT", 0), parseSpendGuardSentinel("DAILY_SPEND_LIMIT:0"))
        assertNull(parseSpendGuardSentinel("INSUFFICIENT_CREDITS"))
        assertNull(parseSpendGuardSentinel(null))
        assertEquals(Pair("RATE_LIMITED", 0), parseSpendGuardSentinel("RATE_LIMITED"))
    }

    @Test
    fun `http errors map to spend guard tokens`() {
        assertEquals(
            "DAILY_SPEND_LIMIT:3600",
            mapSpendGuardHttpError(402, """{"error":"DAILY_SPEND_LIMIT","retry_after_seconds":3600}""")
        )
        assertEquals("INSUFFICIENT_CREDITS", mapSpendGuardHttpError(402, """{"error":"402"}"""))
        assertEquals("INSUFFICIENT_CREDITS", mapSpendGuardHttpError(402, null))
        assertEquals("RATE_LIMITED:30", mapSpendGuardHttpError(429, null))
        assertEquals(
            "RATE_LIMITED:45",
            mapSpendGuardHttpError(429, """{"error":"RATE_LIMITED","retry_after_seconds":45}""")
        )
        assertNull(mapSpendGuardHttpError(500, "boom"))
        assertNull(mapSpendGuardHttpError(200, ""))
    }

    @Test
    fun `stream errors map to spend guard tokens`() {
        assertEquals("RATE_LIMITED:20", mapSpendGuardStreamError("RATE_LIMITED", 20))
        assertEquals("DAILY_SPEND_LIMIT:0", mapSpendGuardStreamError("DAILY_SPEND_LIMIT", 0))
        assertEquals("INSUFFICIENT_CREDITS", mapSpendGuardStreamError("INSUFFICIENT_CREDITS", 0))
        assertEquals("boom", mapSpendGuardStreamError("boom", 0))
    }

    @Test
    fun `cloud tts model switch covers every selectable tts model`() {
        assertTrue(isCloudTtsModelEnabled(GEMINI_CLOUD_TTS_MODEL_ID))
        assertTrue(isCloudTtsModelEnabled(FISH_TTS_MODEL_ID))
        assertFalse(isCloudTtsModelEnabled(""))
        assertFalse(isCloudTtsModelEnabled("gemini:unknown-model"))
        // Regression: these selectable models used to read as "cloud off",
        // which silently disabled read-aloud in the reader.
        assertTrue(isCloudTtsModelEnabled(GEMINI_TTS_MODEL_LITE_ID))
        assertTrue(isCloudTtsModelEnabled(GEMINI_TTS_MODEL_PREVIEW_ID))
        assertTrue(isCloudTtsModelEnabled(FISH_TTS_MODEL_FREE_ID))
    }

    @Test
    fun `fish free tier is selectable and drives byok like the paid model`() {
        val settings = ReaderAiByokSettings(fishKey = "key", ttsModel = FISH_TTS_MODEL_FREE_ID)
        assertTrue(settings.isFishByokTtsAvailable)
        assertTrue(settings.isAnyByokTtsAvailable)
        assertEquals("fish", settings.ttsProvider)
        assertEquals(
            FISH_TTS_MODEL_FREE_ID,
            ReaderAiByokSettings(ttsModel = FISH_TTS_MODEL_FREE_ID).sanitized().ttsModel
        )
        assertTrue(ReaderTtsByokOptions.map { it.id }.contains(FISH_TTS_MODEL_FREE_ID))
    }

    @Test
    fun `backend resolution prefers a byok key over spending credits`() {
        val worker = mapOf(true to true, false to false)
        for (signedIn in worker.keys) for (token in worker.keys) {
            val available = signedIn && token
            val signedInArg = signedIn
            val tokenArg = token
            // Fish key wins over a Gemini key and over the worker.
            assertEquals(
                CloudTtsBackend.FISH_BYOK,
                resolveCloudTtsBackend(
                    settings = ReaderAiByokSettings(fishKey = "f", geminiKey = "g", ttsModel = FISH_TTS_MODEL_ID),
                    isSignedIn = signedInArg,
                    hasAuthToken = tokenArg,
                    hasWorkerUrl = true,
                ),
            )
            // Gemini key next.
            assertEquals(
                CloudTtsBackend.GEMINI_BYOK,
                resolveCloudTtsBackend(
                    settings = ReaderAiByokSettings(geminiKey = "g", ttsModel = GEMINI_TTS_MODEL_LITE_ID),
                    isSignedIn = signedInArg,
                    hasAuthToken = tokenArg,
                    hasWorkerUrl = true,
                ),
            )
            // No keys: the wallet-backed worker, and nothing without auth.
            assertEquals(
                if (available) CloudTtsBackend.WORKER else CloudTtsBackend.UNAVAILABLE,
                resolveCloudTtsBackend(
                    settings = ReaderAiByokSettings(ttsModel = FISH_TTS_MODEL_ID),
                    isSignedIn = signedInArg,
                    hasAuthToken = tokenArg,
                    hasWorkerUrl = true,
                ),
            )
        }
    }

    @Test
    fun `legacy live gemini model still resolves as a byok backend`() {
        assertEquals(
            CloudTtsBackend.GEMINI_BYOK,
            resolveCloudTtsBackend(
                settings = ReaderAiByokSettings(geminiKey = "g", ttsModel = GEMINI_CLOUD_TTS_MODEL_ID),
                isSignedIn = false,
                hasAuthToken = false,
                hasWorkerUrl = false,
            ),
        )
    }

    @Test
    fun `a model with no matching key is not treated as byok`() {
        // Fish model selected but only a Gemini key saved: Android falls
        // through to the worker, it does not synthesize with the wrong key.
        assertEquals(
            CloudTtsBackend.UNAVAILABLE,
            resolveCloudTtsBackend(
                settings = ReaderAiByokSettings(geminiKey = "g", ttsModel = FISH_TTS_MODEL_ID),
                isSignedIn = false,
                hasAuthToken = false,
                hasWorkerUrl = false,
            ),
        )
    }

    @Test
    fun `ai cost deducted formats dollars for wallet and credits for legacy`() {
        assertEquals("$0.03", formatAiCostDeducted(0.03, true))
        assertEquals("$0.05", formatAiCostDeducted(0.049, true))
        assertEquals("2 credits", formatAiCostDeducted(2.0, false))
        assertEquals("0.5 credits", formatAiCostDeducted(0.5, false))
    }

    @Test
    fun `spend guard countdown formats seconds minutes hours`() {
        assertEquals("45s", formatSpendGuardCountdown(45))
        assertEquals("3m 20s", formatSpendGuardCountdown(200))
        assertEquals("11h 05m", formatSpendGuardCountdown(39900))
    }
}
