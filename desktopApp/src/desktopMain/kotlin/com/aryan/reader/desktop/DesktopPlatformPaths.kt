package com.aryan.reader.desktop

import java.io.File
import java.util.Locale

internal enum class DesktopOperatingSystem {
    WINDOWS,
    LINUX,
    MACOS,
    OTHER
}

internal enum class DesktopArchitecture(val resourceName: String) {
    X64("x64"),
    ARM64("arm64"),
    X86("x86"),
    OTHER("unknown")
}

internal data class DesktopPlatform(
    val os: DesktopOperatingSystem,
    val architecture: DesktopArchitecture
) {
    val isLinux: Boolean get() = os == DesktopOperatingSystem.LINUX
    val isWindows: Boolean get() = os == DesktopOperatingSystem.WINDOWS

    val kcefBundleDirectoryName: String
        get() = when (os) {
            DesktopOperatingSystem.WINDOWS -> "kcef-bundle"
            DesktopOperatingSystem.LINUX -> "kcef-bundle-linux-${architecture.resourceName}"
            DesktopOperatingSystem.MACOS -> "kcef-bundle-macos-${architecture.resourceName}"
            DesktopOperatingSystem.OTHER -> "kcef-bundle-${architecture.resourceName}"
        }

    val pdfiumDirectoryName: String
        get() = when (os) {
            DesktopOperatingSystem.WINDOWS -> "win-${architecture.resourceName}-v8"
            DesktopOperatingSystem.LINUX -> "linux-${architecture.resourceName}-v8"
            DesktopOperatingSystem.MACOS -> "mac-${architecture.resourceName}-v8"
            DesktopOperatingSystem.OTHER -> "${architecture.resourceName}-v8"
        }

    val pdfiumLibraryFileName: String
        get() = when (os) {
            DesktopOperatingSystem.WINDOWS -> "pdfium.dll"
            DesktopOperatingSystem.LINUX -> "libpdfium.so"
            DesktopOperatingSystem.MACOS -> "libpdfium.dylib"
            DesktopOperatingSystem.OTHER -> "pdfium"
        }

    val pdfiumLibraryDirectoryName: String
        get() = when (os) {
            DesktopOperatingSystem.WINDOWS -> "bin"
            DesktopOperatingSystem.LINUX,
            DesktopOperatingSystem.MACOS,
            DesktopOperatingSystem.OTHER -> "lib"
        }
}

internal fun currentDesktopPlatform(
    osName: String = System.getProperty("os.name").orEmpty(),
    osArch: String = System.getProperty("os.arch").orEmpty()
): DesktopPlatform {
    return DesktopPlatform(
        os = desktopOperatingSystem(osName),
        architecture = desktopArchitecture(osArch)
    )
}

internal fun desktopOperatingSystem(osName: String): DesktopOperatingSystem {
    val normalized = osName.trim().lowercase(Locale.ROOT)
    return when {
        normalized.startsWith("windows") -> DesktopOperatingSystem.WINDOWS
        normalized == "linux" || normalized.contains("linux") -> DesktopOperatingSystem.LINUX
        normalized.startsWith("mac") || normalized.contains("darwin") -> DesktopOperatingSystem.MACOS
        else -> DesktopOperatingSystem.OTHER
    }
}

internal fun desktopArchitecture(osArch: String): DesktopArchitecture {
    return when (osArch.trim().lowercase(Locale.ROOT)) {
        "amd64", "x86_64", "x64" -> DesktopArchitecture.X64
        "aarch64", "arm64" -> DesktopArchitecture.ARM64
        "x86", "i386", "i686" -> DesktopArchitecture.X86
        else -> DesktopArchitecture.OTHER
    }
}

internal fun desktopUserDataRoot(
    platform: DesktopPlatform = currentDesktopPlatform(),
    env: (String) -> String? = System::getenv,
    userHome: String = System.getProperty("user.home").orEmpty()
): File {
    return when (platform.os) {
        DesktopOperatingSystem.WINDOWS -> File(windowsRoamingBase(env, userHome), "Episteme")
        DesktopOperatingSystem.LINUX -> File(xdgBase("XDG_DATA_HOME", ".local/share", env, userHome), "episteme")
        DesktopOperatingSystem.MACOS -> File(userHome, "Library/Application Support/Episteme")
        DesktopOperatingSystem.OTHER -> File(userHome, ".episteme")
    }
}

internal fun desktopUserConfigRoot(
    platform: DesktopPlatform = currentDesktopPlatform(),
    env: (String) -> String? = System::getenv,
    userHome: String = System.getProperty("user.home").orEmpty()
): File {
    return when (platform.os) {
        DesktopOperatingSystem.WINDOWS -> File(windowsRoamingBase(env, userHome), "Episteme")
        DesktopOperatingSystem.LINUX -> File(xdgBase("XDG_CONFIG_HOME", ".config", env, userHome), "episteme")
        DesktopOperatingSystem.MACOS -> File(userHome, "Library/Application Support/Episteme")
        DesktopOperatingSystem.OTHER -> File(userHome, ".episteme")
    }
}

internal fun desktopUserCacheRoot(
    platform: DesktopPlatform = currentDesktopPlatform(),
    env: (String) -> String? = System::getenv,
    userHome: String = System.getProperty("user.home").orEmpty()
): File {
    return when (platform.os) {
        DesktopOperatingSystem.WINDOWS -> File(windowsRoamingBase(env, userHome), "Episteme")
        DesktopOperatingSystem.LINUX -> File(xdgBase("XDG_CACHE_HOME", ".cache", env, userHome), "episteme")
        DesktopOperatingSystem.MACOS -> File(userHome, "Library/Caches/Episteme")
        DesktopOperatingSystem.OTHER -> File(userHome, ".episteme/cache")
    }
}

private fun windowsRoamingBase(env: (String) -> String?, userHome: String): File {
    return env("APPDATA")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?: File(userHome, "AppData/Roaming")
}

private fun xdgBase(
    envName: String,
    fallbackRelativePath: String,
    env: (String) -> String?,
    userHome: String
): File {
    return env(envName)
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isAbsolute }
        ?: File(userHome, fallbackRelativePath)
}
