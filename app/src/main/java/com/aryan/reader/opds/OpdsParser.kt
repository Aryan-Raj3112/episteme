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
                    if (rel == "next") {
                        nextUrl = resolveUrl(baseUrl, href ?: "")
                        Timber.tag("OpdsDebug").d("Next page found: $nextUrl")
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }
        return OpdsFeed(title, entries, nextUrl)
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

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "id" -> id = readText(parser)
                "title" -> title = readText(parser)
                "summary", "content" -> summary = readText(parser)
                "author" -> author = readAuthor(parser)
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel")
                    val href = parser.getAttributeValue(null, "href")
                    val type = parser.getAttributeValue(null, "type")

                    Timber.tag("OpdsDebug").v("Link found - rel: $rel, type: $type, href: $href")

                    if (href != null) {
                        val absoluteUrl = resolveUrl(baseUrl, href)

                        if (rel?.contains("http://opds-spec.org/image") == true) {
                            if (coverUrl == null || rel.contains("thumbnail")) {
                                coverUrl = absoluteUrl
                            }
                        } else if (rel?.contains("http://opds-spec.org/acquisition") == true) {
                            if (downloadUrl == null || type?.contains("epub") == true || type?.contains("pdf") == true) {
                                downloadUrl = absoluteUrl
                                downloadMimeType = type
                            }
                        } else if (type?.contains("profile=opds-catalog") == true || type?.contains("application/atom+xml") == true) {
                            if (navigationUrl == null) {
                                navigationUrl = absoluteUrl
                            }
                        } else if (rel == "subsection" || rel == "collection" || rel == "start") {
                            if (navigationUrl == null) {
                                navigationUrl = absoluteUrl
                            }
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
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result.trim()
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