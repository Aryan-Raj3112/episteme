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
import java.util.zip.ZipInputStream

class Fb2Parser(private val context: Context) {

    suspend fun createFb2Book(
        inputStream: InputStream,
        originalBookNameHint: String
    ): EpubBook {
        val bookId = originalBookNameHint.hashCode().toString()
        val extractionDir = File(context.cacheDir, "imported_file_$bookId").apply {
            if (!exists()) mkdirs()
        }

        // Seamless ZIP extraction for .fb2.zip extensions
        var streamToParse = inputStream
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
        var currentChapterTitle = "Chapter"
        var chapterCount = 0
        var inSection = false
        var inBody = false
        var inTitle = false
        var skipElement = false
        val titleBuilder = java.lang.StringBuilder() // Buffer to handle <p> tags inside <title>

        val cssStyle = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            p { margin-bottom: 1em; text-indent: 1.5em; text-align: justify; }
            h1, h2, h3, h4 { text-align: center; margin-top: 1.5em; margin-bottom: 1em; }
            .empty-line { height: 1.5em; }
            img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
            .epigraph { margin-left: 2em; font-style: italic; margin-bottom: 1.5em; }
        """.trimIndent()

        fun saveChapter() {
            if (currentChapterHtml.isEmpty()) return
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
                            val nameAttr = parser.getAttributeValue(null, "name")
                            if (nameAttr == "notes" || nameAttr == "comments") {
                                skipElement = true
                            } else {
                                inBody = true
                            }
                        }
                        "section" -> {
                            if (inBody && !skipElement) {
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
                        "p" -> if (!inTitle) currentChapterHtml.append("<p>")
                        "v" -> if (!inTitle) currentChapterHtml.append("<p style='text-indent: 0;'>")
                        "subtitle" -> currentChapterHtml.append("<h3>")
                        "empty-line" -> currentChapterHtml.append("<div class='empty-line'></div>")
                        "strong" -> currentChapterHtml.append("<b>")
                        "emphasis" -> currentChapterHtml.append("<i>")
                        "strikethrough" -> currentChapterHtml.append("<s>")
                        "sup" -> currentChapterHtml.append("<sup>")
                        "sub" -> currentChapterHtml.append("<sub>")
                        "epigraph" -> currentChapterHtml.append("<div class='epigraph'>")
                        "image" -> {
                            // Safely extract href checking all possible namespace stripped versions
                            val href = parser.getAttributeValue(null, "l:href")
                                ?: parser.getAttributeValue(null, "xlink:href")
                                ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                ?: parser.getAttributeValue(null, "href")

                            if (href != null) {
                                val id = href.removePrefix("#")
                                if (!inBody) {
                                    coverImageId = id
                                } else {
                                    currentChapterHtml.append("<img src=\"$id\" />")
                                }
                            }
                        }
                        "binary" -> {
                            val id = parser.getAttributeValue(null, "id")
                            if (id != null) {
                                val base64Data = parser.nextText()
                                try {
                                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    val imgFile = File(extractionDir, id)
                                    withContext(Dispatchers.IO) {
                                        FileOutputStream(imgFile).use { it.write(bytes) }
                                    }

                                    // Add the image to the EpubBook image index
                                    images.add(EpubImage(absPath = id))

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
                        } else if (inBody && !skipElement) {
                            currentChapterHtml.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    when (name) {
                        "body" -> {
                            skipElement = false
                            inBody = false
                        }
                        "title" -> {
                            if (inTitle) {
                                currentChapterTitle = titleBuilder.toString().trim()
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
                    }
                }
            }

            // Calling nextText() moves the parser directly to END_TAG.
            // We ensure we don't accidentally read past the EOF.
            if (eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next()
            }
        }

        saveChapter() // Save the final chunk of content

        if (chapters.isEmpty()) {
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

        return EpubBook(
            fileName = originalBookNameHint,
            title = title,
            author = author,
            language = "en",
            coverImage = coverBitmap,
            chapters = chapters,
            chaptersForPagination = chapters,
            images = images, // Extracted images attached!
            pageList = emptyList(),
            tableOfContents = emptyList(),
            extractionBasePath = extractionDir.absolutePath,
            css = emptyMap()
        )
    }
}