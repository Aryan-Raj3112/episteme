package com.aryan.reader.shared.reader

import java.io.File
import java.util.Locale

internal fun sharedJvmEpistemeCacheRoot(
    env: (String) -> String? = System::getenv,
    userHome: String = System.getProperty("user.home").orEmpty(),
    osName: String = System.getProperty("os.name").orEmpty()
): File {
    val normalizedOs = osName.trim().lowercase(Locale.ROOT)
    return when {
        normalizedOs.startsWith("windows") -> {
            val baseDir = env("APPDATA")?.takeIf { it.isNotBlank() }
                ?: File(userHome, "AppData/Roaming").absolutePath
            File(baseDir, "Episteme")
        }
        normalizedOs == "linux" || normalizedOs.contains("linux") -> {
            val baseDir = env("XDG_CACHE_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isAbsolute }
                ?: File(userHome, ".cache")
            File(baseDir, "episteme")
        }
        normalizedOs.startsWith("mac") || normalizedOs.contains("darwin") -> {
            File(userHome, "Library/Caches/Episteme")
        }
        else -> File(userHome, ".episteme/cache")
    }
}
