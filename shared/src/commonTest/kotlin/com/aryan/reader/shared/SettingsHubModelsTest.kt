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
    fun `sync unavailable hides cloud sync while preserving account and folder sync`() {
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
        assertTrue(SharedSettingsAction.SIGN_OUT in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
    }

    @Test
    fun `account unavailable hides sign-in rows independently from cloud sync`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.DESKTOP,
                accountAvailable = false,
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
    fun `ios standard settings omit auth and cloud while retaining local folders`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                accountAvailable = false,
                includeAccountAuthActions = false,
                syncAvailable = false,
                folderSyncAvailable = true,
                aiSettingsAvailable = false,
                ttsSettingsAvailable = false,
                bookCacheMaintenanceAvailable = false,
                reflowCacheMaintenanceAvailable = false,
            )
        )
        val actions = model.visibleNestedActions()

        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertFalse(SharedSettingsAction.SIGN_OUT in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.AI_SETTINGS in actions)
        assertFalse(SharedSettingsAction.TTS_SETTINGS in actions)
        assertFalse(SharedSettingsAction.CLEAR_BOOK_CACHE in actions)
        assertFalse(SharedSettingsAction.CLEAR_REFLOW_CACHE in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
        assertTrue(SharedSettingsAction.CUSTOM_FONTS in actions)
        assertTrue(SharedSettingsAction.APP_THEME in actions)
    }

    @Test
    fun `ios can expose native account actions without claiming cloud sync support`() {
        val signedOut = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                accountAvailable = true,
                includeAccountAuthActions = true,
                syncAvailable = false,
                isSignedIn = false,
            )
        ).visibleNestedActions()
        val signedIn = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                accountAvailable = true,
                includeAccountAuthActions = true,
                syncAvailable = false,
                isSignedIn = true,
            )
        ).visibleNestedActions()

        assertTrue(SharedSettingsAction.SIGN_IN in signedOut)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in signedOut)
        assertTrue(SharedSettingsAction.SIGN_OUT in signedIn)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in signedIn)
    }

    @Test
    fun `ios native tts settings describe device controls`() {
        val item = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                ttsSettingsAvailable = true,
                aiSettingsAvailable = false,
            )
        ).page(SharedSettingsDestination.TTS_AI)
            .items
            .single { it.action == SharedSettingsAction.TTS_SETTINGS }

        assertEquals("Choose an iOS voice, speech rate, and pitch", item.summary)
    }

    @Test
    fun `cloud sync row remains actionable while account setup is required`() {
        val item = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                isSignedIn = true,
                isProUser = true,
                syncAvailable = true,
                cloudSyncSetupIntent = CloudSyncSetupIntent.NEEDS_DRIVE_AUTH,
            )
        ).page(SharedSettingsDestination.SYNC_ACCOUNTS)
            .items
            .single { it.action == SharedSettingsAction.CLOUD_SYNC }

        assertTrue(item.enabled)
        assertTrue(item.summary.contains("Google"))
    }

    @Test
    fun `eligible ios cloud sync row reflects the persisted toggle state`() {
        val item = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                isSignedIn = true,
                isProUser = true,
                syncAvailable = true,
                cloudSyncSetupIntent = CloudSyncSetupIntent.READY,
                isSyncEnabled = true,
            )
        ).page(SharedSettingsDestination.SYNC_ACCOUNTS)
            .items
            .single { it.action == SharedSettingsAction.CLOUD_SYNC }

        assertTrue(item.enabled)
        assertEquals(true, item.checked)
        assertTrue(item.summary.contains("Sync library metadata"))
    }

    @Test
    fun `desktop can hide account auth rows while preserving sync controls`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.DESKTOP,
                includeAccountAuthActions = false,
                accountAvailable = true,
                syncAvailable = true,
                folderSyncAvailable = true,
                isSignedIn = true,
                isProUser = true
            )
        )
        val actions = model.visibleNestedActions()

        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertFalse(SharedSettingsAction.SIGN_OUT in actions)
        assertTrue(SharedSettingsAction.CLOUD_SYNC in actions)
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
    fun `language setting can show current platform selection`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.DESKTOP,
                includeLanguage = true,
                languageTitle = "App language",
                languageSummary = "Deutsch"
            )
        )

        val item = model.page(SharedSettingsDestination.EXTRA)
            .items
            .single { it.action == SharedSettingsAction.LANGUAGE }

        assertEquals("App language", item.title)
        assertEquals("Deutsch", item.summary)
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
