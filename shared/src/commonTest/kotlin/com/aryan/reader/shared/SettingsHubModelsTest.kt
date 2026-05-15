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
                SharedSettingsDestination.SYNC_ACCOUNTS,
                SharedSettingsDestination.EXTRA
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
    fun `sync unavailable hides account rows while preserving folder sync`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.DESKTOP,
                syncAvailable = false,
                folderSyncAvailable = true,
                isSignedIn = true,
                isProUser = true
            )
        )
        val actions = model.visibleNestedActions()

        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertFalse(SharedSettingsAction.SIGN_OUT in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
    }

    @Test
    fun `reader tabs setting can be omitted for platforms without visible tabs`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.DESKTOP,
                includeReaderTabs = false
            )
        )

        assertFalse(SharedSettingsAction.TABS_TOGGLE in model.visibleNestedActions())
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
        assertEquals("Settings / Library & Files", results.first().breadcrumb)
    }

    @Test
    fun `settings destinations expose stable parents`() {
        assertEquals(SharedSettingsDestination.ROOT, SharedSettingsDestination.EPUB_TEXT.parentDestination())
        assertEquals(SharedSettingsDestination.EPUB_TEXT, SharedSettingsDestination.EPUB_FORMAT.parentDestination())
        assertEquals(SharedSettingsDestination.PDF_COMICS, SharedSettingsDestination.PDF_READER_TOOLS.parentDestination())
        assertEquals(SharedSettingsDestination.TTS_AI, SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS.parentDestination())
        assertEquals(SharedSettingsDestination.ROOT, SharedSettingsDestination.EXTRA.parentDestination())
    }

    @Test
    fun `tts replacements are only exposed from global tts area`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        )

        assertFalse(
            model.page(SharedSettingsDestination.EPUB_TEXT)
                .items
                .any { it.action == SharedSettingsAction.TTS_REPLACEMENTS }
        )
        assertEquals(
            SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS,
            model.page(SharedSettingsDestination.TTS_AI)
                .items
                .single { it.action == SharedSettingsAction.TTS_REPLACEMENTS }
                .destination
        )
    }
}

private fun SharedSettingsHubModel.visibleNestedActions(): List<SharedSettingsAction> {
    return rootCategories.flatMap { category ->
        page(category.destination).items.map { it.action }
    }
}
