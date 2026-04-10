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
import java.util.UUID
import java.util.zip.ZipInputStream

class OdtParser(private val context: Context) {

    suspend fun createOdtBook(
        inputStream: InputStream,
        bookId: String,
        originalBookNameHint: String,
        isFlat: Boolean,
        parseContent: Boolean = true
    ): EpubBook = withContext(Dispatchers.IO) {
        val extractionDir = File(context.cacheDir, "imported_file_$bookId").apply {
            if (!exists()) mkdirs()
        }

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)

        var title = originalBookNameHint.substringBeforeLast(".")
        var author = "Unknown"
        var coverImageId: String? = null
        var coverBytes: ByteArray? = null

        val chapters = mutableListOf<EpubChapter>()
        val images = mutableListOf<EpubImage>()

        var currentChapterHtml = StringBuilder()
        var chapterCount = 0

        val cssStyle = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            p { margin-bottom: 1em; text-indent: 1.5em; text-align: justify; }
            h1, h2, h3, h4 { text-align: center; margin-top: 1.5em; margin-bottom: 1em; }
            ul, ol { margin-bottom: 1em; padding-left: 2em; }
            img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
        """.trimIndent()

        fun saveChapter() {
            if (!parseContent || currentChapterHtml.isEmpty()) return
            chapterCount++
            val chapterTitle = "Part $chapterCount"
            val fileName = "chapter_$chapterCount.html"
            val file = File(extractionDir, fileName)

            val fullHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>${chapterTitle}</title>
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
                    title = chapterTitle,
                    htmlFilePath = fileName,
                    plainTextContent = plainText,
                    htmlContent = "",
                    depth = 0,
                    isInToc = true
                )
            )
            currentChapterHtml.clear()
        }

        try {
            if (!isFlat) {
                // Handle zipped .odt
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                var contentXmlBytes: ByteArray? = null

                while (entry != null) {
                    if (!entry.isDirectory) {
                        if (entry.name == "content.xml") {
                            contentXmlBytes = zis.readBytes()
                        } else if (entry.name.startsWith("Pictures/")) {
                            val picFile = File(extractionDir, entry.name)
                            picFile.parentFile?.mkdirs()
                            FileOutputStream(picFile).use { out -> zis.copyTo(out) }
                        }
                    }
                    entry = zis.nextEntry
                }

                if (contentXmlBytes != null) {
                    parser.setInput(contentXmlBytes.inputStream(), null)
                } else {
                    throw Exception("content.xml not found in ODT archive.")
                }
            } else {
                // Handle flat .fodt
                parser.setInput(inputStream, null)
            }

            var eventType = parser.eventType
            var inOfficeBinaryData = false
            var currentImageHref: String? = null
            val base64Builder = java.lang.StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        when (name) {
                            "dc:title" -> {
                                val t = parser.nextText().trim()
                                if (t.isNotBlank()) title = t
                            }
                            "dc:creator" -> {
                                val a = parser.nextText().trim()
                                if (a.isNotBlank()) author = a
                            }
                            "text:h" -> currentChapterHtml.append("<h2>")
                            "text:p" -> currentChapterHtml.append("<p>")
                            "text:list" -> currentChapterHtml.append("<ul>\n")
                            "text:list-item" -> currentChapterHtml.append("<li>")
                            "draw:image" -> {
                                val href = parser.getAttributeValue(null, "xlink:href") ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                if (href != null) {
                                    if (isFlat) {
                                        currentImageHref = href.replace("/", "_")
                                    } else {
                                        currentChapterHtml.append("<img src=\"$href\" />")
                                    }
                                }
                            }
                            "office:binary-data" -> {
                                if (isFlat) {
                                    inOfficeBinaryData = true
                                    base64Builder.clear()
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inOfficeBinaryData) {
                            base64Builder.append(parser.text)
                        } else {
                            val text = parser.text?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;")
                            if (!text.isNullOrBlank()) {
                                currentChapterHtml.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        when (name) {
                            "text:h" -> currentChapterHtml.append("</h2>\n")
                            "text:p" -> {
                                currentChapterHtml.append("</p>\n")
                                // Chunking: ~64KB per part
                                if (currentChapterHtml.length >= 64 * 1024) {
                                    saveChapter()
                                }
                            }
                            "text:list" -> currentChapterHtml.append("</ul>\n")
                            "text:list-item" -> currentChapterHtml.append("</li>\n")
                            "office:binary-data" -> {
                                inOfficeBinaryData = false
                                if (isFlat) {
                                    try {
                                        val bytes = Base64.decode(base64Builder.toString(), Base64.DEFAULT)
                                        val imgName = currentImageHref ?: "${UUID.randomUUID()}.png"
                                        val imgFile = File(extractionDir, imgName)
                                        FileOutputStream(imgFile).use { it.write(bytes) }
                                        currentChapterHtml.append("<img src=\"${imgName}\" />")
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to decode FODT image")
                                    }
                                    currentImageHref = null
                                }
                            }
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
                    throw Exception("No valid content found in ODT file.")
                }
            }

            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = title,
                author = author,
                language = "en",
                coverImage = coverBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
                chapters = chapters,
                chaptersForPagination = chapters,
                images = images,
                pageList = emptyList(),
                extractionBasePath = extractionDir.absolutePath,
                css = emptyMap()
            )
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing ODT stream")
            }
        }
    }
}