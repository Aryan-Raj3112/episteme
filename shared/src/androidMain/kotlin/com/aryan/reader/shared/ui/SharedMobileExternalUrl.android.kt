package com.aryan.reader.shared.ui

import android.content.Intent
import android.net.Uri
import com.aryan.reader.shared.normalizeReaderHref
internal actual fun openSharedMobileExternalUrl(url: String): Boolean = openAndroidUrl(url)

internal fun openAndroidUrl(url: String): Boolean {
    val context = AndroidSharedMobileContext.applicationContext ?: return false
    val normalized = normalizeReaderHref(url)
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
