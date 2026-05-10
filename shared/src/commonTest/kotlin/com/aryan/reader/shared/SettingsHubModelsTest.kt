package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsHubModelsTest {

    @Test
    fun `settings hub keeps reader settings first`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        )

        assertEquals(SharedSettingsSection.READER, model.sections.first().section)
        assertEquals(
            listOf(
                SharedSettingsSection.READER,
                SharedSettingsSection.APP_LIBRARY,
                SharedSettingsSection.SYNC_ACCOUNTS,
                SharedSettingsSection.AI_TTS,
                SharedSettingsSection.STORAGE_ADVANCED,
                SharedSettingsSection.HELP
            ),
            model.sections.map { it.section }
        )
    }

    @Test
    fun `offline feature policy hides network backed settings`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.ANDROID,
                featurePolicy = SharedFeaturePolicy.OssOffline,
                aiSettingsAvailable = true,
                isSignedIn = false
            )
        )
        val actions = model.sections.flatMap { it.items }.map { it.action }

        assertFalse(SharedSettingsAction.AI_SETTINGS in actions)
        assertFalse(SharedSettingsAction.CLOUD_SYNC in actions)
        assertFalse(SharedSettingsAction.SIGN_IN in actions)
        assertTrue(SharedSettingsAction.TTS_SETTINGS in actions)
        assertTrue(SharedSettingsAction.ABOUT in actions)
    }

    @Test
    fun `reader section always explains local overrides`() {
        val readerItems = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        ).itemsIn(SharedSettingsSection.READER)

        val note = readerItems.single { it.action == SharedSettingsAction.LOCAL_OVERRIDE_NOTE }
        assertEquals(SharedSettingsItemKind.INFO, note.kind)
        assertTrue(note.summary.contains("Local overrides"))
        assertTrue(note.summary.contains("reader"))
    }

    @Test
    fun `search filters matching settings and sections`() {
        val model = sharedSettingsHubModel(
            SharedSettingsHubInput(platform = SharedSettingsPlatform.DESKTOP)
        )

        val filtered = model.filtered("fonts")
        val actions = filtered.sections.flatMap { it.items }.map { it.action }

        assertEquals(listOf(SharedSettingsSection.APP_LIBRARY), filtered.sections.map { it.section })
        assertEquals(listOf(SharedSettingsAction.CUSTOM_FONTS), actions)
    }
}
