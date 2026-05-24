package com.aryan.reader.desktop

import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.SharedFeaturePolicy
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBuildProfileTest {
    @Test
    fun `standard desktop flavor keeps online features available`() {
        val profile = desktopBuildProfileForFlavor("standard")

        assertEquals(DesktopFlavorStandard, profile.flavor)
        assertEquals(EpistemeDesktopStandardAppName, profile.appName)
        assertEquals("Standard edition", profile.buildLabel)
        assertEquals(SharedFeaturePolicy.Standard, profile.featurePolicy)
        assertTrue(profile.legalLinks.privacyPolicyUrl.endsWith("/privacy-policy.html"))
        assertTrue(profile.legalLinks.termsUrl.endsWith("/terms-and-conditions.html"))
        assertTrue(profile.featurePolicy.networkAccess)
        assertFalse(profile.featurePolicy.byokAi)
        assertFalse(profile.byokAiAvailable)
        assertTrue(profile.creditBackedCloudTtsControlsAvailable)
    }

    @Test
    fun `oss offline desktop flavor disables network backed features`() {
        val profile = desktopBuildProfileForFlavor("oss-offline")

        assertEquals(DesktopFlavorOssOffline, profile.flavor)
        assertEquals(EpistemeDesktopOssAppName, profile.appName)
        assertEquals("Offline OSS edition", profile.buildLabel)
        assertEquals(SharedFeaturePolicy.OssOffline, profile.featurePolicy)
        assertTrue(profile.legalLinks.privacyPolicyUrl.endsWith("/oss-privacy-policy.html"))
        assertTrue(profile.legalLinks.termsUrl.endsWith("/oss-terms-of-service.html"))
        assertFalse(profile.featurePolicy.networkAccess)
        assertFalse(profile.featurePolicy.aiAndCloud)
        assertTrue(profile.featurePolicy.byokAi)
        assertFalse(profile.byokAiAvailable)
        assertFalse(profile.featurePolicy.opdsCatalogs)
        assertFalse(profile.featurePolicy.googleFontsDownload)
        assertFalse(profile.creditBackedCloudTtsControlsAvailable)
    }

    @Test
    fun `oss desktop flavor aliases resolve to offline oss profile`() {
        val profile = desktopBuildProfileForFlavor("oss")

        assertEquals(DesktopFlavorOssOffline, profile.flavor)
        assertEquals(EpistemeDesktopOssAppName, profile.appName)
        assertEquals(SharedFeaturePolicy.OssOffline, profile.featurePolicy)
    }

    @Test
    fun `desktop BYOK settings are only exposed by an online OSS-style policy`() {
        val settings = ReaderAiByokSettings(
            geminiKey = "gemini_secret",
            modelForAll = "gemini:gemini-flash-lite-latest"
        )
        val onlineOssPolicy = SharedFeaturePolicy.OssOnline

        assertTrue(
            DesktopBuildProfile(
                flavor = "oss-online",
                appName = "Episteme oss",
                buildLabel = "OSS edition",
                featurePolicy = onlineOssPolicy
            ).byokAiAvailable
        )
        assertEquals(settings, settings.withDesktopFeaturePolicy(onlineOssPolicy))
        assertTrue(settings.withDesktopFeaturePolicy(SharedFeaturePolicy.Standard).hideReaderAiFeatures)
        assertTrue(settings.withDesktopFeaturePolicy(SharedFeaturePolicy.OssOffline).hideReaderAiFeatures)

        val byokCloudTtsSettings = settings.copy(
            geminiKey = "gemini_secret",
            ttsModel = GEMINI_CLOUD_TTS_MODEL_ID
        )
        val desktopByokSettings = byokCloudTtsSettings.withDesktopFeaturePolicy(onlineOssPolicy)
        assertEquals(GEMINI_CLOUD_TTS_MODEL_ID, desktopByokSettings.ttsModel)
        assertTrue(desktopByokSettings.isCloudTtsAvailable)
        assertFalse(
            DesktopBuildProfile(
                flavor = "oss-online",
                appName = "Episteme oss",
                buildLabel = "OSS edition",
                featurePolicy = onlineOssPolicy
            ).creditBackedCloudTtsControlsAvailable
        )
    }

    @Test
    fun `desktop tts worker requires its own configured endpoint`() {
        val config = DesktopCloudConfig(
            aiWorkerUrl = "https://example.com/ai",
            ttsWorkerUrl = "",
            firebaseWebApiKey = "",
            firebaseProjectId = "",
            googleOAuthClientId = "",
            googleOAuthClientSecret = ""
        )

        assertTrue(config.isAiWorkerConfigured)
        assertFalse(config.isTtsWorkerConfigured)
    }

    @Test
    fun `desktop cloud tts adapter allows byok before credit worker`() {
        val byokAdapter = DesktopGeminiCloudTtsAdapter(
            settingsProvider = {
                ReaderAiByokSettings(
                    geminiKey = "gemini_secret",
                    ttsModel = GEMINI_CLOUD_TTS_MODEL_ID
                )
            },
            networkAccess = { true },
            workerUrlProvider = { "" }
        )
        val workerAdapter = DesktopGeminiCloudTtsAdapter(
            settingsProvider = { ReaderAiByokSettings(serverBackedCloudTts = true) },
            networkAccess = { true },
            workerUrlProvider = { "https://example.com/tts" }
        )
        val unavailableAdapter = DesktopGeminiCloudTtsAdapter(
            settingsProvider = { ReaderAiByokSettings() },
            networkAccess = { true },
            workerUrlProvider = { "https://example.com/tts" }
        )

        assertTrue(byokAdapter.isAvailable)
        assertTrue(workerAdapter.isAvailable)
        assertFalse(unavailableAdapter.isAvailable)
    }

    @Test
    fun `desktop diagnostics are disabled unless explicitly enabled`() {
        assertFalse(desktopDiagnosticsFlag(null))
        assertFalse(desktopDiagnosticsFlag(""))
        assertFalse(desktopDiagnosticsFlag("false"))
        assertFalse(desktopDiagnosticsFlag("1"))

        assertTrue(desktopDiagnosticsFlag("true"))
        assertTrue(desktopDiagnosticsFlag(" TRUE "))
    }

    @Test
    fun `windows bundled webview detection is disabled for webview2`() {
        val dir = Files.createTempDirectory("episteme-kcef-test").toFile()
        try {
            val windowsX64 = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
            assertEquals(emptyList(), bundledDesktopWebViewRequiredPaths(windowsX64))
            assertFalse(isBundledDesktopWebViewPresent(dir, windowsX64))
            File(dir, "jcef.dll").writeText("jcef")
            File(dir, "libcef.dll").writeText("cef")

            assertFalse(isBundledDesktopWebViewPresent(dir, windowsX64))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `linux bundled webview detection requires cef shared library and resources`() {
        val dir = Files.createTempDirectory("episteme-linux-kcef-test").toFile()
        try {
            val linuxX64 = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
            assertFalse(isBundledDesktopWebViewPresent(dir, linuxX64))

            File(dir, "libcef.so").writeText("cef")
            File(dir, "chrome-sandbox").writeText("sandbox")
            File(dir, "icudtl.dat").writeText("icu")
            File(dir, "locales").mkdir()

            assertTrue(isBundledDesktopWebViewPresent(dir, linuxX64))
        } finally {
            dir.deleteRecursively()
        }
    }
}
