package com.aryan.reader

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Guards the no-browser fallback: tapping a link on a device with nothing to
 * handle ACTION_VIEW must copy the link (and toast) instead of crashing with
 * ActivityNotFoundException.
 */
@RunWith(RobolectricTestRunner::class)
class CustomTabUriHandlerTest {

    private class NoBrowserContext(base: Context) : ContextWrapper(base) {
        override fun startActivity(intent: Intent) {
            throw ActivityNotFoundException("No Activity found to handle $intent")
        }
    }

    @Test
    fun `openUri without a browser copies the link instead of crashing`() {
        val app = RuntimeEnvironment.getApplication() as Application
        val handler = CustomTabUriHandler(NoBrowserContext(app))
        val url = "https://github.com/sponsors/Aryan-Raj3112"

        handler.openUri(url) // must not throw

        // The link is preserved for the user to open elsewhere. (The companion
        // "no browser" toast uses R.string.error_no_browser, which exists in
        // 10 locales; it isn't asserted here because this unit-test runtime
        // cannot load app string resources.)
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(url, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
