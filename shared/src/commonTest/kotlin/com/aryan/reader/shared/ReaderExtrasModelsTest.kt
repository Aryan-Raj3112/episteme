package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderReadingMode
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
        val paginated = engine.createSession(book)
            .reduce(ReaderAction.GoToChapter(1), engine)
        val vertical = engine.createSession(book, settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL))
            .reduce(ReaderAction.GoToChapter(1), engine)

        assertEquals("Second chapter text", ReaderContextExtractor.currentChapterText(paginated))
        assertEquals("Second chapter text", ReaderContextExtractor.currentChapterText(vertical))
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
