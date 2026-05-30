package com.aryan.reader.desktop

import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAiByokStoreTest {
    @Test
    fun `save keeps keys out of plaintext settings file`() {
        val settingsFile = Files.createTempDirectory("reader-ai-store").resolve("ai-byok.properties")
        val store = DesktopAiByokStore(settingsFile.toFile(), ReversibleSecretCodec)

        store.save(
            ReaderAiByokSettings(
                geminiKey = "gemini_secret",
                groqKey = "groq_secret",
                modelForAll = "groq:qwen/qwen3-32b",
                ttsModel = GEMINI_CLOUD_TTS_MODEL_ID
            )
        )

        val raw = settingsFile.readText()
        assertFalse(raw.contains("gemini_secret"))
        assertFalse(raw.contains("groq_secret"))
        assertTrue(raw.contains("geminiKeyProtected="))
        assertTrue(raw.contains("groqKeyProtected="))

        val loaded = store.load()
        assertEquals("gemini_secret", loaded.geminiKey)
        assertEquals("groq_secret", loaded.groqKey)
        assertEquals("groq:qwen/qwen3-32b", loaded.modelForAll)
        assertEquals(GEMINI_CLOUD_TTS_MODEL_ID, loaded.ttsModel)
    }

    @Test
    fun `load migrates legacy plaintext keys into protected entries`() {
        val settingsFile = Files.createTempDirectory("reader-ai-store-legacy").resolve("ai-byok.properties")
        settingsFile.writeText(
            """
            geminiKey=old_gemini
            groqKey=old_groq
            modelForAll=groq:qwen/qwen3-32b
            useOneModel=true
            """.trimIndent()
        )
        val store = DesktopAiByokStore(settingsFile.toFile(), ReversibleSecretCodec)

        val loaded = store.load()

        assertEquals("old_gemini", loaded.geminiKey)
        assertEquals("old_groq", loaded.groqKey)
        assertEquals(GEMINI_CLOUD_TTS_MODEL_ID, loaded.ttsModel)
        val raw = settingsFile.readText()
        assertFalse(raw.contains("geminiKey=old_gemini"))
        assertFalse(raw.contains("groqKey=old_groq"))
        assertTrue(raw.contains("geminiKeyProtected="))
        assertTrue(raw.contains("groqKeyProtected="))
    }

    @Test
    fun `model settings persist when secure key storage is unavailable`() {
        val settingsFile = Files.createTempDirectory("reader-ai-store-unavailable").resolve("ai-byok.properties")
        val store = DesktopAiByokStore(settingsFile.toFile(), UnavailableSecretCodec)

        store.save(
            ReaderAiByokSettings(
                geminiKey = "session_only",
                modelForAll = "groq:qwen/qwen3-32b",
                ttsModel = GEMINI_CLOUD_TTS_MODEL_ID
            )
        )

        val raw = settingsFile.readText()
        assertFalse(raw.contains("session_only"))
        assertFalse(raw.contains("geminiKeyProtected="))

        val loaded = store.load()
        assertEquals("", loaded.geminiKey)
        assertEquals("groq:qwen/qwen3-32b", loaded.modelForAll)
        assertEquals(GEMINI_CLOUD_TTS_MODEL_ID, loaded.ttsModel)
    }

    @Test
    fun `save with blank key clears protected secret entry`() {
        val settingsFile = Files.createTempDirectory("reader-ai-store-clear").resolve("ai-byok.properties")
        val store = DesktopAiByokStore(settingsFile.toFile(), ReversibleSecretCodec)

        store.save(
            ReaderAiByokSettings(
                geminiKey = "gemini_secret",
                groqKey = "groq_secret",
                modelForAll = "groq:qwen/qwen3-32b"
            )
        )
        store.save(
            ReaderAiByokSettings(
                groqKey = "groq_secret",
                modelForAll = "groq:qwen/qwen3-32b"
            )
        )

        val raw = settingsFile.readText()
        assertFalse(raw.contains("geminiKeyProtected="))
        assertTrue(raw.contains("groqKeyProtected="))

        val loaded = store.load()
        assertEquals("", loaded.geminiKey)
        assertEquals("groq_secret", loaded.groqKey)
        assertEquals("groq:qwen/qwen3-32b", loaded.modelForAll)
    }

    @Test
    fun `load ignores legacy hidden reader ai preference on desktop`() {
        val settingsFile = Files.createTempDirectory("reader-ai-store-visible").resolve("ai-byok.properties")
        settingsFile.writeText(
            """
            hideReaderAiFeatures=true
            modelForAll=groq:qwen/qwen3-32b
            useOneModel=true
            """.trimIndent()
        )
        val store = DesktopAiByokStore(settingsFile.toFile(), ReversibleSecretCodec)

        val loaded = store.load()

        assertFalse(loaded.hideReaderAiFeatures)
        assertTrue(loaded.useOneModel)
        assertEquals("groq:qwen/qwen3-32b", loaded.modelForAll)
    }

    @Test
    fun `load does not probe secure storage when settings file is missing`() {
        val settingsFile = Files.createTempDirectory("reader-ai-store-missing").resolve("ai-byok.properties")
        val store = DesktopAiByokStore(settingsFile.toFile(), ThrowingAvailabilitySecretCodec)

        val loaded = store.load()

        assertEquals("", loaded.geminiKey)
        assertEquals("", loaded.groqKey)
    }

    private object ReversibleSecretCodec : DesktopSecretCodec {
        override val isAvailable: Boolean = true

        override fun protect(value: String): String {
            return "test:" + value.reversed()
        }

        override fun unprotect(value: String): String {
            return value.removePrefix("test:").reversed()
        }
    }

    private object UnavailableSecretCodec : DesktopSecretCodec {
        override val isAvailable: Boolean = false
        override fun protect(value: String): String = ""
        override fun unprotect(value: String): String = ""
    }

    private object ThrowingAvailabilitySecretCodec : DesktopSecretCodec {
        override val isAvailable: Boolean
            get() = error("Secure storage should not be checked for a missing settings file.")

        override fun protect(value: String): String = ""
        override fun unprotect(value: String): String = ""
    }
}
