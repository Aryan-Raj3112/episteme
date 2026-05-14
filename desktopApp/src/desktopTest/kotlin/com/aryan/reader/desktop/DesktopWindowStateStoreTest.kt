package com.aryan.reader.desktop

import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowStateStoreTest {

    @Test
    fun `desktop window state round trips through config store`() {
        val file = createTempFile("episteme-window-state", ".json").toFile()
        val store = DesktopWindowStateStore(file)
        val snapshot = DesktopWindowStateSnapshot(
            placement = DesktopSavedWindowPlacement.FLOATING,
            widthDp = 1440f,
            heightDp = 900f,
            xDp = 120f,
            yDp = 80f
        )

        store.save(snapshot)

        assertEquals(snapshot, store.load())
    }

    @Test
    fun `desktop window state clamps too small saved bounds`() {
        val snapshot = DesktopWindowStateSnapshot(
            placement = DesktopSavedWindowPlacement.FLOATING,
            widthDp = 12f,
            heightDp = 34f
        ).sanitized()

        assertEquals(EpistemeDesktopWindowMinimumWidthPx.toFloat(), snapshot.widthDp)
        assertEquals(EpistemeDesktopWindowMinimumHeightPx.toFloat(), snapshot.heightDp)
    }
}
