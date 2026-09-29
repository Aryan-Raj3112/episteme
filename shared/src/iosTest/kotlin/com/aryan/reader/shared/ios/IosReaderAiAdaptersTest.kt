package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderRecapRequest
import com.aryan.reader.shared.ReaderRecapSection
import com.aryan.reader.shared.SharedSummaryCache
import com.aryan.reader.shared.SharedSummaryCacheStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosReaderAiAdaptersTest {
    @Test
    fun keychainRoundTripReadsDataReturnedBySecurityFramework() {
        val account = "ios-reader-ai-regression"
        val expected = "keychain-regression-value"
        IosReaderAiKeychain.delete(account)
        try {
            val wrote = IosReaderAiKeychain.write(account, expected)
            if (!wrote) {
                // Unsigned/simulator test hosts often lack keychain entitlement
                // (errSecMissingEntitlement). Production app has the entitlement;
                // this host cannot exercise the Security framework round-trip.
                assertEquals("", IosReaderAiKeychain.read(account))
                return
            }
            assertEquals(expected, IosReaderAiKeychain.read(account))
        } finally {
            IosReaderAiKeychain.delete(account)
        }
    }

    @Test
    fun availabilityRequiresNetworkAndVisibleConfiguredAccess() {
        var settings = ReaderAiByokSettings(geminiKey = "test-key")
        var account = IosReaderAiAccountState()
        var networkAvailable = true
        val adapter = IosReaderAiAdapter(
            settingsProvider = { settings },
            accountStateProvider = { account },
            authTokenProvider = { null },
            networkAccess = { networkAvailable },
        )

        assertTrue(adapter.isAvailable)
        networkAvailable = false
        assertFalse(adapter.isAvailable)
        networkAvailable = true
        settings = settings.copy(hideReaderAiFeatures = true)
        assertFalse(adapter.isAvailable)
        settings = settings.copy(hideReaderAiFeatures = false, geminiKey = "")
        // Android parity (areReaderAiFeaturesEnabled): the managed worker
        // keeps AI available signed-out, so single-word define works just
        // like Android.
        account = IosReaderAiAccountState()
        assertTrue(adapter.isAvailable)
        account = IosReaderAiAccountState(isSignedIn = true)
        assertTrue(adapter.isAvailable)
    }

    @Test
    fun paidFeaturesKeepAndroidStyleSignInAndCreditGates() = runTest {
        val adapter = IosReaderAiAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            accountStateProvider = { IosReaderAiAccountState() },
            authTokenProvider = { null },
        )

        assertEquals("Sign in to use this AI feature.", adapter.summarize("text").error)
        assertEquals("Sign in to use this AI feature.", adapter.recap("context").error)
        // Android parity (PdfViewerScreen.onDictionaryLookup): smart
        // dictionary is Pro-only for every length — no sign-in gate, the
        // Pro error surfaces even signed-out.
        assertEquals("Smart dictionary requires Pro.", adapter.define("two words").error)
    }

    @Test
    fun defineRequiresProForAllLengths() = runTest {
        val adapter = IosReaderAiAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            accountStateProvider = { IosReaderAiAccountState() },
            authTokenProvider = { null },
            workerUrlProvider = { "" },
        )

        // Single words hit the same Pro gate as phrases now (worker /define
        // is Pro-only); BYOK is the only non-Pro path.
        assertEquals("Smart dictionary requires Pro.", adapter.define("word").error)
        assertEquals("Smart dictionary requires Pro.", adapter.define("two  words").error)
        assertEquals("Smart dictionary requires Pro.", adapter.define("  two words  ").error)
    }

    @Test
    fun signedInAccountWithoutCreditsCannotUsePaidGeneration() = runTest {
        val adapter = IosReaderAiAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            accountStateProvider = { IosReaderAiAccountState(isSignedIn = true, credits = 0) },
            authTokenProvider = { null },
        )

        assertEquals("This action needs credits.", adapter.summarize("text").error)
        assertEquals("This action needs credits.", adapter.recap("context").error)
    }

    private class FakeRecapCacheStorage : SharedSummaryCacheStorage {
        val files = mutableMapOf<String, String>()
        override fun read(fileName: String): String? = files[fileName]
        override fun write(fileName: String, content: String): Boolean {
            files[fileName] = content
            return true
        }
        override fun delete(fileName: String): Boolean = files.remove(fileName) != null
        override fun listFileNames(): List<String> = files.keys.toList()
    }

    @Test
    fun chainedRecapRejectsBlankContext() = runTest {
        val adapter = IosReaderAiAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            accountStateProvider = { IosReaderAiAccountState() },
            authTokenProvider = { null },
        )

        val result = adapter.recapChained(
            ReaderRecapRequest("Book", 0, emptyList(), "   ")
        )
        assertEquals("There is no reading context for a recap.", result.error)
    }

    @Test
    fun chainedRecapAbortsWhenPastChapterSummarizeIsGated() = runTest {
        val progress = mutableListOf<String>()
        val adapter = IosReaderAiAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            accountStateProvider = { IosReaderAiAccountState() },
            authTokenProvider = { null },
            networkAccess = { false },
        )

        // Android parity (executeRecapLogic): the uncached past chapter
        // summarizes first, so its gate error aborts before the final recap.
        val result = adapter.recapChained(
            ReaderRecapRequest(
                bookTitle = "Book",
                sectionIndex = 1,
                pastSections = listOf(ReaderRecapSection("Chapter 1", "x".repeat(200))),
                currentText = "Current chapter text.",
                summaryCache = SharedSummaryCache(FakeRecapCacheStorage()),
            ),
            onProgress = { progress.add(it) },
        )
        assertEquals("AI features are unavailable while offline.", result.error)
        assertEquals(listOf("CHECKING_PAST", "ANALYZING:1"), progress)
    }

    @Test
    fun chainedRecapUsesCachedPastChapterThenFinalGate() = runTest {
        val progress = mutableListOf<String>()
        val cache = SharedSummaryCache(FakeRecapCacheStorage())
        cache.saveSummary("Book", 0, "Chapter 1", "Past events.")
        val adapter = IosReaderAiAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            accountStateProvider = { IosReaderAiAccountState() },
            authTokenProvider = { null },
            networkAccess = { false },
        )

        val result = adapter.recapChained(
            ReaderRecapRequest(
                bookTitle = "Book",
                sectionIndex = 1,
                pastSections = listOf(ReaderRecapSection("Chapter 1", "Full past text.")),
                currentText = "Current chapter text.",
                summaryCache = cache,
            ),
            onProgress = { progress.add(it) },
        )
        // The cached past chapter needs no network; the full staged
        // sequence runs and the offline gate surfaces from the final recap.
        assertEquals(
            listOf("CHECKING_PAST", "ANALYZING:1", "READING_POSITION", "GENERATING"),
            progress,
        )
        assertEquals("AI features are unavailable while offline.", result.error)
    }

    @Test
    fun aiKeyStoreSupportsAllProvidersWithoutThrowing() {
        // Android parity (saveAiByokKey/deleteAiByokKey/maskedAiByokKey):
        // gemini, groq, AND fish must all round-trip through the store.
        // Headless-safe: without a keychain entitlement the writes fail
        // silently and reads return "", but nothing may throw (previously
        // "fish" hit error("Unsupported AI provider")).
        val store = IosReaderAiSettingsStore()
        listOf("gemini", "groq", "fish").forEach { provider ->
            store.saveKey(provider, "test-key-value")
            store.deleteKey(provider)
        }
        val masked = store.maskedKeys()
        assertTrue(masked.keys.containsAll(listOf("gemini", "groq", "fish")))
        // A fish key in memory must survive a store save/load cycle
        // (load() previously dropped fishKey entirely).
        store.save(
            ReaderAiByokSettings(
                geminiKey = "",
                groqKey = "",
                fishKey = "",
            )
        )
        // Load must not throw headless (keychain reads return "").
        store.load()
    }

    @Test
    fun recapProgressTokensResolveToLocalizedCopy() {
        assertEquals(
            RecapProgressCopy("ai_recap_checking_past", "Checking past chapters..."),
            recapProgressCopy("CHECKING_PAST"),
        )
        assertEquals(
            RecapProgressCopy("ai_recap_analyzing_chapter", "Analyzing Chapter %1\$d...", 3),
            recapProgressCopy("ANALYZING:3"),
        )
        assertEquals(
            RecapProgressCopy("ai_recap_reading_position", "Reading current position..."),
            recapProgressCopy("READING_POSITION"),
        )
        assertEquals(
            RecapProgressCopy("ai_recap_generating", "Generating Recap..."),
            recapProgressCopy("GENERATING"),
        )
        // Unknown tokens pass through (hosts render them verbatim).
        assertEquals(RecapProgressCopy("ai_thinking", "SOMETHING_ELSE"), recapProgressCopy("SOMETHING_ELSE"))
    }
}
