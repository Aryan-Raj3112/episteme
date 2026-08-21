package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ReaderAiByokSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosReaderAiAdaptersTest {
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
        assertEquals("Sign in to use multi-word smart dictionary.", adapter.define("two words").error)
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
