package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlatformPathsTest {
    @Test
    fun `desktop platform detects linux x64 resource names`() {
        val platform = currentDesktopPlatform(osName = "Linux", osArch = "amd64")

        assertEquals(DesktopOperatingSystem.LINUX, platform.os)
        assertEquals(DesktopArchitecture.X64, platform.architecture)
        assertEquals("kcef-bundle-linux-x64", platform.kcefBundleDirectoryName)
        assertEquals("linux-x64-v8", platform.pdfiumDirectoryName)
        assertEquals("lib", platform.pdfiumLibraryDirectoryName)
        assertEquals("libpdfium.so", platform.pdfiumLibraryFileName)
    }

    @Test
    fun `desktop platform keeps existing windows resource names`() {
        val platform = currentDesktopPlatform(osName = "Windows 11", osArch = "amd64")

        assertEquals(DesktopOperatingSystem.WINDOWS, platform.os)
        assertEquals(DesktopArchitecture.X64, platform.architecture)
        assertEquals("kcef-bundle", platform.kcefBundleDirectoryName)
        assertEquals("win-x64-v8", platform.pdfiumDirectoryName)
        assertEquals("bin", platform.pdfiumLibraryDirectoryName)
        assertEquals("pdfium.dll", platform.pdfiumLibraryFileName)
    }

    @Test
    fun `linux user directories follow xdg environment variables`() {
        val platform = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val env = mapOf(
            "XDG_DATA_HOME" to "/tmp/xdg-data",
            "XDG_CONFIG_HOME" to "/tmp/xdg-config",
            "XDG_CACHE_HOME" to "/tmp/xdg-cache"
        )

        assertEquals("/tmp/xdg-data/episteme", desktopUserDataRoot(platform, env::get, "/home/reader").portablePath())
        assertEquals("/tmp/xdg-config/episteme", desktopUserConfigRoot(platform, env::get, "/home/reader").portablePath())
        assertEquals("/tmp/xdg-cache/episteme", desktopUserCacheRoot(platform, env::get, "/home/reader").portablePath())
    }

    @Test
    fun `linux user directories ignore relative xdg environment values`() {
        val platform = DesktopPlatform(DesktopOperatingSystem.LINUX, DesktopArchitecture.X64)
        val env = mapOf(
            "XDG_DATA_HOME" to "relative-data",
            "XDG_CONFIG_HOME" to "relative-config",
            "XDG_CACHE_HOME" to "relative-cache"
        )

        assertEquals("/home/reader/.local/share/episteme", desktopUserDataRoot(platform, env::get, "/home/reader").portablePath())
        assertEquals("/home/reader/.config/episteme", desktopUserConfigRoot(platform, env::get, "/home/reader").portablePath())
        assertEquals("/home/reader/.cache/episteme", desktopUserCacheRoot(platform, env::get, "/home/reader").portablePath())
    }

    @Test
    fun `windows user directories keep appdata compatible root`() {
        val platform = DesktopPlatform(DesktopOperatingSystem.WINDOWS, DesktopArchitecture.X64)
        val env = mapOf("APPDATA" to "C:/Users/reader/AppData/Roaming")

        assertEquals("C:/Users/reader/AppData/Roaming/Episteme", desktopUserDataRoot(platform, env::get, "C:/Users/reader").portablePath())
        assertEquals("C:/Users/reader/AppData/Roaming/Episteme", desktopUserConfigRoot(platform, env::get, "C:/Users/reader").portablePath())
        assertEquals("C:/Users/reader/AppData/Roaming/Episteme", desktopUserCacheRoot(platform, env::get, "C:/Users/reader").portablePath())
    }
}

private fun java.io.File.portablePath(): String {
    return path.replace('\\', '/')
}
