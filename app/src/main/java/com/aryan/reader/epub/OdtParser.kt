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

private data class StyleProps(
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isStrikethrough: Boolean = false,
    var isUnderline: Boolean = false
)

class OdtParser(private val context: Context) {

    suspend fun createOdtBook(
        inputStream: InputStream,
        bookId: String,
        originalBookNameHint: String,
        isFlat: Boolean,
        parseContent: Boolean = true
    ): EpubBook = withContext(Dispatchers.IO) {
        File(context.cacheDir, "imported_file_$bookId").deleteRecursively()
        val extractionDir = File(context.cacheDir, "imported_file_$bookId").apply {
            if (!exists()) mkdirs()
        }

        val mathJaxFileName = "tex-mml-chtml.js"
        val mathJaxFile = File(extractionDir, mathJaxFileName)
        if (!mathJaxFile.exists()) {
            try {
                context.assets.open("mathjax/$mathJaxFileName").use { input ->
                    FileOutputStream(mathJaxFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy MathJax local asset")
            }
        }

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)

        var title = originalBookNameHint.substringBeforeLast(".")
        var author = "Unknown"
        var coverBytes: ByteArray? = null

        val chapters = mutableListOf<EpubChapter>()
        val images = mutableListOf<EpubImage>()

        var currentChapterHtml = StringBuilder()
        var chapterCount = 0

        val cssStyle = """
            body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
            p { margin-bottom: 1em; text-align: justify; }
            h1, h2, h3, h4, h5, h6 { text-align: center; margin-top: 1.5em; margin-bottom: 1em; }
            ul, ol { margin-bottom: 1em; padding-left: 2em; }
            img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
            table { border-collapse: collapse; width: 100%; margin-bottom: 1em; }
            th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
            .footnote { font-size: 0.85em; background-color: #f9f9f9; padding: 0 4px; border: 1px solid #ddd; border-radius: 3px; }
        """.trimIndent()

        val mathJaxScript = """
            <script>
            MathJax = {
              tex: {
                inlineMath: [['\\(', '\\)']],
                displayMath: [['\\[', '\\]']]
              }
            };
            </script>
            <script src="$mathJaxFileName"></script>
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
                    <meta charset="utf-8">
                    <title>${chapterTitle}</title>
                    <style>${cssStyle}</style>
                    $mathJaxScript
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

        val styleMap = mutableMapOf<String, StyleProps>()

        // Helper function to extract styles from styles.xml (or inline FODT)
        fun extractStyles(inputStream: InputStream) {
            try {
                val p = Xml.newPullParser()
                p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                p.setInput(inputStream, null)
                var event = p.eventType
                var currentStyle: String? = null
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "style:style" -> {
                                currentStyle = p.getAttributeValue(null, "style:name")
                                if (currentStyle != null) {
                                    styleMap[currentStyle] = StyleProps()
                                }
                            }
                            "style:text-properties" -> {
                                val props = styleMap[currentStyle]
                                if (props != null) {
                                    if (p.getAttributeValue(null, "fo:font-weight") == "bold") props.isBold = true
                                    if (p.getAttributeValue(null, "fo:font-style") == "italic") props.isItalic = true
                                    val strike = p.getAttributeValue(null, "style:text-line-through-style")
                                    if (strike != null && strike != "none") props.isStrikethrough = true
                                    val under = p.getAttributeValue(null, "style:text-underline-style")
                                    if (under != null && under != "none") props.isUnderline = true
                                }
                            }
                        }
                    }
                    event = p.next()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse styles.xml")
            }
        }

        try {
            if (!isFlat) {
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                var contentXmlBytes: ByteArray? = null
                var stylesXmlBytes: ByteArray? = null
                val ignoredFiles = setOf("meta.xml", "settings.xml", "META-INF/manifest.xml")

                while (entry != null) {
                    if (!entry.isDirectory) {
                        when (entry.name) {
                            "content.xml" -> contentXmlBytes = zis.readBytes()
                            "styles.xml" -> stylesXmlBytes = zis.readBytes()
                            "Thumbnails/thumbnail.png" -> coverBytes = zis.readBytes()
                            else -> {
                                if (entry.name !in ignoredFiles) {
                                    val extractedFile = File(extractionDir, entry.name)
                                    extractedFile.parentFile?.mkdirs()
                                    FileOutputStream(extractedFile).use { out -> zis.copyTo(out) }
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }

                // Pre-parse styles if available
                stylesXmlBytes?.let { extractStyles(it.inputStream()) }

                if (contentXmlBytes != null) {
                    parser.setInput(contentXmlBytes.inputStream(), null)
                } else {
                    throw Exception("content.xml not found in ODT archive.")
                }
            } else {
                parser.setInput(inputStream, null)
            }

            var eventType = parser.eventType
            var inOfficeBinaryData = false
            var currentImageHref: String? = null
            val base64Builder = java.lang.StringBuilder()

            var currentParsedStyleName: String? = null
            val spanStack = ArrayDeque<List<String>>()
            val headerStack = ArrayDeque<String>()
            var inMath = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (inMath) {
                            if (name != "math:math") {
                                currentChapterHtml.append("<$name")
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i)
                                    val attrValue = parser.getAttributeValue(i)?.replace("\"", "&quot;")
                                    currentChapterHtml.append(" $attrName=\"$attrValue\"")
                                }
                                currentChapterHtml.append(">")
                            }
                        } else {
                            when (name) {
                                "dc:title" -> {
                                    val t = parser.nextText().trim()
                                    if (t.isNotBlank()) title = t
                                }
                                "dc:creator" -> {
                                    val a = parser.nextText().trim()
                                    if (a.isNotBlank()) author = a
                                }
                                "style:style" -> {
                                    currentParsedStyleName = parser.getAttributeValue(null, "style:name")
                                    if (currentParsedStyleName != null) {
                                        styleMap[currentParsedStyleName] = StyleProps()
                                    }
                                }
                                "style:text-properties" -> {
                                    val props = styleMap[currentParsedStyleName]
                                    if (props != null) {
                                        val weight = parser.getAttributeValue(null, "fo:font-weight")
                                        if (weight == "bold") props.isBold = true

                                        val style = parser.getAttributeValue(null, "fo:font-style")
                                        if (style == "italic") props.isItalic = true

                                        val lineThrough = parser.getAttributeValue(null, "style:text-line-through-style")
                                        if (lineThrough != null && lineThrough != "none") props.isStrikethrough = true

                                        val underline = parser.getAttributeValue(null, "style:text-underline-style")
                                        if (underline != null && underline != "none") props.isUnderline = true
                                    }
                                }
                                "text:h" -> {
                                    val levelStr = parser.getAttributeValue(null, "text:outline-level")
                                    val level = levelStr?.toIntOrNull() ?: 2
                                    val hTag = "h${level.coerceIn(1, 6)}"
                                    headerStack.addLast(hTag)
                                    currentChapterHtml.append("<$hTag>")
                                }
                                "text:p" -> currentChapterHtml.append("<p>")
                                "text:span" -> {
                                    val styleName = parser.getAttributeValue(null, "text:style-name")
                                    val props = styleMap[styleName]
                                    val openedTags = mutableListOf<String>()
                                    if (props != null) {
                                        if (props.isBold) { currentChapterHtml.append("<b>"); openedTags.add("b") }
                                        if (props.isItalic) { currentChapterHtml.append("<i>"); openedTags.add("i") }
                                        if (props.isUnderline) { currentChapterHtml.append("<u>"); openedTags.add("u") }
                                        if (props.isStrikethrough) { currentChapterHtml.append("<s>"); openedTags.add("s") }
                                    }
                                    spanStack.addLast(openedTags)
                                }
                                "text:a" -> {
                                    val href = parser.getAttributeValue(null, "xlink:href") ?: ""
                                    currentChapterHtml.append("<a href=\"$href\">")
                                }
                                "text:list" -> currentChapterHtml.append("<ul>\n")
                                "text:list-item" -> currentChapterHtml.append("<li>")
                                "table:table" -> currentChapterHtml.append("<table>")
                                "table:table-row" -> currentChapterHtml.append("<tr>")
                                "table:table-cell" -> {
                                    val colspan = parser.getAttributeValue(null, "table:number-columns-spanned") ?: "1"
                                    val rowspan = parser.getAttributeValue(null, "table:number-rows-spanned") ?: "1"
                                    currentChapterHtml.append("<td colspan=\"$colspan\" rowspan=\"$rowspan\">")
                                }
                                "text:line-break" -> currentChapterHtml.append("<br/>")
                                "text:tab" -> currentChapterHtml.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                                "text:note" -> currentChapterHtml.append("<span class=\"footnote\">[Note: ")
                                "math:math" -> {
                                    inMath = true
                                    currentChapterHtml.append("<math xmlns=\"http://www.w3.org/1998/Math/MathML\">")
                                }
                                "draw:image" -> {
                                    val href = parser.getAttributeValue(null, "xlink:href") ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                                    if (href != null) {
                                        if (isFlat) {
                                            currentImageHref = href.substringAfterLast("/")
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
                    }
                    XmlPullParser.TEXT -> {
                        if (inOfficeBinaryData) {
                            base64Builder.append(parser.text)
                        } else {
                            val text = parser.text?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;")
                            // isNullOrEmpty correctly preserves single-space characters required for mixing words and inline bold/italic tags
                            if (!text.isNullOrEmpty()) {
                                currentChapterHtml.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (inMath) {
                            if (name == "math:math") {
                                inMath = false
                                currentChapterHtml.append("</math>")
                            } else {
                                currentChapterHtml.append("</$name>")
                            }
                        } else {
                            when (name) {
                                "text:h" -> {
                                    val hTag = if (headerStack.isNotEmpty()) headerStack.removeLast() else "h2"
                                    currentChapterHtml.append("</$hTag>\n")
                                }
                                "text:p" -> {
                                    currentChapterHtml.append("</p>\n")
                                    if (currentChapterHtml.length >= 64 * 1024) {
                                        saveChapter()
                                    }
                                }
                                "text:span" -> {
                                    val openedTags = if (spanStack.isNotEmpty()) spanStack.removeLast() else emptyList()
                                    for (tag in openedTags.reversed()) {
                                        currentChapterHtml.append("</$tag>")
                                    }
                                }
                                "text:a" -> currentChapterHtml.append("</a>")
                                "text:list" -> currentChapterHtml.append("</ul>\n")
                                "text:list-item" -> currentChapterHtml.append("</li>\n")
                                "table:table" -> currentChapterHtml.append("</table>\n")
                                "table:table-row" -> currentChapterHtml.append("</tr>\n")
                                "table:table-cell" -> currentChapterHtml.append("</td>\n")
                                "text:note" -> currentChapterHtml.append("]</span>")
                                "office:binary-data" -> {
                                    inOfficeBinaryData = false
                                    if (isFlat) {
                                        try {
                                            val bytes = Base64.decode(base64Builder.toString(), Base64.DEFAULT)
                                            val imgName = currentImageHref?.substringAfterLast("/") ?: "${UUID.randomUUID()}.png"
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

            val finalCoverBitmap = coverBytes?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode thumbnail bitmap")
                    null
                }
            }

            return@withContext EpubBook(
                fileName = originalBookNameHint,
                title = title,
                author = author,
                language = "en",
                coverImage = finalCoverBitmap,
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