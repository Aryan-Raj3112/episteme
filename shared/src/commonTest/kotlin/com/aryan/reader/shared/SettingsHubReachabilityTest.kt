package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards that every settings destination the hub can navigate to is actually reachable.
 *
 * The audit found `HELP_ABOUT` fully built — a page, a parent, a title, and items — with no
 * category linking to it and no mention in search, so "Help & About" simply did not exist for the
 * user. Nothing failed: the page built fine, every platform compiled, and every test passed. The
 * bug was only visible by asking "can a user get there from anywhere?", which is what these
 * tests do.
 */
class SettingsHubReachabilityTest {

    private val model = sharedSettingsHubModel(
        SharedSettingsHubInput(
            platform = SharedSettingsPlatform.ANDROID,
            featurePolicy = SharedFeaturePolicy.Standard,
            aiSettingsAvailable = true,
            isSignedIn = true,
            isProUser = true,
            syncAvailable = true,
            folderSyncAvailable = true,
        ),
    )

    @Test
    fun `every destination is reachable by navigating from the root`() {
        val reachable = mutableSetOf<SharedSettingsDestination>()
        val queue = ArrayDeque<SharedSettingsDestination>()

        fun visit(destination: SharedSettingsDestination) {
            if (!reachable.add(destination)) return
            val page = model.page(destination)
            page.categories.forEach { queue.add(it.destination) }
            page.items.filter { it.kind == SharedSettingsItemKind.NAVIGATION }
                .forEach { it.destination?.let(queue::add) }
        }

        visit(SharedSettingsDestination.ROOT)
        while (queue.isNotEmpty()) visit(queue.removeFirst())

        // Detail pages are entered from their parent category's rows, so a destination with no
        // incoming navigation row is the bug this file exists for.
        val unreachable = SharedSettingsDestination.entries.filter { it !in reachable }
        assertEquals(
            emptyList(),
            unreachable,
            "unreachable destinations:\n" + unreachable.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `root categories include help and about`() {
        assertTrue(
            SharedSettingsDestination.HELP_ABOUT in model.rootCategories.map { it.destination },
        )
    }

    @Test
    fun `help and about is findable through search`() {
        val results = model.searchResults("help")
        assertTrue(
            results.any { it.destination == SharedSettingsDestination.HELP_ABOUT },
            "searching \"help\" must surface the Help & About category",
        )
    }

    @Test
    fun `help and about page lists its rows`() {
        val actions = model.page(SharedSettingsDestination.HELP_ABOUT).items.map { it.action }
        assertTrue(SharedSettingsAction.ABOUT in actions, "About row is missing")
        assertTrue(SharedSettingsAction.HELP_FEEDBACK in actions, "Help and feedback row is missing")
    }

    @Test
    fun `about and feedback are not duplicated into extra`() {
        // Help & About is now its own root category, so leaving these rows in Extra would show
        // them twice under two different breadcrumbs.
        val extraActions = model.page(SharedSettingsDestination.EXTRA).items.map { it.action }
        assertTrue(SharedSettingsAction.ABOUT !in extraActions)
        assertTrue(SharedSettingsAction.SUPPORT !in extraActions)
        assertTrue(SharedSettingsAction.HELP_FEEDBACK !in extraActions)
    }

    @Test
    fun `every root category with items is non-degenerate`() {
        for (category in model.rootCategories) {
            assertTrue(
                category.itemCount > 0,
                "${category.destination} is listed with itemCount=${category.itemCount}",
            )
            assertEquals(
                model.page(category.destination).items.size,
                category.itemCount,
                "${category.destination} advertises a count it does not have",
            )
        }
    }

    @Test
    fun `every category summary is distinct from its siblings`() {
        // Two categories with identical summaries read as a duplicate to the user and usually
        // mean one of them was meant to say something else.
        val bySummary = model.rootCategories.groupBy { it.summary }
        val collisions = bySummary.filterValues { it.size > 1 }
        assertEquals(
            emptyList(),
            collisions.toList(),
            "duplicate summaries:\n" + collisions.entries.joinToString("\n") { (summary, cats) ->
                "  \"$summary\" used by ${cats.map { it.destination }}"
            },
        )
    }

    @Test
    fun `no category claims to contain app info after about moved out of extra`() {
        // "app info" moved to Help & About; leaving it on Extra is the kind of drift that makes a
        // user hunt in the wrong place.
        val extra = model.rootCategories.first { it.destination == SharedSettingsDestination.EXTRA }
        assertTrue("app info" !in extra.summary, "Extra still advertises app info")
    }

    @Test
    fun `reader toolbar defaults is a destination the epub category links to`() {
        val rows = model.page(SharedSettingsDestination.EPUB_TEXT).items
        assertTrue(
            rows.any {
                it.destination == SharedSettingsDestination.READER_TOOLBAR_DEFAULTS &&
                    it.kind == SharedSettingsItemKind.NAVIGATION
            },
            "the Reader Toolbar Defaults row must navigate to its own page",
        )
    }

    @Test
    fun `every navigation row resolves to a real page`() {
        for (category in model.rootCategories) {
            for (item in model.page(category.destination).items) {
                val destination = item.destination ?: continue
                val kind = model.page(destination).kind
                assertTrue(
                    kind == SharedSettingsPageKind.CATEGORY || kind == SharedSettingsPageKind.DETAIL,
                    "${item.action} points at $destination, which is neither a category nor a detail page",
                )
                // A detail page whose parent is not reachable is invisible in practice, which the
                // traversal above already covers; here we only pin the parent relationship.
                assertEquals(
                    destination.parentDestination(),
                    category.destination,
                    "${item.action} is filed under a parent it does not declare",
                )
            }
        }
    }
}
