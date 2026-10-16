// WebViewProviderAvailability.kt
package com.aryan.reader.epubreader

import android.webkit.WebView

/**
 * Whether the platform can load a WebView provider in this process.
 *
 * Device builds without an installed (or with a disabled) WebView provider
 * crash the first `android.webkit.WebView` construction with
 * `AndroidRuntimeException: WebViewFactory$MissingWebViewPackageException`,
 * so WebView-backed surfaces must check this before composing any WebView
 * (see `EpubReaderRenderSurfaces`).
 *
 * [currentWebViewPackage] is injectable for tests. The platform query reports
 * a missing provider either by returning null or by throwing (older releases
 * throw `MissingWebViewPackageException`/`AndroidRuntimeException`; broken
 * provider installs can surface as errors), so any failure counts as
 * unavailable.
 */
internal fun isWebViewProviderAvailable(
    currentWebViewPackage: () -> Any? = { WebView.getCurrentWebViewPackage() },
): Boolean = try {
    currentWebViewPackage() != null
} catch (_: Throwable) {
    false
}
