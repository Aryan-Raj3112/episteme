package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.SharedFeaturePolicy
import java.io.File

internal const val DesktopFlavorProperty = "episteme.desktop.flavor"
internal const val DesktopVersionProperty = "episteme.desktop.version"
internal const val DesktopFlavorStandard = "standard"
internal const val DesktopFlavorOssOffline = "oss-offline"
internal const val EpistemeDesktopStandardAppName = "Episteme"
internal const val EpistemeDesktopOssAppName = "Episteme oss"
internal const val ComposeApplicationResourcesDirProperty = "compose.application.resources.dir"

internal data class DesktopBuildProfile(
    val flavor: String,
    val appName: String,
    val buildLabel: String,
    val featurePolicy: SharedFeaturePolicy
) {
    val isOssOffline: Boolean get() = flavor == DesktopFlavorOssOffline
}

internal fun currentDesktopBuildProfile(): DesktopBuildProfile {
    return desktopBuildProfileForFlavor(
        System.getProperty(DesktopFlavorProperty, DesktopFlavorStandard)
    )
}

internal fun desktopBuildProfileForFlavor(rawFlavor: String?): DesktopBuildProfile {
    val flavor = normalizedDesktopFlavor(rawFlavor)
    return when (flavor) {
        DesktopFlavorOssOffline -> DesktopBuildProfile(
            flavor = DesktopFlavorOssOffline,
            appName = EpistemeDesktopOssAppName,
            buildLabel = "Offline OSS edition",
            featurePolicy = SharedFeaturePolicy.OssOffline
        )
        else -> DesktopBuildProfile(
            flavor = DesktopFlavorStandard,
            appName = EpistemeDesktopStandardAppName,
            buildLabel = "Standard edition",
            featurePolicy = SharedFeaturePolicy.Standard
        )
    }
}

private fun normalizedDesktopFlavor(rawFlavor: String?): String {
    return when (rawFlavor?.trim()?.lowercase()) {
        DesktopFlavorOssOffline,
        "oss",
        "episteme-oss" -> DesktopFlavorOssOffline
        else -> DesktopFlavorStandard
    }
}

internal fun ReaderAiByokSettings.withDesktopFeaturePolicy(
    featurePolicy: SharedFeaturePolicy
): ReaderAiByokSettings {
    return if (featurePolicy.aiAndCloud) {
        sanitized()
    } else {
        ReaderAiByokSettings(hideReaderAiFeatures = true)
    }
}

internal fun bundledDesktopWebViewDir(): File {
    val platform = currentDesktopPlatform()
    val resourceDir = System.getProperty(ComposeApplicationResourcesDirProperty)
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    return listOfNotNull(
        resourceDir?.resolve("kcef-bundle"),
        File(System.getProperty("user.dir"), "kcef-bundle"),
        File(System.getProperty("user.dir"), "desktopApp/${platform.kcefBundleDirectoryName}"),
        File(System.getProperty("user.dir"), "desktopApp/kcef-bundle"),
        File("desktopApp/${platform.kcefBundleDirectoryName}"),
        File("desktopApp/kcef-bundle"),
        File(platform.kcefBundleDirectoryName),
        File("kcef-bundle")
    ).firstOrNull(::isBundledDesktopWebViewPresent)
        ?: resourceDir?.resolve("kcef-bundle")
        ?: File(platform.kcefBundleDirectoryName)
}

internal fun isBundledDesktopWebViewPresent(
    dir: File,
    platform: DesktopPlatform = currentDesktopPlatform()
): Boolean {
    return dir.isDirectory &&
        bundledDesktopWebViewRequiredPaths(platform).all { requiredPath ->
            dir.resolve(requiredPath).exists()
        }
}

internal fun bundledDesktopWebViewRequiredPaths(
    platform: DesktopPlatform = currentDesktopPlatform()
): List<String> {
    return when (platform.os) {
        DesktopOperatingSystem.WINDOWS -> listOf("jcef.dll", "libcef.dll")
        DesktopOperatingSystem.LINUX -> listOf("libcef.so", "chrome-sandbox", "icudtl.dat", "locales")
        DesktopOperatingSystem.MACOS -> listOf("jcef Helper.app", "Chromium Embedded Framework.framework")
        DesktopOperatingSystem.OTHER -> emptyList()
    }
}
