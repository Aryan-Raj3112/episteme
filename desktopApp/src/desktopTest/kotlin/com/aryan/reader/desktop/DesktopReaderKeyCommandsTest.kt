package com.aryan.reader.desktop

import java.awt.Canvas
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopReaderKeyCommandsTest {

    @Test
    fun `ctrl f opens epub reader search`() {
        assertEquals(
            DesktopReaderKeyNavigation.SEARCH,
            awtKeyEvent(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, 'F')
                .desktopReaderKeyNavigationOrNull(fullscreen = false)
        )
    }

    @Test
    fun `ctrl f opens pdf reader search while reading`() {
        assertEquals(
            DesktopPdfKeyCommand.SEARCH,
            awtKeyEvent(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, 'F')
                .desktopPdfKeyCommandOrNull(fullscreen = false, editingText = false)
        )
    }

    @Test
    fun `ctrl f opens pdf reader search while text editing`() {
        assertEquals(
            DesktopPdfKeyCommand.SEARCH,
            awtKeyEvent(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, 'F')
                .desktopPdfKeyCommandOrNull(fullscreen = false, editingText = true)
        )
    }

    private fun awtKeyEvent(
        keyCode: Int,
        modifiers: Int,
        keyChar: Char
    ): KeyEvent {
        return KeyEvent(
            Canvas(),
            KeyEvent.KEY_PRESSED,
            0L,
            modifiers,
            keyCode,
            keyChar
        )
    }
}
