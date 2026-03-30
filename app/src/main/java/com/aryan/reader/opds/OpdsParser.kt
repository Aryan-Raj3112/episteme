package com.aryan.reader.opds

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.InputStream
import java.net.URI

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

        parser.require(XmlPullParser.START_TAG, null, "feed")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            Timber.tag("OpdsDebug").v("Feed tag found: <${parser.name}>")
            when (parser.name) {
                "title" -> {
                    title = readText(parser)
                    Timber.tag("OpdsDebug").d("Feed Title: $title")
                }
                "entry" -> {
                    val entry = readEntry(parser, baseUrl)
                    entries.add(entry)
                    Timber.tag("OpdsDebug").v("Entry parsed: ${entry.title}")
                }
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel")
                    val href = parser.getAttributeValue(null, "href")
                    val type = parser.getAttributeValue(null, "type")
                    if (rel == "next") {
                        nextUrl = resolveUrl(baseUrl, href ?: "")
                        Timber.tag("OpdsDebug").d("Next page found: $nextUrl")
                    } else if (rel == "search" && type?.contains("application/atom+xml") == true) {
                        searchUrl = resolveUrl(baseUrl, href ?: "")
                        Timber.tag("OpdsDebug").d("Search URL found: $searchUrl")
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return OpdsFeed(title, entries, nextUrl, searchUrl)
    }

    private fun readEntry(parser: XmlPullParser, baseUrl: String): OpdsEntry {
        parser.require(XmlPullParser.START_TAG, null, "entry")
        var id = ""
        var title = ""
        var summary: String? = null
        var author: String? = null
        var coverUrl: String? = null
        var downloadUrl: String? = null
        var downloadMimeType: String? = null
        var navigationUrl: String? = null
        var currentBestPriority = -1

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            Timber.tag("OpdsDebug").v("Entry child tag found: <${parser.name}>")

            when (parser.name) {
                "id" -> id = readText(parser)
                "title" -> title = readText(parser)
                "summary", "content" -> summary = readText(parser)
                "author" -> author = readAuthor(parser)
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel") ?: ""
                    val href = parser.getAttributeValue(null, "href") ?: ""
                    val type = parser.getAttributeValue(null, "type") ?: ""

                    if (href.isNotEmpty()) {
                        val absoluteUrl = resolveUrl(baseUrl, href)

                        Timber.tag("OpdsDebug").v("Checking Link: rel=$rel | type=$type | title=$title")

                        if (rel.contains("http://opds-spec.org/image")) {
                            if (coverUrl == null || rel.contains("thumbnail")) {
                                coverUrl = absoluteUrl
                            }
                        } else if (rel.contains("http://opds-spec.org/acquisition")) {
                            val formatPriority = when {
                                type.contains("epub") -> 5
                                type.contains("pdf") -> 4
                                type.contains("mobi") || type.contains("x-mobipocket-ebook") -> 3
                                type.contains("fictionbook") || type.contains("fb2") -> 2
                                type.contains("txt") || type.contains("text/plain") -> 1
                                else -> 0
                            }

                            Timber.tag("OpdsDebug").d("Acquisition candidate: $absoluteUrl | priority=$formatPriority")

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
        if (title.isEmpty()) {
            Timber.tag("OpdsDebug").w("Warning: Parsed an entry with an empty title. Check XML tag names.")
        }
        return OpdsEntry(id, title, summary, author, coverUrl, downloadUrl, downloadMimeType, navigationUrl)
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
            URI.create(baseUrl).resolve(href).toString()
        } catch (_: Exception) {
            href
        }
    }
}