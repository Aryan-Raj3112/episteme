package com.aryan.reader.shared.ios

import com.aryan.reader.shared.CloudSyncSetupIntent
import com.aryan.reader.shared.CloudSyncSetupRoute
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform
import com.aryan.reader.shared.cloudSyncSetupRoute
import com.aryan.reader.shared.sharedSettingsHubModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosSettingsParityTest {
    @Test
    fun everySharedSettingsActionHasAnIosDisposition() {
        val dispositions = SharedSettingsAction.entries.associateWith { it.iosDisposition() }

        assertEquals(SharedSettingsAction.entries.size, dispositions.size)
        assertTrue(dispositions.values.none { it.name.isBlank() })
    }

    @Test
    fun noNonCloudNonPaidSettingsGapsRemain() {
        val gaps = SharedSettingsAction.entries
            .filter { it.iosDisposition() == IosSettingsActionDisposition.PARITY_GAP }
            .toSet()

        assertTrue(gaps.isEmpty())
        // Pinned as a set rather than merely "non-empty" so that *adding* a difference is a conscious
        // decision: this assertion is the ratchet, and an ratchet that accepts anything new is not
        // one. Each entry needs its own reason in `IosSettingsParity.kt`, and the three do not share
        // one — screen capture protection is a platform capability, clearing the book cache is a
        // storage-layout consequence, and Support is withheld by App Review 3.1.1.
        assertEquals(
            setOf(
                SharedSettingsAction.SCREEN_CAPTURE_PROTECTION,
                SharedSettingsAction.CLEAR_BOOK_CACHE,
                SharedSettingsAction.SUPPORT,
            ),
            SharedSettingsAction.entries
                .filter { it.iosDisposition() == IosSettingsActionDisposition.INTENTIONAL_PLATFORM_DIFFERENCE }
                .toSet(),
        )
    }

    @Test
    fun cloudAccountAndAiSettingsAreImplementedOnIos() {
        assertEquals(
            IosSettingsActionDisposition.IMPLEMENTED_ON_IOS,
            SharedSettingsAction.AI_SETTINGS.iosDisposition(),
        )
        assertEquals(
            IosSettingsActionDisposition.IMPLEMENTED_ON_IOS,
            SharedSettingsAction.CLOUD_SYNC.iosDisposition(),
        )
        assertEquals(
            IosSettingsActionDisposition.IMPLEMENTED_ON_IOS,
            SharedSettingsAction.DEVICE_MANAGEMENT.iosDisposition(),
        )
        assertEquals(
            IosSettingsActionDisposition.IMPLEMENTED_ON_IOS,
            SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA.iosDisposition(),
        )
        assertEquals(
            IosSettingsActionDisposition.IMPLEMENTED_ON_IOS,
            SharedSettingsAction.DELETE_ACCOUNT.iosDisposition(),
        )
    }

    @Test
    fun iosDiagnosticsExposeLogExportWithoutAndroidOnlyMlActions() {
        val items = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                includeDiagnosticLogExport = true,
                isDebugBuild = false,
            )
        ).sections.flatMap { it.items }
        val actions = items.map { it.action }.toSet()

        assertTrue(SharedSettingsAction.EXPORT_LOGS in actions)
        assertTrue(SharedSettingsAction.TEST_PANEL_DETECTION !in actions)
        assertTrue(SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION !in actions)
    }

    @Test
    fun iosDebugAccountOperationsDoNotExposeAndroidOnlyMlActions() {
        val items = sharedSettingsHubModel(
            SharedSettingsHubInput(
                platform = SharedSettingsPlatform.IOS,
                isDebugBuild = true,
                isSignedIn = true,
                isProUser = true,
                syncAvailable = true,
                cloudSyncSetupIntent = CloudSyncSetupIntent.READY,
                includeCloudLocalDataClear = true,
                includeHideReaderAi = true,
            )
        ).sections.flatMap { it.items }
        val actions = items.map { it.action }.toSet()

        assertTrue(SharedSettingsAction.DEVICE_MANAGEMENT in actions)
        assertTrue(SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA in actions)
        assertTrue(SharedSettingsAction.EXPORT_LOGS in actions)
        assertEquals(
            1,
            items.count { it.action == SharedSettingsAction.DEVICE_MANAGEMENT },
        )
        assertEquals(
            1,
            items.count { it.action == SharedSettingsAction.HIDE_READER_AI },
        )
        assertFalse(SharedSettingsAction.TEST_PANEL_DETECTION in actions)
        assertFalse(SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION in actions)
    }

    @Test
    fun iosCloudSyncSetupRoutesProviderAndDrivePrerequisites() {
        assertEquals(
            CloudSyncSetupRoute.OPEN_ACCOUNT,
            cloudSyncSetupRoute(CloudSyncSetupIntent.NEEDS_GOOGLE_LINK),
        )
        assertEquals(
            CloudSyncSetupRoute.AUTHORIZE_GOOGLE_DRIVE,
            cloudSyncSetupRoute(CloudSyncSetupIntent.NEEDS_DRIVE_AUTH),
        )
    }
}
