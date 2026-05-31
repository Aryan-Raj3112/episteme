package com.aryan.reader.desktop

internal data class DesktopFeatureNoticePlacement(
    val readerWindowId: String? = null
) {
    fun rendersInMainWindow(): Boolean = readerWindowId == null

    fun rendersInReaderWindow(windowId: String): Boolean = readerWindowId == windowId
}

internal fun desktopFeatureNoticePlacement(readerWindowId: String?): DesktopFeatureNoticePlacement {
    return DesktopFeatureNoticePlacement(readerWindowId = readerWindowId?.takeIf { it.isNotBlank() })
}
