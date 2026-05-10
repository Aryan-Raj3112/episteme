package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.SharedFeaturePolicy
import java.io.File

internal const val DesktopFlavorProperty = "episteme.desktop.flavor"
internal const val DesktopFlavorStandard = "standard"
internal const val DesktopFlavorOssOffline = "oss-offline"
internal const val ComposeApplicationResourcesDirProperty = "compose.application.resources.dir"

internal data class DesktopBuildProfile(
    val flavor: String,
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
    val flavor = rawFlavor?.trim()?.lowercase().takeUnless { it.isNullOrBlank() }
        ?: DesktopFlavorStandard
    return when (flavor) {
        DesktopFlavorOssOffline -> DesktopBuildProfile(
            flavor = DesktopFlavorOssOffline,
            featurePolicy = SharedFeaturePolicy.OssOffline
        )
        else -> DesktopBuildProfile(
            flavor = DesktopFlavorStandard,
            featurePolicy = SharedFeaturePolicy.Standard
        )
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
    val resourceDir = System.getProperty(ComposeApplicationResourcesDirProperty)
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    return listOfNotNull(
        resourceDir?.resolve("kcef-bundle"),
        File(System.getProperty("user.dir"), "kcef-bundle"),
        File(System.getProperty("user.dir"), "desktopApp/kcef-bundle"),
        File("desktopApp/kcef-bundle"),
        File("kcef-bundle")
    ).firstOrNull(::isBundledDesktopWebViewPresent)
        ?: resourceDir?.resolve("kcef-bundle")
        ?: File("kcef-bundle")
}

internal fun isBundledDesktopWebViewPresent(dir: File): Boolean {
    return dir.isDirectory &&
        dir.resolve("jcef.dll").isFile &&
        dir.resolve("libcef.dll").isFile
}
