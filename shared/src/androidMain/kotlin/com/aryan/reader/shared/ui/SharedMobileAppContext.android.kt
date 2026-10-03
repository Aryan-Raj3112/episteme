package com.aryan.reader.shared.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The application `Context` shared module-wide, since a dozen Android actuals need one and none
 * of them is inside a `@Composable` where `LocalContext` is available.
 *
 * Registered once from `MyApplication.onCreate`, and defensively re-registered by the PDF renderer
 * and TTS entry points so a process that starts there is never left without one.
 *
 * Lives in its own file rather than inside a seam because every seam needs it.
 */
internal object AndroidSharedMobileContext {
    var applicationContext: Context? = null
}

internal fun sharedAndroidMobileApplicationContext(): Context? =
    AndroidSharedMobileContext.applicationContext

fun registerSharedAndroidMobileApplicationContext(context: Context) {
    AndroidSharedMobileContext.applicationContext = context.applicationContext
}

@Composable
internal fun rememberAndroidSharedMobileContext(): Context {
    val context = LocalContext.current
    AndroidSharedMobileContext.applicationContext = context.applicationContext
    return context
}
