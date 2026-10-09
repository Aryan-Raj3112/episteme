package com.aryan.reader.shared.ui

/** Stable, typed application destinations shared by the mobile hosts. */
enum class SharedMobileAppDestination(val route: String) {
    MAIN("main"),
    PDF_VIEWER("pdf_viewer"),
    EPUB_READER("epub_reader"),
    PRO("pro_screen"),
    FEEDBACK("feedback_screen_route"),
    SUPPORT_PROJECT("support_project_screen_route"),
    FONTS("fonts_screen_route"),
    AI_SETTINGS("ai_settings_screen_route"),
    SETTINGS("settings_screen_route"),
    /** Dedicated cloud-folder policy surface reachable from Home and Library Beta. */
    FOLDER_SYNC_SETTINGS("folder_sync_settings_route");

    val isReader: Boolean
        get() = this == PDF_VIEWER || this == EPUB_READER

    val participatesInSelectedFileSync: Boolean
        get() = this == MAIN || isReader

    companion object {
        fun fromRoute(route: String?): SharedMobileAppDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * Android's source-of-truth main-screen order, shared by both phone hosts.
 *
 * Library Beta is the only main destination: the separate Home and Library screens were
 * retired and the bottom navigation bar removed. `HOME` and `LIBRARY` remain declared so a
 * page index persisted by an older install still resolves instead of reading out of bounds.
 */
enum class SharedMobileMainDestination {
    HOME,
    LIBRARY,
    UNIFIED_LIBRARY;

    companion object {
        /** The single destination the app renders. */
        val current: SharedMobileMainDestination = UNIFIED_LIBRARY

        /** Any persisted page index resolves to Library Beta; retired indices are remapped. */
        fun fromPageIndex(index: Int): SharedMobileMainDestination = UNIFIED_LIBRARY
    }
}
