package com.aryan.reader.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAurPackagingMetadataTest {
    @Test
    fun `aur metadata declares arch runtime dependencies license and desktop mime support`() {
        val buildScript = desktopBuildScriptText()

        assertTrue(buildScript.contains("\"libarchive\""))
        assertTrue(buildScript.contains("license=('AGPL-3.0-only')"))
        assertTrue(buildScript.contains("license = AGPL-3.0-only"))
        assertTrue(buildScript.contains("/usr/share/licenses/${'$'}pkgname/LICENSE"))
        assertTrue(buildScript.contains("application/epub+zip"))
        assertTrue(buildScript.contains("application/vnd.comicbook+zip"))
        assertTrue(buildScript.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    private fun desktopBuildScriptText(): String {
        val candidates = listOf(
            File("build.gradle.kts"),
            File("desktopApp/build.gradle.kts")
        )
        val buildFile = candidates.firstOrNull { file ->
            file.isFile && file.readText().contains("PrepareDesktopAurPackageTask")
        }
        requireNotNull(buildFile) { "Could not locate desktopApp/build.gradle.kts" }
        return buildFile.readText()
    }
}
