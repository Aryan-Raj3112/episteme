package com.aryan.reader.shared.reader

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.CssRule
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.FontFaceInfo
import com.aryan.reader.paginatedreader.FontFamilyMapper
import com.aryan.reader.paginatedreader.OptimizedCssRules
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.isEbookmakerImageMarkerId
import com.aryan.reader.paginatedreader.readerMissingFigureText
import com.aryan.reader.paginatedreader.withReaderMissingFigure
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticSpan
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTableCell
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.UserAgentStylesheet
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Converts a chapter's (sanitized and resource-rewritten) XHTML body into the same
 * [SemanticBlock] model the desktop and Android readers use. Block CFIs are produced
 * with the Android path algorithm ([SharedSemanticDomElement.cfiPath]), and
 * `startCharOffsetInSource` aligns each block to the chapter's normalized plain text
 * ([normalizeEpubWhitespace] of the tag-space transform), keeping search and
 * text-range locators consistent with [SharedEpubChapter.plainText].
 */
internal fun sharedEpubHtmlToSemanticBlocks(html: String): List<SemanticBlock> {
    return sharedEpubHtmlToSemanticBlocks(html, SharedSemanticCssSnapshot.EMPTY)
}

/**
 * Book CSS applied to the shared semantic model (Android benchmark parity).
 *
 * The Android paginated reader styles every chapter with the merged book CSS
 * (`PaginatedReader` merges the user-agent sheet + all book stylesheets, then
 * `SharedContentStyler`/`htmlToSemanticBlocks` resolve each element). The
 * shared loader used to skip CSS entirely, so publisher typography (heading
 * sizes, margins, alignment, floats, table styling) never reached iOS
 * pagination or rendering. Rules are matched here against the lightweight
 * shared DOM; text, CFIs and source offsets are untouched, so locators,
 * search and TTS stay aligned.
 */
internal fun sharedEpubHtmlToSemanticBlocks(
    html: String,
    css: SharedSemanticCssSnapshot,
    baseFontSizeSp: Float = SharedSemanticCssBaseFontSizeSp,
    density: Float = SharedSemanticCssDensity,
    constraints: Constraints = SharedSemanticCssConstraints
): List<SemanticBlock> {
    val document = SharedSemanticHtmlDocument.parse(html) ?: return emptyList()
    return SharedEpubSemanticBlockBuilder(document, css, baseFontSizeSp, density, constraints).build()
}

/** Pre-parsed book CSS for [sharedEpubHtmlToSemanticBlocks]. */
internal data class SharedSemanticCssSnapshot(
    val rules: OptimizedCssRules,
    val fontFaces: List<FontFaceInfo>
) {
    companion object {
        val EMPTY = SharedSemanticCssSnapshot(OptimizedCssRules(), emptyList())
    }
}

/**
 * Merges the user-agent sheet with every collected book stylesheet, in
 * deterministic order (user-agent, manifest stylesheets, embedded chapter
 * styles). Metric-sensitive values (% of viewport, em of the reader size)
 * resolve against the passed metrics, so callers choose the baseline:
 * nominal (18sp, density 1, 980x720, like `SharedJvmBookLoader.chapterFromHtml`)
 * for load-time blocks, or the live reader size / density / viewport for
 * pagination (like `BookPaginator`, which re-parses per pagination config).
 */
internal fun parseSharedSemanticCss(
    bookCss: Map<String, String>,
    baseFontSizeSp: Float = 18f,
    density: Float = 1f,
    constraints: Constraints = Constraints(maxWidth = 980, maxHeight = 720)
): SharedSemanticCssSnapshot {
    var rules = OptimizedCssRules()
    val fontFaces = mutableListOf<FontFaceInfo>()
    // Each CssParser.parse call restarts block source orders at zero, so ties
    // across stylesheets would interleave UA rules with author rules. Re-stamp
    // orders globally (user-agent, then stylesheets in order) so the cascade
    // resolves cross-file ties by stylesheet order, author over user-agent.
    var orderBase = 0
    fun rebase(parsed: OptimizedCssRules): OptimizedCssRules {
        fun List<CssRule>.rebased(): List<CssRule> =
            map { rule -> rule.copy(sourceOrder = rule.sourceOrder + orderBase) }
        val rebased = OptimizedCssRules(
            byTag = parsed.byTag.mapValues { (_, bucket) -> bucket.rebased() },
            byClass = parsed.byClass.mapValues { (_, bucket) -> bucket.rebased() },
            byId = parsed.byId.mapValues { (_, bucket) -> bucket.rebased() },
            otherComplex = parsed.otherComplex.rebased(),
            allRules = parsed.allRules.rebased()
        )
        orderBase += (parsed.allRules.maxOfOrNull { it.sourceOrder } ?: -1) + 1
        return rebased
    }
    fun accumulate(cssContent: String, cssPath: String?) {
        if (cssContent.isBlank()) return
        runCatching {
            val parsed = CssParser.parse(
                cssContent = cssContent,
                cssPath = cssPath,
                baseFontSizeSp = baseFontSizeSp,
                density = density,
                constraints = constraints,
                isDarkTheme = false,
                adaptThemeColors = false
            )
            rules = rules.merge(rebase(parsed.rules))
            fontFaces += parsed.fontFaces
        }
    }
    accumulate(UserAgentStylesheet.default, cssPath = null)
    bookCss.forEach { (path, content) ->
        accumulate(content, path.substringBeforeLast('#').takeIf { it.isNotBlank() })
    }
    return SharedSemanticCssSnapshot(rules, fontFaces)
}

// Nominal styling baseline, mirroring `SharedJvmBookLoader.chapterFromHtml`
// (18sp, density 1, 980x720). dp/em values resolve against the live reader
// settings at paginate/render time, so load-time parsing stays viewport
// independent; only px-absolute and container-% values bake the nominal
// baseline, exactly like the JVM loader.
private const val SharedSemanticCssBaseFontSizeSp = 18f
private const val SharedSemanticCssDensity = 1f
private val SharedSemanticCssConstraints = Constraints(maxWidth = 980, maxHeight = 720)

private val SharedSemanticCssClassSplitRegex = Regex("\\s+")
private val SharedSemanticCssPreWhiteSpace = setOf("pre", "pre-wrap", "pre-line", "break-spaces")

internal fun SharedSemanticCssWhiteSpacePreserves(raw: String?): Boolean {
    return raw?.trim()?.lowercase() in SharedSemanticCssPreWhiteSpace
}

/**
 * True when a computed inline style carries anything the span pipeline
 * consumes (typeface, size, color, decoration, spacing). Block-only
 * properties (margins, padding, alignment) must not force span creation.
 */
private fun CssStyle.hasSharedSemanticSpanEffect(): Boolean {
    if (spanStyle != SpanStyle()) return true
    if (fontSize.isSpecified) return true
    if (fontFamilies.isNotEmpty()) return true
    if (fontVariantNumeric != null) return true
    if (verticalAlign != null) return true
    if (wordSpacing.isSpecified) return true
    return false
}

/**
 * Publication `text-transform` applied at parse, mirroring the JVM parser
 * (`HtmlParser.appendTransformedText`) so both platforms paginate, search and
 * read the same characters. The stored transforms are length-preserving in
 * practice, keeping source offsets and CFIs aligned with the plain text.
 */
private fun String.transformedSharedSemanticText(textTransform: String?): String {
    return when (textTransform?.trim()?.lowercase()) {
        "uppercase" -> uppercase()
        "lowercase" -> lowercase()
        "capitalize" -> split(' ').joinToString(" ") { word ->
            if (word.isNotEmpty()) word.replaceFirstChar { it.titlecase() } else ""
        }
        else -> this
    }
}

private val SharedSemanticCssCascadeComparator =
    compareBy<CssRule> { it.selector.specificity }.thenBy { it.sourceOrder }

private fun mergeSharedSemanticCssRuleBuckets(lists: List<List<CssRule>>): List<CssRule> {
    if (lists.isEmpty()) return emptyList()
    if (lists.size == 1) return lists.single()
    // Buckets arrive pre-sorted; a flat distinct + cascade sort keeps one
    // total order (specificity, then source order) like the JVM merge.
    return lists.flatten().distinct().sortedWith(SharedSemanticCssCascadeComparator)
}

private data class SharedSemanticCssAttributeCondition(
    val name: String,
    val op: String?,
    val value: String?
)

private sealed interface SharedSemanticCssPseudoClass {
    data object FirstChild : SharedSemanticCssPseudoClass
    data object LastChild : SharedSemanticCssPseudoClass
    data class NthChild(val step: Int, val offset: Int, val fromEnd: Boolean) : SharedSemanticCssPseudoClass
    data class Not(val inner: SharedSemanticCssCompound) : SharedSemanticCssPseudoClass
}

private data class SharedSemanticCssCompound(
    val tag: String?,
    val id: String? = null,
    val classes: List<String> = emptyList(),
    val attributes: List<SharedSemanticCssAttributeCondition> = emptyList(),
    val pseudoClasses: List<SharedSemanticCssPseudoClass> = emptyList()
)

private data class SharedSemanticCssSelectorPart(
    val compound: SharedSemanticCssCompound,
    /** Combinator linking this part to the previous one; null for the leftmost. */
    val combinatorToPrevious: Char?
)

private data class SharedSemanticCssSelectorChain(
    val parts: List<SharedSemanticCssSelectorPart>
)

private fun SharedSemanticDomElement.sharedCssClassNames(): List<String> {
    return attribute("class")?.split(SharedSemanticCssClassSplitRegex)
        ?.filter { it.isNotEmpty() }.orEmpty()
}

private fun SharedSemanticDomElement.sharedCssElementSiblings(): List<SharedSemanticDomElement> {
    return parent?.children?.filterIsInstance<SharedSemanticDomElement>().orEmpty()
}

private fun splitSharedCssSelectorSegments(selector: String): List<String>? {
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    var combinatorPending: Char? = null
    var index = 0
    fun flushSegment() {
        val segment = current.toString().trim()
        if (segment.isNotEmpty()) {
            if (segments.isNotEmpty() || combinatorPending != null) {
                segments += (combinatorPending?.toString() ?: " ") + segment
            } else {
                segments += segment
            }
            current.clear()
            combinatorPending = null
        } else {
            current.clear()
            // An explicit combinator survives whitespace: "div > p" must keep
            // the child combinator across the spaces around ">".
        }
    }
    while (index < selector.length) {
        val char = selector[index]
        when {
            char == '[' -> {
                val end = selector.indexOf(']', index + 1)
                if (end < 0) return null
                current.append(selector, index, end + 1)
                index = end + 1
            }
            char == '(' -> {
                var depth = 1
                var cursor = index + 1
                var inQuote: Char? = null
                while (cursor < selector.length && depth > 0) {
                    val next = selector[cursor]
                    if (inQuote != null) {
                        if (next == inQuote) inQuote = null
                    } else when (next) {
                        '\'', '"' -> inQuote = next
                        '(' -> depth++
                        ')' -> depth--
                    }
                    cursor++
                }
                if (depth != 0) return null
                current.append(selector, index, cursor)
                index = cursor
            }
            char == '\'' || char == '"' -> {
                val end = selector.indexOf(char, index + 1)
                if (end < 0) return null
                current.append(selector, index, end + 1)
                index = end + 1
            }
            char == '>' || char == '+' || char == '~' -> {
                flushSegment()
                combinatorPending = char
                index++
            }
            char.isWhitespace() -> {
                flushSegment()
                if (combinatorPending == null && segments.isNotEmpty()) combinatorPending = ' '
                index++
            }
            else -> {
                current.append(char)
                index++
            }
        }
    }
    flushSegment()
    return segments.takeIf { it.isNotEmpty() }
}

private fun parseSharedCssCompound(raw: String): SharedSemanticCssCompound? {
    var rest = raw.trim()
    if (rest.isEmpty()) return null
    var tag: String? = null
    if (rest == "*") return SharedSemanticCssCompound(tag = null)
    val typeEnd = rest.indexOfFirst { it == '#' || it == '.' || it == '[' || it == ':' }
    val typePart = if (typeEnd < 0) rest.also { rest = "" } else rest.substring(0, typeEnd).also { rest = rest.substring(typeEnd) }
    if (typePart.isNotEmpty() && typePart != "*") {
        if (!typePart.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '|' }) return null
        tag = typePart.substringAfterLast('|').lowercase().takeIf { it.isNotEmpty() } ?: return null
    }
    var id: String? = null
    val classes = mutableListOf<String>()
    val attributes = mutableListOf<SharedSemanticCssAttributeCondition>()
    val pseudoClasses = mutableListOf<SharedSemanticCssPseudoClass>()
    while (rest.isNotEmpty()) {
        when (rest[0]) {
            '#' -> {
                val match = Regex("^#([A-Za-z0-9_-]+)").find(rest) ?: return null
                if (id != null) return null
                id = match.groupValues[1]
                rest = rest.substring(match.value.length)
            }
            '.' -> {
                val match = Regex("^\\.([A-Za-z0-9_-]+)").find(rest) ?: return null
                classes += match.groupValues[1]
                rest = rest.substring(match.value.length)
            }
            '[' -> {
                val end = rest.indexOf(']') 
                if (end < 0) return null
                val condition = parseSharedCssAttributeCondition(rest.substring(1, end)) ?: return null
                attributes += condition
                rest = rest.substring(end + 1)
            }
            ':' -> {
                // `(?s)` rather than RegexOption.DOT_MATCHES_ALL: the option is absent from the
                // common stdlib, so naming it breaks compileIosMainKotlinMetadata.
                val match = Regex("(?s)^:([a-zA-Z-]+)(\\((.*)\\))?$").find(rest)
                    ?: return null
                val name = match.groupValues[1].lowercase()
                val args = match.groupValues[3]
                when (name) {
                    "first-child" -> pseudoClasses += SharedSemanticCssPseudoClass.FirstChild
                    "last-child" -> pseudoClasses += SharedSemanticCssPseudoClass.LastChild
                    "nth-child" -> pseudoClasses += parseSharedCssNth(args, fromEnd = false) ?: return null
                    "nth-last-child" -> pseudoClasses += parseSharedCssNth(args, fromEnd = true) ?: return null
                    "not" -> {
                        val inner = parseSharedCssCompound(args.trim()) ?: return null
                        pseudoClasses += SharedSemanticCssPseudoClass.Not(inner)
                    }
                    "root" -> if (tag == null) tag = "html" else return null
                    else -> return null
                }
                rest = ""
            }
            else -> return null
        }
    }
    return SharedSemanticCssCompound(tag = tag, id = id, classes = classes, attributes = attributes, pseudoClasses = pseudoClasses)
}

private fun parseSharedCssAttributeCondition(raw: String): SharedSemanticCssAttributeCondition? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val operators = listOf("~=", "|=", "^=", "$=", "*=", "=")
    for (op in operators) {
        val at = trimmed.indexOf(op)
        if (at > 0) {
            val name = trimmed.substring(0, at).trim().lowercase().takeIf { it.isNotEmpty() } ?: return null
            var value = trimmed.substring(at + op.length).trim()
            if ((value.startsWith('"') && value.endsWith('"') && value.length >= 2) ||
                (value.startsWith('\'') && value.endsWith('\'') && value.length >= 2)
            ) {
                value = value.substring(1, value.length - 1)
            }
            return SharedSemanticCssAttributeCondition(name, if (op == "=") "=" else op, value)
        }
    }
    if (trimmed.any { it.isWhitespace() || it == '\'' || it == '"' }) return null
    return SharedSemanticCssAttributeCondition(trimmed.lowercase(), null, null)
}

private fun parseSharedCssNth(args: String, fromEnd: Boolean): SharedSemanticCssPseudoClass.NthChild? {
    val normalized = args.trim().lowercase().replace("\\s+".toRegex(), "")
    if (normalized.isEmpty()) return null
    if (normalized == "odd") return SharedSemanticCssPseudoClass.NthChild(2, 1, fromEnd)
    if (normalized == "even") return SharedSemanticCssPseudoClass.NthChild(2, 0, fromEnd)
    normalized.toIntOrNull()?.let { return SharedSemanticCssPseudoClass.NthChild(0, it, fromEnd) }
    val nAt = normalized.indexOf('n')
    if (nAt < 0) return null
    val stepText = normalized.substring(0, nAt)
    val step = when (stepText) {
        "", "+" -> 1
        "-" -> -1
        else -> stepText.toIntOrNull() ?: return null
    }
    val offsetText = normalized.substring(nAt + 1)
    val offset = when {
        offsetText.isEmpty() -> 0
        else -> offsetText.toIntOrNull() ?: return null
    }
    return SharedSemanticCssPseudoClass.NthChild(step, offset, fromEnd)
}

private fun parseSharedCssSelectorChain(selector: String): SharedSemanticCssSelectorChain? {
    val segments = splitSharedCssSelectorSegments(selector) ?: return null
    val parts = segments.map { segment ->
        var text = segment.trim()
        var combinator: Char? = null
        if (text.startsWith('>') || text.startsWith('+') || text.startsWith('~')) {
            combinator = text[0]
            text = text.substring(1).trim()
        } else if (text.startsWith(' ')) {
            combinator = ' '
            text = text.trim()
        }
        val compound = parseSharedCssCompound(text) ?: return null
        SharedSemanticCssSelectorPart(compound, combinator)
    }
    if (parts.isEmpty()) return null
    // First part must not carry a combinator; subsequent parts default to descendant.
    val normalized = parts.mapIndexed { index, part ->
        when {
            index == 0 && part.combinatorToPrevious != null -> return null
            index > 0 && part.combinatorToPrevious == null ->
                part.copy(combinatorToPrevious = ' ')
            else -> part
        }
    }
    return SharedSemanticCssSelectorChain(normalized)
}

private fun SharedSemanticDomElement.matchesSharedCssAttribute(
    condition: SharedSemanticCssAttributeCondition
): Boolean {
    val actual = attribute(condition.name) ?: return false
    val op = condition.op ?: return true
    val expected = condition.value.orEmpty()
    return when (op) {
        "=" -> actual == expected
        "~=" -> actual.split(SharedSemanticCssClassSplitRegex).any { it == expected }
        "|=" -> actual == expected || actual.startsWith("$expected-")
        "^=" -> actual.startsWith(expected)
        "$=" -> actual.endsWith(expected)
        "*=" -> actual.contains(expected)
        else -> false
    }
}

private fun SharedSemanticDomElement.matchesSharedCssPseudo(
    pseudo: SharedSemanticCssPseudoClass
): Boolean {
    val siblings = sharedCssElementSiblings()
    val index = siblings.indexOfFirst { it === this }
    if (index < 0) return false
    return when (pseudo) {
        SharedSemanticCssPseudoClass.FirstChild -> index == 0
        SharedSemanticCssPseudoClass.LastChild -> index == siblings.lastIndex
        is SharedSemanticCssPseudoClass.NthChild -> {
            val position = if (pseudo.fromEnd) siblings.size - index else index + 1
            if (pseudo.step == 0) {
                position == pseudo.offset
            } else if (pseudo.step > 0) {
                position >= pseudo.offset && (position - pseudo.offset) % pseudo.step == 0
            } else {
                position <= pseudo.offset && (pseudo.offset - position) % (-pseudo.step) == 0
            }
        }
        is SharedSemanticCssPseudoClass.Not -> !matchesSharedCssCompound(pseudo.inner)
    }
}

private fun SharedSemanticDomElement.matchesSharedCssCompound(compound: SharedSemanticCssCompound): Boolean {
    compound.tag?.let { if (localName != it) return false }
    compound.id?.let { if (elementId() != it) return false }
    if (!compound.classes.all { it in sharedCssClassNames() }) return false
    if (!compound.attributes.all { matchesSharedCssAttribute(it) }) return false
    if (!compound.pseudoClasses.all { matchesSharedCssPseudo(it) }) return false
    return true
}

private fun previousSharedCssElementSibling(element: SharedSemanticDomElement): SharedSemanticDomElement? {
    val siblings = element.sharedCssElementSiblings()
    val index = siblings.indexOfFirst { it === element }
    return if (index > 0) siblings[index - 1] else null
}

private fun matchesSharedSemanticCssChain(
    element: SharedSemanticDomElement,
    chain: SharedSemanticCssSelectorChain
): Boolean {
    val parts = chain.parts
    var current: SharedSemanticDomElement? = element
    if (current == null || !current.matchesSharedCssCompound(parts.last().compound)) return false
    var index = parts.lastIndex
    while (index > 0) {
        val combinator = parts[index].combinatorToPrevious ?: return false
        val previous = parts[index - 1].compound
        current = when (combinator) {
            '>' -> current!!.parent?.takeIf { it.matchesSharedCssCompound(previous) }
            '+' -> previousSharedCssElementSibling(current!!)?.takeIf { it.matchesSharedCssCompound(previous) }
            '~' -> {
                val siblings = current!!.sharedCssElementSiblings()
                val at = siblings.indexOfFirst { it === current }
                if (at <= 0) null
                else siblings.subList(0, at).lastOrNull { it.matchesSharedCssCompound(previous) }
            }
            else -> generateSequence(current!!.parent) { it.parent }
                .firstOrNull { it.matchesSharedCssCompound(previous) }
        } ?: return false
        index--
    }
    return true
}

/**
 * Matches book CSS rules against the shared DOM, mirroring the JVM parser's
 * bucket lookup + Jsoup-selector matching (`rulesForElement`). Complex
 * selectors beyond type/class/id/descendant/child/sibling/attribute and the
 * structural pseudo-classes above never match here; the JVM engine may apply
 * a few exotic ones (`:empty`, `:contains`), which is a documented gap.
 */
private class SharedSemanticCssMatcher(
    rules: OptimizedCssRules,
    private val baseFontSizeSp: Float = SharedSemanticCssBaseFontSizeSp,
    private val density: Float = SharedSemanticCssDensity,
    private val constraints: Constraints = SharedSemanticCssConstraints
) {
    private val tagRules: Map<String, List<CssRule>> = rules.byTag.entries
        .groupBy({ it.key.lowercase() }, { it.value })
        .mapValues { (_, grouped) -> grouped.flatten().sortedWith(SharedSemanticCssCascadeComparator) }
    private val classRules: Map<String, List<CssRule>> =
        rules.byClass.mapValues { (_, bucket) -> bucket.sortedWith(SharedSemanticCssCascadeComparator) }
    private val idRules: Map<String, List<CssRule>> =
        rules.byId.mapValues { (_, bucket) -> bucket.sortedWith(SharedSemanticCssCascadeComparator) }
    private val complexRules: List<CssRule> =
        rules.otherComplex.sortedWith(SharedSemanticCssCascadeComparator)
    private val chainCache = HashMap<String, SharedSemanticCssSelectorChain?>()
    private val elementRulesCache = HashMap<SharedSemanticDomElement, List<CssRule>>()

    private fun compiledChain(selector: String): SharedSemanticCssSelectorChain? {
        return chainCache.getOrPut(selector) {
            runCatching { parseSharedCssSelectorChain(selector) }.getOrNull()
        }
    }

    fun rulesForElement(element: SharedSemanticDomElement): List<CssRule> {
        return elementRulesCache.getOrPut(element) {
            val candidates = ArrayList<List<CssRule>>(4)
            tagRules[element.localName]?.let { candidates += it }
            element.sharedCssClassNames().forEach { classRules[it]?.let { bucket -> candidates += bucket } }
            element.elementId()?.let { idRules[it]?.let { bucket -> candidates += bucket } }
            if (complexRules.isNotEmpty()) candidates += complexRules
            mergeSharedSemanticCssRuleBuckets(candidates).filter { rule ->
                rule.pseudoElement == null &&
                    (compiledChain(rule.selector.selector)?.let { chain ->
                        runCatching { matchesSharedSemanticCssChain(element, chain) }.getOrDefault(false)
                    } == true)
            }
        }
    }

    /** Author + UA rules plus the inline `style` attribute and legacy `align`. */
    fun elementOwnStyle(
        element: SharedSemanticDomElement,
        inheritedCustomProperties: Map<String, String>
    ): CssStyle {
        var style = rulesForElement(element).fold(
            CssStyle(customProperties = inheritedCustomProperties)
        ) { acc, rule -> acc.merge(rule.style) }
        element.attribute("style")?.takeIf { it.isNotBlank() }?.let { inline ->
            style = style.merge(
                CssParser.parseProperties(
                    properties = inline,
                    baseFontSizeSp = baseFontSizeSp,
                    density = density,
                    constraints = constraints,
                    onlyImportant = false,
                    isDarkTheme = false,
                    adaptThemeColors = false,
                    inheritedCustomProperties = style.customProperties
                )
            )
            style = style.merge(
                CssParser.parseProperties(
                    properties = inline,
                    baseFontSizeSp = baseFontSizeSp,
                    density = density,
                    constraints = constraints,
                    onlyImportant = true,
                    isDarkTheme = false,
                    adaptThemeColors = false,
                    inheritedCustomProperties = style.customProperties
                )
            )
        }
        element.attribute("align")?.trim()?.takeIf { it.isNotEmpty() }?.let { align ->
            val textAlign = when (align.lowercase()) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                "justify" -> TextAlign.Justify
                "left" -> TextAlign.Start
                else -> null
            }
            if (textAlign != null) {
                style = style.merge(CssStyle(paragraphStyle = ParagraphStyle(textAlign = textAlign)))
            }
        }
        return style
    }

    /**
     * Merges an element's own style over its inherited style, mirroring the
     * JVM parser's inherited propagation (span/paragraph merge, block list
     * and visibility inheritance, em font-size resolution). Embedded
     * `@font-face` families stay as names here (load-time has no font files);
     * only generic families resolve, exactly like the JVM loader's empty
     * font map.
     */
    fun finalizeElementStyle(own: CssStyle, inherited: CssStyle): CssStyle {
        val blockStyle = own.blockStyle.copy(
            listStyleType = own.blockStyle.listStyleType ?: inherited.blockStyle.listStyleType,
            listStyleImage = own.blockStyle.listStyleImage ?: inherited.blockStyle.listStyleImage,
            visibility = own.blockStyle.visibility ?: inherited.blockStyle.visibility
        )
        return own.copy(
            spanStyle = inherited.spanStyle.merge(own.spanStyle),
            paragraphStyle = inherited.paragraphStyle.merge(own.paragraphStyle),
            blockStyle = blockStyle,
            fontFamilies = own.fontFamilies.ifEmpty { inherited.fontFamilies },
            fontSize = own.fontSize,
            textTransform = own.textTransform ?: inherited.textTransform,
            hyphens = own.hyphens ?: inherited.hyphens,
            fontVariantNumeric = own.fontVariantNumeric ?: inherited.fontVariantNumeric,
            textEmphasis = own.textEmphasis ?: inherited.textEmphasis,
            whiteSpace = own.whiteSpace ?: inherited.whiteSpace,
            writingMode = own.writingMode ?: inherited.writingMode,
            wordBreak = own.wordBreak ?: inherited.wordBreak,
            wordSpacing = own.wordSpacing,
            customProperties = inherited.customProperties + own.customProperties
        ).resolveSharedSemanticFontSizeAgainst(inherited).withSharedSemanticFontFamily()
    }

    private fun CssStyle.resolveSharedSemanticFontSizeAgainst(parent: CssStyle): CssStyle {
        if (!fontSize.isSpecified) return copy(fontSize = parent.fontSize)
        if (fontSize.type != TextUnitType.Em) return this
        val parentSize = parent.fontSize
            .takeIf { it.isSpecified && it.type != TextUnitType.Em }
            ?: baseFontSizeSp.sp
        return copy(fontSize = (parentSize.value * fontSize.value).sp)
    }

    private fun CssStyle.withSharedSemanticFontFamily(): CssStyle {
        if (spanStyle.fontFamily != null) return this
        val resolved = fontFamilies.firstNotNullOfOrNull(FontFamilyMapper::nameToFontFamily)
            ?: return this
        return copy(spanStyle = spanStyle.copy(fontFamily = resolved))
    }
}

private val SharedEpubBlockCloseTags = setOf(
    "p", "div", "section", "article", "aside", "main", "header", "footer",
    "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "table", "blockquote", "ul", "ol"
)

private val SharedEpubVoidElementNames = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
    "param", "source", "track", "wbr"
)

private val SharedEpubInlineVoidTags = setOf("br", "img")

private val SharedEpubNonRenderableTags = setOf(
    "script", "style", "head", "title", "meta", "link", "iframe", "object", "embed",
    "form", "noscript", "template", "svg"
)

private val SharedEpubBlockTags = setOf(
    "p", "div", "section", "article", "aside", "main", "header", "footer", "nav",
    "figure", "figcaption", "blockquote", "pre", "address", "center", "details",
    "summary", "caption", "dt", "dd", "h1", "h2", "h3", "h4", "h5", "h6", "ul",
    "ol", "li", "table", "tr", "td", "th", "br", "hr", "img", "math"
)

private val SharedEpubContainerTags = setOf(
    "div", "section", "article", "aside", "main", "header", "footer", "nav",
    "figure", "blockquote", "details", "body", "html"
)

private val SharedEpubHeaderLevels = mapOf(
    "h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6
)

private val SharedEpubBoldTags = setOf("b", "strong")
private val SharedEpubItalicTags = setOf("i", "em", "cite", "var")
private val SharedEpubUnderlineTags = setOf("u", "ins")
private val SharedEpubStrikeTags = setOf("s", "strike", "del")
private val SharedEpubCodeTags = setOf("code", "kbd", "samp", "tt")

internal sealed interface SharedSemanticDomNode {
    val parent: SharedSemanticDomElement?
    var normalizedStart: Int
}

internal class SharedSemanticDomElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<SharedSemanticDomNode> = mutableListOf(),
    override val parent: SharedSemanticDomElement?
) : SharedSemanticDomNode {
    val localName: String get() = name.substringAfter(':').lowercase()

    fun attribute(localName: String): String? = attributes[localName]

    fun elementId(): String? = attribute("id")?.trim()?.takeIf(String::isNotBlank)

    /** Ebookmaker figure frame: `<div class="figcenter">`/`<figure>` whose image was stripped. */
    fun isMissingFigureContainer(): Boolean {
        val isFrame = localName == "figure" ||
            attribute("class")?.split(Regex("\\s+"))?.any { it.equals("figcenter", ignoreCase = true) } == true
        if (!isFrame) return false
        return findEbookmakerImageMarker() != null && !containsElementNamed("img")
    }

    fun findEbookmakerImageMarker(): SharedSemanticDomElement? {
        val stack = ArrayDeque<SharedSemanticDomNode>()
        children.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.localName == "span" && node.elementId()?.isEbookmakerImageMarkerId() == true) {
                    return node
                }
                node.children.forEach { stack.addLast(it) }
            }
        }
        return null
    }

    fun findCaptionElement(): SharedSemanticDomElement? {
        val stack = ArrayDeque<SharedSemanticDomNode>()
        children.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.localName == "span" &&
                    node.attribute("class")?.split(Regex("\\s+"))?.any { it.equals("caption", ignoreCase = true) } == true
                ) {
                    return node
                }
                node.children.forEach { stack.addLast(it) }
            }
        }
        return null
    }

    private fun containsElementNamed(name: String): Boolean {
        val stack = ArrayDeque<SharedSemanticDomNode>()
        children.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.localName == name) return true
                node.children.forEach { stack.addLast(it) }
            }
        }
        return false
    }

    fun findFirstElementNamed(name: String): SharedSemanticDomElement? {
        val stack = ArrayDeque<SharedSemanticDomNode>()
        children.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.localName == name) return node
                node.children.forEach { stack.addLast(it) }
            }
        }
        return null
    }

    /**
     * First descendant (or self) whose `id` matches, in document order.
     *
     * The transcript anchor lookup for media overlays: a SMIL `par` names the
     * element it narrates, and this is that element. First match wins because
     * ids are unique per spec; a duplicate is a broken file, and either copy
     * carries the same line.
     */
    fun findElementById(id: String): SharedSemanticDomElement? {
        if (id.isBlank()) return null
        if (elementId() == id) return this
        val stack = ArrayDeque<SharedSemanticDomNode>()
        // Reversed so removal from the end visits document order.
        children.asReversed().forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.elementId() == id) return node
                node.children.asReversed().forEach { stack.addLast(it) }
            }
        }
        return null
    }

    /** This element's own text with descendants, for transcript anchoring. */
    fun elementTextContent(): String = buildString {
        fun append(node: SharedSemanticDomNode) {
            when (node) {
                is SharedSemanticDomText -> {
                    if (appendedSinceSpace(node.text)) append(' ')
                    append(node.text)
                }
                is SharedSemanticDomElement -> node.children.forEach(::append)
            }
        }
        children.forEach(::append)
    }

    private fun StringBuilder.appendedSinceSpace(value: String): Boolean =
        isNotEmpty() && !last().isWhitespace() && value.isNotEmpty() && !value.first().isWhitespace()

    val isBlockElement: Boolean get() = localName in SharedEpubBlockTags

    fun hasBlockDescendant(): Boolean {
        val stack = ArrayDeque<SharedSemanticDomNode>()
        children.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.isBlockElement && node.localName !in SharedEpubInlineVoidTags) return true
                node.children.forEach { stack.addLast(it) }
            }
        }
        return false
    }

    /**
     * Android-compatible CFI path. Walks ancestors to the document root, indexing
     * this element among its parent's meaningful children (elements plus non-blank
     * text runs), using `(nodeIndex * 2) + 2` per level with the `/4` prefix.
     */
    fun cfiPath(): String? {
        val path = mutableListOf<Int>()
        var currentNode: SharedSemanticDomNode? = this
        while (currentNode is SharedSemanticDomElement && currentNode.parent != null) {
            val parent = currentNode.parent
            val meaningful = parent.children.filter { node ->
                node is SharedSemanticDomElement || (node is SharedSemanticDomText && node.text.isNotBlank())
            }
            val nodeIndex = meaningful.indexOfFirst { it === currentNode }
            if (nodeIndex == -1) {
                currentNode = parent
                continue
            }
            path.add(0, (nodeIndex * 2) + 2)
            currentNode = parent
        }
        if (path.isEmpty()) return null
        path.add(0, 4)
        return "/" + path.joinToString("/")
    }

    var contentStart: Int = 0
    var contentEnd: Int = 0
    override var normalizedStart: Int = -1
}

internal class SharedSemanticDomText(
    val text: String,
    override val parent: SharedSemanticDomElement?
) : SharedSemanticDomNode {
    var sourceStart: Int = 0
    var sourceEnd: Int = 0
    override var normalizedStart: Int = -1
}

internal class SharedSemanticHtmlTree(
    val root: SharedSemanticDomElement,
    val normalizedMapping: IntArray
)

internal object SharedSemanticHtmlDocument {

    fun parse(html: String): SharedSemanticHtmlTree? {
        val tokens = sharedEpubXmlTokens(html).toList()
        val root = SharedSemanticDomElement("#document", emptyMap(), parent = null)
        val stack = ArrayDeque<SharedSemanticDomElement>().apply { addLast(root) }
        var cursor = 0
        for (token in tokens) {
            if (token.start > cursor) {
                val raw = html.substring(cursor, token.start)
                if (raw.isNotBlank()) {
                    stack.last().children += SharedSemanticDomText(raw.decodeEpubEntities(), stack.last())
                }
            }
            cursor = token.endExclusive
            val value = token.value
            when {
                value.startsWith("<!--") || value.startsWith("<?") || value.startsWith("<!DOCTYPE", ignoreCase = true) -> Unit
                value.startsWith("<![CDATA[") -> {
                    val content = value.removePrefix("<![CDATA[").removeSuffix("]]>")
                    if (content.isNotBlank()) {
                        stack.last().children += SharedSemanticDomText(content, stack.last())
                    }
                }
                value.startsWith("</") -> {
                    val closingName = value.removePrefix("</").substringBefore('>').trim()
                        .takeWhile { !it.isWhitespace() && it != '/' && it != '>' }
                    if (closingName.isBlank()) continue
                    val matched = stack.indexOfLast {
                        it.localName == closingName.substringAfter(':').lowercase()
                    }
                    if (matched > 0) {
                        while (stack.size > matched) stack.removeLast()
                    }
                }
                value.startsWith("<") -> {
                    val selfClosing = value.trimEnd().endsWith("/>")
                    val inside = value.removePrefix("<").removeSuffix(">").removeSuffix("/").trim()
                    val rawName = inside.takeWhile { !it.isWhitespace() }
                    if (rawName.isBlank()) continue
                    val localName = rawName.substringAfter(':').lowercase()
                    val attributeText = inside.substring(rawName.length)
                    val element = SharedSemanticDomElement(
                        rawName,
                        parseAttributes(attributeText),
                        parent = stack.last()
                    )
                    stack.last().children += element
                    if (!selfClosing && localName !in SharedEpubVoidElementNames) {
                        stack.addLast(element)
                    }
                }
            }
        }
        if (cursor < html.length && html.substring(cursor).isNotBlank()) {
            stack.last().children += SharedSemanticDomText(html.substring(cursor).decodeEpubEntities(), stack.last())
        }
        val mapping = buildTextSpace(root)
        return SharedSemanticHtmlTree(root, mapping)
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val attributes = linkedMapOf<String, String>()
        var offset = 0
        while (offset < raw.length) {
            while (offset < raw.length && raw[offset].isWhitespace()) offset++
            val match = EpubXmlAttributeRegex.find(raw, offset)
            if (match == null || match.range.first != offset) break
            val name = match.groupValues[1].substringAfter(':').lowercase()
            attributes[name] = match.groupValues[3].decodeEpubEntities()
            offset = match.range.last + 1
        }
        return attributes
    }

    private fun buildTextSpace(root: SharedSemanticDomElement): IntArray {
        val text = StringBuilder()
        fun appendElement(element: SharedSemanticDomElement) {
            val isBlockClose = element.localName in SharedEpubBlockCloseTags
            element.contentStart = text.length + 1
            text.append(' ')
            element.children.forEach { node ->
                when (node) {
                    is SharedSemanticDomText -> {
                        node.sourceStart = text.length
                        text.append(node.text)
                        node.sourceEnd = text.length
                    }
                    is SharedSemanticDomElement -> appendElement(node)
                }
            }
            text.append(if (element.localName == "br") '\n' else if (isBlockClose) '\n' else ' ')
            element.contentEnd = text.length
        }
        root.children.forEach { node ->
            when (node) {
                is SharedSemanticDomText -> {
                    node.sourceStart = text.length
                    text.append(node.text)
                    node.sourceEnd = text.length
                }
                is SharedSemanticDomElement -> appendElement(node)
            }
        }
        val (_, mapping) = normalizeWithSourceMapping(text.toString())
        assignNormalizedRanges(root, mapping)
        return mapping
    }

    private fun assignNormalizedRanges(root: SharedSemanticDomElement, mapping: IntArray) {
        fun firstNormalizedAtOrAfter(sourceIndex: Int): Int {
            var low = 0
            var high = mapping.size
            while (low < high) {
                val mid = (low + high) / 2
                if (mapping[mid] >= sourceIndex) high = mid else low = mid + 1
            }
            return low
        }

        fun walk(node: SharedSemanticDomNode) {
            when (node) {
                is SharedSemanticDomText -> {
                    val start = firstNormalizedAtOrAfter(node.sourceStart)
                    node.normalizedStart = if (start < mapping.size && mapping[start] < node.sourceEnd) start else -1
                }
                is SharedSemanticDomElement -> {
                    val start = firstNormalizedAtOrAfter(node.contentStart)
                    node.normalizedStart = if (start < mapping.size && mapping[start] < node.contentEnd) start else -1
                    node.children.forEach(::walk)
                }
            }
        }
        walk(root)
    }
}

private val SharedEpubWhitespaceChars = charArrayOf(' ', '\t', '\u000B', '\u000C', '\r')

/**
 * Replicates [normalizeEpubWhitespace] (pass by pass) while tracking, for every
 * output character, the index of the source character it derives from.
 */
internal fun normalizeWithSourceMapping(input: String): Pair<String, IntArray> {
    // Pass 1: '\u0000' -> ' '
    var text = input
    var map = IntArray(input.length) { it }
    if (text.contains('\u0000')) {
        val output = text.replace('\u0000', ' ')
        text = output
    }

    // Pass 2: collapse runs of [ \t\x0B\f\r] into a single space
    if (text.any { it in SharedEpubWhitespaceChars }) {
        val output = StringBuilder(text.length)
        val outputMap = ArrayList<Int>(text.length)
        text.forEachIndexed { index, char ->
            if (char in SharedEpubWhitespaceChars) {
                if (index == 0 || text[index - 1] !in SharedEpubWhitespaceChars) {
                    output.append(' ')
                    outputMap += map[index]
                }
            } else {
                output.append(char)
                outputMap += map[index]
            }
        }
        text = output.toString()
        map = outputMap.toIntArray()
    }

    // Pass 3: " *\n *" -> "\n"
    if (text.contains('\n')) {
        val output = StringBuilder(text.length)
        val outputMap = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\n' -> {
                    var cursor = index + 1
                    while (cursor < text.length && text[cursor] == ' ') cursor++
                    output.append('\n')
                    outputMap += map[index]
                    index = cursor - 1
                }
                ' ' -> if (index + 1 >= text.length || text[index + 1] != '\n') {
                    output.append(' ')
                    outputMap += map[index]
                }
                else -> {
                    output.append(text[index])
                    outputMap += map[index]
                }
            }
            index++
        }
        text = output.toString()
        map = outputMap.toIntArray()
    }

    // Pass 4: "\n{3,}" -> "\n\n"
    if (text.contains("\n\n\n")) {
        val output = StringBuilder(text.length)
        val outputMap = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                var cursor = index + 1
                while (cursor < text.length && text[cursor] == '\n') cursor++
                output.append('\n')
                outputMap += map[index]
                if (cursor - index > 2) {
                    output.append('\n')
                    outputMap += map[index + 1]
                }
                index = cursor - 1
            } else {
                output.append(text[index])
                outputMap += map[index]
            }
            index++
        }
        text = output.toString()
        map = outputMap.toIntArray()
    }

    // Pass 5: trim
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return "" to IntArray(0)
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return text.substring(start, end) to map.copyOfRange(start, end)
}

private class SharedEpubSemanticBlockBuilder(
    private val document: SharedSemanticHtmlTree,
    css: SharedSemanticCssSnapshot = SharedSemanticCssSnapshot.EMPTY,
    baseFontSizeSp: Float = SharedSemanticCssBaseFontSizeSp,
    density: Float = SharedSemanticCssDensity,
    constraints: Constraints = SharedSemanticCssConstraints
) {
    private val blocks = mutableListOf<SemanticBlock>()
    private var nextBlockIndex = 0
    private val cssMatcher = SharedSemanticCssMatcher(
        rules = css.rules,
        baseFontSizeSp = baseFontSizeSp,
        density = density,
        constraints = constraints
    )

    fun build(): List<SemanticBlock> {
        buildContainer(document.root, blocks)
        return blocks
    }

    /**
     * Author/UA/inline style for [element], merged over the inherited ancestor
     * style. Text, CFIs and offsets are unaffected; only styling changes.
     */
    private fun elementFinalStyle(
        element: SharedSemanticDomElement,
        inherited: CssStyle
    ): CssStyle {
        val own = cssMatcher.elementOwnStyle(element, inherited.customProperties)
        return cssMatcher.finalizeElementStyle(own, inherited)
    }

    private fun buildContainer(
        element: SharedSemanticDomElement,
        target: MutableList<SemanticBlock>,
        inherited: CssStyle = CssStyle()
    ) {
        val finalStyle = elementFinalStyle(element, inherited)
        if (finalStyle.display == "none") return
        if (element.isMissingFigureContainer()) {
            val temp = mutableListOf<SemanticBlock>()
            buildContainerChildren(element, temp, finalStyle)
            foldMissingFigureChildren(element, temp)?.let { placeholder ->
                target += placeholder
                return
            }
            target += temp
            return
        }
        buildContainerChildren(element, target, finalStyle)
    }

    /**
     * Folds an image-less figure (ebookmaker `<span id="img_...">` marker, no `<img>`) into a
     * single missing-figure placeholder, mirroring the JVM parser. The placeholder borrows the
     * first text block's source offset so page clipping and locators keep working.
     */
    private fun foldMissingFigureChildren(
        element: SharedSemanticDomElement,
        children: List<SemanticBlock>
    ): SemanticBlock? {
        if (children.any { it is SemanticTable || it is SemanticImage || it is SemanticList || it is SemanticMath || it is SemanticFlexContainer }) {
            return null
        }
        val marker = element.findEbookmakerImageMarker() ?: return null
        val captionElement = element.findCaptionElement()
        val caption = captionElement?.let { buildLocalText(listOf(it)).text }
            ?: buildLocalText(listOf(marker)).text
        val anchor = children.filterIsInstance<SemanticTextBlock>().firstOrNull()
        return SemanticParagraph(
            text = readerMissingFigureText(caption),
            spans = emptyList(),
            style = CssStyle().withReaderMissingFigure(),
            elementId = element.elementId() ?: marker.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = anchor?.startCharOffsetInSource ?: 0,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildContainerChildren(
        element: SharedSemanticDomElement,
        target: MutableList<SemanticBlock>,
        inherited: CssStyle
    ) {
        val buffer = mutableListOf<SharedSemanticDomNode>()
        var chunkOrdinal = 0
        val preserveInlineWhitespace = SharedSemanticCssWhiteSpacePreserves(inherited.whiteSpace)

        fun flush() {
            if (buffer.isEmpty()) return
            val nodes = buffer.toList()
            buffer.clear()
            val local = buildLocalText(nodes, inherited, preserveInlineWhitespace)
            if (local.text.isBlank()) return
            target += SemanticParagraph(
                text = local.text,
                spans = local.spans,
                style = inherited,
                elementId = element.elementId().takeIf { chunkOrdinal == 0 },
                cfi = element.cfiPath() ?: if (element === document.root) "/4" else null,
                startCharOffsetInSource = local.startCharOffsetInSource,
                blockIndex = nextBlockIndex++
            )
            chunkOrdinal++
        }

        element.children.forEach { child ->
            when (child) {
                is SharedSemanticDomText -> buffer += child
                is SharedSemanticDomElement -> when {
                    // Standalone vector art must become an image block: "svg"
                    // stays in NonRenderableTags so inline svg never leaks its
                    // <text> content into surrounding paragraphs (buildLocalText
                    // still skips it), but block-level svg is intercepted here
                    // first. Same for <picture>, whose <img> would otherwise
                    // dissolve into the text buffer and vanish.
                    child.localName == "svg" || child.localName == "picture" -> {
                        flush()
                        buildSpecific(child, target, inherited)
                    }
                    child.localName in SharedEpubNonRenderableTags -> Unit
                    child.localName == "br" || child.localName == "hr" || child.localName == "img" ||
                        child.localName == "math" || child.localName == "table" ||
                        child.localName == "ul" || child.localName == "ol" -> {
                        flush()
                        buildSpecific(child, target, inherited)
                    }
                    child.localName in SharedEpubHeaderLevels -> {
                        flush()
                        buildHeader(child, target, inherited)
                    }
                    child.localName == "pre" -> {
                        flush()
                        buildPreTextBlock(child, target, inherited)
                    }
                    child.localName in SharedEpubContainerTags || child.hasBlockDescendant() -> {
                        flush()
                        buildContainer(child, target, inherited)
                    }
                    child.localName in SharedEpubBlockTags -> {
                        flush()
                        buildTextBlock(child, target, inherited)
                    }
                    else -> buffer += child
                }
            }
        }
        flush()
    }

    private fun buildSpecific(
        element: SharedSemanticDomElement,
        target: MutableList<SemanticBlock>,
        inherited: CssStyle
    ) {
        val finalStyle = elementFinalStyle(element, inherited)
        if (finalStyle.display == "none") return
        when (element.localName) {
            "br" -> target += SemanticSpacer(
                style = finalStyle,
                elementId = element.elementId(),
                cfi = element.cfiPath(),
                isExplicitLineBreak = true,
                blockIndex = nextBlockIndex++
            )
            "hr" -> target += SemanticSpacer(
                style = finalStyle,
                elementId = element.elementId(),
                cfi = element.cfiPath(),
                blockIndex = nextBlockIndex++
            )
            "img" -> buildImage(element, finalStyle)?.let { target += it }
            "svg" -> buildSvgImage(element, finalStyle)?.let { target += it }
            "picture" -> buildPictureImage(element, finalStyle)?.let { target += it }
            "math" -> {
                val text = buildLocalText(listOf(element), finalStyle).text.trim()
                target += SemanticMath(
                    svgContent = null,
                    altText = text.ifBlank { "Equation" },
                    svgWidth = null,
                    svgHeight = null,
                    svgViewBox = null,
                    isFromMathJax = false,
                    style = finalStyle,
                    elementId = element.elementId(),
                    cfi = element.cfiPath(),
                    blockIndex = nextBlockIndex++
                )
            }
            "table" -> buildTable(element, finalStyle)?.let { target += it }
            "ul", "ol" -> buildList(element, finalStyle)?.let { target += it }
        }
    }

    private fun buildImage(element: SharedSemanticDomElement, style: CssStyle): SemanticImage? {
        // Lazy-loaded markup often carries no src (data-src/srcset only);
        // Android resolves data-src the same way. Without a fallback the
        // image silently drops out of pagination while the WebView still
        // shows it.
        val src = element.attribute("src")?.trim()?.takeIf(String::isNotBlank)
            ?: element.attribute("data-src")?.trim()?.takeIf(String::isNotBlank)
            ?: element.attribute("srcset").firstSrcSetUrl()
            ?: return null
        return SemanticImage(
            path = src,
            altText = element.attribute("alt"),
            intrinsicWidth = element.attribute("width").sharedSemanticCssPixelSize(),
            intrinsicHeight = element.attribute("height").sharedSemanticCssPixelSize(),
            style = style,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    /**
     * Inline vector art (`<svg>` directly in chapter markup) never reaches the
     * resource-rewritten `<img>` path, so pagination dropped it entirely while
     * the WebView rendered it. Serializes the element into a data-URI image
     * with viewBox-derived intrinsics so measure == render can contain-fit it.
     */
    private fun buildSvgImage(element: SharedSemanticDomElement, style: CssStyle): SemanticImage? {
        val markup = element.toStandaloneSvgMarkup().takeIf { it.isNotBlank() } ?: return null
        // Attribute keys are lowercased by the parser (viewBox -> viewbox).
        val viewBoxSize = element.attribute("viewbox").sharedSemanticViewBoxSize()
        return SemanticImage(
            path = "data:image/svg+xml;base64,${markup.encodeToByteArray().toSharedSemanticBase64()}",
            altText = element.attribute("aria-label")?.trim()?.takeIf(String::isNotBlank),
            intrinsicWidth = element.attribute("width").sharedSemanticCssPixelSize()
                ?: viewBoxSize?.first,
            intrinsicHeight = element.attribute("height").sharedSemanticCssPixelSize()
                ?: viewBoxSize?.second,
            style = style,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildPictureImage(element: SharedSemanticDomElement, style: CssStyle): SemanticImage? {
        element.findFirstElementNamed("img")?.let { return buildImage(it, style) }
        val sourceUrl = element.findFirstElementNamed("source")
            ?.attribute("srcset").firstSrcSetUrl()
            ?: return null
        return SemanticImage(
            path = sourceUrl,
            altText = null,
            intrinsicWidth = null,
            intrinsicHeight = null,
            style = style,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    /**
     * Serializes an `<svg>` element (with Skia/browser-compatible casing) so it
     * can travel as a data-URI image. Raster `<image>` references rewritten to
     * `reader-epub-res://` URLs are embedded as data URIs: neither Skia's
     * SVGDOM nor a data-URI context can resolve them, and without embedding
     * the whole SVG decodes to nothing (invisible in pagination).
     */
    private fun SharedSemanticDomElement.toStandaloneSvgMarkup(): String {
        val out = StringBuilder()
        appendSvgMarkup(out, isRoot = true)
        return out.toString()
    }

    private fun SharedSemanticDomElement.appendSvgMarkup(out: StringBuilder, isRoot: Boolean) {
        out.append('<').append(name)
        if (isRoot && attribute("xmlns") == null) {
            out.append(" xmlns=\"http://www.w3.org/2000/svg\"")
        }
        for ((key, value) in attributes) {
            var rendered = value
            if (localName == "image" && key == "href") {
                embedSharedSvgImageHref(value)?.let { rendered = it }
            }
            out.append(' ').append(key.sharedSemanticSvgAttributeName())
                .append("=\"").append(rendered.escapeSharedSemanticSvgAttribute()).append('"')
        }
        if (children.isEmpty()) {
            out.append("/>")
            return
        }
        out.append('>')
        for (child in children) {
            when (child) {
                is SharedSemanticDomText -> out.append(child.text.escapeSharedSemanticSvgText())
                is SharedSemanticDomElement -> child.appendSvgMarkup(out, isRoot = false)
            }
        }
        out.append("</").append(name).append('>')
    }

    private fun embedSharedSvgImageHref(href: String): String? {
        if (!isSharedEpubResourceUrl(href)) return null
        val bytes = runCatching { resolveSharedEpubResourceBytes(href) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val entryPath = parseSharedEpubResourceUrl(href)?.entryPath ?: return null
        return "data:${sharedEpubResourceMimeType(entryPath)};base64,${bytes.toSharedSemanticBase64()}"
    }

    private fun buildHeader(
        element: SharedSemanticDomElement,
        target: MutableList<SemanticBlock>,
        inherited: CssStyle
    ) {
        val finalStyle = elementFinalStyle(element, inherited)
        if (finalStyle.display == "none") return
        // Pass the PARENT style, not the final style: buildLocalText re-derives
        // the element's own rules (see elementFinalStyle), so handing it the
        // already-resolved final style applies a relative font-size twice.
        // Android benchmark parity (JVM `buildSemanticTextAndSpans` resolves the
        // element once, then lays out only its child nodes): `h1.chapterTitle
        // { font-size: 175% }` resolved 1.75 x 28sp = 49sp spans under a 28sp
        // block, drawing every heading ~75% too large and wrapping it.
        val local = buildLocalText(listOf(element), inherited)
        if (local.text.isBlank()) return
        target += SemanticHeader(
            level = SharedEpubHeaderLevels[element.localName] ?: 1,
            text = local.text,
            spans = local.spans,
            style = finalStyle,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = local.startCharOffsetInSource,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildTextBlock(
        element: SharedSemanticDomElement,
        target: MutableList<SemanticBlock>,
        inherited: CssStyle
    ) {
        val finalStyle = elementFinalStyle(element, inherited)
        if (finalStyle.display == "none") return
        // Parent style, not the final style: buildLocalText re-derives the
        // element's own rules, so the final style would apply a relative
        // font-size twice (same double-application as buildHeader).
        val local = buildLocalText(
            listOf(element),
            inherited,
            SharedSemanticCssWhiteSpacePreserves(finalStyle.whiteSpace)
        )
        if (local.text.isBlank()) return
        target += SemanticParagraph(
            text = local.text,
            spans = local.spans,
            style = finalStyle,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = local.startCharOffsetInSource,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildPreTextBlock(
        element: SharedSemanticDomElement,
        target: MutableList<SemanticBlock>,
        inherited: CssStyle
    ) {
        // The tag default (pre-wrap) sits underneath author CSS so
        // `pre { white-space: normal }` still wins.
        val finalStyle = CssStyle(whiteSpace = "pre-wrap").merge(elementFinalStyle(element, inherited))
        if (finalStyle.display == "none") return
        // Default-underneath composition, not the final style: buildLocalText
        // re-derives the element's own rules, so the final style would apply a
        // relative font-size twice (same double-application as buildHeader).
        // Repeating the default-underneath merge here keeps `pre` whitespace
        // identical while the font-size resolves exactly once.
        val local = buildLocalText(
            listOf(element),
            CssStyle(whiteSpace = "pre-wrap").merge(inherited),
            SharedSemanticCssWhiteSpacePreserves(finalStyle.whiteSpace)
        )
        if (local.text.isBlank()) return
        target += SemanticParagraph(
            text = local.text,
            spans = local.spans,
            style = finalStyle,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = local.startCharOffsetInSource,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildList(element: SharedSemanticDomElement, inherited: CssStyle): SemanticList? {
        val finalStyle = elementFinalStyle(element, inherited)
        if (finalStyle.display == "none") return null
        val ordered = element.localName == "ol"
        val list = SemanticList(
            items = emptyList(),
            isOrdered = ordered,
            style = finalStyle,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
        val items = buildListItems(element, finalStyle, inheritedOrdinal = element.attribute("start")?.toIntOrNull() ?: 1)
        if (items.isEmpty()) return null
        return list.copy(items = items)
    }

    private fun buildListItems(
        listElement: SharedSemanticDomElement,
        listStyle: CssStyle,
        inheritedOrdinal: Int
    ): List<SemanticListItem> {
        var ordinal = inheritedOrdinal
        val items = mutableListOf<SemanticListItem>()
        val ordered = listElement.localName == "ol"
        listElement.children.forEach { child ->
            if (child !is SharedSemanticDomElement || child.localName != "li") return@forEach
            val nestedLists = child.children
                .filterIsInstance<SharedSemanticDomElement>()
                .filter { it.localName == "ul" || it.localName == "ol" }
                .toSet()
            val itemStyle = elementFinalStyle(child, listStyle)
            if (itemStyle.display == "none") {
                // Hidden subtrees stay hidden entirely, like browsers and the
                // JVM parser: nested lists inside a hidden item never surface.
                ordinal = (child.attribute("value")?.toIntOrNull() ?: ordinal) + 1
                return@forEach
            }
            val local = buildLocalText(
                child.children.filterNot { it in nestedLists },
                itemStyle,
                SharedSemanticCssWhiteSpacePreserves(itemStyle.whiteSpace)
            )
            val value = child.attribute("value")?.toIntOrNull() ?: ordinal
            if (local.text.isNotBlank()) {
                items += SemanticListItem(
                    text = local.text,
                    spans = local.spans,
                    style = itemStyle,
                    elementId = child.elementId(),
                    cfi = child.cfiPath(),
                    startCharOffsetInSource = local.startCharOffsetInSource,
                    itemMarkerImage = null,
                    blockIndex = nextBlockIndex++,
                    markerText = if (ordered) "$value." else "•"
                )
            }
            ordinal = value + 1
            nestedLists.forEach { nested ->
                items += buildListItems(
                    nested,
                    elementFinalStyle(nested, itemStyle),
                    inheritedOrdinal = nested.attribute("start")?.toIntOrNull() ?: 1
                )
            }
        }
        return items
    }

    private fun buildTable(element: SharedSemanticDomElement, inherited: CssStyle): SemanticTable? {
        val finalStyle = elementFinalStyle(element, inherited)
        if (finalStyle.display == "none") return null
        val rows = mutableListOf<List<SemanticTableCell>>()
        fun collectRows(node: SharedSemanticDomElement, rowInherited: CssStyle) {
            if (node.localName == "tr") {
                val rowStyle = elementFinalStyle(node, rowInherited)
                if (rowStyle.display == "none") return
                val cells = node.children.mapNotNull { child ->
                    if (child !is SharedSemanticDomElement || child.localName !in setOf("td", "th")) return@mapNotNull null
                    val cellStyle = elementFinalStyle(child, rowStyle)
                    if (cellStyle.display == "none") return@mapNotNull null
                    val cellBlocks = mutableListOf<SemanticBlock>()
                    buildContainer(child, cellBlocks, cellStyle)
                    SemanticTableCell(
                        content = cellBlocks,
                        isHeader = child.localName == "th",
                        colspan = child.attribute("colspan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        style = cellStyle
                    )
                }
                if (cells.isNotEmpty()) rows += cells
                return
            }
            node.children.forEach { child ->
                if (child is SharedSemanticDomElement) collectRows(child, rowInherited)
            }
        }
        collectRows(element, finalStyle)
        if (rows.isEmpty()) return null
        return SemanticTable(
            rows = rows,
            style = finalStyle,
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    private class LocalTextResult(
        val text: String,
        val spans: List<SemanticSpan>,
        val startCharOffsetInSource: Int
    )

    private fun buildLocalText(
        nodes: List<SharedSemanticDomNode>,
        inherited: CssStyle = CssStyle(),
        preserveWhitespace: Boolean = false
    ): LocalTextResult {
        val text = StringBuilder()
        val spans = mutableListOf<SemanticSpan>()

        fun spanStyleFor(tag: String): SpanStyle? = when {
            tag in SharedEpubBoldTags -> SpanStyle(fontWeight = FontWeight.Bold)
            tag in SharedEpubItalicTags -> SpanStyle(fontStyle = FontStyle.Italic)
            tag in SharedEpubUnderlineTags -> SpanStyle(textDecoration = TextDecoration.Underline)
            tag in SharedEpubStrikeTags -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            tag in SharedEpubCodeTags -> SpanStyle(fontFamily = FontFamily.Monospace)
            else -> null
        }

        // Tag semantics act as the user-agent origin: author/UA/inline CSS
        // merges over them, so `b { font-weight: normal }` still wins.
        fun tagBaseStyle(tag: String): CssStyle {
            return CssStyle(spanStyle = spanStyleFor(tag) ?: SpanStyle())
        }

        fun process(node: SharedSemanticDomNode, inheritedLinkHref: String?, inheritedStyle: CssStyle) {
            when (node) {
                is SharedSemanticDomText -> text.append(node.text.transformedSharedSemanticText(inheritedStyle.textTransform))
                is SharedSemanticDomElement -> when {
                    node.localName in SharedEpubNonRenderableTags -> Unit
                    node.localName == "br" -> text.append('\n')
                    node.localName == "img" -> Unit
                    else -> {
                        val elementStyle = elementFinalStyle(node, inheritedStyle)
                        // Hidden subtrees contribute no text, mirroring the
                        // JVM parser's display:none pruning.
                        if (elementStyle.display == "none") return
                        val start = text.length
                        val href = (if (node.localName == "a") node.attribute("href") else null)
                            ?: inheritedLinkHref
                        node.children.forEach { process(it, href, elementStyle) }
                        val end = text.length
                        val style = tagBaseStyle(node.localName).merge(elementStyle)
                        val elementId = node.elementId()
                        if (style.hasSharedSemanticSpanEffect() || elementId != null ||
                            (href != null && node.localName == "a")
                        ) {
                            spans += SemanticSpan(
                                start = start,
                                end = end,
                                style = style,
                                linkHref = href,
                                tag = node.localName,
                                elementId = elementId
                            )
                        }
                    }
                }
            }
        }

        nodes.forEach { process(it, null, inherited) }

        val rawText = text.toString()
        val startOffset = nodes.firstOrNull()?.normalizedStart ?: -1
        if (preserveWhitespace) {
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) {
                return LocalTextResult(
                    text = "",
                    spans = emptyList(),
                    startCharOffsetInSource = startOffset.coerceAtLeast(0)
                )
            }
            val leadingTrim = rawText.indexOf(trimmed[0])
            val adjustedSpans = spans.mapNotNull { span ->
                val adjustedStart = (span.start - leadingTrim).coerceAtLeast(0)
                val adjustedEnd = (span.end - leadingTrim).coerceAtLeast(adjustedStart)
                if (adjustedEnd > adjustedStart || span.elementId != null) {
                    span.copy(start = adjustedStart, end = adjustedEnd)
                } else {
                    null
                }
            }
            return LocalTextResult(
                text = trimmed,
                spans = adjustedSpans,
                startCharOffsetInSource = startOffset.coerceAtLeast(0)
            )
        }
        val (normalizedText, mapping) = normalizeWithSourceMapping(rawText)
        if (normalizedText.isEmpty()) {
            return LocalTextResult(
                text = "",
                spans = emptyList(),
                startCharOffsetInSource = startOffset.coerceAtLeast(0)
            )
        }
        val inverse = IntArray(rawText.length) { -1 }
        mapping.forEachIndexed { index, sourceIndex ->
            if (sourceIndex in inverse.indices) inverse[sourceIndex] = index
        }
        val adjustedSpans = spans.mapNotNull { span ->
            val adjustedStart = inverse.getOrElse(span.start.coerceAtMost(rawText.lastIndex)) { -1 }
            val adjustedEnd = if (span.end <= 0) {
                -1
            } else {
                inverse.getOrElse((span.end - 1).coerceAtMost(rawText.lastIndex)) { -1 } + 1
            }
            if (adjustedStart >= 0 && adjustedEnd >= 0 && (adjustedEnd > adjustedStart || span.elementId != null)) {
                span.copy(start = adjustedStart, end = adjustedEnd.coerceAtLeast(adjustedStart))
            } else {
                null
            }
        }
        return LocalTextResult(
            text = normalizedText,
            spans = adjustedSpans,
            startCharOffsetInSource = startOffset.coerceAtLeast(0)
        )
    }
}

/** First URL of a srcset list (`"a.jpg 1x, b.jpg 2x"` -> `"a.jpg"`). */
private fun String?.firstSrcSetUrl(): String? {
    val first = this?.split(',')
        ?.firstOrNull()
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return first
}

/**
 * Parses an HTML width/height attribute to px. Bare numbers and `px`
 * (the only absolute units the paginated measure/render path can honor
 * without font context); `pt` converts like the math-SVG path; `%`/`em`
 * intentionally return null so the viewBox/fallback sizing takes over
 * instead of a wrong absolute value.
 */
private fun String?.sharedSemanticCssPixelSize(): Float? {
    val trimmed = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    trimmed.toFloatOrNull()?.let { return it.takeIf { value -> value > 0f } }
    if (trimmed.endsWith("px", ignoreCase = true)) {
        return trimmed.dropLast(2).toFloatOrNull()?.takeIf { it > 0f }
    }
    if (trimmed.endsWith("pt", ignoreCase = true)) {
        return trimmed.dropLast(2).toFloatOrNull()?.takeIf { it > 0f }?.times(1.3333334f)
    }
    return null
}

/** `viewBox="minX minY width height"` -> width to height (attribute key arrives lowercased). */
private fun String?.sharedSemanticViewBoxSize(): Pair<Float, Float>? {
    val parts = this
        ?.trim()
        ?.split(Regex("[,\\s]+"))
        ?.mapNotNull { it.toFloatOrNull() }
        ?: return null
    if (parts.size < 4) return null
    val width = parts[2]
    val height = parts[3]
    return if (width > 0f && height > 0f) width to height else null
}

/**
 * Restores the canonical casing of the SVG attributes the parser lowercased.
 * Geometry-critical only: a lowercased `viewbox` is ignored by SVG parsers,
 * which would lose the aspect ratio of every serialized SVG without explicit
 * width/height.
 */
private fun String.sharedSemanticSvgAttributeName(): String = when (this) {
    "viewbox" -> "viewBox"
    "preserveaspectratio" -> "preserveAspectRatio"
    "gradienttransform" -> "gradientTransform"
    "patterntransform" -> "patternTransform"
    else -> this
}

private fun String.escapeSharedSemanticSvgAttribute(): String = replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")

private fun String.escapeSharedSemanticSvgText(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.toSharedSemanticBase64(): String = Base64.Default.encode(this)
