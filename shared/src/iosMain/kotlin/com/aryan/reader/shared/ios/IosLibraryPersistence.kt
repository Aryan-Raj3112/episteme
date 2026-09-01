package com.aryan.reader.shared.ios

import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.migrateAndroidEpubFormatSettings
import com.aryan.reader.shared.toSharedMobileLibrarySnapshot
import platform.Foundation.NSUserDefaults

private const val IosReaderPreferencesDefaultsKey = "reader_ios_reader_preferences_v1"
private const val IosLibrarySnapshotDefaultsKey = "reader_ios_library_snapshot_v1"

internal fun loadIosLibrarySnapshot(): SharedLibrarySnapshot {
    val defaults = NSUserDefaults.standardUserDefaults
    val encoded = defaults.stringForKey(IosLibrarySnapshotDefaultsKey)
        ?: defaults.stringForKey(IosReaderPreferencesDefaultsKey)
        ?: return SharedLibrarySnapshot()
    val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(encoded)
    val normalized = decoded.migrateAndroidEpubFormatSettings()
    if (normalized != decoded) {
        defaults.setObject(
            SharedLibrarySnapshotJson.encode(
                normalized
                    .withStableIosBookPaths()
                    .withStableIosAudiobookPaths()
            ),
            forKey = IosLibrarySnapshotDefaultsKey,
        )
    }
    return normalized
        .withResolvedIosBookPaths()
        .withResolvedIosAudiobookPaths()
}

internal fun persistIosLibrarySnapshot(state: SharedReaderScreenState) {
    val encoded = SharedLibrarySnapshotJson.encode(
        state.toSharedMobileLibrarySnapshot()
            .migrateAndroidEpubFormatSettings()
            .withStableIosBookPaths()
            .withStableIosAudiobookPaths()
    )
    NSUserDefaults.standardUserDefaults.setObject(encoded, forKey = IosLibrarySnapshotDefaultsKey)
}
