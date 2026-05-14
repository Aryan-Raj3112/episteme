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
        assertEquals(EpistemeDesktopStandardAppName, profile.appName)
        assertEquals("Standard edition", profile.buildLabel)
        assertEquals(SharedFeaturePolicy.Standard, profile.featurePolicy)
        assertTrue(profile.featurePolicy.networkAccess)
    }

    @Test
    fun `oss offline desktop flavor disables network backed features`() {
        val profile = desktopBuildProfileForFlavor("oss-offline")

        assertEquals(DesktopFlavorOssOffline, profile.flavor)
        assertEquals(EpistemeDesktopOssAppName, profile.appName)
        assertEquals("Offline OSS edition", profile.buildLabel)
        assertEquals(SharedFeaturePolicy.OssOffline, profile.featurePolicy)
        assertFalse(profile.featurePolicy.networkAccess)
        assertFalse(profile.featurePolicy.aiAndCloud)
        assertFalse(profile.featurePolicy.opdsCatalogs)
        assertFalse(profile.featurePolicy.googleFontsDownload)
    }

    @Test
    fun `oss desktop flavor aliases resolve to offline oss profile`() {
        val profile = desktopBuildProfileForFlavor("oss")

        assertEquals(DesktopFlavorOssOffline, profile.flavor)
        assertEquals(EpistemeDesktopOssAppName, profile.appName)
        assertEquals(SharedFeaturePolicy.OssOffline, profile.featurePolicy)
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
            val windowsX64 = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
            assertFalse(isBundledDesktopWebViewPresent(dir, windowsX64))
            File(dir, "jcef.dll").writeText("jcef")
            File(dir, "libcef.dll").writeText("cef")

            assertTrue(isBundledDesktopWebViewPresent(dir, windowsX64))
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
