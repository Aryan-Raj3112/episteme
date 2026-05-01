package com.aryan.reader.desktop

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.OptimizedCssRules
import com.aryan.reader.paginatedreader.UserAgentStylesheet
import com.aryan.reader.paginatedreader.htmlToSemanticBlocks
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

object DesktopEpubLoader {
    fun load(file: File): SharedEpubBook {
        ZipFile(file).use { zip ->
            val container = zip.readText("META-INF/container.xml")
            val opfPath = container
                .substringAfter("full-path=\"", missingDelimiterValue = "")
                .substringBefore("\"")
                .ifBlank { error("EPUB container does not point to an OPF package.") }
            val opf = zip.readText(opfPath)
            val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
                .let { if (it.isBlank()) "" else "$it/" }

            val title = opf.tagText("title").ifBlank { file.nameWithoutExtension }
            val author = opf.tagText("creator").ifBlank { null }
            val manifest = parseManifest(opf)
            val cssRules = parseCssRules(zip, manifest, basePath)
            val spine = Regex("<itemref[^>]*idref=[\"']([^\"']+)[\"'][^>]*/?>")
                .findAll(opf)
                .mapNotNull { match -> manifest[match.groupValues[1]] }
                .toList()

            val chapterPaths = spine.ifEmpty {
                manifest.values.filter { it.endsWith(".xhtml", ignoreCase = true) || it.endsWith(".html", ignoreCase = true) }
            }

            val chapters = chapterPaths.mapIndexedNotNull { index, href ->
                val path = normalizeZipPath(basePath + href)
                val html = zip.readTextOrNull(path) ?: return@mapIndexedNotNull null
                val text = htmlToText(html)
                val semanticBlocks = runCatching {
                    htmlToSemanticBlocks(
                        html = html,
                        cssRules = cssRules,
                        textStyle = TextStyle(fontSize = 18.sp),
                        chapterAbsPath = path,
                        extractionBasePath = "",
                        density = Density(1f),
                        fontFamilyMap = emptyMap(),
                        constraints = Constraints(maxWidth = 980, maxHeight = 720)
                    )
                }.getOrElse { emptyList() }
                if (text.isBlank()) {
                    null
                } else {
                    SharedEpubChapter(
                        id = "chapter_$index",
                        title = html.tagText("h1")
                            .ifBlank { html.tagText("h2") }
                            .ifBlank { html.tagText("title") }
                            .ifBlank { "Chapter ${index + 1}" },
                        plainText = text,
                        semanticBlocks = semanticBlocks
                    )
                }
            }

            return SharedEpubBook(
                id = file.absolutePath,
                fileName = file.name,
                title = title,
                author = author,
                chapters = chapters.ifEmpty {
                    listOf(
                        SharedEpubChapter(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            plainText = "This EPUB opened, but no readable spine text was found by the lightweight desktop loader."
                        )
                    )
                }
            )
        }
    }

    private fun parseManifest(opf: String): Map<String, String> {
        return Regex("<item\\s+[^>]*>").findAll(opf).mapNotNull { match ->
            val item = match.value
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isBlank() || href.isBlank()) null else id to href
        }.toMap()
    }

    private fun parseCssRules(zip: ZipFile, manifest: Map<String, String>, basePath: String): OptimizedCssRules {
        val constraints = Constraints(maxWidth = 980, maxHeight = 720)
        val baseRules = CssParser.parse(
            cssContent = UserAgentStylesheet.default,
            cssPath = null,
            baseFontSizeSp = 18f,
            density = 1f,
            constraints = constraints,
            isDarkTheme = false
        ).rules

        return manifest.values
            .filter { it.endsWith(".css", ignoreCase = true) }
            .fold(baseRules) { rules, href ->
                val path = normalizeZipPath(basePath + href)
                val css = zip.readTextOrNull(path).orEmpty()
                if (css.isBlank()) {
                    rules
                } else {
                    rules.merge(
                        CssParser.parse(
                            cssContent = css,
                            cssPath = path,
                            baseFontSizeSp = 18f,
                            density = 1f,
                            constraints = constraints,
                            isDarkTheme = false
                        ).rules
                    )
                }
            }
    }

    private fun ZipFile.readText(path: String): String {
        val entry = getEntry(path) ?: error("Missing EPUB entry: $path")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun ZipFile.readTextOrNull(path: String): String? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun String.attr(name: String): String {
        return Regex("""\b$name=["']([^"']+)["']""").find(this)?.groupValues?.get(1).orEmpty()
    }

    private fun String.tagText(tag: String): String {
        return Regex("<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.let(::htmlToText)
            .orEmpty()
    }

    private fun normalizeZipPath(path: String): String {
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n\n")
            .replace(Regex("(?i)</h[1-6]\\s*>"), "\n\n")
            .replace(Regex("<[^>]+>"), " ")
            .decodeEntities()
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.decodeEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
    }
}
