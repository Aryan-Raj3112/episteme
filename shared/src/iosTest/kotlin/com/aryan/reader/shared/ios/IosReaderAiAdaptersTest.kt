package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ReaderAiByokSettings
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
}
