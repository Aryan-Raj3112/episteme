package com.aryan.reader.desktop

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
        assertEquals(SharedFeaturePolicy.Standard, profile.featurePolicy)
        assertTrue(profile.featurePolicy.networkAccess)
    }

    @Test
    fun `oss offline desktop flavor disables network backed features`() {
        val profile = desktopBuildProfileForFlavor("oss-offline")

        assertEquals(DesktopFlavorOssOffline, profile.flavor)
        assertEquals(SharedFeaturePolicy.OssOffline, profile.featurePolicy)
        assertFalse(profile.featurePolicy.networkAccess)
        assertFalse(profile.featurePolicy.aiAndCloud)
        assertFalse(profile.featurePolicy.opdsCatalogs)
        assertFalse(profile.featurePolicy.googleFontsDownload)
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
    fun `bundled webview detection requires cef binaries`() {
        val dir = Files.createTempDirectory("episteme-kcef-test").toFile()
        try {
            assertFalse(isBundledDesktopWebViewPresent(dir))
            File(dir, "jcef.dll").writeText("jcef")
            File(dir, "libcef.dll").writeText("cef")

            assertTrue(isBundledDesktopWebViewPresent(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
