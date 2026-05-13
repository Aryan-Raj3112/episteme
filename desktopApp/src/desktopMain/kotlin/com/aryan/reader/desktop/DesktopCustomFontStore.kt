package com.aryan.reader.desktop

import com.aryan.reader.shared.CustomFontItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID

class DesktopCustomFontStore(
    private val fontsDir: File = defaultFontsDir(),
    private val googleFontsDownloadAvailable: () -> Boolean = { true }
) {
    private var googleFontsCache: List<String>? = null

    fun importFont(source: File, displayNameOverride: String? = null): Result<CustomFontItem> {
        if (!source.isFile) {
            return Result.failure(IllegalArgumentException("Choose a font file."))
        }
        val extension = source.extension.lowercase()
        if (extension !in SupportedFontExtensions) {
            return Result.failure(IllegalArgumentException("Unsupported font format. Use TTF, OTF, or WOFF2."))
        }

        return runCatching {
            fontsDir.mkdirs()
            val fontId = UUID.randomUUID().toString()
            val fileName = "font_$fontId.$extension"
            val destination = File(fontsDir, fileName)
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            CustomFontItem(
                id = fontId,
                displayName = displayNameOverride?.takeIf { it.isNotBlank() }
                    ?: source.nameWithoutExtension.ifBlank { "Imported font" },
                fileName = fileName,
                fileExtension = extension,
                path = destination.absolutePath,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    fun deleteFont(font: CustomFontItem): Boolean {
        val target = runCatching { File(font.path).canonicalFile }.getOrNull() ?: return false
        val root = runCatching { fontsDir.canonicalFile }.getOrNull() ?: return false
        val insideFontStore = generateSequence(target) { it.parentFile }.any { it == root }
        if (!insideFontStore) return false
        return !target.exists() || target.delete()
    }

    fun loadGoogleFontsList(): List<String> {
        googleFontsCache?.let { return it }
        val loaded = runCatching {
            val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(GoogleFontsResource)
                ?: DesktopCustomFontStore::class.java.classLoader?.getResourceAsStream(GoogleFontsResource)
                ?: return@runCatching emptyList()
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                googleFontsFromJson(reader.readText())
            }
        }.getOrDefault(emptyList())
        googleFontsCache = loaded
        return loaded
    }

    fun downloadGoogleFont(fontName: String): Result<CustomFontItem> {
        if (!googleFontsDownloadAvailable()) {
            return Result.failure(IllegalStateException("Google Fonts download is unavailable in this desktop build."))
        }
        val normalizedFontName = fontName.trim()
        if (normalizedFontName.isBlank()) {
            return Result.failure(IllegalArgumentException("Choose a Google Font."))
        }

        return runCatching {
            val encodedName = URLEncoder.encode(normalizedFontName, Charsets.UTF_8.name())
            val cssConnection = URL("https://fonts.googleapis.com/css?family=$encodedName")
                .openConnection() as HttpURLConnection
            cssConnection.setRequestProperty("User-Agent", GoogleFontsSafariUserAgent)
            cssConnection.connectTimeout = 15_000
            cssConnection.readTimeout = 15_000

            if (cssConnection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Font '$normalizedFontName' was not found on Google Fonts.")
            }

            val css = cssConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val fontUrl = googleFontDownloadUrlFromCss(css)
                ?: throw IllegalStateException("Could not parse a download link for $normalizedFontName.")
            val extension = googleFontFileExtension(fontUrl)
            if (extension !in SupportedFontExtensions) {
                throw IllegalStateException("Unsupported format ($extension) returned for $normalizedFontName.")
            }

            val tempFile = File.createTempFile("episteme_google_font_", ".$extension")
            try {
                val fontConnection = URL(fontUrl).openConnection() as HttpURLConnection
                fontConnection.connectTimeout = 15_000
                fontConnection.readTimeout = 30_000
                fontConnection.inputStream.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                importFont(tempFile, displayNameOverride = normalizedFontName).getOrThrow()
            } finally {
                tempFile.delete()
            }
        }
    }

    companion object {
        private val SupportedFontExtensions = setOf("ttf", "otf", "woff2")
        private const val GoogleFontsResource = "google_fonts.json"
        private const val GoogleFontsSafariUserAgent =
            "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; en-us) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1"

        fun defaultFontsDir(): File {
            return File(desktopUserDataRoot(), "custom_fonts")
        }
    }
}

internal fun googleFontDownloadUrlFromCss(css: String): String? {
    return Regex("""url\((https://[^)]+)\)""")
        .find(css)
        ?.groupValues
        ?.getOrNull(1)
}

internal fun googleFontFileExtension(fontUrl: String): String {
    return fontUrl.substringBefore('?')
        .substringAfterLast('.', "ttf")
        .lowercase()
}

internal fun googleFontsFromJson(rawJson: String): List<String> {
    return Json.parseToJsonElement(rawJson)
        .jsonArray
        .mapNotNull { element ->
            runCatching { element.jsonPrimitive.content.trim().takeIf { it.isNotBlank() } }.getOrNull()
        }
}
