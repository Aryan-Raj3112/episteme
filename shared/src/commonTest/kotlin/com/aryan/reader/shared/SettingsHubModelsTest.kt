package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsHubModelsTest {

    private fun model(
        platform: SharedSettingsPlatform = SharedSettingsPlatform.DESKTOP,
        block: SharedSettingsHubInput.() -> SharedSettingsHubInput = { this }
    ): SharedSettingsHubModel {
        return sharedSettingsHubModel(SharedSettingsHubInput(platform = platform).block())
    }

    @Test
    fun `root lists the seven settings categories in user-facing order`() {
        val root = model().page(SharedSettingsDestination.ROOT)

        assertEquals(SharedSettingsPageKind.ROOT, root.kind)
        assertTrue(root.items.isEmpty())
        assertEquals(
            listOf(
                SharedSettingsDestination.EPUB_TEXT,
                SharedSettingsDestination.PDF_COMICS,
                SharedSettingsDestination.APP_LIBRARY,
                SharedSettingsDestination.TTS_AI,
                SharedSettingsDestination.SYNC_ACCOUNT,
                SharedSettingsDestination.STORAGE_PRIVACY,
                SharedSettingsDestination.HELP_ABOUT
            ),
            root.categories.map { it.destination }
        )
    }

    @Test
    fun `every category row leads to a non-empty page`() {
        val model = model()

        for (category in model.rootCategories) {
            val page = model.page(category.destination)
            assertEquals(SharedSettingsPageKind.CATEGORY, page.kind)
            assertTrue(
                page.items.isNotEmpty(),
                "${category.destination} is listed but renders no rows"
            )
        }
    }

    @Test
    fun `category summaries are distinct`() {
        val bySummary = model().rootCategories.groupBy { it.summary.fallback }
        assertEquals(emptyList(), bySummary.filterValues { it.size > 1 }.toList())
    }

    @Test
    fun `offline feature policy hides network backed settings`() {
        val actions = model {
            copy(featurePolicy = SharedFeaturePolicy.OssOffline, aiSettingsAvailable = true, isSignedIn = false)
        }.visibleNestedActions()

        assertFalse(SharedSettingsAction.AI_SETTINGS in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertTrue(SharedSettingsAction.TTS_SETTINGS in actions)
        assertTrue(SharedSettingsAction.ABOUT in actions)
    }

    @Test
    fun `sync unavailable hides cloud sync while preserving account and folder sync`() {
        val actions = model {
            copy(syncAvailable = false, folderSyncAvailable = true, isSignedIn = true, isProUser = true)
        }.visibleNestedActions()

        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertTrue(SharedSettingsAction.SIGN_OUT in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
    }

    @Test
    fun `account unavailable hides sign-in rows independently from cloud sync`() {
        val actions = model {
            copy(
                accountAvailable = false,
                syncAvailable = false,
                folderSyncAvailable = true,
                isSignedIn = true,
                isProUser = true
            )
        }.visibleNestedActions()

        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertFalse(SharedSettingsAction.SIGN_OUT in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
    }

    @Test
    fun `book cache maintenance exposes clear action when available`() {
        val actions = model(SharedSettingsPlatform.IOS) {
            copy(bookCacheMaintenanceAvailable = true)
        }.visibleNestedActions()

        assertTrue(SharedSettingsAction.CLEAR_BOOK_CACHE in actions)
    }

    @Test
    fun `ios standard settings omit auth and cloud while retaining local folders`() {
        val actions = model(SharedSettingsPlatform.IOS) {
            copy(
                accountAvailable = false,
                includeAccountAuthActions = false,
                syncAvailable = false,
                folderSyncAvailable = true,
                aiSettingsAvailable = false,
                ttsSettingsAvailable = false,
                bookCacheMaintenanceAvailable = false,
                reflowCacheMaintenanceAvailable = false
            )
        }.visibleNestedActions()

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
        val signedOut = model(SharedSettingsPlatform.IOS) {
            copy(accountAvailable = true, includeAccountAuthActions = true, syncAvailable = false, isSignedIn = false)
        }.visibleNestedActions()
        val signedIn = model(SharedSettingsPlatform.IOS) {
            copy(accountAvailable = true, includeAccountAuthActions = true, syncAvailable = false, isSignedIn = true)
        }.visibleNestedActions()

        assertTrue(SharedSettingsAction.SIGN_IN in signedOut)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in signedOut)
        assertTrue(SharedSettingsAction.SIGN_OUT in signedIn)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in signedIn)
    }

    @Test
    fun `ios keeps the account category while cloud sync stays hidden`() {
        val model = model(SharedSettingsPlatform.IOS) {
            copy(
                isSignedIn = true,
                accountAvailable = true,
                includeAccountAuthActions = true,
                includeAccountDeletion = true,
                syncAvailable = false,
                folderSyncAvailable = false
            )
        }

        assertTrue(SharedSettingsDestination.SYNC_ACCOUNT in model.rootCategories.map { it.destination })
        assertTrue(SharedSettingsAction.SIGN_OUT in model.visibleNestedActions())
        assertTrue(SharedSettingsAction.DELETE_ACCOUNT in model.visibleNestedActions())
    }

    @Test
    fun `tts copy is platform specific`() {
        val iosItem = model(SharedSettingsPlatform.IOS) {
            copy(ttsSettingsAvailable = true, aiSettingsAvailable = false)
        }.page(SharedSettingsDestination.TTS_AI)
            .items
            .single { it.action == SharedSettingsAction.TTS_SETTINGS }

        assertEquals("Choose an iOS voice, speech rate, and pitch", iosItem.summary.fallback)
    }

    @Test
    fun `cloud sync row remains actionable while account setup is required`() {
        val item = model(SharedSettingsPlatform.IOS) {
            copy(
                isSignedIn = true,
                isProUser = true,
                syncAvailable = true,
                cloudSyncSetupIntent = CloudSyncSetupIntent.NEEDS_DRIVE_AUTH
            )
        }.page(SharedSettingsDestination.SYNC_ACCOUNT)
            .items
            .single { it.action == SharedSettingsAction.CLOUD_SYNC }

        assertTrue(item.enabled)
        assertTrue(item.summary.fallback.contains("Google"))
    }

    @Test
    fun `cloud sync stays enabled for free users so the upgrade path is reachable`() {
        // A disabled switch with no action is a dead end: the row must always reach the host,
        // which either toggles sync or opens the upgrade flow.
        val item = model(SharedSettingsPlatform.ANDROID) {
            copy(isSignedIn = true, isProUser = false, cloudSyncSetupIntent = CloudSyncSetupIntent.NEEDS_PRO)
        }.page(SharedSettingsDestination.SYNC_ACCOUNT)
            .items
            .single { it.action == SharedSettingsAction.CLOUD_SYNC }

        assertTrue(item.enabled)
        assertTrue(item.summary.fallback.contains("Pro"))
    }

    @Test
    fun `eligible cloud sync row reflects the persisted toggle state`() {
        val item = model(SharedSettingsPlatform.IOS) {
            copy(
                isSignedIn = true,
                isProUser = true,
                syncAvailable = true,
                cloudSyncSetupIntent = CloudSyncSetupIntent.READY,
                isSyncEnabled = true
            )
        }.page(SharedSettingsDestination.SYNC_ACCOUNT)
            .items
            .single { it.action == SharedSettingsAction.CLOUD_SYNC }

        assertTrue(item.enabled)
        assertEquals(true, item.checked)
        assertTrue(item.summary.fallback.contains("Sync library metadata"))
    }

    @Test
    fun `desktop can hide account auth rows while preserving sync controls`() {
        val actions = model {
            copy(
                includeAccountAuthActions = false,
                accountAvailable = true,
                syncAvailable = true,
                folderSyncAvailable = true,
                isSignedIn = true,
                isProUser = true
            )
        }.visibleNestedActions()

        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertFalse(SharedSettingsAction.SIGN_OUT in actions)
        assertTrue(SharedSettingsAction.CLOUD_SYNC in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
    }

    @Test
    fun `reader tabs setting can be omitted for platforms without visible tabs`() {
        val actions = model { copy(includeReaderTabs = false) }.visibleNestedActions()

        assertFalse(SharedSettingsAction.TABS_TOGGLE in actions)
    }

    @Test
    fun `language row keeps stable copy and shows the current choice as its value`() {
        val item = model {
            copy(
                includeLanguage = true,
                itemValues = mapOf(SharedSettingsAction.LANGUAGE.name to "Deutsch")
            )
        }.page(SharedSettingsDestination.APP_LIBRARY)
            .items
            .single { it.action == SharedSettingsAction.LANGUAGE }

        // Copy comes from a stable key, not from the host's current selection: the selection is a
        // value, so translating the row never depends on which language is active.
        assertEquals("Language", item.title.fallback)
        assertEquals("Deutsch", item.value)
        assertEquals(SharedSettingsItemKind.CONTROL, item.kind)
    }

    @Test
    fun `host value previews are applied by row id`() {
        val page = model {
            copy(
                itemValues = mapOf(
                    SharedSettingsDestination.EPUB_FORMAT.name to "Literata · 100%"
                )
            )
        }.page(SharedSettingsDestination.EPUB_TEXT)

        assertEquals("Literata · 100%", page.itemById(SharedSettingsDestination.EPUB_FORMAT.name)?.value)
    }

    @Test
    fun `reader-only overrides are stated on the category page and on detail pages`() {
        val model = model()

        // The category page tells the user up front that per-book changes made inside the reader
        // win, instead of leaving them to discover it after tapping through.
        val categoryNote = model.page(SharedSettingsDestination.EPUB_TEXT)
            .items
            .single { it.action == SharedSettingsAction.LOCAL_OVERRIDE_NOTE }
        assertEquals(SharedSettingsItemKind.INFO, categoryNote.kind)
        assertEquals(null, categoryNote.destination)

        val detailNote = assertNotNull(model.page(SharedSettingsDestination.EPUB_FORMAT).localOverrideNote)
        assertEquals(SharedSettingsItemKind.INFO, detailNote.kind)
        assertTrue(detailNote.summary.fallback.contains("reader"))
    }

    @Test
    fun `sync and account live on one page with sync rows first`() {
        val model = model(SharedSettingsPlatform.ANDROID) {
            copy(isSignedIn = true, isProUser = true, includeAccountDeletion = true)
        }
        val items = model.page(SharedSettingsDestination.SYNC_ACCOUNT).items
        val actions = items.map { it.action }

        assertTrue(SharedSettingsAction.CLOUD_SYNC in actions)
        assertTrue(SharedSettingsAction.FOLDER_SYNC in actions)
        assertTrue(SharedSettingsAction.SIGN_OUT in actions)
        assertTrue(SharedSettingsAction.DELETE_ACCOUNT in actions)
        assertTrue(actions.indexOf(SharedSettingsAction.CLOUD_SYNC) < actions.indexOf(SharedSettingsAction.SIGN_OUT))
    }

    @Test
    fun `delete account is hidden unless explicitly enabled`() {
        val defaultActions = model(SharedSettingsPlatform.ANDROID) {
            copy(isSignedIn = true, isProUser = true)
        }.visibleNestedActions()

        assertFalse(SharedSettingsAction.DELETE_ACCOUNT in defaultActions)
    }

    @Test
    fun `delete account requires a signed-in account`() {
        val signedOutActions = model(SharedSettingsPlatform.IOS) {
            copy(isSignedIn = false, includeAccountDeletion = true)
        }.visibleNestedActions()
        val signedInItem = model(SharedSettingsPlatform.IOS) {
            copy(isSignedIn = true, includeAccountDeletion = true)
        }.page(SharedSettingsDestination.SYNC_ACCOUNT)
            .items
            .single { it.action == SharedSettingsAction.DELETE_ACCOUNT }

        assertFalse(SharedSettingsAction.DELETE_ACCOUNT in signedOutActions)
        assertEquals(SharedSettingsItemKind.DESTRUCTIVE, signedInItem.kind)
    }

    @Test
    fun `search returns nested results with breadcrumbs`() {
        val results = model().searchResults("custom fonts")
        val result = results.single { it.title.fallback == "Custom fonts" }

        // Custom fonts opens a host screen, so search lands on the page that owns the row.
        assertEquals(SharedSettingsDestination.APP_LIBRARY, result.destination)
        assertEquals(
            listOf("Settings", "App & Library"),
            result.breadcrumb.map { it.fallback }
        )
    }

    @Test
    fun `search results always land on a rendered page`() {
        // The hub runs nothing from a result row: every hit navigates, so a stray toggle or cache
        // clear can never fire out of context. The result type enforces a destination; this pins
        // that the destination is a page the hub can actually render.
        val model = model()
        val renderable = buildSet {
            add(SharedSettingsDestination.ROOT)
            model.rootCategories.forEach { add(it.destination) }
            model.rootCategories.forEach { category ->
                model.page(category.destination).items.mapNotNullTo(this) { it.destination }
            }
        }

        val results = model.searchResults("e")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.destination in renderable })
    }

    @Test
    fun `search lands on the owning page for rows that open a dialog`() {
        val result = model().searchResults("language").single {
            it.title.fallback == "Language" && it.destination == SharedSettingsDestination.APP_LIBRARY
        }

        assertEquals(SharedSettingsDestination.APP_LIBRARY, result.destination)
        assertEquals(SharedSettingsItemKind.CONTROL, result.kind)
    }

    @Test
    fun `search matches control names through keywords`() {
        val result = model().searchResults("line spacing").single {
            it.title.fallback == "Text and layout"
        }

        assertEquals(SharedSettingsDestination.EPUB_FORMAT, result.destination)
    }

    @Test
    fun `settings destinations expose stable parents`() {
        assertEquals(SharedSettingsDestination.ROOT, SharedSettingsDestination.EPUB_TEXT.parentDestination())
        assertEquals(SharedSettingsDestination.ROOT, SharedSettingsDestination.STORAGE_PRIVACY.parentDestination())
        assertEquals(SharedSettingsDestination.EPUB_TEXT, SharedSettingsDestination.EPUB_FORMAT.parentDestination())
        assertEquals(SharedSettingsDestination.PDF_COMICS, SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS.parentDestination())
        assertEquals(SharedSettingsDestination.TTS_AI, SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS.parentDestination())
        assertEquals(null, SharedSettingsDestination.ROOT.parentDestination())
    }

    @Test
    fun `tts replacements are only exposed from the speech page`() {
        val model = model()

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

    @Test
    fun `show reader ai toggle replaces the old inverted label`() {
        val item = model {
            copy(includeHideReaderAi = true, hideReaderAi = true)
        }.page(SharedSettingsDestination.TTS_AI)
            .items
            .single { it.action == SharedSettingsAction.HIDE_READER_AI }

        assertEquals("Show reader AI", item.title.fallback)
        assertEquals(false, item.checked)
    }

    @Test
    fun `pdf reader managed tools render as a note instead of a dead page`() {
        val items = model().page(SharedSettingsDestination.PDF_COMICS).items

        val note = items.single { it.kind == SharedSettingsItemKind.INFO }
        assertEquals("PDF reader tools", note.title.fallback)
        assertEquals(null, note.destination)
        assertTrue(items.any { it.destination == SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS })
    }

    @Test
    fun `every navigation row resolves to a real page filed under its own category`() {
        val model = model()

        for (category in model.rootCategories) {
            for (item in model.page(category.destination).items) {
                val destination = item.destination ?: continue
                val kind = model.page(destination).kind
                assertTrue(kind == SharedSettingsPageKind.CATEGORY || kind == SharedSettingsPageKind.DETAIL)
                assertEquals(
                    destination.parentDestination(),
                    category.destination,
                    "${item.action} is filed under a parent it does not declare"
                )
            }
        }
    }

    @Test
    fun `no row is exposed on two pages`() {
        // Device management used to appear in both Account and Extra; a row that exists twice is
        // either a bug or a copy that has already started to drift.
        val model = model(SharedSettingsPlatform.ANDROID) {
            copy(
                isDebugBuild = true,
                isSignedIn = true,
                includeScreenCaptureProtection = true,
                includePdfFileNameDisplayName = true,
                includeCloudLocalDataClear = true,
                includeDiagnosticLogExport = true,
                includeFpsOverlayToggle = true
            )
        }
        val ids = model.rootCategories
            .flatMap { category -> model.page(category.destination).items }
            .map { it.id }

        assertEquals(ids.size, ids.distinct().size, "duplicated row ids: ${ids.groupBy { it }.filterValues { it.size > 1 }}")
    }

    @Test
    fun `destructive rows never navigate`() {
        // Destructive rows are split into their own trailing group and confirmed by the host, so a
        // chevron would promise a page that does not exist.
        val model = model(SharedSettingsPlatform.ANDROID) {
            copy(
                isDebugBuild = true,
                isSignedIn = true,
                includeAccountDeletion = true,
                includeCloudLocalDataClear = true
            )
        }

        val destructive = model.rootCategories
            .flatMap { category -> model.page(category.destination).items }
            .filter { it.kind == SharedSettingsItemKind.DESTRUCTIVE }

        assertTrue(destructive.isNotEmpty())
        assertTrue(destructive.all { it.destination == null })
    }

    @Test
    fun `navigation depth orders every page under its parent`() {
        // The hub picks the slide direction from this, so a detail page that reported the same
        // depth as its category would animate a drill-down as if it were a step back.
        assertEquals(0, SharedSettingsDestination.ROOT.navigationDepth())
        SharedSettingsDestination.entries
            .filter { it != SharedSettingsDestination.ROOT }
            .forEach { destination ->
                val parent = assertNotNull(destination.parentDestination())
                assertEquals(parent.navigationDepth() + 1, destination.navigationDepth())
            }
    }

    @Test
    fun `every settings action still has a home`() {
        // Guards the promise that the IA change reorganised options without dropping any. Signed-in
        // and signed-out states are unioned because SIGN_IN and SIGN_OUT are mutually exclusive.
        fun actionsFor(signedIn: Boolean): List<SharedSettingsAction> = model(SharedSettingsPlatform.ANDROID) {
            copy(
                featurePolicy = SharedFeaturePolicy.Standard,
                isDebugBuild = true,
                isSignedIn = signedIn,
                isProUser = true,
                accountAvailable = true,
                syncAvailable = true,
                folderSyncAvailable = true,
                includeLanguage = true,
                includeScreenCaptureProtection = true,
                includeExternalFileBehavior = true,
                includeCustomFonts = true,
                includeStrictFileFilter = true,
                includePdfFileNameDisplayName = true,
                includeReaderTabs = true,
                includeHideReaderAi = true,
                includeAccountDeletion = true,
                includeCloudLocalDataClear = true,
                includeDiagnosticLogExport = true,
                includeFpsOverlayToggle = true,
                supportProjectAvailable = true
            )
        }.visibleNestedActions()

        // Detail pages carry their own per-book note, which is not a row on a category page.
        val detailNotes = SharedSettingsDestination.entries
            .mapNotNull { destination -> model(SharedSettingsPlatform.ANDROID).page(destination).localOverrideNote }
            .map { it.action }

        val actions = (
            actionsFor(signedIn = true) +
                actionsFor(signedIn = false) +
                detailNotes
            ).toSet()

        val missing = SharedSettingsAction.entries.filterNot { it in actions }
        assertEquals(emptyList(), missing, "settings lost their row: $missing")
    }
}

private fun SharedSettingsHubModel.visibleNestedActions(): List<SharedSettingsAction> {
    return rootCategories.flatMap { category ->
        page(category.destination).items.map { it.action }
    }
}
