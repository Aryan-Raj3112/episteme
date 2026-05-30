package com.aryan.reader.desktop

import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SharedLegalLinks
import com.aryan.reader.shared.SharedLegalProfile
import com.aryan.reader.shared.sharedLegalLinksForProfile

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
    val featurePolicy: SharedFeaturePolicy,
    val legalProfile: SharedLegalProfile = if (featurePolicy.byokAi) {
        SharedLegalProfile.OSS
    } else {
        SharedLegalProfile.STANDARD
    }
) {
    val isOssOffline: Boolean get() = flavor == DesktopFlavorOssOffline
    val aiKeySettingsAvailable: Boolean
        get() = featurePolicy.aiAndCloud && featurePolicy.networkAccess && legalProfile != SharedLegalProfile.OSS
    val byokAiAvailable: Boolean get() = featurePolicy.byokAi && featurePolicy.aiAndCloud && featurePolicy.networkAccess
    val creditBackedCloudTtsControlsAvailable: Boolean
        get() = featurePolicy.aiAndCloud && featurePolicy.networkAccess && !byokAiAvailable
    val legalLinks: SharedLegalLinks
        get() = sharedLegalLinksForProfile(legalProfile)
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
            featurePolicy = SharedFeaturePolicy.OssOffline,
            legalProfile = SharedLegalProfile.OSS
        )
        else -> DesktopBuildProfile(
            flavor = DesktopFlavorStandard,
            appName = EpistemeDesktopStandardAppName,
            buildLabel = "Standard edition",
            featurePolicy = SharedFeaturePolicy.Standard,
            legalProfile = SharedLegalProfile.STANDARD
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
    return if (featurePolicy.byokAi && featurePolicy.aiAndCloud && featurePolicy.networkAccess) {
        toDesktopPersistableAiSettings()
    } else {
        ReaderAiByokSettings()
    }
}

internal fun ReaderAiByokSettings.toDesktopPersistableAiSettings(): ReaderAiByokSettings {
    return sanitized().copy(hideReaderAiFeatures = false)
}
