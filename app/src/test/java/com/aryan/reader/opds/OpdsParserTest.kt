package com.aryan.reader.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpdsParserTest {

    @Test
    fun `parse OPDS 2 feed resolves links facets navigation publications and metadata`() {
        val feed = OpdsParser().parse(
            bodyString = """
                {
                  "metadata": {"title": "Catalog"},
                  "links": [
                    {"rel": "next", "href": "page/2"},
                    {"rel": ["search"], "href": "search{?query}"}
                  ],
                  "facets": [
                    {
                      "metadata": {"title": "Format"},
                      "links": [
                        {"title": "EPUB", "href": "?format=epub", "properties": {"active": true}}
                      ]
                    }
                  ],
                  "navigation": [
                    {"title": "Authors", "href": "../authors", "description": "Browse authors"}
                  ],
                  "publications": [
                    {
                      "metadata": {
                        "identifier": "pub-1",
                        "title": "Example Book",
                        "description": "Long summary",
                        "author": [{"name": "Ada Writer", "links": [{"href": "/authors/ada"}]}],
                        "language": "en",
                        "publisher": "Example Press",
                        "published": "2026-01-02",
                        "subject": [{"name": "Fiction"}],
                        "belongsTo": {"series": {"name": "Series", "position": 2}}
                      },
                      "images": [
                        {"href": "images/thumb.jpg"},
                        {"rel": "cover", "href": "images/cover.jpg"}
                      ],
                      "links": [
                        {
                          "rel": "http://opds-spec.org/acquisition",
                          "href": "downloads/book.epub",
                          "type": "application/epub+zip"
                        },
                        {
                          "rel": ["http://vaemendis.net/opds-pse/stream"],
                          "href": "stream/{page}",
                          "properties": {"numberOfItems": 12}
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            baseUrl = "https://example.org/opds/catalog/index.json"
        )

        assertEquals("Catalog", feed.title)
        assertEquals("https://example.org/opds/catalog/page/2", feed.nextUrl)
        assertEquals("https://example.org/opds/catalog/search{?query}", feed.searchUrl)
        assertEquals(OpdsFacet("EPUB", "Format", "https://example.org/opds/catalog/?format=epub", true), feed.facets.single())

        val navigation = feed.entries.first { it.isNavigation }
        assertEquals("Authors", navigation.title)
        assertEquals("https://example.org/opds/authors", navigation.navigationUrl)

        val publication = feed.entries.first { it.isAcquisition }
        assertEquals("pub-1", publication.id)
        assertEquals("Example Book", publication.title)
        assertEquals("Ada Writer", publication.author)
        assertEquals("https://example.org/authors/ada", publication.authors.single().url)
        assertEquals("Long summary", publication.summary)
        assertEquals("https://example.org/opds/catalog/images/cover.jpg", publication.coverUrl)
        assertEquals("Example Press", publication.publisher)
        assertEquals("2026-01-02", publication.published)
        assertEquals("en", publication.language)
        assertEquals("Series", publication.series)
        assertEquals("2", publication.seriesIndex)
        assertEquals(listOf("Fiction"), publication.categories)
        assertEquals("https://example.org/opds/catalog/downloads/book.epub", publication.bestAcquisition?.url)
        assertEquals("EPUB", publication.bestAcquisition?.formatName)
        assertEquals(12, publication.pseCount)
        assertEquals("https://example.org/opds/catalog/stream/{page}", publication.pseUrlTemplate)
        assertTrue(publication.isStreamable)
    }

    @Test
    fun `parse OPDS 1 feed extracts catalog links entry metadata acquisitions and stream info`() {
        val feed = OpdsParser().parse(
            bodyString = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom"
                      xmlns:opds="http://opds-spec.org/2010/catalog"
                      xmlns:pse="http://vaemendis.net/opds-pse/ns">
                  <title>XML Catalog</title>
                  <link rel="next" href="next.xml" />
                  <link rel="search" href="/search.xml" />
                  <link rel="facet" title="English" href="?lang=en" opds:facetGroup="Language" opds:activeFacet="true" />
                  <entry>
                    <id>xml-1</id>
                    <title>XML Book</title>
                    <summary>Summary text</summary>
                    <author>
                      <name>XML Author</name>
                      <uri>/people/xml-author</uri>
                    </author>
                    <publisher>XML Press</publisher>
                    <language>en</language>
                    <published>2025-12-31</published>
                    <category term="fiction" label="Fiction" />
                    <meta property="calibre:series">XML Series</meta>
                    <meta property="calibre:series_index">3</meta>
                    <link rel="http://opds-spec.org/image/thumbnail" href="thumb.jpg" />
                    <link rel="http://opds-spec.org/image" href="cover.jpg" />
                    <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="book.pdf" />
                    <link rel="http://vaemendis.net/opds-pse/stream" href="stream/{page}" pse:count="8" />
                  </entry>
                </feed>
            """.trimIndent(),
            baseUrl = "https://example.org/root/feed.xml"
        )

        assertEquals("XML Catalog", feed.title)
        assertEquals("https://example.org/root/next.xml", feed.nextUrl)
        assertEquals("https://example.org/search.xml", feed.searchUrl)
        assertEquals(OpdsFacet("English", "Language", "https://example.org/root/?lang=en", true), feed.facets.single())

        val entry = feed.entries.single()
        assertEquals("xml-1", entry.id)
        assertEquals("XML Book", entry.title)
        assertEquals("Summary text", entry.summary)
        assertEquals(OpdsAuthor("XML Author", "https://example.org/people/xml-author"), entry.authors.single())
        assertEquals("https://example.org/root/thumb.jpg", entry.coverUrl)
        assertEquals("XML Press", entry.publisher)
        assertEquals("2025-12-31", entry.published)
        assertEquals("en", entry.language)
        assertEquals("XML Series", entry.series)
        assertEquals("3", entry.seriesIndex)
        assertEquals(listOf("Fiction"), entry.categories)
        assertEquals(OpdsAcquisition("https://example.org/root/book.pdf", "application/pdf"), entry.acquisitions.single())
        assertEquals(8, entry.pseCount)
        assertEquals("https://example.org/root/stream/{page}", entry.pseUrlTemplate)
    }

    @Test
    fun `parse OPDS 2 groups and fallback metadata produce navigation entries`() {
        val feed = OpdsParser().parse(
            bodyString = """
                {
                  "groups": [
                    {
                      "metadata": {"title": "Group Title"},
                      "links": [{"href": "group-feed"}],
                      "navigation": [{"title": "Nested Nav", "href": "nested"}],
                      "publications": [{"links": [], "metadata": {"title": "No Identifier"}}]
                    }
                  ]
                }
            """.trimIndent(),
            baseUrl = "https://example.org/catalog/"
        )

        assertEquals("OPDS 2.0 Feed", feed.title)
        assertEquals("Nested Nav", feed.entries[0].title)
        assertEquals("https://example.org/catalog/nested", feed.entries[0].navigationUrl)
        assertEquals("Group Title", feed.entries[2].title)
        assertEquals("https://example.org/catalog/group-feed", feed.entries[2].navigationUrl)
        assertEquals("No Identifier", feed.entries[1].title)
        assertFalse(feed.entries[1].isAcquisition)
        assertNull(feed.entries[1].bestAcquisition)
    }

    @Test
    fun `acquisition format names and priority prefer richer reader formats`() {
        val acquisitions = listOf(
            OpdsAcquisition("txt", "text/plain"),
            OpdsAcquisition("pdf", "application/pdf"),
            OpdsAcquisition("epub", "application/epub+zip"),
            OpdsAcquisition("unknown", "application/octet-stream")
        )
        val entry = OpdsEntry(
            id = "id",
            title = "Book",
            summary = null,
            coverUrl = null,
            acquisitions = acquisitions,
            navigationUrl = null
        )

        assertEquals("EPUB", acquisitions[2].formatName)
        assertEquals("TXT", acquisitions[0].formatName)
        assertEquals("OCTET-STREAM", acquisitions[3].formatName)
        assertEquals(acquisitions[2], entry.bestAcquisition)
    }
}
