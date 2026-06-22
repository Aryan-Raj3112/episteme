package com.aryan.reader.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopFlatpakPackagingMetadataTest {
    @Test
    fun `flatpak metadata declares app id runtime integration files license and permissions`() {
        val buildScript = desktopBuildScriptText()

        assertTrue(buildScript.contains("io.github.Aryan_Raj3112.episteme"))
        assertTrue(buildScript.contains("io.github.Aryan_Raj3112.episteme_oss"))
        assertTrue(buildScript.contains("runtime: org.freedesktop.Platform"))
        assertTrue(buildScript.contains("runtime-version: '$" + "{runtimeVersion.get()}'"))
        assertTrue(buildScript.contains("sdk: org.freedesktop.Sdk"))
        assertTrue(buildScript.contains("/app/share/applications/$" + "appIdValue.desktop"))
        assertTrue(buildScript.contains("/app/share/metainfo/$" + "appIdValue.metainfo.xml"))
        assertTrue(buildScript.contains("/app/share/icons/hicolor/512x512/apps/$" + "appIdValue.png"))
        assertTrue(buildScript.contains("/app/share/licenses/$" + "appIdValue/LICENSE"))
        assertTrue(buildScript.contains("<metadata_license>CC0-1.0</metadata_license>"))
        assertTrue(buildScript.contains("<project_license>AGPL-3.0-only</project_license>"))
        assertTrue(buildScript.contains("--socket=wayland"))
        assertTrue(buildScript.contains("--socket=fallback-x11"))
        assertTrue(buildScript.contains("--socket=pulseaudio"))
        assertTrue(buildScript.contains("--share=network"))
        assertTrue(buildScript.contains("application/epub+zip"))
        assertTrue(buildScript.contains("application/vnd.comicbook+zip"))
        assertTrue(buildScript.contains("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
    }

    private fun desktopBuildScriptText(): String {
        val candidates = listOf(
            File("build.gradle.kts"),
            File("desktopApp/build.gradle.kts")
        )
        val buildFile = candidates.firstOrNull { file ->
            file.isFile && file.readText().contains("PrepareDesktopFlatpakPackageTask")
        }
        requireNotNull(buildFile) { "Could not locate desktopApp/build.gradle.kts" }
        return buildFile.readText()
    }
}
