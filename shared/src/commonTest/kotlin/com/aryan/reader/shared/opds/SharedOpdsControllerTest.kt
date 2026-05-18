package com.aryan.reader.shared.opds

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedOpdsControllerTest {
    @Test
    fun `controller opens paginates and navigates shared feed state`() = runBlocking {
        val catalog = OpdsCatalog(id = "catalog", title = "Catalog", url = "root")
        val repository = FakeOpdsRepository(
            catalogs = listOf(catalog),
            feeds = mapOf(
                "root" to feed("Root", entry("one"), nextUrl = "next"),
                "next" to feed("Next", entry("two")),
                "child" to feed("Child", entry("child"))
            )
        )
        val controller = SharedOpdsController(
            repository = repository,
            idFactory = { "generated" }
        )
        val emissions = mutableListOf<SharedOpdsScreenState>()

        controller.openCatalog(catalog, emissions::add)
        assertEquals("Root", controller.state.currentFeed?.title)
        assertEquals(listOf("one"), controller.state.currentFeed?.entries?.map { it.id })
        assertFalse(controller.hasFeedHistory())

        controller.loadNextPage(emissions::add)
        assertEquals(listOf("one", "two"), controller.state.currentFeed?.entries?.map { it.id })

        controller.openFeedUrl("child", emissions::add)
        assertEquals("Child", controller.state.currentFeed?.title)
        assertTrue(controller.hasFeedHistory())

        assertTrue(controller.navigateBack(emissions::add))
        assertEquals("Root", controller.state.currentFeed?.title)
        assertFalse(controller.hasFeedHistory())

        assertFalse(controller.navigateBack(emissions::add))
        assertFalse(controller.state.isViewingCatalog)
        assertNull(controller.state.currentFeed)
        assertEquals(listOf("root", "next", "child", "root"), repository.requestedUrls)
    }

    @Test
    fun `controller search fetches expanded query through repository`() = runBlocking {
        val catalog = OpdsCatalog(id = "catalog", title = "Catalog", url = "root")
        val repository = FakeOpdsRepository(
            catalogs = listOf(catalog),
            feeds = mapOf(
                "root" to feed("Root", entry("one"), searchUrl = "https://example.org/search{?query}"),
                "https://example.org/search?query=ada%20lovelace" to feed("Search", entry("result"))
            )
        )
        val controller = SharedOpdsController(
            repository = repository,
            idFactory = { "generated" }
        )
        val emissions = mutableListOf<SharedOpdsScreenState>()

        controller.openCatalog(catalog, emissions::add)
        controller.search("ada lovelace", emissions::add)

        assertEquals("Search", controller.state.currentFeed?.title)
        assertEquals(listOf("root", "https://example.org/search?query=ada%20lovelace"), repository.requestedUrls)
    }

    private fun feed(
        title: String,
        vararg entries: OpdsEntry,
        nextUrl: String? = null,
        searchUrl: String? = null
    ): OpdsFeed {
        return OpdsFeed(
            title = title,
            entries = entries.toList(),
            nextUrl = nextUrl,
            searchUrl = searchUrl
        )
    }

    private fun entry(id: String): OpdsEntry {
        return OpdsEntry(
            id = id,
            title = id,
            summary = null,
            coverUrl = null,
            navigationUrl = null
        )
    }

    private class FakeOpdsRepository(
        catalogs: List<OpdsCatalog>,
        private val feeds: Map<String, OpdsFeed>
    ) : SharedOpdsRepository {
        private var storedCatalogs = catalogs
        val requestedUrls = mutableListOf<String>()

        override fun loadCatalogs(): List<OpdsCatalog> = storedCatalogs

        override fun saveCatalogs(catalogs: List<OpdsCatalog>) {
            storedCatalogs = catalogs
        }

        override suspend fun fetchFeed(url: String, username: String?, password: String?): Result<OpdsFeed> {
            requestedUrls += url
            return feeds[url]?.let { Result.success(it) }
                ?: Result.failure(IllegalArgumentException("Missing feed: $url"))
        }

        override suspend fun getSearchTemplate(
            openSearchUrl: String,
            username: String?,
            password: String?
        ): String? = null
    }
}
