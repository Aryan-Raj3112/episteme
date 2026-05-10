package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsHubModelsTest {

    @Test
    fun `settings hub root shows parent categories only`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        )

        assertEquals(
            listOf(
                SharedSettingsDestination.EPUB_TEXT,
                SharedSettingsDestination.PDF_COMICS,
                SharedSettingsDestination.THEME_APPEARANCE,
                SharedSettingsDestination.TTS_AI,
                SharedSettingsDestination.LIBRARY_SYNC_STORAGE,
                SharedSettingsDestination.HELP_ABOUT
            ),
            model.rootCategories.map { it.destination }
        )
        assertTrue(model.page(SharedSettingsDestination.ROOT).items.isEmpty())
    }

    @Test
    fun `offline feature policy hides network backed nested settings`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.ANDROID,
                featurePolicy = SharedFeaturePolicy.OssOffline,
                aiSettingsAvailable = true,
                isSignedIn = false
            )
        )
        val actions = model.visibleNestedActions()

        assertFalse(SharedSettingsAction.AI_SETTINGS in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertTrue(SharedSettingsAction.TTS_SETTINGS in actions)
        assertTrue(SharedSettingsAction.ABOUT in actions)
    }

    @Test
    fun `local override note appears on reader detail pages only`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        )

        assertFalse(
            model.page(SharedSettingsDestination.EPUB_TEXT)
                .items
                .any { it.action == SharedSettingsAction.LOCAL_OVERRIDE_NOTE }
        )
        val note = model.page(SharedSettingsDestination.EPUB_FORMAT).localOverrideNote

        assertEquals(SharedSettingsItemKind.INFO, note?.kind)
        assertTrue(note?.summary.orEmpty().contains("Local overrides"))
        assertTrue(note?.summary.orEmpty().contains("reader"))
    }

    @Test
    fun `search returns nested results with breadcrumbs`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        )

        val results = model.searchResults("custom fonts")

        assertEquals(1, results.size)
        assertEquals(SharedSettingsAction.CUSTOM_FONTS, results.first().action)
        assertEquals("Settings / Theme & Appearance", results.first().breadcrumb)
    }

    @Test
    fun `settings destinations expose stable parents`() {
        assertEquals(SharedSettingsDestination.ROOT, SharedSettingsDestination.EPUB_TEXT.parentDestination())
        assertEquals(SharedSettingsDestination.EPUB_TEXT, SharedSettingsDestination.EPUB_FORMAT.parentDestination())
        assertEquals(SharedSettingsDestination.PDF_COMICS, SharedSettingsDestination.PDF_READER_TOOLS.parentDestination())
        assertEquals(SharedSettingsDestination.TTS_AI, SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS.parentDestination())
    }
}

private fun SharedSettingsHubModel.visibleNestedActions(): List<SharedSettingsAction> {
    return rootCategories.flatMap { category ->
        page(category.destination).items.map { it.action }
    }
}
