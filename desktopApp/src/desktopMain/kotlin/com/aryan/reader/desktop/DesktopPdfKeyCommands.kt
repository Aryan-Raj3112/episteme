package com.aryan.reader.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.event.KeyEvent as AwtKeyEvent

internal enum class DesktopPdfKeyCommand {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    SCROLL_UP,
    SCROLL_DOWN,
    FIRST_PAGE,
    LAST_PAGE,
    SEARCH,
    ZOOM_IN,
    ZOOM_OUT,
    EXIT_FULLSCREEN
}

internal fun KeyEvent.desktopPdfKeyCommandOrNull(
    fullscreen: Boolean,
    editingText: Boolean,
    rightToLeftPagination: Boolean = false
): DesktopPdfKeyCommand? {
    if (type != KeyEventType.KeyDown) return null
    if (fullscreen && key == Key.Escape) {
        return DesktopPdfKeyCommand.EXIT_FULLSCREEN
    }
    if (editingText && !isCtrlPressed) {
        return null
    }
    return when {
        key == Key.DirectionLeft -> if (rightToLeftPagination) {
            DesktopPdfKeyCommand.NEXT_PAGE
        } else {
            DesktopPdfKeyCommand.PREVIOUS_PAGE
        }
        key == Key.DirectionRight -> if (rightToLeftPagination) {
            DesktopPdfKeyCommand.PREVIOUS_PAGE
        } else {
            DesktopPdfKeyCommand.NEXT_PAGE
        }
        key == Key.DirectionUp -> DesktopPdfKeyCommand.SCROLL_UP
        key == Key.DirectionDown -> DesktopPdfKeyCommand.SCROLL_DOWN
        key == Key.PageUp -> DesktopPdfKeyCommand.PREVIOUS_PAGE
        key == Key.PageDown -> DesktopPdfKeyCommand.NEXT_PAGE
        key == Key.MoveHome -> DesktopPdfKeyCommand.FIRST_PAGE
        key == Key.MoveEnd -> DesktopPdfKeyCommand.LAST_PAGE
        isCtrlPressed && key == Key.F -> DesktopPdfKeyCommand.SEARCH
        isCtrlPressed && key == Key.Equals -> DesktopPdfKeyCommand.ZOOM_IN
        isCtrlPressed && key == Key.Minus -> DesktopPdfKeyCommand.ZOOM_OUT
        else -> null
    }
}

internal fun AwtKeyEvent.desktopPdfKeyCommandOrNull(
    fullscreen: Boolean,
    editingText: Boolean,
    rightToLeftPagination: Boolean = false
): DesktopPdfKeyCommand? {
    if (id != AwtKeyEvent.KEY_PRESSED) return null
    if (fullscreen && keyCode == AwtKeyEvent.VK_ESCAPE) {
        return DesktopPdfKeyCommand.EXIT_FULLSCREEN
    }
    if (editingText && !isControlDown) {
        return null
    }
    return when (keyCode) {
        AwtKeyEvent.VK_LEFT -> if (rightToLeftPagination) {
            DesktopPdfKeyCommand.NEXT_PAGE
        } else {
            DesktopPdfKeyCommand.PREVIOUS_PAGE
        }
        AwtKeyEvent.VK_RIGHT -> if (rightToLeftPagination) {
            DesktopPdfKeyCommand.PREVIOUS_PAGE
        } else {
            DesktopPdfKeyCommand.NEXT_PAGE
        }
        AwtKeyEvent.VK_UP -> DesktopPdfKeyCommand.SCROLL_UP
        AwtKeyEvent.VK_DOWN -> DesktopPdfKeyCommand.SCROLL_DOWN
        AwtKeyEvent.VK_PAGE_UP -> DesktopPdfKeyCommand.PREVIOUS_PAGE
        AwtKeyEvent.VK_PAGE_DOWN -> DesktopPdfKeyCommand.NEXT_PAGE
        AwtKeyEvent.VK_HOME -> DesktopPdfKeyCommand.FIRST_PAGE
        AwtKeyEvent.VK_END -> DesktopPdfKeyCommand.LAST_PAGE
        AwtKeyEvent.VK_F -> if (isControlDown) DesktopPdfKeyCommand.SEARCH else null
        AwtKeyEvent.VK_EQUALS,
        AwtKeyEvent.VK_PLUS,
        AwtKeyEvent.VK_ADD -> if (isControlDown) DesktopPdfKeyCommand.ZOOM_IN else null
        AwtKeyEvent.VK_MINUS,
        AwtKeyEvent.VK_SUBTRACT -> if (isControlDown) DesktopPdfKeyCommand.ZOOM_OUT else null
        else -> null
    }
}
