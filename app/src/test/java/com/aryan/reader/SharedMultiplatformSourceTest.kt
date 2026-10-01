package com.aryan.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shared module's multiplatform surface.
 *
 * `shared` is compiled for Android *and* iOS, so anything in `commonMain`/`mobileMain`/`iosMain`
 * must avoid JVM-only stdlib APIs. `String.format` is the one that has actually bitten: it exists
 * in `kotlin-stdlib-jdk` and simply does not resolve on Kotlin/Native, so the failure only shows
 * up in an iOS compile — long after `:app` and the Android host tests are green.
 *
 * Localize through `SharedStringResolver` (`readerString`, or capture
 * `LocalSharedStringResolver.current` when the call site is not `@Composable`) instead.
 */
class SharedMultiplatformSourceTest {

    private val sharedSourceSets = listOf("commonMain", "mobileMain", "iosMain")

    @Test
    fun `shared avoids String format outside the jvm source sets`() {
        val offenders = mutableListOf<String>()
        for (sourceSet in sharedSourceSets) {
            for (file in sharedKotlinFiles(sourceSet)) {
                file.codeLines().forEachIndexed { index, code ->
                    // No leading \b on the quoted-string forms: `"` is not a word
                    // character, so a boundary can never occur in front of it.
                    if (Regex("""String\.format\(|"[^"]*"\s*\.\s*format\(|\)\s*\.\s*format\(""")
                            .containsMatchIn(code)
                    ) {
                        offenders += "${file.name}:${index + 1}: ${code.trim()}"
                    }
                }
            }
        }
        assertTrue(
            "JVM-only String.format in shared (breaks the iOS build):\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * File lines with comments removed, preserving line numbering. KDoc often *documents* the
     * JVM-only call it replaced, so scanning raw text produces false positives.
     */
    private fun File.codeLines(): List<String> {
        val result = ArrayList<String>()
        var inBlockComment = false
        forEachLine { raw ->
            var line = raw
            if (inBlockComment) {
                val end = line.indexOf("*/")
                if (end < 0) {
                    result += ""
                    return@forEachLine
                }
                line = line.substring(end + 2)
                inBlockComment = false
            }
            val blockStart = line.indexOf("/*")
            if (blockStart >= 0) {
                val blockEnd = line.indexOf("*/", blockStart)
                if (blockEnd < 0) {
                    line = line.substring(0, blockStart)
                    inBlockComment = true
                } else {
                    line = line.substring(0, blockStart) + " " + line.substring(blockEnd + 2)
                }
            }
            val lineComment = line.indexOf("//")
            if (lineComment >= 0) line = line.substring(0, lineComment)
            // A line-commented or string-swallowed line can leave a bare quote; drop the
            // remainder so a half-parsed literal cannot look like a call.
            result += line
        }
        return result
    }

    @Test
    fun `shared pdf jump history caption resolves through the string resolver`() {
        val source = readShared("mobileMain", "ui/SharedMobilePdfReaderScreen.kt")
        assertTrue(
            "the page caption lambda is not @Composable, so it must capture " +
                "LocalSharedStringResolver rather than call readerString",
            source.contains("LocalSharedStringResolver.current")
        )
    }

    private fun sharedKotlinFiles(sourceSet: String): List<File> {
        val root = File("shared/src/$sourceSet")
        if (root.isDirectory) return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        // Gradle runs unit tests with the module dir as the working directory, and the
        // iOS-only source sets only exist relative to the repo root.
        val fromModuleRoot = File("../shared/src/$sourceSet")
        if (fromModuleRoot.isDirectory) {
            return fromModuleRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        error("Cannot locate shared/src/$sourceSet from ${File(".").absolutePath}")
    }

    private fun readShared(sourceSet: String, relativePath: String): String {
        val direct = File("shared/src/$sourceSet/kotlin/com/aryan/reader/shared/$relativePath")
        if (direct.isFile) return direct.readText()
        val fromModuleRoot = File("../shared/src/$sourceSet/kotlin/com/aryan/reader/shared/$relativePath")
        return fromModuleRoot.readText()
    }
}
