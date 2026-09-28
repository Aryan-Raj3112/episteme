// src\pro
package com.aryan.reader

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** Header name the workers check for the Firebase App Check token. */
internal const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

/**
 * Installs the App Check provider factory. Call once from Application.onCreate
 * (factory is consulted lazily at token time, so onCreate is early enough).
 * Debug builds use the debug provider (register the printed token in the
 * Firebase console); release builds use Play Integrity.
 */
fun installAppCheck() {
    val factory = if (BuildConfig.DEBUG) {
        DebugAppCheckProviderFactory.getInstance()
    } else {
        PlayIntegrityAppCheckProviderFactory.getInstance()
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
}

/**
 * Current App Check token, or null when unavailable. Blocking with a short
 * timeout — call from IO threads only (every worker call site already runs
 * there). The SDK caches fresh tokens, so this is usually instant. A null
 * simply omits the header; the server logs the miss while enforcement is off.
 */
fun appCheckTokenBlocking(): String? {
    return try {
        Tasks.await(FirebaseAppCheck.getInstance().getToken(false), 5, TimeUnit.SECONDS).token
    } catch (e: Exception) {
        Timber.w(e, "App Check token unavailable")
        null
    }
}

/** Header map carrying the App Check token when available, else empty. */
fun appCheckHeaderMap(): Map<String, String> {
    val token = appCheckTokenBlocking() ?: return emptyMap()
    return mapOf(APP_CHECK_HEADER to token)
}
