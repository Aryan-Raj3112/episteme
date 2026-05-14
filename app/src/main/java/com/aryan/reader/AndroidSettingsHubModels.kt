package com.aryan.reader

import com.aryan.reader.shared.SharedFeaturePolicy
import com.aryan.reader.shared.SharedSettingsHubInput
import com.aryan.reader.shared.SharedSettingsPlatform

fun androidSettingsHubInput(
    uiState: ReaderScreenState,
    isOssBuild: Boolean = BuildConfig.FLAVOR == "oss",
    isOfflineBuild: Boolean = BuildConfig.IS_OFFLINE,
    isDebugBuild: Boolean = BuildConfig.DEBUG,
    hideReaderAi: Boolean = false
): SharedSettingsHubInput {
    val supportsSync = !isOssBuild && !isOfflineBuild
    val supportsOssAiKeys = isOssBuild && !isOfflineBuild
    val featurePolicy = if (isOfflineBuild) {
        SharedFeaturePolicy.OssOffline
    } else {
        SharedFeaturePolicy.Standard
    }
    return SharedSettingsHubInput(
        platform = SharedSettingsPlatform.ANDROID,
        featurePolicy = featurePolicy,
        isDebugBuild = isDebugBuild,
        isSignedIn = uiState.currentUser != null,
        isProUser = uiState.isProUser,
        syncAvailable = supportsSync,
        folderSyncAvailable = supportsSync,
        aiSettingsAvailable = supportsOssAiKeys,
        ttsSettingsAvailable = true,
        includePdfReaderDefaults = true,
        includeReaderToolbar = true,
        includeLanguage = true,
        includeScreenCaptureProtection = true,
        includeExternalFileBehavior = true,
        includeRecentLimit = true,
        includeCustomFonts = true,
        includeStrictFileFilter = true,
        includeHideReaderAi = !isOfflineBuild,
        includeCloudLocalDataClear = supportsSync,
        supportProjectAvailable = isOssBuild,
        isTabsEnabled = uiState.isTabsEnabled,
        isSyncEnabled = uiState.isSyncEnabled,
        isFolderSyncEnabled = uiState.isFolderSyncEnabled,
        useStrictFileFilter = uiState.useStrictFileFilter,
        isScreenCaptureProtectionEnabled = uiState.isScreenCaptureProtectionEnabled,
        hideReaderAi = hideReaderAi
    )
}
