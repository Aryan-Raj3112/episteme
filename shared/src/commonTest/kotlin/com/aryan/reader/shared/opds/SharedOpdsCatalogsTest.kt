package com.aryan.reader.shared.opds

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedOpdsCatalogsTest {
    @Test
    fun `catalog json seeds defaults and preserves edits`() {
        var nextId = 0
        fun id() = "id-${nextId++}"

        val defaults = SharedOpdsCatalogs.decodeOrSeed(null, ::id)
        assertEquals(2, defaults.size)
        assertTrue(defaults.all { it.isDefault })

        val added = SharedOpdsCatalogs.addCatalog(defaults, " Custom ", " https://example.org/opds ", " user ", " pass ", ::id)
        val updated = SharedOpdsCatalogs.updateCatalog(
            catalogs = added,
            id = "id-2",
            title = " Updated ",
            url = " https://example.org/new ",
            username = " ",
            password = " token "
        )
        val custom = updated.single { !it.isDefault }
        assertEquals("Updated", custom.title)
        assertEquals("https://example.org/new", custom.url)
        assertNull(custom.username)
        assertEquals("token", custom.password)

        val encoded = SharedOpdsCatalogs.encode(updated)
        assertEquals(updated, SharedOpdsCatalogs.decode(encoded))
        assertEquals(updated, SharedOpdsCatalogs.removeCatalog(updated, defaults.first().id))
        assertTrue(SharedOpdsCatalogs.removeCatalog(updated, custom.id).all { it.isDefault })
    }

    @Test
    fun `catalog json decodes null credentials as absent credentials`() {
        val catalogs = SharedOpdsCatalogs.decode(
            """
            [
              {
                "id": "catalog",
                "title": "Catalog",
                "url": "https://example.org/opds",
                "username": null,
                "password": null
              }
            ]
            """.trimIndent()
        )

        val catalog = catalogs.single()
        assertNull(catalog.username)
        assertNull(catalog.password)
    }

    @Test
    fun `search templates expand opds uri template variants`() {
        assertEquals(
            "https://example.org/search?query=ada%20lovelace",
            SharedOpdsSearch.expandSearchTemplate("https://example.org/search{?query}", "ada lovelace")
        )
        assertEquals(
            "https://example.org/search?q=ada%20lovelace",
            SharedOpdsSearch.expandSearchTemplate("https://example.org/search?q={searchTerms}", "ada lovelace")
        )
        assertEquals(
            "https://example.org/search?q=ada%20lovelace&per-page=12&page=1",
            SharedOpdsSearch.expandSearchTemplate(
                "https://example.org/search?q={searchTerms}&per-page={count}&page={startPage}",
                "ada lovelace"
            )
        )
        assertEquals(
            "https://example.org/search?existing=1&query=ada%20lovelace",
            SharedOpdsSearch.expandSearchTemplate("https://example.org/search?existing=1", "ada lovelace")
        )
    }

    @Test
    fun `stream uri round trips encoded template and catalog`() {
        val reference = OpdsStreamReference(
            id = "book 1",
            count = 12,
            urlTemplate = "https://example.org/page/{pageNumber}?w={maxWidth}",
            catalogId = "catalog 1"
        )

        assertEquals(reference, SharedOpdsStreamUri.parse(SharedOpdsStreamUri.build(reference)))
    }

    @Test
    fun `download namer prefers content disposition and falls back to acquisition format`() {
        assertEquals(
            ".azw3",
            SharedOpdsDownloadNamer.resolveExtension(
                acquisition = OpdsAcquisition("https://example.org/download", "application/octet-stream"),
                contentDisposition = "attachment; filename*=UTF-8''Book.azw3",
                urlPathSegment = null
            )
        )
        assertEquals(
            ".pdf",
            SharedOpdsDownloadNamer.resolveExtension(
                acquisition = OpdsAcquisition("https://example.org/download", "application/pdf"),
                contentDisposition = null,
                urlPathSegment = null
            )
        )
        assertEquals(
            ".pptx",
            SharedOpdsDownloadNamer.resolveExtension(
                acquisition = OpdsAcquisition(
                    "https://example.org/download",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                ),
                contentDisposition = null,
                urlPathSegment = null
            )
        )
        assertEquals(
            ".cbt",
            SharedOpdsDownloadNamer.resolveExtension(
                acquisition = OpdsAcquisition("https://example.org/download", "application/vnd.comicbook+tar"),
                contentDisposition = null,
                urlPathSegment = null
            )
        )
    }

    @Test
    fun `local book matcher recognizes opds download temp names and acquisition filenames`() {
        val entry = OpdsEntry(
            id = "entry",
            title = "A Catalog Book",
            summary = null,
            coverUrl = null,
            acquisitions = listOf(
                OpdsAcquisition("https://example.org/files/alternate-title.epub", "application/epub+zip")
            ),
            navigationUrl = null
        )
        val books = listOf(
            BookItem(
                id = "book",
                path = "file:///library/opds_dl_A_Catalog_Book.epub",
                type = FileType.EPUB,
                displayName = "opds_dl_A_Catalog_Book.epub",
                timestamp = 1L,
                title = "Embedded Title"
            )
        )

        assertEquals(books.single(), SharedOpdsLocalBookMatcher.findBook(entry, books))

        val acquisitionNamedBook = books.single().copy(
            path = "file:///library/alternate-title.epub",
            displayName = "alternate-title.epub",
            title = "Different Embedded Title"
        )

        assertEquals(
            acquisitionNamedBook,
            SharedOpdsLocalBookMatcher.findBook(entry, listOf(acquisitionNamedBook))
        )
    }
}
