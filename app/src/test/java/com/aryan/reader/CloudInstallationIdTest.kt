package com.aryan.reader

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Guards the single shared installation identity.
 *
 * This value keys `users/{uid}/devices/{installationId}` in Firestore, and the
 * server-side FCM fan-out excludes the publishing device by comparing against
 * `originDeviceId`. Three call sites previously reimplemented the same
 * `installation_id` preference lookup independently. If they ever disagreed —
 * one reading a blank string as a valid ID, another generating a second UUID —
 * the fan-out would stop excluding its own publisher and every sync would echo
 * back to the device that just pushed.
 */
class CloudInstallationIdTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private val stored = mutableMapOf<String, String?>()

    @Before
    fun setUp() {
        preferences = mockk(relaxed = true)
        every { preferences.getString(any(), any()) } answers {
            val key = firstArg<String>()
            stored[key] ?: secondArg<String?>()
        }
        every { preferences.edit() } answers {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.putString(any(), any()) } answers {
                stored[firstArg()] = secondArg<String?>()
                editor
            }
            editor
        }
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
    }

    @Test
    fun `returns the same id for every call so device exclusion keeps working`() {
        val first = CloudInstallationId.get(context)
        val second = CloudInstallationId.get(context)

        assertEquals("every call must resolve to one stable identity", first, second)
    }

    @Test
    fun `generates an id when none is stored`() {
        assertNotEquals(
            "must not hand back a blank identity",
            "",
            CloudInstallationId.get(context),
        )
    }

    @Test
    fun `reuses an already persisted id instead of regenerating`() {
        stored["installation_id"] = "existing-install-id"

        assertEquals("existing-install-id", CloudInstallationId.get(context))
    }

    @Test
    fun `treats a blank stored value as absent rather than as an identity`() {
        // A blank originDeviceId would make the fan-out's exclusion compare
        // against "" and match no real device document.
        stored["installation_id"] = "   "

        val generated = CloudInstallationId.get(context)

        assertNotEquals("a blank stored value must be replaced", "   ", generated)
        assertEquals(generated, CloudInstallationId.get(context))
    }
}