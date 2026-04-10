// OpdsParser.kt
package com.aryan.reader.opds

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.InputStream
import java.util.UUID

class OpdsParser {

    fun parse(bodyString: String, baseUrl: String): OpdsFeed {
        val trimmed = bodyString.trimStart()
        return if (trimmed.startsWith("{")) {
            Timber.tag("OpdsDebug").d("Detected OPDS 2.0 (JSON) feed")
            parseOpds2(trimmed, baseUrl)
        } else {
            Timber.tag("OpdsDebug").d("Detected OPDS 1.x (XML) feed")
            parseOpds1(trimmed.byteInputStream(), baseUrl)
        }
    }

    // --- OPDS 2.0 (JSON) Parsing ---

    private fun parseOpds2(jsonString: String, baseUrl: String): OpdsFeed {
        val root = JSONObject(jsonString)
        val metadata = root.optJSONObject("metadata")
        val title = metadata?.optString("title") ?: "OPDS 2.0 Feed"

        var nextUrl: String? = null
        var searchUrl: String? = null
        val facets = mutableListOf<OpdsFacet>()

        // Root Links
        val links = root.optJSONArray("links")
        if (links != null) {
            for (i in 0 until links.length()) {
                val link = links.getJSONObject(i)
                val relArray = link.optJSONArray("rel")
                val rels = mutableListOf<String>()
                if (relArray != null) {
                    for (j in 0 until relArray.length()) rels.add(relArray.getString(j))
                } else if (link.has("rel")) {
                    val rel = link.optString("rel")
                    if (rel.isNotBlank()) rels.add(rel)
                }

                val href = link.optString("href")
                if (href.isNotEmpty()) {
                    val resolvedHref = resolveUrl(baseUrl, href)
                    if (rels.contains("next")) {
                        nextUrl = resolvedHref
                    } else if (rels.contains("search")) {
                        searchUrl = resolvedHref
                    }
                }
            }
        }

        // Facets
        val facetsArray = root.optJSONArray("facets")
        if (facetsArray != null) {
            for (i in 0 until facetsArray.length()) {
                val facetObj = facetsArray.getJSONObject(i)
                val group = facetObj.optJSONObject("metadata")?.optString("title") ?: "Filter"
                val facetLinks = facetObj.optJSONArray("links")
                if (facetLinks != null) {
                    for (j in 0 until facetLinks.length()) {
                        val link = facetLinks.getJSONObject(j)
                        val href = link.optString("href")
                        if (href.isNotEmpty()) {
                            val titleFacet = link.optString("title", "Facet")
                            val properties = link.optJSONObject("properties")
                            val active = properties?.optBoolean("active", false) ?: false
                            facets.add(OpdsFacet(titleFacet, group, resolveUrl(baseUrl, href), active))
                        }
                    }
                }
            }
        }

        val entries = mutableListOf<OpdsEntry>()

        // Publications
        val publications = root.optJSONArray("publications")
        if (publications != null) {
            for (i in 0 until publications.length()) {
                entries.add(parseOpds2Publication(publications.getJSONObject(i), baseUrl))
            }
        }

        // Navigation
        val navigation = root.optJSONArray("navigation")
        if (navigation != null) {
            for (i in 0 until navigation.length()) {
                entries.add(parseOpds2Navigation(navigation.getJSONObject(i), baseUrl))
            }
        }

        // Groups (Collections containing sub-navigation or sub-publications)
        val groups = root.optJSONArray("groups")
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                val groupTitle = group.optJSONObject("metadata")?.optString("title") ?: ""

                val groupNav = group.optJSONArray("navigation")
                if (groupNav != null) {
                    for (j in 0 until groupNav.length()) {
                        entries.add(parseOpds2Navigation(groupNav.getJSONObject(j), baseUrl))
                    }
                }

                val groupPubs = group.optJSONArray("publications")
                if (groupPubs != null) {
                    for (j in 0 until groupPubs.length()) {
                        entries.add(parseOpds2Publication(groupPubs.getJSONObject(j), baseUrl))
                    }
                }

                val groupLinks = group.optJSONArray("links")
                if (groupLinks != null) {
                    for (j in 0 until groupLinks.length()) {
                        val link = groupLinks.getJSONObject(j)
                        val href = link.optString("href")
                        if (href.isNotEmpty()) {
                            val linkTitle = link.optString("title", groupTitle)
                            entries.add(OpdsEntry(
                                id = href,
                                title = linkTitle,
                                summary = null,
                                authors = emptyList(),
                                coverUrl = null,
                                acquisitions = emptyList(),
                                navigationUrl = resolveUrl(baseUrl, href)
                            ))
                        }
                    }
                }
            }
        }

        return OpdsFeed(title, entries, nextUrl, searchUrl, facets)
    }

    private fun parseOpds2Publication(pub: JSONObject, baseUrl: String): OpdsEntry {
        val metadata = pub.optJSONObject("metadata")
        val title = metadata?.optString("title") ?: "Unknown Title"
        val id = metadata?.optString("identifier") ?: pub.optString("id", UUID.randomUUID().toString())
        val summary = metadata?.optString("description") ?: metadata?.optString("summary")
        val language = metadata?.optString("language")
        val publisher = metadata?.optString("publisher")
        val published = metadata?.optString("published")

        val authors = mutableListOf<OpdsAuthor>()
        val authorObj = metadata?.opt("author")
        if (authorObj is String) {
            authors.add(OpdsAuthor(authorObj, null))
        } else if (authorObj is JSONArray) {
            for (i in 0 until authorObj.length()) {
                val item = authorObj.get(i)
                if (item is String) authors.add(OpdsAuthor(item, null))
                else if (item is JSONObject) {
                    val name = item.optString("name")
                    var uri: String? = null
                    val links = item.optJSONArray("links")
                    if (links != null && links.length() > 0) {
                        uri = resolveUrl(baseUrl, links.getJSONObject(0).optString("href"))
                    }
                    if (name.isNotBlank()) authors.add(OpdsAuthor(name, uri))
                }
            }
        } else if (authorObj is JSONObject) {
            val name = authorObj.optString("name")
            var uri: String? = null
            val links = authorObj.optJSONArray("links")
            if (links != null && links.length() > 0) {
                uri = resolveUrl(baseUrl, links.getJSONObject(0).optString("href"))
            }
            if (name.isNotBlank()) authors.add(OpdsAuthor(name, uri))
        }

        val categories = mutableListOf<String>()
        when (val subjectObj = metadata?.opt("subject")) {
            is String -> categories.add(subjectObj)
            is JSONArray -> {
                for (i in 0 until subjectObj.length()) {
                    val subj = subjectObj.get(i)
                    if (subj is String) categories.add(subj)
                    else if (subj is JSONObject) categories.add(subj.optString("name"))
                }
            }
            is JSONObject -> {
                categories.add(subjectObj.optString("name"))
            }
        }

        var series: String? = null
        var seriesIndex: String? = null
        val belongsTo = metadata?.optJSONObject("belongsTo")
        if (belongsTo != null) {
            val seriesObj = belongsTo.opt("series")
            if (seriesObj is String) {
                series = seriesObj
            } else if (seriesObj is JSONObject) {
                series = seriesObj.optString("name")
                if (seriesObj.has("position")) {
                    seriesIndex = seriesObj.optDouble("position").toString().removeSuffix(".0")
                }
            } else if (seriesObj is JSONArray && seriesObj.length() > 0) {
                val firstSeries = seriesObj.get(0)
                if (firstSeries is String) {
                    series = firstSeries
                } else if (firstSeries is JSONObject) {
                    series = firstSeries.optString("name")
                    if (firstSeries.has("position")) {
                        seriesIndex = firstSeries.optDouble("position").toString().removeSuffix(".0")
                    }
                }
            }
        }

        var coverUrl: String? = null
        val images = pub.optJSONArray("images")
        if (images != null && images.length() > 0) {
            for (i in 0 until images.length()) {
                val image = images.getJSONObject(i)
                val href = image.optString("href")
                if (href.isNotEmpty()) {
                    val resolvedHref = resolveUrl(baseUrl, href)
                    if (coverUrl == null) coverUrl = resolvedHref
                    val rels = image.opt("rel")
                    var isCover = false
                    if (rels is String && rels == "cover") isCover = true
                    else if (rels is JSONArray) {
                        for (j in 0 until rels.length()) if (rels.optString(j) == "cover") isCover = true
                    }
                    if (isCover) {
                        coverUrl = resolvedHref
                        break
                    }
                }
            }
        }

        val acquisitions = mutableListOf<OpdsAcquisition>()
        var pseCount: Int? = null
        var pseUrlTemplate: String? = null

        val links = pub.optJSONArray("links")
        if (links != null) {
            for (i in 0 until links.length()) {
                val link = links.getJSONObject(i)
                val href = link.optString("href")
                if (href.isNotEmpty()) {
                    val rels = link.opt("rel")

                    var isStream = false
                    if (rels is String && rels == "http://vaemendis.net/opds-pse/stream") isStream = true
                    else if (rels is JSONArray) {
                        for (j in 0 until rels.length()) if (rels.optString(j) == "http://vaemendis.net/opds-pse/stream") isStream = true
                    }
                    if (isStream) {
                        pseUrlTemplate = resolveUrl(baseUrl, href)
                        val properties = link.optJSONObject("properties")
                        pseCount = properties?.optInt("numberOfItems")?.takeIf { it > 0 }
                    }

                    var isAcquisition = false
                    if (rels is String && rels.contains("acquisition")) isAcquisition = true
                    else if (rels is JSONArray) {
                        for (j in 0 until rels.length()) if (rels.optString(j).contains("acquisition")) isAcquisition = true
                    }

                    if (isAcquisition) {
                        val type = link.optString("type") ?: ""
                        acquisitions.add(OpdsAcquisition(resolveUrl(baseUrl, href), type))
                    }
                }
            }
        }

        return OpdsEntry(
            id = id, title = title, summary = summary, authors = authors,
            coverUrl = coverUrl, acquisitions = acquisitions,
            navigationUrl = null, publisher = publisher, published = published,
            language = language, series = series, seriesIndex = seriesIndex, categories = categories,
            pseCount = pseCount, pseUrlTemplate = pseUrlTemplate
        )
    }

    private fun parseOpds2Navigation(nav: JSONObject, baseUrl: String): OpdsEntry {
        val title = nav.optString("title", "Unknown")
        val href = nav.optString("href")
        val summary = nav.optString("description")
        val navigationUrl = if (href.isNotEmpty()) resolveUrl(baseUrl, href) else null

        return OpdsEntry(
            id = href, title = title, summary = summary, authors = emptyList(),
            coverUrl = null, acquisitions = emptyList(),
            navigationUrl = navigationUrl
        )
    }

    // --- OPDS 1.x (XML) Parsing ---

    private fun parseOpds1(inputStream: InputStream, baseUrl: String): OpdsFeed {
        return inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            Timber.tag("OpdsDebug").d($$"Parser started at root tag: <${parser.name}>")
            readFeed(parser, baseUrl)
        }
    }

    private fun readFeed(parser: XmlPullParser, baseUrl: String): OpdsFeed {
        var title = ""
        var nextUrl: String? = null
        var searchUrl: String? = null
        val entries = mutableListOf<OpdsEntry>()
        val facets = mutableListOf<OpdsFacet>()

        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name.substringAfter(":")) {
                "title" -> title = readText(parser)
                "entry" -> entries.add(readEntry(parser, baseUrl))
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel")
                    val href = parser.getAttributeValue(null, "href")
                    val linkTitle = parser.getAttributeValue(null, "title")
                    val facetGroup = parser.getAttributeValue(null, "opds:facetGroup") ?: "Filter"
                    val activeFacet = parser.getAttributeValue(null, "opds:activeFacet") == "true"

                    if (rel == "next") {
                        nextUrl = resolveUrl(baseUrl, href ?: "")
                    } else if (rel == "search") {
                        searchUrl = resolveUrl(baseUrl, href ?: "")
                    } else if (rel == "facet" || rel == "http://opds-spec.org/facet") {
                        if (href != null && linkTitle != null) {
                            facets.add(OpdsFacet(linkTitle, facetGroup, resolveUrl(baseUrl, href), activeFacet))
                        }
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return OpdsFeed(title, entries, nextUrl, searchUrl, facets)
    }

    private fun readEntry(parser: XmlPullParser, baseUrl: String): OpdsEntry {
        parser.require(XmlPullParser.START_TAG, null, "entry")
        var id = ""; var title = ""; var summary: String? = null
        var coverUrl: String? = null; var navigationUrl: String? = null
        var publisher: String? = null; var published: String? = null; var language: String? = null
        var series: String? = null; var seriesIndex: String? = null
        var pseCount: Int? = null
        var pseUrlTemplate: String? = null
        val authors = mutableListOf<OpdsAuthor>()
        val categories = mutableListOf<String>()
        val acquisitions = mutableListOf<OpdsAcquisition>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (val tagName = parser.name.substringAfter(":")) {
                "id" -> id = readText(parser)
                "title" -> title = readText(parser)
                "summary", "content" -> summary = readText(parser)
                "author" -> authors.add(readAuthor(parser, baseUrl))
                "publisher" -> publisher = readText(parser)
                "language" -> language = language ?: readText(parser)
                "issued", "published", "updated" -> {
                    val date = readText(parser)
                    if (published == null || tagName != "updated") published = date
                }
                "category" -> {
                    val label = parser.getAttributeValue(null, "label")
                    val term = parser.getAttributeValue(null, "term")
                    val cat = label ?: term
                    if (!cat.isNullOrBlank()) categories.add(cat)
                    skip(parser)
                }
                "meta" -> {
                    val property = parser.getAttributeValue(null, "property") ?: parser.getAttributeValue(null, "name")
                    val content = parser.getAttributeValue(null, "content")
                    val textContent = readText(parser)
                    if (property == "calibre:series") series = content ?: textContent.takeIf { it.isNotBlank() }
                    else if (property == "calibre:series_index") seriesIndex = content ?: textContent.takeIf { it.isNotBlank() }
                }
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel") ?: ""
                    val href = parser.getAttributeValue(null, "href") ?: ""
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    val linkTitle = parser.getAttributeValue(null, "title")

                    if (rel == "http://vaemendis.net/opds-pse/stream") {
                        pseUrlTemplate = resolveUrl(baseUrl, href)
                        val countStr = parser.getAttributeValue(null, "pse:count")
                        pseCount = countStr?.toIntOrNull()
                    }

                    if (rel == "http://calibre-ebook.com/opds/series") {
                        if (series == null) series = linkTitle
                    }

                    if (href.isNotEmpty()) {
                        val absoluteUrl = resolveUrl(baseUrl, href)

                        if (rel.contains("http://opds-spec.org/image")) {
                            if (coverUrl == null || rel.contains("thumbnail")) coverUrl = absoluteUrl
                        } else if (rel.contains("http://opds-spec.org/acquisition")) {
                            acquisitions.add(OpdsAcquisition(absoluteUrl, type))
                        } else if (type.contains("profile=opds-catalog") || type.contains("application/atom+xml")) {
                            if (navigationUrl == null) navigationUrl = absoluteUrl
                        } else if (rel == "subsection" || rel == "collection" || rel == "start") {
                            if (navigationUrl == null) navigationUrl = absoluteUrl
                        }
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return OpdsEntry(id, title, summary, authors, coverUrl, acquisitions, navigationUrl, publisher, published, language, series, seriesIndex, categories, pseCount, pseUrlTemplate)
    }

    private fun readAuthor(parser: XmlPullParser, baseUrl: String): OpdsAuthor {
        var name = ""
        var uri: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name.substringAfter(":")) {
                "name" -> name = readText(parser)
                "uri" -> uri = resolveUrl(baseUrl, readText(parser))
                else -> skip(parser)
            }
        }
        return OpdsAuthor(name, uri)
    }

    private fun readText(parser: XmlPullParser): String {
        val result = StringBuilder()
        var depth = 1

        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    result.append(parser.text)
                }
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
        return result.toString().trim()
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) throw java.lang.IllegalStateException()
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        return try {
            val resolved = java.net.URL(java.net.URL(baseUrl), href).toString()

            resolved.replace("http://m.gutenberg.org", "https://m.gutenberg.org")
                .replace("http://www.gutenberg.org", "https://www.gutenberg.org")
        } catch (_: Exception) {
            href
        }
    }
}