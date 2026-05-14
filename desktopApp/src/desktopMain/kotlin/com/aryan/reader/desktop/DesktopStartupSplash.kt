package com.aryan.reader.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JWindow
import javax.swing.SwingConstants

internal data class DesktopStartupSplashSpec(
    val title: String = EpistemeDesktopWindowTitle,
    val message: String = "Opening your library",
    val width: Int = 360,
    val height: Int = 220
)

internal fun epistemeDesktopStartupSplashSpec(
    profile: DesktopBuildProfile = currentDesktopBuildProfile()
): DesktopStartupSplashSpec {
    return DesktopStartupSplashSpec(title = profile.appName)
}

internal class DesktopStartupSplash private constructor(
    private val window: JWindow
) {
    fun close() {
        runOnSplashEventThread {
            window.isVisible = false
            window.dispose()
        }
    }

    companion object {
        fun show(spec: DesktopStartupSplashSpec = epistemeDesktopStartupSplashSpec()): DesktopStartupSplash? {
            if (GraphicsEnvironment.isHeadless()) return null

            val splashRef = AtomicReference<DesktopStartupSplash?>()
            runOnSplashEventThreadAndWait {
                runCatching {
                    val window = JWindow().apply {
                        name = "episteme-startup-splash"
                        preferredSize = Dimension(spec.width, spec.height)
                        minimumSize = Dimension(spec.width, spec.height)
                        background = SplashBackground
                        contentPane = startupSplashContent(spec)
                        pack()
                        setLocationRelativeTo(null)
                        isAlwaysOnTop = true
                        isVisible = true
                    }
                    splashRef.set(DesktopStartupSplash(window))
                }
            }
            return splashRef.get()
        }
    }
}

private fun startupSplashContent(spec: DesktopStartupSplashSpec): JPanel {
    return JPanel(BorderLayout()).apply {
        preferredSize = Dimension(spec.width, spec.height)
        background = SplashBackground
        border = BorderFactory.createLineBorder(SplashBorder)

        val body = JPanel().apply {
            background = SplashBackground
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(24, 28, 22, 28)
        }

        startupSplashIcon()?.let { icon ->
            body.add(
                JLabel(icon).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    horizontalAlignment = SwingConstants.CENTER
                }
            )
            body.add(Box.createVerticalStrut(14))
        }

        body.add(
            JLabel(spec.title).apply {
                alignmentX = Component.CENTER_ALIGNMENT
                horizontalAlignment = SwingConstants.CENTER
                foreground = SplashTitle
                font = font.deriveFont(Font.BOLD, 24f)
            }
        )
        body.add(Box.createVerticalStrut(8))
        body.add(
            JLabel(spec.message).apply {
                alignmentX = Component.CENTER_ALIGNMENT
                horizontalAlignment = SwingConstants.CENTER
                foreground = SplashText
                font = font.deriveFont(Font.PLAIN, 13f)
            }
        )
        body.add(Box.createVerticalStrut(20))
        body.add(
            JProgressBar().apply {
                alignmentX = Component.CENTER_ALIGNMENT
                isIndeterminate = true
                isBorderPainted = false
                preferredSize = Dimension(220, 8)
                maximumSize = Dimension(220, 8)
                foreground = SplashAccent
                background = SplashTrack
            }
        )

        add(body, BorderLayout.CENTER)
    }
}

private fun startupSplashIcon(): ImageIcon? {
    val resource = Thread.currentThread().contextClassLoader?.getResource(EpistemeDesktopWindowIconResource)
        ?: DesktopStartupSplash::class.java.classLoader?.getResource(EpistemeDesktopWindowIconResource)
        ?: return null
    val icon = ImageIcon(resource)
    return ImageIcon(icon.image.getScaledInstance(56, 56, Image.SCALE_SMOOTH))
}

private fun runOnSplashEventThread(block: () -> Unit) {
    if (EventQueue.isDispatchThread()) {
        block()
    } else {
        EventQueue.invokeLater { block() }
    }
}

private fun runOnSplashEventThreadAndWait(block: () -> Unit) {
    if (EventQueue.isDispatchThread()) {
        block()
        return
    }
    try {
        EventQueue.invokeAndWait { block() }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    } catch (_: InvocationTargetException) {
        // Startup feedback should never prevent the app from launching.
    }
}

private val SplashBackground = Color(0xF9, 0xF7, 0xEF)
private val SplashBorder = Color(0xD8, 0xD2, 0xC3)
private val SplashTitle = Color(0x1E, 0x22, 0x1A)
private val SplashText = Color(0x61, 0x64, 0x58)
private val SplashAccent = Color(0x2F, 0x6F, 0x68)
private val SplashTrack = Color(0xE3, 0xDE, 0xD1)
