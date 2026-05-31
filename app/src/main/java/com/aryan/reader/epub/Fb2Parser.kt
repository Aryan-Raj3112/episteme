package com.aryan.reader.epub

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class Fb2Parser(private val context: Context) {

    suspend fun createFb2Book(
        inputStream: InputStream,
        bookId: String,
        originalBookNameHint: String,
        parseContent: Boolean = true,
        extractionDirOverride: File? = null
    ): EpubBook = withContext(Dispatchers.IO) {
        val extractionDir = extractionDirOverride?.let(ImportedFileCache::prepareDirectory)
            ?: if (parseContent) {
                ImportedFileCache.prepareActiveBookDir(context, bookId)
            } else {
                ImportedFileCache.createTemporaryBookDir(context, bookId, "metadata")
            }

        var streamToParse = inputStream
        try {
            if (originalBookNameHint.endsWith(".zip", ignoreCase = true)) {
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".fb2", ignoreCase = true)) {
                        break
                    }
                    entry = zis.nextEntry
                }
                if (entry != null) {
                    streamToParse = zis
                } else {
                    throw Exception("No .fb2 file found inside the ZIP archive.")
                }
            }

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(streamToParse, null)

            var title = originalBookNameHint.substringBeforeLast(".")
            var author = "Unknown"
            var coverImageId: String? = null
            var coverBytes: ByteArray? = null

            val chapters = mutableListOf<EpubChapter>()
            val images = mutableListOf<EpubImage>() // Keep track of extracted images

            var currentChapterHtml = StringBuilder()
            var currentChapterTitle = "Chapter 1"
            var chapterCount = 0
            var inSection = false
            var inBody = false
            var inTitle = false
            val titleBuilder = java.lang.StringBuilder() // Buffer to handle <p> tags inside <title>

            val cssStyle = """
                body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
                p { margin-bottom: 1em; text-indent: 1.5em; text-align: justify; }
                h1, h2, h3, h4 { text-align: center; margin-top: 1.5em; margin-bottom: 1em; }
                .empty-line { height: 1.5em; }
                img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
                .epigraph { margin-left: 2em; font-style: italic; margin-bottom: 1.5em; }
                .cite { border-left: 4px solid currentColor; padding-left: 1em; margin-left: 0; opacity: 0.8; font-style: italic; }
                .poem { margin: 1.5em 0; padding-left: 2em; }
                .stanza { margin-bottom: 1em; }
            """.trimIndent()

            fun saveChapter() {
                if (!parseContent || currentChapterHtml.isEmpty()) return
                chapterCount++
                val fileName = "chapter_$chapterCount.html"
                val file = File(extractionDir, fileName)

                val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>${currentChapterTitle.replace("\"", "&quot;")}</title>
                        <style>${cssStyle}</style>
                    </head>
                    <body>
                    $currentChapterHtml
                    </body>
                    </html>
                """.trimIndent()

                FileOutputStream(file).use { it.write(fullHtml.toByteArray()) }
                val plainText = Jsoup.parse(fullHtml).text()

                chapters.add(
                    EpubChapter(
                        chapterId = "${bookId}_${chapterCount}",
                        absPath = fileName,
                        title = currentChapterTitle,
                        htmlFilePath = fileName,
                        plainTextContent = plainText,
                        htmlContent = "",
                        depth = 0,
                        isInToc = true
                    )
                )
                currentChapterHtml.clear()
                currentChapterTitle = "Chapter ${chapterCount + 1}"
            }

            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        when (name) {
                            "book-title" -> {
                                title = parser.nextText().trim()
                            }
                            "first-name", "last-name", "middle-name" -> {
                                val namePart = parser.nextText().trim()
                                if (namePart.isNotBlank()) {
                                    if (author == "Unknown") author = namePart else author += " $namePart"
                                }
                            }
                            "body" -> {
                                inBody = true
                            }
                            "section" -> {
                                if (inBody) {
                                    if (currentChapterHtml.isNotBlank()) {
                                        saveChapter()
                                    }
                                    inSection = true
                                }
                            }
                            "title" -> {
                                if (inSection && currentChapterHtml.isEmpty()) {
                                    inTitle = true
                                    titleBuilder.clear()
                                }
                                currentChapterHtml.append("<h2>")
                            }
                            "p" -> {
                                if (!inTitle) {
                                    currentChapterHtml.append("<p>")
                                } else if (titleBuilder.isNotEmpty()) {
                                    titleBuilder.append(" ")
                                    currentChapterHtml.append("<br>")
                                }
                            }
                            "v" -> {
                                if (!inTitle) {
                                    currentChapterHtml.append("<p style='text-indent: 0; text-align: left;'>")
                                } else if (titleBuilder.isNotEmpty()) {
                                    titleBuilder.append(" ")
                                    currentChapterHtml.append("<br>")
                                }
                            }
                            "subtitle" -> currentChapterHtml.append("<h3>")
                            "empty-line" -> {
                                if (!inTitle) {
                                    currentChapterHtml.append("<div class='empty-line'></div>")
                                } else if (titleBuilder.isNotEmpty()) {
                                    titleBuilder.append(" ")
                                    currentChapterHtml.append("<br>")
                                }
                            }
                            "strong" -> currentChapterHtml.append("<b>")
                            "emphasis" -> currentChapterHtml.append("<i>")
                            "strikethrough" -> currentChapterHtml.append("<s>")
                            "sup" -> currentChapterHtml.append("<sup>")
                            "sub" -> currentChapterHtml.append("<sub>")
                            "epigraph" -> currentChapterHtml.append("<div class='epigraph'>")
                            "cite" -> currentChapterHtml.append("<blockquote class='cite'>")
                            "poem" -> currentChapterHtml.append("<div class='poem'>")
                            "stanza" -> currentChapterHtml.append("<div class='stanza'>")
                            "a" -> {
                                val href = parser.getAttributeValue(null, "l:href")
                                    ?: parser.getAttributeValue(null, "xlink:href")
                                    ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                if (!inTitle) {
                                    if (href != null) {
                                        currentChapterHtml.append("<a href=\"$href\">")
                                    } else {
                                        currentChapterHtml.append("<a>")
                                    }
                                }
                            }
                            "image" -> {
                                // Safely extract href checking all possible namespace stripped versions
                                val href = parser.getAttributeValue(null, "l:href")
                                    ?: parser.getAttributeValue(null, "xlink:href")
                                    ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                    ?: parser.getAttributeValue(null, "href")

                                if (href != null) {
                                    val id = href.removePrefix("#")
                                    if (!inBody) {
                                        if (coverImageId == null) coverImageId = id
                                    } else {
                                        val safeImageName = safeResourceFileName(id)
                                        if (safeImageName != null) {
                                            currentChapterHtml.append("<img src=\"$safeImageName\" />")
                                        } else {
                                            Timber.w("Skipping unsafe FB2 image reference: $id")
                                        }
                                    }
                                }
                            }
                            "binary" -> {
                                val id = parser.getAttributeValue(null, "id")
                                if (id != null) {
                                    val base64Data = parser.nextText()
                                    try {
                                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        val safeId = safeResourceFileName(id)
                                        if (parseContent) {
                                            if (safeId != null) {
                                                val imgFile = safeFileInRoot(extractionDir, safeId)
                                                if (imgFile != null) {
                                                    FileOutputStream(imgFile).use { it.write(bytes) }
                                                } else {
                                                    Timber.w("Skipping unsafe FB2 binary path: $id")
                                                }
                                            } else {
                                                Timber.w("Skipping unsafe FB2 binary id: $id")
                                            }
                                        }

                                        if (safeId != null) {
                                            images.add(EpubImage(absPath = safeId))
                                        }

                                        if (id == coverImageId || (coverImageId == null && id.contains("cover", ignoreCase = true))) {
                                            coverBytes = bytes
                                            coverImageId = id
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to decode binary image $id")
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;")
                        if (!text.isNullOrBlank()) {
                            if (inTitle) {
                                titleBuilder.append(text) // Append to buffer since it could be split by <p> tags
                                currentChapterHtml.append(text)
                            } else if (inBody) {
                                currentChapterHtml.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        when (name) {
                            "body" -> {
                                inBody = false
                            }
                            "title" -> {
                                if (inTitle) {
                                    currentChapterTitle = titleBuilder.toString().replace("\\s+".toRegex(), " ").trim()
                                    if (currentChapterTitle.isBlank()) {
                                        currentChapterTitle = "Chapter ${chapterCount + 1}"
                                    }
                                    inTitle = false
                                }
                                currentChapterHtml.append("</h2>\n")
                            }
                            "p", "v" -> if (!inTitle) currentChapterHtml.append("</p>\n")
                            "subtitle" -> currentChapterHtml.append("</h3>\n")
                            "strong" -> currentChapterHtml.append("</b>")
                            "emphasis" -> currentChapterHtml.append("</i>")
                            "strikethrough" -> currentChapterHtml.append("</s>")
                            "sup" -> currentChapterHtml.append("</sup>")
                            "sub" -> currentChapterHtml.append("</sub>")
                            "epigraph" -> currentChapterHtml.append("</div>\n")
                            "cite" -> currentChapterHtml.append("</blockquote>\n")
                            "poem", "stanza" -> currentChapterHtml.append("</div>\n")
                            "a" -> if (!inTitle) currentChapterHtml.append("</a>")
                        }
                    }
                }

                if (eventType != XmlPullParser.END_DOCUMENT) {
                    eventType = parser.next()
                }
            }

            saveChapter()

            if (chapters.isEmpty() && parseContent) {
                if (currentChapterHtml.isNotBlank()) {
                    saveChapter()
                } else {
                    throw Exception("No valid content found in FB2 file.")
                }
            }

            val coverBitmap = coverBytes?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode cover bitmap for FB2")
                    null
                }
            }

            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = title,
                author = author,
                language = "en",
                coverImage = coverBitmap,
                chapters = chapters,
                chaptersForPagination = chapters,
                images = images,
                pageList = emptyList(),
                tableOfContents = emptyList(),
                extractionBasePath = extractionDir.absolutePath,
                css = emptyMap()
            )
        } finally {
            try {
                streamToParse.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing FB2 stream")
            }
        }
    }

    private fun safeResourceFileName(id: String): String? {
        val rawName = id.substringAfterLast('/').substringAfterLast('\\').trim()
        if (rawName.isBlank() || rawName == "." || rawName == "..") return null

        val extension = rawName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() && it.length <= 12 }
            ?.replace(Regex("[^A-Za-z0-9]"), "")
            .orEmpty()
        val baseName = rawName.substringBeforeLast('.', rawName)
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('.', '_', '-')
            .ifBlank { "image" }
            .take(48)
        val suffix = sha256Hex(id).take(12)
        return if (extension.isBlank()) {
            "${baseName}_$suffix"
        } else {
            "${baseName}_$suffix.$extension"
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
