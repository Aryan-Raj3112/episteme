package com.aryan.reader.shared.docparse

/**
 * Portable Markdown-to-HTML converter covering the syntax set Android's
 * Flexmark pipeline enables: CommonMark core plus tables, strikethrough, task
 * lists, and autolinks. Headings materialize as chapters exactly like
 * Android's `markdownSections`: every rendered `h1..h6` starts a new section.
 *
 * Documented deviations from Flexmark: raw inline HTML is escaped instead of
 * passed through, reference-style links/images are treated as plain text, and
 * generated heading ids are simple slugs without duplicate suffixes.
 */
internal object SharedMarkdownConverter {

    data class Section(
        val title: String?,
        val depth: Int,
        val html: String,
    )

    fun convert(markdownSource: String): List<Section> {
        val normalized = markdownSource.replace("\r\n", "\n").replace('\r', '\n')
        val renderer = BlockRenderer(normalized.lines())
        val blocks = renderer.renderAll()
        return splitIntoSections(blocks)
    }

    private fun splitIntoSections(blocks: List<String>): List<Section> {
        if (blocks.isEmpty()) return emptyList()
        val sections = mutableListOf<Section>()
        var currentTitle: String? = null
        var currentDepth = 0
        val currentHtml = StringBuilder()

        fun flush() {
            if (currentHtml.isNotBlank()) {
                sections += Section(currentTitle, currentDepth, currentHtml.toString())
            }
            currentHtml.clear()
        }

        blocks.forEach { block ->
            val heading = headingOf(block)
            if (heading != null) {
                flush()
                currentTitle = heading.second.takeIf(String::isNotBlank)
                currentDepth = heading.first - 1
            }
            currentHtml.append(block).append('\n')
        }
        flush()
        return sections
    }

    /** Returns (level, text) when [block] is a rendered heading element. */
    private fun headingOf(block: String): Pair<Int, String>? {
        val match = Regex("""^<h([1-6])(?:\s[^>]*)?>(.*)</h[1-6]>${'$'}""", RegexOption.DOT_MATCHES_ALL).find(block.trimEnd())
            ?: return null
        val innerText = HtmlTagRegex.replace(match.groupValues[2], "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        return match.groupValues[1].toInt() to innerText.trim()
    }

    private val HtmlTagRegex = Regex("<[^>]+>")

    private class BlockRenderer(private val lines: List<String>) {
        var index = 0
        val headingIdCounts = mutableMapOf<String, Int>()

        fun renderAll(): List<String> {
            val blocks = mutableListOf<String>()
            while (index < lines.size) {
                renderNextBlock()?.let(blocks::add)
            }
            return blocks
        }

        private fun peek(): String? = lines.getOrNull(index)

        /** Skips blank lines; returns false at end of input. */
        private fun skipBlank(): Boolean {
            while (index < lines.size && lines[index].isBlank()) index++
            return index < lines.size
        }

        private fun renderNextBlock(): String? {
            if (!skipBlank()) return null
            val line = peek()!!
            val trimmedStart = line.trimStart()
            val indent = line.length - trimmedStart.length

            // Fenced code block
            val fenceMatch = Regex("""^(`{3,}|~{3,})(.*)${'$'}""").find(trimmedStart)
            if (fenceMatch != null && indent < 4) {
                val fenceChar = fenceMatch.groupValues[1][0]
                val fenceLength = fenceMatch.groupValues[1].length
                val info = fenceMatch.groupValues[2].trim()
                index++
                val codeLines = mutableListOf<String>()
                while (index < lines.size) {
                    val candidate = lines[index]
                    val candidateTrimmed = candidate.trimStart()
                    if (candidateTrimmed.isNotEmpty() &&
                        candidateTrimmed[0] == fenceChar &&
                        candidateTrimmed.takeWhile { it == fenceChar }.length >= fenceLength &&
                        candidateTrimmed.dropWhile { it == fenceChar }.isBlank()
                    ) {
                        index++
                        break
                    }
                    codeLines += candidate
                    index++
                }
                val language = info.substringBefore(' ').takeIf(String::isNotBlank)
                val classAttribute = language?.let { """ class="language-${escapeAttribute(it.lowercase())}"""" } ?: ""
                return "<pre><code$classAttribute>${escapeHtml(codeLines.joinToString("\n"))}</code></pre>"
            }

            // ATX heading
            val atxMatch = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*${'$'}""").find(trimmedStart)
            if (atxMatch != null && indent < 4) {
                index++
                val level = atxMatch.groupValues[1].length
                return renderHeading(level, renderInlines(atxMatch.groupValues[2]))
            }

            // Thematic break (checked before setext/list so "---" alone becomes an <hr>)
            if (indent < 4 && isThematicBreak(trimmedStart)) {
                index++
                return "<hr />"
            }

            // Indented code block
            if (indent >= 4) {
                val codeLines = mutableListOf<String>()
                while (index < lines.size) {
                    val candidate = lines[index]
                    val candidateIndent = candidate.length - candidate.trimStart().length
                    if (candidate.isBlank() && lines.getOrNull(index + 1)?.let { it.length - it.trimStart().length >= 4 || it.isBlank() } == true) {
                        codeLines += ""
                        index++
                        continue
                    }
                    if (candidateIndent >= 4) {
                        codeLines += candidate.drop(4)
                        index++
                    } else if (candidate.isBlank()) {
                        index++
                    } else {
                        break
                    }
                }
                while (codeLines.firstOrNull()?.isEmpty() == true) codeLines.removeAt(0)
                while (codeLines.lastOrNull()?.isEmpty() == true) codeLines.removeAt(codeLines.lastIndex)
                if (codeLines.isEmpty()) return null
                return "<pre><code>${escapeHtml(codeLines.joinToString("\n"))}</code></pre>"
            }

            // Blockquote
            if (trimmedStart.startsWith(">")) {
                val quoted = mutableListOf<String>()
                while (index < lines.size) {
                    val candidate = lines[index].trimStart()
                    when {
                        candidate.startsWith(">") -> {
                            quoted += candidate.removePrefix(">").removePrefix(" ")
                            index++
                        }
                        candidate.isNotBlank() && quoted.isNotEmpty() -> {
                            // Lazy continuation
                            quoted += candidate
                            index++
                        }
                        else -> break
                    }
                }
                val inner = BlockRenderer(quoted).renderAll()
                return "<blockquote>\n${inner.joinToString("\n")}\n</blockquote>"
            }

            // Lists (bullet or ordered, possibly nested)
            val listItemMatch = Regex("""^([-+*]|\d{1,9}[.)])(\s+)(.*)${'$'}""").find(trimmedStart)
            if (listItemMatch != null) {
                return renderList(line, indent)
            }

            // Table: a paragraph line containing '|' followed by a delimiter row
            if (trimmedStart.contains('|') && indent < 4) {
                val nextLine = lines.getOrNull(index + 1)?.trim()
                if (nextLine != null && isTableDelimiter(nextLine)) {
                    return renderTable()
                }
            }

            // Paragraph with possible setext heading underline
            val paragraphLines = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) {
                val candidate = lines[index]
                val candidateTrimmed = candidate.trimStart()
                if (paragraphLines.isNotEmpty()) {
                    if (candidateTrimmed.startsWith("#") ||
                        candidateTrimmed.startsWith(">") ||
                        candidateTrimmed.startsWith("```") ||
                        candidateTrimmed.startsWith("~~~") ||
                        isThematicBreak(candidateTrimmed) ||
                        Regex("""^([-+*]|\d{1,9}[.)])\s""").containsMatchIn(candidateTrimmed)
                    ) break
                    if (candidateTrimmed.contains('|') && isTableDelimiter(lines.getOrNull(index + 1)?.trim().orEmpty())) break
                    if ((candidateTrimmed.startsWith("=") || candidateTrimmed.startsWith("-")) &&
                        Regex("""^={1,}\s*${'$'}|^-{1,}\s*${'$'}""").containsMatchIn(candidateTrimmed)
                    ) {
                        // Setext underline
                        val level = if (candidateTrimmed.startsWith("=")) 1 else 2
                        index++
                        return renderHeading(level, renderInlines(paragraphLines.joinToString("\n")))
                    }
                }
                paragraphLines += candidateTrimmed
                index++
            }
            if (paragraphLines.isEmpty()) return null
            val content = paragraphLines.mapIndexedNotNull { position, text ->
                when {
                    text.endsWith("  ") -> renderInlines(text.trimEnd()) + "<br />"
                    text.endsWith("\\") -> renderInlines(text.trimEnd().removeSuffix("\\")) + "<br />"
                    else -> renderInlines(text).takeIf { it.isNotEmpty() || position == 0 }
                }
            }.joinToString("\n")
            return "<p>$content</p>"
        }

        private fun renderHeading(level: Int, innerHtml: String): String {
            val slugSource = HtmlTagRegex.replace(innerHtml, "")
                .lowercase()
                .trim()
                .replace(Regex("[^\\p{L}\\p{N}\\s-]"), "")
                .replace(Regex("[\\s-]+"), "-")
                .trim('-')
                .ifBlank { "heading" }
            val count = headingIdCounts[slugSource] ?: 0
            headingIdCounts[slugSource] = count + 1
            val id = if (count == 0) slugSource else "$slugSource-$count"
            return "<h$level id=\"${escapeAttribute(id)}\">$innerHtml</h$level>"
        }

        private fun isThematicBreak(text: String): Boolean =
            Regex("""^(\*[ ]?){3,}${'$'}|^(-[ ]?){3,}${'$'}|^(_[ ]?){3,}${'$'}""").matches(text)

        private fun isTableDelimiter(text: String): Boolean {
            val cleaned = text.trim().removeSurrounding("|")
            if (!cleaned.contains('-')) return false
            return cleaned.split('|').all { cell ->
                val trimmedCell = cell.trim()
                // GFM delimiter cells need at least one dash with optional alignment colons.
                trimmedCell.matches(Regex("""\:?-+\:?"""))
            }
        }

        private fun renderTable(): String {
            fun splitRow(row: String): List<String> {
                val cleaned = row.trim().removeSurrounding("|")
                return cleaned.split('|').map { cell -> escapePipes(cell.trim()) }
            }

            val alignments = splitRow(lines[index + 1]).map { cell ->
                val left = cell.startsWith(":")
                val right = cell.endsWith(":")
                when {
                    left && right -> " style=\"text-align:center\""
                    right -> " style=\"text-align:right\""
                    left -> " style=\"text-align:left\""
                    else -> ""
                }
            }
            val headerCells = splitRow(lines[index])
            index += 2

            val builder = StringBuilder()
            builder.append("<table>\n<thead>\n<tr>\n")
            headerCells.forEachIndexed { cellIndex, cell ->
                val alignment = alignments.getOrElse(cellIndex) { "" }
                builder.append("<th$alignment>").append(renderInlines(cell)).append("</th>\n")
            }
            builder.append("</tr>\n</thead>\n")
            val bodyRows = mutableListOf<List<String>>()
            while (index < lines.size) {
                val candidate = lines[index]
                if (candidate.isBlank()) break
                val candidateTrimmed = candidate.trimStart()
                if (!candidateTrimmed.contains('|') ||
                    candidateTrimmed.startsWith("```") ||
                    candidateTrimmed.startsWith("#") ||
                    candidateTrimmed.startsWith(">")
                ) break
                bodyRows += splitRow(candidateTrimmed)
                index++
            }
            if (bodyRows.isNotEmpty()) builder.append("<tbody>\n")
            bodyRows.forEach { row ->
                builder.append("<tr>\n")
                row.forEachIndexed { cellIndex, cell ->
                    val alignment = alignments.getOrElse(cellIndex) { "" }
                    builder.append("<td$alignment>").append(renderInlines(cell)).append("</td>\n")
                }
                builder.append("</tr>\n")
            }
            if (bodyRows.isNotEmpty()) builder.append("</tbody>\n")
            builder.append("</table>")
            return builder.toString()
        }

        private fun escapePipes(cell: String): String = cell.replace("\\|", "&#124;")

        private fun renderList(firstLine: String, firstIndent: Int): String {
            val firstMarker = Regex("""^([-+*]|\d{1,9}[.)])""").find(firstLine.trimStart())!!.value
            val ordered = firstMarker.last().isDigit()
            val tag = if (ordered) "ol" else "ul"

            // Collect the raw lines belonging to this list (same marker family and indent band,
            // including deeper indented continuations).
            val itemLines = mutableListOf<String>()
            var currentIndex = index
            while (currentIndex < lines.size) {
                val candidate = lines[currentIndex]
                if (candidate.isBlank()) {
                    // Blank belongs to the list only if another item follows at the same level.
                    val following = lines.getOrNull(currentIndex + 1)?.trimStart()
                    if (following != null && Regex("""^([-+*]|\d{1,9}[.)])\s""").containsMatchIn(following)) {
                        itemLines += ""
                        currentIndex++
                        continue
                    }
                    break
                }
                val candidateIndent = candidate.length - candidate.trimStart().length
                val candidateTrimmed = candidate.trimStart()
                val candidateIsItem = Regex("""^([-+*]|\d{1,9}[.)])\s""").containsMatchIn(candidateTrimmed)
                when {
                    candidateIndent <= firstIndent - 1 -> break
                    candidateIsItem && candidateIndent <= firstIndent -> {
                        itemLines += candidateTrimmed
                        currentIndex++
                    }
                    candidateIndent > firstIndent || !candidateIsItem -> {
                        itemLines += candidateTrimmed
                        currentIndex++
                    }
                }
            }
            index = currentIndex

            // Group collected lines into items at the top marker level: a top-level
            // item line starts a new item; deeper or continuation lines join it.
            val items = mutableListOf<MutableList<String>>()
            itemLines.forEach { line ->
                val isTopLevelItem = Regex("""^([-+*]|\d{1,9}[.)])\s.*${'$'}""").matches(line)
                if (items.isEmpty() || isTopLevelItem) {
                    items += mutableListOf(line)
                } else {
                    items.last().add(line)
                }
            }

            val builder = StringBuilder()
            builder.append("<$tag>\n")
            items.forEach { item ->
                val marker = Regex("""^([-+*]|\d{1,9}[.)])\s""").find(item.first())?.value.orEmpty()
                var contentFirst = item.first().removePrefix(marker).removePrefix(" ")
                val rest = item.drop(1)

                // Task list item
                val taskMatch = Regex("""^\[( |x|X)\]\s+(.*)${'$'}""").find(contentFirst)
                val taskCheckbox = taskMatch?.let {
                    val checked = it.groupValues[1] == "x" || it.groupValues[1] == "X"
                    """<input type="checkbox" disabled=""${if (checked) " checked=\"\"" else ""} /> """
                }
                if (taskMatch != null) {
                    contentFirst = taskMatch.groupValues[2]
                }

                val nestedRenderer = BlockRenderer(rest.map { it })
                val nestedBlocks = nestedRenderer.renderAll()
                builder.append("<li>")
                if (taskCheckbox != null) builder.append(taskCheckbox)
                builder.append(renderInlines(contentFirst))
                if (nestedBlocks.isNotEmpty()) {
                    builder.append('\n').append(nestedBlocks.joinToString("\n"))
                }
                builder.append("</li>\n")
            }
            builder.append("</$tag>")
            return builder.toString()
        }
    }

    /** Renders inline markdown (code spans, emphasis, links, images, autolinks, escapes). */
    internal fun renderInlines(source: String): String {
        var result = ""
        var cursor = 0
        val length = source.length

        while (cursor < length) {
            val char = source[cursor]
            when {
                char == '\\' && cursor + 1 < length && source[cursor + 1] in PunctuationEscapables -> {
                    result += escapeHtmlChar(source[cursor + 1])
                    cursor += 2
                }
                char == '`' -> {
                    val closing = findClosingBacktick(source, cursor)
                    if (closing != null) {
                        var code = source.substring(cursor + 1, closing)
                        if (code.length >= 2 && code.startsWith(" ") && code.endsWith(" ") && code.isNotBlank()) {
                            code = code.substring(1, code.length - 1)
                        }
                        result += "<code>${escapeHtml(code)}</code>"
                        cursor = closing + 1
                    } else {
                        result += escapeHtmlChar(char)
                        cursor++
                    }
                }
                char == '!' && cursor + 1 < length && source[cursor + 1] == '[' -> {
                    val image = parseLinkLike(source, cursor + 1, isImage = true)
                    if (image != null) {
                        val (endIndex, text, destination, title) = image
                        val safeDestination = sanitizeUrl(destination)
                        if (safeDestination.isBlank()) {
                            // Unsafe destinations (javascript:, data:, …) drop the element.
                            result += escapeHtml(text)
                            cursor = endIndex
                        } else {
                            val titleAttribute = title?.let { """ title="${escapeAttribute(it)}"""" } ?: ""
                            result += """<img src="${escapeAttribute(safeDestination)}"$titleAttribute alt="${escapeAttribute(stripMarkdown(text))}" />"""
                            cursor = endIndex
                        }
                    } else {
                        result += escapeHtmlChar(char)
                        cursor++
                    }
                }
                char == '[' -> {
                    val link = parseLinkLike(source, cursor, isImage = false)
                    if (link != null) {
                        val (endIndex, text, destination, title) = link
                        val safeDestination = sanitizeUrl(destination)
                        if (safeDestination.isBlank()) {
                            result += escapeHtml(text)
                            cursor = endIndex
                        } else {
                            val titleAttribute = title?.let { """ title="${escapeAttribute(it)}"""" } ?: ""
                            result += """<a href="${escapeAttribute(safeDestination)}"$titleAttribute>""".trimEnd(' ', '"')
                            result += renderInlines(text)
                            result += "</a>"
                            cursor = endIndex
                        }
                    } else {
                        result += escapeHtmlChar(char)
                        cursor++
                    }
                }
                char == '<' -> {
                    val autolink = Regex("""^<(https?://[^ >]+)>""").find(source.substring(cursor))
                    if (autolink != null) {
                        val url = autolink.groupValues[1]
                        result += """<a href="${escapeAttribute(url)}">${escapeHtml(url)}</a>"""
                        cursor += autolink.value.length
                    } else {
                        result += "&lt;"
                        cursor++
                    }
                }
                char == '&' -> {
                    val entity = Regex("""^&([A-Za-z][A-Za-z0-9]{1,31};|#[0-9]{1,7};|#[xX][0-9A-Fa-f]{1,6};)""").find(source.substring(cursor))
                    if (entity != null) {
                        result += entity.value
                        cursor += entity.value.length
                    } else {
                        result += "&amp;"
                        cursor++
                    }
                }
                char in "*_" -> {
                    val runLength = source.takeWhileFrom(cursor) { it == char }.length
                    val matched = matchEmphasis(source, cursor, runLength, char)
                    if (matched != null) {
                        val (closeIndex, useStrong, closingRun) = matched
                        val inner = renderInlines(source.substring(cursor + runLength, closeIndex))
                        val tag = if (useStrong) "strong" else "em"
                        result += "<$tag>$inner</$tag>"
                        cursor = closeIndex + closingRun
                    } else {
                        result += escapeHtmlChar(char)
                        cursor++
                    }
                }
                char == '~' && source.takeWhileFrom(cursor) { it == '~' }.length >= 2 -> {
                    val closing = source.indexOf("~~", cursor + 2)
                    if (closing >= 0) {
                        result += "<del>${renderInlines(source.substring(cursor + 2, closing))}</del>"
                        cursor = closing + 2
                    } else {
                        result += escapeHtmlChar(char)
                        cursor++
                    }
                }
                char == 'h' || char == 'w' || char == 'f' -> {
                    val bare = Regex("""^(https?://[^\s<>]+|www\.[^\s<>]+)""").find(source.substring(cursor))
                    if (bare != null && (cursor == 0 || source[cursor - 1].isWhitespace() || source[cursor - 1] == '(')) {
                        var url = bare.value.trimEnd('.', ')', '!', '?', ';', ':')
                        val href = if (url.startsWith("www.")) "https://$url" else url
                        result += """<a href="${escapeAttribute(href)}">${escapeHtml(url)}</a>"""
                        cursor += url.length
                    } else {
                        result += escapeHtmlChar(char)
                        cursor++
                    }
                }
                else -> {
                    result += escapeHtmlChar(char)
                    cursor++
                }
            }
        }
        return result
    }

    private fun String.takeWhileFrom(start: Int, predicate: (Char) -> Boolean): String {
        var end = start
        while (end < length && predicate(this[end])) end++
        return substring(start, end)
    }

    /**
     * Finds the closing delimiter run for the emphasis opening at [start].
     * Returns (contentCloseIndex, useStrong, closingRunLength) where content
     * excludes both delimiter runs.
     */
    private fun matchEmphasis(
        source: String,
        start: Int,
        runLength: Int,
        char: Char,
    ): Triple<Int, Boolean, Int>? {
        val needed = minOf(runLength, 2)
        var search = start + runLength
        while (search < source.length) {
            if (source[search] == char) {
                val closeRunEnd = source.takeWhileFrom(search) { it == char }.length.let { search + it }
                val candidateRun = closeRunEnd - search
                val canClose = search > start + runLength && !source[search - 1].isWhitespace()
                if (canClose && candidateRun >= needed) {
                    return Triple(search, needed == 2, needed)
                }
                search = closeRunEnd
            } else {
                search++
            }
        }
        return null
    }

    private fun findClosingBacktick(source: String, opening: Int): Int? {
        val openRun = source.takeWhileFrom(opening) { it == '`' }.length
        var search = opening + openRun
        while (search < source.length) {
            if (source[search] == '`') {
                val closeRun = source.takeWhileFrom(search) { it == '`' }.length
                if (closeRun == openRun) return search
                search += closeRun
            } else {
                search++
            }
        }
        return null
    }

    private fun parseLinkLike(
        source: String,
        bracketStart: Int,
        isImage: Boolean,
    ): Quadruple<Int, String, String, String?>? {
        // Find matching closing bracket allowing one nesting level.
        var depth = 1
        var scan = bracketStart + 1
        var closingBracket = -1
        while (scan < source.length) {
            when (source[scan]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        closingBracket = scan
                        break
                    }
                }
                '\\' -> scan++
            }
            scan++
        }
        if (closingBracket < 0 || closingBracket + 1 >= source.length || source[closingBracket + 1] != '(') return null
        val parenClose = findParenClose(source, closingBracket + 2) ?: return null
        val inside = source.substring(closingBracket + 2, parenClose).trim()
        if (inside.contains(' ') && !inside.contains('"')) return null
        val parts = splitDestinationAndTitle(inside)
        return Quadruple(parenClose + 1, source.substring(bracketStart + 1, closingBracket), parts.first, parts.second)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun findParenClose(source: String, from: Int): Int? {
        var depth = 1
        var scan = from
        while (scan < source.length) {
            when (source[scan]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return scan
                }
            }
            scan++
        }
        return null
    }

    private fun splitDestinationAndTitle(inside: String): Pair<String, String?> {
        val titleMatch = Regex("""^(.*?)[ \t]+["']([^"']*)["']\s*${'$'}""").find(inside)
        if (titleMatch != null && titleMatch.groupValues[1].isNotBlank()) {
            return titleMatch.groupValues[1].trim().trim('<', '>') to titleMatch.groupValues[2]
        }
        return inside.trim().trim('<', '>') to null
    }

    /** Blocks javascript/data destinations like readers do on both platforms. */
    private fun sanitizeUrl(url: String): String {
        val normalized = url.trim()
        val scheme = normalized.substringBefore(':', "").lowercase()
        return if (scheme in setOf("javascript", "vbscript", "data")) "" else normalized
    }

    private fun stripMarkdown(value: String): String = value

    private val PunctuationEscapables = "\\`*_{}[]()#+-.!:|<>~\""

    internal fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { append(escapeHtmlChar(it)) }
    }

    private fun escapeHtmlChar(char: Char): String = when (char) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        else -> char.toString()
    }

    private fun escapeAttribute(value: String): String = escapeHtml(value).replace("\n", " ")
}
