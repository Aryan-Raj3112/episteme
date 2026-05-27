package com.aryan.reader.shared.opds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URL
import java.util.UUID

class SharedOpdsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(bodyString: String, baseUrl: String): OpdsFeed {
        val trimmed = bodyString.trimStart()
        return if (trimmed.startsWith("{")) {
            parseOpds2(trimmed, baseUrl)
        } else {
            parseOpds1(trimmed, baseUrl)
        }
    }

    fun extractOpenSearchTemplate(bodyString: String, openSearchUrl: String): String? {
        val document = Jsoup.parse(bodyString, openSearchUrl, Parser.xmlParser())
        return document.allElements
            .asSequence()
            .filter { it.localTagName().equals("url", ignoreCase = true) }
            .mapNotNull { urlElement ->
                val type = urlElement.attrAny("type").orEmpty()
                val template = urlElement.attrAny("template")
                if (
                    template != null &&
                    (type.contains("atom+xml", ignoreCase = true) || type.contains("opds+xml", ignoreCase = true))
                ) {
                    OpenSearchTemplateCandidate(
                        template = resolveUrl(openSearchUrl, template),
                        priority = openSearchTemplatePriority(type)
                    )
                } else {
                    null
                }
            }
            .maxByOrNull { it.priority }
            ?.template
    }

    private fun parseOpds2(jsonString: String, baseUrl: String): OpdsFeed {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val metadata = root.obj("metadata")
        val title = metadata?.string("title") ?: "OPDS 2.0 Feed"

        var nextUrl: String? = null
        var searchUrl: String? = null
        val facets = mutableListOf<OpdsFacet>()

        root.array("links").forEach { link ->
            val href = link.string("href")
            if (!href.isNullOrBlank()) {
                val resolvedHref = resolveUrl(baseUrl, href)
                val rels = link.rels()
                when {
                    "next" in rels -> nextUrl = resolvedHref
                    "search" in rels -> searchUrl = resolvedHref
                }
            }
        }

        root.array("facets").forEach { facetObj ->
            val group = facetObj.obj("metadata")?.string("title") ?: "Filter"
            facetObj.array("links").forEach { link ->
                val href = link.string("href")
                if (!href.isNullOrBlank()) {
                    facets.add(
                        OpdsFacet(
                            title = link.string("title") ?: "Facet",
                            group = group,
                            url = resolveUrl(baseUrl, href),
                            isActive = link.obj("properties")?.boolean("active") ?: false
                        )
                    )
                }
            }
        }

        val entries = mutableListOf<OpdsEntry>()
        root.array("publications").forEach { entries.add(parseOpds2Publication(it, baseUrl)) }
        root.array("navigation").forEach { entries.add(parseOpds2Navigation(it, baseUrl)) }
        root.array("groups").forEach { group ->
            val groupTitle = group.obj("metadata")?.string("title").orEmpty()
            group.array("navigation").forEach { entries.add(parseOpds2Navigation(it, baseUrl)) }
            group.array("publications").forEach { entries.add(parseOpds2Publication(it, baseUrl)) }
            group.array("links").forEach { link ->
                val href = link.string("href")
                if (!href.isNullOrBlank()) {
                    entries.add(
                        OpdsEntry(
                            id = href,
                            title = link.string("title") ?: groupTitle,
                            summary = null,
                            authors = emptyList(),
                            coverUrl = null,
                            acquisitions = emptyList(),
                            navigationUrl = resolveUrl(baseUrl, href)
                        )
                    )
                }
            }
        }

        return OpdsFeed(title = title, entries = entries, nextUrl = nextUrl, searchUrl = searchUrl, facets = facets)
    }

    private fun parseOpds2Publication(pub: JsonObject, baseUrl: String): OpdsEntry {
        val metadata = pub.obj("metadata")
        val title = metadata?.string("title") ?: "Unknown Title"
        val id = metadata?.string("identifier") ?: pub.string("id") ?: UUID.randomUUID().toString()
        val summary = metadata?.string("description") ?: metadata?.string("summary")
        val language = metadata?.string("language")
        val publisher = metadata?.string("publisher")
        val published = metadata?.string("published")
        val authors = parseOpds2Authors(metadata?.get("author"), baseUrl)
        val categories = parseOpds2Categories(metadata?.get("subject"))
        val (series, seriesIndex) = parseOpds2Series(metadata?.obj("belongsTo"))

        var coverUrl: String? = null
        var coverPriority = Int.MIN_VALUE
        pub.array("images").forEach { image ->
            val href = image.string("href")
            if (!href.isNullOrBlank()) {
                val resolvedHref = resolveUrl(baseUrl, href)
                val priority = image.coverPriority()
                if (coverUrl == null || priority > coverPriority) {
                    coverUrl = resolvedHref
                    coverPriority = priority
                }
            }
        }

        val acquisitions = mutableListOf<OpdsAcquisition>()
        var pseCount: Int? = null
        var pseUrlTemplate: String? = null
        pub.array("links").forEach { link ->
            val href = link.string("href")
            if (!href.isNullOrBlank()) {
                val rels = link.rels()
                val type = link.string("type").orEmpty()
                if (rels.any { it.equals(PSE_STREAM_REL, ignoreCase = true) }) {
                    pseUrlTemplate = resolveUrl(baseUrl, href)
                    pseCount = link.obj("properties")?.int("numberOfItems")?.takeIf { it > 0 }
                }
                val coverLinkPriority = coverPriority(rels, type)
                if (coverLinkPriority != null && coverLinkPriority > coverPriority) {
                    coverUrl = resolveUrl(baseUrl, href)
                    coverPriority = coverLinkPriority
                }
                if (rels.any { it.contains("acquisition", ignoreCase = true) } ||
                    (rels.any { it.equals("enclosure", ignoreCase = true) } && type.isDownloadableMediaType())
                ) {
                    acquisitions.add(OpdsAcquisition(resolveUrl(baseUrl, href), type))
                }
            }
        }

        return OpdsEntry(
            id = id,
            title = title,
            summary = summary,
            authors = authors,
            coverUrl = coverUrl,
            acquisitions = acquisitions,
            navigationUrl = null,
            publisher = publisher,
            published = published,
            language = language,
            series = series,
            seriesIndex = seriesIndex,
            categories = categories,
            pseCount = pseCount,
            pseUrlTemplate = pseUrlTemplate
        )
    }

    private fun parseOpds2Navigation(nav: JsonObject, baseUrl: String): OpdsEntry {
        val href = nav.string("href")
        return OpdsEntry(
            id = href.orEmpty(),
            title = nav.string("title") ?: "Unknown",
            summary = nav.string("description"),
            authors = emptyList(),
            coverUrl = null,
            acquisitions = emptyList(),
            navigationUrl = href?.takeIf { it.isNotBlank() }?.let { resolveUrl(baseUrl, it) }
        )
    }

    private fun parseOpds1(xmlString: String, baseUrl: String): OpdsFeed {
        val document = Jsoup.parse(xmlString, baseUrl, Parser.xmlParser())
        val feed = document.allElements.firstOrNull { it.localTagName() == "feed" }
            ?: return OpdsFeed("OPDS Feed", emptyList(), nextUrl = null)
        var title = ""
        var nextUrl: String? = null
        var searchUrl: String? = null
        val entries = mutableListOf<OpdsEntry>()
        val facets = mutableListOf<OpdsFacet>()

        feed.children().forEach { child ->
            when (child.localTagName()) {
                "title" -> title = child.cleanText()
                "entry" -> entries.add(readOpds1Entry(child, baseUrl))
                "link" -> {
                    val rel = child.attrAny("rel")
                    val href = child.attrAny("href")
                    val linkTitle = child.attrAny("title")
                    val facetGroup = child.attrAny("opds:facetGroup", "facetGroup") ?: "Filter"
                    val activeFacet = child.attrAny("opds:activeFacet", "activeFacet") == "true"
                    when {
                        rel == "next" -> nextUrl = href?.let { resolveUrl(baseUrl, it) }
                        rel == "search" -> searchUrl = href?.let { resolveUrl(baseUrl, it) }
                        rel == "facet" || rel == "http://opds-spec.org/facet" -> {
                            if (href != null && linkTitle != null) {
                                facets.add(OpdsFacet(linkTitle, facetGroup, resolveUrl(baseUrl, href), activeFacet))
                            }
                        }
                    }
                }
            }
        }

        return OpdsFeed(title, entries, nextUrl, searchUrl, facets)
    }

    private fun readOpds1Entry(entry: Element, baseUrl: String): OpdsEntry {
        var id = ""
        var title = ""
        var summary: String? = null
        var coverUrl: String? = null
        var coverPriority = Int.MIN_VALUE
        var navigationUrl: String? = null
        var publisher: String? = null
        var published: String? = null
        var language: String? = null
        var series: String? = null
        var seriesIndex: String? = null
        var pseCount: Int? = null
        var pseUrlTemplate: String? = null
        val authors = mutableListOf<OpdsAuthor>()
        val categories = mutableListOf<String>()
        val acquisitions = mutableListOf<OpdsAcquisition>()

        entry.children().forEach { child ->
            when (val tagName = child.localTagName()) {
                "id" -> id = child.cleanText()
                "title" -> title = child.cleanText()
                "summary", "content" -> summary = child.text().trim()
                "author" -> authors.add(readOpds1Author(child, baseUrl))
                "publisher" -> publisher = child.cleanText()
                "language" -> if (language == null) language = child.cleanText()
                "issued", "published", "updated" -> {
                    val date = child.cleanText()
                    if (published == null || tagName != "updated") published = date
                }
                "category" -> {
                    val category = child.attrAny("label") ?: child.attrAny("term")
                    if (!category.isNullOrBlank()) categories.add(category)
                }
                "meta" -> {
                    val property = child.attrAny("property", "name")
                    val content = child.attrAny("content")
                    val textContent = child.cleanText()
                    when (property) {
                        "calibre:series" -> series = content ?: textContent.takeIf { it.isNotBlank() }
                        "calibre:series_index" -> seriesIndex = content ?: textContent.takeIf { it.isNotBlank() }
                    }
                }
                "link" -> {
                    val rel = child.attrAny("rel").orEmpty()
                    val rels = rel.relTokens()
                    val href = child.attrAny("href").orEmpty()
                    val type = child.attrAny("type").orEmpty()
                    val linkTitle = child.attrAny("title")

                    if (rels.any { it.equals(PSE_STREAM_REL, ignoreCase = true) }) {
                        pseUrlTemplate = resolveUrl(baseUrl, href)
                        pseCount = child.attrAny("pse:count", "count")?.toIntOrNull()
                    }

                    if (rels.any { it.equals("http://calibre-ebook.com/opds/series", ignoreCase = true) } && series == null) {
                        series = linkTitle
                    }

                    if (href.isNotEmpty()) {
                        val absoluteUrl = resolveUrl(baseUrl, href)
                        val linkCoverPriority = coverPriority(rels, type)
                        when {
                            linkCoverPriority != null -> {
                                if (linkCoverPriority > coverPriority) {
                                    coverUrl = absoluteUrl
                                    coverPriority = linkCoverPriority
                                }
                            }
                            rels.any { it.contains("acquisition", ignoreCase = true) } ||
                                (rels.any { it.equals("enclosure", ignoreCase = true) } && type.isDownloadableMediaType()) -> {
                                acquisitions.add(OpdsAcquisition(absoluteUrl, type))
                            }
                            type.contains("profile=opds-catalog", ignoreCase = true) ||
                                type.contains("application/atom+xml", ignoreCase = true) -> {
                                if (navigationUrl == null) navigationUrl = absoluteUrl
                            }
                            rels.any { it == "subsection" || it == "collection" || it == "start" } -> {
                                if (navigationUrl == null) navigationUrl = absoluteUrl
                            }
                        }
                    }
                }
            }
        }

        return OpdsEntry(
            id = id,
            title = title,
            summary = summary,
            authors = authors,
            coverUrl = coverUrl,
            acquisitions = acquisitions,
            navigationUrl = navigationUrl,
            publisher = publisher,
            published = published,
            language = language,
            series = series,
            seriesIndex = seriesIndex,
            categories = categories,
            pseCount = pseCount,
            pseUrlTemplate = pseUrlTemplate
        )
    }

    private fun readOpds1Author(author: Element, baseUrl: String): OpdsAuthor {
        var name = ""
        var uri: String? = null
        author.children().forEach { child ->
            when (child.localTagName()) {
                "name" -> name = child.cleanText()
                "uri" -> uri = resolveUrl(baseUrl, child.cleanText())
            }
        }
        return OpdsAuthor(name, uri)
    }

    private fun parseOpds2Authors(authorElement: JsonElement?, baseUrl: String): List<OpdsAuthor> {
        return when (authorElement) {
            is JsonArray -> authorElement.mapNotNull { parseOpds2Author(it, baseUrl) }
            null -> emptyList()
            else -> listOfNotNull(parseOpds2Author(authorElement, baseUrl))
        }
    }

    private fun parseOpds2Author(authorElement: JsonElement, baseUrl: String): OpdsAuthor? {
        authorElement.primitiveString()?.let { return OpdsAuthor(it, null) }
        val obj = authorElement.asObjectOrNull() ?: return null
        val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return null
        val uri = obj.array("links")
            .firstOrNull()
            ?.string("href")
            ?.let { resolveUrl(baseUrl, it) }
        return OpdsAuthor(name, uri)
    }

    private fun parseOpds2Categories(subjectElement: JsonElement?): List<String> {
        return when (subjectElement) {
            is JsonArray -> subjectElement.mapNotNull(::parseOpds2Category)
            null -> emptyList()
            else -> listOfNotNull(parseOpds2Category(subjectElement))
        }
    }

    private fun parseOpds2Category(subjectElement: JsonElement): String? {
        subjectElement.primitiveString()?.let { return it }
        return subjectElement.asObjectOrNull()?.string("name")?.takeIf { it.isNotBlank() }
    }

    private fun parseOpds2Series(belongsTo: JsonObject?): Pair<String?, String?> {
        val seriesElement = belongsTo?.get("series") ?: return null to null
        val first = if (seriesElement is JsonArray) seriesElement.firstOrNull() else seriesElement
        first?.primitiveString()?.let { return it to null }
        val seriesObj = first?.asObjectOrNull() ?: return null to null
        val name = seriesObj.string("name")
        val index = seriesObj.get("position")
            ?.jsonPrimitive
            ?.doubleOrNull
            ?.toString()
            ?.removeSuffix(".0")
        return name to index
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        return runCatching {
            URL(URL(baseUrl), href).toString()
                .replace("http://m.gutenberg.org", "https://m.gutenberg.org")
                .replace("http://www.gutenberg.org", "https://www.gutenberg.org")
        }.getOrDefault(href)
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObjectOrNull()

    private fun JsonObject.array(name: String): List<JsonObject> {
        return runCatching { get(name)?.jsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty() }
            .getOrDefault(emptyList())
    }

    private fun JsonObject.string(name: String): String? {
        return runCatching { get(name)?.jsonPrimitive?.contentOrNull }.getOrNull()
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        return runCatching { get(name)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() }.getOrNull()
    }

    private fun JsonObject.int(name: String): Int? {
        return runCatching { get(name)?.jsonPrimitive?.intOrNull }.getOrNull()
    }

    private fun JsonObject.coverPriority(): Int {
        return coverPriority(rels(), string("type").orEmpty()) ?: ImageTypeCoverPriority
    }

    private fun JsonObject.rels(): List<String> {
        val rel = get("rel") ?: return emptyList()
        rel.primitiveString()?.let { return it.relTokens() }
        return runCatching { rel.jsonArray.mapNotNull { it.primitiveString() } }.getOrDefault(emptyList())
            .flatMap { it.relTokens() }
    }

    private fun JsonElement.primitiveString(): String? {
        return runCatching { jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? {
        return runCatching { jsonObject }.getOrNull()
    }

    private fun Element.localTagName(): String = tagName().substringAfter(":")

    private fun Element.cleanText(): String = wholeText().trim().ifBlank { text().trim() }

    private fun Element.attrAny(vararg names: String): String? {
        names.forEach { name ->
            val direct = attr(name)
            if (direct.isNotBlank()) return direct
        }
        val localNames = names.map { it.substringAfter(":") }
        return attributes()
            .asList()
            .firstOrNull { attribute ->
                localNames.any { local -> attribute.key.substringAfter(":").equals(local, ignoreCase = true) }
            }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun coverPriority(rel: String, type: String): Int? {
        return coverPriority(rel.relTokens(), type)
    }

    private fun coverPriority(rels: List<String>, type: String): Int? {
        val normalizedRels = rels.map { it.lowercase() }
        return when {
            normalizedRels.any { it == "thumbnail" || it.endsWith("/thumbnail") || it.endsWith("/image/thumbnail") } ->
                ThumbnailCoverPriority
            normalizedRels.any { it == "cover" || it.endsWith("/cover") || it.contains("/image") } ->
                GenericCoverPriority
            type.lowercase().substringBefore(';').trim().startsWith("image/") ->
                ImageTypeCoverPriority
            else -> null
        }
    }

    private fun String.relTokens(): List<String> {
        return trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
    }

    private fun openSearchTemplatePriority(type: String): Int {
        val normalizedType = type.lowercase()
        return when {
            normalizedType.contains("profile=opds-catalog") && normalizedType.contains("kind=acquisition") -> 4
            normalizedType.contains("profile=opds-catalog") -> 3
            normalizedType.contains("opds+xml") -> 2
            normalizedType.contains("atom+xml") -> 1
            else -> 0
        }
    }

    private fun String.isDownloadableMediaType(): Boolean {
        val normalizedType = lowercase().substringBefore(';').trim()
        return normalizedType in DownloadableMediaTypes ||
            DownloadableMediaTypeHints.any { normalizedType.contains(it) }
    }

    private data class OpenSearchTemplateCandidate(
        val template: String,
        val priority: Int
    )

    private companion object {
        private const val PSE_STREAM_REL = "http://vaemendis.net/opds-pse/stream"
        private const val ImageTypeCoverPriority = 1
        private const val GenericCoverPriority = 2
        private const val ThumbnailCoverPriority = 3
        private val DownloadableMediaTypes = setOf(
            "application/epub+zip",
            "application/kepub+zip",
            "application/pdf",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/vnd.comicbook+zip",
            "application/vnd.comicbook-rar",
            "application/x-cbz",
            "application/x-cbr",
            "application/x-fictionbook+xml",
            "application/fb2+zip",
            "application/xhtml+xml",
            "text/html",
            "text/plain",
            "text/markdown",
            "text/x-markdown"
        )
        private val DownloadableMediaTypeHints = listOf(
            "epub",
            "kepub",
            "mobipocket",
            "kindle",
            "azw",
            "fictionbook",
            "comicbook"
        )
    }
}
