package com.aryan.reader.paginatedreader

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidEpubKeyCommandsTest {
    @Test
    fun `left and right map to page changes`() {
        assertEquals(
            AndroidEpubKeyCommand.PREVIOUS_PAGE,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_DPAD_LEFT)
        )
        assertEquals(
            AndroidEpubKeyCommand.NEXT_PAGE,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_DPAD_RIGHT)
        )
    }

    @Test
    fun `left and right respect right to left pagination`() {
        assertEquals(
            AndroidEpubKeyCommand.NEXT_PAGE,
            androidEpubKeyCommandOrNull(
                KeyEvent.KEYCODE_DPAD_LEFT,
                rightToLeftPagination = true
            )
        )
        assertEquals(
            AndroidEpubKeyCommand.PREVIOUS_PAGE,
            androidEpubKeyCommandOrNull(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                rightToLeftPagination = true
            )
        )
    }

    @Test
    fun `up and down map to vertical scroll`() {
        assertEquals(
            AndroidEpubKeyCommand.SCROLL_UP,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_DPAD_UP)
        )
        assertEquals(
            AndroidEpubKeyCommand.SCROLL_DOWN,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_DPAD_DOWN)
        )
    }

    @Test
    fun `page home and end keys map to reader navigation`() {
        assertEquals(
            AndroidEpubKeyCommand.PREVIOUS_PAGE,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_PAGE_UP)
        )
        assertEquals(
            AndroidEpubKeyCommand.NEXT_PAGE,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_PAGE_DOWN)
        )
        assertEquals(
            AndroidEpubKeyCommand.FIRST_PAGE,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_MOVE_HOME)
        )
        assertEquals(
            AndroidEpubKeyCommand.LAST_PAGE,
            androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_MOVE_END)
        )
    }

    @Test
    fun `ctrl shortcuts are left for reader chrome and search handling`() {
        assertNull(androidEpubKeyCommandOrNull(KeyEvent.KEYCODE_DPAD_RIGHT, isCtrlPressed = true))
    }
}
