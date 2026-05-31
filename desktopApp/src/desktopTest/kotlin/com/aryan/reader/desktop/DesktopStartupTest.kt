package com.aryan.reader.desktop

import com.aryan.reader.shared.SharedReaderScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class DesktopStartupTest {
    @Test
    fun `startup splash uses compact branded feedback`() {
        val spec = epistemeDesktopStartupSplashSpec(desktopBuildProfileForFlavor("standard"))

        assertEquals(EpistemeDesktopWindowTitle, spec.title)
        assertTrue(spec.message.isNotBlank())
        assertTrue(spec.width in 320..480)
        assertTrue(spec.height in 180..280)
    }

    @Test
    fun `oss startup splash uses oss branding`() {
        val spec = epistemeDesktopStartupSplashSpec(desktopBuildProfileForFlavor("oss-offline"))

        assertEquals(EpistemeDesktopOssAppName, spec.title)
    }

    @Test
    fun `desktop epub webview uses native browser backends without bundled runtime`() {
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
        val macos = DesktopPlatform(DesktopOperatingSystem.MACOS, DesktopArchitecture.ARM64)
        val other = DesktopPlatform(DesktopOperatingSystem.OTHER, DesktopArchitecture.X64)

        assertEquals(DesktopEpubWebViewBackend.WEBKIT, desktopEpubWebViewBackend(linux))
        assertEquals(DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2, desktopEpubWebViewBackend(windows))
        assertEquals(DesktopEpubWebViewBackend.WEBKIT, desktopEpubWebViewBackend(macos))
        assertEquals(DesktopEpubWebViewBackend.UNSUPPORTED, desktopEpubWebViewBackend(other))

        assertTrue(desktopEpubWebViewUsesNativeSwtBrowser(linux))
        assertTrue(desktopEpubWebViewUsesNativeSwtBrowser(windows))
        assertTrue(desktopEpubWebViewUsesNativeSwtBrowser(macos))
        assertFalse(desktopEpubWebViewUsesNativeSwtBrowser(other))
    }

    @Test
    fun `native webviews can render without bundled runtime state`() {
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val macos = DesktopPlatform(DesktopOperatingSystem.MACOS, DesktopArchitecture.ARM64)
        val other = DesktopPlatform(DesktopOperatingSystem.OTHER, DesktopArchitecture.X64)

        assertTrue(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(), windows))
        assertTrue(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(), linux))
        assertTrue(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(), macos))
        assertTrue(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(initialized = true), linux))
        assertFalse(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(initialized = true), other))
    }

    @Test
    fun `native webview unavailable messages point to the platform runtime`() {
        val windowsMessage = desktopNativeWebViewUnavailableMessage(
            backend = DesktopEpubWebViewBackend.WINDOWS_WEBVIEW2,
            detail = "missing runtime"
        )
        val linuxMessage = desktopNativeWebViewUnavailableMessage(
            backend = DesktopEpubWebViewBackend.WEBKIT,
            detail = "missing library"
        )

        assertTrue(windowsMessage.contains("WebView2 Runtime"))
        assertTrue(windowsMessage.contains("missing runtime"))
        assertTrue(linuxMessage.contains("WebKitGTK"))
        assertTrue(linuxMessage.contains("Linux distribution packages"))
        assertTrue(linuxMessage.contains("missing library"))
    }

    @Test
    fun `compose interop blending stays off by default for native swt webviews`() {
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val other = DesktopPlatform(DesktopOperatingSystem.OTHER, DesktopArchitecture.X64)

        assertNull(composeInteropBlendingDefault(windows))
        assertNull(composeInteropBlendingDefault(linux))
        assertEquals(ComposeInteropBlendingEnabled, composeInteropBlendingDefault(other))
    }

    @Test
    fun `silent startup folder sync does not surface missing folder banner`() {
        val completed = desktopFolderSyncCompletedState(
            state = SharedReaderScreenState(),
            message = "Folder sync failed for 1 folder.",
            failedFolderCount = 1,
            showBanner = false
        )

        assertNull(completed.bannerMessage)
    }

    @Test
    fun `manual folder sync still surfaces missing folder banner`() {
        val completed = desktopFolderSyncCompletedState(
            state = SharedReaderScreenState(),
            message = "Folder sync failed for 1 folder.",
            failedFolderCount = 1,
            showBanner = true
        )

        assertEquals("Folder sync failed for 1 folder.", completed.bannerMessage?.message)
        assertTrue(completed.bannerMessage?.isError == true)
    }

    @Test
    fun `desktop account profile store restores cached profile for matching user`() {
        val directory = Files.createTempDirectory("episteme-account-profile-test").toFile()
        try {
            val store = DesktopAccountProfileStore(File(directory, "account_profile.properties"))
            store.save("user-1", DesktopAccountProfile(isProUser = true, credits = 42, fetchedAtEpochMillis = 123L))

            assertEquals(
                DesktopAccountProfile(isProUser = true, credits = 42, fetchedAtEpochMillis = 123L),
                store.load("user-1")
            )
            assertNull(store.load("user-2"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `desktop account profile freshness uses fetched timestamp`() {
        val now = 10_000L
        val ttl = 1_000L

        assertTrue(DesktopAccountProfile(fetchedAtEpochMillis = now - ttl).isFresh(now, ttl))
        assertTrue(DesktopAccountProfile(fetchedAtEpochMillis = now - 1L).isFresh(now, ttl))
        assertFalse(DesktopAccountProfile(fetchedAtEpochMillis = now - ttl - 1L).isFresh(now, ttl))
        assertFalse(DesktopAccountProfile(fetchedAtEpochMillis = now + 1L).isFresh(now, ttl))
        assertFalse(DesktopAccountProfile(fetchedAtEpochMillis = 0L).isFresh(now, ttl))
    }

    @Test
    fun `desktop account profile repository ignores stale startup cache`() {
        val directory = Files.createTempDirectory("episteme-account-profile-policy-test").toFile()
        try {
            val store = DesktopAccountProfileStore(File(directory, "account_profile.properties"))
            val repository = DesktopAccountProfileRepository(testDesktopCloudConfig(), store)
            val now = DesktopAccountProfileCacheTtlMillis + 10_000L
            val freshProfile = DesktopAccountProfile(
                isProUser = true,
                credits = 42,
                fetchedAtEpochMillis = now - DesktopAccountProfileCacheTtlMillis + 1L
            )

            repository.saveFetchedProfile("user-1", freshProfile)
            assertEquals(freshProfile, repository.cachedProfile("user-1", now))

            repository.saveFetchedProfile(
                "user-1",
                freshProfile.copy(fetchedAtEpochMillis = now - DesktopAccountProfileCacheTtlMillis - 1L)
            )
            assertNull(repository.cachedProfile("user-1", now))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `desktop account profile repository clear removes sign out cache`() {
        val directory = Files.createTempDirectory("episteme-account-profile-clear-test").toFile()
        try {
            val store = DesktopAccountProfileStore(File(directory, "account_profile.properties"))
            val repository = DesktopAccountProfileRepository(testDesktopCloudConfig(), store)

            repository.saveFetchedProfile(
                "user-1",
                DesktopAccountProfile(isProUser = true, credits = 42, fetchedAtEpochMillis = 10_000L)
            )
            repository.clearCachedProfiles()

            assertNull(repository.cachedProfile("user-1", 10_001L))
            assertNull(store.load("user-1"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun testDesktopCloudConfig(): DesktopCloudConfig {
        return DesktopCloudConfig(
            aiWorkerUrl = "",
            ttsWorkerUrl = "",
            firebaseWebApiKey = "",
            firebaseProjectId = "reader-test",
            googleOAuthClientId = "",
            googleOAuthClientSecret = ""
        )
    }
}
