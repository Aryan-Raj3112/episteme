package com.aryan.reader.desktop

fun main() {
    if (currentDesktopPlatform().os == DesktopOperatingSystem.MACOS) {
        DesktopSwtBrowserEventLoop.runMacOsEventLoop {
            Thread(
                {
                    val startupSplash = DesktopStartupSplash.show()
                    launchEpistemeDesktopApplication(startupSplash)
                },
                "Episteme Compose UI"
            ).start()
        }
    } else {
        val startupSplash = DesktopStartupSplash.show()
        launchEpistemeDesktopApplication(startupSplash)
    }
}
