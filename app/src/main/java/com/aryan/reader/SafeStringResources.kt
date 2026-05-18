package com.aryan.reader

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.IllegalFormatException
import java.util.Locale

@Composable
fun safeStringResource(@StringRes id: Int, vararg formatArgs: Any?): String {
    LocalConfiguration.current
    return LocalContext.current.safeGetString(id, *formatArgs)
}

fun Context.safeGetString(@StringRes id: Int, vararg formatArgs: Any?): String {
    return try {
        resources.getString(id, *formatArgs)
    } catch (_: IllegalFormatException) {
        try {
            englishResources().getString(id, *formatArgs)
        } catch (_: IllegalFormatException) {
            resources.getString(id)
        }
    }
}

private fun Context.englishResources() =
    createConfigurationContext(
        Configuration(resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
    ).resources
