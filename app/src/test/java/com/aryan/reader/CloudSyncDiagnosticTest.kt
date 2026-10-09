package com.aryan.reader

import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared-tag sync diagnostics.
 *
 * `CloudSyncDiagnostic.kt` replaced two separate log tags with one
 * (`EpistemeCloudSync`) carrying explicit `plane`/`dir` fields, so a single
 * `logcat -s EpistemeCloudSync` shows both sides of sync. These tests protect the
 * two properties that make that safe and useful:
 *
 *  1. **No user data.** Identifiers are hashed. A regression here would put
 *     account IDs, book IDs or URIs into every user's logcat.
 *  2. **Device attribution is stable and distinct.** The whole value of the
 *     single tag is telling two devices apart; if the label were unstable or
 *     collided, cross-device correlation would silently break.
 */
class CloudSyncDiagnosticTest {

    private fun sha256Short(value: String, take: Int = 8): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(Locale.US, byte) }
            .take(take)

    @Test
    fun `device label is stable for the same installation id`() {
        // Cross-device correlation depends on this: the same install must always
        // render the same label, or every line looks like a different device.
        assertEquals(
            cloudSyncDeviceLabel("install-a"),
            cloudSyncDeviceLabel("install-a"),
        )
    }

    @Test
    fun `device label never contains the raw installation id`() {
        val raw = "550e8400-e29b-41d4-a716-446655440000"

        val label = cloudSyncDeviceLabel(raw)

        assertFalse("raw installation id must not be logged", label.contains(raw))
        assertFalse("label must be a compact token: $label", label.contains("-"))
        assertTrue("label must carry its plane prefix: $label", label.startsWith("dev"))
    }

    @Test
    fun `different installations get different labels`() {
        assertNotEquals(
            cloudSyncDeviceLabel("install-a"),
            cloudSyncDeviceLabel("install-b"),
        )
    }

    @Test
    fun `blank installation id is reported as unknown rather than hashed`() {
        // Hashing "" would produce a stable-looking label for every device that
        // has not resolved its identity yet, silently merging unrelated events.
        assertEquals("unknown", cloudSyncDeviceLabel(null))
        assertEquals("unknown", cloudSyncDeviceLabel(""))
        assertEquals("unknown", cloudSyncDeviceLabel("   "))
    }

    @Test
    fun `folder device label uses a distinct prefix because it is a different id space`() {
        // The folder identity is ANDROID_ID-derived while the FCM identity is a
        // random UUID. Same prefix would make the two indistinguishable in a log.
        val folder = cloudSyncFolderDeviceLabel("folder-device")
        val install = cloudSyncDeviceLabel("folder-device")

        assertTrue("expected fdev prefix, got $folder", folder.startsWith("fdev"))
        assertTrue("expected dev prefix, got $install", install.startsWith("dev"))
        assertFalse(folder.startsWith("devdev"))
    }

    @Test
    fun `folder device label is stable and redacted`() {
        val raw = "some-folder-uuid"

        assertEquals(cloudSyncFolderDeviceLabel(raw), cloudSyncFolderDeviceLabel(raw))
        assertFalse(cloudSyncFolderDeviceLabel(raw).contains(raw))
        assertEquals("unknown", cloudSyncFolderDeviceLabel(null))
    }

    @Test
    fun `label digest matches the documented derivation`() {
        // Pinned so a change to the label format is a deliberate decision rather
        // than an accident that invalidates previously captured logs.
        assertEquals("dev${sha256Short("install-a")}", cloudSyncDeviceLabel("install-a"))
        assertEquals("fdev${sha256Short("folder-device")}", cloudSyncFolderDeviceLabel("folder-device"))
    }

    @Test
    fun `plane and direction constants are the documented tokens`() {
        // These exact strings appear in the doc as the logcat vocabulary. Changing
        // them silently breaks every existing log-reading workflow.
        assertEquals("library", CloudSyncPlaneLibrary)
        assertEquals("folder", CloudSyncPlaneFolder)
        assertEquals("push", CloudSyncDirPush)
        assertEquals("pull", CloudSyncDirPull)
        assertEquals("both", CloudSyncDirBoth)
        assertEquals("none", CloudSyncDirNone)
    }
}