package com.aryan.reader.shared.pdf

import kotlin.math.roundToInt

data class SharedPdfReflowTextSpan(
    val text: String,
    val size: Float,
    val isBold: Boolean,
    val isItalic: Boolean
)

sealed interface SharedPdfReflowPageElement {
    val yPos: Float
}

data class SharedPdfReflowTextElement(
    val line: SharedPdfReflowTextLine,
    override val yPos: Float = line.yPos
) : SharedPdfReflowPageElement

data class SharedPdfReflowImageElement(
    val base64Data: String,
    val width: Int,
    val height: Int,
    override val yPos: Float,
    val mimeType: String = "image/jpeg"
) : SharedPdfReflowPageElement

data class SharedPdfReflowTextLine(
    val spans: List<SharedPdfReflowTextSpan>,
    val yPos: Float,
    val charCount: Int
)

data class SharedPdfReflowPage(
    val pageNumber: Int,
    val elements: List<SharedPdfReflowPageElement>
)

object SharedPdfReflowHtml {
    fun buildGlobalHtmlHeader(): String = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
        body { font-family: sans-serif; line-height: 1.65; padding: 1em; max-width: 100%; margin: 0; }
        h1 { font-size: 1.9em; font-weight: bold; margin: 1.2em 0 0.4em; }
        h2 { font-size: 1.55em; font-weight: bold; margin: 1.1em 0 0.35em; }
        h3 { font-size: 1.3em; font-weight: bold; margin: 1.0em 0 0.3em; }
        h4 { font-size: 1.1em; font-weight: bold; margin: 0.9em 0 0.25em; }
        p { margin: 0.5em 0; }
        ul, ol { padding-left: 1.5em; margin: 0.5em 0; }
        li { margin-bottom: 0.2em; }
        hr { border: none; border-top: 1px solid currentColor; opacity: 0.25; margin: 1.4em 0; }
        .page-section { margin-bottom: 0.5em; }
        .page-marker { opacity: 0.4; font-size: 0.72em; margin-bottom: 1.2em; letter-spacing: 0.04em; }
        .page-divider { border: none; border-top: 1px solid currentColor; opacity: 0.12; margin: 2em 0 1.5em; }
        </style>
        </head>
        <body>
    """.trimIndent() + "\n"

    fun buildGlobalHtmlFooter(): String = "\n</body>\n</html>\n"

    fun buildPageHtml(
        page: SharedPdfReflowPage,
        headerFooterStrings: Set<String> = emptySet()
    ): String {
        if (page.elements.isEmpty()) return buildEmptyPageSection(page.pageNumber)

        val textElements = page.elements.filterIsInstance<SharedPdfReflowTextElement>()
        if (textElements.isEmpty()) {
            return buildPageHtmlFromElements(
                pageNumber = page.pageNumber,
                elements = page.elements,
                headerFooterStrings = headerFooterStrings,
                baseSize = 12f,
                wrapThreshold = 64
            )
        }

        val sizeFreq = HashMap<Int, Int>()
        textElements.forEach { textElement ->
            textElement.line.spans.forEach { span ->
                val size = span.size.roundToInt().coerceAtLeast(1)
                sizeFreq[size] = (sizeFreq[size] ?: 0) + span.text.length
            }
        }
        val baseSize = sizeFreq.maxByOrNull { it.value }?.key?.toFloat() ?: 12f

        val lineLengths = textElements
            .filter { it.line.charCount > 10 }
            .map { it.line.charCount }
            .sorted()
        val typicalLineLen = if (lineLengths.isNotEmpty()) {
            lineLengths[(lineLengths.size * 0.80).toInt().coerceAtMost(lineLengths.size - 1)]
        } else {
            80
        }
        val wrapThreshold = (typicalLineLen * 0.80).toInt()

        return buildPageHtmlFromElements(
            pageNumber = page.pageNumber,
            elements = page.elements,
            headerFooterStrings = headerFooterStrings,
            baseSize = baseSize,
            wrapThreshold = wrapThreshold
        )
    }

    fun buildEmptyPageSection(pageNumber: Int): String =
        "<section class=\"page-section\">\n" +
            "<p class=\"page-marker\">-- Page $pageNumber --</p>\n" +
            "<p><em>(No text on this page)</em></p>\n</section>\n"

    fun buildFallbackPageSection(pageNumber: Int, rawText: String): String =
        "<section class=\"page-section\">\n" +
            "<p class=\"page-marker\">-- Page $pageNumber --</p>\n" +
            "<p>${rawText.escapeSharedPdfReflowHtml()}</p>\n</section>\n"

    fun detectRepeatingHeaderFooter(samplePageLines: List<List<String>>): Set<String> {
        if (samplePageLines.size < 5) return emptySet()
        val frequency = HashMap<String, Int>()
        samplePageLines.forEach { lines ->
            val edgeLines = lines
                .map { it.trim() }
                .filter { it.length > 2 }
                .let { cleanLines -> cleanLines.take(2) + cleanLines.takeLast(2) }
            edgeLines.forEach { line ->
                frequency[line] = (frequency[line] ?: 0) + 1
            }
        }
        return frequency.filter { it.value >= 3 }.keys
    }

    private fun buildPageHtmlFromElements(
        pageNumber: Int,
        elements: List<SharedPdfReflowPageElement>,
        headerFooterStrings: Set<String>,
        baseSize: Float,
        wrapThreshold: Int
    ): String {
        val sb = StringBuilder()
        sb.append("<section class=\"page-section\">\n")
        sb.append("<p class=\"page-marker\">-- Page $pageNumber --</p>\n")

        var inParagraph = false
        var inUl = false
        var inOl = false
        var inLi = false

        fun closeParagraph() {
            if (inParagraph) {
                sb.append("</p>\n")
                inParagraph = false
            }
        }

        fun closeLi() {
            if (inLi) {
                sb.append("</li>\n")
                inLi = false
            }
        }

        fun closeList() {
            closeLi()
            if (inUl) {
                sb.append("</ul>\n")
                inUl = false
            }
            if (inOl) {
                sb.append("</ol>\n")
                inOl = false
            }
        }

        for ((index, element) in elements.withIndex()) {
            when (element) {
                is SharedPdfReflowImageElement -> {
                    closeParagraph()
                    closeList()
                    sb.append("<div style=\"text-align:center; margin: 1.5em 0;\">\n")
                    sb.append(
                        "<img src=\"data:${element.mimeType};base64,${element.base64Data}\" " +
                            "style=\"max-width:100%; height:auto; border-radius: 6px;\"/>\n"
                    )
                    sb.append("</div>\n")
                }

                is SharedPdfReflowTextElement -> {
                    val line = element.line
                    val lineText = line.spans.joinToString("") { it.text }
                    val trimmed = lineText.trim()

                    if (trimmed.isEmpty() || headerFooterStrings.any { it.equals(trimmed, ignoreCase = true) }) {
                        closeParagraph()
                        continue
                    }

                    val maxSize = line.spans
                        .filter { it.text.isNotBlank() }
                        .maxOfOrNull { it.size }
                        ?: baseSize
                    val headingLevel = when {
                        maxSize > baseSize * 1.6f -> 1
                        maxSize > baseSize * 1.28f -> 2
                        maxSize > baseSize * 1.10f -> 3
                        maxSize > baseSize * 1.04f -> 4
                        else -> 0
                    }

                    val lineLen = trimmed.length
                    val isShort = lineLen < 60
                    val isAllCaps = isShort &&
                        lineLen >= 3 &&
                        trimmed.any { it.isLetter() } &&
                        trimmed.all { it.isUpperCase() || !it.isLetter() } &&
                        !trimmed.endsWith(".")
                    val isBullet = trimmed.startsWith("* ") ||
                        trimmed.startsWith("- ") && trimmed.length > 2 && !trimmed.startsWith("--") ||
                        trimmed.startsWith("\u2022") ||
                        trimmed.startsWith("\u25AA") ||
                        trimmed.startsWith("\u25E6") ||
                        trimmed.startsWith("\u2013")
                    val numberedMatch = Regex("""^(\d{1,3}[.)]\s|\p{L}[.)]\s)""").containsMatchIn(trimmed)
                    val isHr = isShort &&
                        trimmed.length >= 3 &&
                        trimmed.all { it == '-' || it == '=' || it == '_' || it.isWhitespace() }

                    val effectiveHeading = when {
                        headingLevel > 0 -> headingLevel
                        isAllCaps && !isBullet && !numberedMatch -> 2
                        else -> 0
                    }

                    val nextTextElement = elements
                        .drop(index + 1)
                        .firstOrNull {
                            it is SharedPdfReflowTextElement &&
                                it.line.spans.joinToString("") { span -> span.text }.isNotBlank()
                        } as? SharedPdfReflowTextElement
                    val nextLineStartsNewThought = nextTextElement != null &&
                        nextTextElement.line.spans.joinToString("") { it.text }
                            .trimStart()
                            .let { it.startsWith("\"") || it.startsWith("-") || it.startsWith("\u201C") }
                    val shouldBreakParagraph = effectiveHeading > 0 ||
                        isBullet ||
                        numberedMatch ||
                        isHr ||
                        lineLen < wrapThreshold ||
                        trimmed.last().let { it == '.' || it == '!' || it == '?' || it == ':' || it == '"' || it == '\u201D' } ||
                        nextLineStartsNewThought

                    when {
                        isHr -> {
                            closeParagraph()
                            closeList()
                            sb.append("<hr>\n")
                        }

                        effectiveHeading > 0 -> {
                            closeParagraph()
                            closeList()
                            val tag = "h${effectiveHeading.coerceIn(1, 4)}"
                            sb.append("<$tag>${renderSpans(line.spans, insideHeading = true)}</$tag>\n")
                        }

                        isBullet -> {
                            closeParagraph()
                            closeLi()
                            if (inOl) {
                                sb.append("</ol>\n")
                                inOl = false
                            }
                            if (!inUl) {
                                sb.append("<ul>\n")
                                inUl = true
                            }
                            val content = trimmed
                                .removePrefix("* ")
                                .removePrefix("\u2022")
                                .removePrefix("\u25AA")
                                .removePrefix("\u25E6")
                                .removePrefix("\u2013")
                                .removePrefix("- ")
                                .trim()
                            sb.append("<li>${content.escapeSharedPdfReflowHtml()}")
                            inLi = true
                        }

                        numberedMatch -> {
                            closeParagraph()
                            closeLi()
                            if (inUl) {
                                sb.append("</ul>\n")
                                inUl = false
                            }
                            if (!inOl) {
                                sb.append("<ol>\n")
                                inOl = true
                            }
                            val content = trimmed.substringAfter(" ").trim()
                            sb.append("<li>${content.escapeSharedPdfReflowHtml()}")
                            inLi = true
                        }

                        shouldBreakParagraph -> {
                            if (inLi) {
                                sb.append(" ").append(renderSpans(line.spans))
                                closeLi()
                            } else {
                                closeList()
                                if (!inParagraph) {
                                    sb.append("<p>")
                                    inParagraph = true
                                }
                                sb.append(renderSpans(line.spans))
                                closeParagraph()
                            }
                        }

                        else -> {
                            if (inLi) {
                                sb.append(" ").append(renderSpans(line.spans))
                            } else {
                                closeList()
                                if (!inParagraph) {
                                    sb.append("<p>")
                                    inParagraph = true
                                } else {
                                    sb.append(" ")
                                }
                                sb.append(renderSpans(line.spans))
                            }
                        }
                    }
                }
            }
        }

        closeParagraph()
        closeList()
        sb.append("</section>\n")
        return sb.toString()
    }

    private fun renderSpans(spans: List<SharedPdfReflowTextSpan>, insideHeading: Boolean = false): String {
        val sb = StringBuilder()
        for (span in spans) {
            val escaped = span.text.escapeSharedPdfReflowHtml()
            if (escaped.isBlank()) {
                sb.append(escaped)
                continue
            }

            val leadCount = escaped.length - escaped.trimStart().length
            val trailCount = escaped.length - escaped.trimEnd().length
            val pre = escaped.take(leadCount)
            val post = if (trailCount > 0) escaped.takeLast(trailCount) else ""
            val mid = escaped.substring(leadCount, escaped.length - trailCount)

            if (mid.isEmpty()) {
                sb.append(escaped)
                continue
            }

            sb.append(pre)
            if (!insideHeading) {
                when {
                    span.isBold && span.isItalic -> sb.append("<strong><em>")
                    span.isBold -> sb.append("<strong>")
                    span.isItalic -> sb.append("<em>")
                }
            }
            sb.append(mid)
            if (!insideHeading) {
                when {
                    span.isBold && span.isItalic -> sb.append("</em></strong>")
                    span.isBold -> sb.append("</strong>")
                    span.isItalic -> sb.append("</em>")
                }
            }
            sb.append(post)
        }
        return sb.toString()
    }
}

private fun String.escapeSharedPdfReflowHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
