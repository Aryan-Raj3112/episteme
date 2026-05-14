package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderFeatureSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `embedded webview starts only for epub backed reader surfaces`() {
        assertTrue(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.EPUB_READER))
        assertTrue(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.TEXT_READER))
        assertFalse(shouldRequestDesktopWebViewRuntime(ReaderFeatureSurface.PDF_VIEWER))
        assertFalse(shouldRequestDesktopWebViewRuntime(null))
    }

    @Test
    fun `embedded webview startup skips terminal runtime states`() {
        assertFalse(shouldStartDesktopWebViewRuntime(requested = false, state = DesktopWebViewRuntimeState()))
        assertTrue(shouldStartDesktopWebViewRuntime(requested = true, state = DesktopWebViewRuntimeState()))
        assertFalse(
            shouldStartDesktopWebViewRuntime(
                requested = true,
                state = DesktopWebViewRuntimeState(initialized = true)
            )
        )
        assertFalse(
            shouldStartDesktopWebViewRuntime(
                requested = true,
                state = DesktopWebViewRuntimeState(restartRequired = true)
            )
        )
        assertFalse(
            shouldStartDesktopWebViewRuntime(
                requested = true,
                state = DesktopWebViewRuntimeState(errorMessage = "missing bundle")
            )
        )
    }
}
