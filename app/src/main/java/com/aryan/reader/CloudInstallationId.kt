package com.aryan.reader

import android.content.Context
import java.util.UUID

/**
 * The stable per-install device identifier.
 *
 * This is the ID that keys `users/{uid}/devices/{installationId}` in
 * Firestore, and therefore the ID the server-side FCM fan-out excludes the
 * publishing device by. It is deliberately NOT the same value as the
 * cloud-folder `deviceId` (an `ANDROID_ID`-derived value used for folder
 * manifests), because those live in different ID spaces.
 *
 * Three call sites previously reimplemented this against the same
 * `installation_id` preference key in `reader_user_prefs`, which is exactly the
 * kind of duplication that produces a fan-out that cannot exclude its own
 * writer. They now share this one implementation.
 */
internal object CloudInstallationId {
    private const val PREFS_NAME = "reader_user_prefs"
    private const val KEY_INSTALLATION_ID = "installation_id"

    /** Returns the existing ID, creating and persisting one on first call. */
    fun get(context: Context): String {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_INSTALLATION_ID, generated).apply()
        return generated
    }
}