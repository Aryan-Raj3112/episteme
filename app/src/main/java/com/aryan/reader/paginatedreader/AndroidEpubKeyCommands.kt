package com.aryan.reader.paginatedreader

import android.view.KeyEvent

internal enum class AndroidEpubKeyCommand {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    SCROLL_UP,
    SCROLL_DOWN,
    FIRST_PAGE,
    LAST_PAGE
}

internal fun androidEpubKeyCommandOrNull(
    keyCode: Int,
    rightToLeftPagination: Boolean = false,
    isCtrlPressed: Boolean = false
): AndroidEpubKeyCommand? {
    if (isCtrlPressed) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> if (rightToLeftPagination) {
            AndroidEpubKeyCommand.NEXT_PAGE
        } else {
            AndroidEpubKeyCommand.PREVIOUS_PAGE
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> if (rightToLeftPagination) {
            AndroidEpubKeyCommand.PREVIOUS_PAGE
        } else {
            AndroidEpubKeyCommand.NEXT_PAGE
        }
        KeyEvent.KEYCODE_DPAD_UP -> AndroidEpubKeyCommand.SCROLL_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> AndroidEpubKeyCommand.SCROLL_DOWN
        KeyEvent.KEYCODE_PAGE_UP -> AndroidEpubKeyCommand.PREVIOUS_PAGE
        KeyEvent.KEYCODE_PAGE_DOWN -> AndroidEpubKeyCommand.NEXT_PAGE
        KeyEvent.KEYCODE_MOVE_HOME -> AndroidEpubKeyCommand.FIRST_PAGE
        KeyEvent.KEYCODE_MOVE_END -> AndroidEpubKeyCommand.LAST_PAGE
        else -> null
    }
}
