package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ui.MobileAppDrawerCapabilities

/**
 * Drawer variants used by the iOS host.
 *
 * App Review 3.1.1 withholds every support/donation entry point on iOS. The donation
 * surface linked out to GitHub Sponsors and Patreon, so the row is suppressed here rather
 * than removed from the shared model — Android keeps the same drawer as before.
 */
internal val iosGlobalDrawerCapabilities: MobileAppDrawerCapabilities =
    MobileAppDrawerCapabilities.GLOBAL.copy(showSupportProject = false)

internal val iosUnifiedAccountDrawerCapabilities: MobileAppDrawerCapabilities =
    MobileAppDrawerCapabilities.UNIFIED_LIBRARY_ACCOUNT.copy(showSupportProject = false)