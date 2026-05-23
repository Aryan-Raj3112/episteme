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
    fun `epub right to left pagination swaps physical arrow navigation`() {
        assertEquals(
            DesktopReaderKeyNavigation.PREVIOUS,
            awtKeyEvent(KeyEvent.VK_RIGHT, 0, KeyEvent.CHAR_UNDEFINED)
                .desktopReaderKeyNavigationOrNull(fullscreen = false, rightToLeftPagination = true)
        )
        assertEquals(
            DesktopReaderKeyNavigation.NEXT,
            awtKeyEvent(KeyEvent.VK_LEFT, 0, KeyEvent.CHAR_UNDEFINED)
                .desktopReaderKeyNavigationOrNull(fullscreen = false, rightToLeftPagination = true)
        )
        assertEquals(
            DesktopReaderKeyNavigation.NEXT,
            awtKeyEvent(KeyEvent.VK_PAGE_DOWN, 0, KeyEvent.CHAR_UNDEFINED)
                .desktopReaderKeyNavigationOrNull(fullscreen = false, rightToLeftPagination = true)
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

    @Test
    fun `pdf right to left pagination swaps physical arrow navigation`() {
        assertEquals(
            DesktopPdfKeyCommand.PREVIOUS_PAGE,
            awtKeyEvent(KeyEvent.VK_RIGHT, 0, KeyEvent.CHAR_UNDEFINED)
                .desktopPdfKeyCommandOrNull(
                    fullscreen = false,
                    editingText = false,
                    rightToLeftPagination = true
                )
        )
        assertEquals(
            DesktopPdfKeyCommand.NEXT_PAGE,
            awtKeyEvent(KeyEvent.VK_LEFT, 0, KeyEvent.CHAR_UNDEFINED)
                .desktopPdfKeyCommandOrNull(
                    fullscreen = false,
                    editingText = false,
                    rightToLeftPagination = true
                )
        )
        assertEquals(
            DesktopPdfKeyCommand.NEXT_PAGE,
            awtKeyEvent(KeyEvent.VK_PAGE_DOWN, 0, KeyEvent.CHAR_UNDEFINED)
                .desktopPdfKeyCommandOrNull(
                    fullscreen = false,
                    editingText = false,
                    rightToLeftPagination = true
                )
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
