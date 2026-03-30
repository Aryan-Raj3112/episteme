package com.aryan.reader.opds

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.InputStream

class OpdsParser {

    fun parse(inputStream: InputStream, baseUrl: String): OpdsFeed {
        return inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            Timber.tag("OpdsDebug").d("Parser started at root tag: <${parser.name}>")
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
        var id = ""; var title = ""; var summary: String? = null; var author: String? = null
        var coverUrl: String? = null; var downloadUrl: String? = null; var downloadMimeType: String? = null; var navigationUrl: String? = null
        var publisher: String? = null; var published: String? = null; var language: String? = null
        var series: String? = null; var seriesIndex: String? = null
        val categories = mutableListOf<String>()
        var currentBestPriority = -1

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (val tagName = parser.name.substringAfter(":")) {
                "id" -> id = readText(parser)
                "title" -> title = readText(parser)
                "summary", "content" -> summary = readText(parser)
                "author" -> author = readAuthor(parser)
                "publisher" -> publisher = readText(parser)
                "language" -> language = readText(parser)
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

                    if (rel == "http://calibre-ebook.com/opds/series") {
                        if (series == null) series = linkTitle
                    }

                    if (href.isNotEmpty()) {
                        val absoluteUrl = resolveUrl(baseUrl, href)

                        if (rel.contains("http://opds-spec.org/image")) {
                            if (coverUrl == null || rel.contains("thumbnail")) coverUrl = absoluteUrl
                        } else if (rel.contains("http://opds-spec.org/acquisition")) {
                            val formatPriority = when {
                                type.contains("epub") -> 5
                                type.contains("pdf") -> 4
                                type.contains("mobi") || type.contains("x-mobipocket-ebook") -> 3
                                type.contains("fictionbook") || type.contains("fb2") -> 2
                                type.contains("cbz") || type.contains("comicbook") -> 1
                                type.contains("txt") || type.contains("text/plain") -> 0
                                else -> -1
                            }
                            if (formatPriority >= currentBestPriority) {
                                currentBestPriority = formatPriority
                                downloadUrl = absoluteUrl
                                downloadMimeType = type
                            }
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
        return OpdsEntry(id, title, summary, author, coverUrl, downloadUrl, downloadMimeType, navigationUrl, publisher, published, language, series, seriesIndex, categories)
    }

    private fun readAuthor(parser: XmlPullParser): String {
        var name = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "name") {
                name = readText(parser)
            } else {
                skip(parser)
            }
        }
        return name
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
        if (parser.eventType != XmlPullParser.START_TAG) throw IllegalStateException()
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