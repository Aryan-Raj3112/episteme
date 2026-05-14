package com.aryan.reader.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Color as AwtColor
import java.awt.Window as AwtWindow
import java.util.concurrent.atomic.AtomicReference
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay

internal const val EpistemeDesktopWindowTitle = EpistemeDesktopStandardAppName
internal const val EpistemeDesktopWindowIconResource = "episteme_icon.png"
internal const val EpistemeDesktopWindowMinimumWidthPx = 960
internal const val EpistemeDesktopWindowMinimumHeightPx = 640

internal data class DesktopWindowDefaults(
    val title: String,
    val defaultSize: DpSize,
    val minimumSize: Dimension,
    val iconResourcePath: String
)

internal fun epistemeDesktopWindowDefaults(
    profile: DesktopBuildProfile = currentDesktopBuildProfile()
): DesktopWindowDefaults {
    return DesktopWindowDefaults(
        title = profile.appName,
        defaultSize = DpSize(1280.dp, 820.dp),
        minimumSize = Dimension(EpistemeDesktopWindowMinimumWidthPx, EpistemeDesktopWindowMinimumHeightPx),
        iconResourcePath = EpistemeDesktopWindowIconResource
    )
}

internal data class DesktopWindowChromeColors(
    val useDarkMode: Boolean,
    val captionColorRef: Int,
    val textColorRef: Int,
    val borderColorRef: Int
)

internal fun desktopWindowChromeColors(
    captionColor: Color,
    textColor: Color,
    borderColor: Color
): DesktopWindowChromeColors {
    return DesktopWindowChromeColors(
        useDarkMode = captionColor.luminance() < 0.5f,
        captionColorRef = captionColor.toWindowsColorRef(),
        textColorRef = textColor.toWindowsColorRef(),
        borderColorRef = borderColor.toWindowsColorRef()
    )
}

@Composable
internal fun EpistemeDesktopWindowChromeEffect(
    window: Component?,
    captionColor: Color,
    textColor: Color,
    borderColor: Color
) {
    DisposableEffect(window, captionColor, textColor, borderColor) {
        applyDesktopWindowBackground(window, borderColor)
        applyWindowsDesktopWindowChrome(
            window = window,
            colors = desktopWindowChromeColors(
                captionColor = captionColor,
                textColor = textColor,
                borderColor = borderColor
            )
        )
        onDispose {}
    }
}

@Composable
internal fun EpistemeDesktopWindowDecorationEffect(
    window: Component?,
    hideDecoration: Boolean
) {
    val originalStyle = remember(window) { AtomicReference<Int?>(null) }
    LaunchedEffect(window, hideDecoration) {
        delay(if (hideDecoration) 120L else 80L)
        applyWindowsDesktopWindowDecoration(
            window = window,
            hideDecoration = hideDecoration,
            originalStyle = originalStyle
        )
    }
    DisposableEffect(window, hideDecoration) {
        onDispose {
            if (hideDecoration) {
                applyWindowsDesktopWindowDecoration(
                    window = window,
                    hideDecoration = false,
                    originalStyle = originalStyle
                )
            }
        }
    }
}

internal fun isWindowsDesktop(osName: String = System.getProperty("os.name").orEmpty()): Boolean {
    return osName.startsWith("Windows", ignoreCase = true)
}

private fun applyDesktopWindowBackground(window: Component?, color: Color) {
    val awtColor = color.toAwtOpaqueColor()
    runOnEventDispatchThread {
        val awtWindow = window.toAwtWindowOrNull()
        window?.background = awtColor
        awtWindow?.background = awtColor
        (awtWindow as? Container)?.background = awtColor
        (awtWindow as? RootPaneContainer)?.let { rootPaneContainer ->
            rootPaneContainer.contentPane.background = awtColor
            rootPaneContainer.rootPane.background = awtColor
            rootPaneContainer.layeredPane.background = awtColor
            rootPaneContainer.glassPane.background = awtColor
        }
    }
}

private fun applyWindowsDesktopWindowChrome(
    window: Component?,
    colors: DesktopWindowChromeColors,
    osName: String = System.getProperty("os.name").orEmpty()
) {
    if (!isWindowsDesktop(osName)) return
    runOnEventDispatchThread {
        val awtWindow = window.toAwtWindowOrNull() ?: return@runOnEventDispatchThread
        val hwnd = runCatching { Native.getWindowPointer(awtWindow) }.getOrNull() ?: return@runOnEventDispatchThread
        WindowsDwmApi.applyWindowChrome(hwnd, colors)
    }
}

private fun applyWindowsDesktopWindowDecoration(
    window: Component?,
    hideDecoration: Boolean,
    originalStyle: AtomicReference<Int?>,
    osName: String = System.getProperty("os.name").orEmpty()
) {
    if (!isWindowsDesktop(osName)) return
    EventQueue.invokeLater decoration@{
        val awtWindow = window.toAwtWindowOrNull() ?: return@decoration
        val hwnd = runCatching { Native.getWindowPointer(awtWindow) }.getOrNull() ?: return@decoration
        val api = runCatching { User32Api.INSTANCE }.getOrNull() ?: return@decoration
        if (hideDecoration) {
            val style = api.GetWindowLongW(hwnd, GWL_STYLE)
            originalStyle.compareAndSet(null, style)
            val fullscreenStyle = style and WS_CAPTION.inv() and WS_THICKFRAME.inv()
            if (fullscreenStyle != style) {
                api.SetWindowLongW(hwnd, GWL_STYLE, fullscreenStyle)
                api.SetWindowPos(
                    hwnd,
                    null,
                    0,
                    0,
                    0,
                    0,
                    SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED
                )
            }
        } else {
            val restoredStyle = originalStyle.getAndSet(null) ?: return@decoration
            api.SetWindowLongW(hwnd, GWL_STYLE, restoredStyle)
            api.SetWindowPos(
                hwnd,
                null,
                0,
                0,
                0,
                0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED
            )
        }
    }
}

private fun Component?.toAwtWindowOrNull(): AwtWindow? {
    return when (this) {
        null -> null
        is AwtWindow -> this
        else -> SwingUtilities.getWindowAncestor(this)
    }
}

private fun runOnEventDispatchThread(block: () -> Unit) {
    if (EventQueue.isDispatchThread()) {
        block()
    } else {
        EventQueue.invokeLater(block)
    }
}

private fun Color.toAwtOpaqueColor(): AwtColor {
    val argb = toArgb()
    return AwtColor(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF
    )
}

private fun Color.toWindowsColorRef(): Int {
    val argb = toArgb()
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return red or (green shl 8) or (blue shl 16)
}

private const val GWL_STYLE = -16
private const val WS_CAPTION = 0x00C00000
private const val WS_THICKFRAME = 0x00040000
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_NOACTIVATE = 0x0010
private const val SWP_FRAMECHANGED = 0x0020

private object WindowsDwmApi {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    fun applyWindowChrome(hwnd: Pointer, colors: DesktopWindowChromeColors) {
        val api = runCatching { DwmApi.INSTANCE }.getOrNull() ?: return
        val darkModeValue = if (colors.useDarkMode) 1 else 0
        val darkModeResult = api.setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkModeValue)
        if (darkModeResult != 0) {
            api.setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, darkModeValue)
        }
        api.setIntAttribute(hwnd, DWMWA_CAPTION_COLOR, colors.captionColorRef)
        api.setIntAttribute(hwnd, DWMWA_TEXT_COLOR, colors.textColorRef)
        api.setIntAttribute(hwnd, DWMWA_BORDER_COLOR, colors.borderColorRef)
    }

    private fun DwmApi.setIntAttribute(hwnd: Pointer, attribute: Int, value: Int): Int {
        return runCatching {
            val ref = IntByReference(value)
            DwmSetWindowAttribute(hwnd, attribute, ref.pointer, Int.SIZE_BYTES)
        }.getOrDefault(-1)
    }
}

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, valueSize: Int): Int

    companion object {
        val INSTANCE: DwmApi by lazy {
            Native.load("dwmapi", DwmApi::class.java) as DwmApi
        }
    }
}

private interface User32Api : StdCallLibrary {
    fun GetWindowLongW(hwnd: Pointer, index: Int): Int
    fun SetWindowLongW(hwnd: Pointer, index: Int, value: Int): Int
    fun SetWindowPos(
        hwnd: Pointer,
        insertAfter: Pointer?,
        x: Int,
        y: Int,
        cx: Int,
        cy: Int,
        flags: Int
    ): Boolean

    companion object {
        val INSTANCE: User32Api by lazy {
            Native.load("user32", User32Api::class.java) as User32Api
        }
    }
}
