package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderFeatureSurface
import com.aryan.reader.shared.SharedReaderScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `bundled webview starts only for epub backed reader surfaces on non windows`() {
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)

        assertTrue(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.EPUB_READER, linux))
        assertTrue(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.TEXT_READER, linux))
        assertFalse(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.PDF_VIEWER, linux))
        assertFalse(shouldRequestDesktopWebViewRuntime(null, linux))

        assertFalse(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.EPUB_READER, windows))
        assertFalse(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.TEXT_READER, windows))
    }

    @Test
    fun `embedded webview startup skips terminal runtime states`() {
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)

        assertFalse(shouldStartDesktopWebViewRuntime(requested = true, state = DesktopWebViewRuntimeState(), platform = windows))
        assertFalse(shouldStartDesktopWebViewRuntime(requested = false, state = DesktopWebViewRuntimeState(), platform = linux))
        assertTrue(shouldStartDesktopWebViewRuntime(requested = true, state = DesktopWebViewRuntimeState(), platform = linux))
        assertFalse(
            shouldStartDesktopWebViewRuntime(
                requested = true,
                state = DesktopWebViewRuntimeState(initialized = true),
                platform = linux
            )
        )
        assertFalse(
            shouldStartDesktopWebViewRuntime(
                requested = true,
                state = DesktopWebViewRuntimeState(restartRequired = true),
                platform = linux
            )
        )
        assertFalse(
            shouldStartDesktopWebViewRuntime(
                requested = true,
                state = DesktopWebViewRuntimeState(errorMessage = "missing bundle"),
                platform = linux
            )
        )
    }

    @Test
    fun `windows webview2 can render without bundled runtime state`() {
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)

        assertTrue(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(), windows))
        assertFalse(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(), linux))
        assertTrue(desktopEpubWebViewCanRender(DesktopWebViewRuntimeState(initialized = true), linux))
    }

    @Test
    fun `compose interop blending stays off by default for windows webview2`() {
        val windows = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
        val linux = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)

        assertNull(composeInteropBlendingDefault(windows))
        assertEquals(ComposeInteropBlendingEnabled, composeInteropBlendingDefault(linux))
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
}
