package com.aryan.reader.shared.ios

import com.aryan.reader.shared.currentTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the Standard Ebooks "all books" scenario: ~1500 entries with many
 * self-closed links each. The element matcher must try the self-closing
 * alternative first, otherwise every void tag scans the rest of the document
 * for a closing tag that never comes (quadratic).
 */
class IosOpdsFeedParseTest {
    @Test
    fun linkHeavyFeedParsesQuicklyAndCorrectly() {
        val entryCount = 500
        val feed = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<feed xmlns="http://www.w3.org/2005/Atom"><title>Catalog</title>""")
            repeat(entryCount) { index ->
                append("""<entry>""")
                append("""<id>urn:book:$index</id>""")
                append("""<title>Book $index</title>""")
                append("""<author><name>Author $index</name><uri>https://example.com/authors/$index</uri></author>""")
                append("""<summary>Summary $index with comparisons like a &lt; b</summary>""")
                append("""<category term="fiction"/>""")
                append("""<category term="adventure"/>""")
                append("""<link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/books/$index.epub"/>""")
                append("""<link rel="http://opds-spec.org/acquisition" type="application/pdf" href="/books/$index.pdf"/>""")
                append("""<link rel="http://opds-spec.org/image/thumbnail" type="image/jpeg" href="/covers/$index.jpg"/>""")
                append("""<link rel="http://opds-spec.org/image" type="image/jpeg" href="/covers/$index-full.jpg"/>""")
                append("""<link rel="related" type="text/html" href="/books/$index"/>""")
                append("""</entry>""")
            }
            append("</feed>")
        }

        val start = currentTimestamp()
        val parsed = IosOpdsParser().parse(feed, "https://example.com/opds")
        val elapsed = currentTimestamp() - start

        assertEquals(entryCount, parsed.entries.size)
        val first = parsed.entries.first()
        assertEquals("Book 0", first.title)
        assertEquals("urn:book:0", first.id)
        assertEquals(listOf("Author 0"), first.authors.map { it.name })
        assertEquals(
            "https://example.com/covers/0.jpg",
            first.coverUrl,
        )
        assertEquals(
            setOf(
                "https://example.com/books/0.epub",
                "https://example.com/books/0.pdf",
            ),
            first.acquisitions.map { it.url }.toSet(),
        )
        assertTrue(elapsed < 10_000, "link-heavy feed took ${elapsed}ms")
    }
}
